package com.madhi.tracker.adapter.input.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.madhi.tracker.application.usecase.SyncOutcome
import com.madhi.tracker.application.usecase.SyncPendingLocations
import com.madhi.tracker.domain.error.SyncFailure
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Déclenche la synchronisation, et rien d'autre.
 *
 * Ce worker existe indépendamment de l'acquisition GPS : il vide le backlog
 * même quand le suivi est désactivé, comme l'exige `arch/01` §8.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val syncPendingLocations: SyncPendingLocations,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = when (val outcome = syncPendingLocations()) {
        is SyncOutcome.NothingToDo -> Result.success()
        is SyncOutcome.Completed -> Result.success()

        is SyncOutcome.Failed -> if (outcome.failure.isRetryable) {
            // WorkManager applique son propre backoff exponentiel.
            Result.retry()
        } else {
            // Authentification ou payload refusé : réessayer en boucle ne
            // corrigera rien et userait la batterie. L'exécution périodique
            // reprendra de toute façon, et aucun point n'est perdu.
            Result.success()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "sync-pending-locations-periodic"
        const val IMMEDIATE_WORK_NAME = "sync-pending-locations-now"
    }
}
