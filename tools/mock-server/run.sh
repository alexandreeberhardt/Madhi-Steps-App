#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"

set -a
. "$ROOT_DIR/.env"
set +a

exec python3 "$ROOT_DIR/tools/mock-server/server.py"
