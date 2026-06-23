# Intelligent-Power-Inspection

电力智能巡检平台软件端仓库。当前仓库包含 Java 后端、Vue Web 管理端和微信小程序端，用于演示变电站巡检业务中的登录鉴权、站点管理、路线规划、任务调度、机器人管理、告警处置、工单流转、检测模板、LingBot 建图、通知中心和巡检记录等能力。

当前项目定位为课程/演示级可运行系统。真实机器人和生产级部署链路尚未完整接入；模型能力已拆出可替换接口和 Python FastAPI 服务骨架，默认仍以 mock runner 支撑联调，后续可替换为真实 LocateAnything 与 LingBot-Map runner。

## 当前状态

- `backend/`：Spring Boot 3.3.6 / Java 17 / Maven 后端。
- `frontend/web/`：Vue 3 / Vite / TypeScript / Pinia Web 管理端。
- `frontend/wechat-program/`：微信小程序端，已有页面、组件、mock 服务和后端 API 切换配置。
- `ai-services/`：Python FastAPI 模型服务骨架，包含 LocateAnything 检测服务和 LingBot-Map 建图服务。
- Web 端通过 Vite 代理访问后端 `/api/v1` 和 `/ws`。
- 小程序端默认使用本地 mock；需要接真实后端时修改 `frontend/wechat-program/miniprogram/config/api.js`。
- 后端默认使用 MySQL，测试/演示可使用 H2。
- 后端模型网关支持 `mock` 和 `http` 两种模式；`http` 模式调用 `ai-services/` 中的 Python 服务。
- 数据库结构由 Flyway 管理，迁移脚本位于 `backend/src/main/resources/db/migration/`。

## 技术栈

### 后端

- Java 17+
- Spring Boot 3.3.6
- Maven
- Spring Web
- Spring Security
- Bean Validation
- Spring Data JPA
- Spring WebSocket / STOMP
- Flyway
- MySQL Driver
- H2 测试数据库
- JUnit 5 / MockMvc

### Web 前端

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Element Plus
- ECharts
- Leaflet
- Three.js

### 微信小程序

- 原生微信小程序工程
- WXML / WXSS / JavaScript
- 本地 mock store
- 可切换后端 REST API

### Python 模型服务

- Python 3.11+ / 3.13 已验证基础服务测试
- FastAPI
- Uvicorn
- Pydantic
- Pytest
- LocateAnything 服务骨架
- LingBot-Map 异步建图服务骨架

## 目录结构

