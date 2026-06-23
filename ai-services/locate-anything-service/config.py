import os


class Settings:
    use_real_model: bool = os.getenv("LOCATE_ANYTHING_USE_REAL_MODEL", "false").lower() == "true"
    model_path: str = os.getenv("LOCATE_ANYTHING_MODEL_PATH", "nvidia/LocateAnything-3B")
    model_version: str = os.getenv(
        "LOCATE_ANYTHING_MODEL_VERSION",
        model_path if use_real_model else "mock-locate-anything",
    )
    device: str = os.getenv("LOCATE_ANYTHING_DEVICE", "cuda")
    dtype: str = os.getenv("LOCATE_ANYTHING_DTYPE", "bfloat16")
    max_new_tokens: int = int(os.getenv("LOCATE_ANYTHING_MAX_NEW_TOKENS", "2048"))
    temperature: float = float(os.getenv("LOCATE_ANYTHING_TEMPERATURE", "0.7"))
    annotated_base_url: str = os.getenv("ANNOTATED_BASE_URL", "http://127.0.0.1:8080/files/annotated")


settings = Settings()
