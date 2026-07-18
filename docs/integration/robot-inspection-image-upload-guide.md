# 机器人巡检图片上传与检测策略联调指南

## 1. 目标与边界

本指南定义机器人巡检图片进入平台的正式链路：

```text
机器人 / Jetson
  -> HTTPS + 设备 Token
Robot Bridge
  -> 内网 + Bridge 平台凭据
Spring Boot
  -> 管理员查询并选择图片
检测策略页面
  -> 管理员手动提交 LocateAnything 检测
```

检测策略页面不会与机器人相机建立直连，也不会接收浏览器上传的任意图片 URL。机器人图片必须先经由 Robot Bridge 和 Spring Boot 校验、持久化，并关联到真实的任务执行和检查点；管理员随后在页面选择已入库图片并人工点击检测。

本期不包含自动调用模型、自动创建告警、刀闸状态判断或表计读数判断。

## 2. 网络部署与访问地址

| 调用方 | 目标 | 地址形式 | 是否公网开放 |
| --- | --- | --- | --- |
| 机器人 / Jetson | Robot Bridge 设备 API | `https://<bridge-host>/robot-api/v1/inspection-images` | 是，仅开放 `/robot-api/` |
| Robot Bridge | Spring Boot 内部接收 API | `http://<platform-host>:8080/api/v1/internal/robot-inspection-images` | 否，仅 Bridge 可访问 |
| 浏览器 | Spring Boot 图片与查询 API | `http://<platform-host>:8080/api/v1/...`、`/model-files/...` | 按平台现有 Web 部署策略 |

生产环境中，Robot Bridge 管理接口 `/bridge/v1/**` 只监听回环地址，不能通过公网或浏览器访问。Nginx 只代理 `/robot-api/`，并设置 `client_max_body_size` 不小于 `20m`。

当前本地开发环境中，Spring Boot 在 `127.0.0.1:8080` 启动时，Bridge 的 `PLATFORM_BASE_URL` 可以设为 `http://127.0.0.1:8080`。真实机器人不能访问开发机回环地址，现场联调必须把 Bridge 部署到机器人可访问、且 Bridge 可访问 Spring Boot 的网络位置。

## 3. Bridge 配置

在 Robot Bridge 的受保护环境变量文件中配置以下内容，不得提交真实 Token 到仓库：

```dotenv
# Bridge 转发到 Spring Boot 的内部地址和平台凭据。
PLATFORM_BASE_URL=http://<platform-host>:8080
PLATFORM_BEARER_TOKEN=<bridge-platform-token>

# Bridge/Jetson 设备 ID 到每台设备 Token 的映射。
ROBOT_AUTH_TOKENS_JSON={"robot-001":"<device-token>"}

# 显式开启巡检图片上传，并限制单张原图为 20 MB。
BRIDGE_INSPECTION_IMAGE_UPLOAD_ENABLED=true
BRIDGE_INSPECTION_IMAGE_UPLOAD_MAX_BYTES=20971520
BRIDGE_INSPECTION_IMAGE_UPLOAD_TEMP_DIR=/var/lib/robot-bridge/inspection-image-upload-tmp

# Bridge 自身的管理 API 凭据，不提供给机器人或浏览器。
BRIDGE_API_TOKEN=<bridge-admin-token>
```

Spring Boot 必须配置相同的 Bridge 平台凭据和明确的 ID 映射。以平台机器人 `robot_001` 和设备机器人 `robot-001` 为例：

```yaml
app:
  robot:
    bridge-robot-id-mappings:
      robot_001: robot-001
    bridge-platform-token: ${ROBOT_BRIDGE_PLATFORM_TOKEN}
```

`ROBOT_BRIDGE_PLATFORM_TOKEN` 必须与 Bridge 的 `PLATFORM_BEARER_TOKEN` 相同。机器人上传请求不能携带或获知该平台凭据。

## 4. 机器人上传接口

机器人调用 Robot Bridge 的设备接口：

```http
POST /robot-api/v1/inspection-images
Authorization: Bearer <device-token>
Idempotency-Key: <stable-event-uuid>
Content-Type: multipart/form-data
```

完整地址示例：

```text
https://<bridge-host>/robot-api/v1/inspection-images
```

