package com.madhi.tracker.application.port

import com.madhi.tracker.domain.model.DeviceActivation

/**
 * Seul point d'accès au token appareil (ADR-004).
 *
 * Aucune autre couche ne le manipule : ni le domaine, ni les use cases, ni
 * l'interface. Seul l'adaptateur réseau le lit, au moment de construire
 * l'en-tête `Authorization`. Il n'apparaît dans aucun log ni aucun écran.
 */
interface DeviceCredentials {

    suspend fun store(activation: DeviceActivation)

    suspend fun isActivated(): Boolean

    suspend fun deviceId(): String?

    suspend fun tripId(): String?

    /**
     * Réservé à l'adaptateur réseau. Volontairement nommé pour qu'un appel
     * depuis une autre couche saute aux yeux en relecture.
     */
    suspend fun authorizationHeaderValue(): String?

    suspend fun clear()
}
