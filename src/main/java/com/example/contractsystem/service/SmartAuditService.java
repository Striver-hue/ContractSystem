package com.example.contractsystem.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class SmartAuditService {

    private static final String HOST_DIR = "D:\\Shi\\dockerData\\SmartAudit\\input";
    private static final String CONTAINER_NAME = "smartaudit-container";
    private static final String HOST_DIR_OUT = "D:\\Shi\\dockerData\\SmartAudit\\output";   // 服务器目录

    public static String runSmartAudit(MultipartFile file) throws Exception {

        // 1️⃣ 保存文件
        String fileName = file.getOriginalFilename();
        Path filePath = Paths.get(HOST_DIR, fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 2️⃣ 读取 .sol 文件内容（替代 $(cat xxx.sol)）
        String fileContent = Files.readString(filePath);

        // ⚠️ 避免命令行参数爆炸（推荐压缩或限制大小）
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
}