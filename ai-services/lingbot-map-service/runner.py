import json
import os
import shlex
import subprocess
from pathlib import Path
from urllib.parse import unquote, urlparse

from common.schemas import ReconstructionJobRequest, ReconstructionJobResponse
from common.storage import ensure_dir, public_url
from config import settings

REQUIRED_ARTIFACTS = ("cloud.ply", "mesh.glb", "trajectory.json", "metadata.json")


class LingBotMapRunner:
    def run(self, job_id: str, request: ReconstructionJobRequest) -> ReconstructionJobResponse:
        if settings.use_real_model:
            return self._run_real(job_id, request)

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

    def _run_real(self, job_id: str, request: ReconstructionJobRequest) -> ReconstructionJobResponse:
        if not settings.command.strip():
            raise RuntimeError("LINGBOT_MAP_COMMAND 未配置")

        input_path = self._input_path(request)
        map_id = f"map_{request.requestId}"
        artifact_dir = ensure_dir(settings.storage_dir / map_id)
        command = self._build_command(input_path, artifact_dir, request)
        completed = subprocess.run(
            command,
            cwd=str(artifact_dir),
            capture_output=True,
            text=True,
            timeout=settings.timeout_seconds,
            check=False,
        )
        if completed.returncode != 0:
            detail = (completed.stderr or completed.stdout or "").strip()
            raise RuntimeError(f"LingBot-Map 命令执行失败: {detail or completed.returncode}")

        missing = [name for name in REQUIRED_ARTIFACTS if not (artifact_dir / name).exists()]
        if missing:
            raise RuntimeError("LingBot-Map 产物缺失: " + ", ".join(missing))

        metadata = self._metadata(artifact_dir / "metadata.json")
        artifacts = {
            "pointCloudUrl": public_url(settings.artifact_base_url, map_id, "cloud.ply"),
            "meshUrl": public_url(settings.artifact_base_url, map_id, "mesh.glb"),
            "trajectoryUrl": public_url(settings.artifact_base_url, map_id, "trajectory.json"),
            "metadataUrl": public_url(settings.artifact_base_url, map_id, "metadata.json"),
        }
        if (artifact_dir / "preview.mp4").exists():
            artifacts["previewVideoUrl"] = public_url(settings.artifact_base_url, map_id, "preview.mp4")

        return ReconstructionJobResponse(
            jobId=job_id,
            status="SUCCEEDED",
            progress=100,
            message="real reconstruction completed",
            frameCount=self._metadata_int(metadata, "frameCount", "frame_count"),
            pointCount=self._metadata_int(metadata, "pointCount", "point_count"),
            mapId=str(metadata.get("mapId") or metadata.get("map_id") or map_id),
            artifacts=artifacts,
            warnings=self._metadata_warnings(metadata),
        )

    def _input_path(self, request: ReconstructionJobRequest) -> str:
        source = request.videoUrl if request.inputKind == "video" else request.imageFolderUrl
        if not source:
            raise RuntimeError("缺少建图输入 videoUrl 或 imageFolderUrl")

        parsed = urlparse(source)
        if parsed.scheme in {"http", "https"} and parsed.path.startswith("/model-files/"):
            relative = unquote(parsed.path.removeprefix("/model-files/")).lstrip("/")
            return str((settings.model_file_root / relative).resolve())
        if parsed.scheme == "file":
            return unquote(parsed.path)
        if parsed.scheme:
            return source
        return str(Path(source).expanduser().resolve())

    def _build_command(self, input_path: str, artifact_dir: Path, request: ReconstructionJobRequest) -> list[str]:
        command = self._split_command(settings.command)
        return command + [
            "--input",
            input_path,
            "--output",
            str(artifact_dir),
            "--output-profile",
            request.outputProfile,
            "--fps",
            str(request.fps or 10),
            "--stride",
            str(request.stride or 1),
            "--keyframe-interval",
            str(request.keyframeInterval or 5),
            "--window-size",
            str(request.windowSize or 16),
            "--mask-sky",
            "true" if request.maskSky else "false",
        ]

    def _split_command(self, command: str) -> list[str]:
        parts = shlex.split(command, posix=os.name != "nt")
        if os.name == "nt":
            return [part[1:-1] if len(part) >= 2 and part[0] == part[-1] == '"' else part for part in parts]
        return parts

    def _metadata(self, path: Path) -> dict:
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
            return raw if isinstance(raw, dict) else {}
        except json.JSONDecodeError:
            return {}

    def _metadata_int(self, metadata: dict, *keys: str) -> int | None:
        for key in keys:
            value = metadata.get(key)
            if isinstance(value, int):
                return value
            if isinstance(value, str) and value.isdigit():
                return int(value)
        return None

    def _metadata_warnings(self, metadata: dict) -> list[str]:
        warnings = metadata.get("warnings", [])
        return warnings if isinstance(warnings, list) else []


runner = LingBotMapRunner()
