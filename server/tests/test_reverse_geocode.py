"""Le relais de geocodage, eprouve sans reseau ni base de donnees."""

from __future__ import annotations

import asyncio

import pytest

from app.geocoding import ReverseGeocoder, format_address


REPONSE_PARIS = {
    "display_name": "12, Rue de la Paix, 2e Arrondissement, Paris, Ile-de-France, 75002, France",
    "address": {
        "house_number": "12",
        "road": "Rue de la Paix",
        "city": "Paris",
        "postcode": "75002",
        "state": "Ile-de-France",
        "country": "France",
    },
}


def geocodeur(reponses, **kwargs):
    """Un geocodeur dont la sortie reseau est remplacee par une liste."""
    appels = []

    def fetch(latitude, longitude):
        appels.append((latitude, longitude))
        return reponses.pop(0) if reponses else None

    instance = ReverseGeocoder(
        url="https://exemple.invalide/reverse",
        user_agent="test",
        fetch=fetch,
        min_interval_seconds=0.0,
        **kwargs,
    )
    return instance, appels


def test_l_adresse_tient_en_une_ligne_lisible():
    # Pas la hierarchie administrative complete : une bulle de carte a la
    # place de trois elements, pas de sept.
    assert format_address(REPONSE_PARIS) == "12 Rue de la Paix, Paris, France"


def test_sans_champs_detailles_on_retombe_sur_le_texte_brut():
    assert format_address({"display_name": "Quelque part"}) == "Quelque part"


def test_une_reponse_vide_ne_produit_pas_d_adresse():
    assert format_address(None) is None
    assert format_address({}) is None
    assert format_address({"address": {}}) is None


@pytest.mark.asyncio
async def test_la_meme_position_n_est_demandee_qu_une_fois():
    geo, appels = geocodeur([REPONSE_PARIS])

    premiere = await geo.lookup(48.8566, 2.3522)
    seconde = await geo.lookup(48.8566, 2.3522)

    assert premiere == seconde == "12 Rue de la Paix, Paris, France"
    assert len(appels) == 1


@pytest.mark.asyncio
async def test_deux_points_de_la_meme_rue_partagent_leur_reponse():
    # Le cache arrondit a trois decimales, environ 110 metres : c'est
    # exactement la granularite d'une adresse.
    geo, appels = geocodeur([REPONSE_PARIS])

    await geo.lookup(48.85661, 2.35221)
    await geo.lookup(48.85664, 2.35223)

    assert len(appels) == 1


@pytest.mark.asyncio
async def test_une_position_sans_adresse_n_est_pas_redemandee():
    # Un point en pleine mer relancerait sinon un appel a chaque fois qu'on
    # le touche sur la carte.
    geo, appels = geocodeur([None])

    assert await geo.lookup(0.0, -30.0) is None
    assert await geo.lookup(0.0, -30.0) is None
    assert len(appels) == 1


@pytest.mark.asyncio
async def test_une_panne_reseau_ne_se_met_pas_en_cache():
    # L'echec est passager par nature : le reseau revenu doit suffire.
    appels = []

    def fetch(latitude, longitude):
        appels.append((latitude, longitude))
        if len(appels) == 1:
            raise OSError("reseau coupe")
        return REPONSE_PARIS

    geo = ReverseGeocoder(
        url="https://exemple.invalide/reverse",
        user_agent="test",
        fetch=fetch,
        min_interval_seconds=0.0,
    )

    assert await geo.lookup(48.8566, 2.3522) is None
    assert await geo.lookup(48.8566, 2.3522) == "12 Rue de la Paix, Paris, France"


@pytest.mark.asyncio
async def test_le_cache_oublie_les_plus_anciennes_positions():
    geo, appels = geocodeur([REPONSE_PARIS] * 10, cache_size=2)

    await geo.lookup(1.0, 1.0)
    await geo.lookup(2.0, 2.0)
    await geo.lookup(3.0, 3.0)
    await geo.lookup(1.0, 1.0)

    assert len(appels) == 4


@pytest.mark.asyncio
async def test_les_appels_sortants_respectent_un_espacement_minimal():
    # Politique d'usage de Nominatim : une requete par seconde au plus. Dix
    # bulles ouvertes coup sur coup ne doivent pas partir ensemble.
    geo = ReverseGeocoder(
        url="https://exemple.invalide/reverse",
        user_agent="test",
        fetch=lambda latitude, longitude: REPONSE_PARIS,
        min_interval_seconds=0.05,
    )

    debut = asyncio.get_running_loop().time()
    await asyncio.gather(
        geo.lookup(1.0, 1.0),
        geo.lookup(2.0, 2.0),
        geo.lookup(3.0, 3.0),
    )
    ecoule = asyncio.get_running_loop().time() - debut

    assert ecoule >= 0.10
