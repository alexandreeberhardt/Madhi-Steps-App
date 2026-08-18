package com.madhi.tracker.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madhi.tracker.application.port.OnboardingStore
import com.madhi.tracker.application.port.TrackingEnvironment
import com.madhi.tracker.application.usecase.RestoreTracking
import com.madhi.tracker.application.usecase.RestoreTrigger
import com.madhi.tracker.domain.model.DeviceVendor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val onboardingStore: OnboardingStore,
    private val restoreTracking: RestoreTracking,
    private val environment: TrackingEnvironment,
) : ViewModel() {

    private val _destination = MutableStateFlow<RootDestination>(RootDestination.Loading)
    val destination: StateFlow<RootDestination> = _destination.asStateFlow()

    val vendor: DeviceVendor get() = environment.snapshot().vendor

    init {
        viewModelScope.launch {
            // Rattrapage à chaque ouverture : dernier filet quand le démarrage
            // automatique a été bloqué ou qu'une mise à jour d'APK a effacé
            // les tâches planifiées (`arch/01` §8).
            runCatching { restoreTracking(RestoreTrigger.APP_OPENED) }

            _destination.value = if (onboardingStore.isCompleted()) {
                RootDestination.Diagnostics
            } else {
                RootDestination.Onboarding
            }
        }
    }

    fun onOnboardingFinished() {
        _destination.value = RootDestination.Diagnostics
    }
}

sealed interface RootDestination {
    data object Loading : RootDestination
    data object Onboarding : RootDestination
    data object Diagnostics : RootDestination
}
