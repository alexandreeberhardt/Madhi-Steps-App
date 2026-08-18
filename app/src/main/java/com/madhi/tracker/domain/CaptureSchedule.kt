package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.CaptureInterval
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Dans combien de temps la prochaine acquisition est-elle due ?
 *
 * Le calcul part du dernier point réellement enregistré, pas de l'instant où
 * on pose la question. C'est ce qui évite un piège discret : reprogrammer un
 * intervalle complet à chaque ouverture de l'application repousserait la
 * prochaine capture à chaque fois, si bien qu'une utilisatrice consultant
 * l'écran toutes les quatre minutes n'enregistrerait jamais rien.
 */
object CaptureSchedule {

    fun delayUntilNext(
        lastCaptureAt: Instant?,
        interval: CaptureInterval,
        now: Instant,
    ): Duration {
        // Aucun point encore : capturer tout de suite.
        if (lastCaptureAt == null) return Duration.ZERO

        val elapsed = (now.toEpochMilli() - lastCaptureAt.toEpochMilli()).milliseconds

        return when {
            // Horloge revenue en arrière : ne pas attendre indéfiniment.
            elapsed.isNegative() -> Duration.ZERO
            elapsed >= interval.duration -> Duration.ZERO
            else -> interval.duration - elapsed
        }
    }
}
