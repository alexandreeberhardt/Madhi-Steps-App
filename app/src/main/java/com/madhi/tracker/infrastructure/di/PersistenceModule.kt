package com.madhi.tracker.infrastructure.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.madhi.tracker.adapter.output.persistence.datastore.DataStoreOnboardingStore
import com.madhi.tracker.adapter.output.persistence.datastore.DataStoreRebootJournalStore
import com.madhi.tracker.adapter.output.persistence.datastore.DataStoreSyncJournalStore
import com.madhi.tracker.adapter.output.persistence.datastore.DataStoreTrackingIntentStore
import com.madhi.tracker.adapter.output.persistence.datastore.TrackerPreferences
import com.madhi.tracker.adapter.output.persistence.room.LocationDao
import com.madhi.tracker.adapter.output.persistence.room.RoomLocationStore
import com.madhi.tracker.adapter.output.persistence.room.TrackerDatabase
import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.application.port.OnboardingStore
import com.madhi.tracker.application.port.RebootJournalStore
import com.madhi.tracker.application.port.SyncJournalStore
import com.madhi.tracker.application.port.TrackingIntentStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Racine de composition de la persistance : c'est ici, et nulle part
 * ailleurs, que les ports rencontrent Room et DataStore.
 */
@Module
@InstallIn(SingletonComponent::class)
object PersistenceModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TrackerDatabase =
        Room.databaseBuilder(context, TrackerDatabase::class.java, TrackerDatabase.NAME)
            // Aucun fallbackToDestructiveMigration, y compris en debug : une
            // migration manquante doit faire échouer l'ouverture de la base,
            // pas effacer des positions non synchronisées (ADR-005).
            .build()

    @Provides
    fun provideLocationDao(database: TrackerDatabase): LocationDao = database.locationDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(TrackerPreferences.FILE_NAME)
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceBindings {

    @Binds
    abstract fun bindLocationStore(store: RoomLocationStore): LocationStore

    @Binds
    abstract fun bindTrackingIntentStore(store: DataStoreTrackingIntentStore): TrackingIntentStore

    @Binds
    abstract fun bindSyncJournalStore(store: DataStoreSyncJournalStore): SyncJournalStore

    @Binds
    abstract fun bindRebootJournalStore(store: DataStoreRebootJournalStore): RebootJournalStore

    @Binds
    abstract fun bindOnboardingStore(store: DataStoreOnboardingStore): OnboardingStore
}
