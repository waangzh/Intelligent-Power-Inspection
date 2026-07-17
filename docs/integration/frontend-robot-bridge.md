# Vue Web 与微信小程序对接 Robot Bridge

> 对象：Vue Web 与微信小程序同学。Robot Bridge 对前端不可见；协议字段与状态以 [Robot Platform Protocol v1](../protocol/robot-platform-v1.md) 为准。

## 1. 安全边界

前端禁止直接调用：

```text
/bridge/v1
/robot-api/v1
```

前端禁止配置、打包、缓存或打印：

```text
BRIDGE_API_TOKEN
ROBOT_AUTH_TOKENS_JSON
YLHB_CLOUD_ROBOT_TOKEN
```

浏览器和小程序只调用 Spring Boot `/api/v1`。机器人在线、任务状态和事件都由 Spring 整合后提供。

## 2. 统一响应

Vue Web 继续复用 `frontend/web/src/api/http.ts`。该封装只接受 Spring `ApiResponse`：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

Bridge 返回的是原始 JSON，因此不能绕过 Spring。Spring 应将 Bridge 成功或错误映射为现有平台响应；前端不要解析 Bridge 的 `acceptedThroughSequence`、leaseToken 或原始 Authorization 错误。

## 3. 推荐 API 调用

### 3.1 路线部署

```http
POST /api/v1/route-revisions/{revisionId}/deployments
Idempotency-Key: request-demo
Content-Type: application/json
```

```json
{"robotId": "robot-001"}
```

保存返回的 deployment ID，轮询：

```text
GET /api/v1/route-deployments/{deploymentId}
```

按钮重复点击必须复用同一个 `Idempotency-Key`；只有用户明确发起新的部署才生成新 key。

### 3.2 任务控制

```text
POST /api/v1/tasks
GET  /api/v1/tasks/{id}/start-eligibility
POST /api/v1/tasks/{id}/start
POST /api/v1/tasks/{id}/pause
POST /api/v1/tasks/{id}/resume
POST /api/v1/tasks/{id}/takeover
POST /api/v1/tasks/{id}/cancel
```

Bridge 模式创建任务必须提交 `name`、`robotId`、`routeRevisionId`。若同时提交 `routeId`，它必须与 revision 所属路线一致。只有最新、已发布且对所选机器人存在 `READY_FOR_ROBOT` 部署的 revision 才应出现在创建表单中；没有可用版本时禁用创建并引导用户先发布和同步部署。

控制接口成功只代表平台接受了请求或命令已入队，不代表机器人已经达到目标状态。

### 3.3 读取

```text
GET /api/v1/tasks/{id}
GET /api/v1/tasks/{id}/events
GET /api/v1/robots/{id}
GET /api/v1/robots/{id}/telemetry
```

robot online 只从上述 Spring 资源读取，不调用 `GET /bridge/v1/robots/{robotId}`。

## 4. 页面状态与文案

| Task status | 建议文案 | 说明 |
| --- | --- | --- |
| `CREATED` | 待启动 | 尚未创建 START command |
| `STARTING` | 启动请求中 | Spring/Bridge 已接受，尚未确认 route_started |
| `RUNNING` | 巡检中 | 已收到真实 route_started |
| `PAUSED` | 已暂停 | 已收到真实 route_paused |
| `MANUAL_TAKEOVER` | 人工接管 | 已收到真实 manual_takeover |
| `COMPLETED` | 已完成 | 终态 |
| `FAILED` | 执行失败 | 终态，展示错误 |
| `START_FAILED` | 启动失败 | 可重新核验资格并显式重试 |
| `CANCELLED` | 已取消 | 终态 |

不要新增前端 `QUEUED/LEASED/ACKED/APPLIED` 页面主状态；这些是后端/设备投递细节，可在管理员诊断区展示，但不能替代 Task status。

## 5. 按钮启用矩阵

| 状态 | 下发 | 暂停 | 恢复 | 接管 | 取消 |
| --- | --- | --- | --- | --- | --- |
| `CREATED` | 启动 | 禁止 | 禁止 | 禁止 | 禁止 |
| `START_FAILED` | 重试启动 | 禁止 | 禁止 | 禁止 | 禁止 |
| `STARTING` | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 |
| `RUNNING` | 禁止 | 允许 | 禁止 | 允许 | 允许 |
| `PAUSED` | 禁止 | 禁止 | 允许 | 禁止 | 允许 |
| `MANUAL_TAKEOVER` | 禁止 | 禁止 | 允许 | 禁止 | 允许 |
| `COMPLETED` | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 |
| `FAILED` | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 |
| `CANCELLED` | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 |

按钮 disabled 只是体验保护；权限和状态校验必须仍由 Spring 执行。

## 6. 关键交互语义

