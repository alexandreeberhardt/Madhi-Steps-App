package com.madhi.tracker.adapter.output.persistence.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.SyncState
import java.time.Instant

/**
 * Les instants sont stockés en millisecondes epoch plutôt qu'en texte : le
 * tri et la comparaison se font alors dans SQLite sans conversion, ce qui
 * compte pour l'index (sync_state, recorded_at) qui sert à chaque batch.
 */
@Entity(
    tableName = "locations",
    indices = [Index(value = ["sync_state", "recorded_at"])],
)
data class LocationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "recorded_at")
    val recordedAtEpochMillis: Long,

    @ColumnInfo(name = "accuracy_m")
    val accuracyMeters: Float?,

    @ColumnInfo(name = "altitude_m")
    val altitudeMeters: Double?,

    @ColumnInfo(name = "speed_mps")
    val speedMetersPerSecond: Float?,

    @ColumnInfo(name = "battery_percent")
    val batteryPercent: Int?,

    @ColumnInfo(name = "sync_state")
    val syncState: String,

    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int,

    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAtEpochMillis: Long?,

    @ColumnInfo(name = "last_error_code")
    val lastErrorCode: String?,
)

fun LocationEntity.toDomain(): LocationPoint = LocationPoint(
    id = LocationId(id),
    coordinates = Coordinates(latitude, longitude),
    recordedAt = Instant.ofEpochMilli(recordedAtEpochMillis),
    accuracyMeters = accuracyMeters,
    altitudeMeters = altitudeMeters,
    speedMetersPerSecond = speedMetersPerSecond,
    batteryPercent = batteryPercent,
    // Une valeur inconnue en base ne doit jamais faire disparaître un point :
    // dans le doute, il reste à envoyer.
    syncState = SyncState.entries.find { it.name == syncState } ?: SyncState.PENDING,
    attemptCount = attemptCount,
    lastAttemptAt = lastAttemptAtEpochMillis?.let(Instant::ofEpochMilli),
    lastErrorCode = lastErrorCode,
)

fun LocationPoint.toEntity(): LocationEntity = LocationEntity(
    id = id.value,
    latitude = coordinates.latitude,
    longitude = coordinates.longitude,
    recordedAtEpochMillis = recordedAt.toEpochMilli(),
    accuracyMeters = accuracyMeters,
    altitudeMeters = altitudeMeters,
    speedMetersPerSecond = speedMetersPerSecond,
    batteryPercent = batteryPercent,
    syncState = syncState.name,
    attemptCount = attemptCount,
    lastAttemptAtEpochMillis = lastAttemptAt?.toEpochMilli(),
    lastErrorCode = lastErrorCode,
)
