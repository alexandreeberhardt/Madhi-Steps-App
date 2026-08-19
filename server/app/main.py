from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import Annotated
from uuid import UUID

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import ValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException

from .config import Settings, load_settings
from .db import (
    authenticate_device,
    activate_device,
    create_pool,
    ingest_locations,
    known_location_ids,
    latest_location,
    location_history,
    seed_configured_trip_and_activation_code,
    trip_status,
    utc_iso,
)
from .logging_config import configure_logging
from .models import (
    ActivationRequest,
    ActivationResponse,
    LocationBatchRequest,
    LocationBatchResponse,
    LocationPoint,
    LocationResponse,
    RejectedPoint,
    TripStatusResponse,
    parse_recorded_at,
)
from .rate_limit import InMemoryRateLimiter
from .security import activation_code_malformed, parse_bearer


settings = load_settings()
configure_logging(settings.log_level)
logger = logging.getLogger("madhi.server")
rate_limiter = InMemoryRateLimiter(settings.rate_limit_per_minute)


@asynccontextmanager
async def lifespan(app: FastAPI):
    pool = await create_pool(settings.database_url)
    app.state.pool = pool
    await seed_configured_trip_and_activation_code(pool, settings)
    logger.info(
        "server_started",
        extra={"path": "/api/v1", "method": "lifespan"},
    )
    try:
        yield
    finally:
        await pool.close()


app = FastAPI(title="Madhi Tracker API", version="1.0.0", lifespan=lifespan)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(_: Request, __: RequestValidationError) -> JSONResponse:
    return JSONResponse(status_code=400, content={"error": "invalid_payload"})


@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(_: Request, exc: StarletteHTTPException) -> JSONResponse:
    detail = exc.detail
    content = detail if isinstance(detail, dict) else {"error": str(detail)}
    return JSONResponse(status_code=exc.status_code, content=content, headers=exc.headers)


@app.exception_handler(ValidationError)
async def pydantic_validation_exception_handler(_: Request, __: ValidationError) -> JSONResponse:
    return JSONResponse(status_code=400, content={"error": "invalid_payload"})


@app.middleware("http")
async def request_guards(request: Request, call_next):
    rate_limiter.check(request)
    content_length = request.headers.get("Content-Length")
    if content_length and content_length.isdigit() and int(content_length) > settings.max_request_bytes:
        return JSONResponse(status_code=413, content={"error": "request_too_large"})
    return await call_next(request)


@app.get("/health")
async def health() -> dict[str, str]:
    async with app.state.pool.acquire() as conn:
        await conn.fetchval("select 1")
    return {"status": "ok"}


@app.post("/api/v1/devices/activate", response_model=ActivationResponse)
async def activate(request: ActivationRequest, raw_request: Request) -> ActivationResponse:
    code = request.activationCode.strip()
    if activation_code_malformed(code):
        raise HTTPException(status_code=400, detail={"error": "invalid_activation_code"})

    activation = await activate_device(
        raw_request.app.state.pool,
        settings,
        code,
        request.deviceName,
        request.appVersion,
    )
    if activation is None:
        raise HTTPException(status_code=410, detail={"error": "expired_or_unknown_code"})

    device_id, token, trip_id = activation
    logger.info("device_activated", extra={"device_id": str(device_id), "trip_id": str(trip_id)})
    return ActivationResponse(deviceId=str(device_id), deviceToken=token, tripId=str(trip_id))


@app.post("/api/v1/locations/batch", response_model=LocationBatchResponse)
async def locations_batch(
    request: LocationBatchRequest,
    raw_request: Request,
    token: Annotated[str, Depends(parse_bearer)],
) -> LocationBatchResponse:
    device = await authenticate_device(raw_request.app.state.pool, settings, token)
    if device is None:
        raise HTTPException(status_code=401, detail={"error": "unauthorized"})

    if len(request.points) > settings.max_batch_points:
        raise HTTPException(
            status_code=413,
            detail={"error": "batch_too_large", "maxPoints": settings.max_batch_points},
        )

    valid_points: list[LocationPoint] = []
    rejected: list[RejectedPoint] = []
    known_ids = await known_location_ids(raw_request.app.state.pool, _extract_uuid_ids(request.points))
    duplicates: list[str] = []
    for raw_point in request.points:
        point_id = str(raw_point.get("id", "?")) if isinstance(raw_point, dict) else "?"
        if point_id in known_ids:
            duplicates.append(point_id)
            continue
        try:
            point = LocationPoint.model_validate(raw_point)
            parse_recorded_at(point.recordedAt)
            valid_points.append(point)
        except Exception as exc:  # Les erreurs point par point ne doivent pas faire perdre tout le lot.
            rejected.append(RejectedPoint(id=point_id, reason=_point_rejection_reason(exc)))

    accepted, ingest_duplicates = await ingest_locations(raw_request.app.state.pool, device, valid_points)
    duplicates.extend(ingest_duplicates)
    logger.info(
        "locations_batch_ingested",
        extra={"device_id": str(device.device_id), "trip_id": str(device.trip_id)},
    )
    return LocationBatchResponse(accepted=accepted, duplicates=duplicates, rejected=rejected)


