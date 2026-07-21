# ContractSystem 项目管理交接文档

## 1. 交接范围

本文件记录当前项目已经具备的功能、最近应关注的改动点、运行环境依赖、已知问题和下一步建议。系统细致功能说明见同目录 `system-functional-overview.md`。

当前项目目标是提供一个智能合约安全审计平台，整合 Slither、SmartAudit、漏洞数据展示、报告管理、论文资源和交易监控。

## 2. 当前完成的主要功能

### 2.1 基础 Web 系统

已完成：

- Spring Boot 项目骨架
- Maven Wrapper
- MySQL 数据源配置
- 静态前端页面托管
- 跨域配置
- 基础 Spring Security 配置

当前配置：

- 后端端口：`7777`
- 监听地址：`0.0.0.0`
- 数据库：`contract_system`
- 数据库用户：`root`
- 数据库密码：`root`

### 2.2 用户注册与角色体系

已完成：

- 用户实体 `User`
- 用户仓库 `UserRepository`
- 用户服务 `UserService`
- 用户注册接口 `/bac/register`
- Spring Security 用户加载 `CustomUserDetailsService`
- 角色字段 `role`

已设计角色：

- `USER`
- `ADMIN`
- `SUPER_USER`
- `SUPER_ADMIN`

需要注意：

- 当前接口基本全部放行
- 当前密码编码器是 `NoOpPasswordEncoder`
- 当前更像演示权限，不是生产权限

### 2.3 Slither 检测

已完成：

- 文件上传接口 `/slither/analyze`
- 带用户配置接口 `/slither/analyze1`
- 上传合约保存到 `docker/slither/input`
- 调用 Docker 容器 `slither-container`
- 输出 JSON 到 `docker/slither/output`
- 后端读取 JSON 并返回给前端
- 对 Solidity `0.4.x` 合约尝试指定 `solc 0.4.26`

需要注意：

- 容器名硬编码为 `slither-container`
- 输入输出路径在服务里硬编码为当前项目绝对路径
- `analyze1` 中存在 Linux 路径兼容问题
- 上传文件名没有做充分安全处理

### 2.4 SmartAudit AI 审计

已完成：

- 文件上传接口 `/smartaudit/analyze`
- 带用户配置接口 `/smartaudit/analyze1`
- 支持 `.sol` 和 `.txt`
- 自动创建 `docker/smartaudit/input` 和 `docker/smartaudit/output`
- 自动检查、启动或创建 `smartaudit-container`
- 默认镜像名 `shixiaolong0523/contractsystem:smartaudit-container`
- 默认配置 `SmartContractBA`
- 默认模型 `GPT_4_O_MINI`
- 每次任务生成独立 job 目录
- 将容器输出保存为文本报告

需要注意：

- 容器镜像必须已加载或可拉取
- 容器内 API key、网络、Python 依赖会直接影响审计结果
- 当前将 stdout/stderr 全部保存为报告文本
- 失败日志也可能进入报告目录

### 2.5 报告管理

已完成：

- 报告列表接口 `/reports/list`
- 报告内容接口 `/reports/content/{id}`
- 报告下载接口 `/reports/download/{id}`
- 支持 Slither JSON 报告
- 支持 SmartAudit TXT 报告
- 支持 issue 数、风险级别、状态和摘要粗略统计
- 前端报告页 `漏洞扫描报告.html`

需要注意：

- SmartAudit 文本报告的统计是关键词粗略判断，不是严格结构化解析
- 报告目录可能积累大量文件，需要清理策略

### 2.6 检测工具前端

已完成：

- `漏洞检测工具.html`
- 上传 `.sol` 或 `.txt`
- 前端构造 `FormData`
- 调用 Slither 或 SmartAudit 后端接口
- 与报告页联动

需要注意：

- 根目录和 `src/main/resources/static` 中存在重复页面
- 后续应只维护一份正式静态页面

### 2.7 攻击事件展示

已完成：

- 首页攻击事件展示
- 独立页面 `攻击事件展示.html`
- 读取 `excel/merged_v1.xlsx`
- 使用 SheetJS 解析 Excel
- 使用 Chart.js 画统计图
- 展示攻击事件卡片/列表

需要注意：

- 数据是静态 Excel 文件
- 更新数据需要替换或重新生成 Excel

### 2.8 漏洞知识库与工具库

已完成：

- `resource-vulnerability.html`
- `resource-tool.html`
- 读取 `excel/漏洞种类.xlsx`
- 读取 `excel/检测工具.xlsx`
- 展示漏洞类型和检测工具资源

需要注意：

- 这是静态资源展示功能
- Excel 字段变化可能导致前端解析异常

