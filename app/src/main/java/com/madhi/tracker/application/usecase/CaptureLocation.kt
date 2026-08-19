package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.Clock
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.application.port.LocationSource
import com.madhi.tracker.application.port.RebootJournalStore
import com.madhi.tracker.application.port.TrackingIntentStore
import com.madhi.tracker.domain.LocationValidation
import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.LocationAcquisitionFailure
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.TrackerEvent
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Acquisition ponctuelle d'une position, à la demande.
 *
 * Ce n'est plus le mécanisme principal du suivi — le flux continu du
 * fournisseur de localisation l'a remplacé après l'échec du test T1 — mais
 * il reste nécessaire pour le test de fin d'onboarding et comme filet
 * lorsque le flux n'a rien livré depuis trop longtemps.
 */
class CaptureLocation @Inject constructor(
    private val locationSource: LocationSource,
    private val trackingIntentStore: TrackingIntentStore,
    private val recordLocation: RecordLocation,
    private val rebootJournalStore: RebootJournalStore,
    private val eventLog: EventLog,
    private val clock: Clock,
) {

    suspend operator fun invoke(): CaptureResult {
        rebootJournalStore.recordAlive(clock.now(), clock.uptime())

        val interval = trackingIntentStore.read().captureInterval

        return when (val acquisition = locationSource.acquire(acquisitionTimeout(interval))) {
            is Outcome.Failure -> {
                // Un trou d'acquisition est un incident normal : tunnel,
                // sous-sol, ciel bouché. Il ne doit pas faire de bruit.
                eventLog.record(TrackerEvent.ACQUISITION_FAILED, acquisition.reason.code)
                CaptureResult.Failed(acquisition.reason)
            }

            is Outcome.Success -> recordLocation(acquisition.value)
        }
    }

    /**
     * Une acquisition ne doit jamais déborder sur la suivante, sans quoi
     * deux captures se chevaucheraient et le GPS resterait allumé en continu.
     */
    private fun acquisitionTimeout(interval: CaptureInterval): Duration =
        minOf(MAX_ACQUISITION_TIMEOUT, interval.duration / 2)

    private companion object {
        // Au-delà, un fix froid n'arrivera probablement plus : mieux vaut
        // rendre la main et retenter au prochain créneau.
        val MAX_ACQUISITION_TIMEOUT = 90.seconds
    }
}

sealed interface CaptureResult {
    data class Captured(val point: LocationPoint) : CaptureResult
    data class Rejected(val rejection: LocationValidation.Rejection) : CaptureResult
    data class Failed(val failure: LocationAcquisitionFailure) : CaptureResult
}
