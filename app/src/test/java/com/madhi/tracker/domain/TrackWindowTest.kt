package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.TrackPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TrackWindowTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val now: Instant = Instant.parse("2026-08-26T09:30:00Z")

    @Test
    fun `aujourd'hui commence a minuit dans le fuseau de la voyageuse`() {
        // Une date, pas une duree : a 11h30 heure de Paris, « aujourd'hui »
        // remonte a 11h30, pas a hier 11h30.
        val since = TrackWindow.since(TrackPeriod.TODAY, now, paris)

        assertEquals(Instant.parse("2026-08-25T22:00:00Z"), since)
    }

    @Test
    fun `aujourd'hui suit le fuseau, pas UTC`() {
        // Au Cap Nord en ete, l'ecart a UTC est de deux heures : minuit local
        // n'est pas minuit UTC, et la journee affichee serait decalee.
        val nordkapp = ZoneId.of("Europe/Oslo")
        val honolulu = ZoneId.of("Pacific/Honolulu")

        assertTrue(
            TrackWindow.since(TrackPeriod.TODAY, now, nordkapp) !=
                TrackWindow.since(TrackPeriod.TODAY, now, honolulu),
        )
    }

    @Test
    fun `sept jours remonte de sept jours pleins`() {
        val since = TrackWindow.since(TrackPeriod.SEVEN_DAYS, now, paris)

        assertEquals(Instant.parse("2026-08-19T09:30:00Z"), since)
    }

    @Test
    fun `tout le voyage ne borne rien`() {
        val since = TrackWindow.since(TrackPeriod.EVERYTHING, now, paris)

        assertEquals(Instant.EPOCH, since)
        assertTrue(since.isBefore(Instant.parse("2026-01-01T00:00:00Z")))
    }

    @Test
    fun `le pas de temps borne le nombre de points de chaque periode`() {
        // C'est la garantie qui protege un appareil a 4 Go : quelle que soit
        // la cadence de capture reglee, une periode ne peut pas produire plus
        // de points que sa duree divisee par son pas.
        val jour = 24 * 60 * 60 * 1000L
        val an = 365 * jour

        val aujourdhui = jour / TrackWindow.bucketMillis(TrackPeriod.TODAY)
        val semaine = 7 * jour / TrackWindow.bucketMillis(TrackPeriod.SEVEN_DAYS)
        val voyage = an / TrackWindow.bucketMillis(TrackPeriod.EVERYTHING)

        assertEquals(1_440, aujourdhui)
        assertEquals(2_016, semaine)
        assertEquals(8_760, voyage)
    }

    @Test
    fun `plus la periode est longue, plus le pas est grossier`() {
        assertTrue(
            TrackWindow.bucketMillis(TrackPeriod.TODAY) <
                TrackWindow.bucketMillis(TrackPeriod.SEVEN_DAYS),
        )
        assertTrue(
            TrackWindow.bucketMillis(TrackPeriod.SEVEN_DAYS) <
                TrackWindow.bucketMillis(TrackPeriod.EVERYTHING),
        )
    }
}
