# Documentation

These pages describe the system **as it is implemented**. They are written
from the code, and each claim points to the file that backs it. When the code
and a page disagree, the code is right and the page needs fixing.

- [Android app](android-app.md): screens, how tracking keeps running, local
  storage, synchronisation, recovery after a reboot or an update, and the map.
- [Server API](api.md): endpoints, authentication, limits, error codes, and
  how history is sampled.
- [Websites](websites.md): the family website and the delayed public website.

Operations live next to the code they operate:

- `SERVER_DEPLOYMENT.md`: installing and running the VPS.
- `site/README.md`: developing, deploying, and checking the family website.
- `tools/public-site/README.md`: the public website and its export job.
- `tools/monitoring/README.md`, `tools/backup/README.md`: monitoring and backups.
- `tools/tiles/README.md`: the self-hosted base map.
- `tools/mock-server/README.md`: the local simulation server.

The `arch/` directory is the design archive: the specifications written before
and during development, the ADRs, the field-test protocol and its log, and the
departure procedure. It explains why some choices were made, but it is not a
description of the current behaviour.

## The system in one picture

    Phone (Android app)
      │  records a location, writes it to Room first
      │  POST /api/v1/locations/batch      (device token)
      ▼
    VPS: nginx ─► FastAPI ─► PostgreSQL
      ▲                         │
      │  /f/<secret>/api/...    │  read-only role, views delayed by 168 h
      │  (password + read token │
      │   added by nginx)       ▼
    Family website         Public export job ─► static JSON ─► Public website

The phone is the source of truth until the server confirms a point. The server
is the source of truth for both websites. The public website never talks to
the API: it reads static files produced by a separate job with its own
database role.
