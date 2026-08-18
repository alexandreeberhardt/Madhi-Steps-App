package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.CaptureScheduler
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.application.port.SyncScheduler
import com.madhi.tracker.application.port.TrackingIntentStore
import com.madhi.tracker.application.port.TrackingRuntime
import com.madhi.tracker.domain.model.TrackerEvent
import javax.inject.Inject

/**
 * Arrête la collecte de nouvelles positions, et rien d'autre.
 *
 * `arch/01` §2 est explicite : désactiver le tracking ne supprime aucun
 * point local en attente et n'empêche pas leur synchronisation. La
 * synchronisation périodique reste donc planifiée — c'est volontaire et
 * c'est testé.
 */
class StopTracking @Inject constructor(
    private val trackingIntentStore: TrackingIntentStore,
    private val trackingRuntime: TrackingRuntime,
    private val captureScheduler: CaptureScheduler,
    private val syncScheduler: SyncScheduler,
    private val eventLog: EventLog,
) {

    suspend operator fun invoke() {
        trackingIntentStore.setEnabled(false)
        captureScheduler.cancel()
        trackingRuntime.stop()

        // Le backlog doit continuer de partir, même suivi arrêté.
        syncScheduler.ensurePeriodicSyncScheduled()
        eventLog.record(TrackerEvent.TRACKING_STOPPED)
    }
}
