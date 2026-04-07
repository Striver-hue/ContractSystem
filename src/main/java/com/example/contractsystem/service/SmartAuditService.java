package com.example.contractsystem.service;

import com.example.contractsystem.entity.User;
import com.example.contractsystem.repository.UserRepository;
import com.example.contractsystem.utils.SmartAuditParser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.nio.file.*;

@Service
public class SmartAuditService {

    private final UserRepository userRepository;

    public SmartAuditService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private static final String HOST_DIR = "D:\\Shi\\dockerData\\SmartAudit\\input";
    private static final String CONTAINER_NAME = "smartaudit-container";
    private static final String HOST_DIR_OUT = "D:\\Shi\\dockerData\\SmartAudit\\output";   // 服务器目录

    public String runSmartAudit1(MultipartFile file, Map<String, Object> config) throws Exception {

        String username = config.get("username").toString();
        String config_ = config.get("config") == null ? "SmartContractBA" : config.get("config").toString();
        String model = config.get("model") == null ? "GPT_4_O_MINI" : config.get("model").toString();
        String api_key = config.get("api_key") == null ? "" : config.get("api_key").toString();

        if (username == null) {
            throw new RuntimeException("请先登录");
        }

        Optional<User> user = userRepository.findByUsername(username);
        User u = user.orElse(null);

        if (u != null && !"SUPER_ADMIN".equals(u.getRole())) {
            throw new RuntimeException("权限不够");
        }
        String HOST_DIR_ = HOST_DIR + "\\" + username;
        String HOST_DIR_OUT_ = HOST_DIR_OUT + "\\" + username;
        // 1️⃣ 保存文件
        String fileName = file.getOriginalFilename();
        Path filePath = Paths.get(HOST_DIR_, fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 2️⃣ 读取 .sol 文件内容（替代 $(cat xxx.sol)）
        String fileContent = Files.readString(filePath);

        // 简单处理换行（否则容易炸）
        fileContent = fileContent.replace("\"", "\\\"");

        // 3️⃣ 构建 docker exec 命令
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec",
                CONTAINER_NAME,
                "python3", "run.py",
                "--org", "",
                "--config", config_,
                "--task", fileContent,
                "--name", "",
                "--model", model
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 4️⃣ 读取输出日志（就是分析结果）
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );

        StringBuilder logs = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            logs.append(line).append("\n");
        }

        int exitCode = process.waitFor();

