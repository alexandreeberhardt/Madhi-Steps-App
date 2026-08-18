package com.madhi.tracker.adapter.input.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

/**
 * Réveil du métronome.
 *
 * Ce receveur ne fait **pas** l'acquisition lui-même : un `BroadcastReceiver`
 * ne dispose que d'une dizaine de secondes, alors qu'un fix GPS froid peut
 * en demander quatre-vingt-dix. Il transmet donc au service de premier plan,
 * qui a le droit de durer et qui porte la capacité d'accès à la localisation.
 */
@AndroidEntryPoint
class CaptureAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            TrackingForegroundService.requestCapture(context)
        } catch (e: IllegalStateException) {
            // Sans exemption d'optimisation de batterie, Android refuse de
            // démarrer un service de premier plan depuis l'arrière-plan.
            // Laisser remonter ferait planter à chaque réveil d'alarme : une
            // dégradation deviendrait une boucle de plantages.
        } catch (e: SecurityException) {
            // Idem, selon la version d'Android et le constructeur.
        }
    }
}