### 2.9 恶意交易监控

已完成：

- 后端 `RecordController`
- 前端 `交易行为.html`
- 启动时自动采集一次
- 每小时自动刷新
- 手动刷新接口 `/records/refresh`
- 查询缓存接口 `/records/latest`
- 数据源为 GitHub 仓库 `Judgegao/ETH-Malicious-TX-Monitor`
- 展示最近最多 50 条交易记录

需要注意：

- 依赖 GitHub 网络访问
- 数据只在内存缓存，不落库
- GitHub API 限流或网络异常会影响刷新

### 2.10 论文库

已完成：

- 后端 `lunwen.java`
- 前端 `论文库.html`
- 接口 `/lunwen/list`
- 接口 `/lunwen/pdf/{fileName}`
- 论文 PDF 存放在 `论文库/`
- 支持前端列表展示和 PDF 内联浏览

需要注意：

- 论文元数据写死在 Java 代码中
- 新增论文需要同时放 PDF 并改代码，后续可改为 JSON 或数据库配置

### 2.11 BA/TA 漏洞样本接口

已完成：

- `/smartaudit/getBAList`
- `/smartaudit/getTAList`
- `/smartaudit/getBAExample`
- `/smartaudit/getTAExample`
- `/smartaudit/exportBAExamplesCsv`
- `/smartaudit/exportBAExamplesXlsx`

功能包括：

- 解析 BA/TA CSV
- 查找对应日志
- 从日志提取合约代码
- 返回代码和结论
- 导出样例 CSV/XLSX

需要注意：

- 当前这些接口写死 Windows 绝对路径
- Linux 环境需要优先修复路径
- 导出格式化依赖 `npx prettier --plugin=prettier-plugin-solidity`

## 3. 当前重要文件

后端入口：

- `src/main/java/com/example/contractsystem/ContractSystemApplication.java`

配置：

- `src/main/resources/application.yaml`
- `src/main/java/com/example/contractsystem/config/SecurityConfig.java`
- `src/main/java/com/example/contractsystem/config/CorsConfig.java`

核心 Controller：

- `src/main/java/com/example/contractsystem/controller/SlitherController.java`
- `src/main/java/com/example/contractsystem/controller/SmartAuditController.java`
- `src/main/java/com/example/contractsystem/controller/ReportController.java`
- `src/main/java/com/example/contractsystem/controller/RecordController.java`
- `src/main/java/com/example/contractsystem/controller/UserController.java`
- `src/main/java/com/example/contractsystem/controller/lunwen.java`

核心 Service：

- `src/main/java/com/example/contractsystem/service/SlitherService.java`
- `src/main/java/com/example/contractsystem/service/SmartAuditService.java`
- `src/main/java/com/example/contractsystem/service/UserService.java`
- `src/main/java/com/example/contractsystem/service/CustomUserDetailsService.java`

前端页面：

- `src/main/resources/static/index.html`
- `src/main/resources/static/漏洞检测工具.html`
- `src/main/resources/static/漏洞扫描报告.html`
- `src/main/resources/static/交易行为.html`
- `src/main/resources/static/攻击事件展示.html`
- `src/main/resources/static/resource-vulnerability.html`
- `src/main/resources/static/resource-tool.html`
- `src/main/resources/static/论文库.html`

数据资源：

- `data/`
- `excel/`
- `docker/slither/`
- `docker/smartaudit/`
- `论文库/`

## 4. 当前工作区状态提示

交接时工作区已有未提交改动和新增文件。不要随意回滚。

观察到的状态包括：

- 多个 HTML 页面已修改
- `SecurityConfig.java` 已修改
- `SmartAuditService.java` 已修改
- 新增 `ReportController.java`
- 新增 `lunwen.java`
- 新增静态页面 `漏洞扫描报告.html`
- 新增静态页面 `论文库.html`
- 新增 `docker/smartaudit/`
- 存在大文件 `smartaudit-container.tar`

接手者应先执行：

```bash
git status --short
```

并确认哪些改动需要提交、哪些是生成物、哪些需要加入 `.gitignore`。

## 5. 运行前检查清单

### 5.1 Java 与 Maven

检查：

```bash
java -version
./mvnw -version
```

要求：

- Java 17
- Maven Wrapper 可执行

### 5.2 MySQL

检查数据库：

```sql
CREATE DATABASE IF NOT EXISTS contract_system DEFAULT CHARACTER SET utf8mb4;
```

确认 `application.yaml` 中用户名、密码和数据库地址正确。

### 5.3 Docker

检查 Docker：

```bash
docker ps -a
docker images
```

需要：

- `slither-container`
- `smartaudit-container` 或镜像 `shixiaolong0523/contractsystem:smartaudit-container`

