# Ngrok 内网穿透配置文档

## 1. 配置目的

本项目后端服务运行在内网 `localhost:7777`，外网无法直接访问。通过 ngrok 将本地服务暴露到公网，实现外网访问。

公网访问地址：**https://washable-disaster-recycled.ngrok-free.dev**

## 2. 技术方案

| 项目 | 说明 |
|------|------|
| 工具 | ngrok v3.39.8 |
| 安装路径 | `/home/user/bin/ngrok` |
| 配置文件 | `/home/user/.config/ngrok/ngrok.yml` |
| 穿透端口 | 7777 |
| 服务类型 | systemd 用户服务 |
| 服务名称 | `ngrok-tunnel.service` |
| 账号类型 | 免费版 |

## 3. 当前配置状态

- [x] ngrok 已安装
- [x] authtoken 已配置
- [x] systemd 服务已创建
- [x] 开机自启已启用
- [x] 掉线自动重连已配置

## 4. 安装记录

### 4.1 下载 ngrok

```bash
cd /tmp
curl -sSL https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-linux-amd64.tgz -o ngrok.tgz
tar xzf ngrok.tgz
mkdir -p ~/bin
mv ngrok ~/bin/
rm ngrok.tgz
```

### 4.2 配置 authtoken

```bash
~/bin/ngrok config add-authtoken <你的authtoken>
```

当前 authtoken 已保存在：`/home/user/.config/ngrok/ngrok.yml`

### 4.3 测试启动

```bash
~/bin/ngrok http 7777
```

访问 http://127.0.0.1:4040 可查看 ngrok Web 控制台。

## 5. Systemd 服务配置

### 5.1 服务文件位置

```
/home/user/.config/systemd/user/ngrok-tunnel.service
```

### 5.2 服务文件内容

```ini
[Unit]
Description=Ngrok Tunnel for Port 7777
After=network.target

[Service]
Type=simple
ExecStart=/home/user/bin/ngrok http 7777
Restart=always
RestartSec=5
Environment=HOME=/home/user

[Install]
WantedBy=default.target
```

### 5.3 服务管理命令

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

## 6. 获取当前公网 URL

### 6.1 通过 API 获取

```bash
curl -s http://127.0.0.1:4040/api/tunnels | python3 -c "import sys,json; print(json.load(sys.stdin)['tunnels'][0]['public_url'])"
```

### 6.2 通过 Web 控制台

浏览器打开：http://127.0.0.1:4040

可查看：
- 当前隧道 URL
- 请求日志
- 请求详情

## 7. 免费版限制说明

| 限制项 | 说明 |
|--------|------|
| 域名 | 随机分配，如 `washable-disaster-recycled.ngrok-free.dev` |
| 域名持久性 | 免费版账号期间域名固定 |
| 并发隧道 | 1 个 |
| 连接数 | 有限制 |
| 请求数 | 有限制 |

## 8. 故障排查

### 8.1 服务无法启动

```bash
# 检查服务状态
systemctl --user status ngrok-tunnel.service

# 查看详细日志
journalctl --user -u ngrok-tunnel.service --no-pager -n 50

# 手动测试
~/bin/ngrok http 7777
```

### 8.2 URL 变化

如果重启服务后 URL 变化，使用以下命令获取新 URL：

```bash
curl -s http://127.0.0.1:4040/api/tunnels | python3 -c "import sys,json; print(json.load(sys.stdin)['tunnels'][0]['public_url'])"
```

### 8.3 连接超时

检查 ngrok 服务是否运行：

```bash
ps aux | grep ngrok
systemctl --user status ngrok-tunnel.service
```

如果服务停止，重启：

```bash
systemctl --user restart ngrok-tunnel.service
```

## 9. 注意事项

1. **公网暴露风险**：服务暴露到公网后，任何人都可访问，需确保应用本身有安全防护
2. **免费版限制**：免费版有连接数和请求数限制，高并发场景可能不够用
3. **URL 可能变化**：虽然免费版域名相对固定，但重启服务可能会变化
4. **依赖网络**：ngrok 需要稳定的网络连接，网络中断会导致隧道断开
5. **开机自启**：已配置 systemd 用户服务，用户登录后自动启动

## 10. 相关链接

- ngrok 官网：https://ngrok.com
- ngrok 文档：https://ngrok.com/docs
- ngrok Dashboard：https://dashboard.ngrok.com

## 11. 交接说明

接手者需要确认：

1. ngrok 服务是否正常运行：`systemctl --user status ngrok-tunnel.service`
2. 公网 URL 是否可访问：用浏览器打开当前 URL
3. 本地 7777 端口服务是否正常：`curl http://localhost:7777`

如需更换 ngrok 账号，更新 authtoken：

```bash
~/bin/ngrok config add-authtoken <新token>
systemctl --user restart ngrok-tunnel.service
```
