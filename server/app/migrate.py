from __future__ import annotations

import asyncio
import logging
from pathlib import Path

import asyncpg

from .config import load_settings
from .logging_config import configure_logging


MIGRATIONS_DIR = Path(__file__).resolve().parents[1] / "migrations"


async def migrate() -> None:
    settings = load_settings()
    configure_logging(settings.log_level)
    logger = logging.getLogger("madhi.migrations")
    conn = await asyncpg.connect(settings.database_url)
    try:
        await conn.execute(
            """
            create table if not exists schema_migrations (
                version text primary key,
                applied_at timestamptz not null default now()
            )
            """
        )
        async with conn.transaction():
            for path in sorted(MIGRATIONS_DIR.glob("*.sql")):
                version = path.name
                applied = await conn.fetchval("select 1 from schema_migrations where version = $1", version)
                if applied:
                    continue
                sql = path.read_text(encoding="utf-8")
                await conn.execute(sql)
                await conn.execute("insert into schema_migrations (version) values ($1)", version)
                logger.info("migration_applied", extra={"path": version})
    finally:
        await conn.close()


if __name__ == "__main__":
    asyncio.run(migrate())
