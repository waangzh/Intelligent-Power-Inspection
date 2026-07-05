import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from common.schemas import ReconstructionJobRequest
from config import settings
from runner import LingBotMapRunner


def configure_real_runner(monkeypatch, tmp_path, script):
    monkeypatch.setattr(settings, "use_real_model", True)
    monkeypatch.setattr(settings, "command", f'"{sys.executable}" "{script}"')
    monkeypatch.setattr(settings, "timeout_seconds", 10)
    monkeypatch.setattr(settings, "storage_dir", tmp_path / "maps")
    monkeypatch.setattr(settings, "model_file_root", tmp_path / "model-files")
    monkeypatch.setattr(settings, "artifact_base_url", "http://127.0.0.1:8080/model-files/lingbot/maps")


def test_real_runner_wraps_external_command(monkeypatch, tmp_path):
    script = tmp_path / "fake_lingbot.py"
    script.write_text(
        """
import json
import pathlib
import sys

out = pathlib.Path(sys.argv[sys.argv.index("--output") + 1])
out.mkdir(parents=True, exist_ok=True)
(out / "cloud.ply").write_text("ply\\n", encoding="utf-8")
(out / "mesh.glb").write_bytes(b"glb")
(out / "trajectory.json").write_text("[]", encoding="utf-8")
(out / "metadata.json").write_text(json.dumps({"mapId": "real_map_001", "frameCount": 42, "pointCount": 2048}), encoding="utf-8")
""",
        encoding="utf-8",
    )
    configure_real_runner(monkeypatch, tmp_path, script)

    result = LingBotMapRunner().run(
        "job_001",
        ReconstructionJobRequest(requestId="lingbot_001", siteId="site_001", videoUrl=str(tmp_path / "input.mp4")),
    )

    assert result.status == "SUCCEEDED"
    assert result.mapId == "real_map_001"
    assert result.frameCount == 42
    assert result.pointCount == 2048
    assert result.artifacts["pointCloudUrl"] == "http://127.0.0.1:8080/model-files/lingbot/maps/map_lingbot_001/cloud.ply"


def test_real_runner_fails_when_required_artifact_missing(monkeypatch, tmp_path):
    script = tmp_path / "fake_lingbot_missing.py"
    script.write_text(
        """
import pathlib
import sys

out = pathlib.Path(sys.argv[sys.argv.index("--output") + 1])
out.mkdir(parents=True, exist_ok=True)
(out / "metadata.json").write_text("{}", encoding="utf-8")
""",
        encoding="utf-8",
    )
    configure_real_runner(monkeypatch, tmp_path, script)

    with pytest.raises(RuntimeError, match="产物缺失"):
        LingBotMapRunner().run(
            "job_002",
            ReconstructionJobRequest(requestId="lingbot_002", siteId="site_001", videoUrl=str(tmp_path / "input.mp4")),
        )


def test_command_split_keeps_windows_path(monkeypatch, tmp_path):
    monkeypatch.setattr(settings, "command", r"python D:\path\to\lingbot_demo.py")
    command = LingBotMapRunner()._build_command(
        str(tmp_path / "input.mp4"),
        tmp_path / "output",
        ReconstructionJobRequest(requestId="lingbot_003", siteId="site_001", videoUrl=str(tmp_path / "input.mp4")),
    )

    if sys.platform.startswith("win"):
        assert command[:2] == ["python", r"D:\path\to\lingbot_demo.py"]
