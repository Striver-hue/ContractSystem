# ChainAuditPro — 一站式智能合约安全审计平台

## 1. 项目基础信息

### 项目名称
**ContractSystem (ChainAuditPro)** — 区块链智能合约安全审计与漏洞检测系统

### 项目简介
本项目是一个**前后端分离**的智能合约安全审计平台，核心功能包括：
- **Slither 静态分析**：通过 Docker 容器调用 Slither 工具对 Solidity 智能合约进行漏洞检测，返回结构化 JSON 报告
- **SmartAudit AI 审计**：通过 Docker 容器调用 Python 脚本 + LLM（GPT-4o-mini）进行智能合约语义级漏洞分析
- **用户认证与权限管理**：基于 Spring Security 的注册/登录系统，支持 USER / ADMIN / SUPER_USER / SUPER_ADMIN 四级角色
- **漏洞数据可视化**：从 CSV 数据源加载链上攻击事件、BA/TA 漏洞标注结果，前端以饼图、卡片列表形式展示
- **WebSocket 通信**：内置 OkHttp WebSocket 客户端，可与 FastAPI 后端实时通信

### 前后端分离架构说明
| 层级 | 技术 | 职责 |
|------|------|------|
| **前端** | 纯 HTML + Tailwind CSS + Vanilla JS | 页面展示、文件上传、检测结果可视化 |
| **后端** | Spring Boot 4.0.3 (Java 17) | REST API、用户认证、Docker 调用编排、数据解析 |
| **检测引擎** | Docker 容器 (Slither / SmartAudit) | 实际执行合约检测任务 |
| **数据层** | MySQL + CSV 文件 | 用户数据持久化、漏洞数据集存储 |

### 项目适用场景
- 智能合约安全审计服务
- 区块链漏洞研究与教学
- DeFi 项目上线前安全检测
- 链上攻击事件数据聚合与可视化

### 核心价值
- 集成 Slither + AI 双引擎检测能力
- Docker 容器化隔离执行，安全可靠
- 角色权限分级，支持多用户协作
- 丰富的漏洞数据展示（饼图、事件卡片、分页列表）

---

## 2. 技术栈清单

### 后端技术栈
| 类别 | 技术/框架 | 版本 |
|------|-----------|------|
| 编程语言 | Java | 17 |
| 框架 | Spring Boot | 4.0.3 |
| 安全框架 | Spring Security | 4.0.3 |
| ORM / 持久层 | Spring Data JPA (Hibernate) | 随 Spring Boot |
| Web 层 | Spring WebMVC | 随 Spring Boot |
| WebSocket | Spring WebSocket + Jakarta WebSocket API | 2.1.0 |
| HTTP 客户端 | OkHttp | 4.12.0 |
| JSON 处理 | Jackson (tools.jackson) | 随 Spring Boot |
| 数据库 | MySQL | 8.x (通过 mysql-connector-j) |
| 密码加密 | BCrypt（已注释）/ NoOp | — |
| 构建工具 | Maven (mvnw wrapper) | 3.x |
| 包管理器 | Maven Wrapper (`mvnw`) | 内置 |

### 前端技术栈
| 类别 | 技术/框架 | 版本 |
|------|-----------|------|
| HTML/CSS | Tailwind CSS (CDN) | CDN 动态版本 |
| 图标 | Font Awesome | 6.4.0 |
| 图表 | Chart.js + chartjs-plugin-datalabels | CDN 最新 |
| 表格解析 | SheetJS (xlsx) | 0.18.5 |
| 框架 | 无框架，纯原生 JavaScript | — |

### 第三方依赖 / 检测工具 API
| 工具 | 用途 | 集成方式 |
|------|------|----------|
| **Slither** | Solidity 静态分析工具 | Docker 容器命令行调用 |
| **SmartAudit** | AI 驱动智能合约审计 | Docker 容器调用 Python `run.py` |
| **GPT-4o-mini** | LLM 分析引擎（SmartAudit 内部使用） | 通过 SmartAudit 容器间接调用 |

### 部署环境
| 环境 | 说明 |
|------|------|
| 操作系统 | Windows（开发）、Ubuntu 20.04/22.04（服务器部署） |
| Web 服务器 | Nginx（前端静态文件代理） |
| 容器引擎 | Docker（运行 Slither 与 SmartAudit 容器） |
| 数据库 | MySQL 8.x |
| Java 运行时 | JDK 17 |

