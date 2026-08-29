from __future__ import annotations

from types import SimpleNamespace

import pytest

from app.config import Settings, validate_settings
from app.rate_limit import real_client_ip
from app.security import activation_code_malformed, hash_secret


def production_settings(**overrides) -> Settings:
    values = {
        "app_env": "production",
        "database_url": "postgresql://madhi:real_password_without_placeholder@postgres:5432/madhi_tracker",
        "device_token_hash_secret": "x" * 48,
        "activation_code_hash_secret": "y" * 48,
        "initial_trip_id": "8f14e45f-ceea-467a-9f4e-2b1c9a1a1a1a",
        "initial_trip_name": "Madhi 2026",
        "initial_activation_code": "ABCD-1234",
        "activation_code_ttl_minutes": 15,
        "max_batch_points": 200,
        "max_request_bytes": 1048576,
        "rate_limit_per_minute": 120,
        "public_read_token": "z" * 48,
        "log_level": "INFO",
    }
    values.update(overrides)
    return Settings(**values)


def test_le_geocodage_exige_une_identite_reelle():
    # Nominatim refuse le trafic anonyme, et il a raison : c'est un bien
    # commun. Mieux vaut echouer au demarrage qu'etre bloque en voyage.
    settings = production_settings(reverse_geocode_enabled=True, reverse_geocode_user_agent="")

    with pytest.raises(RuntimeError, match="REVERSE_GEOCODE_USER_AGENT"):
        validate_settings(settings)


def test_le_geocodage_refuse_une_adresse_en_clair():
    settings = production_settings(
        reverse_geocode_enabled=True,
        reverse_geocode_user_agent="Madhi Tracker (contact@exemple.fr)",
        reverse_geocode_url="http://nominatim.openstreetmap.org/reverse",
    )

    with pytest.raises(RuntimeError, match="REVERSE_GEOCODE_URL"):
        validate_settings(settings)


def test_le_geocodage_eteint_n_exige_rien():
    # Le defaut : un deploiement qui ne touche pas a cette option reste
    # valide, et aucune coordonnee ne sort du VPS.
    validate_settings(production_settings())


def test_production_rejects_example_secret():
    settings = production_settings(device_token_hash_secret="dev-secret-change-me")

    with pytest.raises(RuntimeError, match="DEVICE_TOKEN_HASH_SECRET"):
        validate_settings(settings)


def test_production_rejects_short_read_token():
    settings = production_settings(public_read_token="too-short")

    with pytest.raises(RuntimeError, match="PUBLIC_READ_TOKEN"):
        validate_settings(settings)


def test_production_accepts_non_placeholder_configuration():
    validate_settings(production_settings())


def test_hash_secret_uses_pepper():
    first = hash_secret("device-token", "pepper-a")
    second = hash_secret("device-token", "pepper-b")

    assert first != "device-token"
    assert first != second


@pytest.mark.parametrize(
    ("code", "malformed"),
    [
        ("ABCD-1234", False),
        ("abcd-1234", False),
        ("ABC-1234", True),
        ("ABCD1234", True),
        ("ABCD-12345", True),
    ],
)
def test_activation_code_format(code: str, malformed: bool):
    assert activation_code_malformed(code) is malformed


def test_real_client_ip_prefers_x_real_ip():
    request = SimpleNamespace(
        headers={"X-Real-IP": "203.0.113.10", "X-Forwarded-For": "198.51.100.20"},
        client=SimpleNamespace(host="172.18.0.2"),
    )

    assert real_client_ip(request) == "203.0.113.10"


def test_real_client_ip_ignores_client_forgeable_headers():
    # Sans `X-Real-IP`, un client qui envoie ces en-tetes ne doit pas pouvoir
    # choisir son compartiment de rate limiting.
    request = SimpleNamespace(
        headers={"Forwarded": 'for="203.0.113.10"', "X-Forwarded-For": "198.51.100.20"},
        client=SimpleNamespace(host="172.18.0.2"),
    )

    assert real_client_ip(request) == "172.18.0.2"
