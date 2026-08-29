package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.AddressLookup
import com.madhi.tracker.domain.model.Coordinates
import javax.inject.Inject

/**
 * L'adresse d'une position, ou rien.
 *
 * Rien est une réponse acceptable et fréquente, exactement comme pour une
 * tuile absente : la bulle reste utile avec l'heure et les coordonnées.
 */
class LookUpAddress @Inject constructor(
    private val addressLookup: AddressLookup,
) {

    suspend operator fun invoke(coordinates: Coordinates): String? = addressLookup.address(coordinates)
}
