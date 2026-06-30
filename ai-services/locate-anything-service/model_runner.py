from io import BytesIO
import html
from pathlib import Path
from urllib.parse import urlparse
import re

import httpx
import logging
import time

from common.schemas import LocateCheckpointRequest, LocateFinding
from common.storage import ensure_dir, public_url
from config import settings
from parser import BOX_PATTERN, NONE_PATTERN, POINT_PATTERN, parse_answer


logger = logging.getLogger(__name__)
REF_PATTERN = re.compile(r"<ref>.*?</ref>", re.IGNORECASE | re.DOTALL)
SAFE_FILENAME_PATTERN = re.compile(r"[^A-Za-z0-9_.-]+")


def compact_answer(answer: str) -> str:
    normalized_answer = html.unescape(answer or "").strip()
    if not normalized_answer:
        return ""
    if NONE_PATTERN.search(normalized_answer):
        return "<box>none</box>"

    ref_match = REF_PATTERN.search(normalized_answer)
    prefix = ref_match.group(0) if ref_match else ""

    boxes: list[str] = []
    seen_boxes: set[tuple[int, int, int, int]] = set()
    for match in BOX_PATTERN.finditer(normalized_answer):
        values = tuple(int(value) for value in match.groups())
        if values in seen_boxes:
            continue
        seen_boxes.add(values)
        boxes.append(f"<box><{values[0]}><{values[1]}><{values[2]}><{values[3]}></box>")
        if len(boxes) >= settings.max_answer_boxes:
            break
    if boxes:
        return truncate_text(prefix + "".join(boxes), settings.max_raw_answer_chars)

    point_match = POINT_PATTERN.search(normalized_answer)
    if point_match:
        x, y = point_match.groups()
        return f"{prefix}<box><{x}><{y}></box>"

    return truncate_text(normalized_answer, settings.max_raw_answer_chars)


def truncate_text(text: str, max_chars: int) -> str:
    if max_chars <= 0 or len(text) <= max_chars:
        return text
    return text[:max_chars] + "...<truncated>"


def load_image(image_url: str):
    from PIL import Image

    parsed = urlparse(image_url or "")
    if parsed.scheme in {"http", "https"}:
        response = httpx.get(image_url, timeout=30)
        response.raise_for_status()
        return Image.open(BytesIO(response.content)).convert("RGB")
    if parsed.scheme == "file":
        return Image.open(Path(parsed.path)).convert("RGB")
    return Image.open(Path(image_url)).convert("RGB")


def safe_filename(value: str) -> str:
    safe_value = SAFE_FILENAME_PATTERN.sub("_", value or "image").strip("._")
    return safe_value or "image"


def normalized_box_to_image_box(box: list[int], width: int, height: int) -> list[int]:
    x1, y1, x2, y2 = box
    left = round(x1 * width / 1000)
    top = round(y1 * height / 1000)
    right = round(x2 * width / 1000)
    bottom = round(y2 * height / 1000)
    return [max(0, left), max(0, top), min(width - 1, right), min(height - 1, bottom)]


def normalized_point_to_image_point(point: list[int], width: int, height: int) -> list[int]:
    x, y = point
    return [max(0, min(width - 1, round(x * width / 1000))), max(0, min(height - 1, round(y * height / 1000)))]


def save_annotated_image(image_url: str, parsed: dict, request_id: str, index: int) -> str | None:
    from PIL import ImageDraw

    image = load_image(image_url)
    draw = ImageDraw.Draw(image)
    stroke_width = max(3, round(min(image.width, image.height) / 200))

    if parsed["outputType"] == "box" and parsed.get("normalizedBox"):
        box = normalized_box_to_image_box(parsed["normalizedBox"], image.width, image.height)
        draw.rectangle(box, outline="red", width=stroke_width)
    elif parsed["outputType"] == "point" and parsed.get("point"):
        point = normalized_point_to_image_point(parsed["point"], image.width, image.height)
        radius = max(6, stroke_width * 2)
        draw.ellipse([point[0] - radius, point[1] - radius, point[0] + radius, point[1] + radius], outline="red", width=stroke_width)
    else:
        return None

    output_dir = ensure_dir(settings.annotated_output_dir)
    filename = f"{safe_filename(request_id)}_{index}.jpg"
    output_path = output_dir / filename
    image.save(output_path, format="JPEG", quality=92)
    logger.info("LocateAnything annotated image saved path=%s url=%s", output_path, public_url(settings.annotated_base_url, filename))
    return public_url(settings.annotated_base_url, filename)


