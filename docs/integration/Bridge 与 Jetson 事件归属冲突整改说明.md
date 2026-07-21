# Bridge 与 Jetson 事件归属冲突整改说明

## 1. 文档目的

本文用于指导 Bridge 与 Jetson/机器人端修复巡检启动过程中出现的以下故障：

```text
EVENT_OWNERSHIP_CONFLICT：Bridge 事件归属校验失败
```

典型现象是机器人已经收到启动命令并开始巡检，但 Web 页面显示启动失败。根因不是机器人没有执行，而是机器人上报的生命周期事件与 Bridge 保存的命令归属信息不一致，Bridge 拒绝接收事件；平台因此无法用可信事件确认任务状态。

本文只描述 Bridge 和 Jetson/硬件侧需要完成的修改，不直接修改这些模块的代码。平台 Spring 后端与 Web 的容错修复应独立发布，不能代替协议两端的整改。

协议事实来源：

- `docs/protocol/robot-platform-v1.md`
- `docs/integration/state-event-contract.md`
- `docs/integration/acceptance-runbook.md`
- `docs/integration/服务器与 Jetson 联调修改建议.md`

本文按硬件仓库提交 `4b7c131b4fbcf6ad96b86c18c91bcd555d95571d` 的实际目录和实现校对。硬件端事件序号由 `src/ylhb_mobile_bridge/ylhb_mobile_bridge/platform_store.py` 的 SQLite `events.sequence` 生成；下文的幂等和字段要求以当前软件 Bridge 的接口实现为基准，不能把示例字段当成现有代码已经实现。

## 2. 事件归属规则

每条机器人事件必须能够唯一追溯到“哪个机器人、哪次启动、哪个执行、哪次部署、哪个平台请求和哪条命令”。事件上报必须包含以下字段：

| 字段 | 要求 |
| --- | --- |
| `schema_version` | 固定为协议版本 `1.0` |
| `robot_id` | Bridge 机器人 ID，例如 `robot-001` |
| `boot_id` | 当前机器人进程/系统启动实例的稳定 ID |
| `sequence` | 机器人维度全局、持久、严格递增的事件序号；当前 Bridge 的持久化主键是 `(robot_id, sequence)` |
| `event` | 协议定义的事件名称 |
| `execution_id` | 平台下发的执行 ID，原样回传 |
| `deployment_id` | 平台下发的部署 ID，原样回传 |
| `request_id` | 产生该事件的命令请求 ID，原样回传 |
| `command_id` | 产生该事件的命令 ID，原样回传 |
| `occurred_at` | 带时区的事件实际发生时间 |

身份字段不得由 Bridge 根据 Token、最近一次 heartbeat、当前 execution 或最近命令自动猜测和补齐。否则错误事件可能被绑定到不相关的执行，破坏审计和状态机一致性。

## 3. Bridge 必须修改的内容

### 3.1 恢复事件入口的完整协议校验

修改位置：`integration/robot-bridge/app/main.py` 中的 `/robot-api/v1/events/batch`。

Bridge 必须在写入事件前完成以下校验：

1. 校验第 2 节列出的全部必填字段存在、类型正确且非空。
2. 校验 `schema_version` 等于 `1.0`。
3. 校验 `robot_id` 与当前认证机器人一致。
4. 校验 `sequence` 为合法正整数，并按协议处理重复、连续和缺口；`boot_id` 只用于诊断，不参与当前事件主键。
5. 校验事件引用的 `execution_id`、`deployment_id`、`request_id` 和 `command_id` 可以共同对应到同一条已下发命令。
6. 校验失败时不得写入事件、不得推进事件游标、不得改变命令或执行状态。

当前 `main.py` 会用认证机器人 ID 兜底缺失的 `robot_id`，并兼容读取 `robotId`。严格协议启用后必须移除该兜底：机器人事件只接受 snake_case `robot_id`，缺失或 camelCase 均按格式错误处理。

缺字段、字段类型错误、非法协议版本等请求格式问题应返回：

```text
HTTP 400
code: INVALID_REQUEST
```

机器人、执行、部署、请求或命令之间存在归属冲突时应返回：

```text
HTTP 409
code: EVENT_OWNERSHIP_CONFLICT
```

响应中应包含可定位的字段级原因，但不得回显 Token、Authorization 或其他凭据。

### 3.2 补全命令与请求的交叉校验

修改位置：`integration/robot-bridge/app/store.py`。

Bridge 在验证 `command_id` 时，不能只确认命令存在，还必须确认：

- 命令属于事件中的 `robot_id`。
- 命令属于事件中的 `execution_id` 和 `deployment_id`。
- 命令记录的 `request_id` 与事件中的 `request_id` 完全一致。
- 当前 `store._validate_event()` 尚未检查 `request_id`，必须补上该交叉校验。
- 事件类型符合该命令的生命周期阶段。例如，`route_started` 必须关联触发本次启动的 START 命令。