- start 返回 `202`：提示“命令已接受，等待机器人确认”，保持 `STARTING`。
- 只有收到 `RUNNING` 才显示“巡检中”并启动运行时长展示。
- pause 返回成功：提示“暂停命令已发送”，按钮进入 pending；等待 `PAUSED`。
- resume/takeover/cancel 同理，等待目标状态或错误事件。
- pending 期间禁止同按钮重复提交；页面刷新后以 REST 状态恢复，不靠本地布尔值。
- robot offline 时显示“机器人未在线”，不要泛化成“网络错误”。
- 失败时显示 `lastErrorCode` 和后端提供的用户可读 message；原始内部异常仅放可折叠诊断区域。
- TIMEOUT 不等于失败：提示“命令下发超时，最终状态以任务详情为准”，继续刷新详情。

## 7. Robot 在线状态

建议统一为：

```ts
type RobotConnectivity = 'ONLINE' | 'OFFLINE' | 'UNKNOWN'
```

- `ONLINE`：Spring 整合后的 Bridge 最近心跳在阈值内。
- `OFFLINE`：超过阈值或明确断开。
- `UNKNOWN`：后端尚未完成同步、Bridge 不可达或数据缺失。

不要根据浏览器能否请求 Spring 推断 robot online；也不要直接使用 Jetson IP、mobile bridge URL 或 Bridge health。

## 8. STOMP

继续订阅：

```text
/topic/tasks
/topic/tasks/{taskId}
/topic/robots
/topic/robots/{robotId}
```

如现有页面使用事件 topic，可继续订阅后端现有 `/topic/tasks/{taskId}/events` 或 `/topic/task-events`。后端处理 Robot sequence，前端只按业务事件 ID 去重。

Pinia 伪代码：

```ts
const seenEventIds = new Set<string>()

function onTaskUpdate(task: TaskExecutionSummary) {
  tasksById[task.taskId] = task
}

function onRobotUpdate(robot: { robotId: string; connectivity: RobotConnectivity }) {
  robotsById[robot.robotId] = { ...robotsById[robot.robotId], ...robot }
}

function onTaskEvent(event: TaskEvent) {
  if (seenEventIds.has(event.id)) return
  seenEventIds.add(event.id)
  eventsByTask[event.taskId] = [...(eventsByTask[event.taskId] ?? []), event]
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
}

async function restoreTaskPage(taskId: string) {
  const [task, events] = await Promise.all([
    http.get<TaskExecutionSummary>(`/tasks/${taskId}`),
    http.get<TaskEvent[]>(`/tasks/${taskId}/events`),
  ])
  onTaskUpdate(task)
  eventsByTask[taskId] = dedupeById(events)
}
```

STOMP 断线重连后必须先 REST 恢复，再继续实时订阅，避免漏事件。

## 9. TypeScript 类型示例

这些类型是前端业务模型，不是 Bridge 原始 schema：

```ts
export type TaskStatus =
  | 'CREATED'
  | 'DISPATCHED'
  | 'RUNNING'
  | 'PAUSED'
  | 'MANUAL_TAKEOVER'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

export type RobotConnectivity = 'ONLINE' | 'OFFLINE' | 'UNKNOWN'

export interface TaskExecutionSummary {
  taskId: string
  executionId: string
  deploymentId?: string
  routeRevisionId: string
  robotId: string
  status: TaskStatus
  progress?: number
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  updatedAt: string
}

export interface TaskEvent {
  id: string
  taskId: string
  type: string
  message: string
  createdAt: string
  checkpointName?: string
  imageUrl?: string
}

export interface TaskControlResponse {
  taskId: string
  executionId: string
  status: TaskStatus
  accepted: boolean
  message?: string
}
```

不要把 `leaseToken`、设备 Token、Bridge Admin Token 或 robot sequence 放进前端类型。

## 10. 错误提示矩阵

| HTTP/错误码 | 用户提示 | 前端动作 |
| --- | --- | --- |
| `401` | 登录已过期，请重新登录 | 清平台 session，跳登录 |
| `403` | 权限不足 | 保持页面，禁用无权限操作 |
| `409 IDEMPOTENCY_CONFLICT` | 请勿重复提交或刷新任务 | 停止重试，刷新详情 |
| `409 EXECUTION_CONFLICT` | 任务执行绑定冲突 | 停止控制，联系管理员 |
| `409 DEPLOYMENT_CONFLICT` | 路线部署内容发生变化 | 返回路线部署页重新核对 |
| `503 PLATFORM_UNREACHABLE` | 服务器内部服务暂不可用 | 可退避重试读取，不自动重放控制 |
| `ROBOT_OFFLINE` | 机器人未在线 | 禁用下发，保留刷新 |
| `TIMEOUT` | 命令下发超时，最终状态以任务详情为准 | 轮询详情，不判定失败 |

当前 `http.ts` 只保留 message，没有暴露 error code。后端接线时建议仍用统一 `ApiResponse`，并在 `data` 或稳定错误扩展字段中提供 code；不要让前端改成解析 Bridge 原始错误。

## 11. 页面验收清单

- 构建产物与浏览器存储中不存在三类设备/Bridge Token 名称和值。
- 网络面板只有 `/api/v1` 与 STOMP，没有 `/bridge/v1`、`/robot-api/v1`。
- 202/普通成功响应不会直接显示“巡检中”或“已暂停”。
- 页面刷新能从 REST 恢复；STOMP 重复消息不重复追加事件。
- offline、timeout、409 三类冲突和终态按钮均符合矩阵。
