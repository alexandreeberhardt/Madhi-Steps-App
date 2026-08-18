package com.madhi.tracker.infrastructure.log

import android.util.Log
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.domain.model.TrackerEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Écrit dans Logcat sous une étiquette unique, pour que `adb logcat -s
 * MadhiTracker` donne le déroulé complet du suivi sans le reste du système.
 *
 * Le détail est une chaîne courte : un code d'erreur, un état. Jamais une
 * coordonnée — la signature du port l'interdit de toute façon.
 */
@Singleton
class AndroidEventLog @Inject constructor() : EventLog {

    override fun record(event: TrackerEvent, detail: String?) {
        val message = if (detail == null) event.name else "${event.name} $detail"
        when (event) {
            TrackerEvent.ACQUISITION_FAILED,
            TrackerEvent.LOCATION_REJECTED,
            TrackerEvent.SYNC_FAILED,
            TrackerEvent.AUTOSTART_BLOCKED_DETECTED,
            -> Log.w(TAG, message)

            else -> Log.i(TAG, message)
        }
    }

    private companion object {
        const val TAG = "MadhiTracker"
    }
}
