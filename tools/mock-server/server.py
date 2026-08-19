#!/usr/bin/env python3
"""Serveur de simulation implementant arch/13_contrat_api_android_v1.md.

Outil de developpement uniquement. Il n'a ni chiffrement, ni persistance
durable, ni controle d'acces serieux : ne jamais l'exposer sur Internet.

Son role est double. Il permet de valider la chaine complete de
l'application avant que le serveur reel existe, et il sert de specification
executable pour ce serveur : ce qui est teste ici est ce que le serveur POC
devra reproduire.

Zero dependance, bibliotheque standard seulement.
"""

from __future__ import annotations

import argparse
import functools
import json
import os
import re
import secrets
import threading
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

API_PREFIX = "/api/v1"

# Sortie non tamponnee : on veut voir les lots arriver en direct pendant un
# test, pas les decouvrir en bloc a l'arret du serveur.
print = functools.partial(print, flush=True)  # noqa: A001

ISO_INSTANT = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$")

REQUIRED_POINT_FIELDS = ("id", "deviceId", "latitude", "longitude", "recordedAt")


class Store:
    """Etat du serveur simule.

    Le verrou protege les acces concurrents : l'application peut envoyer un
    lot pendant qu'un autre est en cours de traitement, et c'est justement
    un des cas qu'on veut pouvoir reproduire.
    """

    def __init__(self, activation_code: str) -> None:
        self._lock = threading.Lock()
        self.activation_code = activation_code
        self.code_used = False
        self.tokens: dict[str, str] = {}
        self.locations: dict[str, dict] = {}
        # Reponses d'erreur a servir avant de reprendre le comportement
        # normal, pour eprouver la gestion d'erreur du client.
        self.forced_failures: list[int] = []
        self.batch_count = 0

    def activate(self, code: str, device_name: str) -> dict | None:
        with self._lock:
            # Le contrat dit : usage unique, expiration rapide. On simule
            # l'usage unique, qui est le cas que le client doit gerer.
            if code != self.activation_code or self.code_used:
                return None
            self.code_used = True
            device_id = f"device-{secrets.token_hex(4)}"
            token = secrets.token_urlsafe(32)
            self.tokens[token] = device_id
            print(f"  activation acceptee : {device_name} -> {device_id}")
            return {
                "deviceId": device_id,
                "deviceToken": token,
                "tripId": "trip-madhi-2026",
            }

    def device_for(self, token: str | None) -> str | None:
        if token is None:
            return None
        with self._lock:
            return self.tokens.get(token)

    def next_forced_failure(self) -> int | None:
        with self._lock:
            return self.forced_failures.pop(0) if self.forced_failures else None

    def ingest(self, points: list[dict]) -> dict:
        accepted, duplicates, rejected = [], [], []
        with self._lock:
            self.batch_count += 1
            for point in points:
                reason = validation_error(point)
                if reason:
                    rejected.append({"id": point.get("id", "?"), "reason": reason})
                    continue
                # Le coeur de l'idempotence : un identifiant deja connu
                # revient en duplicates, jamais en erreur, et n'est pas
                # reecrit. Un lot rejoue apres une reponse perdue doit
                # aboutir sans creer de doublon.
                if point["id"] in self.locations:
                    duplicates.append(point["id"])
                    continue
                stored = dict(point)
                stored["receivedAt"] = now_iso()
                self.locations[point["id"]] = stored
                accepted.append(point["id"])
        return {"accepted": accepted, "duplicates": duplicates, "rejected": rejected}


def validation_error(point: dict) -> str | None:
    for field in REQUIRED_POINT_FIELDS:
        if field not in point:
            return f"missing_{field}"
    try:
        latitude = float(point["latitude"])
        longitude = float(point["longitude"])
    except (TypeError, ValueError):
        return "invalid_coordinates"
    if not (-90.0 <= latitude <= 90.0) or not (-180.0 <= longitude <= 180.0):
        return "invalid_coordinates"
    if not ISO_INSTANT.match(str(point["recordedAt"])):
        return "invalid_recorded_at"
    return None


