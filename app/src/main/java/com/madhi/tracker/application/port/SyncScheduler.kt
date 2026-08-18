package com.madhi.tracker.application.port

/**
 * La synchronisation périodique est indépendante de l'acquisition GPS
 * (`arch/01` §8) : elle continue de vider le backlog même suivi désactivé.
 */
interface SyncScheduler {
    fun ensurePeriodicSyncScheduled()
    fun requestImmediateSync()
}
