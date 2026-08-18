package com.madhi.tracker.domain.model

import com.madhi.tracker.domain.error.SyncFailure
import java.time.Instant

/**
 * Le diagnostic interne exigé par `arch/02` §6. Conservé localement, il
 * répond à la seule question qui compte quand le suivi semble bloqué :
 * est-ce qu'on essaie d'envoyer, et qu'est-ce qui échoue ?
 */
data class SyncJournal(
    val lastAttemptAt: Instant? = null,
    val lastSuccessAt: Instant? = null,
    val lastFailure: SyncFailure? = null,
    val lastBatchSize: Int? = null,
    val consecutiveFailures: Int = 0,
) {
    companion object {
        val EMPTY = SyncJournal()
    }
}
