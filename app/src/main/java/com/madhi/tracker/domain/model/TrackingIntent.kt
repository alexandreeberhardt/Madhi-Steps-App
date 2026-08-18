package com.madhi.tracker.domain.model

/**
 * L'intention de l'utilisatrice, persistée et restaurée après redémarrage.
 *
 * Elle est distincte du fait que le service tourne réellement : fermer
 * l'application ne doit pas arrêter le suivi, et MIUI peut tuer le service
 * sans que l'intention change. C'est cet écart entre intention et réalité
 * que le watchdog corrige.
 */
data class TrackingIntent(
    val enabled: Boolean,
    val captureInterval: CaptureInterval,
) {
    companion object {
        val INITIAL = TrackingIntent(enabled = false, captureInterval = CaptureInterval.DEFAULT)
    }
}