Slither 容器需要挂载：

```text
项目/docker/slither/input  -> /input
项目/docker/slither/output -> /output
```

SmartAudit 容器由代码自动创建时会挂载：

```text
项目/docker/smartaudit/input  -> /input
项目/docker/smartaudit/output -> /output
```

### 5.4 前端依赖网络

页面依赖 CDN：

- Tailwind CSS
- Font Awesome
- Chart.js
- chartjs-plugin-datalabels
- SheetJS

如果部署环境不能访问公网，需要本地化这些静态依赖。

## 6. 重点风险与注意事项

1. 权限配置不适合生产

当前所有路径基本放行，`NoOpPasswordEncoder` 明文密码，仅适合演示。生产前必须重做认证授权。

2. Windows 绝对路径未清理

BA/TA 样本接口仍写死 `D:\Shi\code\ContractSystem\...`。这是目前最明显的跨环境问题。

3. Docker 强依赖硬编码

Slither 和 SmartAudit 都依赖固定容器名、固定挂载路径、固定镜像名。建议移入配置文件。

4. 文件上传安全不足

当前缺少文件大小限制、扩展名白名单强化、路径穿越防护、并发任务隔离等完整校验。

5. 根目录和 static 页面重复

多个 HTML 在根目录和 `src/main/resources/static` 同时存在。后续应明确正式入口，避免修一份漏一份。

6. 报告和容器镜像可能污染仓库

`target/`、报告输出、`.tar` 容器镜像、大量检测结果不一定适合纳入 Git。需要明确版本管理策略。

7. 外部网络依赖

交易监控依赖 GitHub，前端依赖 CDN，SmartAudit 可能依赖 LLM API。部署环境需要提前验证网络。

8. README 与当前代码不完全一致

README 中部分端口、路径、文件名说明已经滞后。建议后续更新 README 或以 `handoff/` 作为新的交接基础。

## 7. 建议下一步计划

### 第一优先级：让系统在目标环境稳定跑通

1. 修复 BA/TA 样本接口路径
2. 将 Slither、SmartAudit 输入输出目录、容器名、镜像名移入 `application.yaml`
3. 确认 Docker 容器启动脚本或部署文档
4. 跑通：

```text
/slither/analyze
/smartaudit/analyze
/reports/list
/records/latest
/lunwen/list
```

5. 确认前端所有页面在 `http://localhost:7777` 下能正常加载

### 第二优先级：整理仓库与文档

1. 明确 `src/main/resources/static` 是正式前端目录
2. 删除或归档根目录重复 HTML
3. 更新 README 中过时端口、路径和功能说明
4. 决定是否提交 `smartaudit-container.tar`
5. 补充 `.gitignore`，排除不应提交的报告输出、临时文件和构建产物

### 第三优先级：安全与权限加固

1. 将密码编码改为 `BCryptPasswordEncoder`
2. 设计登录态方案，例如 Session、JWT 或 Basic Auth 明确使用规范
3. 收紧 `/slither/**`、`/smartaudit/**`、`/reports/**` 权限
4. 后端不要信任前端传入的 `username` 决定权限
5. 增加上传文件大小限制和文件名清洗

### 第四优先级：体验和可维护性优化

1. 报告列表增加筛选、删除、重新检测
2. SmartAudit 输出改为结构化结果，减少关键词猜测
3. BA/TA 数据从硬编码 CSV 转为配置或数据库
4. 论文列表改为 JSON、YAML 或数据库配置
5. 恶意交易记录可落库，避免重启后缓存丢失
6. 前端依赖本地化，支持离线部署

## 8. 推荐验收用例

基础启动：

```bash
./mvnw spring-boot:run
```

打开：

```text
http://localhost:7777/index.html
```

Slither 验收：

- 上传 `text/reentrance.sol`
- 期望生成 `docker/slither/output/reentrance.json`
- 报告页能看到 Slither 报告

SmartAudit 验收：

- 上传一个小型 `.sol`
- 期望生成 `docker/smartaudit/output/<jobId>/<name>.txt`
- 报告页能看到 SmartAudit 报告

报告页验收：

```text
GET /reports/list
GET /reports/content/{id}
GET /reports/download/{id}
```

交易监控验收：

```text
GET /records/latest
POST /records/refresh
```

论文库验收：

```text
GET /lunwen/list
GET /lunwen/pdf/<fileName>
```

BA/TA 验收：

当前需先修复 Windows 路径，否则 Linux 环境不作为通过标准。

## 9. 内网穿透配置（ngrok）

### 9.1 配置说明

本项目后端服务运行在内网 `localhost:7777`，外网无法直接访问。已通过 ngrok 将本地服务暴露到公网。

