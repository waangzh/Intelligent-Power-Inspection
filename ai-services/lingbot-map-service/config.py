import os
from pathlib import Path


class Settings:
    storage_dir: Path = Path(os.getenv("LINGBOT_STORAGE_DIR", "../runtime-storage/maps")).resolve()
    artifact_base_url: str = os.getenv("LINGBOT_ARTIFACT_BASE_URL", "http://127.0.0.1:8080/files/maps")
    use_real_model: bool = os.getenv("LINGBOT_MAP_USE_REAL_MODEL", "false").lower() == "true"


settings = Settings()
