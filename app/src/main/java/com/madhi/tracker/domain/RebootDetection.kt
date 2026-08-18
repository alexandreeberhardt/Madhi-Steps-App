package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.RebootJournal
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Détecte qu'un redémarrage a eu lieu sans que notre receveur de démarrage
 * soit appelé — la signature d'un démarrage automatique bloqué (ADR-007).
 *
 * On observe le symptôme plutôt que le réglage. Interroger l'état d'un
 * réglage propriétaire MIUI demanderait de la réflexion sur des API non
 * documentées, différentes selon les versions de la surcouche. Ce calcul-ci
 * ne dépend d'aucun constructeur et se teste sans téléphone.
 */
object RebootDetection {

    /** Marge absorbant la dérive entre l'horloge murale et le temps de fonctionnement. */
    private val TOLERANCE = 2.minutes

    fun rebootWasMissed(
        journal: RebootJournal,
        now: Instant,
        uptime: Duration,
    ): Boolean {
        val lastSeenAt = journal.lastSeenAt ?: return false
        if (!rebootedSince(journal, lastSeenAt, now, uptime)) return false
        return !bootWasHandled(journal, bootedAt(now, uptime))
    }

    private fun rebootedSince(
        journal: RebootJournal,
        lastSeenAt: Instant,
        now: Instant,
        uptime: Duration,
    ): Boolean {
        // Le temps de fonctionnement ne fait que croître au sein d'un même
        // démarrage : le voir reculer est une preuve directe de redémarrage,
        // insensible aux changements d'heure du téléphone.
        val previousUptime = journal.lastSeenUptime
        if (previousUptime != null && uptime < previousUptime) return true

        // Sinon, comparer l'instant du démarrage à notre dernier signe de vie.
        // Le cas typique est un redémarrage suivi d'une longue période allumée.
        return bootedAt(now, uptime).isAfter(lastSeenAt.plusMillis(TOLERANCE.inWholeMilliseconds))
    }

    private fun bootWasHandled(journal: RebootJournal, bootedAt: Instant): Boolean {
        val handledAt = journal.bootHandledAt ?: return false
        return handledAt.isAfter(bootedAt.minusMillis(TOLERANCE.inWholeMilliseconds))
    }

    private fun bootedAt(now: Instant, uptime: Duration): Instant =
        now.minusMillis(uptime.inWholeMilliseconds)
}