---

## 3. 项目目录结构

```
ContractSystem/
├── .gitattributes                  # Git 换行符配置
├── .gitignore                      # Git 忽略规则
├── mvnw                            # Maven Wrapper (Linux/Mac)
├── mvnw.cmd                        # Maven Wrapper (Windows)
├── pom.xml                         # ⭐ Maven 项目配置文件（依赖、构建、插件）
├── README.md                       # 本文件
│
├── index.html                      # ⭐ 前端首页（ChainAuditPro 主页面）
├── 漏洞检测工具.html                # ⭐ 前端检测工具页（上传合约 + 查看报告）
├── 攻击事件展示.html                # 攻击事件数据可视化页
├── resource-vulnerability.html     # 漏洞知识库页面
├── resource-tool.html              # 集成工具集页面
├── slither.html                    # Slither 独立检测页（低使用频率）
├── SmartAudit_report.html          # SmartAudit 报告页（低使用频率）
│
├── data/                           # ⭐ 数据集目录
│   ├── GPT4_Labeled_BA_vulnerability_result.csv   # BA 漏洞标注结果
│   ├── GPT4_Labeled_TA_vulnerability_result.csv   # TA 漏洞标注结果
│   ├── BA/Labeled_GPT4/            # BA 漏洞样本日志文件
│   └── TA/Labeled_GPT4/            # TA 漏洞样本日志文件
│
├── excel/                          # Excel 攻击事件数据
│   ├── merged_v1.xlsx              # 攻击事件汇总表
│   ├── 检测工具.xlsx                # 检测工具配置数据
│   └── 漏洞种类.xlsx                # 漏洞种类数据
│
├── pictures/                       # 图片资源
│   └── 首屏.png                    # 首页首屏背景图
│
├── text/                           # 前端本地模拟数据目录
│   ├── reentrance.sol              # 测试用 Solidity 合约
│   ├── reentrance.json             # 测试用审计报告
│   ├── ba_examples.xlsx            # BA 示例数据
│   ├── Slither/
│   │   ├── input/                  # Slither 输入文件（.sol）
│   │   └── output/                 # Slither 输出结果（.json）
│   └── SmartAudit/
│       ├── input/                  # SmartAudit 输入文件（.sol）
│       └── output/                 # SmartAudit 输出结果（文本/json）
│
└── src/
    ├── main/
    │   ├── java/com/example/contractsystem/
    │   │   ├── ContractSystemApplication.java     # ⭐ Spring Boot 主入口
    │   │   ├── config/
    │   │   │   └── SecurityConfig.java            # ⭐ Spring Security 安全配置
    │   │   ├── controller/
    │   │   │   ├── SlitherController.java         # ⭐ Slither 检测 REST API
    │   │   │   ├── SmartAuditController.java      # ⭐ SmartAudit 检测 REST API
    │   │   │   └── UserController.java            # ⭐ 用户注册/鉴权 API
    │   │   ├── entity/
    │   │   │   └── User.java                      # 用户实体（JPA Entity）
    │   │   ├── repository/
    │   │   │   └── UserRepository.java            # 用户数据访问层
    │   │   ├── service/
    │   │   │   ├── UserService.java               # 用户业务逻辑
    │   │   │   ├── CustomUserDetailsService.java  # Spring Security 用户加载
    │   │   │   ├── SlitherService.java            # ⭐ Slither Docker 调用编排
    │   │   │   └── SmartAuditService.java         # ⭐ SmartAudit Docker 调用 + CSV 解析
    │   │   ├── utils/
    │   │   │   └── SmartAuditParser.java          # CSV 文件名解析工具
    │   │   └── websocket/
    │   │       └── WSClient.java                  # ⭐ OkHttp WebSocket 客户端
    │   └── resources/
    │       └── application.yaml                   # ⭐ Spring Boot 核心配置文件
    └── test/
        └── java/com/...（测试代码，当前为空框架）
```

---

## 4. 环境配置要求

### 4.1 后端运行所需环境

| 依赖项 | 版本/说明 |
|--------|-----------|
| **JDK** | 17 或更高（`pom.xml` 中 `java.version=17`） |
| **Maven** | 3.6+（项目内置 `mvnw` wrapper，无需手动安装） |
| **MySQL** | 8.0+，需创建数据库 `contract_system` |
| **Docker** | 需拉取并运行 Slither 与 SmartAudit 容器 |
| **操作系统** | Windows 10+ / Ubuntu 20.04+ / macOS |

