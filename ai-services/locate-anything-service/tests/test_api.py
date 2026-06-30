from fastapi.testclient import TestClient
from PIL import Image

from app import annotated_dir, app
from config import settings


client = TestClient(app)


def test_locate_checkpoint_api(tmp_path, monkeypatch):
    source_image = tmp_path / "cp.jpg"
    output_dir = tmp_path / "annotated"
    Image.new("RGB", (1000, 500), color="white").save(source_image)
    monkeypatch.setattr(settings, "annotated_output_dir", str(output_dir))
    monkeypatch.setattr(settings, "annotated_base_url", "http://127.0.0.1:9001/files/annotated")

    response = client.post(
        "/v1/locate/checkpoint",
        json={
            "requestId": "task_001_cp_001",
            "imageUrl": str(source_image),
            "imageWidth": 1000,
            "imageHeight": 500,
            "detections": [{"type": "SWITCH", "prompt": "red knife switch", "threshold": 0.75}],
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCEEDED"
    assert body["findings"][0]["type"] == "SWITCH"
    assert body["findings"][0]["imageUrl"] == "http://127.0.0.1:9001/files/annotated/task_001_cp_001_0.jpg"
    assert (output_dir / "task_001_cp_001_0.jpg").exists()

def test_annotated_static_files_are_served():
    static_file = annotated_dir / "static_test.txt"
    static_file.write_text("ok", encoding="utf-8")
    try:
        response = client.get("/files/annotated/static_test.txt")
        assert response.status_code == 200
        assert response.text == "ok"
    finally:
        static_file.unlink(missing_ok=True)
