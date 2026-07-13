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

管理 API 使用 `BRIDGE_API_TOKEN`：`POST /bridge/v1/deployments/{id}/sync` 从平台拉取并缓存 deployment；`POST /bridge/v1/executions/{id}/{start|pause|resume|takeover|cancel}` 只创建持久 QUEUED 命令，返回 202；`GET /bridge/v1/executions/{id}` 和 `/events` 只查 Bridge SQLite。相同 requestId/相同载荷返回原命令；不同载荷返回 `409 IDEMPOTENCY_CONFLICT`。

设备 API 使用 `ROBOT_AUTH_TOKENS_JSON` 内的占位 Token：`POST /robot-api/v1/heartbeat`、`POST /robot-api/v1/commands/{id}/ack`、`POST /robot-api/v1/events/batch`，以及 deployment 的 manifest/route/yaml/pgm GET。命令在 SQLite `BEGIN IMMEDIATE` 中领取租约，12 秒无心跳离线；事件以 `(robot_id, sequence)` 去重且只确认连续序号。
