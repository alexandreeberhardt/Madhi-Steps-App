package com.madhi.tracker.adapter.output.persistence.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.madhi.tracker.application.port.OnboardingStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreOnboardingStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OnboardingStore {

    override suspend fun isCompleted(): Boolean = observe().first()

    override suspend fun markCompleted() {
        dataStore.edit { it[COMPLETED] = true }
    }

    override fun observe(): Flow<Boolean> = dataStore.data.map { it[COMPLETED] ?: false }

    private companion object {
        val COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
