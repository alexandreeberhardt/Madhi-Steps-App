package com.madhi.tracker.adapter.output.persistence.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.madhi.tracker.application.port.RebootJournalStore
import com.madhi.tracker.domain.model.RebootJournal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class DataStoreRebootJournalStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : RebootJournalStore {

    override suspend fun read(): RebootJournal = dataStore.data.map { preferences ->
        RebootJournal(
            lastSeenAt = preferences[TrackerPreferences.REBOOT_LAST_SEEN_AT]?.let(Instant::ofEpochMilli),
            lastSeenUptime = preferences[TrackerPreferences.REBOOT_LAST_SEEN_UPTIME_MS]?.milliseconds,
            bootHandledAt = preferences[TrackerPreferences.REBOOT_BOOT_HANDLED_AT]?.let(Instant::ofEpochMilli),
        )
    }.first()

    override suspend fun recordAlive(at: Instant, uptime: Duration) {
        dataStore.edit {
            it[TrackerPreferences.REBOOT_LAST_SEEN_AT] = at.toEpochMilli()
            it[TrackerPreferences.REBOOT_LAST_SEEN_UPTIME_MS] = uptime.inWholeMilliseconds
        }
    }

    override suspend fun recordBootHandled(at: Instant) {
        dataStore.edit { it[TrackerPreferences.REBOOT_BOOT_HANDLED_AT] = at.toEpochMilli() }
    }
}
