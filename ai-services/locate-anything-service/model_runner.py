from io import BytesIO
import html
from pathlib import Path
from urllib.parse import urlparse
import re

import httpx
import logging
import time
from threading import Lock

from common.schemas import LocateCheckpointRequest, LocateFinding
from common.storage import ensure_dir, public_url
from config import settings
from parser import BOX_PATTERN, NONE_PATTERN, POINT_PATTERN, parse_answer


logger = logging.getLogger(__name__)
REF_PATTERN = re.compile(r"<ref>.*?</ref>", re.IGNORECASE | re.DOTALL)
SAFE_FILENAME_PATTERN = re.compile(r"[^A-Za-z0-9_.-]+")
ANNOTATION_COLORS = {
    "PERSON": "#2563EB",
    "HELMET": "#7C3AED",
    "OBSTACLE": "#EA580C",
    "FIRE": "#DC2626",
    "SWITCH": "#16A34A",
    "METER": "#0891B2",
    "OIL_LEAK": "#CA8A04",
    "FOREIGN_OBJECT": "#DB2777",
}
ANNOTATION_FONT_CANDIDATES = (
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
    "/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf",
    "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
    "C:/Windows/Fonts/msyh.ttc",
    "C:/Windows/Fonts/simhei.ttf",
)


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


def annotation_font(size: int):
    from PIL import ImageFont

    candidates = ([settings.annotation_font_path] if settings.annotation_font_path else []) + list(ANNOTATION_FONT_CANDIDATES)
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            try:
                return ImageFont.truetype(candidate, size=size), True
            except OSError:
                continue
    return ImageFont.load_default(), False


def boxes_overlap(left: list[int], right: list[int]) -> bool:
    return left[0] < right[2] and left[2] > right[0] and left[1] < right[3] and left[3] > right[1]


def label_box(draw, text: str, font, anchor_x: int, anchor_y: int, image_width: int, image_height: int, occupied: list[list[int]]) -> list[int]:
    text_bounds = draw.textbbox((0, 0), text, font=font)
    padding_x = 6
    padding_y = 4
    width = text_bounds[2] - text_bounds[0] + padding_x * 2
    height = text_bounds[3] - text_bounds[1] + padding_y * 2
    left = max(0, min(anchor_x, image_width - width))
    preferred_top = anchor_y - height
    candidates = [preferred_top, anchor_y]
    candidates.extend(anchor_y + height * offset for offset in range(1, 6))
    top = max(0, min(anchor_y, image_height - height))
    for candidate in candidates:
        candidate_top = max(0, min(candidate, image_height - height))
        candidate_box = [left, candidate_top, left + width, candidate_top + height]
        if not any(boxes_overlap(candidate_box, existing) for existing in occupied):
            top = candidate_top
            break
    result = [left, top, left + width, top + height]
    occupied.append(result)
    return result


