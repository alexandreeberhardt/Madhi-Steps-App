from __future__ import annotations

import json
import logging
import sys
from datetime import datetime, timezone


HEALTH_PATH = "/health"


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "timestamp": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        for key in ("request_id", "client_ip", "status_code", "path", "method", "device_id", "trip_id"):
            value = getattr(record, key, None)
            if value is not None:
                payload[key] = value
        return json.dumps(payload, separators=(",", ":"))


class DropHealthcheckAccessLogs(logging.Filter):
    """Ecarte du journal les acces au healthcheck.

    Docker sonde `/health` toutes les dix secondes, soit environ trois millions
    de lignes sur l'annee du voyage. Les evenements reels s'y perdraient.
    """

    def filter(self, record: logging.LogRecord) -> bool:
        args = record.args
        if isinstance(args, tuple) and len(args) >= 3:
            return args[2] != HEALTH_PATH
        return True


def silence_healthcheck_access_logs() -> None:
    """Uvicorn configure `uvicorn.access` avant d'importer l'application, avec
    son propre gestionnaire et `propagate` a faux. Le filtre se pose donc sur
    ce journal la, et pas sur la racine.
    """
    access = logging.getLogger("uvicorn.access")
    already_filtered = any(isinstance(existing, DropHealthcheckAccessLogs) for existing in access.filters)
    if not already_filtered:
        access.addFilter(DropHealthcheckAccessLogs())


def configure_logging(level: str) -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(level.upper())
    silence_healthcheck_access_logs()