### 4.2 前端运行所需环境
- 现代浏览器（Chrome / Edge / Firefox 最新版）
- 本地静态文件服务器（VS Code Live Server 或 Nginx）

### 4.3 Docker 容器准备
```bash
# 拉取 Slither 镜像（需提前准备对应的 Docker 镜像）
docker pull trailofbits/slither   # 或自定义镜像

# 运行 Slither 容器（名称必须为 slither-container）
docker run -d --name slither-container \
  -v D:\Shi\dockerData\Slither\input:/input \
  -v D:\Shi\dockerData\Slither\output:/output \
  trailofbits/slither tail -f /dev/null

# 运行 SmartAudit 容器（名称必须为 smartaudit-container）
docker run -d --name smartaudit-container \
  -v D:\Shi\dockerData\SmartAudit\input:/input \
  -v D:\Shi\dockerData\SmartAudit\output:/output \
  <smartaudit镜像> tail -f /dev/null
```

### 4.4 一键安装命令（依赖安装）

```bash
# === Ubuntu 服务器 ===
# Java 17
sudo apt update
sudo apt install -y openjdk-17-jdk

# MySQL
sudo apt install -y mysql-server
sudo systemctl start mysql
sudo systemctl enable mysql

# Docker
sudo apt install -y docker.io
sudo systemctl start docker
sudo systemctl enable docker

# Nginx
sudo apt install -y nginx
sudo systemctl start nginx
```

---

## 5. 后端运行指南

### 5.1 本地运行完整步骤

#### Step 1：克隆代码
```bash
git clone https://github.com/Striver-hue/ContractSystem.git
cd ContractSystem
```

#### Step 2：配置 MySQL 数据库
```sql
-- 登录 MySQL
mysql -u root -p

-- 创建数据库
CREATE DATABASE IF NOT EXISTS contract_system DEFAULT CHARACTER SET utf8mb4;
```

#### Step 3：修改配置文件（见第 6 节）
编辑 `src/main/resources/application.yaml`

#### Step 4：构建并启动
```bash
# Windows
mvnw.cmd clean package -DskipTests
mvnw.cmd spring-boot:run

# Linux / Mac
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

### 5.2 后端启动命令
```bash
# 开发模式（热重载需 IDE 支持）
./mvnw spring-boot:run

# 生产模式（打包运行）
./mvnw clean package -DskipTests
java -jar target/ContractSystem-0.0.1-SNAPSHOT.jar
```

### 5.3 后端停止命令
```bash
# 前台运行：Ctrl + C

# 后台运行：查找进程并终止
# Linux
ps aux | grep ContractSystem
kill -9 <PID>

# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### 5.4 重启命令
```bash
# 先停止，再启动
kill -9 $(ps aux | grep ContractSystem | grep -v grep | awk '{print $2}')
./mvnw spring-boot:run &
```

### 5.5 运行成功验证
```bash
# 测试后端是否启动
curl http://localhost:8080/actuator/health

# 或者直接访问浏览器
# http://localhost:8080/bac/any
```

---

## 6. 核心配置项说明

### 6.1 IP 和端口配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| **后端绑定端口** | `8080` | Spring Boot 默认端口 |
| **后端绑定地址** | `0.0.0.0` | 默认监听所有网卡 |

#### 修改端口方法
**配置文件路径**：`src/main/resources/application.yaml`

在文件末尾添加（当前文件中未显式配置，使用 Spring Boot 默认值）：
```yaml
server:
  port: 8080              # 修改为需要的端口
  address: 0.0.0.0        # 或指定 IP
```

#### 在代码中修改
**入口文件**：`src/main/java/com/example/contractsystem/ContractSystemApplication.java`（第 10 行）
```java
public static void main(String[] args) {
    SpringApplication.run(ContractSystemApplication.class, args);
}
```
端口通过 `application.yaml` 或启动参数修改：
```bash
java -jar target/ContractSystem-0.0.1-SNAPSHOT.jar --server.port=9090
```

### 6.2 数据库配置

**配置文件路径**：`src/main/resources/application.yaml`（第 4-8 行）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/contract_system?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update      # 自动创建/更新表结构
    show-sql: true           # 输出 SQL 日志
