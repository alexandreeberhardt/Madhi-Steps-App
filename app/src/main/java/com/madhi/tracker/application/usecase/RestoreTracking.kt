package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.Clock
import com.madhi.tracker.application.port.CaptureScheduler
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.application.port.RebootJournalStore
import com.madhi.tracker.application.port.SyncScheduler
import com.madhi.tracker.application.port.TrackingIntentStore
import com.madhi.tracker.application.port.TrackingRuntime
import com.madhi.tracker.domain.CaptureSchedule
import com.madhi.tracker.domain.RebootDetection
import com.madhi.tracker.domain.model.TrackerEvent
import javax.inject.Inject

/**
 * Remet le suivi dans l'état voulu par l'utilisatrice, quel que soit ce qui
 * vient de se passer : redémarrage du téléphone, mise à jour de l'APK,
 * ouverture de l'application, ou passage du watchdog.
 *
 * C'est le point de rattrapage unique du système. Il est **idempotent** :
 * l'appeler dix fois de suite produit le même état qu'une seule fois.
 */
class RestoreTracking @Inject constructor(
    private val trackingIntentStore: TrackingIntentStore,
    private val trackingRuntime: TrackingRuntime,
    private val captureScheduler: CaptureScheduler,
    private val syncScheduler: SyncScheduler,
    private val rebootJournalStore: RebootJournalStore,
    private val locationStore: LocationStore,
    private val eventLog: EventLog,
    private val clock: Clock,
) {

    suspend operator fun invoke(trigger: RestoreTrigger): RestoreResult {
        val now = clock.now()
        val autostartBlocked = detectBlockedAutostart(trigger, now)

        // Planifiée dans tous les cas, même suivi arrêté : le backlog doit
        // continuer de partir (`arch/01` §8).
        syncScheduler.ensurePeriodicSyncScheduled()

        val intent = trackingIntentStore.read()
        if (!intent.enabled) {
            rebootJournalStore.recordAlive(now, clock.uptime())
            return RestoreResult(
                trackingResumed = false,
                serviceWasRevived = false,
                autostartBlockedDetected = autostartBlocked,
            )
        }

        val wasRunning = trackingRuntime.isRunning()
        if (!wasRunning) {
            trackingRuntime.start()
            eventLog.record(
                if (trigger == RestoreTrigger.BOOT) {
                    TrackerEvent.TRACKING_RECOVERED_AFTER_BOOT
                } else {
                    TrackerEvent.TRACKING_SERVICE_REVIVED
                },
                trigger.name,
            )
        }

        // Le délai part du dernier point enregistré : reprogrammer un
        // intervalle complet à chaque appel repousserait indéfiniment la
        // prochaine capture.
        captureScheduler.scheduleNext(
            CaptureSchedule.delayUntilNext(
                lastCaptureAt = locationStore.lastRecordedAt(),
                interval = intent.captureInterval,
                now = now,
            ),
        )

        rebootJournalStore.recordAlive(now, clock.uptime())

        return RestoreResult(
            trackingResumed = true,
            serviceWasRevived = !wasRunning,
            autostartBlockedDetected = autostartBlocked,
        )
    }

    private suspend fun detectBlockedAutostart(trigger: RestoreTrigger, now: java.time.Instant): Boolean {
        if (trigger == RestoreTrigger.BOOT) {
            // Notre receveur a été appelé : le démarrage automatique
            // fonctionne, et cette trace le prouvera au prochain contrôle.
            rebootJournalStore.recordBootHandled(now)
            return false
        }

        val blocked = RebootDetection.rebootWasMissed(
            journal = rebootJournalStore.read(),
            now = now,
            uptime = clock.uptime(),
        )
        if (blocked) {
            eventLog.record(TrackerEvent.AUTOSTART_BLOCKED_DETECTED, trigger.name)
        }
        return blocked
    }
}

enum class RestoreTrigger {
    /** Le receveur BOOT_COMPLETED a bien été appelé. */
    BOOT,

    /** Mise à jour de l'APK : les tâches planifiées peuvent avoir été perdues. */
    PACKAGE_REPLACED,

    APP_OPENED,

    /** Passage périodique du worker, qui ressuscite le service si besoin. */
    WATCHDOG,
}

data class RestoreResult(
    val trackingResumed: Boolean,
    val serviceWasRevived: Boolean,
    val autostartBlockedDetected: Boolean,
)
