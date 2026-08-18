package com.madhi.tracker.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madhi.tracker.application.port.EnvironmentSnapshot
import com.madhi.tracker.application.port.OnboardingStore
import com.madhi.tracker.application.port.TrackingEnvironment
import com.madhi.tracker.application.usecase.ActivateDevice
import com.madhi.tracker.application.usecase.RunSetupCheck
import com.madhi.tracker.application.usecase.SetupCheckResult
import com.madhi.tracker.application.usecase.StartTracking
import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.ActivationFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val environment: TrackingEnvironment,
    private val activateDevice: ActivateDevice,
    private val runSetupCheck: RunSetupCheck,
    private val startTracking: StartTracking,
    private val onboardingStore: OnboardingStore,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState(environment = environment.snapshot()))
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    /** Les permissions changent hors de l'application : on relit à chaque reprise. */
    fun refreshEnvironment() {
        _state.update { it.copy(environment = environment.snapshot()) }
    }

    fun goTo(step: OnboardingStep) {
        refreshEnvironment()
        _state.update { it.copy(step = step) }
    }

    fun onActivationCodeChanged(code: String) {
        _state.update { it.copy(activationCode = code, activationError = null) }
    }

    fun onActivate(deviceName: String) {
        val code = _state.value.activationCode
        _state.update { it.copy(activating = true, activationError = null) }

        viewModelScope.launch {
            when (val result = activateDevice(code, deviceName)) {
                is Outcome.Success -> _state.update {
                    it.copy(activating = false, activated = true, step = OnboardingStep.BATTERY)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(activating = false, activationError = result.reason)
                }
            }
        }
    }

    fun onRunSetupCheck() {
        _state.update { it.copy(checking = true, checkResult = null) }
        viewModelScope.launch {
            val result = runSetupCheck()
            _state.update { it.copy(checking = false, checkResult = result) }
        }
    }

    /**
     * Terminer reste possible même si le test échoue : la voyageuse peut être
     * hors réseau au moment de la configuration, et bloquer l'accès à
     * l'application ne l'aiderait en rien. Le diagnostic gardera la trace.
     */
    fun onFinish(onDone: () -> Unit) {
        viewModelScope.launch {
            startTracking()
            onboardingStore.markCompleted()
            onDone()
        }
    }
}

enum class OnboardingStep {
    WELCOME,
    LOCATION,
    BACKGROUND_LOCATION,
    ACTIVATION,
    BATTERY,
    CHECK,
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val environment: EnvironmentSnapshot,
    val activationCode: String = "",
    val activating: Boolean = false,
    val activated: Boolean = false,
    val activationError: ActivationFailure? = null,
    val checking: Boolean = false,
    val checkResult: SetupCheckResult? = null,
)
