"""Trouver l'adresse d'un point, cote serveur et jamais cote telephone.

Le telephone ne parle qu'au serveur du voyage. C'est la contrainte du projet,
et elle vaut ici plus qu'ailleurs : une adresse demandee depuis le telephone
livrerait a un tiers la position exacte et l'adresse IP du reseau mobile
traverse, a chaque ouverture de bulle. Le VPS, lui, est deja connu, et ce
qu'il expose est une seule adresse IP fixe pour tout le voyage.

Trois garde-fous ici :

- un cache, parce que la meme position sera redemandee a chaque fois qu'on
  reviendra sur ce point de la carte ;
- un espacement d'au moins une seconde entre deux appels sortants, exige par
  la politique d'usage de Nominatim ;
- aucune coordonnee dans les journaux, comme le promet l'ecran de reglages de
  l'application.
"""

from __future__ import annotations

import asyncio
import json
import logging
import time
import urllib.parse
import urllib.request
from collections import OrderedDict
from typing import Callable

logger = logging.getLogger("madhi.server")

# Trois decimales : environ 110 metres. Deux points d'une meme rue partagent
# leur reponse, ce qui est exactement ce qu'on veut d'un cache d'adresses.
CACHE_PRECISION_DECIMALS = 3

# Nominatim demande au moins une seconde entre deux requetes, et une identite
# reelle dans l'en-tete User-Agent. Les deux sont des conditions d'usage, pas
# des optimisations.
MIN_INTERVAL_SECONDS = 1.0

DEFAULT_TIMEOUT_SECONDS = 6.0
DEFAULT_CACHE_SIZE = 1024

# Le niveau de detail demande : la rue et la commune, pas le batiment.
DETAIL_ZOOM = 17


class ReverseGeocoder:
    """Adresse d'une position, en cache et a debit borne."""

    def __init__(
        self,
        url: str,
        user_agent: str,
        fetch: Callable[[float, float], dict | None] | None = None,
        cache_size: int = DEFAULT_CACHE_SIZE,
        min_interval_seconds: float = MIN_INTERVAL_SECONDS,
        timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
    ) -> None:
        self._url = url
        self._user_agent = user_agent
        self._fetch = fetch or self._fetch_over_http
        self._cache: OrderedDict[tuple[float, float], str | None] = OrderedDict()
        self._cache_size = cache_size
        self._min_interval = min_interval_seconds
        self._timeout = timeout_seconds
        self._lock = asyncio.Lock()
        self._last_call_at = 0.0

    async def lookup(self, latitude: float, longitude: float) -> str | None:
        """L'adresse, ou `None` si personne ne sait la dire."""
        key = self._cache_key(latitude, longitude)
        if key in self._cache:
            self._cache.move_to_end(key)
            logger.info("reverse_geocode_cache_hit")
            return self._cache[key]

        # Le verrou serialise les appels sortants : sans lui, dix bulles
        # ouvertes coup sur coup partiraient ensemble et se feraient refuser.
        async with self._lock:
            if key in self._cache:
                self._cache.move_to_end(key)
                return self._cache[key]

            await self._wait_for_turn()
            try:
                payload = await asyncio.to_thread(self._fetch, key[0], key[1])
            except Exception:
                # Une adresse absente n'est pas une panne : la carte affiche
                # les coordonnees. On ne met pas l'echec en cache, pour que le
                # reseau revenu suffise a reessayer.
                logger.warning("reverse_geocode_failed")
                return None

            address = format_address(payload)
            self._remember(key, address)
            logger.info("reverse_geocode_resolved" if address else "reverse_geocode_empty")
            return address

    async def _wait_for_turn(self) -> None:
        elapsed = time.monotonic() - self._last_call_at
        if elapsed < self._min_interval:
            await asyncio.sleep(self._min_interval - elapsed)
        self._last_call_at = time.monotonic()

    def _remember(self, key: tuple[float, float], address: str | None) -> None:
        # Une absence de reponse se retient aussi : sinon un point en pleine
        # mer relancerait un appel a chaque fois qu'on le touche.
        self._cache[key] = address
        self._cache.move_to_end(key)
        while len(self._cache) > self._cache_size:
            self._cache.popitem(last=False)

    def _cache_key(self, latitude: float, longitude: float) -> tuple[float, float]:
        return (
            round(latitude, CACHE_PRECISION_DECIMALS),
            round(longitude, CACHE_PRECISION_DECIMALS),
        )

    def _fetch_over_http(self, latitude: float, longitude: float) -> dict | None:
        query = urllib.parse.urlencode(
            {
                "format": "jsonv2",
                "lat": f"{latitude}",
                "lon": f"{longitude}",
                "zoom": DETAIL_ZOOM,
                "addressdetails": 1,
            }
        )
        request = urllib.request.Request(
            f"{self._url}?{query}",
            headers={"User-Agent": self._user_agent, "Accept": "application/json"},
        )
        with urllib.request.urlopen(request, timeout=self._timeout) as response:
            return json.loads(response.read().decode("utf-8"))


def format_address(payload: dict | None) -> str | None:
    """Une ligne lisible sur un telephone, pas la hierarchie administrative.

    Nominatim renvoie « 12, Rue de la Paix, 1er Arrondissement, Paris, Ile-de-France,
    75002, France » la ou une bulle de carte a la place de trois elements. On
    reconstruit donc a partir des champs plutot que de couper le texte tout
    fait, qui n'a pas la meme forme d'un pays a l'autre.
    """
    if not isinstance(payload, dict):
        return None

    address = payload.get("address")
    if not isinstance(address, dict):
        display = payload.get("display_name")
        return display if isinstance(display, str) and display else None

    rue = _first(address, ("road", "pedestrian", "footway", "hamlet", "neighbourhood"))
    numero = address.get("house_number")
    if rue and isinstance(numero, str):
        rue = f"{numero} {rue}"

    commune = _first(address, ("village", "town", "city", "municipality", "county"))
    pays = address.get("country")

    morceaux = [part for part in (rue, commune, pays) if isinstance(part, str) and part]
    if morceaux:
        return ", ".join(morceaux)

    display = payload.get("display_name")
    return display if isinstance(display, str) and display else None


def _first(address: dict, keys: tuple[str, ...]) -> str | None:
    for key in keys:
        value = address.get(key)
        if isinstance(value, str) and value:
            return value
    return None
