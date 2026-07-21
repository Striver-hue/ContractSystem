package com.example.contractsystem.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/records")
public class RecordController {
    private static final String OWNER = "Judgegao";
    private static final String REPO = "ETH-Malicious-TX-Monitor";
    private static final String BRANCH = "main";
    private static final String GITHUB_TREE_URL = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/git/trees/" + BRANCH + "?recursive=1";
    private static final String GITHUB_BLOB_BASE = "https://github.com/" + OWNER + "/" + REPO + "/blob/" + BRANCH + "/";
    private static final String GITHUB_RAW_BASE = "https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/" + BRANCH + "/";
    private static final int DISPLAY_LIMIT = 50;
    private static final int CSV_FETCH_LIMIT = 12;
    private static final Pattern CSV_PATH_PATTERN = Pattern.compile("^\\d{4}/\\d{2}/\\d{2}/(\\d+)-(\\d+)_\\d+\\.csv$");
    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions";
    private static final String DEEPSEEK_MODEL = "deepseek-v4-pro";
    private static final String DEEPSEEK_API_KEY = System.getenv("DEEPSEEK_API_KEY");
    private static final String ETH_RPC_URL = System.getenv().getOrDefault(
            "ETH_RPC_URL",
            "https://ethereum.publicnode.com"
    );
    private static final String ERC_TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "malicious-tx-record-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    private volatile Map<String, Object> cache = emptyCache("等待首次采集");

    @PostConstruct
    public void startRecordCollector() {
        scheduler.execute(this::refreshRecordsSafely);
        scheduler.scheduleAtFixedRate(this::refreshRecordsSafely, 1, 1, TimeUnit.HOURS);
    }

    @PreDestroy
    public void stopRecordCollector() {
        scheduler.shutdownNow();
    }

    @GetMapping("/latest")
    public ResponseEntity<?> latestRecords() {
        return ResponseEntity.ok(cache);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshNow() {
        refreshRecords();
        return ResponseEntity.ok(cache);
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeRecord(@RequestBody AnalyzeRequest request) {
        try {
            validateAnalyzeRequest(request);
            AnalysisContext context = buildAnalysisContext(request);
            String analysis = analyzeWithDeepSeek(context);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ok");
            result.put("model", DEEPSEEK_MODEL);
            result.put("analysis", analysis);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "error");
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private void refreshRecordsSafely() {
        try {
            refreshRecords();
        } catch (Exception ignored) {
            // Keep serving the last successful cache when GitHub is temporarily unavailable.
        }
    }

    private void refreshRecords() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }

        try {
            List<GitHubCsvFile> csvFiles = findLatestCsvFiles();
            List<RecordItem> records = new ArrayList<>();
            GitHubCsvFile newestSource = csvFiles.isEmpty() ? null : csvFiles.get(0);
            List<String> fetchErrors = new ArrayList<>();

            for (GitHubCsvFile csvFile : csvFiles) {
                try {
                    records.addAll(fetchCsvRecords(csvFile));
                } catch (Exception e) {
                    fetchErrors.add(csvFile.path() + "：" + e.getMessage());
                }
                if (records.size() >= DISPLAY_LIMIT * 2) {
                    break;
                }
            }

            records = records.stream()
                    .sorted(Comparator.comparing(RecordItem::detectTime, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(RecordItem::blockNum, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(DISPLAY_LIMIT)
                    .toList();

            Map<String, Object> nextCache = new LinkedHashMap<>();
            nextCache.put("records", records);
            nextCache.put("count", records.size());
            nextCache.put("refreshedAt", Instant.now().toString());
            nextCache.put("sourcePath", newestSource == null ? "" : newestSource.path());
            nextCache.put("sourceUrl", newestSource == null ? GITHUB_BLOB_BASE + "2026" : newestSource.htmlUrl());
            nextCache.put("repositoryUrl", "https://github.com/" + OWNER + "/" + REPO);
            nextCache.put("status", resolveStatus(records, fetchErrors));
            nextCache.put("message", resolveMessage(records, fetchErrors));
            nextCache.put("errors", fetchErrors.stream().limit(3).toList());
            cache = nextCache;
        } catch (Exception e) {
            Map<String, Object> errorCache = new LinkedHashMap<>(cache);
            errorCache.put("status", "error");
            errorCache.put("message", e.getMessage());
            errorCache.put("refreshedAt", Instant.now().toString());
            cache = errorCache;
        } finally {
            refreshing.set(false);
        }
    }

    private List<GitHubCsvFile> findLatestCsvFiles() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GITHUB_TREE_URL))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ContractSystem-RecordCollector")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("GitHub目录读取失败：recursive tree，HTTP " + response.statusCode());
        }

        JsonNode tree = objectMapper.readTree(response.body()).path("tree");
        List<GitHubCsvFile> files = new ArrayList<>();
        if (tree.isArray()) {
            for (JsonNode item : tree) {
                if (!"blob".equals(item.path("type").asText(""))) {
                    continue;
                }

                String path = item.path("path").asText("");
                Matcher matcher = CSV_PATH_PATTERN.matcher(path);
                if (!matcher.matches()) {
                    continue;
                }

                String name = path.substring(path.lastIndexOf('/') + 1);
                files.add(new GitHubCsvFile(
                        name,
                        path,
                        GITHUB_RAW_BASE + path,
                        GITHUB_BLOB_BASE + path,
                        Long.parseLong(matcher.group(1)),
                        Long.parseLong(matcher.group(2))
                ));
            }
        }

        return files.stream()
                .sorted(Comparator.comparing(GitHubCsvFile::endEpoch).reversed())
                .limit(CSV_FETCH_LIMIT)
                .toList();
    }