当前公网访问地址：**https://washable-disaster-recycled.ngrok-free.dev**

> 注意：当前使用 **ngrok 旧实例**（原账号），配置文件为 `~/.config/ngrok/ngrok.yml`。

**⚠️ 重要：ngrok 实例分配**
- **ContractSystem 项目（端口 7777）**：使用 ngrok **旧实例**（原账号），配置文件 `~/.config/ngrok/ngrok.yml`
- **deepfake 项目（端口 18081）**：使用 ngrok **新实例**（新账号），配置文件 `~/.config/ngrok/account_b.yml`
- 两个项目使用不同的 ngrok 账号和配置文件，避免冲突
- 如需重启 ngrok，注意选择正确的配置文件

### 9.2 技术方案

| 项目 | 说明 |
|------|------|
| 工具 | ngrok v3.39.8 |
| 安装路径 | `/home/user/bin/ngrok` |
| 配置文件 | `/home/user/.config/ngrok/ngrok.yml` |
| 穿透端口 | 7777 |
| 服务类型 | systemd 用户服务 |
| 服务名称 | `ngrok-tunnel.service` |
| 账号类型 | 免费版 |
| 月流量限制 | 1GB |

### 9.3 获取当前公网 URL

方法一：命令行获取

```bash
curl -s http://127.0.0.1:4040/api/tunnels | python3 -c "import sys,json; print(json.load(sys.stdin)['tunnels'][0]['public_url'])"
```

方法二：Web 控制台

浏览器打开 http://127.0.0.1:4040 可查看：
- 当前隧道 URL
- 请求日志
- 请求详情

### 9.4 服务管理命令

```bash
# 查看服务状态
systemctl --user status ngrok-tunnel.service

# 启动服务
systemctl --user start ngrok-tunnel.service

# 停止服务
systemctl --user stop ngrok-tunnel.service

# 重启服务
systemctl --user restart ngrok-tunnel.service

# 查看服务日志
journalctl --user -u ngrok-tunnel.service -f
```

### 9.5 流量消耗说明

| 功能 | 单次消耗 | 说明 |
|------|----------|------|
| 页面浏览 | ~50-100KB | CDN 资源不消耗流量 |
| 上传 .sol 检测 | ~5-50KB | 文件较小 |
| 下载报告 | ~5-30KB | JSON/TXT 报告 |
| 下载论文 | ~1.4-3.4MB | ⚠️ 流量消耗较大 |

1GB 流量预计可用：
- 开发测试：2-3 个月以上
- 演示展示：1-2 个月
- 频繁下载论文：1-2 周

### 9.6 故障排查

服务无法启动：

```bash
# 检查服务状态
systemctl --user status ngrok-tunnel.service

# 查看详细日志
journalctl --user -u ngrok-tunnel.service --no-pager -n 50

# 手动测试
~/bin/ngrok http 7777
```

URL 变化或连接超时：

```bash
# 重启服务
systemctl --user restart ngrok-tunnel.service

# 获取新 URL
curl -s http://127.0.0.1:4040/api/tunnels | python3 -c "import sys,json; print(json.load(sys.stdin)['tunnels'][0]['public_url'])"
```

### 9.7 更换 ngrok 账号

如需更换 ngrok 账号：

```bash
# 更新 authtoken
~/bin/ngrok config add-authtoken <新token>

# 重启服务
systemctl --user restart ngrok-tunnel.service
```

### 9.8 注意事项

1. **公网暴露风险**：服务暴露到公网后，任何人都可访问，需确保应用本身有安全防护
2. **免费版限制**：并发隧道 1 个，连接数和请求数有限制
3. **URL 可能变化**：虽然免费版域名相对固定，但重启服务可能会变化
4. **依赖网络**：ngrok 需要稳定的网络连接，网络中断会导致隧道断开
5. **开机自启**：已配置 systemd 用户服务，用户登录后自动启动

详细配置文档见同目录 `ngrok-tunnel-setup.md`。

## 10. 交接建议

下一位开发接手时建议先做三件事：

1. 跑 `git status --short`，确认已有改动归属。
2. 跑通 `./mvnw spring-boot:run`，打开首页和检测页。
3. 优先修复路径配置化和安全配置，否则后续功能扩展会继续堆叠环境问题。

接手后确认 ngrok 服务：

```bash
# 检查服务是否正常运行
systemctl --user status ngrok-tunnel.service

# 确认公网 URL 是否可访问
curl -s http://127.0.0.1:4040/api/tunnels | python3 -c "import sys,json; print(json.load(sys.stdin)['tunnels'][0]['public_url'])"
```

