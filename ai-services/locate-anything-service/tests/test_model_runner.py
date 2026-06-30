from PIL import Image

from config import settings
from model_runner import compact_answer, save_annotated_image


def test_compact_answer_deduplicates_repeated_boxes():
    answer = (
        "<ref>seat</ref>"
        "<box><0><719><77><797></box>"
        "<box><0><251><999><793></box>"
        "<box><0><251><999><793></box>"
    )

    compacted = compact_answer(answer)

    assert compacted == (
        "<ref>seat</ref>"
        "<box><0><719><77><797></box>"
        "<box><0><251><999><793></box>"
    )


def test_compact_answer_preserves_none():
    assert compact_answer("<ref>seat</ref><box>none</box>") == "<box>none</box>"


def test_save_annotated_image_creates_file(tmp_path, monkeypatch):
    source_image = tmp_path / "source.jpg"
    output_dir = tmp_path / "annotated"
    Image.new("RGB", (100, 50), color="white").save(source_image)
    monkeypatch.setattr(settings, "annotated_output_dir", str(output_dir))
    monkeypatch.setattr(settings, "annotated_base_url", "http://127.0.0.1:9001/files/annotated")

    url = save_annotated_image(
        str(source_image),
        {"outputType": "box", "normalizedBox": [100, 200, 900, 800], "point": None},
        "task/001",
        0,
    )

    assert url == "http://127.0.0.1:9001/files/annotated/task_001_0.jpg"
    assert (output_dir / "task_001_0.jpg").exists()
