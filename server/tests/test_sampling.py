"""Le choix du pas d'echantillonnage, eprouve sans base de donnees."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

from app.sampling import MAX_HISTORY_POINTS, sampling_step_seconds

DEPART = datetime(2026, 8, 1, tzinfo=timezone.utc)


def test_une_periode_courte_ne_regroupe_rien():
    # Une journee visee a 10 000 points donne un pas de 9 secondes, bien en
    # dessous de la cadence de capture : aucune position n'est perdue.
    pas = sampling_step_seconds(DEPART, DEPART + timedelta(days=1), MAX_HISTORY_POINTS)

    assert pas < 120


def test_une_annee_tient_dans_la_cible():
    # C'est le cas qui interdisait « tout le voyage » : 105 000 positions pour
    # un plafond de 10 000.
    an = timedelta(days=365)
    pas = sampling_step_seconds(DEPART, DEPART + an, MAX_HISTORY_POINTS)

    points_rendus = an.total_seconds() / pas
    assert points_rendus <= MAX_HISTORY_POINTS
    # Et la reponse reste utile : une position par heure environ.
    assert pas < 3 * 3600


def test_le_pas_grandit_avec_la_periode():
    court = sampling_step_seconds(DEPART, DEPART + timedelta(days=7), MAX_HISTORY_POINTS)
    long_ = sampling_step_seconds(DEPART, DEPART + timedelta(days=365), MAX_HISTORY_POINTS)

    assert court < long_


def test_une_fenetre_vide_ne_divise_pas_par_zero():
    assert sampling_step_seconds(None, None, MAX_HISTORY_POINTS) == 1
    assert sampling_step_seconds(DEPART, None, MAX_HISTORY_POINTS) == 1


def test_un_seul_point_ne_divise_pas_par_zero():
    # Premiere position d'un voyage : debut et fin confondus.
    assert sampling_step_seconds(DEPART, DEPART, MAX_HISTORY_POINTS) == 1


def test_une_cible_absurde_ne_fait_pas_tomber_le_serveur():
    assert sampling_step_seconds(DEPART, DEPART + timedelta(days=7), 0) == 1
    assert sampling_step_seconds(DEPART, DEPART + timedelta(days=7), -1) == 1


def test_le_pas_ne_descend_jamais_sous_la_seconde():
    # Deux positions ne sont jamais capturees dans la meme seconde : un pas
    # plus fin ne regrouperait rien et diviserait par zero en SQL.
    assert sampling_step_seconds(DEPART, DEPART + timedelta(seconds=1), MAX_HISTORY_POINTS) == 1
