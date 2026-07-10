# 电力智能巡检平台 — 接口文档

> **项目**：`power-inspection-web`
> **版本**：1.0.0（前后端联调版；`/routes` 已切换为 ROS 地图路线标注）
> **说明**：当前已接入 Spring Boot REST 后端。前端 `src/api/*` 通过 HTTP 调用 `/api/v1`，Pinia Store 只保留页面状态与内存缓存；业务数据由后端持久化，浏览器 `localStorage` 仅保留登录会话 token。

---

## 1. 总体架构

```
Vue 页面
    ↓
Pinia Store（stores/*）  ← 页面状态与业务入口
    ↓
API 层（api/http.ts, api/auth.ts, api/profile.ts, api/resources.ts）
    ↓
Spring Boot REST API（/api/v1） + WebSocket/STOMP（/ws）
    ↓
MySQL / H2 测试数据库
```

| 层级 | 说明 |
|------|------|
| **认证** | JWT Token，前端 `pi_session` 保存会话 |
| **鉴权** | 后端 Spring Security + 前端路由守卫，基于 `UserRole` + `Permission` |
| **数据** | 后端数据库持久化，刷新后从 `/api/v1` 恢复 |
| **实时** | 后端调度器模拟任务进度，并通过 WebSocket/STOMP topic 推送；前端启动后订阅实时更新 |

---

## 2. 认证与会话

### 2.1 会话结构 `AuthSession`

| 字段 | 类型 | 说明 |
|------|------|------|
| `token` | string | 后端签发的 JWT |
| `user` | User | 当前登录用户 |
| `expiresAt` | number? | 勾选「记住我」时 7 天过期（毫秒时间戳） |

**存储 Key**：`pi_session`

### 2.2 演示账号

| 角色 | username | password | role |
|------|----------|----------|------|
| 管理员 | `admin` | `Admin@123` | `ADMIN` |
| 调度员 | `dispatcher` | `Disp@123` | `DISPATCHER` |
| 观察员 | `viewer` | `View@123` | `VIEWER` |

---

## 3. 权限体系

### 3.1 角色

```typescript
type UserRole = 'ADMIN' | 'DISPATCHER' | 'VIEWER'
```

### 3.2 权限点

```typescript
type Permission =
  | 'task:view'       // 查看任务
  | 'task:create'     // 创建任务
  | 'task:dispatch'   // 下发任务 / 工单
  | 'task:control'    // 暂停/恢复/接管/取消
  | 'site:edit'       // 站点管理
  | 'route:edit'      // 巡检规划
  | 'alarm:ack'       // 告警确认
  | 'robot:manage'    // 机器人管理
  | 'detection:manage'// 检测策略
  | 'user:manage'     // 用户管理
  | 'record:export'   // 记录导出
```

### 3.3 角色权限矩阵

| 权限 | ADMIN | DISPATCHER | VIEWER |
|------|:-----:|:----------:|:------:|
| task:view | ✅ | ✅ | ✅ |
| task:create / dispatch / control | ✅ | ✅ | ❌ |
| site:edit / route:edit | ✅ | ✅ | ❌ |
| alarm:ack | ✅ | ✅ | ❌ |
| record:export | ✅ | ✅ | ❌ |
| robot / detection / user:manage | ✅ | ❌ | ❌ |

**路由守卫**：未登录 → `/login`；无权限 → `/403`  
**用户管理**：额外限制 `roles: ['ADMIN']`

---

## 4. 前端 API 层接口（`src/api`）

前端统一通过 `api/http.ts` 调用后端。默认 `Base URL` 为 `/api/v1`，开发环境由 Vite 代理到 `http://localhost:8080`。

### 4.1 认证与用户 — `auth.ts`

#### `loginApi`

- **说明**：用户登录
- **参数**：`username: string`, `password: string`
- **返回**：`Promise<User>`
- **延迟**：300ms
- **错误**：`用户名或密码错误` / `用户不存在`

#### `registerApi`

- **说明**：注册新用户（默认角色 `VIEWER`）
- **参数**：`RegisterForm`

