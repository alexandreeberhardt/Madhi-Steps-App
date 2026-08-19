package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.Clock
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.application.port.RebootJournalStore
import com.madhi.tracker.application.port.SyncScheduler
import com.madhi.tracker.application.port.TrackingEnvironment
import com.madhi.tracker.domain.LocationValidation
import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.model.LocationFix
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.TrackerEvent
import javax.inject.Inject

/**
 * Ce qui arrive à une position, quelle que soit son origine.
 *
 * Valider, enregistrer, puis seulement évoquer le réseau. Extrait de
 * [CaptureLocation] pour que le flux continu du fournisseur de localisation
 * et l'acquisition ponctuelle empruntent exactement le même chemin : deux
 * versions de cette séquence finiraient par diverger sur un détail qui
 * coûterait des positions.
 */
class RecordLocation @Inject constructor(
    private val locationStore: LocationStore,
    private val environment: TrackingEnvironment,
    private val rebootJournalStore: RebootJournalStore,
    private val syncScheduler: SyncScheduler,
    private val eventLog: EventLog,
    private val clock: Clock,
) {

    suspend operator fun invoke(fix: LocationFix): CaptureResult {
        // Signe de vie : il prouve que le processus tourne, ce qui sert
        // ensuite à détecter un redémarrage non traité (ADR-007).
        rebootJournalStore.recordAlive(clock.now(), clock.uptime())
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
        runCatching { syncScheduler.requestImmediateSync() }
        return point
    }
}
