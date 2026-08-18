package com.madhi.tracker.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madhi.tracker.application.usecase.ObserveTrackingStatus
import com.madhi.tracker.application.usecase.StartTracking
import com.madhi.tracker.domain.model.TrackingHealth
import com.madhi.tracker.domain.model.TrackingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    observeTrackingStatus: ObserveTrackingStatus,
    private val startTracking: StartTracking,
) : ViewModel() {

    val status: StateFlow<TrackingStatus?> = observeTrackingStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    fun onStartTracking() {
        viewModelScope.launch { startTracking() }
    }

    val health: TrackingHealth? get() = status.value?.health

    private companion object {
        // Assez pour survivre à une rotation d'écran sans relancer les flux.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
