#!/usr/bin/env python3
"""单张图片检测 + 自动画框保存，不走 API 服务。

用法:
  python infer_and_draw.py --model . --image test.jpg --query "insulator"
  python infer_and_draw.py --model . --image test.jpg --query "broken glass" --output result.jpg
"""

import argparse
import logging
import os
import re
import sys
from pathlib import Path
import warnings
warnings.filterwarnings("ignore")
logging.getLogger("transformers").setLevel(logging.ERROR)
logging.getLogger("modeling").setLevel(logging.ERROR)

# 把模型目录加到 sys.path，以便 import batch_utils
MODEL_DIR = Path(__file__).resolve().parent.parent / "model" / "locate-anything-service"
sys.path.insert(0, str(MODEL_DIR))

from PIL import Image, ImageDraw

# ---- 坐标解析（跟 parser.py 一致）----

BOX_PATTERN = re.compile(
    r"<box>\s*<\s*(\d+)\s*>\s*<\s*(\d+)\s*>\s*<\s*(\d+)\s*>\s*<\s*(\d+)\s*>\s*</box>",
    re.IGNORECASE,
)
POINT_PATTERN = re.compile(
    r"<box>\s*<\s*(\d+)\s*>\s*<\s*(\d+)\s*>\s*</box>", re.IGNORECASE,
)
NONE_PATTERN = re.compile(r"<box>\s*none\s*</box>", re.IGNORECASE)


def normalize_box(box):
    """如果第二个值大于第三个，交换它们（模型输出可能是 xywh）"""
    a, b, c, d = box
    if b > c:
        return [a, c, b, d]
    return box


def normalized_to_pixel(box, img_w, img_h):
    """归一化坐标 0-1000 → 像素坐标"""
    x1, y1, x2, y2 = box
    return [
        round(x1 * img_w / 1000),
        round(y1 * img_h / 1000),
        round(x2 * img_w / 1000),
        round(y2 * img_h / 1000),
    ]


def parse_boxes(raw_answer):
    """从模型输出中提取所有 bounding box（归一化坐标）"""
    if not raw_answer or NONE_PATTERN.search(raw_answer):
        return []
    boxes = []
    for m in BOX_PATTERN.finditer(raw_answer):
        boxes.append(normalize_box([int(v) for v in m.groups()]))
    if not boxes:
        m = POINT_PATTERN.search(raw_answer)
        if m:
            return [("point", [int(v) for v in m.groups()])]
    return [("box", b) for b in boxes]


# ---- 画框 ----

COLORS = ["red", "lime", "yellow", "cyan", "magenta", "orange", "white"]


def draw_boxes(image, detections, stroke_width=None):
    """在图片上画框/点，返回标注后的图片副本"""
    img = image.copy()
    draw = ImageDraw.Draw(img)
    sw = stroke_width or max(3, round(min(img.width, img.height) / 200))

    for i, (kind, coords) in enumerate(detections):
        color = COLORS[i % len(COLORS)]
        if kind == "box":
            x1, y1, x2, y2 = normalized_to_pixel(coords, img.width, img.height)
            draw.rectangle([x1, y1, x2, y2], outline=color, width=sw)
        elif kind == "point":
            x, y = normalized_to_pixel(coords, img.width, img.height) if len(coords) == 2 else coords
            r = max(6, sw * 2)
            draw.ellipse([x - r, y - r, x + r, y + r], outline=color, width=sw)

    return img


# ---- 主流程 ----

def main():
    ap = argparse.ArgumentParser(description="单张图片检测并画框保存")
    ap.add_argument("--model", default="../model/locate-anything-service", help="模型路径")
    ap.add_argument("--image", default="input_image/image.png", help="输入图片路径")
    ap.add_argument("--query", required=True, help="检测目标描述，如 insulator / broken glass")
    ap.add_argument("--output", "-o", default="", help="输出图片路径，默认 {输入名}_annotated.jpg")
    ap.add_argument("--max-new-tokens", type=int, default=2048)
    ap.add_argument("--temperature", type=float, default=0.7)

    args = ap.parse_args()

    # 设置模型路径环境变量（batch_utils 需要）
    os.environ["LA_FLASH_MODEL"] = args.model

    from batch_utils import generate_batch_hybrid, load
    from batch_utils.hybrid_runtime import load_pil

    # 加载模型
    print(f"⏳ 加载模型: {args.model}")
    load()
    print("✅ 模型就绪")

    # 加载图片
    image = load_pil(args.image)
    print(f"📷 图片尺寸: {image.width}x{image.height}")

    # 推理
    print(f"🔍 检测目标: {args.query}")
    texts = generate_batch_hybrid(
        [(image, args.query)],
        temperature=args.temperature,
        max_new_tokens=args.max_new_tokens,
    )
    raw_answer = texts[0]

    # 解析坐标
    detections = parse_boxes(raw_answer)
    if not detections:
        print("⚠️  未检测到目标 (none / 无法解析)")
        print(f"原始输出: {raw_answer[:500]}")
        return

    print(f"✅ 检测到 {len(detections)} 个目标:")
    for i, (kind, coords) in enumerate(detections):
        px = normalized_to_pixel(coords, image.width, image.height)
        print(f"   [{i}] {kind}: 归一化={coords}  像素={px}")

    # 画框保存
    output_dir = Path("output_image")
    output_dir.mkdir(exist_ok=True)
    output_path = args.output or str(output_dir / (Path(args.image).stem + "_annotated.jpg"))
    annotated = draw_boxes(image, detections)
    annotated.save(output_path, quality=92)
    print(f"💾 标注图片已保存: {output_path}")


if __name__ == "__main__":
    main()