```typescript
interface RegisterForm {
  username: string        // 4～20 位，字母数字下划线
  password: string        // ≥8 位，含字母和数字
  confirmPassword: string
  displayName: string
  phone?: string
  agreed: boolean         // 必须 true
}
```

- **返回**：`Promise<User>`
- **错误**：用户名已存在、密码不一致、校验失败等

#### `listUsersApi`

- **说明**：获取用户列表
- **返回**：`User[]`（同步）

#### `updateUserRoleApi`

- **说明**：修改用户角色（仅管理员页面调用）
- **参数**：`userId: string`, `role: UserRole`
- **返回**：`Promise<User>`

#### `updateProfileApi`

- **说明**：更新用户资料
- **参数**：`userId: string`, `form: ProfileForm`

```typescript
interface ProfileForm {
  displayName?: string
  phone?: string
  bio?: string          // ≤80 字
  avatarUrl?: string
}
```

- **返回**：`Promise<User>`

#### `toggleUserEnabledApi`

- **说明**：启用/禁用用户（仅管理员）

#### `validateUsername` / `validatePassword`

- **说明**：校验工具函数，返回错误文案或 `null`

---

### 4.2 个人中心 — `profile.ts`

#### `getUserActivitiesApi`

- **参数**：`userId: string`
- **返回**：`UserActivity[]`（最多 200 条）

#### `getUserPreferencesApi`

- **参数**：`userId: string`
- **返回**：`UserPreferences`

```typescript
interface UserPreferences {
  notifyAlarm: boolean
  notifyTask: boolean
  notifySystem: boolean
  defaultSiteId?: string
  sidebarCollapsed: boolean
}
```

#### `saveUserPreferencesApi`

- **参数**：`userId: string`, `prefs: UserPreferences`
- **返回**：`Promise<UserPreferences>`

#### `changePasswordApi`

- **参数**：`user: User`, `form: ChangePasswordForm`

```typescript
interface ChangePasswordForm {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}
```

- **错误**：原密码不正确、新密码校验失败

#### `logUserActivity`

- **说明**：内部写入活动日志（登录、改资料等自动调用）

---

## 5. 业务 Store 接口

> 以下 Store 方法名基本保持原前端演示版不变，但数据来源已切换为后端 REST API；Store 内部只保存内存缓存，不再持久化核心业务数据到 `localStorage`。

### 5.1 认证 Store — `useAuthStore`

| 方法 | 说明 |
|------|------|
| `login(username, password, remember?)` | 登录并写 session |
| `register(form)` | 注册（不自动登录） |
| `logout()` | 清除 session |
| `restoreSession()` | 启动时恢复会话，并同步最新角色 |
| `updateProfile(form)` | 更新当前用户资料 |
| `patchUser(patch)` | 局部更新当前用户（如改角色后） |

**状态**：`token`, `user`, `isLoggedIn`

---

### 5.2 用户管理 — `useUserStore`

| 方法 | 说明 |
|------|------|
| `loadUsers()` | 加载用户列表 |
| `updateRole(userId, role)` | 修改角色 |
| `syncUser(updated)` | 同步单条用户到列表 |

---

### 5.3 个人中心 — `useProfileStore`

| 方法 | 说明 |
|------|------|
| `loadActivities(userId)` | 加载活动记录 |
| `loadPreferences(userId)` | 加载偏好 |
| `savePreferences(userId, prefs)` | 保存偏好 |
| `changePassword(user, form)` | 修改密码 |

---

### 5.4 站点 — `useSiteStore`

**数据来源**：后端 `/api/v1/sites`，Store 仅保存内存缓存。

| 方法 | 说明 |
|------|------|
| `addSite(site)` | 创建站点 |
| `updateSite(id, patch)` | 更新站点 |
| `removeSite(id)` | 删除站点及下属区域 |
| `addArea(area)` | 添加区域 |
| `removeArea(id)` | 删除区域 |
| `getSiteById(id)` | 查询站点 |
| `getAreasBySite(siteId)` | 查询站点下区域 |

