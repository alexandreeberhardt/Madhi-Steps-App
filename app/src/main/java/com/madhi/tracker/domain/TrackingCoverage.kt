package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.CaptureInterval
import kotlin.time.Duration

/**
 * Combien de positions aurait-on dû enregistrer, et combien en a-t-on
 * réellement ?
 *
 * C'est l'instrument de mesure du test terrain. Sans lui, la question
 * « est-ce que MIUI tue le suivi ? » n'a pour réponse qu'une impression.
 * Avec lui, elle a un pourcentage.
 */
object TrackingCoverage {

    fun evaluate(
        window: Duration,
        interval: CaptureInterval,
        actualCount: Int,
    ): Coverage {
        val expected = (window.inWholeSeconds / interval.duration.inWholeSeconds).toInt()
        return Coverage(
            expected = expected,
            actual = actualCount,
            // Une fenêtre plus courte qu'un intervalle ne permet aucun verdict :
            // zéro point sur trente secondes est normal, pas un trou.
            ratio = if (expected <= 0) null else actualCount.toDouble() / expected,
        )
    }

    data class Coverage(
        val expected: Int,
        val actual: Int,
        val ratio: Double?,
    ) {
        /**
         * En dessous des deux tiers, le suivi ne fait plus son travail.
         * Le seuil est volontairement tolérant : Android a le droit de
         * décaler une acquisition, l'objectif produit dit « environ »
         * cinq minutes.
         */
        val isDegraded: Boolean get() = ratio != null && ratio < DEGRADED_THRESHOLD

        private companion object {
            const val DEGRADED_THRESHOLD = 0.66
        }
    }
}
