# 电力智能巡检 — 微信小程序

与 `frontend/web` 功能对齐的微信小程序端，**共用同一后端 REST API**（`/api/v1`）。

## 快速开始

1. 安装 [微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)
2. 导入本项目目录：`frontend/wechat-program`
3. AppID 可使用测试号（`project.config.json` 中 `touristappid`）
4. 启动后端（见仓库根目录 README）
5. 编译运行（**默认对接真实后端**）

## 对接共用后端（默认）

`miniprogram/config/api.js` 默认 `useMock: false`，请求 `http://localhost:8080/api/v1`。

开发阶段在微信开发者工具中：详情 → 本地设置 → 勾选「不校验合法域名」。

## 演示模式（无后端时）

复制本地配置示例并启用 Mock：

```bash
cp miniprogram/config/api.local.example.js miniprogram/config/api.local.js
```

`api.local.js` 已加入 `.gitignore`，不会提交到 Git。演示账号与 web 端相同：`admin` / `Admin@123` 等。

## 权限

运行时权限来自后端 `/auth/login`、`/auth/me` 返回的 `permissions[]`。Mock 演示登录使用 `miniprogram/generated/permissions.js`（由 backend codegen 生成，勿手工维护）。

仓库根目录校验三端一致性：

```bash
npm run permissions:check
```

## 目录结构

```text
wechat-program/
├─ docs/API.md              # 接口文档
├─ scripts/                 # 本地校验脚本
├─ project.config.json
└─ miniprogram/
   ├─ app.js / app.json
   ├─ config/               # api、api.local.example、menu、theme
   ├─ services/             # 业务层（mock + HTTP）
   ├─ utils/                 # request、permission、analytics
   ├─ components/            # page-header、user-avatar 等
   └─ pages/                 # 全部业务页面
```

## 功能模块

| 模块 | 页面 |
|------|------|
| 认证 | 登录、注册 |
| Tab 栏 | 总览、监控、告警（含工单入口）、任务、我的 |
| 个人中心 | 我的信息、头像、安全、记录、设置、消息中心 |

## 接口文档

详见 [docs/API.md](./docs/API.md)

## 与 web 端差异

| 项目 | 说明 |
|------|------|
| 3D 地图 | 小程序简化为 2D / 列表展示 |
| 数据存储 | mock 模式用 wx.storage，与 web localStorage 不互通 |
| Agent 模块 | 小程序暂未实现 Agent 页面 |
| 共用后端 | 默认 `useMock: false`，两端数据一致 |
