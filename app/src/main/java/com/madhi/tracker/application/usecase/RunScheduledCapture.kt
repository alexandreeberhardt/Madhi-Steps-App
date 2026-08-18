package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.CaptureScheduler
import com.madhi.tracker.application.port.TrackingIntentStore
import javax.inject.Inject

/**
 * Une capture déclenchée par le métronome, suivie de la programmation de la
 * suivante.
 *
 * La reprogrammation est la partie critique, et elle est **inconditionnelle**
 * en cas d'échec : un fix raté sous un tunnel ne doit pas interrompre la
 * chaîne d'alarmes. Sans cette garantie, un seul échec arrêterait le suivi
 * pour le reste du voyage, silencieusement.
 */
class RunScheduledCapture @Inject constructor(
    private val captureLocation: CaptureLocation,
    private val captureScheduler: CaptureScheduler,
    private val trackingIntentStore: TrackingIntentStore,
) {

    suspend operator fun invoke(): CaptureResult {
        val result = runCatching { captureLocation() }

        val intent = trackingIntentStore.read()
        if (intent.enabled) {
            captureScheduler.scheduleNext(intent.captureInterval.duration)
        } else {
            // Le suivi a pu être arrêté pendant l'acquisition.
            captureScheduler.cancel()
        }

        return result.getOrThrow()
    }
}
