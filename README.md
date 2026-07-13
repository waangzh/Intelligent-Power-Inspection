# Intelligent-Power-Inspection

电力智能巡检平台——软件端仓库。面向变电站巡检场景，提供 Web 管理端、微信小程序端、Java 后端和 AI 模型服务的一体化演示系统。

> 硬件端（机器人控制、传感器采集、边缘推理）位于 [electric-power-inspection-robot](https://github.com/liaojingwu20041031/electric-power-inspection-robot)，本仓库聚焦软件层：业务系统 + AI 检测 + 路线规划 + 巡检处置 Agent。

[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.6-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.x-4fc08d)](https://vuejs.org/)
[![Python](https://img.shields.io/badge/Python-3.11%2B-blue)](https://www.python.org/)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

## 目录

- [项目定位](#项目定位)
- [仓库关系](#仓库关系)
- [功能概览](#功能概览)
- [技术栈](#技术栈)
- [目录结构](#目录结构)
- [环境要求](#环境要求)
- [快速启动](#快速启动)
- [默认演示账号](#默认演示账号)
- [巡检路线规划](#巡检路线规划)
- [巡检处置 Agent](#巡检处置-agent)
- [模型接入架构](#模型接入架构)
- [数据库与 Flyway](#数据库与-flyway)
- [权限点](#权限点)
- [API 与实时推送](#api-与实时推送)
- [前端数据流](#前端数据流)
- [常用命令](#常用命令)
- [常见问题](#常见问题)
- [当前限制](#当前限制)

## 项目定位

本项目是课程/演示级可运行系统，完整覆盖：

```text
机器人数据采集 → 告警生成 → AI 视觉检测 → Agent 智能研判
    → 工单流转 → 人工处置 → 通知闭环
```

当前定位为 **软件平台**。真实机器人控制、传感器采集、边缘推理等硬件侧能力由 [electric-power-inspection-robot](https://github.com/liaojingwu20041031/electric-power-inspection-robot) 仓库负责。

## 仓库关系

| 仓库 | 定位 | 说明 |
| --- | --- | --- |
| [Intelligent-Power-Inspection](https://github.com/waangzh/Intelligent-Power-Inspection) | **软件端（本仓库）** | Java 后端 + Vue Web + 微信小程序 + Python AI 服务 |
| [electric-power-inspection-robot](https://github.com/liaojingwu20041031/electric-power-inspection-robot) | **硬件端** | 机器人运动控制、传感器数据采集、边缘端 AI 推理、与软件平台的通信协议 |

两个仓库通过 REST API 和 WebSocket 协作：软件平台下发任务指令，硬件端上报机器人状态、遥测数据和检测结果。

## 功能概览

| 模块 | 能力 |
| --- | --- |
| 站点管理 | 站点创建、GIS 区域划分、地图资产管理 |
| 机器人管理 | 机器人注册、状态监控、遥测数据、模拟网关 |
| 巡检路线 | ROS map 坐标系标注、起点/巡检点/方向、executorJson v2 持久化 |
| 任务调度 | 巡检任务创建、下发、暂停/恢复、接管/取消、执行事件流 |
| 告警处置 | 多级告警生成、确认、转工单，支持自动/人工转换 |
| 巡检处置 Agent | Plan-Act-Observe 编排、只读工具调用、人工提问闭环、Policy 引擎、受控 Action 执行 |
| 工单流转 | 从告警创建、调度员抢单接单、现场处置与管理员复核 |
| AI 检测 | LocateAnything 视觉检测服务、手动上传异步检测 |
| 通知中心 | 实时推送、已读管理、Agent 处置通知 |
| 记录导出 | 巡检记录查询与 Excel 导出 |
| 权限控制 | 管理员 / 调度员 / 查看者三级角色 + 细粒度权限点 |

## 技术栈

| 层 | 技术 |
| --- | --- |
| **后端** | Java 17、Spring Boot 3.3.6、Spring Security、Spring Data JPA、Spring WebSocket/STOMP、Flyway、MySQL、H2 |
| **Web 前端** | Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、ECharts、Leaflet、Three.js |
| **微信小程序** | 原生框架、WXML/WXSS/JavaScript、本地 mock store、可切换后端 API |
| **AI 服务** | Python 3.11+、FastAPI、Uvicorn、Pydantic、Pytest、LocateAnything |
| **硬件端** | 参见 [electric-power-inspection-robot](https://github.com/liaojingwu20041031/electric-power-inspection-robot) |

## 目录结构

```text
Intelligent-Power-Inspection/
├─ backend/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/
│     │  ├─ java/com/powerinspection/
│     │  │  ├─ agent/           # 巡检处置 Agent（编排/Planner/Policy/Action/HumanInput）
│     │  │  ├─ alarm/           # 告警
│     │  │  ├─ auth/            # 登录、注册、会话
│     │  │  ├─ business/        # 通用 CRUD 支撑
│     │  │  ├─ common/          # 统一响应、异常、JSON/ID 工具
│     │  │  ├─ config/          # Security、CORS、JWT、WebSocket
│     │  │  ├─ data/            # 通用 JSON 数据仓库与种子数据
│     │  │  ├─ detection/       # 检测模板
│     │  │  ├─ mapasset/        # 地图资产管理
│     │  │  ├─ model/           # AI 模型 HTTP 网关
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
│     │     └─ db/migration/    # Flyway 迁移（V1-V5）
│     └─ test/
│        ├─ java/               # MockMvc 集成测试（44 项）
│        └─ resources/          # H2 测试配置
├─ frontend/
│  ├─ web/
│  │  ├─ src/
│  │  │  ├─ api/                # HTTP 与 WebSocket 封装
│  │  │  ├─ components/         # 业务组件（含 RosMapRouteEditor）
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
│     └─ miniprogram/           # 原生微信小程序
├─ ai-services/
│  ├─ common/                   # Python 共享工具
│  └─ locate-anything-service/  # 视觉检测服务（端口 9001）
├─ .gitignore
└─ README.md
```

## 环境要求

| 组件 | 版本 | 用途 |
| --- | --- | --- |
| JDK | 17+ | 后端编译与运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 18+ | Web 前端 |
| npm | — | Web 前端依赖 |
| Python | 3.11+ | AI 模型服务 |
| MySQL | 8.x | 开发/运行数据库 |
| H2 | 内嵌 | 测试与快速演示 |
| 微信开发者工具 | — | 小程序端 |

## 快速启动

### 方式一：H2 演示（无需 MySQL）

```powershell
# 后端
cd backend
$env:SPRING_PROFILES_ACTIVE="test"
mvn spring-boot:test-run

# Web 前端（新终端）
cd frontend/web
npm install
npm run dev
```

访问 http://localhost:5173/

### 方式二：MySQL 开发

```sql
CREATE DATABASE power_inspection CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```powershell
cd backend
$env:DB_URL="jdbc:mysql://localhost:3306/power_inspection?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="<你的数据库密码>"
$env:JWT_SECRET="dev-secret-change-me"
mvn spring-boot:run
```

### 方式三：微信小程序

用微信开发者工具打开 `frontend/wechat-program/`，默认使用 mock 数据。接入真实后端时修改 `miniprogram/config/api.js` 设置 `useMock: false`。

### 方式四：Python AI 服务联调

```powershell
# 安装依赖
conda env create -f ai-services/locate-anything-service/environment.yml
conda activate ipi-locate-anything

# 如需真实模型推理
conda run -n ipi-locate-anything python -m pip install -r ai-services/locate-anything-service/requirements-torch-cu126.txt

# 启动服务
cd ai-services\locate-anything-service
uvicorn app:app --host 0.0.0.0 --port 9001
```

后端默认通过 HTTP 模型网关调用 `http://127.0.0.1:9001`。

## 默认演示账号

| 用户名 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| `admin` | `Admin@123` | `ADMIN` | 全部权限 |
| `dispatcher` | `Disp@123` | `DISPATCHER` | 调度、路线、告警、导出 |
| `viewer` | `View@123` | `VIEWER` | 只读查看 |

## 巡检路线规划

Web 端「巡检业务 → 巡检规划」基于 ROS `map` 坐标系，在 PGM/YAML 地图上标注机器人导航路线。

### 使用流程

1. 选择站点 → 新建/选择路线
2. 上传 `.yaml` + `.pgm`（支持拖拽），也可导入已有 `.json`
3. 切换标注模式：**起点** / **巡检点** / **方向** / **拖动**
4. 右侧面板调整参数和巡检点顺序
5. 点击 **保存到平台**，通过 `PATCH /api/v1/routes/{id}` 持久化 `executorJson`
6. **复制 JSON** 或 **下载 route.json** 供机器人执行器加载

### 数据格式

- 主数据：`Route.executorJson`，route.json v2，`frame_id: "map"`
- 坐标换算与 ROS map_server 一致：`x = origin_x + pixel_x * resolution`，`y = origin_y + (height - pixel_y) * resolution`
- 保存时同步生成兼容字段供监控/任务页展示

## 巡检处置 Agent

阶段 3 引入受约束、可审计的巡检处置 Agent，架构如下：

```text
AgentCase（处置案件）
  └─ AgentRun（分析运行，可多次）
       ├─ AgentStep（执行步骤，有序可回放）
       ├─ AgentEvidence（不可变证据，按 Run 隔离）
       ├─ AgentToolCall（只读工具调用，参数白名单 + 脱敏）
       └─ AgentAction（受控写动作，Policy + 审批 + 幂等）
```

### 核心能力

- **Plan-Act-Observe 循环**：LLM 规划 → 只读工具 → 观察结果 → 重新规划
- **WAITING_HUMAN 闭环**：Agent 提问 → 人工回答 → OPERATOR_INPUT Evidence → 继续编排
- **Policy Engine**：AUTO_EXECUTE / REQUIRE_APPROVAL / DENY 三级决策
- **Action 状态机**：PROPOSED → APPROVED → EXECUTING → SUCCEEDED/FAILED，乐观锁 + 幂等键
- **Action Handler**：通知本人、创建工单草稿

### 安全边界

- Agent 只能调用注册表中的只读工具
- 所有写操作必须经过 Policy 引擎
- LLM 输出不直接执行，须经 PlannerDecisionValidator 校验
- 人工回答标记为 `untrusted`，作为 Evidence 而非系统指令

## 模型接入架构

```text
Web / 小程序
  → Spring Boot 后端
    → LocateAnythingGateway（mock / http）
      → Python FastAPI 模型服务（:9001）
        → 模型 runner / artifact 存储
```

| 模式 | 行为 |
| --- | --- |
| `mock` | Java 内置 mock 网关，测试环境默认 |
| `http` | HTTP 调用 `ai-services/` FastAPI 服务 |

手动检测使用异步接口：`POST /api/v1/detections/manual` 提交任务 → `GET /api/v1/detections/manual/{requestId}` 轮询结果。

## 数据库与 Flyway

迁移脚本位于 `backend/src/main/resources/db/migration/`：

| 版本 | 内容 |
| --- | --- |
| V1 | 基础表（users, preferences, activities, data_records） |
| V2 | Agent 领域模型（cases, runs, steps, evidence, tool_calls, actions, execution_claims） |
| V3 | 编排器扩展（degraded, pending_question, arguments_hash） |
| V4 | 人工问答 + Policy 审计字段 |
| V5 | idempotency_key 唯一约束 |

## 权限点

```text
agent:view  agent:run  agent:approve  agent:admin
task:view  task:create  task:dispatch  task:control
site:edit  route:edit  alarm:ack  robot:manage
detection:manage  user:manage  record:export
```

| 角色 | 权限范围 |
| --- | --- |
| `ADMIN` | 全部 |
| `DISPATCHER` | Agent 查看/运行/审批，任务/站点/路线/告警/记录 |
| `VIEWER` | 基础查看 |

## API 与实时推送

REST API 统一前缀 `http://localhost:8080/api/v1`，统一响应 `{ code, message, data }`。

WebSocket/STOMP 端点 `ws://localhost:8080/ws`，主要 topic：

```text
/topic/tasks/{taskId}   /topic/robots/{robotId}   /topic/notifications/{userId}
/topic/alarms           /topic/agent-cases/{caseId}
```

完整接口说明见 `frontend/web/docs/API.md`。

## 前端数据流

核心业务数据以后端为准。WebSocket 实时刷新任务、机器人、告警、通知；首次进入通过 REST API 加载快照。

浏览器本地仅保存 `pi_session`（JWT）和少量 UI 偏好。

## 常用命令

| 场景 | 命令 |
| --- | --- |
| 后端测试 | `cd backend && mvn test` |
| H2 演示启动 | `$env:SPRING_PROFILES_ACTIVE="test"; mvn spring-boot:test-run` |
| MySQL 启动 | `mvn spring-boot:run` |
| 前端开发 | `cd frontend/web && npm install && npm run dev` |
| 前端构建 | `npm run build` |
| Python 测试 | `python -m pytest tests` |
| 启动 AI 服务 | `uvicorn app:app --host 0.0.0.0 --port 9001` |

## 常见问题

### 后端提示 Java 版本不正确
Spring Boot 3 需要 JDK 17+。先检查 `java -version`，必要时切换 `JAVA_HOME`。

### 连接 MySQL 失败
确认 MySQL 已启动、数据库已创建，且 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 正确。快速演示可用 H2。

### Flyway 报 non-empty schema
当前数据库有表但无 `flyway_schema_history`。开发环境建议使用空库重新启动。

### 前端登录失败
需要同时启动后端和前端。登录和业务页面依赖 `/api/v1`。

### 巡检规划如何加载地图
在 `/routes` 页面上传 `.yaml` + `.pgm` 即可。地图不上传服务器，标注结果保存到 `executorJson`。

## 当前限制

- 课程/演示级系统，非生产部署方案
- 真实机器人控制未接入；`RobotGateway` 默认模拟执行
- YAML/PGM 需每次本地上传，未实现服务端地图存储
- 巡检规划 Leaflet 地图与 ROS PGM 标注页未统一
- LocateAnything 已接入真实模型，但未做生产级性能优化
- 生产级日志、审计、监控、CI/CD 需进一步完善
- MySQL 兼容性仅通过 H2 测试验证
- 硬件端协作参见 [electric-power-inspection-robot](https://github.com/liaojingwu20041031/electric-power-inspection-robot)