        return "{ \"exitCode\": " + exitCode + ", \"output\": \"" + logs.toString().replace("\"", "\\\"") + "\" }";
    }

    public String runSmartAudit(MultipartFile file) throws Exception {
        // 1️⃣ 保存文件
        String fileName = file.getOriginalFilename();
        Path filePath = Paths.get(HOST_DIR, fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 2️⃣ 读取 .sol 文件内容（替代 $(cat xxx.sol)）
        String fileContent = Files.readString(filePath);

        // 简单处理换行（否则容易炸）
        fileContent = fileContent.replace("\"", "\\\"");

        // 3️⃣ 构建 docker exec 命令
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec",
                CONTAINER_NAME,
                "python3", "run.py",
                "--org", "",
                "--config", "SmartContractBA",
                "--task", fileContent,
                "--name", "",
                "--model", "GPT_4_O_MINI"
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 4️⃣ 读取输出日志（就是分析结果）
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );

        StringBuilder logs = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            logs.append(line).append("\n");
        }

        int exitCode = process.waitFor();

        return "{ \"exitCode\": " + exitCode + ", \"output\": \"" + logs.toString().replace("\"", "\\\"") + "\" }";
    }

    public String getBAList() throws Exception {
        String csvFilePath = "D:\\Shi\\code\\ContractSystem\\data\\GPT4_Labeled_BA_vulnerability_result.csv";
        return SmartAuditParser.getList(csvFilePath);
    }

    public String getTAList() throws Exception {
        String csvFilePath = "D:\\Shi\\code\\ContractSystem\\data\\GPT4_Labeled_TA_vulnerability_result.csv";
        return SmartAuditParser.getList(csvFilePath);
    }

    public static String getBAExample(String inputId) {
        String csvFilePath = "D:\\Shi\\code\\ContractSystem\\data\\GPT4_Labeled_BA_vulnerability_result.csv";
        String folderPath = "D:\\Shi\\code\\ContractSystem\\data\\BA\\Labeled_GPT4";
        Map<String, String> ans = new HashMap<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvFilePath), StandardCharsets.UTF_8))) {

            String line;
            boolean isHeader = true;
            int fileNameIndex = -1;
            int seminarIndex = -1;

            StringBuilder recordBuffer = new StringBuilder(); // 用于拼接多行记录
            int quoteCount = 0; // 统计双引号数量，判断记录是否完整

            while ((line = br.readLine()) != null) {
                if (recordBuffer.length() > 0) {
                    recordBuffer.append("\n"); // 保留换行符
                }
                recordBuffer.append(line);

                // 统计双引号数量
                for (char c : line.toCharArray()) {
                    if (c == '"') quoteCount++;
                }

                // 如果双引号是偶数，说明记录完整
                if (quoteCount % 2 != 0) continue;

                // 处理完整记录
                String record = recordBuffer.toString();
                recordBuffer.setLength(0); // 清空缓冲
                quoteCount = 0;

                // 按逗号分列，但保留双引号内的逗号
                String[] cols = record.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                for (int i = 0; i < cols.length; i++) {
                    cols[i] = cols[i].trim().replaceAll("^\"|\"$", "");
                }

                // 解析表头
                if (isHeader) {
                    for (int i = 0; i < cols.length; i++) {
                        if ("File Name".equalsIgnoreCase(cols[i])) fileNameIndex = i;
                        if ("Seminar Conclusion".equalsIgnoreCase(cols[i])) seminarIndex = i;
                        System.out.println("Header col[" + i + "]: '" + cols[i] + "'");
                    }
                    System.out.println("fileNameIndex: " + fileNameIndex + ", seminarIndex: " + seminarIndex);
                    isHeader = false;
                    continue;
                }

                if (fileNameIndex == -1 || seminarIndex == -1 || fileNameIndex >= cols.length) continue;

                String fileName = cols[fileNameIndex];
                String seminarConclusion = seminarIndex < cols.length ? cols[seminarIndex] : "";

                // CSV 匹配
                if (fileName.contains(inputId)) {
                    System.out.println("匹配到文件: " + fileName);

                    String prefix = fileName.replaceAll("_RealWord_\\d+\\.log$", "");

                    // 文件夹查找：第一个匹配前缀的 .log 文件
                    File folder = new File(folderPath);
                    if (folder.isDirectory()) {
                        File[] files = folder.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".log"));
                        if (files != null && files.length > 0) {
                            System.out.println("找到文件: " + files[0].getAbsolutePath());
                            String sol = extractSolCode(files[0].getAbsolutePath());
                            System.out.println(sol);
                            ans.put("code", sol);
                        } else {
                            System.out.println("文件夹中未找到匹配文件");
                        }
                    }
                    ans.put("conclusion", seminarConclusion);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ans);
    }

    public static String getTAExample(String inputId) {
        String csvFilePath = "D:\\Shi\\code\\ContractSystem\\data\\GPT4_Labeled_TA_vulnerability_result.csv";
        String folderPath = "D:\\Shi\\code\\ContractSystem\\data\\TA\\Labeled_GPT4";
        Map<String, Object> ans = new HashMap<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvFilePath), StandardCharsets.UTF_8))) {

            String line;
            boolean isHeader = true;
            int fileNameIndex = -1;
            int seminarIndex = -1;

            StringBuilder recordBuffer = new StringBuilder(); // 用于拼接多行记录
            int quoteCount = 0; // 统计双引号数量，判断记录是否完整

            while ((line = br.readLine()) != null) {
                if (recordBuffer.length() > 0) {
                    recordBuffer.append("\n"); // 保留换行符
                }
                recordBuffer.append(line);

                // 统计双引号数量
                for (char c : line.toCharArray()) {
                    if (c == '"') quoteCount++;
                }

                // 如果双引号是偶数，说明记录完整
                if (quoteCount % 2 != 0) continue;

                // 处理完整记录
                String record = recordBuffer.toString();
                recordBuffer.setLength(0); // 清空缓冲
                quoteCount = 0;

                // 按逗号分列，但保留双引号内的逗号
                String[] cols = record.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                for (int i = 0; i < cols.length; i++) {
                    cols[i] = cols[i].trim().replaceAll("^\"|\"$", "");
                }

                // 解析表头
                if (isHeader) {
                    for (int i = 0; i < cols.length; i++) {
                        if ("File Name".equalsIgnoreCase(cols[i])) fileNameIndex = i;
                        if ("Vulnerability Type".equalsIgnoreCase(cols[i])) seminarIndex = i;
                        System.out.println("Header col[" + i + "]: '" + cols[i] + "'");
                    }
                    System.out.println("fileNameIndex: " + fileNameIndex + ", Vulnerability Type: " + seminarIndex);
                    isHeader = false;
                    continue;
                }

                if (fileNameIndex == -1 || seminarIndex == -1 || fileNameIndex >= cols.length) continue;

                String fileName = cols[fileNameIndex];
                String seminarConclusion = seminarIndex < cols.length ? cols[seminarIndex] : "";
                // CSV 匹配
                if (fileName.contains(inputId)) {
                    System.out.println("匹配到文件: " + fileName);

                    String prefix = fileName.replaceAll("_Labeled_\\d+\\.log$", "");

                    // 文件夹查找：第一个匹配前缀的 .log 文件
                    File folder = new File(folderPath);
                    if (folder.isDirectory()) {
                        File[] files = folder.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".log"));
                        if (files != null && files.length > 0) {
                            System.out.println("找到文件: " + files[0].getAbsolutePath());
                            String sol = extractSolCode(files[0].getAbsolutePath());
                            System.out.println(sol);
                            ans.put("code", sol);
                        } else {
                            System.out.println("文件夹中未找到匹配文件");
                        }
                    }
                    String[] split = seminarConclusion.split("<INFO>");
                    List<String> cleaned = Arrays.asList(Arrays.stream(split)
                            .map(String::trim)
                            .map(s -> s.replaceAll(",$", ""))
                            .filter(s -> !s.isEmpty())
                            .toArray(String[]::new));
                    ans.put("conclusion", cleaned);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ans);
    }

    public static String extractSolCode(String logFilePath) {
        StringBuilder sb = new StringBuilder();
        boolean isCollecting = false;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(logFilePath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();

                // 检测开始标记
                if (line.startsWith("**task_prompt**:")) {
                    isCollecting = true;
                    continue; // 不包含开始标记本身
                }

                // 检测结束标记
                if (line.startsWith("**project_name**:")) {
                    isCollecting = false;
                    break; // 找到结束标记就停止
                }

                // 收集中间内容
                if (isCollecting) {
                    sb.append(line).append("\n");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return sb.toString().trim(); // 去掉首尾多余换行
    }
}