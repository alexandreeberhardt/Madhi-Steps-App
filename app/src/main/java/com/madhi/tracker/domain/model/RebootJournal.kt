package com.madhi.tracker.domain.model

import java.time.Instant
import kotlin.time.Duration

/**
 * Les traces qui permettent de savoir, après coup, si l'application s'est
 * réveillée au dernier redémarrage du téléphone (ADR-007 §3.1).
 */
data class RebootJournal(
    /** Horloge murale au dernier signe de vie de l'application. */
    val lastSeenAt: Instant? = null,

    /** Temps écoulé depuis le démarrage, au même instant. Remis à zéro à chaque boot. */
    val lastSeenUptime: Duration? = null,

    /** Dernier passage effectif du receveur de démarrage. */
    val bootHandledAt: Instant? = null,
) {
    companion object {
        val EMPTY = RebootJournal()
    }
}