```

#### 修改数据库连接
| 参数 | 默认值 | 修改方法 |
|------|--------|----------|
| 数据库地址 | `localhost:3306` | 修改 `url` 中的主机和端口 |
| 数据库名 | `contract_system` | 修改 `url` 中的数据库名 |
| 用户名 | `root` | 修改 `username` |
| 密码 | `root` | 修改 `password` |

### 6.3 检测工具（Docker 容器）配置

#### Slither 容器配置
**代码位置**：`src/main/java/com/example/contractsystem/service/SlitherService.java`（第 21-23 行）

```java
private static final String HOST_DIR = "D:\\Shi\\dockerData\\Slither\\input";     // 宿主机输入目录
private static final String CONTAINER_NAME = "slither-container";                  // Docker 容器名
private static final String HOST_DIR_OUT = "D:\\Shi\\dockerData\\Slither\\output"; // 宿主机输出目录
```

#### SmartAudit 容器配置
**代码位置**：`src/main/java/com/example/contractsystem/service/SmartAuditService.java`（第 28-30 行）

```java
private static final String HOST_DIR = "D:\\Shi\\dockerData\\SmartAudit\\input";
private static final String CONTAINER_NAME = "smartaudit-container";
private static final String HOST_DIR_OUT = "D:\\Shi\\dockerData\\SmartAudit\\output";
```

#### Docker 命令行模板

**Slither 执行命令**（SlitherService.java 第 54-58 行）：
```bash
docker exec slither-container slither /input/<file.sol> --json /output/<file.json>
```

**SmartAudit 执行命令**（SmartAuditService.java 第 63-72 行）：
```bash
docker exec smartaudit-container python3 run.py \
  --org "" \
  --config SmartContractBA \
  --task <solidity_code_content> \
  --name "" \
  --model GPT_4_O_MINI
```

### 6.4 SmartAudit API 参数说明

**代码位置**：`SmartAuditService.java`（第 33-37 行）

| 参数 | 说明 | 默认值 | 可选值 |
|------|------|--------|--------|
| `config` | 审计配置模式 | `SmartContractBA` | `SmartContractBA`（BA 审计）/ `SmartContractTA`（TA 审计） |
| `model` | LLM 模型 | `GPT_4_O_MINI` | GPT 模型系列 |
| `api_key` | API 密钥 | 空字符串 | OpenAI API Key |
| `username` | 当前用户 | 必填 | — |

#### 接口修改方法
如需添加新的模型或配置：
1. 修改 `SmartAuditService.java` 中 `runSmartAudit1()` 方法的 `config_`, `model` 变量默认值
2. 同步修改 `runSmartAudit()` 方法（第 93-134 行）中硬编码的参数
3. 前端 `漏洞检测工具.html` 目前为纯静态模拟，真实 API 调用需修改 JavaScript 逻辑

### 6.5 安全配置（跨域、认证）

**代码位置**：`src/main/java/com/example/contractsystem/config/SecurityConfig.java`

#### 当前配置（第 52-74 行）
```java
http
    .csrf(AbstractHttpConfigurer::disable)  // CSRF 已禁用
    .httpBasic(Customizer.withDefaults());   // 使用 HTTP Basic 认证
```

> **说明**：当前 `authorizeHttpRequests` 和 `formLogin` 已被注释（第 54-71 行），意味着**所有路径均不设权限拦截**，仅启用了 HTTP Basic 认证框架和 `@PreAuthorize` 注解支持。

#### 密码加密（第 36-40 行）
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return NoOpPasswordEncoder.getInstance();  // 明文密码，仅测试用！
}
```

> ⚠️ **警告**：当前使用 `NoOpPasswordEncoder`（明文密码），仅适合开发测试。生产环境需切换为 `BCryptPasswordEncoder`（第 31-34 行已注释）。

### 6.6 用户角色体系

**代码位置**：`entity/User.java`（第 22-23 行）

| 角色 | 常量值 | 权限 |
|------|--------|------|
| 普通用户 | `USER` | 访问 `/bac/any` |
| 管理员 | `ADMIN` | 访问 `/bac/any` + `/bac/only` |
| 超级用户 | `SUPER_USER` | 访问 `/bac/any` + `/bac/vip` |
| 超级管理员 | `SUPER_ADMIN` | **可使用 Slither/SmartAudit 带配置的检测接口** (`/slither/analyze1`, `/smartaudit/analyze1`) |

### 6.7 数据文件路径配置

