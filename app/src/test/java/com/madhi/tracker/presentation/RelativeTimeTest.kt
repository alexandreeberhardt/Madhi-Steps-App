package com.madhi.tracker.presentation

import com.madhi.tracker.presentation.common.relativeAge
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RelativeTimeTest {

    private val now: Instant = Instant.parse("2026-08-19T12:00:00Z")

    @Test
    fun `aucune position connue`() {
        assertEquals("aucune", relativeAge(null, now))
    }

    @Test
    fun `moins d'une minute`() {
        assertEquals("à l'instant", relativeAge(now.minusSeconds(30), now))
    }

    @Test
    fun `quelques minutes`() {
        assertEquals("il y a 3 min", relativeAge(now.minusSeconds(200), now))
    }

    @Test
    fun `quelques heures`() {
        assertEquals("il y a 4 h", relativeAge(now.minusSeconds(4 * 3600 + 120), now))
    }

    @Test
    fun `plusieurs jours`() {
        assertEquals("il y a 3 j", relativeAge(now.minusSeconds(3 * 24 * 3600), now))
    }

    @Test
    fun `une horloge en avance ne produit pas de duree negative`() {
        // Correction NTP ou changement de fuseau pendant le voyage.
        assertEquals("à l'instant", relativeAge(now.plusSeconds(120), now))
    }
}
