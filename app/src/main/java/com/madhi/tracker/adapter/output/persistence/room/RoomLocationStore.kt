package com.madhi.tracker.adapter.output.persistence.room

import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLocationStore @Inject constructor(
    private val dao: LocationDao,
) : LocationStore {

    override suspend fun save(point: LocationPoint) {
        dao.insert(point.toEntity())
    }

    override suspend fun oldestPending(limit: Int): List<LocationPoint> =
        dao.oldestPending(limit).map { it.toDomain() }

    override suspend fun markSynced(ids: List<LocationId>, at: Instant) {
        if (ids.isEmpty()) return
        // SQLite plafonne le nombre de paramètres d'une requête. Un backlog de
        // plusieurs milliers de points doit pouvoir être confirmé sans échouer.
        ids.map { it.value }.chunked(SQLITE_PARAMETER_CHUNK).forEach { chunk ->
            dao.markSynced(chunk, at.toEpochMilli())
        }
    }

    override suspend fun recordFailedAttempt(ids: List<LocationId>, at: Instant, errorCode: String) {
        if (ids.isEmpty()) return
        ids.map { it.value }.chunked(SQLITE_PARAMETER_CHUNK).forEach { chunk ->
            dao.recordFailedAttempt(chunk, at.toEpochMilli(), errorCode)
        }
    }

    override suspend fun pendingCount(): Int = dao.pendingCount()

    override suspend fun countRecordedSince(since: Instant): Int =
        dao.countRecordedSince(since.toEpochMilli())

    override suspend fun lastRecordedAt(): Instant? = dao.lastRecordedAt()?.let(Instant::ofEpochMilli)

    override suspend fun oldestPendingRecordedAt(): Instant? =
        dao.oldestPendingRecordedAt()?.let(Instant::ofEpochMilli)

    override fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    override fun observeLastRecordedAt(): Flow<Instant?> =
        dao.observeLastRecordedAt().map { it?.let(Instant::ofEpochMilli) }

    private companion object {
        // SQLite en accepte 999 par défaut ; on garde une marge confortable.
        const val SQLITE_PARAMETER_CHUNK = 500
    }
}
