# 电力智能巡检平台 — 微信小程序接口文档

> **项目**：`power-inspection-wechat`  
> **版本**：1.0.0  
> **后端**：与网页端（`frontend/web`）**共用同一 REST API**（`/api/v1`）  
> **Mock**：仅通过构建变量启用（见下文），默认对接真实后端

---

## 1. 总体架构

```
小程序页面 (pages/*)
    ↓
services/index.js        ← 统一业务入口（generated api-client）
    ↓
┌─────────────────────────┬─────────────────────────┐
│ mockMode=none           │ mockMode=openapi        │
│ HTTP → 共用后端         │ HTTP → Prism Mock       │
│ utils/request.js        │ utils/request.js        │
└─────────────────────────┴─────────────────────────┘
```


| 配置项       | 来源                          | 说明                                      |
| --------- | --------------------------- | --------------------------------------- |
| `baseUrl` | `build-env.js` / `api.local.js` | 后端根路径，默认 `http://localhost:8080/api/v1` |
| `mockMode`| **`npm run miniprogram:env`** 生成 | `none`（真实后端）/ `openapi`（Prism 演示） |
| `timeout` | `api.js`                      | 请求超时毫秒数                                 |




### 切换为共用后端（默认）

```bash
# 仓库根目录
npm run miniprogram:env
```

或在 `miniprogram/config/api.local.js` 中仅覆盖 `baseUrl`（勿设置 `mockMode`）。

### 启用 OpenAPI Mock Server（Prism，无后端演示）

```bash
# 终端 1：启动 Prism
npm run mock:openapi

# 终端 2：生成小程序 env
npm run miniprogram:env:mock
```

---



## 2. HTTP 通用约定（共用后端）

与 `frontend/web/docs/API.md` 第 9 章一致：

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

**错误**：`code !== 0` 时 `message` 为中文错误信息；HTTP 401 清除本地 `pi_session` 并提示重新登录。

---



## 3. 认证与会话



### 3.1 会话结构


| 字段          | 类型      | 说明               |
| ----------- | ------- | ---------------- |
| `token`     | string  | JWT 或 mock token |
| `user`      | User    | 当前用户             |
| `expiresAt` | number? | 记住我 7 天过期时间戳     |


**存储 Key**：`pi_session`（wx.storage）

### 3.2 演示账号（后端 test profile）

与 Web 端一致，见仓库根目录 README「默认账号」：

| 用户名          | 密码          | 角色         |
| ------------ | ----------- | ---------- |
| `admin`      | `Admin@123` | ADMIN      |
| `dispatcher` | `Disp@123`  | DISPATCHER |
| `viewer`     | `View@123`  | VIEWER     |




### 3.3 Service 方法 — `services/index.js`


| 方法                                     | HTTP                        | 说明            |
| -------------------------------------- | --------------------------- | ------------- |
| `login(username, password, remember?)` | `POST /auth/login`          | 登录并写入 session |
| `register(form)`                       | `POST /auth/register`       | 注册，默认 VIEWER  |
| `getSession()`                         | —                           | 读取本地 session  |
| `logout()`                             | `POST /auth/logout`         | 清除 session    |
| `updateProfile(form)`                  | `PATCH /users/me`           | 更新资料          |
| `changePassword(form)`                 | `PUT /auth/password`        | 修改密码          |
| `listUsers()`                          | `GET /users`                | 用户列表          |
| `updateUserRole(userId, role)`         | `PATCH /users/{id}/role`    | 改角色           |
| `toggleUserEnabled(userId, enabled)`   | `PATCH /users/{id}/enabled` | 启用/禁用用户（`pages/profile/users`，仅管理员） |
| `getPreferences()`                     | `GET /users/me/preferences` | 读取偏好          |
| `savePreferences(prefs)`               | `PUT /users/me/preferences` | 保存偏好          |
| `getActivities()`                      | `GET /users/me/activities`  | 活动记录          |


---



## 4. 权限体系

与 web 端完全一致，实现于 `utils/permission.js`。

