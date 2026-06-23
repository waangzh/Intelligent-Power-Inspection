from io import BytesIO
from pathlib import Path
from urllib.parse import urlparse

import httpx

from common.schemas import LocateCheckpointRequest, LocateFinding
from common.storage import public_url
from config import settings
from parser import parse_answer


class LocateAnythingWorker:
    def __init__(self) -> None:
        try:
            import torch
            from transformers import AutoModel, AutoProcessor, AutoTokenizer
        except ImportError as exc:
            raise RuntimeError(
                "真实 LocateAnything 模型依赖未安装，请先安装 torch、transformers、Pillow、huggingface_hub"
            ) from exc

        self.torch = torch
        self.device = settings.device
        model_path = settings.model_path
        dtype = self._dtype(settings.dtype)

        self.tokenizer = AutoTokenizer.from_pretrained(model_path, trust_remote_code=True)
        self.processor = AutoProcessor.from_pretrained(model_path, trust_remote_code=True)
        self.model = AutoModel.from_pretrained(
            model_path,
            trust_remote_code=True,
            torch_dtype=dtype,
        ).to(self.device)
        self.model.eval()

    def generate(self, image_url: str, prompt: str) -> str:
        image = self._load_image(image_url)
        question = (
            "Locate a single instance in the image that matches this inspection target: "
            f"{prompt}. Return the result as <box><x1><y1><x2><y2></box> or <box>none</box>."
        )

        messages = [{
            "role": "user",
            "content": [
                {"type": "image", "image": image},
                {"type": "text", "text": question},
            ],
        }]
        conversation = self.processor.apply_chat_template(
            messages,
            add_generation_prompt=True,
            tokenize=False,
        )
        inputs = self.processor(images=[image], text=[conversation], return_tensors="pt").to(self.device)

        with self.torch.inference_mode():
            output_ids = self.model.generate(
                **inputs,
                max_new_tokens=settings.max_new_tokens,
                do_sample=False,
            )
        output_ids = output_ids[:, inputs.input_ids.shape[1]:]
        answer = self.processor.batch_decode(output_ids, skip_special_tokens=True)[0]
        return str(answer).strip()

    def _load_image(self, image_url: str):
        from PIL import Image

        parsed = urlparse(image_url or "")
        if parsed.scheme in {"http", "https"}:
            response = httpx.get(image_url, timeout=30)
            response.raise_for_status()
            return Image.open(BytesIO(response.content)).convert("RGB")
        if parsed.scheme == "file":
            return Image.open(Path(parsed.path)).convert("RGB")
        return Image.open(Path(image_url)).convert("RGB")

    def _dtype(self, name: str):
        normalized = (name or "").lower()
        if normalized in {"auto", ""}:
            return "auto"
        if normalized == "float16":
            return self.torch.float16
        if normalized == "float32":
            return self.torch.float32
        if normalized == "bfloat16":
            return self.torch.bfloat16
        raise RuntimeError(f"不支持的 LOCATE_ANYTHING_DTYPE: {name}")


class LocateAnythingRunner:
    def __init__(self) -> None:
        self.ready = True
        self.model_version = settings.model_version
        self.worker = LocateAnythingWorker() if settings.use_real_model else None

    def locate_checkpoint(self, request: LocateCheckpointRequest) -> list[LocateFinding]:
        findings: list[LocateFinding] = []
        for index, detection in enumerate(request.detections):
            prompt = detection.prompt or detection.type
            answer = self._predict(request.imageUrl, prompt)
            parsed = parse_answer(answer, request.imageWidth, request.imageHeight)
            if parsed["outputType"] == "none":
                continue
            findings.append(
                LocateFinding(
                    type=detection.type,
                    prompt=detection.prompt,
                    label="abnormal",
                    score=None,
                    outputType=parsed["outputType"],
                    normalizedBox=parsed["normalizedBox"],
                    pixelBox=parsed["pixelBox"],
                    point=parsed["point"],
                    imageUrl=public_url(settings.annotated_base_url, f"{request.requestId}_{index}.jpg"),
                    rawAnswer=answer,
                )
            )
        return findings

    def _predict(self, image_url: str, prompt: str) -> str:
        if self.worker is not None:
            return self.worker.generate(image_url, prompt)
        return f"<ref>{prompt}</ref><box><120><80><360><260></box>"


runner = LocateAnythingRunner()
