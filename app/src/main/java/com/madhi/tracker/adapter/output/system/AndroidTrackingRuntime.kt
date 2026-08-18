package com.madhi.tracker.adapter.output.system

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.madhi.tracker.adapter.input.tracking.TrackingForegroundService
import com.madhi.tracker.application.port.TrackingRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTrackingRuntime @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TrackingRuntime {

    override fun start() {
        TrackingForegroundService.start(context)
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
