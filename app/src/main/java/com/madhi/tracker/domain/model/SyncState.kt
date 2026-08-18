package com.madhi.tracker.domain.model

/**
 * Deux états seulement, volontairement (ADR-003).
 *
 * Un point non confirmé par le serveur reste [PENDING], sans exception et
 * sans limite de temps. L'échec est du diagnostic — porté par
 * `attemptCount`, `lastAttemptAt` et `lastErrorCode` — jamais un état de
 * cycle de vie dont on risquerait d'oublier de sortir.
 */
enum class SyncState {
    PENDING,
    SYNCED,
}
