"""Provision the export-only role. Run once with the database owner's credentials."""
from __future__ import annotations

import asyncio
import os
import secrets
from urllib.parse import quote, urlsplit, urlunsplit
from uuid import UUID

import asyncpg


def publication_views(trip_id: UUID) -> str:
    # UUID validation makes the sole DDL substitution a fixed-format value.
    return f"""
        create schema if not exists publication;
        revoke all on schema publication from public;
        create or replace view publication.trip with (security_barrier = true) as
        select name,
               case when started_at <= now() - interval '168 hours'
                    then started_at end as started_at,
               case when started_at <= now() - interval '168 hours'
                          and ended_at <= now() - interval '168 hours'
                    then ended_at end as ended_at
          from public.trips where id = '{trip_id}'::uuid;
        create or replace view publication.locations with (security_barrier = true) as
        select l.latitude, l.longitude, l.recorded_at
          from public.locations l join public.trips t on t.id = l.trip_id
         where t.id = '{trip_id}'::uuid
           and t.started_at is not null
           and l.recorded_at >= t.started_at
           and l.recorded_at <= now() - interval '168 hours'
           and (t.ended_at is null or l.recorded_at <= t.ended_at)
           and (l.accuracy_meters is null or l.accuracy_meters <= 165);
        revoke all on all tables in schema publication from public;
        grant usage on schema publication to madhi_public_export;
        grant select on publication.trip, publication.locations to madhi_public_export;
    """


async def provision() -> None:
    password = secrets.token_hex(32)
    trip_id = UUID(os.environ['INITIAL_TRIP_ID'])
    url = os.environ['DATABASE_URL']
    conn = await asyncpg.connect(url)
    try:
        async with conn.transaction():
            if not await conn.fetchval("select 1 from pg_roles where rolname = 'madhi_public_export'"):
                await conn.execute('create role madhi_public_export')
            await conn.execute(
                "alter role madhi_public_export with login nosuperuser nocreatedb "
                "nocreaterole noinherit noreplication nobypassrls password '" + password + "'"
            )
            await conn.execute('alter role madhi_public_export set default_transaction_read_only = on')
            await conn.execute(publication_views(trip_id))
    finally:
        await conn.close()
    parsed = urlsplit(url)
    host = parsed.hostname or 'postgres'
    if ':' in host:
        host = '[' + host + ']'
    authority = f'madhi_public_export:{quote(password)}@{host}:{parsed.port or 5432}'
    # Captured by provision.py; never sent to the terminal or command arguments.
    print(urlunsplit((parsed.scheme, authority, parsed.path, parsed.query, '')))


if __name__ == '__main__':
    asyncio.run(provision())