```text
Intelligent-Power-Inspection/
├─ backend/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/
│     │  ├─ java/com/powerinspection/
│     │  │  ├─ alarm/           # 告警
│     │  │  ├─ auth/            # 登录、注册、会话
│     │  │  ├─ business/        # 通用 CRUD 支撑
│     │  │  ├─ common/          # 统一响应、异常、JSON/ID 工具
│     │  │  ├─ config/          # Security、CORS、JWT、WebSocket 配置
│     │  │  ├─ data/            # 通用 JSON 数据仓库与种子数据
│     │  │  ├─ detection/       # 检测模板
│     │  │  ├─ lingbot/         # LingBot 建图
│     │  │  ├─ notification/    # 通知
│     │  │  ├─ record/          # 巡检记录导出
│     │  │  ├─ robot/           # 机器人与模拟网关
│     │  │  ├─ route/           # 路线与检查点
│     │  │  ├─ security/        # JWT 过滤器与当前用户
│     │  │  ├─ site/            # 站点与区域
│     │  │  ├─ task/            # 巡检任务与模拟执行
│     │  │  ├─ user/            # 用户、角色、权限、偏好
│     │  │  └─ workorder/       # 工单
│     │  └─ resources/
│     │     ├─ application.yml
│     │     └─ db/migration/    # Flyway 迁移脚本
│     └─ test/
│        ├─ java/               # MockMvc 集成测试
│        └─ resources/          # H2 测试配置
├─ frontend/
│  ├─ web/
│  │  ├─ src/
│  │  │  ├─ api/                # HTTP 与实时通信封装
│  │  │  ├─ components/         # 业务组件
│  │  │  ├─ composables/        # 组合式逻辑
│  │  │  ├─ config/             # 菜单等配置
│  │  │  ├─ layouts/            # 页面布局
│  │  │  ├─ router/             # 路由与权限守卫
│  │  │  ├─ stores/             # Pinia 状态
│  │  │  ├─ types/              # TypeScript 类型
│  │  │  ├─ utils/              # 工具函数
│  │  │  └─ views/              # 页面视图
│  │  ├─ docs/API.md
│  │  ├─ package.json
│  │  └─ vite.config.ts
│  └─ wechat-program/
│     ├─ project.config.json
│     ├─ project.private.config.json
│     └─ miniprogram/
│        ├─ app.js
│        ├─ app.json
│        ├─ components/
│        ├─ config/
│        ├─ pages/
│        ├─ services/
│        └─ utils/
├─ ai-services/
│  ├─ README.md
│  ├─ common/                  # Python 服务共享 schema、错误、存储和日志工具
│  ├─ locate-anything-service/  # 检查点图像检测服务，默认端口 9001
│  │  ├─ app.py
│  │  ├─ model_runner.py        # 当前为 mock runner，后续封装 LocateAnythingWorker
│  │  ├─ parser.py              # 解析 <box>/<point>/none 输出
│  │  └─ tests/
│  └─ lingbot-map-service/      # 三维建图异步服务，默认端口 9002
│     ├─ app.py
│     ├─ job_store.py
│     ├─ runner.py              # 当前为 mock runner，后续封装 LingBot-Map
│     ├─ worker.py
│     └─ tests/
├─ .gitignore
└─ README.md
```

## 环境要求

- JDK 17 或更高版本。
- Maven 3.9.x 或兼容版本。
- Node.js 18 或更高版本。
- npm。
- Python 3.11 或更高版本，用于运行 `ai-services/`。
- MySQL 8.x，用于正常开发/运行。
- H2 用于自动化测试和快速演示，不需要单独安装。
- 微信开发者工具，用于运行小程序端。

如果 Windows PowerShell 中 `java -version` 仍显示 JDK 8，先确认本机已安装 JDK 17+，再临时切换：

```powershell
$env:JAVA_HOME="<你的 JDK 17+ 安装目录>"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -version
```

如果 PowerShell 拦截 `npm.ps1`，可使用 `npm.cmd`：

```powershell
npm.cmd install
npm.cmd run dev
```

## 快速启动

### 方式一：H2 演示启动

这种方式不依赖本机 MySQL，适合快速查看前后端联调效果。

启动后端：

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="test"
mvn spring-boot:test-run
```

启动 Web 前端：

```powershell
cd frontend/web
npm install
npm run dev
```

访问：

```text
http://localhost:5173/
http://127.0.0.1:5173/
```

### 方式二：MySQL 开发启动

先创建空数据库。开发环境建议使用空库，让 Flyway 自动初始化表结构：

```sql
CREATE DATABASE power_inspection CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

通过环境变量配置数据库并启动后端：

```powershell
cd backend
$env:DB_URL="jdbc:mysql://localhost:3306/power_inspection?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="<你的数据库密码>"
$env:JWT_SECRET="dev-secret-change-me"
mvn spring-boot:run
```

启动 Web 前端：

```powershell
cd frontend/web
npm install
npm run dev
```

Web 前端开发服务器会把 `/api` 和 `/ws` 代理到 `http://localhost:8080`。

### 方式三：微信小程序端

用微信开发者工具打开：

```text
frontend/wechat-program/
```

小程序 API 配置位于：

```text
frontend/wechat-program/miniprogram/config/api.js
```

默认配置为 `useMock: true`。如需接入真实后端，设置 `useMock: false` 并确认 `baseUrl` 指向后端 `/api/v1`。

### 方式四：Python 模型服务联调

模型服务默认使用 mock runner，不需要下载模型权重，适合先验证 Spring Boot 与 Python 服务之间的 HTTP 协议。

