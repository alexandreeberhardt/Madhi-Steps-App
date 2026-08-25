"""Choix du pas d'echantillonnage d'un historique.

Module volontairement sans dependance : ni FastAPI, ni asyncpg. La regle qui
decide combien de positions une reponse contient doit pouvoir etre eprouvee
sans lever une base de donnees.
"""

from __future__ import annotations

import math
from datetime import datetime

# Nombre de positions vise par une reponse d'historique. Ce n'est pas un
# plafond qui coupe, mais une cible : la reponse couvre toujours toute la
# periode demandee, quitte a l'espacer.
MAX_HISTORY_POINTS = 10000


def sampling_step_seconds(
    first_at: datetime | None,
    last_at: datetime | None,
    target_points: int,
) -> int:
    """Pas de temps tel que la periode tienne en `target_points` positions.

    Une seconde au minimum : en dessous le pas ne regrouperait rien de plus,
    puisque deux positions ne sont jamais capturees dans la meme seconde, et
    une division par zero attend au tournant.
    """
    if first_at is None or last_at is None or target_points < 1:
        return 1
    span = (last_at - first_at).total_seconds()
    return max(1, math.ceil(span / target_points))
