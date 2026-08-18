package com.madhi.tracker.adapter.output.persistence.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.madhi.tracker.application.port.TrackingIntentStore
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.TrackingIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreTrackingIntentStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TrackingIntentStore {

    override suspend fun read(): TrackingIntent = observe().first()

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[TrackerPreferences.TRACKING_ENABLED] = enabled }
    }

    override suspend fun setCaptureInterval(interval: CaptureInterval) {
        dataStore.edit { it[TrackerPreferences.CAPTURE_INTERVAL_MINUTES] = interval.minutes }
    }

    override fun observe(): Flow<TrackingIntent> = dataStore.data.map { preferences ->
        TrackingIntent(
            enabled = preferences[TrackerPreferences.TRACKING_ENABLED] ?: false,
            // Une valeur persistée qui ne correspond à aucun palier connu —
            // après une évolution de la liste, par exemple — retombe sur le
            // défaut plutôt que d'inventer un intervalle.
            captureInterval = preferences[TrackerPreferences.CAPTURE_INTERVAL_MINUTES]
                ?.let(CaptureInterval::fromMinutes)
                ?: CaptureInterval.DEFAULT,
        )
    }
}
