# Isolated Robot Bridge

这是公网服务器上的独立 FastAPI Bridge。它只缓存平台已有的 deployment 并维护 SQLite 命令/事件状态；绝不反向连接 Jetson。当前 Spring Boot 部署保持 `APP_ROBOT_MODE=simulation`，不要启用既有 HTTP 直连模式；后续前后端只调用 `/bridge/v1`。

```bash
cd integration/robot-bridge
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
set -a; . .env; set +a
uvicorn app.main:app --host 127.0.0.1 --port 8001
python3 smoke.py
```

管理 API 使用 `BRIDGE_API_TOKEN`：`POST /bridge/v1/deployments/{id}/sync` 从平台拉取并不可变缓存 deployment；`POST /bridge/v1/executions/{id}/{start|pause|resume|takeover|cancel}` 只创建持久 QUEUED 命令，返回 202；`GET /bridge/v1/executions/{id}`、`/events` 和 `GET /bridge/v1/robots/{robotId}` 只查 Bridge SQLite。相同 requestId/相同载荷返回原命令；不同载荷返回 `409 IDEMPOTENCY_CONFLICT`，execution 改绑返回 `409 EXECUTION_CONFLICT`。

设备 API 使用 `ROBOT_AUTH_TOKENS_JSON` 的 Bearer Token。Heartbeat 领取命令的优先级是 CANCEL、TAKEOVER、PAUSE、RESUME、START；ACK 只表示机器人已持久接收，APPLIED/REJECTED/FAILED 由连续事件推进。事件缺口后的数据先保存但不处理，补齐缺口后才按 sequence 顺序应用；终态不会被旧事件回退。在线状态在查询时按最后心跳 12 秒计算。

生产部署使用 `/opt/robot-bridge/current`、`/opt/robot-bridge/venv`、`/var/lib/robot-bridge` 和系统用户 `robotbridge`。`deploy/install.sh.example` 可重复执行且不覆盖已有 env；占位配置未替换时只安装不启动。已有 HTTPS 站点只引用 `existing-site-location.conf.example`，独立子域名使用 `standalone-subdomain.conf.example`。本仓库的 Spring Boot 始终保持 `APP_ROBOT_MODE=simulation`。
