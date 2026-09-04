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
PUBLIC_READ_TOKEN = os.getenv("PUBLIC_READ_TOKEN")


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


def read_headers() -> dict[str, str]:
    if not PUBLIC_READ_TOKEN:
        return {}
    return {"Authorization": f"Bearer {PUBLIC_READ_TOKEN}"}


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


async def set_started_at(trip_id: str, instant: datetime) -> None:
    conn = await asyncpg.connect(DATABASE_URL)
    try:
        await conn.execute("update trips set started_at = $2 where id = $1", trip_id, instant)
    finally:
        await conn.close()


async def test_latest_location_ignores_points_recorded_before_departure():
    code = "ABCD-1005"
    await seed_activation(code)
    async with httpx.AsyncClient(base_url=API_BASE_URL, timeout=30) as client:
        activation = await activate(client, code)
        headers = {"Authorization": f"Bearer {activation['deviceToken']}"}
        departure = datetime(2026, 8, 25, 6, 0, tzinfo=timezone.utc)
        # Les tests terrain, pris a la maison une semaine avant le depart.
        at_home = point(1, activation["deviceId"], departure - timedelta(days=7))
        on_the_road = point(2, activation["deviceId"], departure + timedelta(hours=3))

        response = await client.post(
            "/locations/batch", headers=headers, json={"points": [at_home, on_the_road]}
        )
        assert response.status_code == 200, response.text

        before_departure = await client.get(f"/trips/{activation['tripId']}/latest-location")
        assert before_departure.status_code == 200, before_departure.text
        assert before_departure.json()["id"] == on_the_road["id"]

        await set_started_at(activation["tripId"], departure)

        after_departure = await client.get(f"/trips/{activation['tripId']}/latest-location")
        assert after_departure.status_code == 200, after_departure.text
        assert after_departure.json()["id"] == on_the_road["id"]


async def test_latest_location_is_null_when_every_point_predates_departure():
    code = "ABCD-1006"
    await seed_activation(code)
    async with httpx.AsyncClient(base_url=API_BASE_URL, timeout=30) as client:
        activation = await activate(client, code)
        headers = {"Authorization": f"Bearer {activation['deviceToken']}"}
        departure = datetime(2026, 8, 25, 6, 0, tzinfo=timezone.utc)
        at_home = [
            point(index, activation["deviceId"], departure - timedelta(days=index))
            for index in range(1, 4)
        ]

        response = await client.post("/locations/batch", headers=headers, json={"points": at_home})
        assert response.status_code == 200, response.text
        await set_started_at(activation["tripId"], departure)

        latest = await client.get(f"/trips/{activation['tripId']}/latest-location")

        assert latest.status_code == 200, latest.text
        assert latest.json() is None


async def test_history_covers_the_whole_range_instead_of_truncating_the_end():
    """Le defaut que le plafond de 10 000 points creait : la fin manquait.

    L'ancienne requete triait par date croissante puis coupait. Passe le
    plafond, ce sont les positions les plus recentes qui disparaissaient --
    sans erreur, avec un statut vert. Le site affichait une derniere position
    figee. C'est la panne muette que ce projet cherche a eliminer.
    """
    code = "ABCD-1010"
    await seed_activation(code)
    async with httpx.AsyncClient(base_url=API_BASE_URL, timeout=60) as client:
        activation = await activate(client, code)
        headers = {"Authorization": f"Bearer {activation['deviceToken']}"}
        depart = datetime(2026, 1, 1, tzinfo=timezone.utc)

        # Deux cents positions etalees sur cent jours, et une cible de vingt.
        points = [
            point(index, activation["deviceId"], depart + timedelta(hours=12 * index))
            for index in range(200)
        ]
        for lot in (points[:100], points[100:]):
            response = await client.post("/locations/batch", headers=headers, json={"points": lot})
            assert response.status_code == 200, response.text

        response = await client.get(
            f"/trips/{activation['tripId']}/locations", params={"limit": 20}
        )
        assert response.status_code == 200, response.text
        rendus = response.json()

        assert len(rendus) <= 20, "l'echantillonnage doit respecter la cible"
        # Le point capital : la derniere position du voyage est presente.
        assert rendus[-1]["recordedAt"] == points[-1]["recordedAt"]
        assert rendus[0]["recordedAt"] == points[0]["recordedAt"]


