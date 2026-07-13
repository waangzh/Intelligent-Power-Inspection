# Isolated Robot Bridge

独立 FastAPI Robot Bridge：Jetson 主动通过 HTTPS heartbeat 领取命令、ACK、补传事件和下载 deployment；Bridge 不反向连接 Jetson。

## 本地快速验证

```bash
cd integration/robot-bridge
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python3 smoke.py
```

## 文档入口

- [协议单一事实来源](../../docs/protocol/robot-platform-v1.md)
- [集成索引](../../docs/integration/README.md)
- [服务器部署、升级与回滚](deploy/README.md)
- [管理 API 仅本机访问](deploy/bridge-local-only.md)
- [无运动与现场验收 Runbook](../../docs/integration/acceptance-runbook.md)

生产服务绑定 `127.0.0.1:8001`。Nginx 只公开 `/robot-api/`；Spring Boot 使用 `http://127.0.0.1:8001/bridge/v1`。当前 Spring 真实任务仍未接通 Bridge，禁止绕过 Spring 从公网创建 START。
