from fastapi.testclient import TestClient

from app import app


client = TestClient(app)


def test_reconstruction_job_lifecycle():
    response = client.post(
        "/v1/reconstruction/jobs",
        json={
            "requestId": "lingbot_job_001",
            "siteId": "site_001",
            "inputKind": "video",
            "videoUrl": "http://example.test/video.mp4",
        },
    )
    assert response.status_code == 200
    job_id = response.json()["jobId"]

    status_response = client.get(f"/v1/reconstruction/jobs/{job_id}")
    assert status_response.status_code == 200
    assert status_response.json()["status"] in {"QUEUED", "RUNNING", "SUCCEEDED"}
