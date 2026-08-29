package com.madhi.tracker.presentation

import com.madhi.tracker.presentation.common.pointTimeLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PointTimeTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val now: Instant = Instant.parse("2026-08-26T09:30:00Z")

    @Test
    fun `une position du jour se dit sans date`() {
        val label = pointTimeLabel(Instant.parse("2026-08-26T06:12:00Z"), now, paris)

        assertEquals("aujourd'hui à 08:12", label)
    }

    @Test
    fun `la veille se dit hier`() {
        val label = pointTimeLabel(Instant.parse("2026-08-25T16:45:00Z"), now, paris)

        assertEquals("hier à 18:45", label)
    }

    @Test
    fun `au-dela d'hier, la date apparait`() {
        val label = pointTimeLabel(Instant.parse("2026-08-20T05:00:00Z"), now, paris)

        assertEquals("le 20 août à 07:00", label)
    }

    @Test
    fun `l'annee n'apparait que si elle differe`() {
        // Un voyage d'un an finira par traverser deux annees, et « 3 janvier »
        // serait alors ambigu.
        val label = pointTimeLabel(Instant.parse("2025-12-31T22:10:00Z"), now, paris)

        assertEquals("le 31 décembre 2025 à 23:10", label)
    }

    @Test
    fun `l'heure est celle du pays traverse, pas UTC`() {
        // Au Cap Nord en ete, deux heures d'ecart avec UTC : afficher l'heure
        // UTC dirait qu'on roulait a 4 h du matin.
        val nordkapp = ZoneId.of("Europe/Oslo")

        assertEquals(
            "aujourd'hui à 08:00",
            pointTimeLabel(Instant.parse("2026-08-26T06:00:00Z"), now, nordkapp),
        )
    }
}
