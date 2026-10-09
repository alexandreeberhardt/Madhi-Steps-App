from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
from unittest.mock import patch

import pytest

from app.public_export import DELAY, add_latest_place, check, make_snapshot, prune, publish, sample

NOW = datetime(2026, 10, 9, 12, tzinfo=timezone.utc)
CUTOFF = NOW - DELAY
START = CUTOFF - timedelta(days=5)


def trip(start=START, end=None):
    return {'name': 'Voyage', 'started_at': start, 'ended_at': end}


def point(at, lat=45):
    return {'recorded_at': at, 'latitude': lat, 'longitude': 4,
            'device_id': 'private-device', 'battery_percent': 70, 'received_at': NOW}


def test_cutoff_start_and_private_fields():
    rows = [point(START - timedelta(microseconds=1)), point(START),
            point(CUTOFF - timedelta(microseconds=1)), point(CUTOFF),
            point(CUTOFF + timedelta(microseconds=1)), point(NOW)]
    files = make_snapshot(trip(), rows, NOW)
    snapshot = files['snapshot.json']
    assert snapshot['totalPoints'] == 3
    assert snapshot['latest']['recordedAt'] == '2026-10-02T12:00:00Z'
    assert set(snapshot['latest']) == {'id', 'latitude', 'longitude', 'recordedAt'}
    encoded = json.dumps(files)
    for forbidden in ('device', 'battery', 'received', 'accuracy', 'speed'):
        assert forbidden not in encoded
    assert len(files['track.json']) == 3


@pytest.mark.parametrize('start', [None, CUTOFF + timedelta(microseconds=1), NOW])
def test_no_public_departure_means_no_locations(start):
    files = make_snapshot(trip(start), [point(START), point(CUTOFF)], NOW)
    snapshot = files['snapshot.json']
    assert snapshot['latest'] is None
    assert snapshot['trip']['startedAt'] is None
    assert snapshot['days'] == []
    assert files['track.json'] == []


def test_end_is_delayed_and_points_after_end_are_excluded():
    files = make_snapshot(trip(end=NOW), [point(CUTOFF)], NOW)
    assert files['snapshot.json']['trip']['endedAt'] is None
    files = make_snapshot(trip(end=CUTOFF), [point(CUTOFF), point(NOW)], NOW)
    assert files['snapshot.json']['trip']['endedAt'] == '2026-10-02T12:00:00Z'
    ended = START + timedelta(hours=1)
    files = make_snapshot(trip(end=ended), [point(START), point(CUTOFF)], NOW)
    assert files['snapshot.json']['totalPoints'] == 1


def test_late_uploads_are_published_by_recorded_time_and_days_are_utc():
    files = make_snapshot(trip(), [point(CUTOFF), point(START)], NOW)
    assert files['snapshot.json']['totalPoints'] == 2
    assert files['snapshot.json']['days'] == ['2026-09-27', '2026-10-02']
    assert len(files['days/2026-09-27.json']) == 1


def test_sampling_preserves_both_ends_and_covers_entire_trip():
    points = list(range(20000))
    result = sample(points, 1500)
    assert len(result) == 1500
    assert result[0] == 0 and result[-1] == 19999
    assert result == sorted(set(result))


def test_subsecond_points_are_sorted_chronologically():
    second = START + timedelta(seconds=1)
    files = make_snapshot(trip(), [point(second + timedelta(microseconds=1)), point(second)], NOW)
    assert files['track.json'][0]['recordedAt'].endswith('01Z')
    assert files['snapshot.json']['latest']['recordedAt'].endswith('01.000001Z')


def test_atomic_publication_keeps_old_version_on_failure(tmp_path):
    files = make_snapshot(trip(), [point(CUTOFF)], NOW)
    previous = publish(tmp_path, files)
    manifest_path = tmp_path / 'live/manifest.json'
    before = manifest_path.read_bytes()
    import app.public_export as exporter
    replace = exporter.os.replace

    def fail_pointer(source, destination):
        if Path(destination).name == 'manifest.json':
            raise OSError('simulated interrupted publication')
        return replace(source, destination)

    with patch.object(exporter.os, 'replace', side_effect=fail_pointer):
        with pytest.raises(OSError):
            publish(tmp_path, make_snapshot(trip(), [], NOW))
    assert manifest_path.read_bytes() == before
    assert (tmp_path / 'live/versions' / previous / 'snapshot.json').is_file()
    assert not list(tmp_path.glob('staging-*'))
    assert not list(tmp_path.glob('manifest-*'))
    assert set(path.name for path in (tmp_path / 'live').iterdir()) == {'manifest.json', 'versions'}


def test_published_files_are_readable_by_nginx(tmp_path):
    version = publish(tmp_path, make_snapshot(trip(), [point(CUTOFF)], NOW))
    directory = tmp_path / 'live/versions' / version
    assert directory.stat().st_mode & 0o777 == 0o755
    for path in directory.rglob('*.json'):
        assert path.stat().st_mode & 0o777 == 0o644
        json.loads(path.read_text())


def test_staleness_check(tmp_path):
    publish(tmp_path, make_snapshot(trip(), [], datetime.now(timezone.utc)))
    check(tmp_path)
    publish(tmp_path, make_snapshot(trip(), [], NOW - timedelta(days=365)))
    with pytest.raises(ValueError, match='stale'):
        check(tmp_path)


def test_cleanup_keeps_current_and_recent_versions(tmp_path):
    import os
    old = publish(tmp_path, make_snapshot(trip(), [], NOW))
    recent = publish(tmp_path, make_snapshot(trip(), [], NOW))
    current = publish(tmp_path, make_snapshot(trip(), [], NOW))
    now = datetime.now(timezone.utc)
    stale = (now - timedelta(days=3)).timestamp()
    for version in (old, current):
        os.utime(tmp_path / 'live/versions' / version, (stale, stale))
    prune(tmp_path, current, now)
    assert not (tmp_path / 'live/versions' / old).exists()
    assert (tmp_path / 'live/versions' / recent).exists()
    assert (tmp_path / 'live/versions' / current).exists()


@pytest.mark.asyncio
async def test_geocoding_only_uses_published_latest_and_reuses_previous_place(tmp_path, monkeypatch):
    from app.geocoding import GeocodedPlace
    monkeypatch.setenv('REVERSE_GEOCODE_ENABLED', 'true')
    monkeypatch.setenv('REVERSE_GEOCODE_USER_AGENT', 'test')
    seen = []

    async def lookup(self, latitude, longitude):
        seen.append((latitude, longitude))
        return GeocodedPlace('Published address', 'France', 'FR')

    monkeypatch.setattr('app.public_export.ReverseGeocoder.lookup_place', lookup)
    files = make_snapshot(trip(), [point(CUTOFF, 45), point(NOW, 48)], NOW)
    await add_latest_place(files['snapshot.json'], tmp_path)
    assert seen == [(45, 4)]
    publish(tmp_path, files)
    again = make_snapshot(trip(), [point(CUTOFF, 45)], NOW)
    await add_latest_place(again['snapshot.json'], tmp_path)
    assert seen == [(45, 4)]
    assert again['snapshot.json']['latestPlace'] == files['snapshot.json']['latestPlace']
    empty = make_snapshot(trip(None), [point(CUTOFF)], NOW)
    await add_latest_place(empty['snapshot.json'], tmp_path)
    assert seen == [(45, 4)]
