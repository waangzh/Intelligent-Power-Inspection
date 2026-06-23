from pathlib import Path
from urllib.parse import urlparse


def ensure_dir(path: str | Path) -> Path:
    directory = Path(path)
    directory.mkdir(parents=True, exist_ok=True)
    return directory


def public_url(base_url: str, *parts: str) -> str:
    clean_base = base_url.rstrip("/")
    clean_parts = "/".join(str(part).strip("/") for part in parts if part is not None)
    return f"{clean_base}/{clean_parts}"


def filename_from_url(url: str, fallback: str) -> str:
    parsed = urlparse(url or "")
    name = Path(parsed.path).name
    return name or fallback
