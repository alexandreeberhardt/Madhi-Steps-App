package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.DeviceCredentials
import com.madhi.tracker.domain.error.SyncFailure
import javax.inject.Inject

/**
 * Le test de fin d'onboarding décrit par `arch/09` §6, écran 5.
 *
 * Il ne simule rien : il capture une vraie position et tente un vrai envoi.
 * C'est la seule façon de savoir, avant le départ, que la chaîne complète
 * fonctionne sur ce téléphone-là, avec ces réglages-là.
 */
class RunSetupCheck @Inject constructor(
    private val captureLocation: CaptureLocation,
    private val syncPendingLocations: SyncPendingLocations,
    private val credentials: DeviceCredentials,
) {

    suspend operator fun invoke(): SetupCheckResult {
        val capture = captureLocation()

        val locationOk = capture is CaptureResult.Captured
        val locationDetail = when (capture) {
            is CaptureResult.Captured -> null
            is CaptureResult.Failed -> capture.failure.code
            is CaptureResult.Rejected -> capture.rejection.code
        }

        if (!credentials.isActivated()) {
            return SetupCheckResult(
                locationOk = locationOk,
                locationDetail = locationDetail,
                serverOk = false,
                serverDetail = SyncFailure.NotActivated.code,
            )
        }

        val sync = syncPendingLocations()
        return SetupCheckResult(
            locationOk = locationOk,
            locationDetail = locationDetail,
            // NothingToDo est un succès : le serveur n'a rien à recevoir
            // parce que tout est déjà parti.
            serverOk = sync !is SyncOutcome.Failed,
            serverDetail = (sync as? SyncOutcome.Failed)?.failure?.code,
        )
    }
}

data class SetupCheckResult(
    val locationOk: Boolean,
    val locationDetail: String?,
    val serverOk: Boolean,
    val serverDetail: String?,
) {
    val isReady: Boolean get() = locationOk && serverOk
}