@app.get("/api/v1/trips/{trip_id}/latest-location", response_model=LocationResponse | None)
async def get_latest_location(
    trip_id: UUID,
    raw_request: Request,
    authorization: Annotated[str | None, Header()] = None,
):
    enforce_read_auth(authorization)
    row = await latest_location(raw_request.app.state.pool, trip_id)
    if row is None:
        return None
    return row_to_location(row)


@app.get("/api/v1/trips/{trip_id}/locations", response_model=list[LocationResponse])
async def get_locations(
    trip_id: UUID,
    raw_request: Request,
    authorization: Annotated[str | None, Header()] = None,
    from_: Annotated[str | None, Query(alias="from")] = None,
    to: str | None = None,
    limit: int = 10000,
):
    enforce_read_auth(authorization)
    if limit < 1 or limit > 10000:
        raise HTTPException(status_code=400, detail={"error": "invalid_limit"})
    try:
        from_instant = parse_recorded_at(from_) if from_ else None
        to_instant = parse_recorded_at(to) if to else None
    except ValueError:
        raise HTTPException(status_code=400, detail={"error": "invalid_time_range"}) from None
    rows = await location_history(raw_request.app.state.pool, trip_id, from_instant, to_instant, limit)
    return [row_to_location(row) for row in rows]


@app.get("/api/v1/trips/{trip_id}/status", response_model=TripStatusResponse)
async def get_trip_status(
    trip_id: UUID,
    raw_request: Request,
    authorization: Annotated[str | None, Header()] = None,
) -> TripStatusResponse:
    enforce_read_auth(authorization)
    row = await trip_status(raw_request.app.state.pool, trip_id)
    if row is None:
        raise HTTPException(status_code=404, detail={"error": "unknown_trip"})
    return TripStatusResponse(
        tripId=str(row["id"]),
        name=row["name"],
        startedAt=utc_iso(row["started_at"]),
        endedAt=utc_iso(row["ended_at"]),
        totalLocations=row["total_locations"],
        latestRecordedAt=utc_iso(row["latest_recorded_at"]),
        latestReceivedAt=utc_iso(row["latest_received_at"]),
    )


def enforce_read_auth(authorization: str | None) -> None:
    if not settings.public_read_token and not settings.production:
        return
    expected = f"Bearer {settings.public_read_token}"
    if authorization != expected:
        raise HTTPException(status_code=403, detail={"error": "forbidden"})


def row_to_location(row) -> LocationResponse:
    return LocationResponse(
        id=str(row["id"]),
        deviceId=str(row["device_id"]),
        latitude=float(row["latitude"]),
        longitude=float(row["longitude"]),
        accuracyMeters=float(row["accuracy_meters"]) if row["accuracy_meters"] is not None else None,
        altitudeMeters=float(row["altitude_meters"]) if row["altitude_meters"] is not None else None,
        speedMps=float(row["speed_mps"]) if row["speed_mps"] is not None else None,
        batteryPercent=row["battery_percent"],
        recordedAt=utc_iso(row["recorded_at"]),
        receivedAt=utc_iso(row["received_at"]),
    )


def _point_rejection_reason(exc: Exception) -> str:
    text = str(exc)
    if "latitude" in text or "longitude" in text or "invalid_coordinates" in text:
        return "invalid_coordinates"
    if "recorded" in text:
        return "invalid_recorded_at"
    if "Field required" in text:
        return "missing_field"
    return "invalid_point"


def _extract_uuid_ids(raw_points: list[dict]) -> list[UUID]:
    ids: list[UUID] = []
    for raw_point in raw_points:
        if not isinstance(raw_point, dict):
            continue
        try:
            ids.append(UUID(str(raw_point.get("id"))))
        except (TypeError, ValueError):
            continue
    return ids