async def test_history_announces_the_resolution_it_used():
    """Le client doit pouvoir dire ce qu'il montre, pas le deviner."""
    code = "ABCD-1011"
    await seed_activation(code)
    async with httpx.AsyncClient(base_url=API_BASE_URL, timeout=60) as client:
        activation = await activate(client, code)
        headers = {"Authorization": f"Bearer {activation['deviceToken']}"}
        depart = datetime(2026, 3, 1, tzinfo=timezone.utc)
        points = [
            point(index, activation["deviceId"], depart + timedelta(days=index))
            for index in range(40)
        ]
        response = await client.post("/locations/batch", headers=headers, json={"points": points})
        assert response.status_code == 200, response.text

        serre = await client.get(f"/trips/{activation['tripId']}/locations", params={"limit": 5})
        large = await client.get(f"/trips/{activation['tripId']}/locations", params={"limit": 5000})

        assert int(serre.headers["X-Madhi-Resolution-Seconds"]) > int(
            large.headers["X-Madhi-Resolution-Seconds"]
        )
        # Une cible large ne regroupe rien : les quarante positions ressortent.
        assert len(large.json()) == 40


async def test_history_of_a_short_range_loses_nothing():
    """Aujourd'hui et sept jours doivent rendre chaque position, sans exception."""
    code = "ABCD-1012"
    await seed_activation(code)
    async with httpx.AsyncClient(base_url=API_BASE_URL, timeout=60) as client:
        activation = await activate(client, code)
        headers = {"Authorization": f"Bearer {activation['deviceToken']}"}
        depart = datetime(2026, 5, 1, tzinfo=timezone.utc)
        # Une journee a cinq minutes.
        points = [
            point(index, activation["deviceId"], depart + timedelta(minutes=5 * index))
            for index in range(288)
        ]
        response = await client.post("/locations/batch", headers=headers, json={"points": points})
        assert response.status_code == 200, response.text

        response = await client.get(f"/trips/{activation['tripId']}/locations")
        assert response.status_code == 200, response.text
        assert len(response.json()) == 288


async def test_history_hides_very_imprecise_points_without_rejecting_them():
    """Un fix imprecis est utile au diagnostic, mais pas au dessin du trajet."""
    code = f"ABCD-{uuid4().hex[:4].upper()}"
    await seed_activation(code)
    async with httpx.AsyncClient(base_url=API_BASE_URL, timeout=30) as client:
        activation = await activate(client, code)
        headers = {"Authorization": f"Bearer {activation['deviceToken']}"}
        depart = datetime(2026, 6, 1, 8, 0, tzinfo=timezone.utc)
        first = point(1, activation["deviceId"], depart)
        spike = point(2, activation["deviceId"], depart + timedelta(minutes=5))
        spike["latitude"] = 45.0
        spike["longitude"] = 4.0
        spike["accuracyMeters"] = 200.0
        last = point(3, activation["deviceId"], depart + timedelta(minutes=10))

        response = await client.post(
            "/locations/batch", headers=headers, json={"points": [first, spike, last]}
        )
        assert response.status_code == 200, response.text
        assert response.json()["accepted"] == [first["id"], spike["id"], last["id"]]

        history = await client.get(f"/trips/{activation['tripId']}/locations", headers=read_headers())

        assert history.status_code == 200, history.text
        assert [item["id"] for item in history.json()] == [first["id"], last["id"]]

        diagnostics = await client.get(
            f"/trips/{activation['tripId']}/diagnostics/recent-locations",
            headers=read_headers(),
        )

        assert diagnostics.status_code == 200, diagnostics.text
        payload = diagnostics.json()
        assert [item["id"] for item in payload] == [first["id"], spike["id"], last["id"]]
        assert payload[1]["accuracyMeters"] == 200.0
        assert "latitude" not in payload[1]
        assert "longitude" not in payload[1]
