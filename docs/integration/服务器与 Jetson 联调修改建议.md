# 机器人端与服务器升级、联调处置清单

本文仅记录 Jetson/ROS 端和服务器的操作与修改要求；不包含机器人端源码，也不替代现场安全规程。软件仓库中的 Bridge、Spring Boot 和 Web 修复应与以下变更配套发布。

## 1. 发布前约束

- 未获现场书面授权时，不创建 START、暂停、恢复、接管或取消命令。
- 保留故障 execution、Bridge SQLite、Jetson 本地事件库和 ROS 日志；不得删除、修改或跳过 sequence。
- 先完成无运动验证。当前健康快照中的 `nav2=not_running` 或传感器诊断未就绪时，禁止进入实机运动验收。
- 不在命令、日志、截图或本文档中记录任何 token、密码、私钥或完整 Authorization 头。

## 2. Jetson/ROS 端需要修改的内容

这些修改在机器人仓库完成，不在本仓库实现。

### 2.1 Deployment 路线哈希

下载 `route` 后，先对 HTTP 响应的原始 UTF-8 字节计算 SHA-256，并与 manifest 中的 `routePayloadSha256`、`routeRevisionContentSha256` 比较；两者不一致时禁止安装和发布 ROS 巡逻命令。

随后再解析 JSON、校验路线 schema，并将校验过的内容放入 staging 目录后原子安装。不得对 pretty JSON、ROS 转换后的文件或重新写入的路线文件计算上述两个哈希。

### 2.2 失败事件必须完整上报

每条事件，包括 `command_failed`，都必须带齐以下字段并保持 snake_case：

```text
schema_version = "1.0"
robot_id
boot_id
sequence
event
execution_id
deployment_id
request_id
command_id
occurred_at
```

- `robot_id` 必须是 Jetson/Bridge ID，例如 `robot-001`；不要发送平台内部 ID `robot_001`。
- `request_id` 和 `command_id` 必须直接取自 heartbeat 领取的同一条 command。
- `sequence` 由 Jetson 本地事件库按 robot 全局正整数递增分配，不能按 execution 重置。
- `command_failed` 额外带脱敏的 `error_code` 和 `error_message`；哈希失败使用 `ROUTE_HASH_MISMATCH`。
- Bridge 不会再从 heartbeat 或命令记录补齐遗漏字段；字段不完整会收到 `400 INVALID_REQUEST`，且不会推进服务器 cursor。

### 2.3 Jetson 无运动验证

1. 仅启动 Cloud Link，确认 heartbeat 为 200，且 Bridge 查询显示 `configured=true`、`online=true`。
2. 只下载 manifest、route、YAML、PGM，完成哈希与 staging 原子安装检查；不要创建 START。
3. 人为使用无效哈希或测试 deployment 时，确认本地安装被拒绝并产生一条完整的 `command_failed` 事件。
4. 确认 Bridge 已接收该事件后，再恢复正常 deployment；不得通过手动修改 cursor 解决 sequence 问题。

## 3. 服务器需要操作的内容

### 3.1 发布顺序

1. 先完成 Jetson 端的上述修改和无运动自测。
2. 发布本仓库的 Robot Bridge 更新；它会严格校验事件字段和 command 归属，并支持失败 START 的安全重试。
3. 发布 Spring Boot 更新；它会把 Bridge ID 反向映射为平台 ID 后再摄取事件。
4. 发布 Web 更新；`READY_FOR_ROBOT` 会显示为“Bridge 已就绪，待机器人领取任务”。

在切换 Bridge 前，按既有部署手册备份 `/var/lib/robot-bridge/robot-bridge.db` 及 deployment 缓存。生产 Bridge 必须保持仅监听 `127.0.0.1:8001`，Nginx 只公开 HTTPS 的 `/robot-api/`，不得公开 `/bridge/`。

### 3.2 配置核对

- Spring 使用 `APP_ROBOT_MODE=bridge`，并通过本机 `http://127.0.0.1:8001` 调用 Bridge 管理 API。
- 平台 ID `robot_001` 与设备 ID `robot-001` 的一对一映射必须保持有效。
- Spring 的 Bridge admin token、Bridge 的平台回读 token 和 Jetson robot token 各自独立配置；不向浏览器或 Jetson 暴露 admin token。
- 确认 Bridge 到 Spring 的平台回读可用，且 HTTPS 证书、DNS、NTP/chrony 正常。

### 3.3 无运动验收

```text
Bridge 本机 health：200
公网无 robot token 的 heartbeat：401 AUTH_FAILED
公网 /bridge/v1/health：404 或未路由
Bridge robot 查询：configured=true、online=true
```

随后执行一次 deployment 同步和 Jetson 下载校验。只有看到哈希校验通过、Jetson 本地 atomic install 成功且 Nav2/传感器诊断满足现场条件，才可以按现场授权进行 START 验收。HTTP 202 与 ACK 都不能代表 ROS 已开始巡检；必须等待真实 `route_started` 事件。

## 4. 当前故障 execution 的处置

`exec_1784190394716_atqpzku` 已存有缺失身份字段的历史事件，且其路线哈希校验失败。不要对该 execution 重试、删除事件或手工推进 cursor。

完成 Jetson 与服务器升级后，保留该 execution 作为审计证据，创建新的任务/execution；如需要重新部署，则创建新的 deploymentId，不能覆盖原 deployment。新 execution 的首次无运动校验通过后，才可进入后续现场流程。

## 5. 验收记录

每次联调至少记录：Bridge/Spring/Jetson 版本或提交、deploymentId、executionId、三类哈希、commandId、事件 sequence、Bridge accepted cursor、Jetson 本地安装结果及 ROS 诊断摘要。记录中不得包含凭据。
