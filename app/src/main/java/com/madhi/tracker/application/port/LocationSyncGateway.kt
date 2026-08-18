package com.madhi.tracker.application.port

import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.SyncFailure
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint

interface LocationSyncGateway {
    suspend fun upload(points: List<LocationPoint>): Outcome<BatchAcknowledgement, SyncFailure>
}

/**
 * Réponse du serveur à un batch.
 *
 * [accepted] et [duplicates] sont traités identiquement par l'appelant :
 * dans les deux cas le serveur détient le point. C'est ce qui rend le rejeu
 * sûr après une réponse perdue (contrat API §5).
 */
data class BatchAcknowledgement(
    val accepted: List<LocationId>,
    val duplicates: List<LocationId>,
    val rejected: List<RejectedPoint>,
) {
    /** Tout ce que le serveur détient désormais, donc marquable SYNCED. */
    val stored: List<LocationId> get() = accepted + duplicates
}

data class RejectedPoint(
    val id: LocationId,
    val reason: String,
)