    private List<RecordItem> fetchCsvRecords(GitHubCsvFile csvFile) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(csvFile.downloadUrl()))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "ContractSystem-RecordCollector")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("CSV读取失败：" + csvFile.path() + "，HTTP " + response.statusCode());
        }

        List<RecordItem> records = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build();

        try (CSVParser parser = new CSVParser(new StringReader(response.body()), format)) {
            for (CSVRecord row : parser) {
                String txHash = getCsvValue(row, "tx_hash");
                if (txHash.isBlank()) {
                    continue;
                }

                String from = getCsvValue(row, "from");
                String to = getCsvValue(row, "to");
                String detectTime = getCsvValue(row, "detect_time");
                Long blockNum = parseLong(getCsvValue(row, "block_num")).orElse(null);

                records.add(new RecordItem(
                        blockNum,
                        txHash,
                        from,
                        to,
                        detectTime,
                        "https://etherscan.io/tx/" + txHash,
                        from.isBlank() ? "" : "https://etherscan.io/address/" + from,
                        to.isBlank() ? "" : "https://etherscan.io/address/" + to,
                        csvFile.path(),
                        csvFile.htmlUrl()
                ));
            }
        }

        return records;
    }

    private void validateAnalyzeRequest(AnalyzeRequest request) {
        if (request == null || request.txHash() == null || request.txHash().isBlank()) {
            throw new IllegalArgumentException("txHash不能为空");
        }
    }

    private String analyzeWithDeepSeek(AnalysisContext context) throws Exception {
        if (DEEPSEEK_API_KEY == null || DEEPSEEK_API_KEY.isBlank()) {
            throw new IllegalStateException("未配置DEEPSEEK_API_KEY，无法调用DeepSeek分析");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", DEEPSEEK_MODEL);
        payload.put("temperature", 0.2);
        payload.put("max_tokens", 1200);
        payload.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", "你是区块链安全分析助手。你只能基于后端采集到的交易详情、交易receipt、样本库命中和地址关联上下文进行分析，不得编造未采集到的地址标签、人工审计结论、资金损失或攻击归因。"
                ),
                Map.of(
                        "role", "user",
                        "content", buildAnalyzePrompt(context)
                )
        ));

        HttpRequest deepSeekRequest = HttpRequest.newBuilder(URI.create(DEEPSEEK_API_URL))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + DEEPSEEK_API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(deepSeekRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("DeepSeek分析失败，HTTP " + response.statusCode() + "：" + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("DeepSeek未返回分析内容");
        }
        return content.trim();
    }

    private AnalysisContext buildAnalysisContext(AnalyzeRequest request) {
        RpcEvidence rpcEvidence = collectRpcEvidence(request.txHash());
        AddressContext addressContext = buildAddressContext(request);
        return new AnalysisContext(request, rpcEvidence, addressContext);
    }

    private RpcEvidence collectRpcEvidence(String txHash) {
        if (ETH_RPC_URL == null || ETH_RPC_URL.isBlank()) {
            return new RpcEvidence(false, "未配置ETH_RPC_URL，跳过链上RPC富化", Map.of());
        }

        try {
            JsonNode transaction = callEthRpc("eth_getTransactionByHash", List.of(txHash));
            JsonNode receipt = callEthRpc("eth_getTransactionReceipt", List.of(txHash));

            Map<String, Object> evidence = new LinkedHashMap<>();
            if (transaction != null && !transaction.isNull()) {
                String input = transaction.path("input").asText("");
                evidence.put("rpcFrom", transaction.path("from").asText(""));
                evidence.put("rpcTo", transaction.path("to").asText(""));
                evidence.put("valueWeiHex", transaction.path("value").asText(""));
                evidence.put("gasHex", transaction.path("gas").asText(""));
                evidence.put("gasPriceHex", transaction.path("gasPrice").asText(""));
                evidence.put("transactionTypeHex", transaction.path("type").asText(""));
                evidence.put("inputLength", input.length());
                evidence.put("methodSelector", input.length() >= 10 ? input.substring(0, 10) : "");
            } else {
                evidence.put("transaction", "RPC未返回交易详情");
            }

            if (receipt != null && !receipt.isNull()) {
                JsonNode logs = receipt.path("logs");
                evidence.put("statusHex", receipt.path("status").asText(""));
                evidence.put("gasUsedHex", receipt.path("gasUsed").asText(""));
                evidence.put("contractAddress", receipt.path("contractAddress").asText(""));
                evidence.put("logCount", logs.isArray() ? logs.size() : 0);
                evidence.put("transferLogCount", countTransferLogs(logs));
            } else {
                evidence.put("receipt", "RPC未返回交易receipt");
            }

            return new RpcEvidence(true, "ok", evidence);
        } catch (Exception e) {
            return new RpcEvidence(false, e.getMessage(), Map.of());
        }
    }

    private JsonNode callEthRpc(String method, List<String> params) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 1);
        payload.put("method", method);
        payload.put("params", params);

        HttpRequest request = HttpRequest.newBuilder(URI.create(ETH_RPC_URL))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Ethereum RPC请求失败，HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        if (!root.path("error").isMissingNode()) {
            throw new IllegalStateException("Ethereum RPC错误：" + root.path("error").toString());
        }
        return root.path("result");
    }

    private int countTransferLogs(JsonNode logs) {
        if (logs == null || !logs.isArray()) {
            return 0;
        }

        int count = 0;
        for (JsonNode log : logs) {
            JsonNode topics = log.path("topics");
            if (topics.isArray() && topics.size() > 0 && ERC_TRANSFER_TOPIC.equalsIgnoreCase(topics.get(0).asText(""))) {
                count++;
            }
        }
        return count;
    }

    private AddressContext buildAddressContext(AnalyzeRequest request) {
        List<RecordItem> records = currentRecordItems();
        List<RecordItem> fromRelated = relatedRecords(records, request.from(), request.txHash());
        List<RecordItem> toRelated = relatedRecords(records, request.to(), request.txHash());
        return new AddressContext(
                request.from(),
                request.to(),
                fromRelated.size(),
                toRelated.size(),
                summarizeRelatedRecords(fromRelated),
                summarizeRelatedRecords(toRelated)
        );
    }

    private List<RecordItem> currentRecordItems() {
        Object value = cache.get("records");
        if (!(value instanceof List<?> items)) {
            return List.of();
        }

        List<RecordItem> records = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof RecordItem record) {
                records.add(record);
            }
        }
        return records;
    }

    private List<RecordItem> relatedRecords(List<RecordItem> records, String address, String currentTxHash) {
        if (address == null || address.isBlank()) {
            return List.of();
        }

        return records.stream()
                .filter(record -> !equalsIgnoreCase(record.txHash(), currentTxHash))
                .filter(record -> equalsIgnoreCase(record.from(), address) || equalsIgnoreCase(record.to(), address))
                .limit(8)
                .toList();
    }

    private List<Map<String, Object>> summarizeRelatedRecords(List<RecordItem> records) {
        List<Map<String, Object>> summary = new ArrayList<>();
        for (RecordItem record : records) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("txHash", record.txHash());
            item.put("blockNum", record.blockNum());
            item.put("from", record.from());
            item.put("to", record.to());
            item.put("detectTime", record.detectTime());
            summary.add(item);
        }
        return summary;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private String buildAnalyzePrompt(AnalysisContext context) throws Exception {
        AnalyzeRequest request = context.request();
        return """
                请对以下以太坊交易样本进行安全分析。分析时优先使用后端采集的链上RPC证据和当前样本批次的地址关联上下文。

                基础样本事实：
                - 交易哈希：%s
                - 区块号：%s
                - From：%s
                - To：%s
                - 检测时间：%s
                - 样本来源：ETH-Malicious-TX-Monitor / %s
                - Etherscan链接：%s

                链上RPC富化结果：
                %s

                当前批次地址关联上下文：
                %s

                输出要求：
                1. 初步判断：给出基于已采集证据的结果总结，不要只复述“命中样本库”。
                2. 链上证据：说明RPC交易详情、receipt、日志数量、Transfer日志等能支持哪些判断。
                3. 地址关联分析：如果From或To在当前批次关联多条交易，请汇总这些交易表现出的模式。
                4. 仍需核验：只列出当前确实没有采集到的信息，例如人工地址标签、第三方威胁情报、完整历史路径等。
                5. 建议动作：给出下一步核验和处置建议。

                不要编造未知字段，不要声称已经确认资金损失，不要把系统样本命中当作Etherscan人工审计结论。
                """.formatted(
                valueOrDash(request.txHash()),
                request.blockNum() == null ? "-" : request.blockNum().toString(),
                valueOrDash(request.from()),
                valueOrDash(request.to()),
                valueOrDash(request.detectTime()),
                valueOrDash(request.sourcePath()),
                valueOrDash(request.txUrl()),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context.rpcEvidence()),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context.addressContext())
        );
    }

    private String resolveStatus(List<RecordItem> records, List<String> fetchErrors) {
        if (records.isEmpty() && !fetchErrors.isEmpty()) {
            return "error";
        }
        if (!records.isEmpty() && !fetchErrors.isEmpty()) {
            return "partial";
        }
        return records.isEmpty() ? "empty" : "ok";
    }

    private String resolveMessage(List<RecordItem> records, List<String> fetchErrors) {
        if (records.isEmpty() && !fetchErrors.isEmpty()) {
            return "CSV采集失败：" + fetchErrors.get(0);
        }
        if (!records.isEmpty() && !fetchErrors.isEmpty()) {
            return "部分CSV采集失败，已展示可用记录";
        }
        return records.isEmpty() ? "未采集到交易记录" : "success";
    }

    private String getCsvValue(CSVRecord row, String name) {
        return row.isMapped(name) ? row.get(name).trim() : "";
    }

    private Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Map<String, Object> emptyCache(String message) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("records", List.of());
        value.put("count", 0);
        value.put("refreshedAt", "");
        value.put("sourcePath", "");
        value.put("sourceUrl", GITHUB_BLOB_BASE + "2026");
        value.put("repositoryUrl", "https://github.com/" + OWNER + "/" + REPO);
        value.put("status", "pending");
        value.put("message", message);
        return value;
    }

    private record GitHubCsvFile(String name, String path, String downloadUrl, String htmlUrl, long startEpoch, long endEpoch) {
    }

    private record RecordItem(
            Long blockNum,
            String txHash,
            String from,
            String to,
            String detectTime,
            String txUrl,
            String fromUrl,
            String toUrl,
            String sourcePath,
            String sourceUrl
    ) {
    }

    private record AnalyzeRequest(
            String txHash,
            Long blockNum,
            String from,
            String to,
            String detectTime,
            String sourcePath,
            String txUrl
    ) {
    }

    private record AnalysisContext(
            AnalyzeRequest request,
            RpcEvidence rpcEvidence,
            AddressContext addressContext
    ) {
    }

    private record RpcEvidence(
            boolean available,
            String message,
            Map<String, Object> data
    ) {
    }

    private record AddressContext(
            String from,
            String to,
            int fromRelatedCount,
            int toRelatedCount,
            List<Map<String, Object>> fromRelatedSamples,
            List<Map<String, Object>> toRelatedSamples
    ) {
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
