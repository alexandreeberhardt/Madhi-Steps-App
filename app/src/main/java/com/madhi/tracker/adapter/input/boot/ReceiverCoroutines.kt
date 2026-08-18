package com.madhi.tracker.adapter.input.boot

import android.content.BroadcastReceiver
import com.madhi.tracker.application.usecase.RestoreTracking
import com.madhi.tracker.application.usecase.RestoreTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * `goAsync` prolonge la vie du receveur le temps d'une opération courte.
 *
 * La reprise ne fait que de la lecture locale et de la planification : elle
 * tient largement dans le budget d'une dizaine de secondes. C'est aussi
 * pourquoi elle ne tente aucune acquisition GPS, qui demanderait bien
 * davantage.
 */
internal fun BroadcastReceiver.restoreInBackground(
    restoreTracking: RestoreTracking,
    trigger: RestoreTrigger,
) {
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            restoreTracking(trigger)
        } catch (e: Exception) {
            // Un échec de reprise ne doit pas faire planter le processus au
            // démarrage du téléphone. L'ouverture de l'application rattrapera.
        } finally {
            pendingResult.finish()
        }
    }
}
