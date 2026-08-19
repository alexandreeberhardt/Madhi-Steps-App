package com.madhi.tracker.application.port

import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.LocationAcquisitionFailure
import com.madhi.tracker.domain.model.LocationFix
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Le fournisseur de localisation du système, sous ses deux formes.
 *
 * [stream] est le mécanisme principal du suivi depuis l'échec du test T1 :
 * confier la cadence au sous-système de localisation plutôt qu'à
 * `AlarmManager`, parce que celui-ci se fait regrouper dans les lots
 * d'économie d'énergie du constructeur — l'alarme demandée exacte revenait
 * avec une fenêtre de 225 secondes, sans que l'application puisse le savoir.
 *
 * [acquire] reste nécessaire pour le test de fin d'onboarding et comme filet
 * lorsque le flux n'a rien livré depuis trop longtemps.
 */
interface LocationSource {

    /**
     * Flux de positions à la cadence demandée. La souscription doit être
     * relâchée à l'annulation, sinon le récepteur reste allumé.
     */
    fun stream(interval: Duration): Flow<LocationFix>

    suspend fun acquire(timeout: Duration): Outcome<LocationFix, LocationAcquisitionFailure>
}
