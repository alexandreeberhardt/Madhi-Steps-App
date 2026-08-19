package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.CaptureScheduler
import com.madhi.tracker.application.port.Clock
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.application.port.TrackingIntentStore
import com.madhi.tracker.domain.model.TrackerEvent
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Filet de sécurité du suivi continu.
 *
 * Depuis que la cadence est confiée au flux du fournisseur de localisation,
 * cette alarme ne capture plus systématiquement : elle vérifie qu'une
 * position est bien arrivée récemment, et n'intervient que si le flux s'est
 * tu. Un abonnement peut mourir sans que le service meure — ce serait alors
 * une panne totalement silencieuse.
 *
 * La reprogrammation reste **inconditionnelle**, y compris en cas d'échec :
 * un seul maillon manquant arrêterait la surveillance pour le reste du voyage.
 */
class RunScheduledCapture @Inject constructor(
    private val captureLocation: CaptureLocation,
    private val captureScheduler: CaptureScheduler,
    private val trackingIntentStore: TrackingIntentStore,
    private val locationStore: LocationStore,
    private val eventLog: EventLog,
    private val clock: Clock,
) {

    suspend operator fun invoke(): CaptureResult? {
        val initialIntent = trackingIntentStore.read()
        if (!initialIntent.enabled) {
            captureScheduler.cancel()
            return null
        }

        val result = runCatching { captureIfStreamWentQuiet() }

        val intent = trackingIntentStore.read()
        if (intent.enabled) {
            captureScheduler.scheduleNext(intent.captureInterval.duration * WATCHDOG_INTERVAL_FACTOR)
        } else {
            // Le suivi a pu être arrêté pendant la vérification.
            captureScheduler.cancel()
        }

        return result.getOrThrow()
    }

    private suspend fun captureIfStreamWentQuiet(): CaptureResult? {
        val interval = trackingIntentStore.read().captureInterval
        val lastRecordedAt = locationStore.lastRecordedAt() ?: return captureLocation()

        val silence = (clock.now().toEpochMilli() - lastRecordedAt.toEpochMilli()).milliseconds
        if (silence < interval.duration * SILENCE_TOLERANCE_FACTOR) {
            // Le flux fait son travail : ne pas rallumer le GPS pour rien.
            return null
        }

        eventLog.record(TrackerEvent.STREAM_SILENT, "${silence.inWholeMinutes}min")
        return captureLocation()
    }

    private companion object {
        /** Vérifier trois fois moins souvent que la cadence de capture suffit. */
        const val WATCHDOG_INTERVAL_FACTOR = 3

        /**
         * Deux intervalles sans position avant de s'inquiéter : le système a
         * le droit de décaler une livraison, l'objectif produit dit
         * « environ » cinq minutes.
         */
        const val SILENCE_TOLERANCE_FACTOR = 2
    }
}
