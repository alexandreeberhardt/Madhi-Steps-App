package com.madhi.tracker.adapter.input.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.madhi.tracker.application.usecase.RestoreTracking
import com.madhi.tracker.application.usecase.RestoreTrigger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Reprise après redémarrage du téléphone.
 *
 * Sur MIUI comme sur OxygenOS, ce receveur n'est appelé que si le démarrage
 * automatique a été autorisé à la main. Quand il ne l'est pas, rien
 * n'échoue : il ne se passe simplement rien. C'est pour cette panne
 * silencieuse que `RebootDetection` existe (ADR-007 §3.1).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var restoreTracking: RestoreTracking

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        restoreInBackground(restoreTracking, RestoreTrigger.BOOT)
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            // Délivrée avant le déverrouillage initial sur les appareils
            // chiffrés : elle arrive plus tôt et sert de filet.
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
        )
    }
}
