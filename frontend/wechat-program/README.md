# 电力智能巡检 — 微信小程序

与 `frontend/web` 功能对齐的微信小程序端，**共用同一后端 REST API**（`/api/v1`）。

## 快速开始

1. 安装 [微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)
2. 导入本项目目录：`frontend/wechat-program`
3. AppID 可使用测试号（`project.config.json` 中 `touristappid`）
4. 启动后端（见仓库根目录 README）
5. 在仓库根目录生成运行配置并编译（**默认对接真实后端**）：

```bash
npm run miniprogram:env
```

开发阶段在微信开发者工具中：详情 → 本地设置 → 勾选「不校验合法域名」。

## 对接共用后端（默认）

`npm run miniprogram:env` 会生成 `miniprogram/config/build-env.js`（`useMock: false`），请求 `http://localhost:8080/api/v1`。

## 演示模式

### wx.storage 本地 Mock（无后端）

```bash
npm run miniprogram:env:mock
```

演示账号与 web 端相同：`admin` / `Admin@123` 等。Mock 权限矩阵来自 `generated/permissions.js`（codegen，与 backend 一致）。

### OpenAPI Mock Server（Prism）

```bash
npm run mock:openapi          # 需后端 /v3/api-docs 可访问
npm run miniprogram:env:openapi
```

## 权限与领域枚举

- 运行时权限来自 `/auth/login`、`/auth/me` 的 `permissions[]`
- 状态枚举（任务/工单/告警等）来自 `miniprogram/generated/domain-enums.js`
- 仓库根目录校验三端一致性：

```bash
npm run codegen:check
```

## 目录结构

```text
wechat-program/
├─ docs/API.md              # 接口文档
├─ project.config.json
└─ miniprogram/
   ├─ app.js / app.json
   ├─ config/               # api、build-env（生成）、api.local.example
   ├─ generated/            # permissions.js、domain-enums.js（codegen）
   ├─ services/             # 业务层（mock + HTTP）
   ├─ utils/
   └─ pages/
```

## 与 Web 端对齐

| 项目 | 说明 |
| --- | --- |
| 共用后端 | 默认 `useMock: false`，两端数据一致 |
| 权限 codegen | `npm run permissions:generate` |
| 枚举 codegen | `npm run domain:generate` |
| Mock 开关 | 构建变量 `USE_MOCK`，不写死在源码 |
