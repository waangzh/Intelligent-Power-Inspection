# Isolated Robot Bridge

独立 FastAPI Robot Bridge：Jetson 主动通过 HTTPS heartbeat 领取命令、ACK、补传事件和下载 deployment；Bridge 不反向连接 Jetson。

设备地图上传使用 `POST /robot-api/v1/map-assets`：Bridge 校验设备 Token 和大小，写入受控临时目录后通过 `httpx` 流式转发到 Spring 内部接口。生产启用需设置 `BRIDGE_MAP_UPLOAD_ENABLED=true`、上传超时/大小和 `BRIDGE_MAP_UPLOAD_TEMP_DIR`；所有退出路径都会清理临时文件。

巡检图片上传使用 `POST /robot-api/v1/inspection-images`。机器人提交 `image`、`executionId`、`taskId`、`checkpointId`、`capturedAt`、`imageSha256` 和持久化生成的 `Idempotency-Key`；机器人身份只由设备 Token 确定，不能在表单中指定 `robotId`。启用前设置 `BRIDGE_INSPECTION_IMAGE_UPLOAD_ENABLED=true`，Bridge 校验哈希和大小后转发到 Spring，并在所有退出路径清理临时文件。

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

生产服务绑定 `127.0.0.1:8001`。Nginx 只公开 `/robot-api/`；Spring Boot 使用 `http://127.0.0.1:8001/bridge/v1`。地图上传只进入 `PENDING_REVIEW`，不会创建 START、路线或部署。