### 4.1 multipart 字段

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `image` | 是 | 原始巡检图片，支持 `.jpg`、`.jpeg`、`.png`、`.webp`、`.bmp`，最大 20 MB |
| `executionId` | 是 | 平台创建的真实任务执行 ID |
| `taskId` | 是 | 平台任务 ID |
| `checkpointId` | 是 | 该任务路线修订中的检查点 ID |
| `capturedAt` | 是 | 拍摄时刻，UTC ISO-8601，例如 `2026-07-18T08:30:00Z` |
| `imageSha256` | 是 | 图片完整原始字节的 SHA-256，64 位十六进制字符串 |
| `width` | 否 | 图片宽度，仅作元数据；后端以实际解码结果为准 |
| `height` | 否 | 图片高度，仅作元数据；后端以实际解码结果为准 |

机器人不得提交 `robotId`、`siteId`、`status`、文件 URL 或平台用户 JWT。Bridge 仅根据设备 Token 识别设备身份，并在转发时添加受控的 `X-Bridge-Robot-Id`。

### 4.2 请求示例

以下示例仅用于机器人侧联调，`Idempotency-Key` 应在机器人生成图片上传事件时持久化生成。网络超时、Bridge 重启或重传时必须复用同一个值：

```bash
SHA256=$(sha256sum inspection.jpg | awk '{print $1}')

curl --fail-with-body \
  -X POST 'https://<bridge-host>/robot-api/v1/inspection-images' \
  -H 'Authorization: Bearer <device-token>' \
  -H 'Idempotency-Key: 642f5764-84dc-4a6b-9c3a-0a7e3b1dca02' \
  -F 'image=@inspection.jpg;type=image/jpeg' \
  -F 'executionId=<execution-id>' \
  -F 'taskId=<task-id>' \
  -F 'checkpointId=<checkpoint-id>' \
  -F 'capturedAt=2026-07-18T08:30:00Z' \
  -F "imageSha256=${SHA256}" \
  -F 'width=1920' \
  -F 'height=1080'
```

### 4.3 成功响应

Bridge 直接返回设备 API 风格 JSON，不使用 Spring 的 `ApiResponse` 包装：

```json
{
  "imageId": "rimg_xxx",
  "status": "AVAILABLE",
  "source": "ROBOT_BRIDGE",
  "executionId": "exec_xxx",
  "taskId": "task_xxx",
  "checkpointId": "checkpoint_xxx"
}
```

首次上传成功返回 `201 Created`。同一台机器人使用相同 `Idempotency-Key` 且图片 SHA-256 相同的重试返回 `200 OK` 和同一 `imageId`。

## 5. 鉴权、幂等与完整性规则

1. 设备 Token 只用于机器人到 Bridge。Bridge 通过 `ROBOT_AUTH_TOKENS_JSON` 确定设备 ID。
2. `Idempotency-Key` 对同一机器人唯一，最大 160 个字符。它必须持久化在机器人本地上传任务中，不能在每次 HTTP 重试时重新生成。
3. `imageSha256` 必须对待上传文件的完整原始字节计算。Bridge 会重新计算并比较；不一致返回冲突。
4. 同一幂等键搭配不同图片内容返回 `409 Conflict`，不会覆盖已有图片。
5. Bridge 临时保存上传文件、校验后以 multipart 转发给 Spring，并在所有退出路径清理临时文件。
6. Spring 不信任机器人提供的身份。它验证 Bridge 平台凭据、设备 ID 映射、任务归属、执行实例、路线修订和检查点归属后才写入原图。
7. 浏览器只能用图片记录 ID 发起检测，不能提交任意外部图片 URL。

## 6. Spring 任务执行前置条件

正式机器人图片上传前，以下条件必须全部满足：

1. 平台已注册对应机器人，例如平台 ID `robot_001`。
2. Spring Boot 已配置平台 ID 到 Bridge/Jetson ID 的一对一映射。
3. 已创建并关联路线修订的巡检任务，生成了真实的任务执行实例。
4. 机器人上传的 `taskId` 对应该执行实例，`executionId` 与平台保存值一致。
5. 该 `checkpointId` 属于任务所用路线修订，且执行实例的机器人与设备 Token 映射到的平台机器人一致。

管理员“导入测试图片”不要求真实执行实例，仅用于平台页面联调；它不是机器人正式上传的替代接口。

