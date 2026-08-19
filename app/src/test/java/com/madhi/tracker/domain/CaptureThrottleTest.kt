package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.CaptureInterval
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Mesuré sur appareil : avec deux fournisseurs abonnés, la couverture
 * atteignait 205 % de la cadence demandée. Chacun livre indépendamment.
 */
class CaptureThrottleTest {

    private val now: Instant = Instant.parse("2026-08-19T12:00:00Z")

    private fun redundant(secondsSinceLast: Long, interval: CaptureInterval = CaptureInterval.FIVE) =
        CaptureThrottle.isRedundant(now.minusSeconds(secondsSinceLast), now, interval)

    @Test
    fun `la toute premiere position est toujours acceptee`() {
        assertFalse(CaptureThrottle.isRedundant(null, now, CaptureInterval.FIVE))
    }

    @Test
    fun `la seconde livraison du meme intervalle est ecartee`() {
        // Le cas reel : le fournisseur reseau livre juste apres le GPS.
        assertTrue(redundant(secondsSinceLast = 5))
        assertTrue(redundant(secondsSinceLast = 150))
    }

    @Test
    fun `une position a l'intervalle demande est acceptee`() {
        assertFalse(redundant(secondsSinceLast = 300))
    }

    @Test
    fun `une legere avance du systeme reste acceptee`() {
        // Ecarter un point de trop creerait un trou : mieux vaut tolerer.
        assertFalse(redundant(secondsSinceLast = 250))
    }

    @Test
    fun `une position anterieure au dernier point est un doublon en retard`() {
        assertTrue(CaptureThrottle.isRedundant(now, now.minusSeconds(60), CaptureInterval.FIVE))
    }

    @Test
    fun `le seuil suit l'intervalle configure`() {
        assertTrue(redundant(secondsSinceLast = 60, interval = CaptureInterval.TWO))
        assertFalse(redundant(secondsSinceLast = 120, interval = CaptureInterval.TWO))
        assertTrue(redundant(secondsSinceLast = 600, interval = CaptureInterval.THIRTY))
    }

    @Test
    fun `un long silence n'est jamais considere comme redondant`() {
        assertFalse(redundant(secondsSinceLast = 7200))
    }
}
