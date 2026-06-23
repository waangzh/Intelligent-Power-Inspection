from fastapi.testclient import TestClient

from app import app


client = TestClient(app)


def test_locate_checkpoint_api():
    response = client.post(
        "/v1/locate/checkpoint",
        json={
            "requestId": "task_001_cp_001",
            "imageUrl": "http://example.test/cp.jpg",
            "imageWidth": 1000,
            "imageHeight": 500,
            "detections": [{"type": "SWITCH", "prompt": "红色刀闸开关", "threshold": 0.75}],
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "SUCCEEDED"
    assert body["findings"][0]["type"] == "SWITCH"
