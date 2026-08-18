package com.madhi.tracker.application.port

/**
 * Démarrage et arrêt du service de premier plan. Séparé de
 * [CaptureScheduler] parce que le service porte la capacité d'accès à la
 * localisation, alors que l'alarme porte la cadence : deux responsabilités
 * distinctes qui échouent pour des raisons différentes.
 */
interface TrackingRuntime {
    fun start()
    fun stop()
    fun isRunning(): Boolean
}
