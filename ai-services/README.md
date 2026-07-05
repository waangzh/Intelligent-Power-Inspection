# AI Services

本目录放置电力智能巡检项目的 Python 模型服务。Spring Boot 后端不直接运行模型，而是通过 HTTP 调用这里的 FastAPI 服务。

## 服务

- `locate-anything-service`：检查点图像检测 / grounding，默认端口 `9001`。
- `lingbot-map-service`：视频或图片序列三维建图，默认端口 `9002`。

## 开发启动

```powershell
cd ai-services/locate-anything-service
conda activate ipi-locate-anything
uvicorn app:app --host 0.0.0.0 --port 9001
```

```powershell
cd ai-services/lingbot-map-service
conda activate ipi-locate-anything
uvicorn app:app --host 0.0.0.0 --port 9002
```

LocateAnything 服务已接入真实模型 runner；LingBot-Map 默认使用 mock runner，也支持通过外部命令接入真实建图实现：

```powershell
cd ai-services/lingbot-map-service
conda activate ipi-locate-anything
$env:LINGBOT_MAP_USE_REAL_MODEL="true"
$env:LINGBOT_MAP_COMMAND="python D:\path\to\lingbot_demo.py"
$env:LINGBOT_MAP_TIMEOUT_SECONDS="3600"
uvicorn app:app --host 0.0.0.0 --port 9002
```

真实命令会收到 `--input`、`--output`、`--output-profile`、`--fps`、`--stride`、`--keyframe-interval`、`--window-size`、`--mask-sky` 参数。命令需在输出目录生成 `cloud.ply`、`mesh.glb`、`trajectory.json`、`metadata.json`，可选生成 `preview.mp4`。

LocateAnything 标注结果图默认保存到 `locate-anything-service/annotated-images/`，并通过 `http://127.0.0.1:9001/files/annotated/{filename}` 访问。该目录仅保留 `.gitkeep`，实际生成的图片不提交 Git。

LingBot-Map 产物默认写入 `backend/runtime-storage/lingbot/maps`，并通过 Spring Boot 的 `/model-files/lingbot/maps/{mapId}/...` 暴露。

## 后端配置

```yaml
app:
  model:
    mode: http
    locate-anything:
      base-url: http://127.0.0.1:9001
      timeout-seconds: 900
      generation-mode: fast
    lingbot-map:
      base-url: http://127.0.0.1:9002
```

不要提交模型权重、视频、点云、mesh、token 或真实生产地址。
