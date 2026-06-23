import re


BOX_PATTERN = re.compile(r"<box><(\d+)><(\d+)><(\d+)><(\d+)></box>")
POINT_PATTERN = re.compile(r"<box><(\d+)><(\d+)></box>")
NONE_PATTERN = re.compile(r"<box>\s*none\s*</box>", re.IGNORECASE)


def parse_answer(answer: str, image_width: int | None = None, image_height: int | None = None) -> dict:
    if not answer or NONE_PATTERN.search(answer):
        return {"outputType": "none", "normalizedBox": None, "pixelBox": None, "point": None}

    box_match = BOX_PATTERN.search(answer)
    if box_match:
        normalized_box = [int(value) for value in box_match.groups()]
        return {
            "outputType": "box",
            "normalizedBox": normalized_box,
            "pixelBox": to_pixel_box(normalized_box, image_width, image_height),
            "point": None,
        }

    point_match = POINT_PATTERN.search(answer)
    if point_match:
        normalized_point = [int(value) for value in point_match.groups()]
        return {
            "outputType": "point",
            "normalizedBox": None,
            "pixelBox": None,
            "point": to_pixel_point(normalized_point, image_width, image_height),
        }

    return {"outputType": "none", "normalizedBox": None, "pixelBox": None, "point": None}


def to_pixel_box(box: list[int], width: int | None, height: int | None) -> list[int] | None:
    if not width or not height:
        return None
    x1, y1, x2, y2 = box
    return [round(x1 * width / 1000), round(y1 * height / 1000), round(x2 * width / 1000), round(y2 * height / 1000)]


def to_pixel_point(point: list[int], width: int | None, height: int | None) -> list[int] | None:
    if not width or not height:
        return point
    x, y = point
    return [round(x * width / 1000), round(y * height / 1000)]
