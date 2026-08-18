package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.RebootJournal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Ces cas décrivent le scénario MIUI de l'ADR-007 : le téléphone redémarre,
 * le démarrage automatique est bloqué, notre receveur n'est jamais appelé.
 */
class RebootDetectionTest {

    private val now: Instant = Instant.parse("2026-08-18T12:00:00Z")

    @Test
    fun `aucun verdict sans historique`() {
        assertFalse(RebootDetection.rebootWasMissed(RebootJournal.EMPTY, now, uptime = 3.hours))
    }

    @Test
    fun `pas de redemarrage quand le telephone tourne depuis avant notre dernier signe de vie`() {
        val journal = RebootJournal(
            lastSeenAt = now.minusSeconds(600),
            lastSeenUptime = 5.hours,
        )
        assertFalse(RebootDetection.rebootWasMissed(journal, now, uptime = 5.hours + 10.minutes))
    }

    @Test
    fun `redemarrage manque detecte quand le temps de fonctionnement recule`() {
        val journal = RebootJournal(
            lastSeenAt = now.minusSeconds(600),
            lastSeenUptime = 5.hours,
        )
        // Le telephone ne tourne que depuis 3 minutes : il a redemarre.
        assertTrue(RebootDetection.rebootWasMissed(journal, now, uptime = 3.minutes))
    }

    @Test
    fun `redemarrage manque detecte apres une longue periode allumee`() {
        // Cas ou le temps de fonctionnement ne recule pas : redemarrage il y a
        // 8 h, dernier signe de vie il y a 10 h.
        val journal = RebootJournal(
            lastSeenAt = now.minusSeconds(10 * 3600),
            lastSeenUptime = 2.hours,
        )
        assertTrue(RebootDetection.rebootWasMissed(journal, now, uptime = 8.hours))
    }

    @Test
    fun `aucun probleme quand le receveur de demarrage a fait son travail`() {
        val journal = RebootJournal(
            lastSeenAt = now.minusSeconds(600),
            lastSeenUptime = 5.hours,
            bootHandledAt = now.minusSeconds(120),
        )
        assertFalse(RebootDetection.rebootWasMissed(journal, now, uptime = 3.minutes))
    }

    @Test
    fun `un demarrage traite lors d'un boot precedent ne compte pas pour celui-ci`() {
        val journal = RebootJournal(
            lastSeenAt = now.minusSeconds(600),
            lastSeenUptime = 5.hours,
            // Traite il y a deux jours, donc avant le demarrage actuel.
            bootHandledAt = now.minusSeconds(2 * 24 * 3600),
        )
        assertTrue(RebootDetection.rebootWasMissed(journal, now, uptime = 3.minutes))
    }

    @Test
    fun `une derive d'horloge d'une minute ne declenche pas de faux positif`() {
        val journal = RebootJournal(
            lastSeenAt = now.minusSeconds(60),
            lastSeenUptime = 4.hours,
        )
        assertFalse(RebootDetection.rebootWasMissed(journal, now, uptime = 4.hours + 1.minutes))
    }
}
