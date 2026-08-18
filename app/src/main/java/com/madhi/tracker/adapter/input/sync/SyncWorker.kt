package com.madhi.tracker.adapter.input.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.madhi.tracker.application.usecase.RestoreTracking
import com.madhi.tracker.application.usecase.RestoreTrigger
import com.madhi.tracker.application.usecase.SyncOutcome
import com.madhi.tracker.application.usecase.SyncPendingLocations
import com.madhi.tracker.domain.error.SyncFailure
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Vide le backlog, et sert de watchdog.
 *
 * Deux responsabilités, parce qu'elles ont le même déclencheur et le même
 * rythme. La synchronisation est indépendante de l'acquisition GPS
 * (`arch/01` §8) ; le watchdog relance le service de suivi si la surcouche
 * constructeur l'a tué (ADR-007 §3.2).
 *
 * Cette résurrection n'est possible que grâce à l'exemption d'optimisation
 * de batterie, qui figure parmi les exemptions autorisant le démarrage d'un
 * service de premier plan depuis l'arrière-plan.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val syncPendingLocations: SyncPendingLocations,
    private val restoreTracking: RestoreTracking,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        // Le watchdog passe en premier : si le service a été tué, le
        // relancer est plus urgent que de vider la file.
        runCatching { restoreTracking(RestoreTrigger.WATCHDOG) }

        return syncOutcome()
    }

    private suspend fun syncOutcome(): Result = when (val outcome = syncPendingLocations()) {
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
