from concurrent.futures import ThreadPoolExecutor
from uuid import uuid4

from fastapi import FastAPI

from common.errors import model_error
from common.logging_config import configure_logging
from common.schemas import ReconstructionJobRequest, ReconstructionJobResponse
from config import settings
from job_store import JobStore
from worker import run_reconstruction

configure_logging()

app = FastAPI(title="LingBot-Map Service", version="0.1.0")
store = JobStore(settings.storage_dir / "jobs.json")
executor = ThreadPoolExecutor(max_workers=1)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.get("/ready")
def ready() -> dict:
    return {"ready": True}


@app.post("/v1/reconstruction/jobs", response_model=ReconstructionJobResponse)
def create_job(request: ReconstructionJobRequest) -> ReconstructionJobResponse:
    job_id = f"py_{request.requestId}_{uuid4().hex[:8]}"
    job = ReconstructionJobResponse(jobId=job_id, status="QUEUED", progress=0, message="queued")
    store.save(job)
    executor.submit(run_reconstruction, job_id, request, store)
    return job


@app.get("/v1/reconstruction/jobs/{job_id}", response_model=ReconstructionJobResponse)
def get_job(job_id: str) -> ReconstructionJobResponse:
    job = store.get(job_id)
    if job is None:
        raise model_error("建图任务不存在", status_code=404, code="JOB_NOT_FOUND")
    return job


@app.get("/v1/reconstruction/jobs/{job_id}/artifacts")
def get_artifacts(job_id: str) -> dict:
    job = get_job(job_id)
    return {"jobId": job.jobId, "artifacts": job.artifacts}


@app.delete("/v1/reconstruction/jobs/{job_id}", response_model=ReconstructionJobResponse)
def cancel_job(job_id: str) -> ReconstructionJobResponse:
    job = store.get(job_id)
    if job is None:
        raise model_error("建图任务不存在", status_code=404, code="JOB_NOT_FOUND")
    cancelled = job.model_copy(update={"status": "CANCELLED", "message": "cancelled"})
    store.save(cancelled)
    return cancelled
