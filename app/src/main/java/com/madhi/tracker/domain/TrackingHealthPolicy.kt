package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.TrackingHealth
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.domain.model.TrackingProblem
import com.madhi.tracker.domain.model.TrackingStatus
import java.time.Instant

/**
 * Traduit l'état réel du système en l'un des quatre états d'affichage de
 * `arch/09` §4. Règle pure, sans Android, donc testable exhaustivement.
 */
object TrackingHealthPolicy {

    fun evaluate(
        intent: TrackingIntent,
        problems: List<TrackingProblem>,
        isOnline: Boolean,
        pendingCount: Int,
        lastPointAt: Instant?,
    ): TrackingStatus {
        val sortedProblems = problems.distinct().sortedBy { it.ordinal }
        return TrackingStatus(
            health = health(intent, sortedProblems, isOnline),
            lastPointAt = lastPointAt,
            pendingCount = pendingCount,
            problems = sortedProblems,
        )
    }

    private fun health(
        intent: TrackingIntent,
        problems: List<TrackingProblem>,
        isOnline: Boolean,
    ): TrackingHealth = when {
        // L'arrêt volontaire prime : afficher « action nécessaire » alors que
        // l'utilisatrice a elle-même coupé le suivi serait un mensonge.
        !intent.enabled -> TrackingHealth.STOPPED

        problems.any { it.causesDataLoss } -> TrackingHealth.ACTION_REQUIRED

        // Hors ligne n'est pas une anomalie : c'est le mode de fonctionnement
        // normal du voyage. Le message doit rassurer, pas alerter.
        !isOnline -> TrackingHealth.OFFLINE

        else -> TrackingHealth.ACTIVE
    }
}
