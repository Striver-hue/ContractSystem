# ContractSystem 系统功能交接说明

## 1. 系统定位

ContractSystem，也称 ChainAuditPro，是一个面向智能合约安全审计的 Web 系统。当前代码包含后端 REST API、静态前端页面、Slither 静态分析集成、SmartAudit AI 审计集成、漏洞与攻击事件数据展示、报告管理、论文库和恶意交易监控。

项目采用 Spring Boot + 静态 HTML 的形态运行。后端负责 API、MySQL 用户数据、Docker 检测工具编排、报告文件读取和外部数据采集；前端主要是 `src/main/resources/static` 下的 HTML 页面，使用 Tailwind CSS、Chart.js、SheetJS 和原生 JavaScript。

当前服务端口配置为 `7777`，配置文件为 `src/main/resources/application.yaml`。

## 2. 技术栈

- 后端语言：Java 17
- 后端框架：Spring Boot 4.0.3
- Web：Spring WebMVC
- 安全：Spring Security，当前全部路径基本放行，启用 HTTP Basic 和方法级权限注解
- 数据库：MySQL，默认库名 `contract_system`
- ORM：Spring Data JPA / Hibernate
- 构建工具：Maven Wrapper
- 容器依赖：Docker
- 静态分析工具：Slither，通过 `slither-container` 容器调用
- AI 审计工具：SmartAudit，通过 `smartaudit-container` 容器调用
- 前端：HTML、Tailwind CSS CDN、Vanilla JavaScript
- 图表：Chart.js、chartjs-plugin-datalabels
- Excel 解析：SheetJS

## 3. 主要目录

```text
ContractSystem/
├── README.md                         # 原项目说明文档，部分内容与当前代码有出入
├── pom.xml                           # Maven 依赖与构建配置
├── src/main/java/com/example/contractsystem
│   ├── config/                       # 安全与跨域配置
│   ├── controller/                   # REST API
│   ├── entity/                       # JPA 实体
│   ├── repository/                   # JPA Repository
│   ├── service/                      # 检测工具、用户、解析等业务逻辑
│   ├── utils/                        # SmartAuditParser
│   └── websocket/                    # WebSocket 客户端
├── src/main/resources
│   ├── application.yaml              # 后端端口、数据库配置
│   └── static/                       # 正式静态前端页面
├── data/                             # BA/TA 漏洞标注 CSV 与日志样本
├── docker/
│   ├── slither/input                 # Slither 输入合约
│   ├── slither/output                # Slither JSON 输出
│   └── smartaudit/input|output       # SmartAudit 输入与文本输出
├── excel/                            # 前端展示用 Excel 数据
├── pictures/                         # 图片资源
├── 论文库/                           # PDF 论文
└── handoff/                          # 本交接文档目录
```

根目录也存在一批 HTML 页面，例如 `index.html`、`漏洞检测工具.html`、`漏洞扫描报告.html`。当前 Spring Boot 静态资源入口更应以 `src/main/resources/static` 为准。根目录页面可能是历史副本或生成物，后续需要统一。

## 4. 后端启动与基础配置

配置文件：`src/main/resources/application.yaml`

当前关键配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/contract_system?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: root
server:
  port: 7777
  address: 0.0.0.0
