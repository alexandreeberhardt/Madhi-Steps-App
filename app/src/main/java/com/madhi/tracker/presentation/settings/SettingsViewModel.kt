package com.madhi.tracker.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madhi.tracker.application.port.DeviceCredentials
import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.application.port.SyncJournalStore
import com.madhi.tracker.application.port.TrackingIntentStore
import com.madhi.tracker.application.usecase.ChangeCaptureInterval
import com.madhi.tracker.application.usecase.StartTracking
import com.madhi.tracker.application.usecase.StopTracking
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.SyncJournal
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.infrastructure.config.AppConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val trackingIntentStore: TrackingIntentStore,
    private val credentials: DeviceCredentials,
    private val locationStore: LocationStore,
    private val syncJournalStore: SyncJournalStore,
    private val changeCaptureInterval: ChangeCaptureInterval,
    private val startTracking: StartTracking,
    private val stopTracking: StopTracking,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    intent = trackingIntentStore.read(),
                    pendingCount = locationStore.pendingCount(),
                    oldestPendingAt = locationStore.oldestPendingRecordedAt(),
                    syncJournal = syncJournalStore.read(),
                    deviceActivated = credentials.isActivated(),
                    loaded = true,
                )
            }
        }
    }

    fun onIntervalSelected(interval: CaptureInterval) {
        viewModelScope.launch {
            changeCaptureInterval(interval)
            refresh()
        }
    }

    fun onToggleTracking() {
        viewModelScope.launch {
            if (_state.value.intent.enabled) stopTracking() else startTracking()
            refresh()
        }
    }
}

data class SettingsState(
    val loaded: Boolean = false,
    val intent: TrackingIntent = TrackingIntent.INITIAL,
    val pendingCount: Int = 0,
    val oldestPendingAt: Instant? = null,
    val syncJournal: SyncJournal = SyncJournal.EMPTY,
    val deviceActivated: Boolean = false,
    val appVersion: String = AppConfig.appVersion,
    val updatePageUrl: String = AppConfig.updatePageUrl,
)
