package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.CaptureInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class TrackingCoverageTest {

    @Test
    fun `une heure a cinq minutes attend douze points`() {
        val coverage = TrackingCoverage.evaluate(1.hours, CaptureInterval.FIVE, actualCount = 12)

        assertEquals(12, coverage.expected)
        assertEquals(1.0, coverage.ratio!!, 0.001)
        assertFalse(coverage.isDegraded)
    }

    @Test
    fun `un leger retard systeme n'est pas une degradation`() {
        // L'objectif produit dit « environ » cinq minutes : Android a le
        // droit de decaler une acquisition.
        val coverage = TrackingCoverage.evaluate(1.hours, CaptureInterval.FIVE, actualCount = 10)

        assertFalse(coverage.isDegraded)
    }

    @Test
    fun `la moitie des points manquants est une degradation`() {
        val coverage = TrackingCoverage.evaluate(1.hours, CaptureInterval.FIVE, actualCount = 6)

        assertTrue(coverage.isDegraded)
    }

    @Test
    fun `un suivi totalement mort est detecte`() {
        val coverage = TrackingCoverage.evaluate(4.hours, CaptureInterval.FIVE, actualCount = 0)

        assertEquals(48, coverage.expected)
        assertTrue(coverage.isDegraded)
    }

    @Test
    fun `une fenetre plus courte qu'un intervalle ne permet aucun verdict`() {
        // Zero point sur trente secondes est normal, pas un trou.
        val coverage = TrackingCoverage.evaluate(30.minutes / 60, CaptureInterval.FIVE, actualCount = 0)

        assertNull(coverage.ratio)
        assertFalse(coverage.isDegraded)
    }

    @Test
    fun `l'intervalle configure change le nombre attendu`() {
        assertEquals(30, TrackingCoverage.evaluate(1.hours, CaptureInterval.TWO, 30).expected)
        assertEquals(2, TrackingCoverage.evaluate(1.hours, CaptureInterval.THIRTY, 2).expected)
    }

    @Test
    fun `plus de points qu'attendu n'est jamais une degradation`() {
        val coverage = TrackingCoverage.evaluate(1.hours, CaptureInterval.FIVE, actualCount = 15)

        assertFalse(coverage.isDegraded)
    }
}
