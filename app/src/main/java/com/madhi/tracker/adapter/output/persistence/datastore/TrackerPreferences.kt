package com.madhi.tracker.adapter.output.persistence.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Toutes les clés en un endroit : une clé dupliquée avec une faute de frappe
 * produirait une préférence silencieusement toujours vide.
 */
internal object TrackerPreferences {
    const val FILE_NAME = "tracker-state"

    val TRACKING_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("tracking_enabled")
    val CAPTURE_INTERVAL_MINUTES: Preferences.Key<Int> = intPreferencesKey("capture_interval_minutes")

    val SYNC_LAST_ATTEMPT_AT: Preferences.Key<Long> = longPreferencesKey("sync_last_attempt_at")
    val SYNC_LAST_SUCCESS_AT: Preferences.Key<Long> = longPreferencesKey("sync_last_success_at")
    val SYNC_LAST_FAILURE_CODE: Preferences.Key<String> = stringPreferencesKey("sync_last_failure_code")
    val SYNC_LAST_BATCH_SIZE: Preferences.Key<Int> = intPreferencesKey("sync_last_batch_size")
    val SYNC_CONSECUTIVE_FAILURES: Preferences.Key<Int> = intPreferencesKey("sync_consecutive_failures")

    val REBOOT_LAST_SEEN_AT: Preferences.Key<Long> = longPreferencesKey("reboot_last_seen_at")
    val REBOOT_LAST_SEEN_UPTIME_MS: Preferences.Key<Long> = longPreferencesKey("reboot_last_seen_uptime_ms")
    val REBOOT_BOOT_HANDLED_AT: Preferences.Key<Long> = longPreferencesKey("reboot_boot_handled_at")
}
