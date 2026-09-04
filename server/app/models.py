from __future__ import annotations

from datetime import datetime
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class ActivationRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")

    activationCode: str
    deviceName: str
    appVersion: str


class ActivationResponse(BaseModel):
    deviceId: str
    deviceToken: str
    tripId: str


class ReverseGeocodeResponse(BaseModel):
    address: str


class LocationPoint(BaseModel):
    model_config = ConfigDict(extra="ignore")

    id: str
    deviceId: str
    latitude: float
    longitude: float
    recordedAt: str
    accuracyMeters: float | None = None
    altitudeMeters: float | None = None
    speedMps: float | None = None
    batteryPercent: int | None = Field(default=None, ge=0, le=100)

    @field_validator("id")
    @classmethod
    def valid_uuid(cls, value: str) -> str:
        UUID(value)
        return value

    @field_validator("latitude")
    @classmethod
    def valid_latitude(cls, value: float) -> float:
        if not -90.0 <= value <= 90.0:
            raise ValueError("invalid_coordinates")
        return value

    @field_validator("longitude")
    @classmethod
    def valid_longitude(cls, value: float) -> float:
        if not -180.0 <= value <= 180.0:
            raise ValueError("invalid_coordinates")
        return value


class LocationBatchRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")

    points: list[Any]


class RejectedPoint(BaseModel):
    id: str
    reason: str


class LocationBatchResponse(BaseModel):
    accepted: list[str]
    duplicates: list[str]
    rejected: list[RejectedPoint]


class LocationResponse(BaseModel):
    id: str
    deviceId: str
    latitude: float
    longitude: float
    recordedAt: str
    receivedAt: str
    accuracyMeters: float | None = None
    altitudeMeters: float | None = None
    speedMps: float | None = None
    batteryPercent: int | None = None


class LocationDiagnosticResponse(BaseModel):
    id: str
    deviceId: str
    recordedAt: str
    receivedAt: str
    accuracyMeters: float | None = None
    altitudeMeters: float | None = None
    speedMps: float | None = None
    batteryPercent: int | None = None


class TripStatusResponse(BaseModel):
    tripId: str
    name: str
    startedAt: str | None
    endedAt: str | None
    totalLocations: int
    latestRecordedAt: str | None
    latestReceivedAt: str | None


def parse_recorded_at(value: str) -> datetime:
    if not isinstance(value, str) or not value.endswith("Z"):
        raise ValueError("invalid_recorded_at")
    parsed = datetime.fromisoformat(value.removesuffix("Z") + "+00:00")
    if parsed.utcoffset() is None:
        raise ValueError("invalid_recorded_at")
    return parsed
