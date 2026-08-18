package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.DeviceActivationGateway
import com.madhi.tracker.application.port.DeviceCredentials
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.ActivationFailure
import com.madhi.tracker.domain.failure
import com.madhi.tracker.domain.model.TrackerEvent
import com.madhi.tracker.domain.success
import javax.inject.Inject

/**
 * Échange le code d'activation saisi contre le token appareil (ADR-004).
 *
 * Cette opération n'est jamais réessayée automatiquement : un code est à
 * usage unique et expire vite, donc une nouvelle tentative demande une
 * action explicite de l'utilisatrice.
 */
class ActivateDevice @Inject constructor(
    private val gateway: DeviceActivationGateway,
    private val credentials: DeviceCredentials,
    private val eventLog: EventLog,
) {

    suspend operator fun invoke(
        activationCode: String,
        deviceName: String,
    ): Outcome<Unit, ActivationFailure> {
        val code = activationCode.trim()
        // Rejeter localement évite de brûler une tentative serveur — et le
        // serveur limite le nombre d'essais.
        if (code.isBlank()) return failure(ActivationFailure.InvalidCode)

        return when (val result = gateway.activate(code, deviceName)) {
            is Outcome.Failure -> {
                // Le code saisi n'apparaît jamais dans le journal : c'est un
                // secret de courte durée, mais un secret quand même.
                eventLog.record(TrackerEvent.DEVICE_ACTIVATED, "echec:${result.reason.code}")
                failure(result.reason)
            }

            is Outcome.Success -> {
                credentials.store(result.value)
                eventLog.record(TrackerEvent.DEVICE_ACTIVATED)
                success(Unit)
            }
        }
    }
}
