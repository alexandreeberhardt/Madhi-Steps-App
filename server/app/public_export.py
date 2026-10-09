"""Publish immutable, delayed snapshots. No dependency on the private HTTP API."""
from __future__ import annotations

import argparse
import asyncio
from collections import defaultdict
from datetime import datetime, timedelta, timezone
import fcntl
import hashlib
import json
import logging
import math
import os
from pathlib import Path
import shutil
import tempfile
from uuid import uuid4

import asyncpg

from .geocoding import ReverseGeocoder

DELAY = timedelta(hours=168)
RETENTION = timedelta(days=2)
LOGGER = logging.getLogger('madhi.public_export')


def instant(value: datetime) -> str:
    if value.tzinfo is None:
        raise ValueError('Timezone required')
    return value.astimezone(timezone.utc).isoformat().replace('+00:00', 'Z')


def sample(points: list[dict], limit: int) -> list[dict]:
    if len(points) <= limit:
        return points
    return [points[index * (len(points) - 1) // (limit - 1)] for index in range(limit)]


def make_snapshot(trip: dict, rows: list, generated_at: datetime) -> dict[str, object]:
    cutoff = generated_at - DELAY
    started = trip['started_at']
    ended = trip['ended_at']
    if started is None or started > cutoff:
        started = None
        ended = None
    if ended is not None and ended > cutoff:
        ended = None
    points = []
    for row in rows:
        recorded = row['recorded_at']
        # Defense in depth: the database views already enforce this boundary.
        if started is None or recorded < started or recorded > cutoff:
            continue
        if ended is not None and recorded > ended:
            continue
        latitude, longitude = float(row['latitude']), float(row['longitude'])
        if not math.isfinite(latitude) or not math.isfinite(longitude):
            raise ValueError('Invalid coordinate')
        if not -90 <= latitude <= 90 or not -180 <= longitude <= 180:
            raise ValueError('Invalid coordinate')
        timestamp = instant(recorded)
        identity = f'{timestamp}|{latitude}|{longitude}'
        points.append({
            'id': hashlib.sha256(identity.encode()).hexdigest()[:24],
            'latitude': latitude, 'longitude': longitude, 'recordedAt': timestamp,
        })
    points.sort(key=lambda point: (datetime.fromisoformat(point['recordedAt'].replace('Z', '+00:00')), point['id']))
    days = defaultdict(list)
    for point in points:
        days[point['recordedAt'][:10]].append(point)
    files = {f'days/{day}.json': daily for day, daily in days.items()}
    files['overview.json'] = sample(points, 1500)
    files['track.json'] = sample(points, 10000)
    files['snapshot.json'] = {
        'schemaVersion': 1,
        'generatedAt': instant(generated_at),
        'cutoffAt': instant(cutoff),
        'delayHours': 168,
        'trip': {
            'name': trip['name'],
            'startedAt': instant(started) if started else None,
            'endedAt': instant(ended) if ended else None,
        },
        'latest': points[-1] if points else None,
        'latestPlace': None,
        'totalPoints': len(points),
        'trackSampled': len(points) > 10000,
        'days': sorted(days),
    }
    return files


def write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('w', encoding='utf-8') as stream:
        json.dump(payload, stream, ensure_ascii=False, allow_nan=False, separators=(',', ':'))
        stream.write('\n')
        stream.flush()
        os.fsync(stream.fileno())
    path.chmod(0o644)


def publish(root: Path, files: dict[str, object]) -> str:
    # nginx mounts only root/live: staging and lock files are never served.
    live = root / 'live'
    versions = live / 'versions'
    versions.mkdir(parents=True, exist_ok=True)
    version = uuid4().hex
    stage = Path(tempfile.mkdtemp(prefix='staging-', dir=root))
    pointer = root / f'manifest-{version}.json'
    try:
        for name, payload in files.items():
            write_json(stage / name, payload)
        stage.chmod(0o755)
        os.replace(stage, versions / version)
        snapshot = files['snapshot.json']
        write_json(pointer, {
            'schemaVersion': 1, 'version': version,
            'generatedAt': snapshot['generatedAt'], 'cutoffAt': snapshot['cutoffAt'],
        })
        os.replace(pointer, live / 'manifest.json')
    finally:
        if stage.exists():
            shutil.rmtree(stage)
        pointer.unlink(missing_ok=True)
    return version


def prune(root: Path, current: str, now: datetime) -> None:
    # Keep two days for already-open tabs. The current version is never removed.
    for directory in (root / 'live' / 'versions').iterdir():
        if directory.name == current or not directory.is_dir():
            continue
        modified = datetime.fromtimestamp(directory.stat().st_mtime, timezone.utc)
        if now - modified > RETENTION:
            shutil.rmtree(directory)


async def read_snapshot(url: str) -> dict[str, object]:
    conn = await asyncpg.connect(url, command_timeout=120)
    try:
        async with conn.transaction(isolation='repeatable_read', readonly=True):
            now = await conn.fetchval('select transaction_timestamp()')
            trip = await conn.fetchrow('select name, started_at, ended_at from publication.trip')
            if trip is None:
                raise ValueError('Public trip missing')
            rows = await conn.fetch(
                'select latitude, longitude, recorded_at from publication.locations '
                'where recorded_at <= $1 order by recorded_at, latitude, longitude', now - DELAY,
            )
            return make_snapshot(trip, rows, now)
    finally:
        await conn.close()


async def export(root: Path) -> str:
    root.mkdir(parents=True, exist_ok=True)
    with (root / 'export.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        files = await read_snapshot(os.environ['PUBLIC_EXPORT_DATABASE_URL'])
        await add_latest_place(files['snapshot.json'], root)
        version = publish(root, files)
        prune(root, version, datetime.now(timezone.utc))
    return version


async def add_latest_place(snapshot: dict, root: Path) -> None:
    latest = snapshot['latest']
    if latest is None or os.getenv('REVERSE_GEOCODE_ENABLED', '').lower() != 'true':
        return
    user_agent = os.getenv('REVERSE_GEOCODE_USER_AGENT', '')
    if not user_agent:
        return
    try:
        manifest = json.loads((root / 'live/manifest.json').read_text())
        previous_path = root / 'live/versions' / manifest['version'] / 'snapshot.json'
        previous = json.loads(previous_path.read_text())
        if previous['latest'] == latest and previous.get('latestPlace'):
            snapshot['latestPlace'] = previous['latestPlace']
            return
    except (OSError, ValueError, KeyError):
        pass
    geocoder = ReverseGeocoder(
        os.getenv('REVERSE_GEOCODE_URL', 'https://nominatim.openstreetmap.org/reverse'), user_agent,
    )
    place = await geocoder.lookup_place(latest['latitude'], latest['longitude'])
    if place is not None:
        snapshot['latestPlace'] = {
            'address': place.address, 'country': place.country, 'countryCode': place.country_code,
        }


def check(root: Path) -> None:
    with (root / 'live' / 'manifest.json').open() as stream:
        manifest = json.load(stream)
    generated = datetime.fromisoformat(manifest['generatedAt'].replace('Z', '+00:00'))
    age = datetime.now(timezone.utc) - generated
    if age < timedelta(0) or age > timedelta(hours=3):
        raise ValueError('Public export is stale')


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path('/export'))
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format='%(levelname)s %(message)s')
    try:
        if args.check:
            check(args.root)
        else:
            asyncio.run(export(args.root))
            LOGGER.info('Public snapshot published')
    except Exception as error:
        # No exception message: connection errors can include credentials.
        LOGGER.error('Public export failed (%s)', type(error).__name__)
        raise SystemExit(1) from None


if __name__ == '__main__':
    main()
