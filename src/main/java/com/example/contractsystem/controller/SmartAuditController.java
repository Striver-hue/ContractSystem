package com.example.contractsystem.controller;

import com.example.contractsystem.service.SlitherService;
import com.example.contractsystem.service.SmartAuditService;
import org.apache.commons.csv.QuoteMode;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.poi.ss.usermodel.*;
import java.io.FileOutputStream;

@RestController
@RequestMapping("/smartaudit")
public class SmartAuditController {
    private final SmartAuditService smartAuditService;

    public SmartAuditController(SmartAuditService smartAuditService) {
        this.smartAuditService = smartAuditService;
    }

    @PostMapping("/analyze1")
    public ResponseEntity<?> analyze1(@RequestPart("file") MultipartFile file,
                                     @RequestPart("config") Map<String, Object> config) {
        try {
            String result = smartAuditService.runSmartAudit1(file, config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestPart("file") MultipartFile file) {
        try {
            String result = smartAuditService.runSmartAudit(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @PostMapping("/getBAList")
    public ResponseEntity<?> getBAList() {
        try {
            String result = smartAuditService.getBAList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @PostMapping("/getTAList")
    public ResponseEntity<?> getTAList() {
        try {
            String result = smartAuditService.getTAList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }
    @PostMapping("/getBAExample")
    public ResponseEntity<?> getBAExample(@RequestParam(value = "filename", defaultValue = "RealWord_20240812223706") String filename) {
        try {
            String result = smartAuditService.getBAExample(filename.replace("RealWorld","RealWord"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @PostMapping("/getTAExample")
    public ResponseEntity<?> getTAExample(@RequestParam(value = "filename", defaultValue = "Labeled_20240813213630") String filename) {
        try {
            String result = smartAuditService.getTAExample(filename);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }


    @PostMapping("/exportBAExamplesCsv")
    public ResponseEntity<?> exportBAExamplesCsv() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            String baListJson = smartAuditService.getBAList();

            JsonNode rootNode = objectMapper.readTree(baListJson);

            Set<String> filenames = new LinkedHashSet<>();

            rootNode.fields().forEachRemaining(entry -> {
                JsonNode valueNode = entry.getValue();

                if (valueNode != null && valueNode.isArray()) {
                    for (JsonNode item : valueNode) {
                        if (item != null && !item.isNull()) {
                            filenames.add(item.asText());
                        }
                    }
                }
            });

            Path outputPath = Paths.get("ba_examples_2.0.csv");

            try (
                    BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
                    CSVPrinter csvPrinter = new CSVPrinter(
                            writer,
                            CSVFormat.EXCEL.builder()
                                    .setHeader("name", "code", "conclusion")
                                    .setQuoteMode(QuoteMode.ALL)
                                    .setRecordSeparator("\r\n")
                                    .build()
                    )
            ) {
                for (String name : filenames) {
                    if (name == null || name.trim().isEmpty()) {
                        continue;
                    }

                    try {
                        String fixedName = name.replace("RealWorld", "RealWord");

                        String exampleJson = smartAuditService.getBAExample(fixedName);

                        JsonNode exampleNode = objectMapper.readTree(exampleJson);

                        String code = exampleNode.path("code").asText("");
                        String conclusion = exampleNode.path("conclusion").asText("");
                        code = formatSolidityCode(code);
                        code = code
                                .replace("\r\n", "\\n")
                                .replace("\n", "\\n")
                                .replace("\r", "\\n");
                        csvPrinter.printRecord(name, code, conclusion);

                    } catch (Exception e) {
                        csvPrinter.printRecord(name, "", "ERROR: " + e.getMessage());
                    }
                }
            }

            return ResponseEntity.ok("CSV saved to: " + outputPath.toAbsolutePath());

        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @PostMapping("/exportBAExamplesXlsx")
    public ResponseEntity<?> exportBAExamplesXlsx() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            String baListJson = smartAuditService.getBAList();

            JsonNode rootNode = objectMapper.readTree(baListJson);

            Set<String> filenames = new LinkedHashSet<>();

            rootNode.fields().forEachRemaining(entry -> {
                JsonNode valueNode = entry.getValue();

                if (valueNode != null && valueNode.isArray()) {
                    for (JsonNode item : valueNode) {
                        if (item != null && !item.isNull()) {
                            filenames.add(item.asText());
                        }
                    }
                }
            });

            Path outputPath = Paths.get("ba_examples.xlsx");

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("BA Examples");

                // 表头样式
                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);

                // code 单元格样式：允许换行
                CellStyle codeStyle = workbook.createCellStyle();
                codeStyle.setWrapText(true);
                codeStyle.setVerticalAlignment(VerticalAlignment.TOP);

                // 普通单元格样式
                CellStyle normalStyle = workbook.createCellStyle();
                normalStyle.setVerticalAlignment(VerticalAlignment.TOP);
                normalStyle.setWrapText(true);

                // 创建表头
                Row headerRow = sheet.createRow(0);

                Cell nameHeader = headerRow.createCell(0);
                nameHeader.setCellValue("name");
                nameHeader.setCellStyle(headerStyle);

                Cell codeHeader = headerRow.createCell(1);
                codeHeader.setCellValue("code");
                codeHeader.setCellStyle(headerStyle);

                Cell conclusionHeader = headerRow.createCell(2);
                conclusionHeader.setCellValue("conclusion");
                conclusionHeader.setCellStyle(headerStyle);

                int rowIndex = 1;

                for (String name : filenames) {
                    if (name == null || name.trim().isEmpty()) {
                        continue;
                    }

                    Row row = sheet.createRow(rowIndex++);

                    try {
                        String fixedName = name.replace("RealWorld", "RealWord");

                        String exampleJson = smartAuditService.getBAExample(fixedName);

                        JsonNode exampleNode = objectMapper.readTree(exampleJson);

                        String code = exampleNode.path("code").asText("");
                        String conclusion = exampleNode.path("conclusion").asText("");

                        // 格式化 Solidity
                        code = formatSolidityCode(code);

                        Cell nameCell = row.createCell(0);
                        nameCell.setCellValue(name);
                        nameCell.setCellStyle(normalStyle);

                        Cell codeCell = row.createCell(1);
                        codeCell.setCellValue(code);
                        codeCell.setCellStyle(codeStyle);

                        Cell conclusionCell = row.createCell(2);
                        conclusionCell.setCellValue(conclusion);
                        conclusionCell.setCellStyle(normalStyle);

                    } catch (Exception e) {
                        Cell nameCell = row.createCell(0);
                        nameCell.setCellValue(name);
                        nameCell.setCellStyle(normalStyle);

                        Cell codeCell = row.createCell(1);
                        codeCell.setCellValue("");
                        codeCell.setCellStyle(codeStyle);

                        Cell conclusionCell = row.createCell(2);
                        conclusionCell.setCellValue("ERROR: " + e.getMessage());
                        conclusionCell.setCellStyle(normalStyle);
                    }
                }

                // 设置列宽
                sheet.setColumnWidth(0, 35 * 256);   // name
                sheet.setColumnWidth(1, 100 * 256);  // code
                sheet.setColumnWidth(2, 80 * 256);   // conclusion

                // 冻结表头
                sheet.createFreezePane(0, 1);

                try (FileOutputStream fileOut = new FileOutputStream(outputPath.toFile())) {
                    workbook.write(fileOut);
                }
            }

            return ResponseEntity.ok("XLSX saved to: " + outputPath.toAbsolutePath());

        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
    private String formatSolidityCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "";
        }

        Path tempFile = null;

        try {
            tempFile = Files.createTempFile("solidity_code_", ".sol");
            Files.writeString(tempFile, code, StandardCharsets.UTF_8);

            String npx = isWindows() ? "npx.cmd" : "npx";

            ProcessBuilder processBuilder = new ProcessBuilder(
                    npx,
                    "prettier",
                    "--plugin=prettier-plugin-solidity",
                    tempFile.toAbsolutePath().toString()
            );

            // 这里建议设置成你的项目根目录，也就是 node_modules 所在目录
            processBuilder.directory(new java.io.File(System.getProperty("user.dir")));

            Process process = processBuilder.start();

            String formattedCode = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

            int exitCode = process.waitFor();

            if (exitCode == 0 && formattedCode != null && !formattedCode.trim().isEmpty()) {
                return formattedCode;
            }

            System.err.println("Solidity format failed: " + error);

            // 格式化失败时不要影响导出，返回原始代码
            return code;

        } catch (Exception e) {
            System.err.println("Solidity format exception: " + e.getMessage());
            return code;
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}