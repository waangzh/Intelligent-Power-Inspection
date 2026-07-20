# Robot Bridge 服务器部署、升级与回滚

> 生产默认只公开 `/robot-api/`。Spring Boot 通过 `http://127.0.0.1:8001/bridge/v1` 调用管理 API；浏览器不得访问管理 API。

## 1. 目录结构

```text
/opt/robot-bridge/releases/<commit>/
/opt/robot-bridge/current -> /opt/robot-bridge/releases/<commit>
/opt/robot-bridge/venv/
/var/lib/robot-bridge/robot-bridge.db
/var/lib/robot-bridge/storage/deployments/
/var/lib/robot-bridge/inspection-image-upload-tmp/
/etc/robot-bridge/robot-bridge.env
```

- release 目录只读发布，不原地覆盖。
- `current` 只做原子软链接切换。
- venv 可跨 release 复用；requirements 变化时先升级依赖再切换。
- SQLite 与 deployment storage 不放 release 目录，升级不会覆盖数据。

## 2. 系统用户与权限

```bash
sudo useradd --system --home /var/lib/robot-bridge --shell /usr/sbin/nologin robotbridge
sudo install -d -o robotbridge -g robotbridge -m 0750 /opt/robot-bridge/releases /var/lib/robot-bridge
sudo install -d -o root -g robotbridge -m 0750 /etc/robot-bridge
```

服务使用 `robotbridge` 用户，不使用 root。env 建议 `root:robotbridge 0640`；数据目录 `robotbridge:robotbridge 0750`。

## 3. 环境变量

| 变量 | 必填 | 示例 | 用途 | 敏感 | 修改后重启 |
| --- | --- | --- | --- | --- | --- |
| `ROBOT_AUTH_TOKENS_JSON` | 是 | `{"robot-001":"token-placeholder"}` | robotId→设备 Token | 是 | 是 |
| `BRIDGE_API_TOKEN` | 是 | `token-placeholder` | Spring/本机管理 API | 是 | 是 |
| `PLATFORM_BASE_URL` | 是 | `https://example.com` | Bridge 回读平台 deployment/revision/map | 否 | 是 |
| `PLATFORM_BEARER_TOKEN` | 是 | `token-placeholder` | Bridge→Spring 服务凭据 | 是 | 是 |
| `ROBOT_BRIDGE_STORAGE_PATH` | 是 | `/var/lib/robot-bridge/robot-bridge.db` | SQLite 文件 | 否 | 是 |
| `BRIDGE_READ_TIMEOUT_SEC` | 否 | `10` | Bridge 读取 Spring 超时 | 否 | 是 |
| `BRIDGE_MAP_UPLOAD_ENABLED` | 是 | `true` | 启用机器人地图转发 | 否 | 是 |
| `BRIDGE_MAP_UPLOAD_CONNECT_TIMEOUT_SEC` | 否 | `10` | 地图转发连接超时 | 否 | 是 |
| `BRIDGE_MAP_UPLOAD_READ_TIMEOUT_SEC` | 否 | `60` | 地图转发读取超时 | 否 | 是 |
| `BRIDGE_MAP_UPLOAD_YAML_MAX_BYTES` | 否 | `1048576` | YAML 上限 1 MiB | 否 | 是 |
| `BRIDGE_MAP_UPLOAD_PGM_MAX_BYTES` | 否 | `104857600` | PGM 上限 100 MiB | 否 | 是 |
| `BRIDGE_MAP_UPLOAD_REQUEST_MAX_BYTES` | 否 | `115343360` | 地图请求总上限 110 MiB | 否 | 是 |
| `BRIDGE_MAP_UPLOAD_TEMP_DIR` | 是 | `/var/lib/robot-bridge/map-upload-tmp` | 地图上传临时目录 | 否 | 是 |
| `BRIDGE_INSPECTION_IMAGE_UPLOAD_ENABLED` | 否 | `true` | 启用巡检图片转发 | 否 | 是 |
| `BRIDGE_INSPECTION_IMAGE_UPLOAD_CONNECT_TIMEOUT_SEC` | 否 | `10` | 图片转发连接超时 | 否 | 是 |
| `BRIDGE_INSPECTION_IMAGE_UPLOAD_READ_TIMEOUT_SEC` | 否 | `60` | 图片转发读取超时 | 否 | 是 |
| `BRIDGE_INSPECTION_IMAGE_UPLOAD_MAX_BYTES` | 否 | `20971520` | 单图上限 20 MiB | 否 | 是 |
| `BRIDGE_INSPECTION_IMAGE_UPLOAD_TEMP_DIR` | 否 | `/var/lib/robot-bridge/inspection-image-upload-tmp` | 受控临时目录 | 否 | 是 |

