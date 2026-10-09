#!/usr/bin/env python3
"""Check the served public export, including every downloadable point."""
import argparse
from datetime import datetime, timedelta, timezone
import json
import urllib.error
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('base', nargs='?', default='http://127.0.0.1:8114')
args = parser.parse_args()
base = args.base.rstrip('/')


def fetch(path):
    with urllib.request.urlopen(base + path, timeout=20) as response:
        return json.load(response)


manifest = fetch('/data/manifest.json')
prefix = '/data/versions/' + manifest['version'] + '/'
snapshot = fetch(prefix + 'snapshot.json')
parse = lambda value: datetime.fromisoformat(value.replace('Z', '+00:00'))
cutoff = parse(snapshot['cutoffAt'])
assert cutoff <= datetime.now(timezone.utc) - timedelta(hours=168)
assert parse(snapshot['generatedAt']) - cutoff == timedelta(hours=168)
assert set(snapshot) == {
    'schemaVersion', 'generatedAt', 'cutoffAt', 'delayHours', 'trip', 'latest',
    'latestPlace', 'totalPoints', 'trackSampled', 'days',
}
assert set(snapshot['trip']) == {'name', 'startedAt', 'endedAt'}
for value in (snapshot['trip']['startedAt'], snapshot['trip']['endedAt']):
    if value is not None:
        assert parse(value) <= cutoff


def check_point(point):
    assert set(point) == {'id', 'latitude', 'longitude', 'recordedAt'}
    assert snapshot['trip']['startedAt'] is not None
    assert parse(snapshot['trip']['startedAt']) <= parse(point['recordedAt']) <= cutoff
    if snapshot['trip']['endedAt'] is not None:
        assert parse(point['recordedAt']) <= parse(snapshot['trip']['endedAt'])


if snapshot['latest'] is not None:
    check_point(snapshot['latest'])
count = 0
for filename in ['overview.json', 'track.json'] + [f'days/{day}.json' for day in snapshot['days']]:
    points = fetch(prefix + filename)
    for point in points:
        check_point(point)
    if filename.startswith('days/'):
        count += len(points)
assert count == snapshot['totalPoints']
for path in [
    '/api/v1/trips/test/status', '/api/reverse-geocode?lat=0&lon=0',
    '/suivi.html', '/installer.html', '/mise_a_jour.html', '/api-client.js',
    '/config.js', '/app.js', '/.env', '/public-export.env', '/data/',
    '/data/versions/', '/data/export.lock', '/data/../public-export.env',
    '/data/%2e%2e/public-export.env', '/f/test/api/trips/test/latest-location',
]:
    try:
        urllib.request.urlopen(base + path, timeout=10)
    except urllib.error.HTTPError as error:
        assert error.code in (403, 404), (path, error.code)
    else:
        raise AssertionError(f'Unexpected public route: {path}')
print(f'Public HTTP checks passed: {count} eligible points; private routes closed.')