任一条件不满足均返回 `409 EVENT_OWNERSHIP_CONFLICT`，并保持数据库状态不变。

### 3.3 保持幂等和顺序语义

- 同一 `robot_id + sequence` 的完全相同事件重复上传，应按幂等成功处理，不得重复应用状态变化；`boot_id` 变化不能让同一机器人重新开始编号。
- 相同序号但 payload 不同，应视为冲突并拒绝。
- 出现 sequence 缺口时，不得跳过缺失事件推进 cursor。
- Bridge 重启后必须从持久化状态恢复游标，不能仅依赖内存。

当前 `accept_events()` 使用 `INSERT OR IGNORE`，会把“相同 `(robot_id, sequence)`、不同 payload”静默当成已存在。整改时应先查询并比较规范化事件内容：完全一致才幂等成功，内容不同返回 `409 EVENT_SEQUENCE_CONFLICT`（或统一映射为 `EVENT_OWNERSHIP_CONFLICT`），且不得继续应用该批次。

### 3.4 恢复 Bridge 负例测试

至少补充或恢复以下自动化/smoke 用例：

| 场景 | 预期结果 |
| --- | --- |
| 缺少任一必填身份字段 | `400 INVALID_REQUEST` |
| `request_id` 与命令记录不一致 | `409 EVENT_OWNERSHIP_CONFLICT` |
| `command_id` 属于另一 execution | `409 EVENT_OWNERSHIP_CONFLICT` |
| `robot_id` 与认证机器人不一致 | `409 EVENT_OWNERSHIP_CONFLICT` |
| sequence 中间缺失 | 不推进 cursor，不应用后续状态 |
| 完全相同事件重复上传 | 幂等成功，仅应用一次 |
| 相同 sequence、不同 payload | 拒绝且不改变状态 |

## 4. Jetson/硬件端必须修改的内容

### 4.1 原样保存并回传命令身份

硬件仓库实际相关文件为：

```text
src/ylhb_mobile_bridge/ylhb_mobile_bridge/platform_cloud_client.py
src/ylhb_mobile_bridge/ylhb_mobile_bridge/platform_store.py
src/ylhb_mobile_bridge/ylhb_mobile_bridge/ros_bridge.py
src/ylhb_llm/ylhb_llm/system_supervisor_node.py
```

Jetson 从 heartbeat 响应取得命令后，必须将以下字段作为一个不可拆分的命令上下文保存：

```text
robot_id
execution_id
deployment_id
request_id
command_id
command type
```

当前 Bridge 在 `/bridge/v1/executions/{execution_id}/{action}` 入队时生成 `commandId`，heartbeat 返回扁平命令对象（`commandId/requestId/type/executionId/deploymentId/...`）。因此 Jetson 只能保存并回传 Bridge 返回的 `commandId`，不能按文档示例自行生成，也不能假设存在嵌套 `payload`。

随后产生的事件必须使用同一命令上下文中的原始值。不得重新生成 `request_id` 或 `command_id`，不得从另一条命令、旧缓存或当前全局状态拼装字段。

命令与事件的绑定规则至少包括：

- 启动接收、启动成功、路线开始和启动失败事件绑定 START 命令。
- 暂停结果事件绑定 PAUSE 命令。
- 恢复结果事件绑定 RESUME 命令。
- 停止/取消结果事件绑定对应的 STOP/CANCEL 命令。
- `command_failed` 绑定实际执行失败的那一条命令。

### 4.2 使用正确的机器人标识

事件中的 `robot_id` 必须使用 Bridge 侧配置和认证使用的机器人 ID：

```text
robot-001
```

不能使用平台展示 ID 或数据库内部 ID，例如 `robot_001`。Jetson 应使用单一配置来源，heartbeat、事件和日志中的机器人标识保持一致。

### 4.3 事件字段和命名必须符合契约

- JSON 字段统一使用 `snake_case`。
- 每条事件都携带第 2 节列出的完整身份字段。
- `occurred_at` 使用带时区的标准时间格式。
- 不发送空字符串占位，也不依赖 Bridge 自动补字段。
- `command_failed` 同样携带完整身份字段；错误信息应可诊断但必须脱敏。

### 4.4 sequence 必须持久、全局递增

`sequence` 是机器人事件流的顺序号，不是 execution 内的计数器。当前硬件 `PlatformStore` 使用 SQLite `AUTOINCREMENT`，Bridge 使用 `(robot_id, sequence)` 维护游标，因此：