```

启动方式：

```bash
./mvnw spring-boot:run
```

或：

```bash
./mvnw clean package -DskipTests
java -jar target/ContractSystem-0.0.1-SNAPSHOT.jar
```

访问示例：

```text
http://localhost:7777/index.html
http://localhost:7777/漏洞检测工具.html
http://localhost:7777/漏洞扫描报告.html
```

## 5. 用户与权限功能

相关文件：

- `controller/UserController.java`
- `entity/User.java`
- `repository/UserRepository.java`
- `service/CustomUserDetailsService.java`
- `config/SecurityConfig.java`

用户表实体字段：

- `id`
- `username`
- `password`
- `email`
- `role`
- `createdAt`

接口：

```text
POST /bac/register
GET  /bac/any
GET  /bac/only
GET  /bac/vip
```

注册接口参数：

```text
username
password
role，默认 USER
```

角色设计：

- `USER`
- `ADMIN`
- `SUPER_USER`
- `SUPER_ADMIN`

当前权限状态：

- `SecurityConfig` 中 `/slither/**`、`/smartaudit/**` 和其他请求均 `permitAll`
- `@PreAuthorize` 仍存在于 `/bac/any`、`/bac/only`、`/bac/vip`
- 密码编码使用 `NoOpPasswordEncoder`，也就是明文或近似明文存储
- `SlitherService.runSlither1` 和 `SmartAuditService.runSmartAudit1` 内部会检查传入 `username` 对应用户是否为 `SUPER_ADMIN`

结论：当前权限体系适合演示，不适合生产。生产前必须改为 BCrypt、收紧接口访问、明确登录态或 Token 方案。

## 6. Slither 静态分析功能

相关文件：

- `controller/SlitherController.java`
- `service/SlitherService.java`
- 前端页面 `src/main/resources/static/漏洞检测工具.html`
- 报告页面 `src/main/resources/static/漏洞扫描报告.html`

接口：

```text
POST /slither/analyze
POST /slither/analyze1
```

`/slither/analyze`：

- 接收 multipart 文件字段 `file`
- 将上传的 `.sol` 保存到 `docker/slither/input`
- 根据合约里的 `pragma solidity` 提取版本
- 如果版本是 `0.4.x`，调用 Slither 时追加 `--solc /root/.solc-select/versions/0.4.26/solc`
- 调用容器：

```bash
docker exec slither-container slither /input/<file.sol> --json /output/<file.json>
```

- 读取 `docker/slither/output/<file.json>` 并返回 JSON

`/slither/analyze1`：

- 接收 `file` 和 `config`
- 从 `config.username` 获取用户名
- 查询数据库用户
- 非 `SUPER_ADMIN` 时返回权限不足
- 保存到按用户区分的输入输出目录

注意点：

- `SlitherService` 中 Slither 路径为当前 Linux 工作区绝对路径：

```text
/home/user/Platform-SmartAudit/ContractSystem/docker/slither/input
/home/user/Platform-SmartAudit/ContractSystem/docker/slither/output
```

- 容器名硬编码为 `slither-container`
- 文件名未做充分安全清洗，后续需要补充上传文件名校验
- `runSlither1` 中路径拼接使用反斜杠 `\`，在 Linux 上不可靠

## 7. SmartAudit AI 审计功能

相关文件：

- `controller/SmartAuditController.java`
- `service/SmartAuditService.java`
- `docker/smartaudit`

接口：

```text
POST /smartaudit/analyze
POST /smartaudit/analyze1
```

`/smartaudit/analyze`：

- 接收 multipart 文件字段 `file`
- 支持 `.sol` 和 `.txt`
- 默认审计配置：

```text
config = SmartContractBA
model  = GPT_4_O_MINI
```

- 自动创建宿主机输入输出目录：

```text
docker/smartaudit/input
docker/smartaudit/output
```

- 自动检查容器 `smartaudit-container`
  - 如果容器正在运行，直接使用
  - 如果容器存在但停止，执行 `docker start`
  - 如果容器不存在，使用镜像 `shixiaolong0523/contractsystem:smartaudit-container` 创建

- 每次审计创建独立 job 目录，命名包含文件名和时间戳
- 调用容器内 `python3 run.py`
- 将容器输出日志写入 `docker/smartaudit/output/<jobId>/<baseName>.txt`
- 返回文本报告内容

实际调用逻辑通过 `python3 -c` 包装：

```text
读取 /input/<jobId>/<file>
将文件内容作为 run.py 的 --task 参数
传入 --org、--config、--name、--model
```

`/smartaudit/analyze1`：

- 支持 `config.username`
- 支持 `config.config`
- 支持 `config.model`
- 会检查用户是否为 `SUPER_ADMIN`

注意点：

- SmartAudit 容器需要镜像存在或可拉取
- 运行耗时取决于容器内模型/API 调用
- 如果容器内缺少 API key 或网络不可用，报告会失败或输出错误日志
- 当前代码会把全部 stdout/stderr 当作报告保存

## 8. BA/TA 漏洞样本数据功能

相关文件：

- `SmartAuditController.java`
- `SmartAuditService.java`
- `utils/SmartAuditParser.java`
- `data/GPT4_Labeled_BA_vulnerability_result.csv`
- `data/GPT4_Labeled_TA_vulnerability_result.csv`
- `data/BA/Labeled_GPT4`
- `data/TA/Labeled_GPT4`

接口：

```text
POST /smartaudit/getBAList
POST /smartaudit/getTAList
POST /smartaudit/getBAExample
POST /smartaudit/getTAExample
POST /smartaudit/exportBAExamplesCsv
POST /smartaudit/exportBAExamplesXlsx
```

功能：

- 从 BA/TA CSV 读取漏洞分类和文件名
- 根据 `filename` 查找对应 `.log`
- 从日志中提取 `**task_prompt**:` 到 `**project_name**:` 之间的 Solidity 代码
- 返回代码和结论信息
- 支持将 BA 样例导出为 CSV 或 XLSX
- 导出时会尝试调用 `npx prettier --plugin=prettier-plugin-solidity` 格式化 Solidity 代码

重要问题：

当前 BA/TA 查询仍写死 Windows 路径：

```text
D:\Shi\code\ContractSystem\data\GPT4_Labeled_BA_vulnerability_result.csv
D:\Shi\code\ContractSystem\data\GPT4_Labeled_TA_vulnerability_result.csv
D:\Shi\code\ContractSystem\data\BA\Labeled_GPT4
D:\Shi\code\ContractSystem\data\TA\Labeled_GPT4
```

因此在 Linux 部署或当前工作区运行时，这些接口大概率不可用。应改为基于 `System.getProperty("user.dir")` 的项目相对路径。

## 9. 报告管理功能

相关文件：

- `controller/ReportController.java`
- 前端页面 `src/main/resources/static/漏洞扫描报告.html`

接口：

```text
GET /reports/list
GET /reports/content/{id}
GET /reports/download/{id}
```

功能：

- 扫描 Slither 输出目录 `docker/slither/output`
- 扫描 SmartAudit 输出目录 `docker/smartaudit/output`
- Slither 报告识别 `.json`
- SmartAudit 报告识别 `.txt`
- 过滤 `mount_check.txt`
- 生成统一报告视图：
  - 工具名称
  - 标题
  - 文件名
  - 相对路径
  - 修改时间
  - 文件大小
  - 问题数
  - high / medium / low 计数
  - 状态
  - 摘要
  - 内容 URL
  - 下载 URL

Slither 报告统计：

- 解析 JSON `results.detectors`
- 根据 `impact` 统计 high、medium、low
- 读取 `success` 判断状态

SmartAudit 报告统计：

- 用关键词粗略统计风险等级
- 出现 `traceback`、`exception`、`error:` 时状态为 `Review`
- 从文本中提取第一段足够长的内容作为摘要

路径安全：

- 报告 ID 使用 `tool:relativePath` 的 Base64 URL-safe 编码
- 读取时会校验 normalize 后路径必须在输出目录下

## 10. 攻击事件展示功能

相关文件：

- `src/main/resources/static/index.html`
- `src/main/resources/static/攻击事件展示.html`
- `excel/merged_v1.xlsx`

功能：

- 前端使用 SheetJS 读取 `excel/merged_v1.xlsx`
- 解析攻击事件数据
- 展示事件列表和卡片
- 使用 Chart.js 绘制攻击类型统计图
- 对 Excel 日期进行格式化

这是纯前端静态数据展示功能，不依赖后端 Controller。

## 11. 漏洞知识库与工具资源

相关页面：

- `src/main/resources/static/resource-vulnerability.html`
- `src/main/resources/static/resource-tool.html`

数据文件：

- `excel/漏洞种类.xlsx`
- `excel/检测工具.xlsx`

功能：

- 漏洞知识库页面读取漏洞类型 Excel，展示漏洞分类、说明等内容
- 工具资源页面读取检测工具 Excel，展示工具名称、类型、能力等内容
- 前端使用 SheetJS 解析 Excel

## 12. 恶意交易监控功能

相关文件：

- `controller/RecordController.java`
- `src/main/resources/static/交易行为.html`

接口：

```text
GET  /records/latest
POST /records/refresh
```

数据源：

```text
https://github.com/Judgegao/ETH-Malicious-TX-Monitor
```

功能：

- 应用启动后自动执行一次采集
- 每小时定时刷新一次
- 通过 GitHub Tree API 查找最新 CSV
- 读取最近最多 12 个 CSV 文件
- 解析最多 50 条恶意交易记录
- 每条记录包含：
  - 区块号
  - 交易哈希
  - from 地址
  - to 地址
  - 检测时间
  - Etherscan 交易链接
  - Etherscan 地址链接
  - 来源 CSV 路径和 URL

缓存行为：

- 采集成功后保存在内存 `cache`
- GitHub 暂时不可用时继续返回上次成功缓存
- `/records/refresh` 可手动刷新

注意点：

- 该功能依赖服务器能访问 GitHub 和 raw.githubusercontent.com
- 没有持久化交易记录，只存在内存缓存中

## 13. 论文库功能

相关文件：

- `controller/lunwen.java`
- `src/main/resources/static/论文库.html`
- `论文库/`

接口：

```text
GET /lunwen/list
GET /lunwen/pdf/{fileName}
```

功能：

- 返回代码内置的论文元数据列表
- 按发布日期倒序排序
- 前端展示标题、作者、期刊/会议和发布日期
- 支持浏览器内联打开 PDF
- PDF 文件从项目根目录 `论文库/` 读取

已内置论文包括：

- Towards Secure Program Partitioning for Smart Contracts With LLM's In-Context Learning
- Automated TEE Adaptation With LLMs
- Detecting Various DeFi Price Manipulations with LLM Reasoning
- Advanced Smart Contract Vulnerability Detection via LLM-Powered Multi-Agent Systems
- Automated Invariant Generation for Solidity Smart Contracts

## 14. 前端页面清单

正式静态页面目录：`src/main/resources/static`

```text
index.html                 # 首页和系统门户
漏洞检测工具.html           # 合约上传与检测入口
漏洞扫描报告.html           # 报告列表、详情和下载
攻击事件展示.html           # 攻击事件数据可视化
交易行为.html               # 恶意交易监控记录
resource-vulnerability.html # 漏洞知识库
resource-tool.html          # 检测工具资源库
论文库.html                 # 论文列表和 PDF 查看
```

前端依赖基本来自 CDN，因此离线环境或网络受限环境会影响页面样式、图表或 Excel 解析。

## 15. 当前运行依赖

必须准备：

- JDK 17
- Maven 或项目自带 `mvnw`
- MySQL，库名 `contract_system`
- Docker
- Slither 容器 `slither-container`
- SmartAudit 镜像或容器 `smartaudit-container`

可选但影响部分功能：

- Node.js / npx / prettier / prettier-plugin-solidity：仅 BA 样例导出格式化时使用
- GitHub 网络访问：恶意交易监控功能需要
- CDN 网络访问：前端 Tailwind、Chart.js、SheetJS 等依赖需要

## 16. 已知不一致与风险点

1. README 中部分端口和路径说明过时。当前后端端口是 `7777`。
2. BA/TA 数据接口仍有 Windows 绝对路径，Linux 环境不可用。
3. `SecurityConfig` 几乎全放行，不能作为生产权限配置。
4. 密码使用 `NoOpPasswordEncoder`，生产必须改 BCrypt。
5. Docker 容器名、镜像名和挂载目录硬编码。
6. Slither 的 `runSlither1` 在 Linux 下使用反斜杠拼路径，容易出错。
7. 根目录 HTML 与 `src/main/resources/static` 存在重复，需确定唯一维护入口。
8. `target/`、`.tar` 镜像包、报告文件等生成物较多，仓库体积和 Git 管理需要清理策略。
9. 前端大量依赖 CDN，部署内网或离线环境需本地化资源。
10. 上传文件缺少完整安全校验，例如文件名、大小、内容类型、并发覆盖等。

