package com.example.contractsystem.service;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;

@Service
public class SlitherService {

    private static final String HOST_DIR = "D:\\Shi\\dockerData\\Slither\\input";   // 服务器目录
    private static final String CONTAINER_NAME = "slither-container";
    private static final String HOST_DIR_OUT = "D:\\Shi\\dockerData\\Slither\\output";   // 服务器目录

    public static String runSlither(MultipartFile file) throws Exception {

        // 1️⃣ 保存文件到宿主机
        String fileName = file.getOriginalFilename();
        Path filePath = Paths.get(HOST_DIR, fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 2️⃣ 输出文件路径
        String outputFile = fileName.replace(".sol", ".json");

        // 3️⃣ docker exec 命令
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec", CONTAINER_NAME,
                "slither", "/input/" + fileName,
                "--json", "/output/" + outputFile
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 4️⃣ 打印日志（可选）
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );

        StringBuilder logs = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            logs.append(line).append("\n");
        }
        int exitCode = process.waitFor();

        // 5️⃣ 读取 JSON 结果
        Path outputPath = Paths.get(HOST_DIR_OUT, outputFile);
        String jsonResult = Files.readString(outputPath);
        return "{ \"exitCode\": " + exitCode + ",\"outputPath\": " + outputPath + ", \"result\": " + jsonResult + " }";
    }
}