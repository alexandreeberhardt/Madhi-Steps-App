package com.madhi.tracker.presentation.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madhi.tracker.application.usecase.BuildDiagnosticsReport
import com.madhi.tracker.application.usecase.ChangeCaptureInterval
import com.madhi.tracker.application.usecase.DiagnosticsReport
import com.madhi.tracker.application.usecase.StartTracking
import com.madhi.tracker.application.usecase.StopTracking
import com.madhi.tracker.domain.model.CaptureInterval
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val buildDiagnosticsReport: BuildDiagnosticsReport,
    private val startTracking: StartTracking,
    private val stopTracking: StopTracking,
    private val changeCaptureInterval: ChangeCaptureInterval,
) : ViewModel() {

    private val _report = MutableStateFlow<DiagnosticsReport?>(null)
    val report: StateFlow<DiagnosticsReport?> = _report.asStateFlow()

    init {
        refresh()
    }

    /**
     * Les permissions et réglages système changent hors de l'application :
     * l'écran relit tout à chaque reprise plutôt que de prétendre les observer.
     */
    fun refresh() {
        viewModelScope.launch { _report.value = buildDiagnosticsReport() }
    }

    fun onStartTracking() {
        viewModelScope.launch {
            startTracking()
            refresh()
        }
    }

    fun onStopTracking() {
        viewModelScope.launch {
            stopTracking()
            refresh()
        }
    }

    fun onIntervalSelected(interval: CaptureInterval) {
        viewModelScope.launch {
            changeCaptureInterval(interval)
            refresh()
        }
    }
}