def save_combined_annotated_image(image_url: str, findings: list[dict], request_id: str) -> str | None:
    from PIL import ImageDraw

    if not findings:
        return None
    image = load_image(image_url)
    draw = ImageDraw.Draw(image)
    stroke_width = max(3, round(min(image.width, image.height) / 200))
    font, supports_chinese = annotation_font(max(14, round(min(image.width, image.height) / 35)))
    occupied_labels: list[list[int]] = []

    for finding in findings:
        detection_type = str(finding.get("type") or "TARGET")
        display_label = str(finding.get("label") or detection_type) if supports_chinese else detection_type
        color = ANNOTATION_COLORS.get(detection_type, "#DC2626")
        anchor_x = 0
        anchor_y = 0
        if finding.get("outputType") == "box" and finding.get("normalizedBox"):
            box = normalized_box_to_image_box(finding["normalizedBox"], image.width, image.height)
            draw.rectangle(box, outline=color, width=stroke_width)
            anchor_x, anchor_y = box[0], box[1]
        elif finding.get("outputType") == "point" and finding.get("point"):
            raw_point = finding["point"]
            point = [
                max(0, min(image.width - 1, round(raw_point[0]))),
                max(0, min(image.height - 1, round(raw_point[1]))),
            ]
            radius = max(6, stroke_width * 2)
            draw.ellipse([point[0] - radius, point[1] - radius, point[0] + radius, point[1] + radius], outline=color, width=stroke_width)
            draw.line([point[0] - radius, point[1], point[0] + radius, point[1]], fill=color, width=stroke_width)
            draw.line([point[0], point[1] - radius, point[0], point[1] + radius], fill=color, width=stroke_width)
            anchor_x, anchor_y = point[0], point[1] - radius
        else:
            continue

        bounds = label_box(draw, display_label, font, anchor_x, anchor_y, image.width, image.height, occupied_labels)
        draw.rectangle(bounds, fill=color)
        draw.text((bounds[0] + 6, bounds[1] + 4), display_label, fill="white", font=font)

    output_dir = ensure_dir(settings.annotated_output_dir)
    filename = f"{safe_filename(request_id)}_annotated.jpg"
    output_path = output_dir / filename
    image.save(output_path, format="JPEG", quality=92)
    logger.info("LocateAnything combined annotated image saved path=%s url=%s", output_path, public_url(settings.annotated_base_url, filename))
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
        self.ready = False
        self.model_version = settings.model_version
        self.worker: LocateAnythingWorker | None = None
        self._load_lock = Lock()
        self.warnings: list[str] = []

    def locate_checkpoint(self, request: LocateCheckpointRequest) -> tuple[list[LocateFinding], str | None]:
        parsed_findings: list[dict] = []
        self.warnings = []
        for detection in request.detections:
            prompt = detection.prompt or detection.type
            answer = self._predict(request.imageUrl, prompt, request.generationMode)
            parsed = parse_answer(answer, request.imageWidth, request.imageHeight)
            logger.info("LocateAnything result type=%s prompt=%s outputType=%s rawAnswer=%s", detection.type, prompt, parsed["outputType"], answer)
            if parsed["outputType"] == "none":
                if answer and "<box>none</box>" not in answer.lower():
                    self.warnings.append(f"Unparsed model output: type={detection.type}, prompt={prompt}, rawAnswer={answer[:500]}")
                continue
            parsed_findings.append({
                "type": detection.type,
                "prompt": detection.prompt,
                "label": detection.displayLabel or detection.type,
                "outputType": parsed["outputType"],
                "normalizedBox": parsed["normalizedBox"],
                "pixelBox": parsed["pixelBox"],
                "point": parsed["point"],
                "rawAnswer": answer,
            })

        result_image_url = None
        if parsed_findings:
            try:
                result_image_url = save_combined_annotated_image(request.imageUrl, parsed_findings, request.requestId)
            except Exception as exc:
                logger.warning(
                    "LocateAnything combined annotated image save failed requestId=%s error=%s",
                    request.requestId,
                    exc,
                    exc_info=True,
                )
                self.warnings.append(f"Combined annotated image save failed: error={exc}")

        findings = [
                LocateFinding(
                    type=finding["type"],
                    prompt=finding["prompt"],
                    label=finding["label"],
                    score=None,
                    outputType=finding["outputType"],
                    normalizedBox=finding["normalizedBox"],
                    pixelBox=finding["pixelBox"],
                    point=finding["point"],
                    imageUrl=result_image_url,
                    rawAnswer=finding["rawAnswer"],
                )
                for finding in parsed_findings
        ]
        return findings, result_image_url

    def _predict(self, image_url: str, prompt: str, generation_mode: str) -> str:
        return self._worker().generate(image_url, prompt, generation_mode)

    def load_model(self) -> None:
        if self.worker is not None:
            return
        with self._load_lock:
            if self.worker is not None:
                return
            logger.info(
                "LocateAnything real model loading modelPath=%s device=%s dtype=%s maxNewTokens=%s",
                settings.model_path,
                settings.device,
                settings.dtype,
                settings.max_new_tokens,
            )
            started_at = time.perf_counter()
            self.worker = LocateAnythingWorker()
            self.ready = True
            elapsed_ms = round((time.perf_counter() - started_at) * 1000)
            logger.info("LocateAnything real model loaded modelPath=%s elapsedMs=%s", settings.model_path, elapsed_ms)

    def _worker(self) -> LocateAnythingWorker:
        self.load_model()
        if self.worker is None:
            raise RuntimeError("LocateAnything real model is not loaded")
        return self.worker


runner = LocateAnythingRunner()
