package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationFix
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LocationPointTest {

    private val fix = LocationFix(
        coordinates = Coordinates(48.85837, 2.29448),
        recordedAt = Instant.parse("2026-08-18T12:00:00Z"),
        accuracyMeters = 12f,
        altitudeMeters = 34.0,
        speedMetersPerSecond = 4.7f,
    )

    @Test
    fun `un point cree est toujours en attente d'envoi`() {
        assertEquals(SyncState.PENDING, LocationPoint.from(fix, batteryPercent = 62).syncState)
    }

    @Test
    fun `un point cree n'a encore aucune tentative`() {
        val point = LocationPoint.from(fix, batteryPercent = 62)
        assertEquals(0, point.attemptCount)
        assertNull(point.lastAttemptAt)
        assertNull(point.lastErrorCode)
    }

    @Test
    fun `chaque point recoit un identifiant unique, base de l'idempotence`() {
        val first = LocationPoint.from(fix, batteryPercent = null)
        val second = LocationPoint.from(fix, batteryPercent = null)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `l'horodatage de la mesure est conserve tel quel`() {
        assertEquals(fix.recordedAt, LocationPoint.from(fix, batteryPercent = null).recordedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un identifiant vide est refuse`() {
        LocationId("")
    }
}