```javascript
type UserRole = 'ADMIN' | 'DISPATCHER' | 'VIEWER'

type Permission =
  | 'task:view' | 'task:create' | 'task:dispatch' | 'task:control'
  | 'site:edit' | 'route:edit' | 'alarm:ack'
  | 'robot:manage' | 'detection:manage'
  | 'user:manage' | 'record:export'
  | 'workorder:view' | 'workorder:create' | 'workorder:assign'
  | 'workorder:process' | 'workorder:review'
```

页面守卫：`app.requireAuth()` + `app.requirePermission(permission, roles?)`

---



## 5. 业务 Service 接口



### 5.1 站点


| 方法               | HTTP                         | 说明      |
| ---------------- | ---------------------------- | ------- |
| `getSites()`     | `GET /sites`                 | 站点列表    |
| `getAreas()`     | `GET /sites/areas`           | 区域列表    |
| `saveSite(site)` | `POST/PUT /sites`            | 新建/更新   |
| `removeSite(id)` | `DELETE /sites/{id}`         | 删除站点及区域 |
| `addArea(area)`  | `POST /sites/{siteId}/areas` | 添加区域    |
| `removeArea(id)` | `DELETE /sites/areas/{id}`   | 删除区域    |




### 5.2 巡检路线


| 方法                                 | HTTP                  | 说明           |
| ---------------------------------- | --------------------- | ------------ |
| `getRoutes()`                      | `GET /routes`         | 路线列表         |
| `createRoute(siteId, name, desc?)` | `POST /routes`        | 创建空路线        |
| `saveRoute(route)`                 | `PUT /routes/{id}`    | 更新路线/检查点/检测项 |
| `removeRoute(id)`                  | `DELETE /routes/{id}` | 删除           |




### 5.3 巡检任务


| 方法                                   | HTTP                        | 说明            |
| ------------------------------------ | --------------------------- | ------------- |
| `getTasks()`                         | `GET /tasks`                | 任务列表          |
| `getRecords()`                       | `GET /records`              | 巡检记录          |
| `getTaskEvents(taskId)`              | `GET /tasks/{id}/events`    | 事件时间线         |
| `createTask(name, routeId, robotId)` | `POST /tasks`               | 创建，状态 CREATED |
| `dispatchTask(id)`                   | `POST /tasks/{id}/dispatch` | 下发            |
| `pauseTask(id)`                      | `POST /tasks/{id}/pause`    | 暂停            |
| `resumeTask(id)`                     | `POST /tasks/{id}/resume`   | 恢复            |
| `takeoverTask(id)`                   | `POST /tasks/{id}/takeover` | 人工接管          |
| `cancelTask(id)`                     | `POST /tasks/{id}/cancel`   | 取消            |


### 5.4 告警


| 方法                       | HTTP                    | 说明   |
| ------------------------ | ----------------------- | ---- |
| `getAlarms()`            | `GET /alarms`           | 告警列表 |
| `acknowledgeAlarm(id)`   | `POST /alarms/{id}/ack` | 确认单条 |
| `acknowledgeAllAlarms()` | `POST /alarms/ack-all`  | 全部确认 |




### 5.5 工单


| 方法                                          | HTTP                                  | 说明                                  |
| ------------------------------------------- | ------------------------------------- | ------------------------------------ |
| `getWorkOrders()`                           | `GET /work-orders`                    | 工单列表                                |
| `createWorkOrderFromAlarm(alarm, creator)`  | `POST /work-orders/from-alarm/{alarmId}` | 从告警创建                              |
| `claimWorkOrder(id)`                        | `POST /work-orders/{id}/claim`        | 调度员接单，仅此接口会写入 assigneeId/assigneeName |
| `updateWorkOrderStatus(id, status, extra?)` | `PATCH /work-orders/{id}/status`      | 状态流转，`extra.review` 需符合后端 conclusion/onsiteFinding/handlingMeasures/followUpPlan 结构 |


状态：`PENDING → PROCESSING（仅通过 claim）→ REVIEW → CLOSED`，`REVIEW → PROCESSING`（管理员退回重做），`PENDING/PROCESSING → CANCELLED`（管理员取消，需 `workorder:review` 权限）

### 5.6 机器人 / 检测 / 通知


