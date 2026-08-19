create table if not exists schema_migrations (
    version text primary key,
    applied_at timestamptz not null default now()
);

create table if not exists trips (
    id uuid primary key,
    name text not null,
    started_at timestamptz,
    ended_at timestamptz,
    created_at timestamptz not null default now()
);

create table if not exists activation_codes (
    code_hash text primary key,
    trip_id uuid not null references trips(id) on delete cascade,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    used_at timestamptz
);

create table if not exists devices (
    id uuid primary key,
    trip_id uuid not null references trips(id) on delete cascade,
    name text not null,
    token_hash text not null unique,
    last_seen_at timestamptz,
    app_version text,
    revoked_at timestamptz,
    created_at timestamptz not null default now()
);

create table if not exists locations (
    id uuid primary key,
    trip_id uuid not null references trips(id) on delete cascade,
    device_id uuid not null references devices(id) on delete restrict,
    latitude double precision not null check (latitude >= -90.0 and latitude <= 90.0),
    longitude double precision not null check (longitude >= -180.0 and longitude <= 180.0),
    accuracy_meters double precision,
    altitude_meters double precision,
    speed_mps double precision,
    battery_percent integer check (battery_percent is null or (battery_percent >= 0 and battery_percent <= 100)),
    recorded_at timestamptz not null,
    received_at timestamptz not null default now()
);

create index if not exists idx_locations_trip_recorded_at on locations (trip_id, recorded_at);
create index if not exists idx_locations_trip_received_at on locations (trip_id, received_at);