机器人侧应从平台下发的任务、部署或执行上下文读取 `taskId`、`executionId` 和 `checkpointId`，不能自行编造这些 ID。

## 7. 失败响应与机器人处理

Bridge 失败响应格式：

```json
{
  "code": "INVALID_REQUEST",
  "message": "request is invalid",
  "requestId": ""
}
```

| HTTP 状态 | 常见原因 | 机器人处理 |
| --- | --- | --- |
| `200` / `201` | 上传已接收或幂等重试命中 | 标记本地上传任务成功，保存 `imageId` |
| `400` | 缺字段、非法时间、扩展名不支持、任务/检查点不匹配 | 标记最终失败，保留诊断信息，不自动重试 |
| `401` | 设备 Token 无效 | 停止重试，进入凭据故障状态 |
| `404` | Bridge ID 未映射到平台机器人，或关联任务不存在 | 停止重试，等待平台配置或任务同步 |
| `409` | SHA-256 不一致、同一幂等键对应不同内容 | 标记最终失败，保留本地证据 |
| `413` | 图片超过 20 MB 限制 | 标记最终失败，调整采集或压缩策略 |
| `429` / `503` / 网络超时 | Bridge 或平台暂不可用 | 使用原 `Idempotency-Key` 指数退避重试 |

图片必须在机器人本地保留到收到 `200` 或 `201` 后才允许清理。重试时不能重新压缩、覆盖或替换同一幂等键对应的图片。

## 8. 联调检查清单

### 8.1 部署检查

- [ ] Robot Bridge 设备 API 已通过 HTTPS 对机器人网络开放。
- [ ] Bridge 可以访问 Spring Boot 的内部 `8080` 地址。
- [ ] `BRIDGE_INSPECTION_IMAGE_UPLOAD_ENABLED=true` 已生效。
- [ ] Bridge 的 `PLATFORM_BEARER_TOKEN` 与 Spring 的 `ROBOT_BRIDGE_PLATFORM_TOKEN` 完全一致。
- [ ] 平台 `robotId` 和 Bridge/Jetson 设备 ID 映射已配置且一对一。
- [ ] Nginx、Bridge、Spring 的图片上传大小限制均不低于 20 MB。

### 8.2 任务检查

- [ ] 已用真实路线修订创建任务，并确认任务存在执行实例。
- [ ] 机器人持有当前任务的 `taskId`、`executionId` 和合法 `checkpointId`。
- [ ] 设备 Token 所属机器人与任务执行实例所属机器人一致。

### 8.3 上传验证

- [ ] 正确 Token、完整字段和有效图片返回 `201`，响应含 `imageId`。
- [ ] 使用相同幂等键和相同图片重试返回 `200` 与同一 `imageId`。
- [ ] 使用相同幂等键和不同图片返回 `409`。
- [ ] 错误 Token 返回 `401`。
- [ ] 图片 SHA-256 与文件不一致返回 `409`。
- [ ] Bridge 临时上传目录在成功和失败后均无残留图片。

## 9. 检测策略页面操作

机器人上传成功后，管理员按以下步骤进行人工检测：

1. 登录 Web 管理端，进入“检测策略”。
2. 将图片来源切换为“机器人图片”。
3. 按任务、检查点和机器人筛选已入库图片；若图片刚上传，刷新页面或重新进入该来源以重新加载列表。
4. 选择目标图片，确认拍摄时间、机器人和检查点正确。
5. 保留或调整检测项的中文框上名称和 LocateAnything 提示词。
6. 点击“调用模型检测”。浏览器只提交 `imageId` 和检测项，Spring 根据受控存储文件生成模型可访问地址。
7. 等待运行状态变为成功后查看标注图、结构化发现结果和检测历史。

LocateAnything 的定位结果仅用于人工分析，不等同于异常告警结论。本期不会自动写入告警。

## 10. 验收边界

完整平台闭环为：

```text
创建真实任务执行
-> 机器人在检查点采集图片
-> Robot Bridge 上传并转发 Spring
-> 图片出现在检测策略“机器人图片”列表
-> 管理员选图并调用模型
-> 标注图和检测结果可在检测策略及任务详情回看
```

测试通过该闭环后，机器人图片接入即完成第一期联调。原始图片保留 30 天，检测标注图和结构化检测结果保留 90 天。
