from pathlib import Path

from common.schemas import ReconstructionJobRequest, ReconstructionJobResponse
from common.storage import ensure_dir, public_url
from config import settings


class LingBotMapRunner:
    def run(self, job_id: str, request: ReconstructionJobRequest) -> ReconstructionJobResponse:
        if settings.use_real_model:
            raise RuntimeError("真实 LingBot-Map runner 尚未接入，请封装官方 demo.py 或底层 API")

        map_id = f"map_{request.requestId}"
        artifact_dir = ensure_dir(settings.storage_dir / map_id)
        self._write_mock_artifacts(artifact_dir, map_id)
        return ReconstructionJobResponse(
            jobId=job_id,
            status="SUCCEEDED",
            progress=100,
            message="mock reconstruction completed",
            frameCount=120,
            pointCount=120000,
            mapId=map_id,
            artifacts={
                "pointCloudUrl": public_url(settings.artifact_base_url, map_id, "cloud.ply"),
                "meshUrl": public_url(settings.artifact_base_url, map_id, "mesh.glb"),
                "trajectoryUrl": public_url(settings.artifact_base_url, map_id, "trajectory.json"),
                "previewVideoUrl": public_url(settings.artifact_base_url, map_id, "preview.mp4"),
                "metadataUrl": public_url(settings.artifact_base_url, map_id, "metadata.json"),
            },
        )

    def _write_mock_artifacts(self, artifact_dir: Path, map_id: str) -> None:
        (artifact_dir / "cloud.ply").write_text("ply\ncomment mock point cloud\n", encoding="utf-8")
        (artifact_dir / "mesh.glb").write_bytes(b"mock-glb")
        (artifact_dir / "trajectory.json").write_text("[]", encoding="utf-8")
        (artifact_dir / "preview.mp4").write_bytes(b"mock-preview")
        (artifact_dir / "metadata.json").write_text(f'{{"mapId":"{map_id}","provider":"mock"}}', encoding="utf-8")


runner = LingBotMapRunner()
