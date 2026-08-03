package com.example.contractsystem.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/fundflow")
public class fundflow {
    private static final Pattern TX_HASH_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{64}$");
    private static final String TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    private static final int EDGE_LIMIT = 60;
    private static final String UNAVAILABLE_MESSAGE = "当前hash无法展示资金流向。";
    private static final String RPC_URL = firstNonBlank(
            System.getenv("FUND_FLOW_RPC_URL"),
            System.getenv("ETH_RPC_URL"),
            "https://ethereum.publicnode.com"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, TokenInfo> tokenInfoCache = new ConcurrentHashMap<>();

    @GetMapping("/{txHash}")
    public ResponseEntity<?> getFundFlow(@PathVariable String txHash) {
        try {
            if (txHash == null || !TX_HASH_PATTERN.matcher(txHash).matches()) {
                return ResponseEntity.badRequest().body(unavailable(txHash, "交易哈希格式不正确"));
            }

            FundFlowGraph graph = buildGraph(txHash.toLowerCase());
            if (graph.edges().isEmpty()) {
                return ResponseEntity.ok(unavailable(txHash, "未从trace或Transfer日志解析到资金转移"));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ok");
            result.put("displayable", true);
            result.put("txHash", txHash);
            result.put("message", "success");
            result.put("nodes", graph.nodes());
            result.put("edges", graph.edges());
            result.put("edgeCount", graph.edgeCount());
            result.put("truncated", graph.edgeCount() > graph.edges().size());
            result.put("source", graph.source());
            result.put("explorerUrl", "https://app.blocksec.com/phalcon/explorer/tx/eth/" + txHash);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(unavailable(txHash, e.getMessage()));
        }
    }

    private FundFlowGraph buildGraph(String txHash) throws Exception {
        List<FundFlowEdge> edges = new ArrayList<>();
        Set<String> sourceParts = new LinkedHashSet<>();

        try {
            JsonNode traces = callRpc("trace_transaction", List.of(txHash));
            int before = edges.size();
            appendTraceEdges(traces, edges);
            if (edges.size() > before) {
                sourceParts.add("trace_transaction");
            }
        } catch (Exception ignored) {
            // Some RPC plans do not expose trace_transaction; receipt logs still provide ERC20 transfers.
        }

        JsonNode receipt = callRpc("eth_getTransactionReceipt", List.of(txHash));
        int beforeReceipt = edges.size();
        appendTransferLogEdges(receipt.path("logs"), edges);
        if (edges.size() > beforeReceipt) {
            sourceParts.add("eth_getTransactionReceipt");
        }

        JsonNode transaction = callRpc("eth_getTransactionByHash", List.of(txHash));
        appendTransactionValueEdge(transaction, edges);

        List<FundFlowEdge> aggregatedEdges = aggregateEdges(edges);
        List<FundFlowNode> nodes = buildNodes(aggregatedEdges);
        int edgeCount = aggregatedEdges.size();
        List<FundFlowEdge> limitedEdges = aggregatedEdges.stream().limit(EDGE_LIMIT).toList();
        String source = sourceParts.isEmpty() ? "eth_getTransactionByHash" : String.join(" + ", sourceParts);
        return new FundFlowGraph(nodes, limitedEdges, edgeCount, source);
    }

    private void appendTraceEdges(JsonNode traces, List<FundFlowEdge> edges) {
        if (traces == null || !traces.isArray()) {
            return;
        }

        for (JsonNode trace : traces) {
            JsonNode action = trace.path("action");
            String valueHex = action.path("value").asText("0x0");
            BigInteger wei = hexToBigInteger(valueHex);
            if (wei.signum() <= 0) {
                continue;
            }

            String from = action.path("from").asText("");
            String to = action.path("to").asText("");
            if (!isAddress(from) || !isAddress(to)) {
                continue;
            }

            edges.add(new FundFlowEdge(
                    edgeId(edges),
                    from.toLowerCase(),
                    to.toLowerCase(),
                    "ETH",
                    "ETH",
                    wei.toString(),
                    formatTokenAmount(wei, 18),
                    18,
                    "native",
                    trace.path("traceAddress").toString()
            ));
        }
    }

    private void appendTransferLogEdges(JsonNode logs, List<FundFlowEdge> edges) {
        if (logs == null || !logs.isArray()) {
            return;
        }

        for (JsonNode log : logs) {
            JsonNode topics = log.path("topics");
            if (!topics.isArray() || topics.size() < 3 || !TRANSFER_TOPIC.equalsIgnoreCase(topics.get(0).asText(""))) {
                continue;
            }

            String from = topicToAddress(topics.get(1).asText(""));
            String to = topicToAddress(topics.get(2).asText(""));
            String tokenAddress = log.path("address").asText("").toLowerCase();
            BigInteger rawAmount = hexToBigInteger(log.path("data").asText("0x0"));
            if (!isAddress(from) || !isAddress(to) || !isAddress(tokenAddress) || rawAmount.signum() <= 0) {
                continue;
            }

            TokenInfo tokenInfo = tokenInfo(tokenAddress);
            edges.add(new FundFlowEdge(
                    edgeId(edges),
                    from,
                    to,
                    tokenInfo.symbol(),
                    tokenAddress,
                    rawAmount.toString(),
                    formatTokenAmount(rawAmount, tokenInfo.decimals()),
                    tokenInfo.decimals(),
                    "erc20",
                    "logIndex " + log.path("logIndex").asText("")
            ));
        }
    }

    private void appendTransactionValueEdge(JsonNode transaction, List<FundFlowEdge> edges) {
        if (transaction == null || transaction.isNull()) {
            return;
        }

        BigInteger wei = hexToBigInteger(transaction.path("value").asText("0x0"));
        if (wei.signum() <= 0) {
            return;
        }

        String from = transaction.path("from").asText("");
        String to = transaction.path("to").asText("");
        if (!isAddress(from) || !isAddress(to) || hasEquivalentNativeEdge(edges, from, to, wei)) {
            return;
        }

        edges.add(new FundFlowEdge(
                edgeId(edges),
                from.toLowerCase(),
                to.toLowerCase(),
                "ETH",
                "ETH",
                wei.toString(),
                formatTokenAmount(wei, 18),
                18,
                "native",
                "transaction.value"
        ));
    }

    private List<FundFlowEdge> aggregateEdges(List<FundFlowEdge> edges) {
        Map<String, MutableEdge> grouped = new LinkedHashMap<>();
        for (FundFlowEdge edge : edges) {
            String key = String.join("|", edge.type(), edge.from(), edge.to(), edge.tokenAddress(), edge.asset());
            MutableEdge mutable = grouped.computeIfAbsent(key, ignored -> new MutableEdge(edge));
            if (mutable.source != edge) {
                mutable.rawAmount = mutable.rawAmount.add(new BigInteger(edge.rawAmount()));
                mutable.count++;
            }
        }

        List<FundFlowEdge> aggregated = new ArrayList<>();
        for (MutableEdge mutable : grouped.values()) {
            FundFlowEdge source = mutable.source;
            String evidence = mutable.count > 1 ? mutable.count + " transfers" : source.evidence();
            aggregated.add(new FundFlowEdge(
                    edgeId(aggregated),
                    source.from(),
                    source.to(),
                    source.asset(),
                    source.tokenAddress(),
                    mutable.rawAmount.toString(),
                    formatTokenAmount(mutable.rawAmount, source.decimals()),
                    source.decimals(),
                    source.type(),
                    evidence
            ));
        }
        return aggregated;
    }

    private List<FundFlowNode> buildNodes(List<FundFlowEdge> edges) {
        Map<String, AtomicInteger> inDegree = new LinkedHashMap<>();
        Map<String, AtomicInteger> outDegree = new LinkedHashMap<>();
        for (FundFlowEdge edge : edges) {
            outDegree.computeIfAbsent(edge.from(), ignored -> new AtomicInteger()).incrementAndGet();
            inDegree.computeIfAbsent(edge.to(), ignored -> new AtomicInteger()).incrementAndGet();
            inDegree.computeIfAbsent(edge.from(), ignored -> new AtomicInteger());
            outDegree.computeIfAbsent(edge.to(), ignored -> new AtomicInteger());
        }

        List<FundFlowNode> nodes = new ArrayList<>();
        Set<String> addresses = new LinkedHashSet<>();
        edges.forEach(edge -> {
            addresses.add(edge.from());
            addresses.add(edge.to());
        });

        for (String address : addresses) {
            int in = inDegree.getOrDefault(address, new AtomicInteger()).get();
            int out = outDegree.getOrDefault(address, new AtomicInteger()).get();
            String role = in == 0 ? "source" : out == 0 ? "sink" : "bridge";
            nodes.add(new FundFlowNode(address, shortAddress(address), role, in, out));
        }
        return nodes;
    }

    private boolean hasEquivalentNativeEdge(List<FundFlowEdge> edges, String from, String to, BigInteger wei) {
        return edges.stream().anyMatch(edge ->
                "native".equals(edge.type())
                        && edge.from().equalsIgnoreCase(from)
                        && edge.to().equalsIgnoreCase(to)
                        && edge.rawAmount().equals(wei.toString())
        );
    }

    private JsonNode callRpc(String method, List<?> params) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", 1);
        payload.put("method", method);
        payload.put("params", params);

        HttpRequest request = HttpRequest.newBuilder(URI.create(RPC_URL))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("RPC请求失败，HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        if (!root.path("error").isMissingNode()) {
            throw new IllegalStateException(root.path("error").path("message").asText("RPC返回错误"));
        }
        return root.path("result");
    }

    private TokenInfo tokenInfo(String tokenAddress) {
        return tokenInfoCache.computeIfAbsent(tokenAddress, address -> {
            int decimals = readDecimals(address);
            String symbol = readSymbol(address);
            return new TokenInfo(symbol.isBlank() ? shortAddress(address) : symbol, decimals);
        });
    }

    private int readDecimals(String tokenAddress) {
        try {
            JsonNode result = ethCall(tokenAddress, "0x313ce567");
            int decimals = hexToBigInteger(result.asText("0x12")).intValue();
            return Math.max(0, Math.min(decimals, 36));
        } catch (Exception e) {
            return 18;
        }
    }

    private String readSymbol(String tokenAddress) {
        try {
            JsonNode result = ethCall(tokenAddress, "0x95d89b41");
            return decodeAbiString(result.asText(""));
        } catch (Exception e) {
            return "";
        }
    }

    private JsonNode ethCall(String to, String data) throws Exception {
        Map<String, String> call = new LinkedHashMap<>();
        call.put("to", to);
        call.put("data", data);
        return callRpc("eth_call", List.of(call, "latest"));
    }

    private String decodeAbiString(String hex) {
        String clean = stripHexPrefix(hex);
        if (clean.isBlank()) {
            return "";
        }

        try {
            if (clean.length() == 64) {
                return decodeAsciiHex(clean);
            }
            if (clean.length() >= 128) {
                int length = hexToBigInteger("0x" + clean.substring(64, 128)).intValue();
                int start = 128;
                int end = Math.min(clean.length(), start + length * 2);
                return decodeAsciiHex(clean.substring(start, end));
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private String decodeAsciiHex(String cleanHex) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i + 1 < cleanHex.length(); i += 2) {
            int codePoint = Integer.parseInt(cleanHex.substring(i, i + 2), 16);
            if (codePoint == 0) {
                continue;
            }
            value.append((char) codePoint);
        }
        return value.toString().replaceAll("[^A-Za-z0-9._ -]", "").trim();
    }

    private Map<String, Object> unavailable(String txHash, String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "unavailable");
        result.put("displayable", false);
        result.put("txHash", txHash);
        result.put("message", UNAVAILABLE_MESSAGE);
        result.put("detail", detail == null ? "" : detail);
        result.put("nodes", List.of());
        result.put("edges", List.of());
        return result;
    }

    private String topicToAddress(String topic) {
        String clean = stripHexPrefix(topic);
        if (clean.length() < 40) {
            return "";
        }
        return "0x" + clean.substring(clean.length() - 40).toLowerCase();
    }

    private boolean isAddress(String value) {
        return value != null && value.matches("^0x[a-fA-F0-9]{40}$");
    }

    private BigInteger hexToBigInteger(String hex) {
        String clean = stripHexPrefix(hex);
        if (clean.isBlank()) {
            return BigInteger.ZERO;
        }
        return new BigInteger(clean, 16);
    }

    private String stripHexPrefix(String value) {
        if (value == null) {
            return "";
        }
        return value.startsWith("0x") || value.startsWith("0X") ? value.substring(2) : value;
    }

    private String formatTokenAmount(BigInteger rawAmount, int decimals) {
        BigDecimal divisor = BigDecimal.TEN.pow(Math.max(0, decimals));
        BigDecimal amount = new BigDecimal(rawAmount).divide(divisor, Math.min(Math.max(decimals, 2), 18), RoundingMode.DOWN);
        BigDecimal normalized = amount.stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ZERO) == 0 && rawAmount.signum() > 0) {
            return "<0.000001";
        }
        if (normalized.scale() > 6) {
            normalized = normalized.setScale(6, RoundingMode.DOWN).stripTrailingZeros();
        }
        return normalized.toPlainString();
    }

    private String shortAddress(String address) {
        if (address == null || address.length() <= 14) {
            return address == null ? "" : address;
        }
        return address.substring(0, 6) + "..." + address.substring(address.length() - 4);
    }

    private String edgeId(List<FundFlowEdge> edges) {
        return "edge-" + (edges.size() + 1);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private record FundFlowGraph(
            List<FundFlowNode> nodes,
            List<FundFlowEdge> edges,
            int edgeCount,
            String source
    ) {
    }

    private record FundFlowNode(
            String address,
            String label,
            String role,
            int inDegree,
            int outDegree
    ) {
    }

    private record FundFlowEdge(
            String id,
            String from,
            String to,
            String asset,
            String tokenAddress,
            String rawAmount,
            String amount,
            int decimals,
            String type,
            String evidence
    ) {
    }

    private record TokenInfo(String symbol, int decimals) {
    }

    private static final class MutableEdge {
        private final FundFlowEdge source;
        private BigInteger rawAmount;
        private int count;

        private MutableEdge(FundFlowEdge source) {
            this.source = source;
            this.rawAmount = new BigInteger(source.rawAmount());
            this.count = 1;
        }
    }
}
