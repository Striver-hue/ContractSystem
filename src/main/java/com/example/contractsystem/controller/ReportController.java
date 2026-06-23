package com.example.contractsystem.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private static final Path PROJECT_DIR = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    private static final Path SLITHER_OUTPUT_DIR = PROJECT_DIR.resolve("docker/slither/output").normalize();
    private static final Path SMARTAUDIT_OUTPUT_DIR = PROJECT_DIR.resolve("docker/smartaudit/output").normalize();
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @GetMapping("/list")
    public List<ReportView> list() throws Exception {
        List<ReportView> reports = new ArrayList<>();
        collectReports(reports, "Slither", SLITHER_OUTPUT_DIR, ".json");
        collectReports(reports, "SmartAudit", SMARTAUDIT_OUTPUT_DIR, ".txt");
        return reports.stream()
                .sorted(Comparator.comparing(ReportView::modifiedAtRaw).reversed())
                .toList();
    }

    @GetMapping("/content/{id}")
    public ResponseEntity<String> content(@PathVariable String id) throws Exception {
        ReportRef ref = decodeRef(id);
        Path reportPath = resolveReportPath(ref);
        if (!Files.isRegularFile(reportPath)) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = "Slither".equals(ref.tool())
                ? MediaType.APPLICATION_JSON
                : MediaType.TEXT_PLAIN;
        return ResponseEntity.ok()
                .contentType(new MediaType(mediaType, StandardCharsets.UTF_8))
                .body(Files.readString(reportPath, StandardCharsets.UTF_8));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id) throws Exception {
        ReportRef ref = decodeRef(id);
        Path reportPath = resolveReportPath(ref);
        if (!Files.isRegularFile(reportPath)) {
            return ResponseEntity.notFound().build();
        }

        String fileName = reportPath.getFileName().toString();
        MediaType mediaType = "Slither".equals(ref.tool())
                ? MediaType.APPLICATION_JSON
                : MediaType.TEXT_PLAIN;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodeHeader(fileName) + "\"")
                .body(new FileSystemResource(reportPath));
    }

    private void collectReports(List<ReportView> reports, String tool, Path root, String extension) throws Exception {
        if (!Files.isDirectory(root)) {
            return;
        }

        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase("mount_check.txt"))
                    .forEach(path -> {
                        try {
                            reports.add(buildReportView(tool, root, path));
                        } catch (Exception ignored) {
                            // Skip unreadable or malformed reports, keep the page usable.
                        }
                    });
        }
    }

    private ReportView buildReportView(String tool, Path root, Path path) throws Exception {
        String relativePath = root.relativize(path).toString().replace('\\', '/');
        String id = encodeRef(tool, relativePath);
        String fileName = path.getFileName().toString();
        Instant modifiedAt = Files.getLastModifiedTime(path).toInstant();
        long size = Files.size(path);

        ReportStats stats = "Slither".equals(tool)
                ? parseSlither(path)
                : parseSmartAudit(path);

        return new ReportView(
                id,
                tool,
                titleFromFile(fileName),
                fileName,
                relativePath,
                DISPLAY_TIME.format(modifiedAt),
                modifiedAt.toEpochMilli(),
                humanSize(size),
                stats.issueCount(),
                stats.highCount(),
                stats.mediumCount(),
                stats.lowCount(),
                stats.status(),
                stats.summary(),
                "/reports/content/" + id,
                "/reports/download/" + id
        );
    }

    private ReportStats parseSlither(Path path) throws Exception {
        JsonNode root = MAPPER.readTree(path.toFile());
        JsonNode detectors = root.path("results").path("detectors");
        int issues = detectors.isArray() ? detectors.size() : 0;
        int high = 0;
        int medium = 0;
        int low = 0;
        String summary = issues == 0 ? "Slither 未发现明确检测项。" : "";

        if (detectors.isArray()) {
            for (JsonNode detector : detectors) {
                String impact = detector.path("impact").asText("").toLowerCase(Locale.ROOT);
                if ("high".equals(impact)) {
                    high++;
                } else if ("medium".equals(impact)) {
                    medium++;
                } else {
                    low++;
                }
                if (summary.isBlank()) {
                    summary = detector.path("description").asText("").replace('\n', ' ').trim();
                }
            }
        }

        String status = root.path("success").asBoolean(true) ? "Completed" : "Failed";
        return new ReportStats(issues, high, medium, low, status, truncate(summary, 180));
    }

    private ReportStats parseSmartAudit(Path path) throws Exception {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        String lower = content.toLowerCase(Locale.ROOT);
        int high = countAny(lower, "critical", "high severity", "severe");
        int medium = countAny(lower, "medium severity", "moderate");
        int low = countAny(lower, "low severity", "informational");
        int issues = Math.max(1, countAny(lower, "vulnerability", "vulnerabilities", "reentrancy", "tx.origin", "overflow", "delegatecall"));
        String status = lower.contains("traceback")
                || lower.contains("exception")
                || lower.contains("error:")
                ? "Review"
                : "Completed";
        String summary = extractSmartAuditSummary(content);
        return new ReportStats(issues, high, medium, low, status, truncate(summary, 180));
    }

    private String extractSmartAuditSummary(String content) {
        for (String line : content.split("\\R")) {
            String cleaned = line.replace("*", "").replace("#", "").trim();
            if (cleaned.length() > 30
                    && !cleaned.startsWith("[")
                    && !cleaned.startsWith("|")
                    && !cleaned.toLowerCase(Locale.ROOT).contains("task_prompt")) {
                return cleaned;
            }
        }
        return "SmartAudit 已生成文本审计报告，可进入详情查看完整输出。";
    }

    private int countAny(String value, String... terms) {
        int count = 0;
        for (String term : terms) {
            int index = 0;
            while ((index = value.indexOf(term, index)) >= 0) {
                count++;
                index += term.length();
            }
        }
        return count;
    }

    private Path resolveReportPath(ReportRef ref) {
        Path root = switch (ref.tool()) {
            case "Slither" -> SLITHER_OUTPUT_DIR;
            case "SmartAudit" -> SMARTAUDIT_OUTPUT_DIR;
            default -> throw new IllegalArgumentException("Unsupported report tool");
        };
        Path path = root.resolve(ref.relativePath()).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid report path");
        }
        return path;
    }

    private String encodeRef(String tool, String relativePath) {
        String raw = tool + ":" + relativePath;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private ReportRef decodeRef(String id) {
        String raw = new String(Base64.getUrlDecoder().decode(id), StandardCharsets.UTF_8);
        int split = raw.indexOf(':');
        if (split <= 0) {
            throw new IllegalArgumentException("Invalid report id");
        }
        return new ReportRef(raw.substring(0, split), raw.substring(split + 1));
    }

    private String titleFromFile(String fileName) {
        return fileName.replaceFirst("\\.[^.]+$", "")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        return String.format(Locale.ROOT, "%.1f MB", kb / 1024.0);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "暂无摘要。";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private static String encodeHeader(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record ReportRef(String tool, String relativePath) {
    }

    private record ReportStats(int issueCount, int highCount, int mediumCount, int lowCount, String status, String summary) {
    }

    public record ReportView(
            String id,
            String tool,
            String title,
            String fileName,
            String relativePath,
            String modifiedAt,
            long modifiedAtRaw,
            String size,
            int issueCount,
            int highCount,
            int mediumCount,
            int lowCount,
            String status,
            String summary,
            String contentUrl,
            String downloadUrl
    ) {
    }
}
