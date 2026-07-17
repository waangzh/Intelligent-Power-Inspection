# Robot Bridge 联调与验收 Runbook

> 协议以 [Robot Platform Protocol v1](../protocol/robot-platform-v1.md) 为准。禁止仅根据 HTTP `202` 判定成功；必须等待对应事件和 execution 状态。

## 0. 安全边界与记录方式

- 阶段 A-C 不创建 START，不调用 pause/resume/takeover/cancel，不发布 `/cmd_vel` 或 Nav2 goal。
- 阶段 D-H 可能导致机器人运动，必须由用户在现场明确授权并亲自执行；Agent 只能提供命令、观察项和停止条件。
- 所有 curl 示例使用占位凭据；真实凭据从受保护 env 或交互输入读取，不写 shell history、文档或工单。
- 每个阶段先填写版本、时间、执行人和回滚点；出现停止条件立即停止，不跨阶段“顺便测试”。

## 阶段 A：纯服务器，不连接机器人

### 前置条件

- Robot Bridge 已绑定 `127.0.0.1:8001`。
- systemd、env、SQLite 目录权限完成。
- Nginx 仅公开 `/robot-api/`，没有公开 `/bridge/`。
- Jetson Cloud Link 保持关闭。

### 执行命令

```bash
sudo systemctl status robot-bridge --no-pager
curl -sS -H 'Authorization: Bearer token-placeholder' \
  http://127.0.0.1:8001/bridge/v1/health
curl -i https://example.com/robot-api/v1/heartbeat \
  -H 'Content-Type: application/json' -d '{}'
sudo sqlite3 /var/lib/robot-bridge/robot-bridge.db \
  'select count(*) from commands; select count(*) from events;'
```

### 预期 HTTP

- 本机 health：`200`，`ok=true`。
- 公网无设备 Token heartbeat：`401 AUTH_FAILED`。
- 公网 `/bridge/v1/health`：Nginx `404` 或不匹配站点，不得到 Bridge `401/200`。

### 预期数据库状态

- schema_meta 存在，commands/events 初始为 0 或仅有历史数据。
- 不产生新 command、execution 或 event。

### 预期 Robot 状态

- 无机器人连接；不适用。

### 预期 UI 状态

- Jetson UI 不在线或 Cloud Link 为 DISABLED；平台不得显示假 ONLINE。

### 失败停止条件

- 8001 监听 `0.0.0.0`；公网 `/bridge/` 可达；health 非 200；SQLite 权限错误；日志打印 Token。

### 回滚方法

```bash
sudo systemctl stop robot-bridge
sudo ln -sfn /opt/robot-bridge/releases/<previous-commit> /opt/robot-bridge/current
sudo systemctl start robot-bridge
```

### 实际结果

- 时间：__________
- commit：__________
- health：__________
- 公网无 Token：__________
- `/bridge/` 暴露检查：__________
- 数据库：__________
- 结论：通过 / 不通过

## 阶段 B：Robot heartbeat，无运动

### 前置条件

- 阶段 A 通过。
- Jetson systemd 已配置 HTTPS URL、Robot Token、CA，`YLHB_CLOUD_ENABLED=true`。
- 巡检未运行，现场确认机器人不会因只启动 mobile bridge 运动。

### 执行命令

```bash
sudo systemctl restart ylhb-mobile-bridge
ros2 topic echo /mobile_bridge/cloud_status --once
ros2 service type /mobile_bridge/set_cloud_enabled
sudo journalctl -u ylhb-mobile-bridge -n 100 --no-pager
```

服务器本机：

```bash
curl -sS -H 'Authorization: Bearer token-placeholder' \
  http://127.0.0.1:8001/bridge/v1/robots/robot-001
```

### 预期 HTTP

- robot 查询：`200`，`configured=true`、`online=true`。
- heartbeat：服务器 access log 为 `200`；不得有 START command。

### 预期数据库状态

- robots 中 robot-001 的 last_seen 持续更新。
- commands 不新增；events 不因空闲心跳增长。

### 预期 Robot 状态

- Cloud status 为 CONNECTED。
- patrol state 保持 idle；pendingCommandCount=0。

### 预期 UI 状态

- 本体 UI 显示 CONNECTED、服务器为脱敏 example.com 形式、待上传事件数量。
- 关闭/开启开关可用，但本阶段不测试运动语义。

### 失败停止条件

- 出现任何运动、收到未知 command、Cloud BACKOFF 持续、TLS 校验失败、Token 出现在日志、存在两个 mobile bridge 进程。

### 回滚方法

```bash
ros2 service call /mobile_bridge/set_cloud_enabled std_srvs/srv/SetBool '{data: false}'
sudo systemctl stop ylhb-mobile-bridge
```

