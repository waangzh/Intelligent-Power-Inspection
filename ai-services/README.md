# AI Services

本目录放置电力智能巡检项目的 Python 模型服务。Spring Boot 后端不直接运行模型，而是通过 HTTP 调用这里的 FastAPI 服务。

## 服务

- `locate-anything-service`：检查点图像检测 / grounding，默认端口 `9001`。

## 开发启动

```powershell
cd ai-services/locate-anything-service
conda activate ipi-locate-anything
uvicorn app:app --host 0.0.0.0 --port 9001
```

LocateAnything 服务已接入真实模型 runner。

LocateAnything 标注结果图默认保存到 `locate-anything-service/annotated-images/`，并通过 `http://127.0.0.1:9001/files/annotated/{filename}` 访问。该目录仅保留 `.gitkeep`，实际生成的图片不提交 Git。

## 后端配置

```yaml
app:
  model:
    mode: http
    locate-anything:
      base-url: http://127.0.0.1:9001
      timeout-seconds: 900
      generation-mode: fast
```

不要提交模型权重、视频、点云、mesh、token 或真实生产地址。
