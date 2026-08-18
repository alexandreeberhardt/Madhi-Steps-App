package com.madhi.tracker.adapter.output.persistence.room

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Base locale du voyage.
 *
 * `exportSchema = true` et les schémas JSON commités ne sont pas une
 * formalité : sans eux, on ne peut ni écrire ni tester une migration, et une
 * mise à jour d'APK effacerait des positions non synchronisées.
 * `fallbackToDestructiveMigration` est interdit, y compris en debug (ADR-005).
 */
@Database(
    entities = [LocationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TrackerDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao

    companion object {
        const val NAME = "madhi-tracker.db"
    }
}