- 在同一机器人事件流内全局递增，不得在新 execution 开始时重置。
- 在本地持久化，Jetson 进程或设备重启后继续递增；`boot_id` 不能作为重置序号的理由。
- 事件应先连同完整 payload 写入本地可靠队列，再尝试上传。
- 网络失败或 Bridge 暂时拒绝时，重试必须复用原 sequence 和原 payload，不得生成一个内容相同但序号不同的新事件。
- 前序事件未确认前，不得丢弃它并直接确认后续事件。

### 4.5 路线部署内容校验

- 当前已核对的两端实现都不是直接对 HTTP 原始字节计算：软件 Bridge 在 `integration/robot-bridge/app/main.py:154-162` 对 `executorJson` 调用 `canonical()` 后生成 `routePayloadSha256`，硬件在 `src/ylhb_mobile_bridge/ylhb_mobile_bridge/platform_store.py:193-200` 解析 JSON 后调用 `canonical_json()` 再校验。因此不存在“软件原始字节、硬件 canonical JSON”的已证实差异；真正需要验收的是 Python 与 Java 的 canonical JSON 规则（键排序、数组顺序、数字和 Unicode 序列化）是否产生完全相同的 SHA-256。
- 哈希、部署 ID 或路线内容校验失败时，不得启动 ROS 巡检流程。
- 校验失败应使用当前 START 命令的完整身份上下文上报失败事件。
- 本地日志中记录哈希、ID 和错误分类即可，不得记录认证凭据或敏感路线数据。

## 5. 历史故障 execution 的处理

已经出现归属冲突的 execution 应视为审计对象，不应通过修改历史数据伪造成成功：

- 不删除 Bridge 或平台已经保存的事件。
- 不直接修改 Bridge SQLite 中的命令、事件、cursor 或归属字段。
- 不人为跳过缺失 sequence。
- 不使用修复后的 Jetson payload 重试旧 execution，避免将旧命令重新驱动到真实机器人。
- 保留现有 execution 及其错误信息用于审计。
- Bridge 和 Jetson 升级完成后，在平台创建新的 task/execution/deployment 进行验证。

## 6. 推荐发布顺序

1. 完成 Jetson 事件组装、命令上下文持久化和 sequence 持久化修改。
2. 在断开运动输出或仿真环境中执行无运动验证，确认不会因测试命令驱动实体机器人。
3. 备份 Bridge SQLite 数据库和 deployment 缓存。
4. 部署 Bridge 的严格校验和归属校验修改，并运行负例测试。
5. 发布平台 Spring 后端和 Web 的人工对账容错修改。
6. 创建全新的 task/execution/deployment，完成端到端启动、暂停、恢复、停止和异常场景验收。

不建议先单独启用 Bridge 严格校验再升级 Jetson；旧 Jetson 如果仍缺少字段，会被正确拒绝，但会扩大现场不可用范围。

## 7. 端到端验收矩阵

| 验收项 | 预期结果 |
| --- | --- |
| 事件缺少身份字段 | Bridge 返回 `400 INVALID_REQUEST`，状态和 cursor 不变 |
| `request_id` 与 `command_id` 不匹配 | Bridge 返回 `409 EVENT_OWNERSHIP_CONFLICT`，状态不变 |
| 事件使用错误 `robot_id` | Bridge 拒绝，不能归属到其他机器人 |
| 正确上报 `route_started` | Bridge 与 Spring 均将对应 execution 推进为 `RUNNING` |
| 正常启动巡检 | Web 不显示“启动失败” |
| 无法可信归属的历史/异常事件 | Web 显示“待人工对账”，不显示为业务执行失败 |
| 完全相同事件重复上传 | 以 `(robot_id, sequence)` 幂等，Bridge 与平台状态只推进一次 |
| sequence 存在缺口 | Bridge 不确认后续 cursor，Jetson 重传缺失事件 |
| Jetson 重启后继续上报 | sequence 不回退、不重置，待发送 payload 不变化 |
| 路线哈希错误 | ROS 不启动，并上报带完整命令身份的失败事件 |

验收时应核对同一事件在 Jetson 本地队列、Bridge 事件表、Bridge 日志和平台日志中的以下字段完全一致：

```text
robot_id / boot_id / sequence / execution_id / deployment_id / request_id / command_id
```

## 8. 回滚与安全要求

发布前必须备份 Bridge SQLite 数据库和 deployment 缓存，并记录对应版本。若 Bridge 升级后需要回滚：

1. 停止新的命令下发和机器人运动。
2. 保留升级期间产生的数据库与日志副本，不覆盖审计数据。
3. 回滚 Bridge 程序版本时确认数据库格式兼容；不通过手工改库恢复业务。
4. 回滚后仅进行无运动健康检查，重新评估事件协议兼容性后再恢复任务。

所有测试数据、日志、截图和问题报告均不得包含 Token、Authorization、密码或其他凭据。Jetson 修改在完成无运动验证前，不得连接真实运动控制链路执行启动测试。
