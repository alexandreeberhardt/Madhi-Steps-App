package com.madhi.tracker.application.port

import com.madhi.tracker.domain.model.TrackerEvent

/**
 * Journal d'événements techniques.
 *
 * Ce port existe pour deux raisons. La première est banale : `android.util.Log`
 * n'a pas sa place dans un use case. La seconde l'est moins — sa signature
 * n'accepte **aucune coordonnée**. Il est structurellement impossible
 * d'écrire une latitude dans les logs en passant par ici, ce qui vaut mieux
 * qu'une consigne que l'on finit par oublier (`arch/01` §4).
 */
interface EventLog {
    fun record(event: TrackerEvent, detail: String? = null)
}