**代码位置**：`SmartAuditService.java`（第 136-149 行）

| 配置项 | 路径 | 说明 |
|--------|------|------|
| BA CSV 数据 | `D:\Shi\code\ContractSystem\data\GPT4_Labeled_BA_vulnerability_result.csv` | BA 漏洞列表 |
| TA CSV 数据 | `D:\Shi\code\ContractSystem\data\GPT4_Labeled_TA_vulnerability_result.csv` | TA 漏洞列表 |
| BA 样本目录 | `D:\Shi\code\ContractSystem\data\BA\Labeled_GPT4` | BA 样例 .log 文件 |
| TA 样本目录 | `D:\Shi\code\ContractSystem\data\TA\Labeled_GPT4` | TA 样例 .log 文件 |

> ⚠️ **部署前必须修改**：以上路径为开发机绝对路径（`D:\Shi\...`），部署到服务器时需改为服务器实际路径。

### 6.8 WebSocket 配置

**代码位置**：`websocket/WSClient.java`（第 11 行）
```java
.url("ws://localhost:8000/ws")  // FastAPI WebSocket 地址
```

修改 WebSocket 地址：改此行中的 `localhost:8000` 为实际地址。

---

## 7. 前端运行指南

### 7.1 前端本地运行步骤

前端为纯静态 HTML 文件，无需构建工具。

**方式一：VS Code Live Server（推荐）**
1. VS Code 安装 "Live Server" 扩展
2. 右键 `index.html` → "Open with Live Server"
3. 浏览器自动打开 `http://127.0.0.1:5500`

**方式二：Python HTTP Server**
```bash
cd ContractSystem
python -m http.server 5500
# 访问 http://localhost:5500
```

**方式三：Node.js http-server**
```bash
npx http-server -p 5500
# 访问 http://localhost:5500
```

### 7.2 前端默认端口
| 方式 | 默认端口 |
|------|----------|
| Live Server | `5500` |
| Python http.server | `5500` |
| http-server | `5500` |

### 7.3 前端停止命令
- Live Server：点击 VS Code 右下角 "Port: 5500" → 停止
- 命令行服务器：`Ctrl + C`

---

## 8. Ubuntu 服务器部署指南

### 8.1 服务器前置准备

#### 系统要求
- Ubuntu 20.04 LTS 或 22.04 LTS
- 至少 4GB 内存、2 核 CPU
- 至少 20GB 可用磁盘空间

#### 安装系统依赖
```bash
sudo apt update && sudo apt upgrade -y

# Java 17
sudo apt install -y openjdk-17-jdk
java -version

# MySQL 8
sudo apt install -y mysql-server
sudo systemctl start mysql
sudo mysql_secure_installation

# Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
sudo systemctl start docker

# Nginx
sudo apt install -y nginx
sudo systemctl start nginx

# Git
sudo apt install -y git
```

### 8.2 代码需要修改的部分

部署前需要修改以下配置项：

#### ① 数据库连接
编辑 `src/main/resources/application.yaml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/contract_system?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: your_db_user       # 改为实际用户名
    password: your_db_password   # 改为实际密码
```

#### ② Docker 目录路径（须改为 Linux 路径）
编辑 `SlitherService.java`：
```java
private static final String HOST_DIR = "/home/ubuntu/dockerData/Slither/input";
private static final String HOST_DIR_OUT = "/home/ubuntu/dockerData/Slither/output";
```

编辑 `SmartAuditService.java`：
```java
private static final String HOST_DIR = "/home/ubuntu/dockerData/SmartAudit/input";
private static final String HOST_DIR_OUT = "/home/ubuntu/dockerData/SmartAudit/output";
```

#### ③ 数据文件路径
编辑 `SmartAuditService.java` 中 CSV 路径：
```java
String csvFilePath = "/home/ubuntu/ContractSystem/data/GPT4_Labeled_BA_vulnerability_result.csv";
String folderPath = "/home/ubuntu/ContractSystem/data/BA/Labeled_GPT4";
// 同理修改 TA 相关路径
```

> 💡 **提示**：建议使用环境变量或配置文件管理路径，避免硬编码。

#### ④ 密码加密（安全加固）
编辑 `SecurityConfig.java`，启用 BCrypt：
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();  // 启用 BCrypt
}
```

### 8.3 后端部署步骤（Systemd 守护进程）

#### Step 1：上传代码并构建
```bash
cd /home/ubuntu
git clone https://github.com/Striver-hue/ContractSystem.git
cd ContractSystem

