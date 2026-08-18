package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LocationValidationTest {

    private val now: Instant = Instant.parse("2026-08-18T12:00:00Z")

    private fun fixAt(
        latitude: Double = 48.85837,
        longitude: Double = 2.29448,
        recordedAt: Instant = now,
    ) = LocationFix(
        coordinates = Coordinates(latitude, longitude),
        recordedAt = recordedAt,
        accuracyMeters = 12f,
        altitudeMeters = 34.0,
        speedMetersPerSecond = 4.7f,
    )

    private fun rejectionOf(fix: LocationFix) =
        LocationValidation.validate(fix, now).failureOrNull()

    @Test
    fun `accepte une position normale`() {
        assertTrue(LocationValidation.validate(fixAt(), now).isSuccess)
    }

    @Test
    fun `accepte le cercle polaire, destination du voyage`() {
        assertTrue(LocationValidation.validate(fixAt(latitude = 71.17, longitude = 25.78), now).isSuccess)
    }

    @Test
    fun `refuse une latitude hors bornes`() {
        assertEquals(LocationValidation.Rejection.LatitudeOutOfRange, rejectionOf(fixAt(latitude = 91.0)))
    }

    @Test
    fun `refuse une longitude hors bornes`() {
        assertEquals(LocationValidation.Rejection.LongitudeOutOfRange, rejectionOf(fixAt(longitude = -181.0)))
    }

    @Test
    fun `refuse le point zero zero, signature d'un fix rate`() {
        assertEquals(LocationValidation.Rejection.NullIsland, rejectionOf(fixAt(latitude = 0.0, longitude = 0.0)))
    }

    @Test
    fun `refuse un horodatage anterieur au projet, symptome d'une horloge remise a zero`() {
        val rejection = rejectionOf(fixAt(recordedAt = Instant.parse("1970-01-01T00:00:00Z")))
        assertEquals(LocationValidation.Rejection.TimestampTooOld, rejection)
    }

    @Test
    fun `refuse un horodatage tres loin dans le futur`() {
        val rejection = rejectionOf(fixAt(recordedAt = now.plusSeconds(3 * 24 * 3600)))
        assertEquals(LocationValidation.Rejection.TimestampInFuture, rejection)
    }

    @Test
    fun `tolere une petite avance d'horloge`() {
        assertTrue(LocationValidation.validate(fixAt(recordedAt = now.plusSeconds(600)), now).isSuccess)
    }

    @Test
    fun `accepte une position tres imprecise plutot que de la perdre`() {
        // Une mesure a 2 km reste une information utile a la famille.
        // Un point refuse, lui, est perdu definitivement.
        val imprecise = fixAt().copy(accuracyMeters = 2_000f)
        assertTrue(LocationValidation.validate(imprecise, now).isSuccess)
    }

    @Test
    fun `accepte une mesure sans precision ni altitude ni vitesse`() {
        val bare = fixAt().copy(accuracyMeters = null, altitudeMeters = null, speedMetersPerSecond = null)
        assertTrue(LocationValidation.validate(bare, now).isSuccess)
    }
}
