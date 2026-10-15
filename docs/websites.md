# Websites

Two websites read the same database in very different ways.

| | Family | Public |
|---|---|---|
| Address | `madhi.alexeber.fr/f/<secret>/` | `travel.alexeber.fr` |
| Access | Secret path + HTTP password | Open |
| Data | Live, through the API | Static files, 168 hours late |
| Language | French | English |
| Code | `site/` | `site/public/` plus shared components |

Neither has a build step: the files in the repository are the files served.
Both use Leaflet 1.9.4 from `site/vendor/` and OpenStreetMap tiles.

## Family website

### Serving

The `site` container in `server/docker-compose.yml` runs nginx with
`tools/nginx/site.conf.template`. The host's nginx terminates TLS and relays
to it.

- Everything outside `/f/<SITE_SECRET_SEGMENT>/` returns 404.
- The site and its API relay require the family password
  (`server/madhi.htpasswd`).
- `/f/<secret>/api/…` is proxied to the API container. nginx **replaces** the
  `Authorization` header with the read token, so the token never reaches the
  browser and the family password never reaches the API.
- Headers: `Referrer-Policy: no-referrer` (the secret path must not leak into
  tile requests), `noindex`, and a Content-Security-Policy that only allows
  the site itself and `tile.openstreetmap.org` images. Location data cannot be
  sent anywhere else, even by mistake.
- `Cache-Control: no-cache` on the site and `no-store` on the API, so a fix
  made from the road shows up on the next load.
- `/tiles/v<n>/…` serves the self-hosted base map built by `tools/tiles`,
  publicly and cached for a year. It contains no trip data.

The `site` container depends on the API being started, not healthy, so the
website can still say that the server is not responding.

### Pages

- `index.html`: the map.
- `suivi.html`: GPS quality, from the diagnostics route (no coordinates).
- `installer.html`: the installation guide, self-contained, no outgoing
  requests.
- `mise_a_jour.html`: the update page opened from the app; links to
  `madhi-tracker-latest.apk` on GitHub releases.

All of them are behind the secret path and password.

### How the map page works

`site/app.js` follows one cycle: **load → state → render**. Rendering reads the
state and updates the DOM; it never makes a network call.

- `api-client.js` is the only module that calls `fetch`, always with relative
  URLs (`./api/…`) so requests stay on the protected path. Timeout 10 s.
- Data refreshes every 2 minutes while the tab is visible; the "8 min ago"
  labels redraw every 30 seconds without a request.
- `features/trip-state.js` computes the single trip state shown everywhere.
  First match wins: loading, server unavailable, before departure (no
  `startedAt`), no location, trip ended, then recent (< 1 h), old (< 12 h), or
  offline (≥ 12 h).
- `features/period.js`: Today, 24 h, 7 days, 30 days, Whole trip (default
  Today). Every period starts no earlier than the trip's `startedAt`.
- The main track asks for up to 10,000 points; the whole trip is drawn
  underneath in light blue from 1,500 points.
- Tapping a point (`features/track-picking.js`) shows its time and, through the
  API relay, its address. Addresses are cached for the life of the tab.

The trip id is in `site/config.js` and must match `INITIAL_TRIP_ID`. It is not
a secret.

Neither the family website nor the app shows the battery level.

Development and checks without a browser:

    python3 tools/site-dev/serve.py
    node tools/site-dev/verifier.mjs

## Public website

### Export

The public site never calls the API. `server/app/public_export.py` runs hourly
(`tools/public-site/madhi-public-export.timer`) in the `exporter` container of
`server/compose.public.yml`:

1. It connects as `madhi_public_export`, a read-only login role created by
   `server/app/public_access.py`. That role can only read two views in the
   `publication` schema, restricted to the configured trip:
   - `publication.trip`: name, and start/end dates only once they are 168 hours
     old;
   - `publication.locations`: latitude, longitude, `recorded_at`, only for
     points after the start, before the end, at least 168 hours old, and with
     accuracy of 165 m or better. No device, battery, or reception time.
2. It reads in a read-only repeatable-read transaction and applies the same
   bounds again in Python.
3. It writes a new version directory, then replaces `live/manifest.json`
   atomically. Previous versions stay for two days for tabs already open.

Each version contains:

- `snapshot.json`: generation and cutoff times, trip dates, latest public
  point, total points, list of days;
- `overview.json`: background track, at most 1,500 points;
- `track.json`: the whole trip, at most 10,000 points spread over its duration;
- `days/YYYY-MM-DD.json`: every point of each UTC day.

Public point ids are hashes of time and coordinates, not database ids. The
trip name in the export is fixed to "Travel 2026".

If geocoding is enabled, only the latest public point is geocoded at export
time, and the previous result is reused if that point has not changed.

The check timer (every 15 minutes) fails if the export is more than 3 hours
old.

### Serving

A separate nginx container with its own Docker network, no secrets, and no
route to the API. It mounts only the public files, the shared map components,
and the finished exports, read-only. The two stacks start and stop
independently.

### Browser

`site/public/data.js` reads only the static files. Periods (Today, 24 h,
7 days, 30 days, All time) end at the cutoff, not at the visitor's current
time. Only the day files needed for a period are downloaded. The page retries
every five minutes while visible and warns when the export is more than three
hours old.

Installation, verification, and rollback: `tools/public-site/README.md`.
