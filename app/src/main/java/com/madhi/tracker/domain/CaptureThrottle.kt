package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.CaptureInterval
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/**
 * Écarte une position qui arrive trop tôt après la précédente.
 *
 * Nécessaire parce que l'application s'abonne à **deux** fournisseurs, GPS et
 * réseau, et que chacun livre indépendamment à la cadence demandée. Sans
 * filtre, on enregistre donc deux fois trop de points — mesuré à 205 % de la
 * couverture attendue sur appareil réel.
 *
 * Le filtre est volontairement tolérant : le système a le droit de livrer un
 * peu en avance, et l'objectif produit dit « environ » cinq minutes. Écarter
 * un point n'est jamais grave — le suivant arrivera — alors qu'en écarter un
 * de trop créerait un trou.
 */
object CaptureThrottle {

    fun isRedundant(
        lastRecordedAt: Instant?,
        candidateAt: Instant,
        interval: CaptureInterval,
    ): Boolean {
        if (lastRecordedAt == null) return false

        val elapsed = (candidateAt.toEpochMilli() - lastRecordedAt.toEpochMilli()).milliseconds
        // Une position antérieure au dernier point enregistré est un doublon
        // livré en retard par l'autre fournisseur.
        if (elapsed.isNegative()) return true

        return elapsed < interval.duration * TOLERANCE
    }

    /**
     * On accepte une livraison jusqu'à vingt pour cent en avance. Au-delà,
     * c'est le second fournisseur qui parle, pas une avance du système.
     */
    private const val TOLERANCE = 0.8
}
