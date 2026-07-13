# Robot Platform v1: outbound heartbeat

公网服务器只验证 `ROBOT_AUTH_TOKENS_JSON` 的 Bearer Token，并持久化 robots、commands、events、executions。Jetson 主动 HTTPS 调用 heartbeat、command ACK、event batch 和 deployment 下载；服务器不保存 Jetson URL，也不访问 Jetson。

部署同步缓存 manifest、route、原始 YAML/PGM 文件名与 SHA-256 到 `storage/deployments/{deploymentId}`，状态 `READY_FOR_ROBOT`。Bridge 对设备下载验证 token 与 deployment 所属 robot，并返回安全固定文件名和 `Content-Length`。

命令使用 `requestId` 幂等，leaseToken、leaseUntil、attemptCount 等投递字段不属于业务载荷。领取为带 leaseToken 的 `LEASED`；ACK 只写 `ACKED`，不能代表 APPLIED。机器人 ACK 后的忙碌、非法状态和队列拒绝通过 `command_rejected` 上报，下载、哈希、文件、SQLite 或 ROS publish 异常通过 `command_failed` 上报。

事件按 robot 全局 sequence 连续确认。缺口后的事件可以先持久化，但只有首次进入连续区间时才处理；旧重复事件不再应用。START/PAUSE/RESUME/TAKEOVER/CANCEL 分别由 route_started/route_paused/route_resumed/manual_takeover/route_canceled 写 APPLIED，execution 和 command 终态吸收旧事件。
