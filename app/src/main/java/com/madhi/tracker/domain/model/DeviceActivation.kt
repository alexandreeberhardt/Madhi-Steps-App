package com.madhi.tracker.domain.model

/**
 * Ce que le serveur rend en échange d'un code d'activation.
 *
 * Le token n'est jamais exposé hors du port `DeviceCredentials` : ni le
 * domaine, ni les use cases, ni l'interface ne le manipulent (ADR-004).
 */
data class DeviceActivation(
    val deviceId: String,
    val deviceToken: String,
    val tripId: String,
)