创建并安装 Python 依赖：

```powershell
cd ai-services
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r .\locate-anything-service\requirements.txt
```

如果系统默认 `python` 来自 MSYS，可能缺少部分 Windows wheel。可改用标准 Windows CPython，例如：

```powershell
D:\Python\Python3.13.0\python.exe -m venv .venv313
.\.venv313\Scripts\python.exe -m pip install -r .\locate-anything-service\requirements.txt
```

启动 LocateAnything 服务：

```powershell
cd ai-services\locate-anything-service
$env:PYTHONPATH=".."
..\.venv313\Scripts\python.exe -m uvicorn app:app --host 0.0.0.0 --port 9001
```

启动 LingBot-Map 服务：

```powershell
cd ai-services\lingbot-map-service
$env:PYTHONPATH=".."
..\.venv313\Scripts\python.exe -m uvicorn app:app --host 0.0.0.0 --port 9002
```

后端切换到 HTTP 模型网关：

```powershell
cd backend
$env:MODEL_MODE="http"
$env:LOCATE_ANYTHING_BASE_URL="http://127.0.0.1:9001"
$env:LINGBOT_MAP_BASE_URL="http://127.0.0.1:9002"
mvn spring-boot:test-run
```

> 注意：上述 Python 服务当前只提供 mock runner。真实模型接入时，分别替换 `ai-services/locate-anything-service/model_runner.py` 和 `ai-services/lingbot-map-service/runner.py`。

## 模型接入架构

后端不会直接加载 PyTorch/CUDA 模型，而是通过模型网关调用 Python 服务。

```text
Web / 小程序
  -> Spring Boot 后端
    -> LocateAnythingGateway / LingBotMapGateway
      -> Python FastAPI 模型服务
        -> 模型 runner / artifact 存储
```

后端模型配置绑定在 `app.model`：

```yaml
app:
  model:
    mode: ${MODEL_MODE:mock}
    service-token: ${MODEL_SERVICE_TOKEN:}
    locate-anything:
      base-url: ${LOCATE_ANYTHING_BASE_URL:http://127.0.0.1:9001}
      timeout-seconds: ${LOCATE_ANYTHING_TIMEOUT_SECONDS:30}
    lingbot-map:
      base-url: ${LINGBOT_MAP_BASE_URL:http://127.0.0.1:9002}
      timeout-seconds: ${LINGBOT_MAP_TIMEOUT_SECONDS:10}
```

模式说明：

| 模式 | 行为 |
| --- | --- |
| `mock` | 使用 Java 内置 mock 网关，默认模式，适合无 Python 服务时演示 |
| `http` | 使用 HTTP 网关调用 `ai-services/` 的 FastAPI 服务 |

LocateAnything 服务接口：

```text
GET  /health
GET  /ready
POST /v1/locate/checkpoint
```

LingBot-Map 服务接口：

```text
GET    /health
GET    /ready
POST   /v1/reconstruction/jobs
GET    /v1/reconstruction/jobs/{jobId}
GET    /v1/reconstruction/jobs/{jobId}/artifacts
DELETE /v1/reconstruction/jobs/{jobId}
```

模型产物约定：

- `runtime-storage/`、模型权重、点云、mesh 等大文件不提交到 Git。
- 第一版 Python 服务会生成 mock artifacts；真实部署时建议替换为 MinIO、OSS 或 COS。
- LingBot-Map 的 viewer 仅作为调试工具，不作为正式后端 API。

## 数据库与 Flyway

Flyway 是数据库迁移工具，不是数据库。项目启动时会读取：

```text
backend/src/main/resources/db/migration/V1__schema.sql
```

并自动创建基础表：

- `app_users`
- `user_preferences`
- `user_activities`
- `data_records`

Flyway 还会创建自己的迁移记录表：

```text
flyway_schema_history
```

如果连接到一个已经有表但没有 `flyway_schema_history` 的数据库，启动会失败并出现类似错误：

```text
Found non-empty schema(s) but no schema history table.
Use baseline() or set baselineOnMigrate to true
```