def now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class Handler(BaseHTTPRequestHandler):
    store: Store
    max_batch: int

    protocol_version = "HTTP/1.1"

    def do_POST(self) -> None:  # noqa: N802 - impose par BaseHTTPRequestHandler
        if self.path == f"{API_PREFIX}/devices/activate":
            self.handle_activate()
        elif self.path == f"{API_PREFIX}/locations/batch":
            self.handle_batch()
        elif self.path.startswith("/_control/fail"):
            self.handle_force_failure()
        else:
            self.respond(404, {"error": "unknown_endpoint"})

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/_control/state":
            self.respond(200, self.state_summary())
        else:
            self.respond(404, {"error": "unknown_endpoint"})

    def handle_activate(self) -> None:
        body = self.read_json()
        if body is None:
            return
        code = str(body.get("activationCode", "")).strip()
        activation = self.store.activate(code, str(body.get("deviceName", "?")))
        if activation is None:
            # 410 et non 400 : le contrat distingue un code malforme d'un
            # code perime ou deja utilise, et le client affiche deux
            # messages differents.
            self.respond(410, {"error": "expired_or_unknown_code"})
            return
        self.respond(200, activation)

    def handle_batch(self) -> None:
        forced = self.store.next_forced_failure()
        if forced is not None:
            print(f"  echec force : HTTP {forced}")
            headers = {"Retry-After": "5"} if forced == 429 else None
            self.respond(forced, {"error": "forced_failure"}, extra_headers=headers)
            return

        token = self.bearer_token()
        device_id = self.store.device_for(token)
        if device_id is None:
            self.respond(401, {"error": "unauthorized"})
            return

        body = self.read_json()
        if body is None:
            return

        points = body.get("points")
        if not isinstance(points, list):
            self.respond(400, {"error": "points_must_be_a_list"})
            return

        if len(points) > self.max_batch:
            self.respond(413, {"error": "batch_too_large", "maxPoints": self.max_batch})
            return

        result = self.store.ingest(points)
        print(
            f"  lot #{self.store.batch_count} : {len(result['accepted'])} nouveaux, "
            f"{len(result['duplicates'])} deja connus, {len(result['rejected'])} refuses "
            f"(total en base : {len(self.store.locations)})"
        )
        self.respond(200, result)

    def handle_force_failure(self) -> None:
        """Injecte des erreurs pour eprouver la gestion d'erreur du client."""
        query = self.path.split("?", 1)[1] if "?" in self.path else ""
        params = dict(pair.split("=", 1) for pair in query.split("&") if "=" in pair)
        code = int(params.get("code", 500))
        times = int(params.get("times", 1))
        self.store.forced_failures.extend([code] * times)
        self.respond(200, {"queued": [code] * times})

    def state_summary(self) -> dict:
        locations = list(self.store.locations.values())
        recorded = sorted(point["recordedAt"] for point in locations)
        return {
            "activated": self.store.code_used,
            "devices": len(self.store.tokens),
            "locations": len(locations),
            "batches": self.store.batch_count,
            "firstRecordedAt": recorded[0] if recorded else None,
            "lastRecordedAt": recorded[-1] if recorded else None,
            "pendingForcedFailures": list(self.store.forced_failures),
        }

    def bearer_token(self) -> str | None:
        header = self.headers.get("Authorization", "")
        return header[len("Bearer "):] if header.startswith("Bearer ") else None

    def read_json(self) -> dict | None:
        length = int(self.headers.get("Content-Length", 0))
        try:
            return json.loads(self.rfile.read(length) or b"{}")
        except json.JSONDecodeError:
            self.respond(400, {"error": "invalid_json"})
            return None

    def respond(self, status: int, payload: dict, extra_headers: dict | None = None) -> None:
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        for key, value in (extra_headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt: str, *args) -> None:
        """Journal reduit : les lignes utiles sont imprimees explicitement."""
        return


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default=os.environ.get("MOCK_SERVER_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("MOCK_SERVER_PORT", "8080")))
    parser.add_argument(
        "--code",
        default=os.environ.get("MOCK_SERVER_ACTIVATION_CODE"),
        help="code d'activation (aleatoire par defaut)",
    )
    parser.add_argument(
        "--max-batch",
        type=int,
        default=int(os.environ.get("MOCK_SERVER_MAX_BATCH", "200")),
        help="au-dela, repondre 413",
    )
    args = parser.parse_args()

    code = args.code or f"{secrets.token_hex(2).upper()}-{secrets.token_hex(2).upper()}"
    Handler.store = Store(code)
    Handler.max_batch = args.max_batch

    print("Serveur de simulation Madhi Tracker — developpement uniquement")
    print(f"  ecoute        http://{args.host}:{args.port}{API_PREFIX}")
    print(f"  code d'activation : {code}")
    print()
    print("Depuis un telephone branche en USB :")
    print(f"  adb reverse tcp:{args.port} tcp:{args.port}")
    print(f"  puis configurer madhi.api.baseUrl.debug=http://localhost:{args.port}{API_PREFIX}")
    print()

    ThreadingHTTPServer((args.host, args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
