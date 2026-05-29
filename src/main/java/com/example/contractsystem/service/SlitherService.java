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

    private static final String HOST_DIR = "D:\\ContractSystem\\dockerData\\Slither\\input";   // 服务器目录
    private static final String CONTAINER_NAME = "slither-container";
    private static final String HOST_DIR_OUT = "D:\\ContractSystem\\dockerData\\Slither\\output";   // 服务器目录



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

        // 3️⃣ 提取合约 Solidity 版本，并选择合适的 solc 编译器
        String solVersion = extractVersion(filePath.toString());
        // 容器中已安装的 solc 版本：全局 0.8.24，solc-select 中 0.4.26
        // 对于 0.4.x 合约，使用 0.4.26 编译（兼容 ^0.4.18 ~ ^0.4.26）
        // 对于 0.5.x+ 合约，使用默认全局 solc（0.8.24）
        String solcArg = "/root/.solc-select/versions/0.4.26/solc";
        boolean useCustomSolc = solVersion.startsWith("0.4.");

        // 4️⃣ 构建 docker exec 命令
        List<String> command = new java.util.ArrayList<>();
        command.add("docker");
        command.add("exec");
        command.add(CONTAINER_NAME);
        command.add("slither");
        command.add("/input/" + fileName);
        command.add("--json");
        command.add("/output/" + outputFile);
        if (useCustomSolc) {
            command.add("--solc");
            command.add(solcArg);
        }

        ProcessBuilder pb = new ProcessBuilder(command);

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 4️⃣ 读取日志（含 stdout + stderr）
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );
        StringBuilder logs = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            logs.append(line).append("\n");
        }
        int exitCode = process.waitFor();

        System.out.println("[Slither] exitCode=" + exitCode);
        System.out.println("[Slither] logs:\n" + logs);

        // 5️⃣ 读取 JSON 结果
        Path outputPath = Paths.get(HOST_DIR_OUT, outputFile);
        if (!Files.exists(outputPath)) {
            throw new RuntimeException("Slither 执行失败，未生成输出文件。退出码：" + exitCode + "\n日志：" + logs);
        }
        String jsonResult = Files.readString(outputPath);
        return jsonResult;
    }
}