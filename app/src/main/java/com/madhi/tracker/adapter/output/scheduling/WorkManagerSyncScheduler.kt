package com.madhi.tracker.adapter.output.scheduling

import android.content.Context
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
        // Pas de backoff : le worker ne renvoie jamais `retry`, et la période
        // de quinze minutes est elle-même le mécanisme de réessai.
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(networkRequired)
            .build()

        // UPDATE conserve la planification en cours mais remplace la
        // spécification. C'est ce qui permet à une version corrigée de
        // reprendre la main sur un travail hérité, sans attendre que
        // l'utilisatrice réinstalle — elle ne réinstallera pas.
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun requestImmediateSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkRequired)
            .build()

        // REPLACE et non KEEP.
        //
        // Avec KEEP, un travail hérité coincé sur une contrainte de délai
        // bloque indéfiniment toute nouvelle tentative : c'est exactement ce
        // qui s'est produit le 19 août, où le backlog d'une nuit refusait de
        // partir alors que le réseau était revenu.
        //
        // Remplacer est sans danger : la synchronisation confirme chaque lot
        // au fur et à mesure, donc une exécution interrompue reprend là où
        // elle s'était arrêtée, et aucun point n'est perdu.
        workManager.enqueueUniqueWork(
            SyncWorker.IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private val networkRequired: Constraints
        get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private companion object {
        const val PERIOD_MINUTES = 15L
    }
}
