package com.madhi.tracker.persistence

import com.madhi.tracker.adapter.output.persistence.room.LocationEntity
import com.madhi.tracker.adapter.output.persistence.room.toDomain
import com.madhi.tracker.adapter.output.persistence.room.toEntity
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LocationEntityTest {

    @Test
    fun `un point conserve toutes ses informations entre domaine et base`() {
        val point = LocationPoint(
            id = LocationId("point-1"),
            coordinates = Coordinates(48.85837, 2.29448),
            recordedAt = Instant.parse("2026-08-18T12:00:00Z"),
            accuracyMeters = 12f,
            altitudeMeters = 34.0,
            speedMetersPerSecond = 4.7f,
            batteryPercent = 62,
            syncState = SyncState.PENDING,
            attemptCount = 3,
            lastAttemptAt = Instant.parse("2026-08-18T12:05:00Z"),
            lastErrorCode = "timeout",
        )

        assertEquals(point, point.toEntity().toDomain())
    }

    @Test
    fun `un etat de synchronisation inconnu en base reste a envoyer`() {
        val entity = baseEntity().copy(syncState = "ARCHIVED_BY_FUTURE_VERSION")

        val point = entity.toDomain()

        // Une version inattendue de la base ne doit jamais faire disparaitre
        // un point du backlog : dans le doute, il repart en attente.
        assertEquals(SyncState.PENDING, point.syncState)
    }

    @Test
    fun `les champs optionnels absents restent absents`() {
        val entity = baseEntity().copy(
            accuracyMeters = null,
            altitudeMeters = null,
            speedMetersPerSecond = null,
            batteryPercent = null,
            lastAttemptAtEpochMillis = null,
            lastErrorCode = null,
        )

        val point = entity.toDomain()

        assertNull(point.accuracyMeters)
        assertNull(point.altitudeMeters)
        assertNull(point.speedMetersPerSecond)
        assertNull(point.batteryPercent)
        assertNull(point.lastAttemptAt)
        assertNull(point.lastErrorCode)
    }

    private fun baseEntity() = LocationEntity(
        id = "point-1",
        latitude = 48.85837,
        longitude = 2.29448,
        recordedAtEpochMillis = Instant.parse("2026-08-18T12:00:00Z").toEpochMilli(),
        accuracyMeters = 12f,
        altitudeMeters = 34.0,
        speedMetersPerSecond = 4.7f,
        batteryPercent = 62,
        syncState = SyncState.PENDING.name,
        attemptCount = 0,
        lastAttemptAtEpochMillis = null,
        lastErrorCode = null,
    )
}
