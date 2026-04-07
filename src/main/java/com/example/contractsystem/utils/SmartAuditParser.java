package com.example.contractsystem.utils;

import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SmartAuditParser {
    // 主漏洞类型，可扩展
    private static final List<String> MAIN_TYPES = Arrays.asList(
            "bad_randomness",
            "reentrancy",
            "arithmetic",
            "tx_origin",
            "unchecked_send",
            "time_manipulation",
            "unsafe_delegatecall",
            "unsafe_suicide",
            "TOD",
            "gasless_send",
            "safecontract"
    );

    public static String getList(String csvFilePath) {
        Map<String, List<String>> clusters = new HashMap<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvFilePath), StandardCharsets.UTF_8))) {

            String line;
            boolean isHeader = true;
            int fileNameIndex = -1;

            while ((line = br.readLine()) != null) {
                String[] cols = line.split(",", -1);

                // 找 File Name 列
                if (isHeader) {
                    for (int i = 0; i < cols.length; i++) {
                        if ("File Name".equalsIgnoreCase(cols[i].trim())) {
                            fileNameIndex = i;
                            break;
                        }
                    }
                    isHeader = false;
                    continue;
                }

                if (fileNameIndex == -1 || fileNameIndex >= cols.length) continue;

                String fileName = cols[fileNameIndex].trim();

                if (!isValidFileName(fileName)) continue;

                ParsedResult parsed = parseFileName(fileName);
                if (parsed == null) continue;

                String mainType = getMainType(parsed.label);

                clusters.computeIfAbsent(mainType, k -> new ArrayList<>()).add(parsed.id);
            }

            ObjectMapper mapper = new ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(clusters);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private static boolean isValidFileName(String fileName) {
        return fileName != null && fileName.endsWith(".log") && fileName.contains("_");
    }

    private static ParsedResult parseFileName(String fileName) {
        try {
            fileName = fileName.substring(0, fileName.length() - 4); // 去掉 .log
            int last = fileName.lastIndexOf("_");
            int secondLast = fileName.lastIndexOf("_", last - 1);
            if (secondLast == -1) return null;

            String id = fileName.substring(secondLast + 1);
            String prefix = fileName.substring(0, secondLast);
            String label = removePrefix(prefix);
            return new ParsedResult(label, id);
        } catch (Exception e) {
            return null;
        }
    }

    private static String removePrefix(String prefix) {
        int first = prefix.indexOf("_");
        int second = prefix.indexOf("_", first + 1);
        return second != -1 ? prefix.substring(second + 1) : prefix;
    }

    private static String getMainType(String label) {
        for (String type : MAIN_TYPES) {
            if (label.contains(type)) return type;
        }
        return "unknown";
    }

    static class ParsedResult {
        String label;
        String id;

        ParsedResult(String label, String id) {
            this.label = label;
            this.id = id;
        }
    }

}