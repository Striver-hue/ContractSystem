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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class SmartAuditService {

    private final UserRepository userRepository;

    public SmartAuditService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private static final Path SMARTAUDIT_DIR = Paths.get(System.getProperty("user.dir"), "docker", "smartaudit")
            .toAbsolutePath()
            .normalize();
    private static final String HOST_DIR = SMARTAUDIT_DIR.resolve("input").toString();
    private static final String CONTAINER_NAME = "smartaudit-container";
    private static final String IMAGE_NAME = "shixiaolong0523/contractsystem:smartaudit-container";
    private static final String HOST_DIR_OUT = SMARTAUDIT_DIR.resolve("output").toString();
    private static final DateTimeFormatter JOB_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String runSmartAudit1(MultipartFile file, Map<String, Object> config) throws Exception {

        String username = config.get("username") == null ? null : config.get("username").toString();
        String config_ = config.get("config") == null ? "SmartContractBA" : config.get("config").toString();
        String model = config.get("model") == null ? "GPT_4_O_MINI" : config.get("model").toString();

        if (username == null) {
            throw new RuntimeException("请先登录");
        }

        Optional<User> user = userRepository.findByUsername(username);
        User u = user.orElse(null);

        if (u != null && !"SUPER_ADMIN".equals(u.getRole())) {
            throw new RuntimeException("权限不够");
        }
        return runSmartAuditInternal(file, config_, model, username);
    }

    public String runSmartAudit(MultipartFile file) throws Exception {
        return runSmartAuditInternal(file, "SmartContractBA", "GPT_4_O_MINI", null);
    }

    private String runSmartAuditInternal(MultipartFile file, String configName, String model, String username) throws Exception {
        ensureHostDirectories();
        ensureContainerReady();

        String fileName = sanitizeFileName(file.getOriginalFilename());
        if (!fileName.endsWith(".sol") && !fileName.endsWith(".txt")) {
            throw new RuntimeException("SmartAudit 仅支持 .sol 或 .txt 文件");
        }

        String baseName = fileName.replaceFirst("\\.[^.]+$", "");
        String jobPrefix = username == null || username.isBlank() ? baseName : username + "_" + baseName;
        String jobId = sanitizeFileName(jobPrefix) + "_" + LocalDateTime.now().format(JOB_TIME_FORMAT);

        Path inputDir = Paths.get(HOST_DIR, jobId);
        Path outputDir = Paths.get(HOST_DIR_OUT, jobId);
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        Path inputPath = inputDir.resolve(fileName);
        Files.copy(file.getInputStream(), inputPath, StandardCopyOption.REPLACE_EXISTING);

        String outputFileName = baseName + ".txt";
        Path outputPath = outputDir.resolve(outputFileName);
        String containerInputPath = "/input/" + jobId + "/" + fileName;

        String runner = String.join("\n",
                "import pathlib, runpy, sys",
                "input_path = pathlib.Path(sys.argv[1])",
                "task = input_path.read_text(encoding='utf-8')",
                "sys.argv = ['run.py', '--org', sys.argv[2], '--config', sys.argv[3], '--task', task, '--name', sys.argv[4], '--model', sys.argv[5]]",
                "runpy.run_path('run.py', run_name='__main__')"
        );

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec",
                CONTAINER_NAME,
                "python3", "-c", runner,
                containerInputPath,
                jobId,
                configName,
                baseName,
                model
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String logs = readProcessOutput(process);
        int exitCode = process.waitFor();

        Files.writeString(outputPath, logs, StandardCharsets.UTF_8);

        System.out.println("[SmartAudit] jobId=" + jobId);
        System.out.println("[SmartAudit] exitCode=" + exitCode);
        System.out.println("[SmartAudit] input=" + inputPath);
        System.out.println("[SmartAudit] output=" + outputPath);

        if (exitCode != 0) {
            throw new RuntimeException("SmartAudit 执行失败，退出码：" + exitCode + "\n日志：\n" + logs);
        }

        return Files.readString(outputPath, StandardCharsets.UTF_8);
    }

    private void ensureHostDirectories() throws IOException {
        Files.createDirectories(Paths.get(HOST_DIR));
        Files.createDirectories(Paths.get(HOST_DIR_OUT));
    }

    private void ensureContainerReady() throws Exception {
        Process inspect = new ProcessBuilder(
                "docker", "inspect", "-f", "{{.State.Running}}", CONTAINER_NAME
        ).redirectErrorStream(true).start();
        String inspectOutput = readProcessOutput(inspect).trim();
        int inspectCode = inspect.waitFor();

        if (inspectCode == 0) {
            if ("true".equalsIgnoreCase(inspectOutput)) {
                return;
            }
            Process start = new ProcessBuilder("docker", "start", CONTAINER_NAME)
                    .redirectErrorStream(true)
                    .start();
            String startOutput = readProcessOutput(start);
            int startCode = start.waitFor();
            if (startCode != 0) {
                throw new RuntimeException("SmartAudit 容器启动失败：\n" + startOutput);
            }
            return;
        }

        Process run = new ProcessBuilder(
                "docker", "run", "-d",
                "--name", CONTAINER_NAME,
                "-v", HOST_DIR + ":/input",
                "-v", HOST_DIR_OUT + ":/output",
                IMAGE_NAME,
                "tail", "-f", "/dev/null"
        ).redirectErrorStream(true).start();
        String runOutput = readProcessOutput(run);
        int runCode = run.waitFor();
        if (runCode != 0) {
            throw new RuntimeException("SmartAudit 容器创建失败，请确认镜像已加载："
                    + IMAGE_NAME + "\n日志：\n" + runOutput);
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder logs = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logs.append(line).append("\n");
            }
        }
        return logs.toString();
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new RuntimeException("文件名不能为空");
        }
        String name = Paths.get(fileName).getFileName().toString();
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public String getBAList() throws Exception {
        String csvFilePath = "D:\\Shi\\code\\ContractSystem\\data\\GPT4_Labeled_BA_vulnerability_result.csv";
        return SmartAuditParser.getList(csvFilePath).replaceAll("RealWord","RealWorld");
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
                String startMarker = "**task_prompt**:";
                String endMarker = "**project_name**:";
                // 检测开始标记
                if (line.startsWith(startMarker)) {
                    isCollecting = true;

                    // 保留 **task_prompt**: 后面的内容
                    String afterMarker = line.substring(startMarker.length()).trim();

                    if (!afterMarker.isEmpty()) {
                        sb.append(afterMarker).append("\n");
                    }

                    continue;
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
