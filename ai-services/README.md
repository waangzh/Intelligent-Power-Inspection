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

默认实现是 mock runner，用于先稳定后端与 Python 服务之间的协议。真实 LocateAnything / LingBot-Map 接入时，只替换各服务的 `model_runner.py` / `runner.py`。

## 后端配置

```yaml
app:
  model:
    mode: http
    locate-anything:
      base-url: http://127.0.0.1:9001
    lingbot-map:
      base-url: http://127.0.0.1:9002
```

不要提交模型权重、视频、点云、mesh、token 或真实生产地址。
