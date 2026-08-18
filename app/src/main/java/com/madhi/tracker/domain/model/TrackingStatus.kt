package com.madhi.tracker.domain.model

import java.time.Instant

/**
 * Les quatre états de l'écran principal décrits par `arch/09` §4.
 */
enum class TrackingHealth {
    /** Suivi actif, rien à signaler. */
    ACTIVE,

    /** Le suivi tourne, mais les points restent sur le téléphone. */
    OFFLINE,

    /** Quelque chose bloque le suivi et demande une action de l'utilisatrice. */
    ACTION_REQUIRED,

    /** Le suivi a été arrêté volontairement dans les réglages. */
    STOPPED,
}

data class TrackingStatus(
    val health: TrackingHealth,
    val lastPointAt: Instant?,
    val pendingCount: Int,
    val problems: List<TrackingProblem>,
) {
    /**
     * L'écran principal n'en montre qu'un : celui à corriger en premier.
     * Le reste vit dans le diagnostic (`arch/09` §3).
     */
    val mostUrgentProblem: TrackingProblem? get() = problems.minByOrNull { it.ordinal }
}