| 模块   | 主要方法                                                                                         | HTTP 前缀                |
| ---- | -------------------------------------------------------------------------------------------- | ---------------------- |
| 机器人  | `getRobots`, `addRobot`, `removeRobot`                                                       | `/robots`              |
| 检测策略 | `getDetectionTemplates`, `addDetectionTemplate`, `removeDetectionTemplate`                   | `/detection-templates` |
| 通知   | `getNotifications`, `markNotificationRead`, `markAllNotificationsRead`, `removeNotification` | `/notifications`       |




### 5.7 聚合


| 方法                 | 说明                            |
| ------------------ | ----------------------------- |
| `fetchDashboard()` | 并行拉取站点/路线/任务/告警/机器人/记录，供总览页使用 |


---



## 6. 本地存储 Key

小程序客户端仅持久化登录会话：

| Key          | 内容   |
| ------------ | ---- |
| `pi_session` | 登录会话 |


---



## 7. 页面路由映射


| 小程序路径                       | Web 路由           | 权限               |
| --------------------------- | ---------------- | ---------------- |
| `pages/auth/login/index`    | `/login`         | 公开               |
| `pages/auth/register/index` | `/register`      | 公开               |
| `pages/dashboard/index`     | `/dashboard`     | 登录               |
| `pages/monitor/index`       | `/monitor`       | 登录               |
| `pages/alarms/index`        | `/alarms`        | 登录               |
| `pages/workorders/index`    | `/workorders`    | `workorder:view` |
| `pages/notifications/index` | `/notifications` | 登录               |
| `pages/tasks/index`         | `/tasks`         | `task:view`      |
| `pages/tasks/detail/index`  | `/tasks/:id`     | `task:view`      |
| `pages/profile/*/index`     | `/profile/*`     | 登录               |
| `pages/profile/users/index` | `/users`         | `user:manage` + `ADMIN` |
| `pages/forbidden/index`     | `/403`           | 登录               |




### 通知 link 映射（`config/menu.js` → `mapNotificationLink`）


| Web link         | 小程序 path                     |
| ---------------- | ---------------------------- |
| `/dashboard`     | `/pages/dashboard/index`     |
| `/alarms`        | `/pages/alarms/index`        |
| `/workorders`    | `/pages/workorders/index`    |
| `/tasks`         | `/pages/tasks/index`         |
| `/notifications` | `/pages/notifications/index` |


---



## 8. 错误处理


| 场景           | 行为                                      |
| ------------ | --------------------------------------- |
| Service 校验失败 | `throw new Error('中文信息')`               |
| 页面捕获         | `wx.showToast({ title, icon: 'none' })` |
| 未登录          | `redirectTo` 登录页，带 `redirect` 参数        |
| 无权限          | `redirectTo` `/pages/forbidden/index`   |
| HTTP 401     | 清除 session，提示重新登录                       |


---



## 9. 与 web 端 API 文档关系


| 文档       | 路径                                    | 用途                                |
| -------- | ------------------------------------- | --------------------------------- |
| Web 接口文档 | `frontend/web/docs/API.md`            | Pinia Store + mock 详解 + REST 设计来源 |
| 小程序接口文档  | `frontend/wechat-program/docs/API.md` | 本文档，侧重小程序调用方式与共用后端                |


**后端 REST 接口清单**以 web 文档第 9.2 节为准；小程序 `services/index.js` 已按该清单预留 HTTP 路径。

---



## 10. 预留 / 未实现


| API                 | 状态               |
| ------------------- | ---------------- |
| `toggleUserEnabled` | 已实现（`PATCH /users/{id}/enabled`），见 `pages/profile/users`（仅管理员，web 端 UI 未接入，但接口已通用） |
| `assignWorkOrder`   | 已实现，见 `services/index.js` 与工单页指派弹窗 |


---



## 11. 实时通道（后端建议）

共用后端若支持 WebSocket，建议与 web 端一致：

```
/ws/tasks/{taskId}     → 任务进度、事件流
/ws/robots/{robotId}   → 位姿、电量
/ws/alarms             → 新告警推送
```

小程序可通过 `wx.connectSocket` 对接，当前版本使用轮询 / mock 定时器。