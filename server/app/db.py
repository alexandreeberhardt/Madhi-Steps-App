from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from uuid import UUID, uuid4

import asyncpg

from .config import Settings
from .models import LocationPoint, parse_recorded_at
from .security import hash_secret, new_device_token


MAX_VISIBLE_ACCURACY_METERS = 165


@dataclass(frozen=True)
class DeviceAuth:
    device_id: UUID
    trip_id: UUID


async def create_pool(database_url: str) -> asyncpg.Pool:
    return await asyncpg.create_pool(dsn=database_url, min_size=1, max_size=5)


async def seed_configured_trip_and_activation_code(pool: asyncpg.Pool, settings: Settings) -> None:
    activation_hash = hash_secret(settings.initial_activation_code, settings.activation_code_hash_secret)
    async with pool.acquire() as conn:
        async with conn.transaction():
            await conn.execute(
                """
                insert into trips (id, name)
                values ($1, $2)
                on conflict (id) do update set name = excluded.name
                """,
                UUID(settings.initial_trip_id),
                settings.initial_trip_name,
            )
            # Le code peut etre rejoue au demarrage tant qu'il n'a pas ete utilise.
            await conn.execute(
                """
                insert into activation_codes (code_hash, trip_id, expires_at)
                values ($1, $2, now() + ($3::text || ' minutes')::interval)
                on conflict (code_hash) do update
                    set trip_id = excluded.trip_id,
                        expires_at = greatest(activation_codes.expires_at, excluded.expires_at)
                    where activation_codes.used_at is null
                """,
                activation_hash,
                UUID(settings.initial_trip_id),
                str(settings.activation_code_ttl_minutes),
            )


async def activate_device(
    pool: asyncpg.Pool,
    settings: Settings,
    activation_code: str,
    device_name: str,
    app_version: str,
) -> tuple[UUID, str, UUID] | None:
    code_hash = hash_secret(activation_code, settings.activation_code_hash_secret)
    device_id = uuid4()
    token = new_device_token()
    token_hash = hash_secret(token, settings.device_token_hash_secret)

    async with pool.acquire() as conn:
        async with conn.transaction():
            row = await conn.fetchrow(
                """
                update activation_codes
                   set used_at = now()
                 where code_hash = $1
                   and used_at is null
                   and expires_at > now()
                 returning trip_id
                """,
                code_hash,
            )
            if row is None:
                return None
            trip_id = row["trip_id"]
            await conn.execute(
                """
                insert into devices (id, trip_id, name, token_hash, app_version)
                values ($1, $2, $3, $4, $5)
                """,
                device_id,
                trip_id,
                device_name[:160],
                token_hash,
                app_version[:80],
            )
            return device_id, token, trip_id


async def authenticate_device(pool: asyncpg.Pool, settings: Settings, token: str) -> DeviceAuth | None:
    token_hash = hash_secret(token, settings.device_token_hash_secret)
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            """
            update devices
               set last_seen_at = now()
             where token_hash = $1
               and revoked_at is null
             returning id, trip_id
            """,
            token_hash,
        )
    if row is None:
        return None
    return DeviceAuth(device_id=row["id"], trip_id=row["trip_id"])


async def ingest_locations(
    pool: asyncpg.Pool,
    device: DeviceAuth,
    points: list[LocationPoint],
) -> tuple[list[str], list[str]]:
    accepted: list[str] = []
    duplicates: list[str] = []
    async with pool.acquire() as conn:
        async with conn.transaction():
            for point in points:
                recorded_at = parse_recorded_at(point.recordedAt)
                inserted = await conn.fetchval(
                    """
                    insert into locations (
                        id, trip_id, device_id, latitude, longitude, accuracy_meters,
                        altitude_meters, speed_mps, battery_percent, recorded_at
                    )
                    values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
                    on conflict (id) do nothing
                    returning id
                    """,
                    UUID(point.id),
                    device.trip_id,
                    device.device_id,
                    point.latitude,
                    point.longitude,
                    point.accuracyMeters,
                    point.altitudeMeters,
                    point.speedMps,
                    point.batteryPercent,
                    recorded_at,
                )
                if inserted is None:
                    duplicates.append(point.id)
                else:
                    accepted.append(point.id)
    return accepted, duplicates


async def known_location_ids(pool: asyncpg.Pool, location_ids: list[UUID]) -> set[str]:
    if not location_ids:
        return set()
    async with pool.acquire() as conn:
        rows = await conn.fetch("select id from locations where id = any($1::uuid[])", location_ids)
    return {str(row["id"]) for row in rows}


