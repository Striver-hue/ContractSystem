package com.example.contractsystem.controller;

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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/lunwen")
public class lunwen {

    private static final Path PAPER_DIR = Path.of(System.getProperty("user.dir"), "论文库");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("yyyy.M.d");

    private static final List<Paper> PAPERS = List.of(
            new Paper(
                    "secure-partitioning",
                    "Towards Secure Program Partitioning for Smart Contracts With LLM's In-Context Learning",
                    "Ye Liu, Yuqing Niu, Chengyan Ma, Ruidong Han, Wei Ma, Yi Li, Debin Gao, David Lo",
                    "IEEE Transactions on Software Engineering",
                    LocalDate.of(2026, 3, 2),
                    "Smart Contract Security",
                    List.of("LLM", "Program Partitioning", "Smart Contracts"),
                    "Index Terms",
                    "Towards_Secure_Program_Partitioning_for_Smart_Contracts_With_LLMs_In-Context_Learning.pdf"
            ),
            new Paper(
                    "tee-adaptation",
                    "Automated TEE Adaptation With LLMs: Identifying, Transforming, and Porting Sensitive Functions in Programs",
                    "Ruidong Han, Zhou Yang, Chengyan Ma, Ye Liu, Yuqing Niu, Siqi Ma, Debin Gao, David Lo",
                    "IEEE Transactions on Software Engineering",
                    LocalDate.of(2026, 1, 21),
                    "Trusted Execution",
                    List.of("TEE", "LLM", "Program Transformation"),
                    "Index Terms",
                    "Automated_TEE_Adaptation_With_LLMs_Identifying_Transforming_and_Porting_Sensitive_Functions_in_Programs.pdf"
            ),
            new Paper(
                    "defi-price-manipulation",
                    "Detecting Various DeFi Price Manipulations with LLM Reasoning",
                    "Juantao Zhong, Daoyuan Wu, Ye Liu, Maoyi Xie, Yang Liu, Yi Li, Ning Liu",
                    "2025 IEEE/ACM International Conference on Automated Software Engineering (ASE)",
                    LocalDate.of(2025, 11, 16),
                    "DeFi Risk Analysis",
                    List.of("DeFi", "Price Manipulation", "LLM Reasoning"),
                    "Keywords",
                    "Detecting_Various_DeFi_Price_Manipulations_with_LLM_Reasoning.pdf"
            ),
            new Paper(
                    "multi-agent-detection",
                    "Advanced Smart Contract Vulnerability Detection via LLM-Powered Multi-Agent Systems",
                    "Zhiyuan Wei, Jing Sun, Yuqiang Sun, Ye Liu, Daoyuan Wu, Zijian Zhang, Xianhao Zhang, Meng Li, Yang Liu, Chunmiao Li, Mingchao Wan, Jin Dong, Liehuang Zhu",
                    "IEEE Transactions on Software Engineering",
                    LocalDate.of(2025, 8, 11),
                    "Smart Contract Security",
                    List.of("Multi-Agent", "LLM", "Vulnerability Detection"),
                    "Index Terms",
                    "Advanced_Smart_Contract_Vulnerability_Detection_via_LLM-Powered_Multi-Agent_Systems.pdf"
            ),
            new Paper(
                    "invariant-generation",
                    "Automated Invariant Generation for Solidity Smart Contracts",
                    "Ye Liu, Chengxuan Zhang, Yi Li",
                    "IEEE Transactions on Dependable and Secure Computing",
                    LocalDate.of(2025, 8, 4),
                    "Formal Methods",
                    List.of("Invariant Generation", "Solidity", "Verification"),
                    "Index Terms",
                    "Automated_Invariant_Generation_for_Solidity_Smart_Contracts.pdf"
            )
    );

    @GetMapping("/list")
    public List<PaperView> list() {
        return PAPERS.stream()
                .sorted(Comparator.comparing(Paper::publishDate).reversed())
                .map(paper -> new PaperView(
                        paper.id(),
                        paper.title(),
                        paper.authors(),
                        paper.venue(),
                        paper.publishDate().format(DISPLAY_DATE),
                        paper.publishDate().getYear(),
                        paper.topic(),
                        paper.keywords(),
                        paper.keywordLabel(),
                        paper.fileName(),
                        "/lunwen/pdf/" + encodePath(paper.fileName()),
                        Files.isRegularFile(PAPER_DIR.resolve(paper.fileName()).normalize())
                ))
                .toList();
    }

    @GetMapping("/pdf/{fileName:.+}")
    public ResponseEntity<Resource> pdf(@PathVariable String fileName) throws Exception {
        String normalizedFileName = Path.of(fileName).getFileName().toString();
        Path pdfPath = PAPER_DIR.resolve(normalizedFileName).normalize();

        if (!pdfPath.startsWith(PAPER_DIR) || !Files.isRegularFile(pdfPath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(pdfPath);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + encodeHeader(normalizedFileName) + "\"")
                .body(resource);
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodeHeader(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record Paper(
            String id,
            String title,
            String authors,
            String venue,
            LocalDate publishDate,
            String topic,
            List<String> keywords,
            String keywordLabel,
            String fileName
    ) {
    }

    public record PaperView(
            String id,
            String title,
            String authors,
            String venue,
            String publishDate,
            int year,
            String topic,
            List<String> keywords,
            String keywordLabel,
            String fileName,
            String pdfUrl,
            boolean pdfAvailable
    ) {
    }
}
