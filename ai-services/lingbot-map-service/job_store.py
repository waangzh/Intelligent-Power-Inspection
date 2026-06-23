import json
from pathlib import Path
from threading import Lock

from common.schemas import ReconstructionJobResponse


class JobStore:
    def __init__(self, path: Path) -> None:
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = Lock()

    def save(self, job: ReconstructionJobResponse) -> ReconstructionJobResponse:
        with self._lock:
            data = self._load()
            data[job.jobId] = job.model_dump()
            self.path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        return job

    def get(self, job_id: str) -> ReconstructionJobResponse | None:
        with self._lock:
            raw = self._load().get(job_id)
        return ReconstructionJobResponse(**raw) if raw else None

    def delete(self, job_id: str) -> None:
        with self._lock:
            data = self._load()
            data.pop(job_id, None)
            self.path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    def _load(self) -> dict:
        if not self.path.exists():
            return {}
        return json.loads(self.path.read_text(encoding="utf-8") or "{}")
