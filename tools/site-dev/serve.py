#!/usr/bin/env python3
"""Serveur de developpement du site familial.

Outil de developpement uniquement. Il tient le role de nginx sur le VPS : il
sert les fichiers de `site/` sous un chemin secret et repond aux appels API
relatifs. Deux modes :

- **scenarios** (par defaut) : les reponses sont fabriquees ici, pour eprouver
  chacun des huit etats de `arch/17_plan_implementation_site_poc.md` §6 sans
  avoir a vider une base ni a arreter un serveur.
- **proxy** : les appels sont relayes vers le vrai serveur, avec l'en-tete
  `Authorization` pose ici comme nginx le fera. C'est ce mode qui verifie que
  le site n'a besoin d'aucun secret pour fonctionner.

Ne jamais l'exposer sur Internet : ni chiffrement, ni mot de passe.

Zero dependance, bibliotheque standard seulement.
"""

from __future__ import annotations

import argparse
import functools
import json
import mimetypes
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

RACINE_SITE = Path(__file__).resolve().parents[2] / "site"

# Le meme decoupage que la configuration nginx : un segment secret, les fichiers
# statiques dessous, l'API dessous encore.
SEGMENT = "dev"
PREFIXE = f"/f/{SEGMENT}/"
PREFIXE_API = f"{PREFIXE}api/"

# Recopiee de tools/nginx/madhi.alexeber.fr : si les deux divergent, une
# violation n'apparaitrait qu'en production.
CSP = (
    "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
    "img-src 'self' data: https://tile.openstreetmap.org; connect-src 'self'; "
    "base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
)

TRIP_ID = "8f14e45f-ceea-467a-9f4e-2b1c9a1a1a1a"
NOM_VOYAGE = "Madhi 2026"

# Une position toutes les cinq minutes, comme le telephone.
INTERVALLE_CAPTURE = timedelta(minutes=5)
LIMITE_POINTS = 10000

print = functools.partial(print, flush=True)  # noqa: A001

SCENARIOS = (
    "nominal",
    "ancien",
    "hors-ligne",
    "avant-depart",
    "aucune-position",
    "termine",
    "historique-vide",
    "tronque",
    "panne",
    "interdit",
    "voyage-inconnu",
    "muet",
)


