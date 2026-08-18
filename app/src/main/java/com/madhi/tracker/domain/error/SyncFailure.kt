package com.madhi.tracker.domain.error

import kotlin.time.Duration

/**
 * Catégories figées par `arch/03` §10.
 *
 * Aucune branche ne supprime de point : il n'existe aucun chemin de code
 * menant à la suppression d'un point PENDING (ADR-003).
 */
sealed interface SyncFailure {

    /** Code court, stable, stocké en base pour le diagnostic. Jamais de coordonnées. */
    val code: String

    /** Une nouvelle tentative a-t-elle une chance d'aboutir sans intervention humaine ? */
    val isRetryable: Boolean

    data object NoNetwork : SyncFailure {
        override val code = "no_network"
        override val isRetryable = true
    }

    data object Timeout : SyncFailure {
        override val code = "timeout"
        override val isRetryable = true
    }

    /** 401/403 — arrête les retries agressifs, signale l'authentification à corriger. */
    data object Unauthorized : SyncFailure {
        override val code = "unauthorized"
        override val isRetryable = false
    }

    /** 413 — le batch est trop gros, il faut le réduire avant de réessayer. */
    data object BatchTooLarge : SyncFailure {
        override val code = "batch_too_large"
        override val isRetryable = true
    }

    /** 429 — respecter [retryAfter] si le serveur l'indique. */
    data class RateLimited(val retryAfter: Duration?) : SyncFailure {
        override val code = "rate_limited"
        override val isRetryable = true
    }

    data class ServerError(val status: Int) : SyncFailure {
        override val code = "server_error_$status"
        override val isRetryable = true
    }

    /** 400 — c'est un bug côté client, pas un incident réseau. Ne pas boucler dessus. */
    data class RejectedPayload(val status: Int) : SyncFailure {
        override val code = "rejected_payload_$status"
        override val isRetryable = false
    }

    /** Appareil pas encore activé : il n'y a pas de token à présenter. */
    data object NotActivated : SyncFailure {
        override val code = "not_activated"
        override val isRetryable = false
    }

    data class Unexpected(val detail: String) : SyncFailure {
        override val code = "unexpected"
        override val isRetryable = true
    }
}
