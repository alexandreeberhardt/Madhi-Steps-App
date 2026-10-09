// Only immutable public files are read here. There is no fallback to the family API.
const DAY_MS = 86400000;
export const PERIODS = [
  { id: '24H', label: '24 h publiées', days: 1 },
  { id: '7D', label: '7 jours publiés', days: 7 },
  { id: '30D', label: '30 jours publiés', days: 30 },
  { id: 'ALL', label: 'Tout le voyage', days: null },
];

async function json(url, mutable = false) {
  const response = await fetch(url, {
    cache: mutable ? 'no-store' : 'default',
    credentials: 'omit',
    signal: AbortSignal.timeout(15000),
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) throw new Error(`public_data_${response.status}`);
  return response.json();
}

export function periodBounds(snapshot, period) {
  const selected = PERIODS.find((item) => item.id === period);
  if (!selected) throw new Error('Unknown period');
  const to = new Date(snapshot.cutoffAt);
  const start = new Date(snapshot.trip.startedAt ?? snapshot.cutoffAt);
  const from = selected.days === null
    ? start
    : new Date(Math.max(start.getTime(), to.getTime() - selected.days * DAY_MS));
  return { from, to };
}

export function publicationStale(snapshot, now = new Date()) {
  return now.getTime() - new Date(snapshot.generatedAt).getTime() > 3 * 60 * 60 * 1000;
}

export function createPublicSource() {
  let cached = null;

  async function open() {
    const manifest = await json('./data/manifest.json', true);
    if (manifest.schemaVersion !== 1 || !/^[a-f0-9]{32}$/.test(manifest.version)) {
      throw new Error('Invalid public manifest');
    }
    if (cached?.version === manifest.version) return cached;
    const base = `./data/versions/${manifest.version}/`;
    const snapshot = await json(`${base}snapshot.json`);
    if (snapshot.schemaVersion !== 1 || snapshot.delayHours !== 168
        || !Number.isFinite(Date.parse(snapshot.generatedAt))
        || !Number.isFinite(Date.parse(snapshot.cutoffAt))
        || Date.parse(snapshot.generatedAt) - Date.parse(snapshot.cutoffAt) < 168 * 3600000
        || !Array.isArray(snapshot.days)
        || snapshot.days.some((day) => !/^\d{4}-\d{2}-\d{2}$/.test(day))) {
      throw new Error('Invalid public snapshot');
    }
    const requests = new Map();
    function read(file) {
      if (!requests.has(file)) {
        requests.set(file, json(base + file).catch((error) => {
          requests.delete(file);
          throw error;
        }));
      }
      return requests.get(file);
    }
    // Each returned handle keeps its version, including during concurrent refreshes.
    cached = { version: manifest.version, snapshot, read };
    return cached;
  }

  async function load(period) {
    const handle = await open();
    const { snapshot } = handle;
    const bounds = periodBounds(snapshot, period);
    // Await this first so a failing period request cannot leave an unhandled rejection.
    const overview = await handle.read('overview.json');
    let points;
    if (period === 'ALL') {
      points = await handle.read('track.json');
    } else {
      const first = bounds.from.toISOString().slice(0, 10);
      const last = bounds.to.toISOString().slice(0, 10);
      const days = snapshot.days.filter((day) => day >= first && day <= last);
      const chunks = [];
      // Bound simultaneous downloads for a month's worth of daily files.
      for (let offset = 0; offset < days.length; offset += 4) {
        const batch = await Promise.all(days.slice(offset, offset + 4).map(
          (day) => handle.read(`days/${day}.json`),
        ));
        chunks.push(...batch);
      }
      points = chunks.flat().filter((point) => {
        const at = Date.parse(point.recordedAt);
        return at >= bounds.from.getTime() && at <= bounds.to.getTime();
      });
    }
    const sampled = period === 'ALL' ? snapshot.trackSampled : points.length > 10000;
    if (points.length > 10000) {
      const all = points;
      points = Array.from({ length: 10000 }, (_, index) => all[Math.floor(index * (all.length - 1) / 9999)]);
    }
    return { snapshot, bounds, points, overview, sampled, version: handle.version };
  }
  return { load };
}
