package com.example.contractsystem.service;
import com.example.contractsystem.entity.User;
import com.example.contractsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SlitherService {

    private final UserRepository userRepository;
    public SlitherService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private static final String HOST_DIR = "D:\\Shi\\dockerData\\Slither\\input";   // 服务器目录
    private static final String CONTAINER_NAME = "slither-container";
    private static final String HOST_DIR_OUT = "D:\\Shi\\dockerData\\Slither\\output";   // 服务器目录



    public String runSlither1(MultipartFile file, Map<String, Object> config) throws Exception {

        String username = config.get("username").toString();

        if (username == null) {
            throw new RuntimeException("请先登录");
        }

        Optional<User> user = userRepository.findByUsername(username);
        User u = user.orElse(null);

        if (u != null && !"SUPER_ADMIN".equals(u.getRole())) {
            throw new RuntimeException("权限不够");
        }

        String HOST_DIR_ = HOST_DIR +"\\" +username;
        String HOST_DIR_OUT_ = HOST_DIR_OUT +"\\" +username;

        // 1️⃣ 保存文件到宿主机
        String fileName = file.getOriginalFilename();
        Path filePath = Paths.get(HOST_DIR_, fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 2️⃣ 输出文件路径
        String outputFile = fileName.replace(".sol", ".json");

        // 3️⃣ docker exec 命令
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec", CONTAINER_NAME,
                "slither", "/input/" + username + "/" + fileName,
                "--json", "/output/" + username+ "/" + outputFile
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
        Path outputPath = Paths.get(HOST_DIR_OUT_, outputFile);
        String jsonResult = Files.readString(outputPath);
        return jsonResult;
    }

    public static String extractVersion(String contractPath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(contractPath)));

        // 正则匹配 pragma solidity 版本
        Pattern pattern = Pattern.compile("pragma\\s+solidity\\s+[\\^~]?(\\d+\\.\\d+\\.\\d+)");
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String version = matcher.group(1);
            // 提取主版本号.次版本号 (如 0.4.18 -> 0.4.18)
            return version;
        }

        // 如果没有找到，返回默认版本
        return "0.8.34";
    }
    public String runSlither(MultipartFile file) throws Exception {

        // 1️⃣ 保存文件到宿主机
        String fileName = file.getOriginalFilename();
        Path filePath = Paths.get(HOST_DIR, fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 2️⃣ 输出文件路径
        String outputFile = fileName.replace(".sol", ".json");


        String solVersion = extractVersion(filePath.toString());
        String solcPath = "/root/.solc-select/versions/"+solVersion+"/solc";
        // 3️⃣ docker exec 命令
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec", CONTAINER_NAME,
                "slither", "/input/" + fileName,
                "--json", "/output/" + outputFile,
                "--solc", solcPath
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
        return jsonResult;
    }
}