禁止把真实 env 复制进仓库或聊天。编辑后检查权限，不打印文件内容：

```bash
sudo install -o root -g robotbridge -m 0640 /dev/null /etc/robot-bridge/robot-bridge.env
sudo stat -c '%U %G %a %n' /etc/robot-bridge/robot-bridge.env
```

## 4. 安装 release

从已验证的仓库导出 `integration/robot-bridge` 到临时上传目录，再安装为指定 commit：

```bash
commit=<commit>
sudo install -d -o robotbridge -g robotbridge -m 0750 "/opt/robot-bridge/releases/$commit"
sudo cp -a app requirements.txt "/opt/robot-bridge/releases/$commit/"
sudo chown -R robotbridge:robotbridge "/opt/robot-bridge/releases/$commit"
sudo python3 -m venv /opt/robot-bridge/venv
sudo /opt/robot-bridge/venv/bin/pip install -r "/opt/robot-bridge/releases/$commit/requirements.txt"
sudo -u robotbridge /opt/robot-bridge/venv/bin/python -m compileall "/opt/robot-bridge/releases/$commit/app"
sudo -u robotbridge env PYTHONPATH="/opt/robot-bridge/releases/$commit" \
  /opt/robot-bridge/venv/bin/python -c 'from app.main import app; print(app.title)'
sudo ln -sfn "/opt/robot-bridge/releases/$commit" /opt/robot-bridge/current
```

不要把数据库或 env 打进 release。

旧部署若 `/opt/robot-bridge/current` 是真实目录而不是软链接，先停止服务，把该目录移动为 `/opt/robot-bridge/releases/<previous-commit>`，再创建 `current` 软链接。安装脚本检测到旧目录会停止，不会覆盖或把新链接嵌套进去。

## 5. systemd

安装 unit：

```bash
sudo install -m 0644 deploy/robot-bridge.service /etc/systemd/system/robot-bridge.service
sudo systemctl daemon-reload
sudo systemctl enable robot-bridge
```

常用命令：

```bash
sudo systemctl start robot-bridge
sudo systemctl stop robot-bridge
sudo systemctl restart robot-bridge
sudo systemctl status robot-bridge --no-pager
sudo journalctl -u robot-bridge -n 200 --no-pager
sudo journalctl -u robot-bridge -f
```

确认只监听回环：

```bash
ss -lntp | grep ':8001'
```

预期为 `127.0.0.1:8001`，不能是 `0.0.0.0:8001`。

本机 health：

```bash
curl -sS -H 'Authorization: Bearer token-placeholder' \
  http://127.0.0.1:8001/bridge/v1/health
```

## 6. Nginx

现有 HTTPS 站点只引用 `existing-site-location.conf.example` 中的：

```nginx
location /robot-api/ {
    proxy_pass http://127.0.0.1:8001/robot-api/;
}
```

正式默认不代理：

```text
/bridge/
```

Spring Boot 使用：

```text
http://127.0.0.1:8001/bridge/v1
```

应用后：

```bash
sudo nginx -t
sudo systemctl reload nginx
curl -i https://example.com/robot-api/v1/heartbeat \
  -H 'Content-Type: application/json' -d '{}'
```

无设备 Token 预期 `401 AUTH_FAILED`。公网 `/bridge/v1/health` 应为 Nginx 404/未路由，而不是 Bridge 返回。临时管理见 [bridge-local-only.md](bridge-local-only.md)。

## 7. TLS

- 公网必须 HTTPS，证书域名与 `YLHB_CLOUD_BASE_URL` 一致。
- 公有 CA 使用系统 trust store；私有 CA 将 CA 文件安全分发到 Jetson，并配置 `YLHB_CLOUD_CA_FILE`。
- 禁止 `curl -k`、`verify=false`、`StrictHostKeyChecking=no` 或任何长期关闭校验的配置。
- 证书更新后先 `nginx -t`，再 reload；Jetson 观察 Cloud status 从 BACKOFF 恢复 CONNECTED。

