# Robot Platform v1: outbound heartbeat

公网服务器只验证 `ROBOT_AUTH_TOKENS_JSON` 的 Bearer Token，并持久化 robots、commands、events、executions。Jetson 主动 HTTPS 调用 heartbeat、command ACK、event batch 和 deployment 下载；服务器不保存 Jetson URL，也不访问 Jetson。

部署同步缓存 manifest、route、原始 YAML/PGM 文件名与 SHA-256 到 `storage/deployments/{deploymentId}`，状态 `READY_FOR_ROBOT`。Bridge 对设备下载验证 token 与 deployment 所属 robot，并返回安全固定文件名和 `Content-Length`。

命令使用 `requestId` 幂等，领取为带 leaseToken 的 `LEASED`；ACK 后 `ACKED`，执行状态只能由 route 事件更新，不能由 ACK 直接写 RUNNING/PAUSED。事件按 robot 全局 sequence 连续确认，缺口不确认，重复插入忽略。
