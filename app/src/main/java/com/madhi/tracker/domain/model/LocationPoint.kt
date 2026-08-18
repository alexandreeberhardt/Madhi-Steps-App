package com.madhi.tracker.domain.model

import java.time.Instant

data class LocationPoint(
    val id: LocationId,
    val coordinates: Coordinates,
    val recordedAt: Instant,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val speedMetersPerSecond: Float? = null,
    val batteryPercent: Int? = null,
    val syncState: SyncState = SyncState.PENDING,
    val attemptCount: Int = 0,
    val lastAttemptAt: Instant? = null,
    val lastErrorCode: String? = null,
) {
    companion object {
        /** Crée un point à partir d'une mesure validée. Toujours PENDING. */
        fun from(
            fix: LocationFix,
            batteryPercent: Int?,
            id: LocationId = LocationId.random(),
        ): LocationPoint = LocationPoint(
            id = id,
            coordinates = fix.coordinates,
            recordedAt = fix.recordedAt,
            accuracyMeters = fix.accuracyMeters,
            altitudeMeters = fix.altitudeMeters,
            speedMetersPerSecond = fix.speedMetersPerSecond,
            batteryPercent = batteryPercent,
        )
    }
}
