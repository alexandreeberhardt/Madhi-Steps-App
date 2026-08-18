package com.madhi.tracker.domain.model

import java.util.UUID

/**
 * Identifiant généré sur l'appareil au moment de la capture, avant toute
 * écriture. Il ne change jamais.
 *
 * C'est lui qui rend les envois idempotents : le serveur n'insère que les
 * identifiants qu'il ne connaît pas, donc un batch rejoué après une réponse
 * perdue ne crée aucun doublon (ADR-003).
 */
@JvmInline
value class LocationId(val value: String) {

    init {
        require(value.isNotBlank()) { "LocationId ne peut pas être vide" }
    }

    override fun toString(): String = value

    companion object {
        fun random(): LocationId = LocationId(UUID.randomUUID().toString())
    }
}