**Site 模型**：

```typescript
interface Site {
  id: string
  name: string
  address: string
  description: string
  center: { lat: number; lng: number }
  createdAt: string
}
```

---

### 5.5 巡检路线 — `useRouteStore`

**数据来源**：后端 `/api/v1/routes`，Store 仅保存内存缓存。

**页面**：`/routes`（`RoutePlan.vue`）已改为 **ROS 建图路线标注**（`RosMapRouteEditor`），基于 `.yaml` + `.pgm` 地图在 Canvas 上标注起点、巡检点与方向，导出/保存 **route.json v2**（`frame_id: "map"`）。原 Leaflet 折线绘制、3D 视图、检查点云台/检测项配置已移除。

| 方法 | 说明 |
|------|------|
| `load()` | 从后端加载路线列表 |
| `createRoute(siteId, name, description?)` | 创建空路线 |
| `updateRoute(id, patch)` | 更新路线（含 `executorJson`、`checkpoints`、`path` 等） |
| `saveExecutorRoute(routeId, doc)` | **推荐**：保存 ROS 标注结果；写入 `executorJson`，并同步 `name`、`checkpoints`、`path` |
| `removeRoute(id)` | 删除路线 |
| `addCheckpoint` / `updateCheckpoint` / `removeCheckpoint` | 检查点 CRUD（Store 仍保留；`/routes` 页面不再直接调用） |
| `getRouteById(id)` | 查询路线 |
| `getRoutesBySite(siteId)` | 按站点查路线 |

**Route 模型**：

```typescript
interface Route {
  id: string
  siteId: string
  name: string
  description: string
  path: LatLng[]                    // 保存时由巡检点 position 同步，供监控/任务页兼容展示
  routeDetections: DetectionItem[]  // 保留字段，新建路线仍有默认值
  checkpoints: Checkpoint[]         // saveExecutorRoute 时由 targets 同步
  mapMode: '2d' | '3d'              // 保存时固定为 '2d'
  executorJson?: RouteExecutorDocument | null  // ROS 执行器路线 JSON（主数据）
  createdAt: string
}
```

**RouteExecutorDocument**（`src/types/routeExecutor.ts`，version 2）：

```typescript
interface RouteExecutorDocument {
  version: 2
  frame_id: 'map'
  active_route_id: string
  start_pose: {
    name: string
    pose: { x: number; y: number; yaw: number }   // ROS map 坐标（米、弧度）
    publish_initial_pose: boolean
    covariance: { x: number; y: number; yaw: number }
  }
  targets: Array<{
    id: string
    name: string
    pose: { x: number; y: number; yaw: number }
    task_duration_sec: number
  }>
  routes: Array<{
    id: string
    name: string
    target_ids: string[]           // 导航顺序
    return_to_start: boolean
    loop: { enabled: boolean; wait_sec: number; max_cycles: number }
    goal_timeout_sec: number
    max_retries_per_checkpoint: number
    failure_policy: 'abort_and_return_home' | 'abort'
  }>
  schedules: unknown[]
}
```

**坐标换算**（与 ROS map_server 一致）：

```text
x = origin_x + pixel_x * resolution
y = origin_y + (image_height - pixel_y) * resolution
```

**`/routes` 页面流程**：

1. 选择站点 → 新建/选择路线  
2. 上传 `.yaml` + `.pgm`（支持拖拽），可选导入已有 `.json`  
3. 模式：**起点** / **巡检点** / **方向** / **拖动**  
4. 点击 **保存到平台** → 调用 `saveExecutorRoute` → `PATCH /routes/{id}`  
5. 可 **复制 JSON** 或 **下载 route.json** 供机器人执行器加载  

**Checkpoint 兼容字段**（`saveExecutorRoute` 自动生成）：

| 字段 | 来源 |
|------|------|
| `id` | `targets[].id` |
| `name` | `targets[].name` |
| `seq` | `routes[0].target_ids` 顺序 |
| `position.lat` | `targets[].pose.y`（map 坐标 y） |
| `position.lng` | `targets[].pose.x`（map 坐标 x） |
| `pan` | `yaw` 转角度 |
| `dwellSeconds` | `task_duration_sec` |