class LocateAnythingWorker:
    def __init__(self) -> None:
        try:
            import torch
            from transformers import AutoModel, AutoProcessor, AutoTokenizer
        except ImportError as exc:
            raise RuntimeError("Real LocateAnything dependencies are not installed. Please install torch, transformers, Pillow and huggingface_hub.") from exc

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
        self.model.config.use_cache = True
        if getattr(self.model, "generation_config", None) is not None:
            self.model.generation_config.use_cache = True
        self.model.eval()

    def generate(self, image_url: str, prompt: str, generation_mode: str = "hybrid") -> str:
        started_at = time.perf_counter()
        image = self._load_image(image_url)
        logger.info("LocateAnything image loaded url=%s size=%sx%s prompt=%s generationMode=%s", image_url, image.width, image.height, prompt, generation_mode)
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
        apply_chat_template = getattr(self.processor, "py_apply_chat_template", None) or self.processor.apply_chat_template
        conversation = apply_chat_template(
            messages,
            add_generation_prompt=True,
            tokenize=False,
        )
        inputs = self.processor(images=[image], text=[conversation], return_tensors="pt").to(self.device)

        with self.torch.inference_mode():
            output = self.model.generate(
                **inputs,
                tokenizer=self.tokenizer,
                max_new_tokens=settings.max_new_tokens,
                do_sample=False,
                use_cache=True,
                generation_mode=generation_mode,
            )
        raw_answer = self._decode_answer(output, inputs)
        answer = compact_answer(raw_answer)
        elapsed_ms = round((time.perf_counter() - started_at) * 1000)
        logger.info(
            "LocateAnything model generated prompt=%s elapsedMs=%s rawChars=%s answer=%s",
            prompt,
            elapsed_ms,
            len(raw_answer),
            answer,
        )
        return answer

    def _decode_answer(self, output, inputs) -> str:
        if isinstance(output, str):
            return output.strip()
        if isinstance(output, (list, tuple)):
            if not output:
                return ""
            first = output[0]
            if isinstance(first, str):
                return first.strip()
            output = first
        if hasattr(output, "shape"):
            if len(output.shape) >= 2 and output.shape[1] > inputs.input_ids.shape[1]:
                output = output[:, inputs.input_ids.shape[1]:]
            answer = self.processor.batch_decode(output, skip_special_tokens=True)[0]
            return str(answer).strip()
        return str(output).strip()

    def _load_image(self, image_url: str):
        return load_image(image_url)

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
        raise RuntimeError(f"Unsupported LOCATE_ANYTHING_DTYPE: {name}")


class LocateAnythingRunner:
    def __init__(self) -> None:
        self.ready = True
        self.model_version = settings.model_version
        self.worker = LocateAnythingWorker() if settings.use_real_model else None
        self.warnings: list[str] = []

    def locate_checkpoint(self, request: LocateCheckpointRequest) -> list[LocateFinding]:
        findings: list[LocateFinding] = []
        self.warnings = []
        for index, detection in enumerate(request.detections):
            prompt = detection.prompt or detection.type
            answer = self._predict(request.imageUrl, prompt, request.generationMode)
            parsed = parse_answer(answer, request.imageWidth, request.imageHeight)
            logger.info("LocateAnything result type=%s prompt=%s outputType=%s rawAnswer=%s", detection.type, prompt, parsed["outputType"], answer)
            if parsed["outputType"] == "none":
                if answer and "<box>none</box>" not in answer.lower():
                    self.warnings.append(f"Unparsed model output: type={detection.type}, prompt={prompt}, rawAnswer={answer[:500]}")
                continue
            annotated_image_url = None
            try:
                annotated_image_url = save_annotated_image(request.imageUrl, parsed, request.requestId, index)
            except Exception as exc:
                logger.warning(
                    "LocateAnything annotated image save failed requestId=%s index=%s error=%s",
                    request.requestId,
                    index,
                    exc,
                    exc_info=True,
                )
                self.warnings.append(f"Annotated image save failed: type={detection.type}, prompt={prompt}, error={exc}")
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
                    imageUrl=annotated_image_url,
                    rawAnswer=answer,
                )
            )
        return findings

    def _predict(self, image_url: str, prompt: str, generation_mode: str) -> str:
        if self.worker is not None:
            return self.worker.generate(image_url, prompt, generation_mode)
        return f"<ref>{prompt}</ref><box><120><80><360><260></box>"


runner = LocateAnythingRunner()