开发环境推荐处理方式：

1. 使用空数据库重新启动，让 Flyway 从头初始化。
2. 如果已有库确实要由 Flyway 接管，再考虑 `baseline-on-migrate: true`；该做法不建议直接用于生产库。

## 默认演示账号

后端启动时会初始化默认账号和演示业务数据。

| 用户名 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| `admin` | `Admin@123` | `ADMIN` | 管理员，拥有全部权限 |
| `dispatcher` | `Disp@123` | `DISPATCHER` | 调度员，可调度任务、维护站点路线、处理告警和导出记录 |
| `viewer` | `View@123` | `VIEWER` | 查看者，主要用于只读查看任务和监控数据 |

## 权限点

后端当前保留 12 个权限点：

- `task:view`
- `task:create`
- `task:dispatch`
- `task:control`
- `site:edit`
- `route:edit`
- `alarm:ack`
- `robot:manage`
- `lingbot:manage`
- `detection:manage`
- `user:manage`
- `record:export`

角色权限大致如下：

| 角色 | 权限范围 |
| --- | --- |
| `ADMIN` | 全部权限 |
| `DISPATCHER` | 任务查看、创建、下发、控制，站点/路线维护，告警确认，记录导出 |
| `VIEWER` | 基础任务查看 |

## API 与实时推送

REST API 统一前缀：

```text
http://localhost:8080/api/v1
```

统一响应格式：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

认证请求头：

```text
Authorization: Bearer <token>
```

主要接口模块：

| 模块 | 路径 |
| --- | --- |
| 认证 | `/auth/login`、`/auth/register`、`/auth/logout`、`/auth/me`、`/auth/password` |
| 用户 | `/users`、`/users/{id}/role`、`/users/{id}/enabled`、`/users/me`、`/users/me/activities`、`/users/me/preferences` |
| 站点区域 | `/sites`、`/sites/{id}`、`/sites/{id}/areas` |
| 路线检查点 | `/routes`、`/routes/{id}`、`/routes/{id}/checkpoints` |
| 任务 | `/tasks`、`/tasks/{id}`、`/tasks/{id}/dispatch`、`/tasks/{id}/pause`、`/tasks/{id}/resume`、`/tasks/{id}/takeover`、`/tasks/{id}/cancel`、`/tasks/{id}/events` |
| 告警 | `/alarms`、`/alarms/{id}/ack`、`/alarms/ack-all` |
| 工单 | `/work-orders`、`/work-orders/{id}`、`/work-orders/from-alarm/{alarmId}` |
| 机器人 | `/robots`、`/robots/{id}`、`/robots/{id}/telemetry` |
| 检测模板 | `/detection-templates`、`/detection-templates/{id}` |
| 记录 | `/records`、`/records/export` |
| 建图 | `/lingbot/jobs`、`/lingbot/jobs/{id}`、`/lingbot/jobs/{id}/simulate`、`/lingbot/maps/{id}/pointcloud` |
| 通知 | `/notifications`、`/notifications/{id}/read`、`/notifications/read-all` |

WebSocket/STOMP 端点：

```text
ws://localhost:8080/ws
```

主要 topic：

```text
/topic/tasks/{taskId}
/topic/tasks
/topic/task-events
/topic/robots/{robotId}
/topic/robots
/topic/alarms
/topic/lingbot/jobs/{id}
/topic/lingbot/jobs
/topic/notifications
/topic/notifications/{userId}
```

更完整的接口说明见 `frontend/web/docs/API.md`。

## 前端数据流

Web 端核心业务数据已通过后端接口加载：

- HTTP 客户端：`frontend/web/src/api/http.ts`
- WebSocket 客户端：`frontend/web/src/api/realtime.ts`
- 业务接口封装：`frontend/web/src/api/resources.ts`
- 登录相关接口：`frontend/web/src/api/auth.ts`
- 个人中心接口：`frontend/web/src/api/profile.ts`
- Pinia Store：`frontend/web/src/stores/*`

浏览器本地仍会保存：