> ⚠️ 监控/任务页的 `Map2D` 仍使用 Leaflet 地理坐标，**不会**显示 PGM 底图；ROS 地图标注仅在 `/routes` 页使用。YAML/PGM 文件由用户本地上传，**不**通过后端 API 存储。

**相关前端文件**：

| 路径 | 说明 |
|------|------|
| `src/views/RoutePlan.vue` | 路线列表 + 标注页容器 |
| `src/components/RosMapRouteEditor.vue` | 标注 UI |
| `src/composables/useRosMapRouteEditor.ts` | Canvas 交互逻辑 |
| `src/utils/rosMap.ts` | YAML/PGM 解析与坐标换算 |
| `src/utils/routeExecutorJson.ts` | JSON 导入/导出/下载 |

---

### 5.6 巡检任务 — `useTaskStore`

**数据来源**：后端 `/api/v1/tasks`、`/records`、`/tasks/{id}/events`，Store 仅保存内存缓存。

| 方法 | 说明 |
|------|------|
| `createTask(name, routeId, robotId)` | 创建任务，状态 `CREATED` |
| `dispatch(id)` | 下发任务，后端调度器随后推进到 `RUNNING` |
| `pause(id)` | 暂停 |
| `resume(id)` | 恢复 |
| `takeover(id)` | 人工接管 |
| `cancel(id)` | 取消 |
| `getTaskById(id)` | 查询任务 |
| `getActiveTask()` | 当前活跃任务 |
| `getEventsByTask(taskId)` | 任务事件流 |

**任务状态流转**：

```
CREATED → DISPATCHED → RUNNING ⇄ PAUSED / MANUAL_TAKEOVER → COMPLETED / CANCELLED
```

**模拟行为**（`RUNNING` 时）：

- 每 1.5s 进度 +4%，更新机器人位置
- 随机生成路线级/检查点级告警
- 到达检查点写入 `ARRIVE` / `INSPECT` / `DETECT` 事件
- 进度 100% 自动完成并生成巡检记录

**TaskStatus**：`CREATED | DISPATCHED | RUNNING | PAUSED | MANUAL_TAKEOVER | COMPLETED | CANCELLED`

---

### 5.7 告警 — `useAlarmStore`

**Key**：`pi_alarms`

| 方法 | 说明 |
|------|------|
| `addAlarm(partial)` | 新增告警，并推送全员通知 |
| `acknowledge(id)` | 确认单条 |
| `acknowledgeAll()` | 全部确认 |
| `maybeGenerateRouteAlarm(task, routeName)` | 内部：模拟路线告警 |
| `maybeGenerateCheckpointAlarm(...)` | 内部：模拟检查点告警 |

**Alarm 模型**：

```typescript
interface Alarm {
  id: string
  taskId: string
  routeName: string
  checkpointName?: string
  type: DetectionType
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  message: string
  imageUrl?: string
  acknowledged: boolean
  createdAt: string
}
```

---

### 5.8 工单 — `useWorkOrderStore`

**Key**：`pi_work_orders`

| 方法 | 说明 |
|------|------|
| `createFromAlarm(alarm, creator, assigneeName?)` | 从告警创建工单 |
| `updateStatus(id, status, extra?)` | 更新状态 |
| `assign(id, assigneeName)` | 指派处理人 |
| `getById(id)` / `getByAlarmId(alarmId)` | 查询 |

**WorkOrderStatus**：`PENDING → PROCESSING → REVIEW → CLOSED`（或 `CANCELLED`）  
**WorkOrderPriority**：`LOW | MEDIUM | HIGH | URGENT`

---

### 5.9 机器人 — `useRobotStore`

**数据来源**：后端 `/api/v1/robots`，实时状态通过 `/topic/robots` 推送。

| 方法 | 说明 |
|------|------|
| `addRobot(robot)` | 新增 |
| `updateRobot(id, patch)` | 更新 |
| `removeRobot(id)` | 删除 |
| `setPosition(id, position)` | 更新 GPS 位置 |
| `getRobotById(id)` | 查询 |

