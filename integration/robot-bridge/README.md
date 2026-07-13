# Isolated Robot Bridge

该服务不读取平台数据库、不导入 Java 类，也不修改平台任务状态；它仅把已有平台 REST 资源部署到机器人并转发控制。

```bash
cd integration/robot-bridge
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
set -a; . .env; set +a
uvicorn app.main:app --host 127.0.0.1 --port 8001
```

按顺序：启动 Spring Boot，取得具备 `route:edit` 的临时 JWT；Jetson 在 `~/.config/ylhb/platform.env` 设置 `YLHB_ROBOT_ID=robot-001` 和 `YLHB_PLATFORM_API_TOKEN=token-placeholder`，安装并启动 Mobile Bridge；启动本服务；创建路线修订和 route deployment；调用 sync、start、status、events、pause/resume/cancel；查看 `/patrol/event` 和执行器日志。实机导航须由现场人员执行。

```bash
curl -X POST -H 'Authorization: Bearer token-placeholder' http://127.0.0.1:8001/bridge/v1/deployments/deploy-1/sync
curl -X POST -H 'Authorization: Bearer token-placeholder' -H 'Content-Type: application/json' -d '{"robotId":"robot-001","deploymentId":"deploy-1","executorRouteId":"route-1","requestId":"req-1"}' http://127.0.0.1:8001/bridge/v1/executions/exe-1/start
curl -H 'Authorization: Bearer token-placeholder' 'http://127.0.0.1:8001/bridge/v1/executions/exe-1/events?afterSequence=0&limit=100'
```

前端接入：路线部署按钮调 `POST /bridge/v1/deployments/{deploymentId}/sync`；任务控制按钮调对应 `POST /bridge/v1/executions/{executionId}/{start|pause|resume|takeover|cancel}`；使用 `suggestedPlatformState` 映射展示但不回写 TaskService；事件按返回 `sequence` 持久游标，下一次用它作 `afterSequence`。