- `pi_session`：JWT token 和当前用户会话。
- 少量 UI 偏好，例如侧边栏折叠状态。

站点、路线、任务、告警、工单、机器人、检测模板、建图任务、通知、记录等核心业务数据以后端为准。任务、机器人、告警、建图任务和通知会通过 WebSocket/STOMP 实时刷新；页面首次进入时仍会通过 REST API 加载快照数据。

## 常用命令

### 后端

在 `backend/` 目录执行：

| 命令 | 说明 |
| --- | --- |
| `mvn test` | 使用 H2 和 MockMvc 执行后端测试 |
| `mvn spring-boot:test-run` | 使用测试 classpath 启动后端，适合 H2 演示 |
| `mvn spring-boot:run` | 使用默认 MySQL 配置启动后端 |

### Web 前端

在 `frontend/web/` 目录执行：

| 命令 | 说明 |
| --- | --- |
| `npm install` | 安装前端依赖 |
| `npm run dev` | 启动 Vite 开发服务器 |
| `npm run build` | TypeScript 类型检查并构建生产产物 |
| `npm run preview` | 本地预览构建产物 |

### Python 模型服务

在对应服务目录执行，并设置 `PYTHONPATH=..`：

| 命令 | 说明 |
| --- | --- |
| `python -m pytest tests` | 运行当前模型服务测试 |
| `python -m uvicorn app:app --host 0.0.0.0 --port 9001` | 启动 LocateAnything 服务 |
| `python -m uvicorn app:app --host 0.0.0.0 --port 9002` | 启动 LingBot-Map 服务 |

当前验证过的命令：

```powershell
$env:JAVA_HOME="D:\JAVA\jdk17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd backend
mvn test

cd ..\ai-services\locate-anything-service
$env:PYTHONPATH=".."
..\.venv313\Scripts\python.exe -m pytest tests

cd ..\lingbot-map-service
$env:PYTHONPATH=".."
..\.venv313\Scripts\python.exe -m pytest tests
```

## 常见问题

### 1. 后端提示 Java 版本不正确

Spring Boot 3 必须使用 JDK 17 或更高版本。若 `java -version` 显示 `1.8`，需要先切换 `JAVA_HOME` 和 `PATH`。

### 2. `mvn spring-boot:run` 连接 MySQL 失败

默认运行配置使用 MySQL。请确认 MySQL 已启动、数据库已创建，并且 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 正确。

如果只想快速演示，可以使用 H2：

```powershell
$env:SPRING_PROFILES_ACTIVE="test"
mvn spring-boot:test-run
```

### 3. Flyway 报 `non-empty schema` 错误

说明当前数据库不是空库，但没有 Flyway 的 `flyway_schema_history` 记录表。开发环境建议使用空库重新启动，或清空测试库后再启动。

### 4. 只启动前端后登录失败

Web 前端登录和主要业务页面依赖后端 `/api/v1`。需要同时启动后端和 Web 前端。

### 5. 重开页面后仍保持登录

Web 端会把登录会话保存在浏览器 `localStorage` 的 `pi_session` 中。清理站点本地存储后会退出登录状态。

### 6. `127.0.0.1:5173` 和 `localhost:5173` 都能用吗

可以。后端默认 CORS 允许：

```text
http://localhost:5173
http://127.0.0.1:5173
```

## 当前限制

- 当前是演示/课程级后端，不是生产部署方案。
- 真实机器人控制尚未接入；`RobotGateway` 默认仍是模拟任务执行和机器人位置。
- LocateAnything 与 LingBot-Map 已有 Python 服务骨架和后端 HTTP 网关，但真实模型 runner 仍需按官方代码进一步封装。
- 当前 Python 服务默认输出 mock 检测结果和 mock 建图产物。
- 生产级日志、审计、监控、部署流水线和权限审计细节仍需进一步完善。
- `backend/target/`、`frontend/web/dist/`、`frontend/web/node_modules/`、`ai-services/.venv*/`、`runtime-storage/`、模型权重和点云/mesh 等产物不应提交。
