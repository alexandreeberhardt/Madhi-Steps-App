from __future__ import annotations

import logging

from app.logging_config import (
    DropHealthcheckAccessLogs,
    silence_healthcheck_access_logs,
)


def access_record(path: str) -> logging.LogRecord:
    """Reproduit un enregistrement du journal d'acces d'uvicorn.

    Uvicorn formate `'%s - "%s %s HTTP/%s" %d'` et passe le chemin en
    troisieme argument.
    """
    return logging.LogRecord(
        name="uvicorn.access",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg='%s - "%s %s HTTP/%s" %d',
        args=("127.0.0.1:53124", "GET", path, "1.1", 200),
        exc_info=None,
    )


def test_healthcheck_access_is_dropped():
    assert DropHealthcheckAccessLogs().filter(access_record("/health")) is False


def test_application_access_is_kept():
    record = access_record("/api/v1/locations/batch")

    assert DropHealthcheckAccessLogs().filter(record) is True


def test_record_without_uvicorn_arguments_is_kept():
    record = logging.LogRecord(
        name="madhi.server",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg="server_started",
        args=None,
        exc_info=None,
    )

    assert DropHealthcheckAccessLogs().filter(record) is True


def test_filter_is_installed_once():
    access = logging.getLogger("uvicorn.access")
    original = list(access.filters)
    try:
        access.filters.clear()

        silence_healthcheck_access_logs()
        silence_healthcheck_access_logs()

        installed = [f for f in access.filters if isinstance(f, DropHealthcheckAccessLogs)]
        assert len(installed) == 1
    finally:
        access.filters.clear()
        access.filters.extend(original)
