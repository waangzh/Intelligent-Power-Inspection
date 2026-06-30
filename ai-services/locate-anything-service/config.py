import os
from pathlib import Path


SERVICE_DIR = Path(__file__).resolve().parent


class Settings:
    use_real_model: bool = os.getenv("LOCATE_ANYTHING_USE_REAL_MODEL", "false").lower() == "true"
    model_path: str = os.getenv("LOCATE_ANYTHING_MODEL_PATH", "../model/locate-anything-service")
    model_version: str = os.getenv(
        "LOCATE_ANYTHING_MODEL_VERSION",
        model_path if use_real_model else "mock-locate-anything",
    )
    device: str = os.getenv("LOCATE_ANYTHING_DEVICE", "cuda")
    dtype: str = os.getenv("LOCATE_ANYTHING_DTYPE", "bfloat16")
    max_new_tokens: int = int(os.getenv("LOCATE_ANYTHING_MAX_NEW_TOKENS", "128"))
    temperature: float = float(os.getenv("LOCATE_ANYTHING_TEMPERATURE", "0.7"))
    max_raw_answer_chars: int = int(os.getenv("LOCATE_ANYTHING_MAX_RAW_ANSWER_CHARS", "2000"))
    max_answer_boxes: int = int(os.getenv("LOCATE_ANYTHING_MAX_ANSWER_BOXES", "5"))
    annotated_output_dir: str = os.getenv("ANNOTATED_OUTPUT_DIR", str(SERVICE_DIR / "annotated-images"))
    annotated_base_url: str = os.getenv("ANNOTATED_BASE_URL", "http://127.0.0.1:9001/files/annotated")


settings = Settings()
