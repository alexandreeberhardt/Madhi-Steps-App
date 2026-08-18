package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.CaptureScheduler
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.application.port.SyncScheduler
import com.madhi.tracker.application.port.TrackingIntentStore
import com.madhi.tracker.application.port.TrackingRuntime
import com.madhi.tracker.domain.model.TrackerEvent
import javax.inject.Inject
import kotlin.time.Duration

/**
 * Démarre le suivi et enregistre l'intention de l'utilisatrice.
 *
 * L'ordre importe : l'intention est persistée **avant** que quoi que ce soit
 * démarre. Si le processus mourait entre les deux, la reprise après
 * redémarrage saurait quand même que le suivi doit tourner.
 */
class StartTracking @Inject constructor(
    private val trackingIntentStore: TrackingIntentStore,
    private val trackingRuntime: TrackingRuntime,
    private val captureScheduler: CaptureScheduler,
    private val syncScheduler: SyncScheduler,
    private val eventLog: EventLog,
) {

    suspend operator fun invoke() {
        trackingIntentStore.setEnabled(true)
        trackingRuntime.start()

        // Première capture immédiate : la voyageuse vient d'appuyer sur un
        // bouton, elle doit voir un point apparaître, pas attendre cinq minutes.
        captureScheduler.scheduleNext(Duration.ZERO)

        syncScheduler.ensurePeriodicSyncScheduled()
        eventLog.record(TrackerEvent.TRACKING_STARTED)
    }
}
