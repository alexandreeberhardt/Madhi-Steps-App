package com.madhi.tracker.adapter.output.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contrat figé par `arch/00` §5 et détaillé dans
 * `arch/13_contrat_api_android_v1.md`. Les champs obligatoires ne changent
 * pas en V2 ; les nouveaux arrivent en optionnels.
 *
 * Les champs optionnels absents ne sont pas sérialisés plutôt que d'être
 * envoyés à `null` : le contrat distingue « inconnu » de « nul ».
 */
@Serializable
data class LocationPointV1Dto(
    val id: String,
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val recordedAt: String,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val speedMps: Float? = null,
    val batteryPercent: Int? = null,
)

@Serializable
data class LocationBatchRequest(
    val points: List<LocationPointV1Dto>,
)

/**
 * `accepted` et `duplicates` sont traités identiquement par l'appelant :
 * dans les deux cas le serveur détient le point. C'est ce qui rend le rejeu
 * sûr après une réponse perdue.
 */
@Serializable
data class LocationBatchResponse(
    val accepted: List<String> = emptyList(),
    val duplicates: List<String> = emptyList(),
    val rejected: List<RejectedPointDto> = emptyList(),
)

@Serializable
data class RejectedPointDto(
    val id: String,
    val reason: String = "unknown",
)

@Serializable
data class ActivationRequest(
    val activationCode: String,
    val deviceName: String,
    val appVersion: String,
)

@Serializable
data class ActivationResponse(
    val deviceId: String,
    val deviceToken: String,
    @SerialName("tripId") val tripId: String,
)
