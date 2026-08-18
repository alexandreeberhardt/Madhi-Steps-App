package com.madhi.tracker.adapter.input.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.madhi.tracker.application.usecase.RestoreTracking
import com.madhi.tracker.application.usecase.RestoreTrigger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Reprise après mise à jour de l'APK.
 *
 * Une réinstallation annule les alarmes et peut perdre les tâches
 * planifiées. `arch/01` §8 exige explicitement que l'application vérifie
 * alors que le worker de synchronisation est toujours en place — c'est un
 * cas réel, puisque l'application sera mise à jour à la main pendant le
 * voyage, hors Play Store.
 */
@AndroidEntryPoint
class PackageReplacedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var restoreTracking: RestoreTracking

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        restoreInBackground(restoreTracking, RestoreTrigger.PACKAGE_REPLACED)
    }
}
