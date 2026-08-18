package com.madhi.tracker.domain.model

import java.time.Instant

/**
 * Le diagnostic interne exigé par `arch/02` §6. Conservé localement, il
 * répond à la seule question qui compte quand le suivi semble bloqué :
 * est-ce qu'on essaie d'envoyer, et qu'est-ce qui échoue ?
 */
data class SyncJournal(
    val lastAttemptAt: Instant? = null,
    val lastSuccessAt: Instant? = null,

    /**
     * Le code de la dernière erreur, pas l'erreur elle-même.
     *
     * Ce journal sert à afficher « dernière erreur : rate_limited » dans le
     * diagnostic. Reconstruire la hiérarchie `SyncFailure` depuis une chaîne
     * persistée serait lossy — un `ServerError(503)` reviendrait sans son
     * statut — et n'apporterait rien à l'affichage.
     */
    val lastFailureCode: String? = null,

    val lastBatchSize: Int? = null,
    val consecutiveFailures: Int = 0,
) {
    companion object {
        val EMPTY = SyncJournal()
    }
}
