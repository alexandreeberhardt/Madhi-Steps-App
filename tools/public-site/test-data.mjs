import assert from 'node:assert/strict';
import { totalDistance, tripDays, formatDistance } from '../../site/public/summary.js';
import { createPublicSource, periodBounds, publicationStale } from '../../site/public/data.js';

const versionA = 'a'.repeat(32);
const versionB = 'b'.repeat(32);
const snapshot = {
  schemaVersion: 1, delayHours: 168,
  generatedAt: '2026-10-09T12:00:00Z', cutoffAt: '2026-10-02T12:00:00Z',
  trip: { name: 'Test', startedAt: '2026-09-01T12:00:00Z', endedAt: null },
  days: ['2026-09-25', '2026-10-01', '2026-10-02'], trackSampled: false,
};
const point = (time) => ({ id: time, recordedAt: time, latitude: 45, longitude: 4 });
const files = {
  'snapshot.json': snapshot,
  'overview.json': [point('2026-10-02T12:00:00Z')],
  'track.json': [point('2026-09-01T12:00:00Z'), point('2026-10-02T12:00:00Z')],
  'days/2026-10-01.json': [point('2026-10-01T11:59:59Z'), point('2026-10-01T12:00:00Z')],
  'days/2026-10-02.json': [point('2026-10-02T12:00:00Z')],
};
const requests = [];
let activeVersion = versionA;
let fail = null;
globalThis.fetch = async (url, options) => {
  requests.push(url);
  assert.equal(options.credentials, 'omit');
  assert.ok(url.startsWith('./data/'));
  assert.ok(!url.includes('/api'));
  if (url === './data/manifest.json') {
    assert.equal(options.cache, 'no-store');
    return Response.json({ schemaVersion: 1, version: activeVersion });
  }
  const parts = url.match(/^\.\/data\/versions\/([ab]{32})\/(.+)$/);
  assert.ok(parts, url);
  if (fail === parts[2]) return new Response('', { status: 503 });
  const file = files[parts[2]];
  assert.ok(file, parts[2]);
  // Simulate a new export appearing halfway through a download.
  activeVersion = versionB;
  return Response.json(file);
};

const bounds = periodBounds(snapshot, '7D');
assert.equal(bounds.to.toISOString(), '2026-10-02T12:00:00.000Z');
assert.equal(bounds.from.toISOString(), '2026-09-25T12:00:00.000Z');
assert.equal(publicationStale(snapshot, new Date('2026-10-09T14:00:00Z')), false);
assert.equal(publicationStale(snapshot, new Date('2026-10-09T16:00:00Z')), true);
const source = createPublicSource();
const first = await source.load('24H');
assert.equal(first.version, versionA);
assert.equal(first.points.length, 2);
assert.ok(requests.filter((url) => url.includes('/versions/')).every((url) => url.includes(versionA)));
assert.ok(!requests.some((url) => url.endsWith('days/2026-09-25.json')));
requests.length = 0;
const next = await source.load('ALL');
assert.equal(next.version, versionB);
assert.equal(next.points.length, 2);
assert.ok(requests.filter((url) => url.includes('/versions/')).every((url) => url.includes(versionB)));

const retrySource = createPublicSource();
fail = 'days/2026-10-02.json';
await assert.rejects(retrySource.load('24H'), /public_data_503/);
fail = null;
assert.equal((await retrySource.load('24H')).points.length, 2);

const emptySource = createPublicSource();
files['snapshot.json'] = { ...snapshot, trip: { ...snapshot.trip, startedAt: null }, days: [] };
files['overview.json'] = [];
const empty = await emptySource.load('7D');
assert.deepEqual(empty.points, []);
assert.equal(empty.bounds.from.getTime(), empty.bounds.to.getTime());
console.log('Public data tests passed: delayed periods, immutable versions, no API, recovery, empty state.');

const today = periodBounds(snapshot, 'TODAY');
assert.equal(today.from.getHours(), 0);
assert.equal(today.to.toISOString(), '2026-10-02T12:00:00.000Z');
assert.equal(tripDays(snapshot), 32);
assert.equal(tripDays({ ...snapshot, trip: { ...snapshot.trip, endedAt: '2026-09-10T12:00:00Z' } }), 10);
assert.equal(tripDays({ ...snapshot, trip: { ...snapshot.trip, startedAt: null } }), null);
assert.equal(totalDistance([]), 0);
assert.equal(totalDistance([{ latitude: 0, longitude: 0 }]), 0);
const metres = totalDistance([{ latitude: 0, longitude: 0 }, { latitude: 1, longitude: 0 }]);
assert.ok(Math.abs(metres - 111195) < 1);
assert.match(formatDistance(metres), /111,2 km/);
assert.equal(first.summary.todayMetres, 0);
assert.equal(first.summary.totalMetres, next.summary.totalMetres);
console.log('Summary checks passed: cutoff day, ended trip, no departure, distances and stable totals.');
