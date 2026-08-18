package com.madhi.tracker.adapter.output.scheduling

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.madhi.tracker.adapter.input.sync.SyncWorker
import com.madhi.tracker.application.port.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deux déclencheurs, un seul comportement (ADR-003).
 *
 * L'exécution périodique est le filet de sécurité — quinze minutes est le
 * minimum imposé par WorkManager, ce qui suffit puisque perdre un point est
 * exclu mais l'envoyer avec du retard ne l'est pas. La demande immédiate est
 * le chemin rapide après chaque capture.
 */
@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SyncScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun ensurePeriodicSyncScheduled() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(networkRequired)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        // KEEP et non UPDATE : replanifier à chaque ouverture de
        // l'application remettrait le compteur à zéro et repousserait
        // indéfiniment la prochaine exécution.
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun requestImmediateSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkRequired)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        // KEEP : si un envoi est déjà en cours ou en attente de réseau,
        // en empiler un second ne ferait qu'user la radio.
        workManager.enqueueUniqueWork(
            SyncWorker.IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private val networkRequired: Constraints
        get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private companion object {
        const val PERIOD_MINUTES = 15L
        const val BACKOFF_SECONDS = 60L
    }
}