## 8. 数据备份与恢复

必须同时备份：

```text
/var/lib/robot-bridge/robot-bridge.db
/var/lib/robot-bridge/storage/deployments
```

在线 SQLite 使用 `.backup`，不要直接复制活动 WAL 数据库：

```bash
sudo -u robotbridge sqlite3 /var/lib/robot-bridge/robot-bridge.db \
  ".backup '/var/lib/robot-bridge/robot-bridge.db.backup'"
sudo tar -C /var/lib/robot-bridge -czf /var/lib/robot-bridge/deployments.backup.tgz storage/deployments
```

恢复前停止服务，保留故障副本，恢复数据库与 deployment 后检查 owner，再启动并执行阶段 A 验收。

## 9. 版本升级

1. 上传到 `/opt/robot-bridge/releases/<commit>`。
2. 安装/更新 requirements。
3. `compileall`。
4. import check。
5. 备份数据库与 deployment。
6. `ln -sfn` 切换 current。
7. restart。
8. 本机 health。
9. 检查日志与 127.0.0.1 监听。
10. 公网无 Token heartbeat 401。

```bash
sudo ln -sfn "/opt/robot-bridge/releases/$commit" /opt/robot-bridge/current
sudo systemctl restart robot-bridge
curl -sS -H 'Authorization: Bearer token-placeholder' \
  http://127.0.0.1:8001/bridge/v1/health
```

升级期间不要创建 START。

## 10. 回滚

记录切换前的链接：

```bash
readlink -f /opt/robot-bridge/current
```

失败时：

```bash
sudo systemctl stop robot-bridge
sudo ln -sfn /opt/robot-bridge/releases/<previous-commit> /opt/robot-bridge/current
sudo systemctl start robot-bridge
sudo systemctl status robot-bridge --no-pager
```

若新版本已经迁移 SQLite schema，先确认旧版本向后兼容；不兼容时恢复升级前数据库和 deployment 备份。回滚后重复 health、Nginx 和无 Token 401 检查。

## 11. 排障

| 症状 | 检查 | 处理 |
| --- | --- | --- |
| 服务无法启动 | `systemctl status`、journal、env 必填项、import check | 修正 env/依赖/权限，勿打印敏感值 |
| 端口占用 | `ss -lntp | grep ':8001'` | 停止旧实例，保持单进程 |
| Nginx 502 | Bridge 状态、回环 health、proxy_pass | 先修 Bridge，再 reload Nginx |
| 401 | 区分设备 token 与 admin token；robotId 映射 | 按轮换流程修正，禁止把 token 发到前端 |
| `PLATFORM_UNREACHABLE` | PLATFORM_BASE_URL、服务凭据、Spring TLS/HTTP | 修复服务端链路，退避重试 sync |
| Robot offline | robots.last_seen、Jetson cloud status、TLS/DNS | 修复出站连接，不开放 Jetson 入站 |
| SQLite 权限 | owner、目录 execute 权限、WAL 文件 | `robotbridge:robotbridge`，数据目录 0750 |
| 证书失败 | 域名、有效期、链、Jetson CA 文件 | 更新证书/CA，禁止关闭校验 |
| sequence 堵塞 | robots cursor、events 缺口、Jetson pendingEventCount | 找缺失序号，不手工跳 cursor |
| deployment 下载失败 | manifest 归属、四个文件、hash、YAML image | 重新 sync 新 deploymentId，不覆盖冲突内容 |

## 12. 安全基线

- 8001 不对公网开放；安全组/防火墙不放行 8001。
- Nginx 不公开 `/bridge/`；仅 `/robot-api/`。
- Token 文件最小权限，服务用户无登录 shell。
- 日志脱敏，不记录 Authorization、Token、JWT、env、URL query/userinfo。
- SSH 使用密钥；密码只能在交互提示中输入，禁止 sshpass、命令行密码和仓库凭据。
- 面板、SSH、数据库密码不得写入仓库、Runbook 或聊天。
- release 可追溯到 commit；升级前备份，失败可原子回滚。
