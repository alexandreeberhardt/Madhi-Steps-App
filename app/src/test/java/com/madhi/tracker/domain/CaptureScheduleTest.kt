package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.CaptureInterval
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class CaptureScheduleTest {

    private val now: Instant = Instant.parse("2026-08-18T12:00:00Z")

    private fun delayAfter(minutesAgo: Long, interval: CaptureInterval = CaptureInterval.FIVE) =
        CaptureSchedule.delayUntilNext(now.minusSeconds(minutesAgo * 60), interval, now)

    @Test
    fun `sans aucun point, on capture tout de suite`() {
        assertEquals(Duration.ZERO, CaptureSchedule.delayUntilNext(null, CaptureInterval.FIVE, now))
    }

    @Test
    fun `le delai restant part du dernier point, pas de l'instant de la question`() {
        // Le piege evite : sans cela, consulter l'ecran toutes les quatre
        // minutes repousserait la capture indefiniment.
        assertEquals(3.minutes, delayAfter(minutesAgo = 2))
    }

    @Test
    fun `un point tout juste enregistre laisse l'intervalle complet`() {
        assertEquals(5.minutes, delayAfter(minutesAgo = 0))
    }

    @Test
    fun `un intervalle deja ecoule declenche une capture immediate`() {
        assertEquals(Duration.ZERO, delayAfter(minutesAgo = 5))
    }

    @Test
    fun `apres un long trou, on capture immediatement plutot que d'attendre`() {
        // Cas du retour d'un redemarrage ou d'un arret force.
        assertEquals(Duration.ZERO, delayAfter(minutesAgo = 600))
    }

    @Test
    fun `une horloge revenue en arriere ne fait pas attendre indefiniment`() {
        val future = now.plusSeconds(3600)

        assertEquals(Duration.ZERO, CaptureSchedule.delayUntilNext(future, CaptureInterval.FIVE, now))
    }

    @Test
    fun `l'intervalle configure est respecte`() {
        assertEquals(28.minutes, delayAfter(minutesAgo = 2, interval = CaptureInterval.THIRTY))
        assertEquals(1.minutes, delayAfter(minutesAgo = 1, interval = CaptureInterval.ofMinutes(2)))
    }
}