### 实际结果

- 时间：__________
- Jetson commit：__________
- cloud state：__________
- server online：__________
- pending command/event：__________
- 日志脱敏：通过 / 不通过
- 结论：通过 / 不通过

## 阶段 C：Deployment 下载，无运动

### 前置条件

- 阶段 B 通过。
- 平台已创建 `deployment-demo`，robotId 为 robot-001。
- 不创建 execution START。

### 执行命令

服务器本机同步：

```bash
curl -sS -X POST -H 'Authorization: Bearer token-placeholder' \
  http://127.0.0.1:8001/bridge/v1/deployments/deployment-demo/sync
```

设备下载只读验证：

```bash
curl -sS -H 'Authorization: Bearer token-placeholder' \
  https://example.com/robot-api/v1/deployments/deployment-demo/manifest
```

Jetson 检查本地缓存和哈希，不启动巡检：

```bash
find ~/.local/share/ylhb/platform/deployments/deployment-demo -maxdepth 1 -type f -printf '%f\n'
sha256sum ~/.local/share/ylhb/platform/deployments/deployment-demo/map.pgm
```

### 预期 HTTP

- sync：`200`，state=`READY_FOR_ROBOT`。
- manifest/route/yaml/pgm：正确 Token `200`；错误 Token `401`。

### 预期数据库状态

- Bridge deployment_mappings 有 deployment-demo、robot-001、READY_FOR_ROBOT。
- storage/deployments/deployment-demo 有 manifest.json、route.json、原始 YAML/PGM。
- commands/executions 不新增。

### 预期 Robot 状态

- 下载后本地 deployment state 为 DEPLOYED。
- route/map hash 与 manifest 一致；patrol 保持 idle。

### 预期 UI 状态

- 平台 deployment 显示同步成功/可下发；Task 仍 CREATED。

### 失败停止条件

- 任一哈希不一致、YAML image 不匹配、缓存内容与同 ID 旧内容冲突、出现 command 或机器人运动。

### 回滚方法

- 不覆盖冲突 deployment；创建新的平台 deploymentId。
- 若服务器 release 有问题，按阶段 A 回滚服务版本。
- Jetson 缓存清理由现场运维在确认无引用后执行，不在联调中自动删除。

### 实际结果

- 时间：__________
- deploymentId：__________
- routeRevisionContentSha256：__________
- routePayloadSha256：__________
- mapImageSha256：__________
- Robot 本地校验：通过 / 不通过
- 结论：通过 / 不通过

## 阶段 D：START 现场运动

### 前置条件

- 必须获得用户现场明确授权；阶段 A-C 全部通过。
- 急停可用，现场清场，机器人电量/底盘/CAN/雷达/IMU/TF/Nav2/地图/路线均由现场人员确认。
- Spring `app.robot.mode=bridge` 与事件轮询已部署并健康；不得绕过 Spring 直接创建 START。

### 执行命令

由现场用户通过平台 UI 执行 dispatch。只读观察：

```bash
ros2 topic echo /patrol/status
ros2 topic echo /patrol/event
ros2 topic echo /mobile_bridge/cloud_status
```

### 预期 HTTP

- Spring dispatch 成功；内部 Bridge START 为 `202`。
- 不能以 202 为通过，必须看到 route_started 与 execution RUNNING。

### 预期数据库状态

- command 依次 QUEUED/LEASED/ACKED，真实事件后 APPLIED。
- execution CREATED→DISPATCHING→RUNNING。

### 预期 Robot 状态

- 先完成路线/地图校验和 ROS 启动；随后按现场路线低速运动。
- `/patrol/event` 产生 route_started。

### 预期 UI 状态

- 202 后“下发中”；RUNNING 后“巡检中”。

### 失败停止条件

- 路线/地图 hash 失败、TF/Nav2 不就绪、方向错误、异常加速、障碍风险、route_started 超时、状态与实际运动不一致。现场人员立即急停。

### 回滚方法

- 现场急停或既有安全停止流程；不要依赖云 CANCEL 作为唯一安全手段。
- 停止后保存 ROS/Bridge/Spring 日志和 execution/event，不删除证据。

### 实际结果

- 用户授权时间：__________
- 现场执行人：__________
- commandId：__________
- route_started sequence：__________
- RUNNING 时间：__________
- 结论：通过 / 不通过

## 阶段 E：PAUSE / RESUME / TAKEOVER / CANCEL

### 前置条件

- 用户逐项现场授权；阶段 D 已安全进入 RUNNING。
- 每个动作使用独立 requestId，现场人员可随时急停。

### 执行命令

由现场用户在平台 UI 逐项执行；每项之间等待真实目标事件和状态，禁止连点。

