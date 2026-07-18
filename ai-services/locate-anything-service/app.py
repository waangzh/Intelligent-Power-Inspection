import logging
import os, sys
import time
from contextlib import asynccontextmanager

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from common.errors import model_error
from common.storage import ensure_dir
from common.logging_config import configure_logging
from common.schemas import LocateCheckpointRequest, LocateCheckpointResponse
from config import settings
from model_runner import runner

configure_logging()
logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    runner.load_model()
    yield


app = FastAPI(title="LocateAnything Service", version="0.1.0", lifespan=lifespan)
annotated_dir = ensure_dir(settings.annotated_output_dir)
app.mount("/files/annotated", StaticFiles(directory=annotated_dir), name="annotated")


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.get("/ready")
def ready() -> dict:
    return {"ready": runner.ready, "modelVersion": runner.model_version}


@app.post("/v1/locate/checkpoint", response_model=LocateCheckpointResponse)
def locate_checkpoint(request: LocateCheckpointRequest) -> LocateCheckpointResponse:
    started_at = time.perf_counter()
    logger.info(
        "Locate request start requestId=%s imageUrl=%s imageSize=%sx%s detections=%s generationMode=%s",
        request.requestId,
        request.imageUrl,
        request.imageWidth,
        request.imageHeight,
        len(request.detections),
        request.generationMode,
    )
    try:
        findings, result_image_url = runner.locate_checkpoint(request)
        elapsed_ms = round((time.perf_counter() - started_at) * 1000)
        response = LocateCheckpointResponse(
            modelVersion=runner.model_version,
            resultImageUrl=result_image_url,
            findings=findings,
            warnings=runner.warnings,
        )
        logger.info(
            "Locate request done requestId=%s status=%s findings=%s warnings=%s elapsedMs=%s",
            request.requestId,
            response.status,
            len(response.findings),
            len(response.warnings),
            elapsed_ms,
        )
        return response
    except Exception as exc:
        elapsed_ms = round((time.perf_counter() - started_at) * 1000)
        logger.exception("Locate request failed requestId=%s elapsedMs=%s", request.requestId, elapsed_ms)
        raise model_error(str(exc)) from exc
