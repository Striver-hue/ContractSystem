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
}
