package com.madhi.tracker.application.port

import com.madhi.tracker.domain.error.SyncFailure
import com.madhi.tracker.domain.model.SyncJournal
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SyncJournalStore {
    suspend fun read(): SyncJournal
    suspend fun recordAttempt(at: Instant, batchSize: Int)
    suspend fun recordSuccess(at: Instant)
    suspend fun recordFailure(at: Instant, failure: SyncFailure)
    fun observe(): Flow<SyncJournal>
}
