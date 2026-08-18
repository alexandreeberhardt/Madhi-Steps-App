package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.CaptureScheduler
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.application.port.TrackingIntentStore
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.TrackerEvent
import javax.inject.Inject

/**
 * Change la cadence sans perdre les points en attente, et sans attendre la
 * fin de l'intervalle précédent pour que le nouveau prenne effet.
 */
class ChangeCaptureInterval @Inject constructor(
    private val trackingIntentStore: TrackingIntentStore,
    private val captureScheduler: CaptureScheduler,
    private val eventLog: EventLog,
) {

    suspend operator fun invoke(interval: CaptureInterval) {
        trackingIntentStore.setCaptureInterval(interval)

        // Reprogrammer immédiatement : sinon, passer de 30 à 2 minutes ne
        // ferait effet qu'une demi-heure plus tard.
        if (trackingIntentStore.read().enabled) {
            captureScheduler.scheduleNext(interval.duration)
        }

        eventLog.record(TrackerEvent.CAPTURE_INTERVAL_CHANGED, "${interval.minutes}min")
    }
}
