package com.madhi.tracker.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * La cadence de capture, en minutes.
 *
 * C'était une liste fermée (`arch/01` §2, `arch/09` §5) : une saisie libre
 * exposait la batterie à une erreur de frappe qu'on ne pourrait pas corriger à
 * distance pendant le voyage. Elle s'ouvre à la demande, mais le garde-fou ne
 * disparaît pas, il change de nature : ce sont maintenant les bornes qui le
 * portent.
 *
 * Trois paliers couvrent l'usage courant et se choisissent d'un geste. Tout le
 * reste passe par « Autre », c'est-à-dire par une saisie délibérée.
 */
@JvmInline
value class CaptureInterval private constructor(val minutes: Int) {

    val duration: Duration get() = minutes.minutes

    /** Vrai pour une cadence proposée d'un geste, fausse pour une saisie. */
    val isPreset: Boolean get() = this in PRESETS

    companion object {
        /** En dessous d'une minute, une prise n'a pas le temps d'aboutir. */
        const val MIN_MINUTES = 1

        /** Au-delà d'un jour, le tracé ne relie plus rien de lisible. */
        const val MAX_MINUTES = 24 * 60

        val FIVE = CaptureInterval(5)
        val THIRTY = CaptureInterval(30)
        val ONE_HOUR = CaptureInterval(60)

        /** Les paliers proposés, du plus fin au plus grossier. */
        val PRESETS = listOf(FIVE, THIRTY, ONE_HOUR)

        val DEFAULT = FIVE

        /**
         * `null` hors bornes : une valeur persistée douteuse — après une
         * évolution du format, par exemple — ne fait pas loi.
         */
        fun fromMinutes(minutes: Int): CaptureInterval? =
            if (minutes in MIN_MINUTES..MAX_MINUTES) CaptureInterval(minutes) else null

        /**
         * Pour du code qui connaît déjà sa valeur. Échoue bruyamment hors
         * bornes : c'est une erreur de programmation, pas une saisie.
         */
        fun ofMinutes(minutes: Int): CaptureInterval =
            requireNotNull(fromMinutes(minutes)) {
                "Cadence hors bornes : $minutes min"
            }
    }
}
