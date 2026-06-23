from common.schemas import ReconstructionJobRequest, ReconstructionJobResponse
from job_store import JobStore
from runner import runner


def run_reconstruction(job_id: str, request: ReconstructionJobRequest, store: JobStore) -> None:
    running = ReconstructionJobResponse(jobId=job_id, status="RUNNING", progress=10, message="started")
    store.save(running)
    try:
        result = runner.run(job_id, request)
        store.save(result)
    except Exception as exc:
        store.save(
            ReconstructionJobResponse(
                jobId=job_id,
                status="FAILED",
                progress=0,
                message="failed",
                errorMessage=str(exc),
            )
        )
