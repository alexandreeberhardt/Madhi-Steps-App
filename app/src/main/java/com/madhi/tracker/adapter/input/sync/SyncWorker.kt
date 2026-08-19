package com.madhi.tracker.adapter.input.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.madhi.tracker.application.usecase.RestoreTracking
import com.madhi.tracker.application.usecase.RestoreTrigger
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

    /**
     * Ce worker ne renvoie **jamais** `retry`.
     *
     * Le backoff exponentiel de WorkManager est un piège ici. Après une nuit
     * hors réseau, il atteint plusieurs heures — plafonnées à cinq — et le
     * travail reste bloqué sur une contrainte `TIMING_DELAY` même une fois le
     * réseau revenu. Comme la demande immédiate utilise `KEEP`, les nouvelles
     * tentatives sont alors ignorées au profit de celle qui attend.
     *
     * Le cas réel : trois jours sans réseau, puis vingt minutes de wifi dans
     * un café, et rien ne part. Observé sur appareil le 19 août 2026.
     *
     * L'exécution périodique de quinze minutes **est** le mécanisme de
     * réessai. Un backoff par-dessus n'ajoute rien et retire beaucoup.
     * Aucun point n'est perdu : ils restent en attente jusqu'à confirmation.
     */
    private suspend fun syncOutcome(): Result {
        syncPendingLocations()
        return Result.success()
    }

    companion object {
        const val PERIODIC_WORK_NAME = "sync-pending-locations-periodic"
        const val IMMEDIATE_WORK_NAME = "sync-pending-locations-now"
    }
}
