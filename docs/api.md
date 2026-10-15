# Server API

FastAPI and PostgreSQL 16, run with Docker Compose (`server/docker-compose.yml`).
Code in `server/app/`. All routes are under `/api/v1`, except `/health`.

## Data model

`server/migrations/001_initial_schema.sql`, applied by the one-shot `migrate`
container before the API starts.

| Table | Contents |
|---|---|
| `trips` | id, name, `started_at`, `ended_at` |
| `activation_codes` | HMAC of the code, trip, expiry, `used_at` |
| `devices` | id, trip, name, HMAC of the token, `last_seen_at`, app version, `revoked_at` |
| `locations` | client UUID, trip, device, coordinates, accuracy, altitude, speed, battery, `recorded_at`, `received_at` |

At startup the server creates (or renames) the trip from `INITIAL_TRIP_ID` /
`INITIAL_TRIP_NAME` and seeds `INITIAL_ACTIVATION_CODE`. An unused code has its
expiry extended on each restart; a used code is never reset.

Activation codes and device tokens are stored only as HMAC-SHA256 with
separate secrets (`ACTIVATION_CODE_HASH_SECRET`, `DEVICE_TOKEN_HASH_SECRET`).

## Authentication

Two kinds of bearer tokens:

- **Device token.** Returned once by activation. Required to upload points.
  A device with `revoked_at` set is refused.
- **Read token** (`PUBLIC_READ_TOKEN`). Required for every read route. It is
  never served to a browser: the family website's nginx adds it when relaying
  (see [Websites](websites.md#family-website)).

In development, with no read token configured, read routes are open. In
production the server refuses to start without one.

## Endpoints

### `GET /health`

Runs `select 1` on the database. `{"status": "ok"}`.

### `POST /api/v1/devices/activate`

No authentication; this call produces the token.

    { "activationCode": "ABCD-1234", "deviceName": "Xiaomi Redmi Note 11", "appVersion": "1.0.3" }

- `400 invalid_activation_code`: not `XXXX-XXXX` (letters and digits).
- `410 expired_or_unknown_code`: unknown, expired, or already used.
- `200`: `{ "deviceId", "deviceToken", "tripId" }`. The code is marked used in
  the same transaction, so it works once.

### `POST /api/v1/locations/batch`

Device token required.

    { "points": [ { "id": "<uuid>", "deviceId": "...", "latitude": 48.85, "longitude": 2.35,
                    "recordedAt": "2026-10-14T08:00:00Z",
                    "accuracyMeters": 12.0, "altitudeMeters": 35.0, "speedMps": 4.2,
                    "batteryPercent": 81 } ] }

Optional fields are omitted when unknown. `recordedAt` must be UTC with a `Z`.
The trip and device come from the token, not from the payload.

Response, always `200` once authenticated and within limits:

    { "accepted": ["<uuid>"], "duplicates": ["<uuid>"], "rejected": [ { "id": "<uuid>", "reason": "invalid_coordinates" } ] }

- **Idempotent by id.** Known ids go to `duplicates`; inserts use
  `on conflict (id) do nothing`. The phone treats `accepted` and `duplicates`
  the same way, so resending a batch after a lost response is safe.
- **Per-point rejection.** One bad point does not reject the batch. Reasons:
  `invalid_coordinates`, `invalid_recorded_at`, `missing_field`, `invalid_point`.
- `401 unauthorized`: missing, unknown, or revoked token.
- `413 batch_too_large` with `maxPoints`: more than `MAX_BATCH_POINTS` (200).

### `GET /api/v1/trips/{tripId}/locations`

Read token. Query: `from`, `to` (ISO-8601 UTC), `limit` (1 to 10,000,
default 10,000).

Returns a list of locations ordered by `recordedAt`, with the
`X-Madhi-Resolution-Seconds` header.

The history is **sampled, never truncated**. The server computes the time
span of the matching points and picks a bucket size so the span fits in
`limit` points (`server/app/sampling.py`), then keeps the oldest point of each
bucket. The response always covers the whole period requested; a longer
period only spaces the points further apart. There is no `LIMIT` in the SQL,
which used to cut off the newest points.

Points with accuracy worse than 165 m are excluded from the history (they stay
in the database).

### `GET /api/v1/trips/{tripId}/latest-location`

Read token. The most recent location, or `null`.

### `GET /api/v1/trips/{tripId}/status`

Read token. `tripId`, `name`, `startedAt`, `endedAt`, `totalLocations`,
`latestRecordedAt`, `latestReceivedAt`. `404 unknown_trip` if the id does not
exist. Timestamps only, no coordinates: monitoring uses this route.

### `GET /api/v1/trips/{tripId}/diagnostics/recent-locations`

Read token. `limit` 1 to 500, default 200. The latest points **without
coordinates**: accuracy, altitude, speed, battery, recorded and received
times. Used by the family website's GPS-quality page (`site/suivi.html`).

### Reverse geocoding

`GET /api/v1/reverse-geocode?lat=…&lon=…`

Accepts either the read token or a device token. Returns
`{ "address", "country", "countryCode" }`.

- `503 reverse_geocode_disabled` unless `REVERSE_GEOCODE_ENABLED=true`. The
  server refuses to start with it enabled and no `REVERSE_GEOCODE_USER_AGENT`,
  or with a non-HTTPS URL.
- `400 invalid_coordinates`, `404 address_not_found`.

The relay (`server/app/geocoding.py`) calls Nominatim (or the configured URL)
at most once per second, serialised by a lock, and caches 1,024 answers keyed
on coordinates rounded to 3 decimals (about 110 m). Empty answers are cached;
network failures are not. No coordinates are logged.

Neither the phone nor the family browser contacts the geocoder directly.

## Errors and limits

Errors are JSON: `{"error": "<code>"}`. Invalid JSON or schema: `400
invalid_payload`.

| Limit | Variable | Default |
|---|---|---|
| Requests per minute per client IP | `RATE_LIMIT_PER_MINUTE` | 120 (`429 rate_limited`, `Retry-After: 60`) |
| Request body size | `MAX_REQUEST_BYTES` | 1 MiB (`413 request_too_large`) |
| Points per batch | `MAX_BATCH_POINTS` | 200 |
| Activation code lifetime | `ACTIVATION_CODE_TTL_MINUTES` | 60 |

The rate limiter is in memory and keys on `X-Real-IP`, which nginx sets and
overwrites. `X-Forwarded-For` is ignored because the client can forge it.

## Production checks

With `APP_ENV=production`, `server/app/config.py` refuses to start if any
secret is missing or still an example value, if a secret is shorter than 32
characters, or if `DATABASE_URL` points to localhost or contains the
development password.

## Logs

Structured logs (`server/app/logging_config.py`) record events, device and
trip ids, never coordinates.

## Tests

`server/tests/`: API contract, configuration checks, logging, sampling,
reverse geocoding, and the public export. Integration tests run against the
Compose stack:

    docker compose -f server/docker-compose.yml --profile test run --rm test
