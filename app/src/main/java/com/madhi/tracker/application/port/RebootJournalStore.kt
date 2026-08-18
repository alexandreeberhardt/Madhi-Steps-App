package com.madhi.tracker.application.port

import com.madhi.tracker.domain.model.RebootJournal
import java.time.Instant
import kotlin.time.Duration

interface RebootJournalStore {
    suspend fun read(): RebootJournal

    /** Appelé à chaque signe de vie : ouverture de l'app, capture, synchronisation. */
    suspend fun recordAlive(at: Instant, uptime: Duration)

    suspend fun recordBootHandled(at: Instant)
}
