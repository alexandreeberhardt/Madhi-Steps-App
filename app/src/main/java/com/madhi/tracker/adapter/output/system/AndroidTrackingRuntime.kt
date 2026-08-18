package com.madhi.tracker.adapter.output.system

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.madhi.tracker.adapter.input.tracking.TrackingForegroundService
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.application.port.TrackingRuntime
import com.madhi.tracker.domain.model.TrackerEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTrackingRuntime @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val eventLog: EventLog,
) : TrackingRuntime {

    /**
     * Démarrer un service de premier plan depuis l'arrière-plan lève une
     * exception si l'application n'est exemptée d'aucune restriction.
     *
     * Le cas se produit exactement quand l'exemption d'optimisation de
     * batterie n'a pas été accordée — c'est-à-dire quand le suivi est déjà
     * fragile. Laisser l'exception remonter ferait planter le receveur
     * d'alarme à chaque réveil, donc transformerait une dégradation en
     * boucle de plantages. On la journalise et on rend la main : le
     * diagnostic signale déjà l'exemption manquante, et le watchdog
     * réessaiera.
     */
    override fun start() {
        try {
            TrackingForegroundService.start(context)
        } catch (e: IllegalStateException) {
            eventLog.record(TrackerEvent.TRACKING_SERVICE_REVIVED, "refuse:${e::class.simpleName}")
        } catch (e: SecurityException) {
            eventLog.record(TrackerEvent.TRACKING_SERVICE_REVIVED, "refuse:securite")
        }
    }

    override fun stop() {
        TrackingForegroundService.stop(context)
    }

    /**
     * `getRunningServices` est déprécié et ne renvoie plus que les services
     * de l'application appelante — ce qui est exactement ce dont on a besoin
     * ici, et reste le seul moyen de savoir si notre propre service tourne.
     */
    @Suppress("DEPRECATION")
    override fun isRunning(): Boolean {
        val manager = ContextCompat.getSystemService(context, ActivityManager::class.java) ?: return false
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == TrackingForegroundService::class.java.name }
    }
}