async def latest_location(pool: asyncpg.Pool, trip_id: UUID) -> asyncpg.Record | None:
    # Le trip contient aussi les points de pre-validation et des tests terrain,
    # pris a la maison avant le depart. Ils ne sont pas supprimes, ils sont
    # anterieurs a started_at : sans ce filtre, le site familial afficherait le
    # domicile comme derniere position connue.
    async with pool.acquire() as conn:
        return await conn.fetchrow(
            """
            select l.*
              from locations l
              join trips t on t.id = l.trip_id
             where l.trip_id = $1
               and (t.started_at is null or l.recorded_at >= t.started_at)
             order by l.recorded_at desc, l.received_at desc
             limit 1
            """,
            trip_id,
        )


async def history_bounds(
    pool: asyncpg.Pool,
    trip_id: UUID,
    from_instant: datetime | None,
    to_instant: datetime | None,
) -> asyncpg.Record | None:
    """Premier et dernier instant de la fenetre demandee, et son effectif.

    Sert a choisir le pas d'echantillonnage avant de lire les points. La
    requete ne touche que l'index (trip_id, recorded_at).
    """
    async with pool.acquire() as conn:
        return await conn.fetchrow(
            """
            select min(recorded_at) as first_at,
                   max(recorded_at) as last_at,
                   count(*)         as total
              from locations
             where trip_id = $1
               and ($2::timestamptz is null or recorded_at >= $2)
               and ($3::timestamptz is null or recorded_at <= $3)
               and (accuracy_meters is null or accuracy_meters <= $4)
            """,
            trip_id,
            from_instant,
            to_instant,
            MAX_VISIBLE_ACCURACY_METERS,
        )


async def location_history(
    pool: asyncpg.Pool,
    trip_id: UUID,
    from_instant: datetime | None,
    to_instant: datetime | None,
    bucket_seconds: int,
) -> list[asyncpg.Record]:
    """L'historique affichable, une position par tranche de `bucket_seconds`.

    Il n'y a volontairement **aucun LIMIT**. Un plafond en nombre de points
    coupe la fin du tableau, donc les positions les plus recentes, et sans
    erreur : le site afficherait une derniere position figee avec un statut
    vert. C'est la panne muette que ce projet cherche a eliminer. Le pas de
    temps borne la reponse par construction, et il la borne en couvrant toute
    la periode au lieu d'en amputer la fin.

    `distinct on` retient la premiere ligne de chaque tranche selon le `order
    by` -- donc la plus ancienne, a egalite l'identifiant le plus petit, ce qui
    rend le resultat stable d'un appel a l'autre.

    Les points tres imprecis restent en base, mais ils ne dessinent pas le
    trajet familial. Un fix a plusieurs centaines de metres ou quelques
    kilometres de precision cree deux longs traits parasites: l'aller vers le
    mauvais point, puis le retour au trajet reel.
    """
    async with pool.acquire() as conn:
        return await conn.fetch(
            """
            with fenetre as (
                select *
                  from locations
                 where trip_id = $1
                   and ($2::timestamptz is null or recorded_at >= $2)
                   and ($3::timestamptz is null or recorded_at <= $3)
                   and (accuracy_meters is null or accuracy_meters <= $5)
            ),
            echantillon as (
                select distinct on (floor(extract(epoch from recorded_at) / $4::bigint))
                       *
                  from fenetre
                 order by floor(extract(epoch from recorded_at) / $4::bigint),
                          recorded_at asc,
                          id asc
            )
            select * from echantillon order by recorded_at asc, id asc
            """,
            trip_id,
            from_instant,
            to_instant,
            bucket_seconds,
            MAX_VISIBLE_ACCURACY_METERS,
        )


async def trip_status(pool: asyncpg.Pool, trip_id: UUID) -> asyncpg.Record | None:
    async with pool.acquire() as conn:
        return await conn.fetchrow(
            """
            select
                t.id,
                t.name,
                t.started_at,
                t.ended_at,
                count(l.id)::int as total_locations,
                max(l.recorded_at) as latest_recorded_at,
                max(l.received_at) as latest_received_at
            from trips t
            left join locations l on l.trip_id = t.id
            where t.id = $1
            group by t.id
            """,
            trip_id,
        )


def utc_iso(value: datetime | None) -> str | None:
    if value is None:
        return None
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
