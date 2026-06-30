import logging


NOISY_MODEL_LOGGERS = (
    "transformers",
    "transformers_modules",
    "huggingface_hub",
    "tokenizers",
)


def configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )
    for logger_name in NOISY_MODEL_LOGGERS:
        logging.getLogger(logger_name).setLevel(logging.WARNING)
