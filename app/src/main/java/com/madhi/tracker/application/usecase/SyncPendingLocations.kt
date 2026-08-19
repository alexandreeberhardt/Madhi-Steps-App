package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.Clock
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.application.port.LocationSyncGateway
import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.SyncFailure
import com.madhi.tracker.domain.model.TrackerEvent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vide la file des points en attente, par lots, du plus ancien au plus récent.
 *
 * La règle absolue : **aucun chemin de ce code ne supprime un point**. Un
 * point non confirmé par le serveur reste `PENDING`, quelle que soit
 * l'erreur, quel que soit le nombre de tentatives (ADR-003).
 */
@Singleton
class SyncPendingLocations @Inject constructor(
    private val locationStore: LocationStore,
    private val gateway: LocationSyncGateway,
    private val eventLog: EventLog,
    private val clock: Clock,
) {

    /**
     * Une seule synchronisation à la fois, comme l'exige le contrat API §7.
     *
     * Sans ce verrou, la capture et le worker périodique peuvent partir
     * ensemble et envoyer deux fois le même lot. L'idempotence côté serveur
     * l'absorbe, mais la requête en trop coûte de la radio et de la
     * batterie — et le cas s'est produit dès le premier test sur appareil.
     */
    suspend operator fun invoke(): SyncOutcome = mutex.withLock { syncOnce() }

    private suspend fun syncOnce(): SyncOutcome {
        if (locationStore.pendingCount() == 0) return SyncOutcome.NothingToDo

        eventLog.record(TrackerEvent.SYNC_STARTED)
        var batchSize = DEFAULT_BATCH_SIZE
        var confirmed = 0

        repeat(MAX_BATCHES_PER_RUN) {
            val batch = locationStore.oldestPending(batchSize)
            if (batch.isEmpty()) {
                eventLog.record(TrackerEvent.SYNC_SUCCESS)
                return SyncOutcome.Completed(confirmed)
            }

            when (val result = gateway.upload(batch)) {
                is Outcome.Success -> {
                    val stored = result.value.stored
                    locationStore.markSynced(stored, clock.now())
                    confirmed += stored.size

                    if (result.value.rejected.isNotEmpty()) {
                        // Un point refusé point par point reste en attente,
                        // avec son motif. C'est un bug à corriger, pas une
                        // raison de le jeter.
                        locationStore.recordFailedAttempt(
                            result.value.rejected.map { it.id },
                            clock.now(),
                            "rejected",
                        )
                    }

                    // Aucun point confirmé alors que le serveur a répondu :
                    // continuer boucierait sur le même lot.
                    if (stored.isEmpty()) {
                        eventLog.record(TrackerEvent.SYNC_FAILED, "no_progress")
                        return SyncOutcome.Failed(SyncFailure.Unexpected("no_progress"), confirmed)
                    }
                }

                is Outcome.Failure -> {
                    val failure = result.reason
                    locationStore.recordFailedAttempt(batch.map { it.id }, clock.now(), failure.code)
                    eventLog.record(TrackerEvent.SYNC_FAILED, failure.code)

                    // Le serveur dit que le lot est trop gros : le réduire et
                    // laisser la prochaine exécution reprendre, plutôt que
                    // d'insister dans la même passe.
                    if (failure == SyncFailure.BatchTooLarge && batchSize > MIN_BATCH_SIZE) {
                        batchSize = maxOf(MIN_BATCH_SIZE, batchSize / 4)
                        return@repeat
                    }

                    return SyncOutcome.Failed(failure, confirmed)
                }
            }
        }

        eventLog.record(TrackerEvent.SYNC_SUCCESS)
        return SyncOutcome.Completed(confirmed)
    }

    private val mutex = Mutex()

    private companion object {
        const val DEFAULT_BATCH_SIZE = 200
        const val MIN_BATCH_SIZE = 25

        // Un plafond par exécution : après une semaine hors réseau, mieux vaut
        // rendre la main au système que monopoliser la radio et la batterie.
        const val MAX_BATCHES_PER_RUN = 20
    }
}

sealed interface SyncOutcome {
    data object NothingToDo : SyncOutcome
    data class Completed(val confirmed: Int) : SyncOutcome
    data class Failed(val failure: SyncFailure, val confirmed: Int) : SyncOutcome
}
