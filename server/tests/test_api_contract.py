from __future__ import annotations

import os
from datetime import datetime, timedelta, timezone
from uuid import uuid4

import asyncpg
import httpx
import pytest

from app.security import hash_secret


API_BASE_URL = os.getenv("MADHI_TEST_BASE_URL", "http://127.0.0.1:8080/api/v1")
DATABASE_URL = os.getenv(
    "TEST_DATABASE_URL",
    "postgresql://madhi:madhi_dev_password@127.0.0.1:5432/madhi_tracker",
)
ACTIVATION_SECRET = os.getenv("ACTIVATION_CODE_HASH_SECRET", "dev-secret-change-me")


pytestmark = pytest.mark.asyncio

if os.getenv("MADHI_TEST_INTEGRATION") != "1":
    pytestmark = [
        pytest.mark.asyncio,
        pytest.mark.skip(reason="integration tests need the Compose API and PostgreSQL services"),
    ]


async def seed_activation(code: str) -> str:
    trip_id = str(uuid4())
    conn = await asyncpg.connect(DATABASE_URL)
    try:
        await conn.execute("insert into trips (id, name) values ($1, $2)", trip_id, "Test trip")
        await conn.execute(
            """
            insert into activation_codes (code_hash, trip_id, expires_at)
            values ($1, $2, now() + interval '15 minutes')
            """,
            hash_secret(code, ACTIVATION_SECRET),
            trip_id,
        )
    finally:
        await conn.close()
    return trip_id


async def activate(client: httpx.AsyncClient, code: str) -> dict:
    response = await client.post(
        "/devices/activate",
        json={"activationCode": code, "deviceName": "pytest", "appVersion": "1.0.0"},
    )
    assert response.status_code == 200, response.text
    return response.json()


def point(index: int, device_id: str, recorded_at: datetime | None = None) -> dict:
    instant = recorded_at or datetime(2026, 8, 18, 14, 0, tzinfo=timezone.utc) + timedelta(seconds=index)
    return {
        "id": str(uuid4()),
        "deviceId": device_id,
        "latitude": 48.0 + index / 100000,
        "longitude": 2.0 + index / 100000,
        "recordedAt": instant.isoformat(timespec="seconds").replace("+00:00", "Z"),
        "accuracyMeters": 12.4,
        "batteryPercent": 62,
    }


async def count_locations() -> int:
    conn = await asyncpg.connect(DATABASE_URL)
    try:
        return await conn.fetchval("select count(*) from locations")
    finally:
        await conn.close()


async def test_contract_lost_response_replay_returns_only_duplicates():
    code = "ABCD-1001"
    await seed_activation(code)
    async with httpx.AsyncClient(base_url=API_BASE_URL, timeout=30) as client:
        activation = await activate(client, code)
        headers = {"Authorization": f"Bearer {activation['deviceToken']}"}
        points = [point(i, activation["deviceId"]) for i in range(3)]

        first = await client.post("/locations/batch", headers=headers, json={"points": points})
        assert first.status_code == 200, first.text
        assert first.json() == {
            "accepted": [item["id"] for item in points],
            "duplicates": [],
            "rejected": [],
        }

        before = await count_locations()
        replay = await client.post("/locations/batch", headers=headers, json={"points": points})
        after = await count_locations()

        assert replay.status_code == 200, replay.text
        assert replay.json() == {
            "accepted": [],
            "duplicates": [item["id"] for item in points],
            "rejected": [],
        }
        assert after == before


async def test_10000_points_import_and_reinject_without_duplicates():
    code = "ABCD-1002"
    await seed_activation(code)
    async with httpx.AsyncClient(base_url=API_BASE_URL, timeout=60) as client:
        activation = await activate(client, code)
        headers = {"Authorization": f"Bearer {activation['deviceToken']}"}
        points = [point(i, activation["deviceId"]) for i in range(10_000)]

        for offset in range(0, len(points), 200):
            chunk = points[offset : offset + 200]
            response = await client.post("/locations/batch", headers=headers, json={"points": chunk})
            assert response.status_code == 200, response.text
            assert response.json()["accepted"] == [item["id"] for item in chunk]

        for offset in range(0, len(points), 200):
            chunk = points[offset : offset + 200]
            response = await client.post("/locations/batch", headers=headers, json={"points": chunk})
            assert response.status_code == 200, response.text
            assert response.json()["duplicates"] == [item["id"] for item in chunk]


async def test_latest_uses_recorded_at_and_history_filters_by_interval():
    code = "ABCD-1003"
    await seed_activation(code)
    async with httpx.AsyncClient(base_url=API_BASE_URL, timeout=30) as client:
        activation = await activate(client, code)
        headers = {"Authorization": f"Bearer {activation['deviceToken']}"}
        base = datetime(2026, 8, 18, 12, 0, tzinfo=timezone.utc)
        older = point(1, activation["deviceId"], base)
        newest = point(2, activation["deviceId"], base + timedelta(hours=2))
        middle = point(3, activation["deviceId"], base + timedelta(hours=1))

        response = await client.post("/locations/batch", headers=headers, json={"points": [newest, older, middle]})
        assert response.status_code == 200, response.text

        latest = await client.get(f"/trips/{activation['tripId']}/latest-location")
        assert latest.status_code == 200, latest.text
        assert latest.json()["id"] == newest["id"]

        history = await client.get(
            f"/trips/{activation['tripId']}/locations",
            params={
                "from": middle["recordedAt"],
                "to": newest["recordedAt"],
            },
        )
        assert history.status_code == 200, history.text
        assert [item["id"] for item in history.json()] == [middle["id"], newest["id"]]


async def test_batch_over_max_returns_413():
    code = "ABCD-1004"
    await seed_activation(code)
    async with httpx.AsyncClient(base_url=API_BASE_URL, timeout=30) as client:
        activation = await activate(client, code)
        headers = {"Authorization": f"Bearer {activation['deviceToken']}"}
        points = [point(i, activation["deviceId"]) for i in range(201)]

        response = await client.post("/locations/batch", headers=headers, json={"points": points})

        assert response.status_code == 413
        assert response.json()["error"] == "batch_too_large"
