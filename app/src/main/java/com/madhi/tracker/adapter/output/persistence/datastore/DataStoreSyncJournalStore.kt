package com.madhi.tracker.adapter.output.persistence.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.madhi.tracker.application.port.SyncJournalStore
import com.madhi.tracker.domain.error.SyncFailure
import com.madhi.tracker.domain.model.SyncJournal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSyncJournalStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SyncJournalStore {

    override suspend fun read(): SyncJournal = observe().first()

    override suspend fun recordAttempt(at: Instant, batchSize: Int) {
        dataStore.edit {
            it[TrackerPreferences.SYNC_LAST_ATTEMPT_AT] = at.toEpochMilli()
            it[TrackerPreferences.SYNC_LAST_BATCH_SIZE] = batchSize
        }
    }

    override suspend fun recordSuccess(at: Instant) {
        dataStore.edit {
            it[TrackerPreferences.SYNC_LAST_SUCCESS_AT] = at.toEpochMilli()
            it[TrackerPreferences.SYNC_CONSECUTIVE_FAILURES] = 0
            it.remove(TrackerPreferences.SYNC_LAST_FAILURE_CODE)
        }
    }

    override suspend fun recordFailure(at: Instant, failure: SyncFailure) {
        dataStore.edit {
            it[TrackerPreferences.SYNC_LAST_ATTEMPT_AT] = at.toEpochMilli()
            it[TrackerPreferences.SYNC_LAST_FAILURE_CODE] = failure.code
            val previous = it[TrackerPreferences.SYNC_CONSECUTIVE_FAILURES] ?: 0
            it[TrackerPreferences.SYNC_CONSECUTIVE_FAILURES] = previous + 1
        }
    }

    override fun observe(): Flow<SyncJournal> = dataStore.data.map { preferences ->
        SyncJournal(
            lastAttemptAt = preferences[TrackerPreferences.SYNC_LAST_ATTEMPT_AT]?.let(Instant::ofEpochMilli),
            lastSuccessAt = preferences[TrackerPreferences.SYNC_LAST_SUCCESS_AT]?.let(Instant::ofEpochMilli),
            lastFailureCode = preferences[TrackerPreferences.SYNC_LAST_FAILURE_CODE],
            lastBatchSize = preferences[TrackerPreferences.SYNC_LAST_BATCH_SIZE],
            consecutiveFailures = preferences[TrackerPreferences.SYNC_CONSECUTIVE_FAILURES] ?: 0,
        )
    }
}
