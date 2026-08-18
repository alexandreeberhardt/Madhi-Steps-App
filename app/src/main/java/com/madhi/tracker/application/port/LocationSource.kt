package com.madhi.tracker.application.port

import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.LocationAcquisitionFailure
import com.madhi.tracker.domain.model.LocationFix
import kotlin.time.Duration

/**
 * Acquisition ponctuelle, jamais un flux continu.
 *
 * L'appel s'abandonne au bout de [timeout] : le GPS ne doit pas rester
 * allumé indéfiniment parce qu'un point ne se fixe pas (ADR-002).
 */
interface LocationSource {
    suspend fun acquire(timeout: Duration): Outcome<LocationFix, LocationAcquisitionFailure>
}
