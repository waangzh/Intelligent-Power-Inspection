import os
from pathlib import Path

SERVICE_DIR = Path(__file__).resolve().parent
REPO_ROOT = SERVICE_DIR.parents[1]
DEFAULT_MODEL_FILE_ROOT = REPO_ROOT / "backend" / "runtime-storage"
DEFAULT_STORAGE_DIR = DEFAULT_MODEL_FILE_ROOT / "lingbot" / "maps"


class Settings:
    storage_dir: Path = Path(os.getenv("LINGBOT_STORAGE_DIR", str(DEFAULT_STORAGE_DIR))).resolve()
    model_file_root: Path = Path(os.getenv("LINGBOT_MODEL_FILE_ROOT", str(DEFAULT_MODEL_FILE_ROOT))).resolve()
    artifact_base_url: str = os.getenv("LINGBOT_ARTIFACT_BASE_URL", "http://127.0.0.1:8080/model-files/lingbot/maps")
    use_real_model: bool = os.getenv("LINGBOT_MAP_USE_REAL_MODEL", "false").lower() == "true"
    command: str = os.getenv("LINGBOT_MAP_COMMAND", "")
    timeout_seconds: int = int(os.getenv("LINGBOT_MAP_TIMEOUT_SECONDS", "3600"))


settings = Settings()
