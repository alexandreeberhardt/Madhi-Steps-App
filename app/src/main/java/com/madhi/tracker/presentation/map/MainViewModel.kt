package com.madhi.tracker.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madhi.tracker.application.usecase.LoadMapTile
import com.madhi.tracker.application.usecase.ObserveRecentTrack
import com.madhi.tracker.application.usecase.ObserveTrackingStatus
import com.madhi.tracker.application.usecase.StartTracking
import com.madhi.tracker.domain.TileId
import com.madhi.tracker.domain.model.TrackPoint
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
    observeRecentTrack: ObserveRecentTrack,
    private val loadMapTile: LoadMapTile,
    private val startTracking: StartTracking,
) : ViewModel() {

    val status: StateFlow<TrackingStatus?> = observeTrackingStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    /**
     * Le tracé dessiné par la carte.
     *
     * `WhileSubscribed` compte double ici : écran éteint, la requête cesse et
     * la base n'est plus relue à chaque position enregistrée. La carte ne doit
     * rien coûter quand personne ne la regarde.
     */
    val track: StateFlow<List<TrackPoint>> = observeRecentTrack()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    val tilesEnabled: Boolean get() = loadMapTile.isEnabled

    val tileAttribution: String get() = loadMapTile.attribution

    suspend fun tile(id: TileId): ByteArray? = loadMapTile(id)

    fun onStartTracking() {
        viewModelScope.launch { startTracking() }
    }

    val health: TrackingHealth? get() = status.value?.health

    private companion object {
        // Assez pour survivre à une rotation d'écran sans relancer les flux.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
