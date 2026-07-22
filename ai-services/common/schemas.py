from typing import Any, Literal

from pydantic import BaseModel, Field


class DetectionPrompt(BaseModel):
    itemId: str | None = None
    type: str
    displayLabel: str | None = None
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
    itemId: str | None = None
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
    resultImageUrl: str | None = None
    findings: list[LocateFinding] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