**Robot.status**：`ONLINE | OFFLINE | BUSY | CHARGING`

---

### 5.10 检测策略 — `useDetectionStore`

**Key**：`pi_detection_templates`

| 方法 | 说明 |
|------|------|
| `addTemplate(tpl)` | 新增模板 |
| `removeTemplate(id)` | 删除模板 |

**DetectionTemplate.scope**：`ROUTE | CHECKPOINT`

---

### 5.11 消息通知 — `useNotificationStore`

**数据来源**：后端 `/api/v1/notifications`，新通知通过 `/topic/notifications` 或 `/topic/notifications/{userId}` 推送。

| 方法 | 说明 |
|------|------|
| `push(userId, type, title, content, link?)` | 推送给指定用户 |
| `pushToAll(type, title, content, link?)` | 推送给所有用户（userId=`*`） |
| `forUser(userId)` | 获取用户可见通知 |
| `markRead(id)` / `markAllRead(userId)` | 标记已读 |
| `remove(id)` | 删除 |

**NotificationType**：`ALARM | TASK | WORKORDER | SYSTEM`

---

## 6. 前端本地存储约定

| Key | 内容 |
|-----|------|
| `pi_session` | 登录会话，包含 JWT token、当前用户和可选过期时间 |

核心业务数据（用户、站点、路线、任务、告警、工单、机器人、检测模板、通知、记录）由后端数据库持久化。

---

## 7. 前端页面路由

| 路径 | 页面 | 权限要求 |
|------|------|----------|
| `/login` | 登录 | 公开 |
| `/register` | 注册 | 公开 |
| `/dashboard` | 运行总览 | 登录 |
| `/monitor` | 实时监控 | 登录 |
| `/alarms` | 告警中心 | 登录 |
| `/workorders` | 工单管理 | `task:dispatch` |
| `/notifications` | 消息中心 | 登录 |
| `/sites` | 站点管理 | `site:edit` |
| `/routes` | ROS 地图路线标注（YAML/PGM + route.json v2） | `route:edit` |
| `/tasks` | 任务调度 | `task:view` |
| `/tasks/:id` | 任务详情 | `task:view` |
| `/robots` | 机器人管理 | `robot:manage` |
| `/detection` | 检测策略 | `detection:manage` |
| `/records` | 巡检记录 | 登录 |
| `/statistics` | 统计分析 | 登录 |
| `/users` | 用户管理 | `ADMIN` + `user:manage` |
| `/profile/*` | 个人中心 | 登录 |
| `/403` | 无权限 | 登录 |

---

## 8. 错误处理约定

| 场景 | 行为 |
|------|------|
| API 校验失败 | `throw new Error('中文错误信息')` |
| 页面捕获 | `ElMessage.error(e.message)` |
| 未登录访问 | 重定向 `/login?redirect=...` |
| 无权限 | 重定向 `/403` |

---

## 9. 真实后端 REST API

当前后端工程位于 `backend`，已按以下 REST 设计映射现有能力。

### 9.1 通用约定

```
Base URL: /api/v1
Authorization: Bearer {token}
Content-Type: application/json
```

**统一响应**：

```json
{
  "code": 0,
  "message": "ok",
  "data": { }
}
```

### 9.2 建议接口清单

