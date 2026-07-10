# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 环境

本项目使用 conda 环境 `ipi-locate-anything`，通过 `locate-anything-service/environment.yml` 定义。

```bash
conda env create -f locate-anything-service/environment.yml
conda activate ipi-locate-anything
```

真实 LocateAnything 模型还需要 PyTorch CUDA wheel（Windows 上 conda CUDA 包容易冲突，单独安装）：

```bash
conda run -n ipi-locate-anything python -m pip install -r locate-anything-service/requirements-torch-cu126.txt
```

## 架构

两个 FastAPI 服务，被 Spring Boot 后端通过 HTTP 调用。`common/` 为共享模块（schemas、错误处理、存储工具、日志）。

- `locate-anything-service`（端口 9001）：**同步** API，接收检查点图像和检测目标列表，返回检测框/点坐标。`POST /v1/locate/checkpoint`

## 开发启动

启动服务前使用 conda 环境 `ipi-locate-anything`。

```bash
conda activate ipi-locate-anything
cd ai-services/locate-anything-service
uvicorn app:app --host 0.0.0.0 --port 9001
```

## 运行测试

```bash
conda activate ipi-locate-anything
cd ai-services/locate-anything-service
python -m pytest tests/ -v
```

## Mock vs 真实模型

默认为 mock 模式，返回固定假数据。切换到真实模型通过环境变量：

- `LOCATE_ANYTHING_USE_REAL_MODEL=true` — 加载 LocateAnything 模型（需 GPU）
- `LOCATE_ANYTHING_MODEL_PATH=../model/locate-anything-service` — 模型路径（默认本地）

服务之间交互流程：后端调用 locate-anything 获取检测结果。