### 预期 HTTP

- 每项内部 Bridge 返回 `202`；不作为通过。

### 预期数据库状态

- 每项有独立 commandId/requestId。
- route_paused→PAUSED；route_resumed→RUNNING；manual_takeover→MANUAL_TAKEOVER；route_canceled→CANCELLED。

### 预期 Robot 状态

- PAUSE 后安全停止；RESUME 后按路线继续；TAKEOVER 进入人工接管；CANCEL 结束 execution。

### 预期 UI 状态

- pending 文案先显示，真实事件后再显示目标状态；终态禁用控制。

### 失败停止条件

- 202 后长期无事件、动作与 ROS 状态不一致、重复 command、暂停后仍运动、取消后继续自动导航。

### 回滚方法

- 现场急停；停止测试；保留 command/event/ROS 日志。

### 实际结果

- PAUSE：__________
- RESUME：__________
- TAKEOVER：__________
- CANCEL：__________
- 结论：通过 / 不通过

## 阶段 F：断网继续巡检与恢复补传

### 前置条件

- 用户现场授权；机器人已安全 RUNNING；明确断网方式不会关闭 ROS/底盘安全服务。

### 执行命令

由现场用户操作网络断开/恢复。Agent 只观察本地 cloud status、patrol event 和服务器 accepted cursor。

### 预期 HTTP

- 断网期间无 heartbeat；恢复后 heartbeat `200`、events batch `200`。

### 预期数据库状态

- Jetson pendingEventCount 增长；服务器 cursor 暂停。
- 恢复后从 accepted cursor 连续补传，重复不重复应用。

### 预期 Robot 状态

- 断网不停止当前巡检；本地急停仍有效。

### 预期 UI 状态

- 平台 robot offline/unknown；恢复后 online，时间线补齐且无重复。

### 失败停止条件

- 断网导致非预期运动/停止策略、事件丢失、sequence 永久缺口、恢复后重复改变终态。

### 回滚方法

- 恢复网络；若 cursor 堵塞，停止后续运动测试并导出两端 SQLite/日志。

### 实际结果

- 断网开始/结束：__________
- 断网期间事件范围：__________
- acceptedThroughSequence：__________
- 重复事件：有 / 无
- 结论：通过 / 不通过

## 阶段 G：Jetson 重启恢复

### 前置条件

- 用户现场授权，明确重启时机器人应处于安全状态；记录 active execution 和本地 cursor。

### 执行命令

由现场用户按批准流程重启 Jetson/mobile bridge。只读观察 systemd、cloud status、patrol status。

### 预期 HTTP

- 新 bootId heartbeat `200`；不创建重复 START。

### 预期数据库状态

- Jetson 本地 command/event/deployment 保留；服务器 robot bootId 更新。
- ACKED/DISPATCHED 恢复依据真实 patrol evidence，不能凭猜测 APPLIED。

### 预期 Robot 状态

- 不出现未经授权的重复运动；恢复策略符合现场安全设计。

### 预期 UI 状态

- 短暂 offline 后恢复；execution 不被错误重置。

### 失败停止条件

- 重复 START、丢 deployment、cursor 回退、无证据把 command 标 APPLIED、意外运动。

### 回滚方法

- 停止 mobile bridge/巡逻，恢复上一 Robot release 与 SQLite 备份，保留日志。

### 实际结果

- 旧/new bootId：__________
- command 恢复：__________
- event cursor：__________
- 结论：通过 / 不通过

## 阶段 H：服务器重启恢复

### 前置条件

- 用户现场授权；Bridge SQLite 和 deployment storage 已备份；记录 command/event/execution。

### 执行命令

```bash
sudo systemctl restart robot-bridge
sudo systemctl status robot-bridge --no-pager
```

### 预期 HTTP

- 本机 health 恢复 200；Jetson 退避后 heartbeat 200。

### 预期数据库状态

- command、execution、events、accepted cursor 和 deployment cache 保留。
- 租约过期后可安全重投；已终态 command 不重投。

### 预期 Robot 状态

- 服务器重启不应停止本地正在执行的巡检；恢复后补传事件。

### 预期 UI 状态

- 短暂内部服务不可用/robot offline；恢复后状态与事件一致。

### 失败停止条件

- SQLite 损坏、deployment 丢失、终态回退、重复 command、Nginx 恢复后公开 `/bridge/`。

### 回滚方法

- 停止服务；恢复 SQLite、deployment storage 和上一 release；启动并先做阶段 A 检查。

### 实际结果

- 重启时间：__________
- health 恢复耗时：__________
- heartbeat 恢复耗时：__________
- cursor/queue：__________
- 结论：通过 / 不通过