| 模块 | Method | Path | 对应当前实现 |
|------|--------|------|-------------|
| 认证 | POST | `/auth/login` | `loginApi` |
| 认证 | POST | `/auth/register` | `registerApi` |
| 认证 | POST | `/auth/logout` | `logout` |
| 认证 | PUT | `/auth/password` | `changePasswordApi` |
| 用户 | GET | `/users` | `listUsersApi` |
| 用户 | PATCH | `/users/{id}/role` | `updateUserRoleApi` |
| 用户 | PATCH | `/users/me` | `updateProfileApi` |
| 用户 | GET | `/users/me/activities` | `getUserActivitiesApi` |
| 用户 | GET/PUT | `/users/me/preferences` | 偏好设置 |
| 站点 | CRUD | `/sites`, `/sites/{id}/areas` | `useSiteStore` |
| 路线 | CRUD | `/routes`, `/routes/{id}/checkpoints` | `useRouteStore` |
| 路线 | PATCH | `/routes/{id}`（body 含 `executorJson`） | `saveExecutorRoute` |
| 任务 | CRUD | `/tasks` | `useTaskStore` |
| 任务 | POST | `/tasks/{id}/dispatch` | `dispatch` |
| 任务 | POST | `/tasks/{id}/pause` | `pause` |
| 任务 | POST | `/tasks/{id}/resume` | `resume` |
| 任务 | POST | `/tasks/{id}/takeover` | `takeover` |
| 任务 | POST | `/tasks/{id}/cancel` | `cancel` |
| 任务 | GET | `/tasks/{id}/events` | `getEventsByTask` |
| 告警 | GET | `/alarms` | `useAlarmStore.alarms` |
| 告警 | POST | `/alarms/{id}/ack` | `acknowledge` |
| 告警 | POST | `/alarms/ack-all` | `acknowledgeAll` |
| 工单 | CRUD | `/work-orders` | `useWorkOrderStore` |
| 机器人 | CRUD | `/robots` | `useRobotStore` |
| 机器人 | GET | `/robots/{id}/telemetry` | 查询当前遥测快照 |
| 检测 | CRUD | `/detection-templates` | `useDetectionStore` |
| 记录 | GET | `/records` | `useTaskStore.records` |
| 记录 | POST | `/records/export` | 导出 CSV |
| 通知 | GET | `/notifications` | `forUser` |
| 通知 | PATCH | `/notifications/{id}/read` | `markRead` |

### 9.3 路线资源 `PATCH /routes/{id}` 扩展字段

后端以 JSON Map 存储路线，除原有 `name`、`path`、`checkpoints`、`routeDetections`、`mapMode` 外，前端保存标注时会额外写入：

| 字段 | 类型 | 说明 |
|------|------|------|
| `executorJson` | object | ROS 执行器 route.json v2，结构见 §5.5 `RouteExecutorDocument` |

**示例**（节选）：

```json
{
  "name": "本地巡逻路线",
  "mapMode": "2d",
  "executorJson": {
    "version": 2,
    "frame_id": "map",
    "active_route_id": "route_patrol_001",
    "start_pose": { "name": "初始起点", "pose": { "x": 0, "y": 0, "yaw": 0 } },
    "targets": [],
    "routes": [{ "id": "route_patrol_001", "target_ids": [], "return_to_start": true }]
  },
  "checkpoints": [],
  "path": []
}
```

`GET /routes`、`GET /routes/{id}` 返回体中若曾保存过标注，会包含 `executorJson`；`/routes` 页面加载时将其传给 `RosMapRouteEditor` 的 `initial-json`。

### 9.4 实时通道（WebSocket/STOMP）

``` 
/topic/tasks                 → 任务状态更新
/topic/tasks/{taskId}        → 单任务状态更新
/topic/task-events           → 任务事件流
/topic/robots                → 机器人状态/遥测更新
/topic/robots/{robotId}      → 单机器人状态/遥测更新
/topic/alarms                → 新告警推送
/topic/notifications         → 全员通知
/topic/notifications/{userId}→ 用户通知
```

---

## 10. 总结

| 问题 | 答案 |
|------|------|
| 有没有 HTTP API？ | **有**，`/api/v1` |
| 任务执行真实吗？ | **半真实**，后端调度器模拟进度、事件、机器人位置和记录 |
| 路线规划真实吗？ | **半真实**，`/routes` 为 ROS map 标注 + route.json v2；PGM/YAML 本地上传，平台持久化 `executorJson` |
| 检测/告警真实吗？ | **否**，后端随机模拟 + picsum 占位图 |
| 接口在哪？ | 前端 `src/api/*`，后端 `backend/src/main/java/com/powerinspection/*` |
