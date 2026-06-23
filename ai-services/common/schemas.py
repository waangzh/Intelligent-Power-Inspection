from typing import Any, Literal

from pydantic import BaseModel, Field


class DetectionPrompt(BaseModel):
    type: str
    prompt: str | None = None
    threshold: float | None = 0.75


class LocateCheckpointRequest(BaseModel):
    requestId: str
    imageUrl: str
    imageWidth: int | None = None
    imageHeight: int | None = None
    detections: list[DetectionPrompt] = Field(default_factory=list)
    generationMode: Literal["fast", "slow", "hybrid"] = "hybrid"


class LocateFinding(BaseModel):
    type: str
    prompt: str | None = None
    label: str = "abnormal"
    score: float | None = None
    outputType: Literal["box", "point", "none", "mixed"] = "box"
    normalizedBox: list[int] | None = None
    pixelBox: list[int] | None = None
    point: list[int] | None = None
    imageUrl: str | None = None
    rawAnswer: str | None = None


class LocateCheckpointResponse(BaseModel):
    provider: str = "locate-anything"
    modelVersion: str = "mock-locate-anything"
    status: Literal["SUCCEEDED", "FAILED"] = "SUCCEEDED"
    findings: list[LocateFinding] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)


class ReconstructionJobRequest(BaseModel):
    requestId: str
    siteId: str | None = None
    inputKind: Literal["video", "image_sequence"] = "video"
    videoUrl: str | None = None
    imageFolderUrl: str | None = None
    mode: Literal["streaming", "windowed"] = "windowed"
    fps: int | None = 10
    stride: int | None = 1
    firstK: int | None = None
    keyframeInterval: int | None = 5
    windowSize: int | None = 16
    outputProfile: Literal["preview", "viewer-ready", "rendered-video", "predictions"] = "preview"
    maskSky: bool = False


class ReconstructionJobResponse(BaseModel):
    jobId: str
    status: Literal["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"] = "QUEUED"
    progress: int = 0
    message: str | None = None
    frameCount: int | None = None
    pointCount: int | None = None
    mapId: str | None = None
    artifacts: dict[str, Any] = Field(default_factory=dict)
    warnings: list[str] = Field(default_factory=list)
    errorMessage: str | None = None