# 修改上述配置后构建
./mvnw clean package -DskipTests
```

#### Step 2：创建 Systemd 服务
```bash
sudo nano /etc/systemd/system/contract-system.service
```

写入以下内容：
```ini
[Unit]
Description=Contract System Spring Boot Application
After=network.target mysql.service docker.service

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/ContractSystem
ExecStart=/usr/bin/java -jar /home/ubuntu/ContractSystem/target/ContractSystem-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
Environment="JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64"

[Install]
WantedBy=multi-user.target
```

#### Step 3：启动服务
```bash
sudo systemctl daemon-reload
sudo systemctl enable contract-system
sudo systemctl start contract-system
sudo systemctl status contract-system
```

### 8.4 前端部署步骤（Nginx 静态文件）

#### Step 1：复制前端文件
```bash
sudo mkdir -p /var/www/chainaudit
sudo cp -r /home/ubuntu/ContractSystem/index.html /var/www/chainaudit/
sudo cp -r /home/ubuntu/ContractSystem/漏洞检测工具.html /var/www/chainaudit/
sudo cp -r /home/ubuntu/ContractSystem/攻击事件展示.html /var/www/chainaudit/
sudo cp -r /home/ubuntu/ContractSystem/*.html /var/www/chainaudit/
sudo cp -r /home/ubuntu/ContractSystem/pictures /var/www/chainaudit/
sudo cp -r /home/ubuntu/ContractSystem/excel /var/www/chainaudit/
sudo cp -r /home/ubuntu/ContractSystem/data /var/www/chainaudit/
sudo cp -r /home/ubuntu/ContractSystem/text /var/www/chainaudit/

sudo chown -R www-data:www-data /var/www/chainaudit
```

#### Step 2：配置 Nginx
```bash
sudo nano /etc/nginx/sites-available/chainaudit
```

写入以下内容：
```nginx
server {
    listen 80;
    server_name your-domain.com;         # 改为实际域名或 IP

    root /var/www/chainaudit;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 反向代理
    location /bac/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location /slither/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /smartaudit/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

#### Step 3：启用站点
```bash
sudo ln -s /etc/nginx/sites-available/chainaudit /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### 8.5 防火墙端口开放
```bash
# UFW 防火墙
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8080/tcp    # 后端端口（如需直接访问）
sudo ufw enable

# 云服务器安全组
# 在云控制台放行：80 (HTTP)、443 (HTTPS)、8080（后端）
```

### 8.6 部署完成后的测试方法
```bash
# 1. 测试后端
curl http://localhost:8080/bac/any

# 2. 测试前端
curl http://localhost/

# 3. 测试 Nginx 代理
curl http://your-domain.com/bac/any

# 4. 查看后端日志
sudo journalctl -u contract-system -f

# 5. 查看 Nginx 日志
sudo tail -f /var/log/nginx/access.log
sudo tail -f /var/log/nginx/error.log
```

---

## 9. 项目维护与排查

### 9.1 日志文件位置

| 日志类型 | 位置 | 查看命令 |
|----------|------|----------|
| 后端运行日志（Systemd） | journal | `sudo journalctl -u contract-system -f` |
| 后端运行日志（直接运行） | 控制台输出 | 直接查看终端 |
| Nginx 访问日志 | `/var/log/nginx/access.log` | `sudo tail -f /var/log/nginx/access.log` |
| Nginx 错误日志 | `/var/log/nginx/error.log` | `sudo tail -f /var/log/nginx/error.log` |
| MySQL 日志 | `/var/log/mysql/error.log` | `sudo tail -f /var/log/mysql/error.log` |

### 9.2 常见问题排查

#### 问题 1：端口占用
```bash
# Linux 查看端口占用
sudo lsof -i :8080
sudo kill -9 <PID>

# Windows 查看端口占用
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

#### 问题 2：API 连接失败
**原因排查**：
1. 后端是否启动：`curl http://localhost:8080/bac/any`
2. Nginx 代理配置是否正确：检查 `proxy_pass` 地址
3. 防火墙是否放行端口
4. 前端 API 地址是否匹配（当前前端为纯静态模拟，不发起 API 请求）

#### 问题 3：后端启动失败
```bash
# 常见原因：
# 1. MySQL 连接失败 → 检查数据库是否运行、账号密码是否正确
sudo systemctl status mysql

# 2. Java 版本不匹配 → 确认 Java 17+
java -version

# 3. Maven 构建失败 → 检查依赖下载是否成功
./mvnw clean package

# 4. Docker 容器未运行
docker ps -a | grep slither-container
docker ps -a | grep smartaudit-container
```

#### 问题 4：部署报错
```bash
# 检查 Systemd 服务状态
sudo systemctl status contract-system

# 查看详细错误
sudo journalctl -u contract-system -n 100 --no-pager

# 检查磁盘空间
df -h

# 检查内存
free -m
```

#### 问题 5：跨域问题
当前项目后端未配置 CORS 过滤器。如前端独立部署需跨域，在 `SecurityConfig.java` 中添加：
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.addAllowedOrigin("http://your-frontend-domain.com");
    config.addAllowedMethod("*");
    config.addAllowedHeader("*");
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

### 9.3 项目更新与备份

#### 备份数据库
```bash
mysqldump -u root -p contract_system > backup_$(date +%Y%m%d).sql
```

#### 备份代码
```bash
cd /home/ubuntu
tar -czf ContractSystem_backup_$(date +%Y%m%d).tar.gz ContractSystem/
```

#### 更新代码
```bash
cd /home/ubuntu/ContractSystem
git pull origin main
./mvnw clean package -DskipTests
sudo systemctl restart contract-system
```

---

## 10. 补充说明

### 10.1 数据库配置

| 配置项 | 默认值 | 修改位置 |
|--------|--------|----------|
| 数据库类型 | MySQL | `pom.xml` 依赖 `mysql-connector-j` |
| 连接地址 | `jdbc:mysql://localhost:3306/contract_system` | `application.yaml` 第 5 行 |
| 用户名 | `root` | `application.yaml` 第 6 行 |
| 密码 | `root` | `application.yaml` 第 7 行 |
| 表自动创建 | `ddl-auto: update` | `application.yaml` 第 11 行 |
| SQL 日志 | `show-sql: true` | `application.yaml` 第 12 行 |

**数据表**：项目使用 JPA 自动创建 `user` 表（对应 `entity/User.java`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (自增) | 主键 |
| username | VARCHAR (唯一) | 用户名 |
| password | VARCHAR | 密码 |
| email | VARCHAR | 邮箱（可选） |
| role | VARCHAR | 角色：USER/ADMIN/SUPER_USER/SUPER_ADMIN |
| created_at | DATETIME | 自动填充创建时间 |

### 10.2 核心接口说明

#### 用户相关接口 (`/bac`)

| 接口 | 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|------|
| 用户注册 | POST | `/bac/register` | 参数：username, password, role | 无限制 |
| 管理员测试 | GET | `/bac/only` | 返回 "Hello Admin!" | ADMIN |
| 普通用户测试 | GET | `/bac/any` | 返回 "Hello User!" | USER/ADMIN |
| VIP 测试 | GET | `/bac/vip` | 返回 "Hello VIP!" | ADMIN/SUPER_USER |

#### Slither 检测接口 (`/slither`)

| 接口 | 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|------|
| 标准检测 | POST | `/slither/analyze` | 上传 .sol 文件，自动分析 | 无限制 |
| 带配置检测 | POST | `/slither/analyze1` | 上传文件 + 配置（需 username） | SUPER_ADMIN |

#### SmartAudit 检测接口 (`/smartaudit`)

| 接口 | 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|------|
| 标准审计 | POST | `/smartaudit/analyze` | 上传 .sol 文件，默认 BA 模式 | 无限制 |
| 带配置审计 | POST | `/smartaudit/analyze1` | 上传文件 + config/model/api_key | SUPER_ADMIN |
| BA 漏洞列表 | POST | `/smartaudit/getBAList` | 获取 BA 漏洞分类列表 | 无限制 |
| TA 漏洞列表 | POST | `/smartaudit/getTAList` | 获取 TA 漏洞分类列表 | 无限制 |
| BA 漏洞样例 | POST | `/smartaudit/getBAExample` | 参数：filename（默认 RealWord_20240812223706） | 无限制 |
| TA 漏洞样例 | POST | `/smartaudit/getTAExample` | 参数：filename（默认 Labeled_20240813213630） | 无限制 |

---

> **文档生成日期**：2026-05-26  
> **基于代码版本**：commit `4200250f26483ffde0b0ccb688b314fbafa12875`