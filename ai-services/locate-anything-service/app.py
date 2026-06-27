import os, sys
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from fastapi import FastAPI

from common.errors import model_error
from common.logging_config import configure_logging
from common.schemas import LocateCheckpointRequest, LocateCheckpointResponse
from model_runner import runner

configure_logging()

app = FastAPI(title="LocateAnything Service", version="0.1.0")


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.get("/ready")
def ready() -> dict:
    return {"ready": runner.ready, "modelVersion": runner.model_version}


@app.post("/v1/locate/checkpoint", response_model=LocateCheckpointResponse)
def locate_checkpoint(request: LocateCheckpointRequest) -> LocateCheckpointResponse:
    try:
      findings = runner.locate_checkpoint(request)
      return LocateCheckpointResponse(modelVersion=runner.model_version, findings=findings)
    except Exception as exc:
      raise model_error(str(exc)) from exc
