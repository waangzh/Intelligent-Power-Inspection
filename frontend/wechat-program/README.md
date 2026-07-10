# 电力智能巡检 — 微信小程序

与 `frontend/web` 功能对齐的微信小程序端，**共用同一后端 REST API**（`/api/v1`）。

## 快速开始

1. 安装 [微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)
2. 导入本项目目录：`frontend/wechat-program`
3. AppID 可使用测试号（`project.config.json` 中 `touristappid`）
4. 编译运行

## 演示模式（默认）

`miniprogram/config/api.js`：

```javascript
useMock: true   // 无需后端，数据存在 wx.storage
```

演示账号与 web 端相同：`admin` / `Admin@123` 等。

## 对接共用后端

1. 启动后端（与 web 端相同地址）
2. 修改配置：

```javascript
// miniprogram/config/api.js
baseUrl: 'http://localhost:8080/api/v1',
useMock: false,
```

3. 开发阶段在微信开发者工具中关闭域名校验

## 目录结构

```text
wechat-program/
├─ docs/API.md              # 接口文档
├─ project.config.json
└─ miniprogram/
   ├─ app.js / app.json
   ├─ config/               # api、menu、theme
   ├─ services/             # 业务层（mock + HTTP）
   ├─ utils/                 # request、permission、analytics
   ├─ components/            # page-header、user-avatar 等
   └─ pages/                 # 全部业务页面
```

## 功能模块

| 模块 | 页面 |
|------|------|
| 认证 | 登录、注册 |
| Tab 栏 | 总览、监控、告警、任务、我的 |
| 监控中心 | 集控大屏 |
| 运维中心 | 工单、消息 |
| 巡检业务 | 站点、路线、任务详情 |
| 资产感知 | 机器人、检测策略 |
| 数据中心 | 巡检记录、统计分析 |
| 系统管理 | 用户管理、个人中心 |

更多功能入口：**我的 → 全部功能** 或 **总览 → 更多**

## 接口文档

详见 [docs/API.md](./docs/API.md)

## 与 web 端差异

| 项目 | 说明 |
|------|------|
| 3D 地图 | 小程序简化为 2D / 列表展示 |
| 大屏 | 移动端简化布局，深色主题保留 |
| 数据存储 | mock 模式用 wx.storage，与 web localStorage 不互通 |
| 共用后端 | `useMock: false` 后两端数据一致 |
