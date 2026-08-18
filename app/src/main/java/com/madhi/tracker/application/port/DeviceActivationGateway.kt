package com.madhi.tracker.application.port

import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.ActivationFailure
import com.madhi.tracker.domain.model.DeviceActivation

interface DeviceActivationGateway {
    suspend fun activate(
        activationCode: String,
        deviceName: String,
    ): Outcome<DeviceActivation, ActivationFailure>
}
