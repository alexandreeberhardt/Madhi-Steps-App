package com.madhi.tracker.application.port

import com.madhi.tracker.domain.model.Coordinates

/**
 * D'où vient l'adresse d'une position.
 *
 * Comme [TileStore], ce port ne lève jamais et peut répondre `null` : hors
 * réseau — le mode normal du voyage — personne ne sait dire l'adresse, et la
 * bulle affiche alors l'heure et les coordonnées, qui viennent de la base
 * locale. Une adresse est un agrément, jamais un prérequis d'affichage.
 *
 * La question est posée au serveur du voyage, jamais à un tiers : interroger
 * un géocodeur depuis l'appareil livrerait la position exacte et l'adresse IP
 * du réseau mobile traversé (`arch/13` §6).
 */
interface AddressLookup {

    suspend fun address(coordinates: Coordinates): String?
}