def iso(instant: datetime) -> str:
    return instant.astimezone(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def fabriquer_points(fin: datetime, nombre: int, retard_reception: timedelta) -> list[dict]:
    """Une trace plausible : un cycliste qui remonte vers le nord."""
    points = []
    for index in range(nombre):
        capture = fin - INTERVALLE_CAPTURE * (nombre - 1 - index)
        points.append(
            {
                "id": f"00000000-0000-4000-8000-{index:012d}",
                "deviceId": "11111111-1111-4111-8111-111111111111",
                "latitude": 48.8566 + index * 0.0012,
                "longitude": 2.3522 + index * 0.0004,
                "recordedAt": iso(capture),
                "receivedAt": iso(capture + retard_reception),
                "accuracyMeters": 12.0,
                "altitudeMeters": 80.0,
                "speedMps": 4.2,
                "batteryPercent": max(5, 95 - index // 40),
            }
        )
    return points


class Scenario:
    """Les trois reponses de lecture, pour un etat donne du voyage."""

    def __init__(self, nom: str) -> None:
        self.nom = nom
        self.maintenant = datetime.now(timezone.utc)

    def statut(self) -> dict:
        depart = self.maintenant - timedelta(days=40)
        points = self.points_du_voyage()
        return {
            "tripId": TRIP_ID,
            "name": NOM_VOYAGE,
            "startedAt": None if self.nom == "avant-depart" else iso(depart),
            "endedAt": iso(self.maintenant - timedelta(days=1)) if self.nom == "termine" else None,
            "totalLocations": len(points),
            "latestRecordedAt": points[-1]["recordedAt"] if points else None,
            "latestReceivedAt": points[-1]["receivedAt"] if points else None,
        }

    def points_du_voyage(self) -> list[dict]:
        """L'historique complet simule, dans lequel la fenetre viendra tailler."""
        if self.nom in ("avant-depart", "aucune-position"):
            return []
        if self.nom == "tronque":
            # Assez de points pour que la fenetre de 30 jours depasse le plafond.
            return fabriquer_points(self.maintenant, LIMITE_POINTS + 500, timedelta(seconds=20))
        if self.nom == "historique-vide":
            # Une position recente, mais rien dans les jours qui precedent : le
            # telephone vient d'etre rallume apres une longue coupure.
            return fabriquer_points(self.maintenant - timedelta(minutes=4), 1, timedelta(seconds=30))

        decalage = {
            "nominal": timedelta(minutes=6),
            "ancien": timedelta(hours=6),
            "hors-ligne": timedelta(days=3),
            "termine": timedelta(days=1),
        }.get(self.nom, timedelta(minutes=6))
        # Une journee de trajet, soit 288 points : le cas que le site doit
        # afficher sans saccade.
        retard = timedelta(hours=2) if self.nom == "hors-ligne" else timedelta(seconds=25)
        return fabriquer_points(self.maintenant - decalage, 288, retard)

    def derniere_position(self) -> dict | None:
        points = self.points_du_voyage()
        return points[-1] if points else None

    def historique(self, depuis: datetime | None, jusqua: datetime | None, limite: int) -> list[dict]:
        points = self.points_du_voyage()
        if self.nom == "historique-vide":
            # La derniere position existe, mais elle est hors de toute periode
            # demandee : c'est l'etat HISTORIQUE_VIDE, a ne pas confondre avec
            # « aucune position recue ».
            return []
        if self.nom == "tronque":
            # Le vrai serveur coupe a `limite` quelle que soit la fenetre : on
            # reproduit le cas ou la reponse fait exactement la taille du
            # plafond, seul indice dont dispose le site.
            return points[:limite]
        retenus = [
            point
            for point in points
            if (depuis is None or point["recordedAt"] >= iso(depuis))
            and (jusqua is None or point["recordedAt"] <= iso(jusqua))
        ]
        # Le vrai serveur trie du plus ancien au plus recent, puis coupe : ce
        # sont les points les plus recents qui disparaissent.
        return retenus[:limite]


class Handler(BaseHTTPRequestHandler):
    scenario_nom = "nominal"
    proxy_base: str | None = None
    proxy_token: str | None = None
    # Nombre d'appels servis avant de tomber en panne, pour eprouver le cas ou
    # le serveur s'arrete pendant que la famille regarde la page.
    panne_apres: int | None = None
    appels = 0

    def do_GET(self) -> None:  # noqa: N802 - impose par BaseHTTPRequestHandler
        chemin, _, requete = self.path.partition("?")

        if chemin in ("/", "/f", f"/f/{SEGMENT}"):
            self.send_response(302)
            self.send_header("Location", PREFIXE)
            self.end_headers()
            return

        if chemin.startswith(PREFIXE_API):
            self.repondre_api(chemin[len(PREFIXE_API) :], requete)
            return

        if chemin.startswith(PREFIXE):
            self.servir_fichier(chemin[len(PREFIXE) :])
            return

        self.envoyer_json(404, {"error": "not_found"})

    # -- API ---------------------------------------------------------------

    def repondre_api(self, reste: str, requete: str) -> None:
        if self.proxy_base is not None:
            self.relayer(reste, requete)
            return

        Handler.appels += 1
        if self.panne_apres is not None and Handler.appels > self.panne_apres:
            self.envoyer_json(502, {"error": "bad_gateway"})
            return

        scenario = Scenario(self.scenario_nom)

        if scenario.nom == "muet":
            # Ne rien repondre : c'est le cas que le delai maximum du client
            # doit rattraper.
            time.sleep(30)
            return
        if scenario.nom == "panne":
            self.envoyer_json(502, {"error": "bad_gateway"})
            return
        if scenario.nom == "interdit":
            self.envoyer_json(403, {"error": "forbidden"})
            return
        if scenario.nom == "voyage-inconnu":
            self.envoyer_json(404, {"error": "unknown_trip"})
            return

        parametres = dict(
            paire.split("=", 1) for paire in requete.split("&") if "=" in paire
        )

        if reste.endswith("/status"):
            self.envoyer_json(200, scenario.statut())
            return
        if reste.endswith("/latest-location"):
            self.envoyer_json(200, scenario.derniere_position())
            return
        if reste.endswith("/locations"):
            depuis = self.lire_instant(parametres.get("from"))
            jusqua = self.lire_instant(parametres.get("to"))
            limite = int(parametres.get("limit", LIMITE_POINTS))
            self.envoyer_json(200, scenario.historique(depuis, jusqua, limite))
            return

        self.envoyer_json(404, {"error": "not_found"})

    def relayer(self, reste: str, requete: str) -> None:
        url = f"{self.proxy_base}/api/v1/{reste}"
        if requete:
            url = f"{url}?{requete}"
        demande = urllib.request.Request(url, headers={"Accept": "application/json"})
        if self.proxy_token:
            # Exactement ce que fait nginx : le token est pose ici, jamais dans
            # les fichiers servis au navigateur.
            demande.add_header("Authorization", f"Bearer {self.proxy_token}")
        try:
            with urllib.request.urlopen(demande, timeout=15) as reponse:
                corps = reponse.read()
                self.envoyer_octets(reponse.status, corps, "application/json")
        except urllib.error.HTTPError as erreur:
            self.envoyer_octets(erreur.code, erreur.read(), "application/json")
        except urllib.error.URLError as erreur:
            self.envoyer_json(502, {"error": "proxy_unreachable", "detail": str(erreur.reason)})

    @staticmethod
    def lire_instant(valeur: str | None) -> datetime | None:
        if not valeur:
            return None
        texte = urllib.parse.unquote(valeur)
        if not texte.endswith("Z"):
            return None
        return datetime.fromisoformat(texte.removesuffix("Z") + "+00:00")

    # -- fichiers statiques ------------------------------------------------

    def servir_fichier(self, relatif: str) -> None:
        relatif = relatif or "index.html"
        cible = (RACINE_SITE / relatif).resolve()
        if not cible.is_relative_to(RACINE_SITE) or not cible.is_file():
            self.envoyer_json(404, {"error": "not_found"})
            return
        type_mime, _ = mimetypes.guess_type(str(cible))
        self.envoyer_octets(200, cible.read_bytes(), type_mime or "application/octet-stream")

    # -- envoi -------------------------------------------------------------

    def envoyer_json(self, statut: int, charge) -> None:
        self.envoyer_octets(statut, json.dumps(charge).encode("utf-8"), "application/json")

    def envoyer_octets(self, statut: int, corps: bytes, type_mime: str) -> None:
        self.send_response(statut)
        self.send_header("Content-Type", type_mime)
        self.send_header("Content-Length", str(len(corps)))
        self.send_header("Cache-Control", "no-store")
        # Les memes en-tetes que nginx, pour que le lien secret ne parte pas
        # vers le serveur de tuiles.
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("X-Robots-Tag", "noindex, nofollow")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Security-Policy", CSP)
        self.end_headers()
        self.wfile.write(corps)

    def log_message(self, fmt: str, *args) -> None:
        print(f"{self.address_string()} {fmt % args}")


def main() -> None:
    analyseur = argparse.ArgumentParser(description=__doc__)
    analyseur.add_argument("--port", type=int, default=8090)
    analyseur.add_argument("--scenario", choices=SCENARIOS, default="nominal")
    analyseur.add_argument(
        "--panne-apres",
        type=int,
        help="tomber en panne apres N appels API servis",
    )
    analyseur.add_argument(
        "--proxy",
        help="relayer vers un vrai serveur, par exemple https://madhi-server.alexeber.fr",
    )
    analyseur.add_argument(
        "--token",
        default=os.environ.get("PUBLIC_READ_TOKEN"),
        help="token de lecture, par defaut la variable d'environnement PUBLIC_READ_TOKEN",
    )
    options = analyseur.parse_args()

    Handler.scenario_nom = options.scenario
    Handler.panne_apres = options.panne_apres
    Handler.proxy_base = options.proxy.rstrip("/") if options.proxy else None
    Handler.proxy_token = options.token

    serveur = ThreadingHTTPServer(("127.0.0.1", options.port), Handler)
    adresse = f"http://127.0.0.1:{options.port}{PREFIXE}"
    if Handler.proxy_base:
        print(f"Site sur {adresse} — API relayee vers {Handler.proxy_base}")
        if not Handler.proxy_token:
            print("Aucun token : le serveur reel repondra 403.")
    else:
        print(f"Site sur {adresse} — scenario « {options.scenario} »")
    try:
        serveur.serve_forever()
    except KeyboardInterrupt:
        print("arret")


if __name__ == "__main__":
    main()
