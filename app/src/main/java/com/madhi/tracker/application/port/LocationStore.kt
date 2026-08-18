package com.madhi.tracker.application.port

import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Il n'existe volontairement aucune opération de suppression : la V1 ne
 * supprime rien (ADR-005). Une purge de rétention viendra en V2, et ne
 * devra jamais pouvoir viser un point PENDING.
 */
interface LocationStore {

    suspend fun save(point: LocationPoint)

    /** Les plus anciens d'abord : le backlog se vide dans l'ordre du voyage. */
    suspend fun oldestPending(limit: Int): List<LocationPoint>

    suspend fun markSynced(ids: List<LocationId>, at: Instant)

    /** Enregistre une tentative échouée. N'altère jamais l'état de synchronisation. */
    suspend fun recordFailedAttempt(ids: List<LocationId>, at: Instant, errorCode: String)

    suspend fun pendingCount(): Int

    /** Nombre de points enregistrés depuis [since], base du détecteur de trous. */
    suspend fun countRecordedSince(since: Instant): Int

    suspend fun lastRecordedAt(): Instant?

    /** Âge du plus vieux point non synchronisé, exigé au diagnostic (`arch/02` §6). */
    suspend fun oldestPendingRecordedAt(): Instant?

    fun observePendingCount(): Flow<Int>

    fun observeLastRecordedAt(): Flow<Instant?>
}
