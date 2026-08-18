package com.madhi.tracker.presentation.common

import java.time.Duration
import java.time.Instant

/**
 * « il y a 3 min » plutôt qu'un horodatage.
 *
 * `arch/09` §2 insiste sur ce point : afficher une heure exacte laisserait
 * croire à du temps réel. L'ancienneté dit ce qui compte — le suivi
 * fonctionne-t-il maintenant.
 */
fun relativeAge(instant: Instant?, now: Instant): String {
    if (instant == null) return "aucune"

    val minutes = Duration.between(instant, now).toMinutes()
    return when {
        minutes < 0 -> "à l'instant"
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 60 * 24 -> "il y a ${minutes / 60} h"
        else -> "il y a ${minutes / (60 * 24)} j"
    }
}
