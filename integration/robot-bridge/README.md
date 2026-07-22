# Isolated Robot Bridge

独立 FastAPI Robot Bridge：Jetson 主动通过 HTTPS heartbeat 领取命令、ACK、补传事件和下载 deployment；Bridge 不反向连接 Jetson。

Heartbeat 可携带顶层 `executionId`、`patrol` 和 `gnssFix`。Bridge 会柔性校验 GPS：异常 GPS 只会被丢弃并记录告警，不会拒绝 heartbeat 或阻断命令拉取；最新标准化快照可由本机管理接口 `GET /bridge/v1/robots/{robotId}` 读取。

设备地图上传使用 `POST /robot-api/v1/map-assets`：Bridge 校验设备 Token 和大小，写入受控临时目录后通过 `httpx` 流式转发到 Spring 内部接口。生产启用需设置 `BRIDGE_MAP_UPLOAD_ENABLED=true`、上传超时/大小和 `BRIDGE_MAP_UPLOAD_TEMP_DIR`；所有退出路径都会清理临时文件。

三维点云上传使用 `POST /robot-api/v1/scene-assets`，首期只接受 `pointcloud.ply` 与 `metadata.json`。生产启用需设置 `BRIDGE_SCENE_UPLOAD_ENABLED=true`；可分别通过 `BRIDGE_SCENE_UPLOAD_MAX_MODEL_BYTES`、`BRIDGE_SCENE_UPLOAD_MAX_METADATA_BYTES`、`BRIDGE_SCENE_UPLOAD_REQUEST_MAX_BYTES`、`BRIDGE_SCENE_UPLOAD_CONNECT_TIMEOUT_SEC`、`BRIDGE_SCENE_UPLOAD_READ_TIMEOUT_SEC` 和 `BRIDGE_SCENE_UPLOAD_TEMP_DIR` 控制上限、超时和临时目录。机器人重试同一次上传时必须复用原 `Idempotency-Key`。

巡检图片上传使用 `POST /robot-api/v1/inspection-images`。机器人提交 `image`、`executionId`、`taskId`、`checkpointId`、`capturedAt`、`imageSha256` 和持久化生成的 `Idempotency-Key`；机器人身份只由设备 Token 确定，不能在表单中指定 `robotId`。生产设置 `BRIDGE_INSPECTION_IMAGE_UPLOAD_ENABLED=true`、独立连接/读取超时、20 MiB 上限和受控临时目录；Bridge 校验哈希和大小后转发到 Spring，并在所有退出路径清理临时文件。

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
