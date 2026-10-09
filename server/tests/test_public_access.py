"""Run only against a disposable PostgreSQL database."""
from datetime import timedelta
import os
from pathlib import Path
from uuid import uuid4

import asyncpg
import pytest

from app.public_access import publication_views
from app.public_export import make_snapshot

pytestmark = pytest.mark.skipif(
    not os.getenv('PUBLIC_EXPORT_TEST_DATABASE_URL'), reason='needs disposable PostgreSQL',
)


@pytest.mark.asyncio
async def test_database_views_enforce_delay_and_role_cannot_read_private_tables():
    conn = await asyncpg.connect(os.environ['PUBLIC_EXPORT_TEST_DATABASE_URL'])
    try:
        async with conn.transaction():
            await conn.execute((Path(__file__).parents[1] / 'migrations/001_initial_schema.sql').read_text())
            if not await conn.fetchval("select 1 from pg_roles where rolname='madhi_public_export'"):
                await conn.execute('create role madhi_public_export nosuperuser noinherit')
            trip_id, device_id = uuid4(), uuid4()
            now = await conn.fetchval('select transaction_timestamp()')
            cutoff = now - timedelta(hours=168)
            start = cutoff - timedelta(days=2)
            await conn.execute('insert into trips(id,name,started_at,ended_at) values($1,$2,$3,$4)',
                               trip_id, 'Public test', start, now)
            await conn.execute("insert into devices(id,trip_id,token_hash,name) values($1,$2,$3,'test')",
                               device_id, trip_id, uuid4().hex)
            for recorded, accuracy in [
                (start - timedelta(microseconds=1), 10), (start, 10),
                (cutoff, 10), (cutoff + timedelta(microseconds=1), 10),
                (now, 10), (cutoff - timedelta(hours=1), 1000),
            ]:
                await conn.execute(
                    'insert into locations(id,trip_id,device_id,latitude,longitude,recorded_at,accuracy_meters) '
                    'values($1,$2,$3,45,4,$4,$5)', uuid4(), trip_id, device_id, recorded, accuracy,
                )
            await conn.execute(publication_views(trip_id))
            await conn.execute('set local role madhi_public_export')
            trip = await conn.fetchrow('select * from publication.trip')
            rows = await conn.fetch('select * from publication.locations')
            assert len(rows) == 2
            assert trip['ended_at'] is None
            assert set(rows[0].keys()) == {'latitude', 'longitude', 'recorded_at'}
            assert make_snapshot(trip, rows, now)['snapshot.json']['totalPoints'] == 2
            for query in [
                'select * from public.locations', 'select * from public.devices',
                'select * from public.trips', 'select * from public.activation_codes',
                "update public.trips set name='changed'",
            ]:
                with pytest.raises(asyncpg.InsufficientPrivilegeError):
                    async with conn.transaction():
                        await conn.execute(query)
            assert not await conn.fetchval(
                "select has_table_privilege(current_user, 'publication.locations', 'INSERT,UPDATE,DELETE')"
            )
            with pytest.raises((asyncpg.InsufficientPrivilegeError, asyncpg.ObjectNotInPrerequisiteStateError)):
                async with conn.transaction():
                    await conn.execute('delete from publication.locations')
            await conn.execute('reset role')
            await conn.execute('update trips set started_at=null where id=$1', trip_id)
            await conn.execute('set local role madhi_public_export')
            assert await conn.fetch('select * from publication.locations') == []
            assert (await conn.fetchrow('select * from publication.trip'))['started_at'] is None
            await conn.execute('reset role')
            await conn.execute('update trips set started_at=$2 where id=$1', trip_id, now)
            await conn.execute('set local role madhi_public_export')
            assert await conn.fetch('select * from publication.locations') == []
            assert (await conn.fetchrow('select * from publication.trip'))['started_at'] is None
            # Roll back all fixtures including roles and views.
            raise RollbackTest()
    except RollbackTest:
        pass
    finally:
        await conn.close()


class RollbackTest(Exception):
    pass
