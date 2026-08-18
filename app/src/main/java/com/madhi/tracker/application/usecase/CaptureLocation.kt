package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.Clock
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.application.port.LocationSource
import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.application.port.RebootJournalStore
import com.madhi.tracker.application.port.SyncScheduler
import com.madhi.tracker.application.port.TrackingEnvironment
import com.madhi.tracker.application.port.TrackingIntentStore
import com.madhi.tracker.domain.LocationValidation
import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.LocationAcquisitionFailure
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.LocationFix
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.TrackerEvent
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Acquérir une position, la valider, l'enregistrer, puis seulement demander
 * un envoi.
 *
 * L'ordre est le principe central du projet : une position est d'abord
 * sauvegardée localement, le réseau intervient ensuite. Aucun échec réseau
 * ne peut faire perdre un point puisque le point est déjà en base quand la
 * synchronisation est évoquée.
 */
class CaptureLocation @Inject constructor(
    private val locationSource: LocationSource,
    private val locationStore: LocationStore,
    private val trackingIntentStore: TrackingIntentStore,
    private val environment: TrackingEnvironment,
    private val rebootJournalStore: RebootJournalStore,
    private val syncScheduler: SyncScheduler,
    private val eventLog: EventLog,
    private val clock: Clock,
) {

    suspend operator fun invoke(): CaptureResult {
        // Signe de vie enregistré avant toute chose : même une acquisition
        // ratée prouve que le processus tourne, ce qui sert à détecter un
        // redémarrage non traité plus tard (ADR-007).
        rebootJournalStore.recordAlive(clock.now(), clock.uptime())

        val interval = trackingIntentStore.read().captureInterval

        return when (val acquisition = locationSource.acquire(acquisitionTimeout(interval))) {
            is Outcome.Failure -> onAcquisitionFailed(acquisition.reason)
            is Outcome.Success -> onFixAcquired(acquisition.value)
        }
    }

    private fun onAcquisitionFailed(failure: LocationAcquisitionFailure): CaptureResult {
        // Un trou d'acquisition est un incident normal : tunnel, sous-sol,
        // ciel bouché. Il ne doit ni interrompre la cadence ni faire de bruit.
        eventLog.record(TrackerEvent.ACQUISITION_FAILED, failure.code)
        return CaptureResult.Failed(failure)
    }

    private suspend fun onFixAcquired(fix: LocationFix): CaptureResult {
        eventLog.record(TrackerEvent.LOCATION_ACQUIRED)

        return when (val validation = LocationValidation.validate(fix, clock.now())) {
            is Outcome.Failure -> {
                eventLog.record(TrackerEvent.LOCATION_REJECTED, validation.reason.code)
                CaptureResult.Rejected(validation.reason)
            }

            is Outcome.Success -> CaptureResult.Captured(save(validation.value))
        }
    }

    private suspend fun save(fix: LocationFix): LocationPoint {
        val point = LocationPoint.from(fix, batteryPercent = environment.snapshot().batteryPercent)
        locationStore.save(point)
        eventLog.record(TrackerEvent.LOCATION_SAVED)

        // Le point est en base : demander l'envoi ne peut plus rien lui faire
        // perdre. Si la demande échoue, le worker périodique reprendra.
        syncScheduler.requestImmediateSync()
        return point
    }

    /**
     * Une acquisition ne doit jamais déborder sur la suivante, sans quoi deux
     * captures se chevaucheraient et le GPS resterait allumé en continu.
     * À l'intervalle le plus court, deux minutes, le plafond descend donc
     * naturellement à une minute.
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
