package com.madhi.tracker.presentation.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madhi.tracker.application.port.DeviceCredentials
import com.madhi.tracker.application.port.SyncScheduler
import com.madhi.tracker.application.usecase.ActivateDevice
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
class ActivationViewModel @Inject constructor(
    private val activateDevice: ActivateDevice,
    private val credentials: DeviceCredentials,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivationState())
    val state: StateFlow<ActivationState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(alreadyActivated = credentials.isActivated()) }
        }
    }

    fun onCodeChange(code: String) {
        _state.update { it.copy(code = code, error = null) }
    }

    fun onSubmit(deviceName: String) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = activateDevice(_state.value.code, deviceName)) {
                is Outcome.Success -> {
                    // Le backlog accumulé pendant que l'appareil n'était pas
                    // autorisé peut partir immédiatement : c'est souvent la
                    // raison même de la réactivation.
                    syncScheduler.requestImmediateSync()
                    _state.update { it.copy(busy = false, succeeded = true) }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(busy = false, error = result.reason)
                }
            }
        }
    }
}

data class ActivationState(
    val code: String = "",
    val busy: Boolean = false,
    val succeeded: Boolean = false,
    val alreadyActivated: Boolean = false,
    val error: ActivationFailure? = null,
)
