from __future__ import annotations

import os
from dataclasses import dataclass


PLACEHOLDER_VALUES = {
    "",
    "change-me",
    "change_me",
    "example",
    "example-secret",
    "dev-secret-change-me",
    "dev-read-token-change-me",
    "madhi_dev_password",
    "XXXX-XXXX",
    "00000000-0000-0000-0000-000000000000",
}


@dataclass(frozen=True)
class Settings:
    app_env: str
    database_url: str
    device_token_hash_secret: str
    activation_code_hash_secret: str
    initial_trip_id: str
    initial_trip_name: str
    initial_activation_code: str
    activation_code_ttl_minutes: int
    max_batch_points: int
    max_request_bytes: int
    rate_limit_per_minute: int
    public_read_token: str | None
    log_level: str

    @property
    def production(self) -> bool:
        return self.app_env.lower() == "production"


def load_settings() -> Settings:
    settings = Settings(
        app_env=os.getenv("APP_ENV", "development"),
        database_url=_env("DATABASE_URL", "postgresql://madhi:madhi_dev_password@postgres:5432/madhi_tracker"),
        device_token_hash_secret=_env("DEVICE_TOKEN_HASH_SECRET", "dev-secret-change-me"),
        activation_code_hash_secret=_env("ACTIVATION_CODE_HASH_SECRET", "dev-secret-change-me"),
        initial_trip_id=_env("INITIAL_TRIP_ID", "8f14e45f-ceea-467a-9f4e-2b1c9a1a1a1a"),
        initial_trip_name=_env("INITIAL_TRIP_NAME", "Madhi 2026"),
        initial_activation_code=_env("INITIAL_ACTIVATION_CODE", "XXXX-XXXX"),
        activation_code_ttl_minutes=int(_env("ACTIVATION_CODE_TTL_MINUTES", "60")),
        max_batch_points=int(_env("MAX_BATCH_POINTS", "200")),
        max_request_bytes=int(_env("MAX_REQUEST_BYTES", "1048576")),
        rate_limit_per_minute=int(_env("RATE_LIMIT_PER_MINUTE", "120")),
        public_read_token=os.getenv("PUBLIC_READ_TOKEN"),
        log_level=_env("LOG_LEVEL", "INFO"),
    )
    validate_settings(settings)
    return settings


def validate_settings(settings: Settings) -> None:
    if settings.max_batch_points <= 0:
        raise RuntimeError("MAX_BATCH_POINTS must be positive")
    if settings.max_request_bytes <= 0:
        raise RuntimeError("MAX_REQUEST_BYTES must be positive")
    if settings.rate_limit_per_minute <= 0:
        raise RuntimeError("RATE_LIMIT_PER_MINUTE must be positive")
    if settings.activation_code_ttl_minutes <= 0:
        raise RuntimeError("ACTIVATION_CODE_TTL_MINUTES must be positive")

    if not settings.production:
        return

    required = {
        "DATABASE_URL": settings.database_url,
        "DEVICE_TOKEN_HASH_SECRET": settings.device_token_hash_secret,
        "ACTIVATION_CODE_HASH_SECRET": settings.activation_code_hash_secret,
        "INITIAL_TRIP_ID": settings.initial_trip_id,
        "INITIAL_ACTIVATION_CODE": settings.initial_activation_code,
        "PUBLIC_READ_TOKEN": settings.public_read_token or "",
    }
    for name, value in required.items():
        _reject_placeholder(name, value)

    _reject_short_secret("DEVICE_TOKEN_HASH_SECRET", settings.device_token_hash_secret)
    _reject_short_secret("ACTIVATION_CODE_HASH_SECRET", settings.activation_code_hash_secret)
    _reject_short_secret("PUBLIC_READ_TOKEN", settings.public_read_token or "")
    if "localhost" in settings.database_url or "127.0.0.1" in settings.database_url:
        raise RuntimeError("DATABASE_URL must not point to localhost in production")
    if "madhi_dev_password" in settings.database_url or "change-me" in settings.database_url:
        raise RuntimeError("DATABASE_URL still contains a development password in production")


def _env(name: str, default: str) -> str:
    return os.getenv(name, default).strip()


def _reject_placeholder(name: str, value: str) -> None:
    if value.strip() in PLACEHOLDER_VALUES:
        raise RuntimeError(f"{name} is missing or still uses an example value")


def _reject_short_secret(name: str, value: str) -> None:
    if len(value) < 32:
        raise RuntimeError(f"{name} is too short for production")
