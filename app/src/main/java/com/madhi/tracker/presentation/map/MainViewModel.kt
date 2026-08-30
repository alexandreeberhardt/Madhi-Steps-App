package com.madhi.tracker.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madhi.tracker.application.usecase.LoadMapTile
import com.madhi.tracker.application.usecase.LookUpAddress
import com.madhi.tracker.application.usecase.ObserveTrack
import com.madhi.tracker.application.usecase.ObserveTrackingStatus
import com.madhi.tracker.application.usecase.StartTracking
import com.madhi.tracker.domain.TileId
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.TrackPeriod
import com.madhi.tracker.domain.model.TrackPoint
import com.madhi.tracker.domain.model.TrackingHealth
import com.madhi.tracker.domain.model.TrackingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    observeTrackingStatus: ObserveTrackingStatus,
    observeTrack: ObserveTrack,
    private val loadMapTile: LoadMapTile,
    private val lookUpAddress: LookUpAddress,
    private val startTracking: StartTracking,
) : ViewModel() {

    val status: StateFlow<TrackingStatus?> = observeTrackingStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    private val selectedPeriod = MutableStateFlow(TrackPeriod.SEVEN_DAYS)

    val period: StateFlow<TrackPeriod> = selectedPeriod

    /**
     * Le tracé dessiné par la carte, pour la période choisie.
     *
     * `WhileSubscribed` compte double ici : écran éteint, la requête cesse et
     * la base n'est plus relue à chaque position enregistrée. La carte ne doit
     * rien coûter quand personne ne la regarde.
     */
    val track: StateFlow<List<TrackPoint>> = selectedPeriod
        .flatMapLatest { observeTrack(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    /**
     * Le reste du voyage, dessiné en fond derrière la période choisie.
     *
     * Des coordonnées et rien d'autre. Ce tracé-là ne se touche pas et ne code
     * aucun état : lui donner le type du vrai tracé inviterait à s'en servir un
     * jour pour le cadrage ou pour le pointage, ce qui est précisément ce qu'il
     * ne doit jamais faire.
     *
     * Sur « Tout le voyage », il n'y a pas de fond : ce serait le tracé
     * lui-même, dessiné deux fois et relu de la base pour rien.
     */
    val backgroundTrack: StateFlow<List<Coordinates>> = selectedPeriod
        .flatMapLatest { period ->
            if (period == TrackPeriod.EVERYTHING) {
                flowOf(emptyList())
            } else {
                observeTrack(TrackPeriod.EVERYTHING)
                    .map { points -> points.map(TrackPoint::coordinates) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    fun onPeriodSelected(period: TrackPeriod) {
        selectedPeriod.value = period
    }

    val tilesEnabled: Boolean get() = loadMapTile.isEnabled

    val tileAttribution: String get() = loadMapTile.attribution

    val tileMaxZoom: Int get() = loadMapTile.maxZoom

    suspend fun tile(id: TileId): ByteArray? = loadMapTile(id)

    /** L'adresse d'un point du tracé, ou `null` : hors ligne, c'est la norme. */
    suspend fun address(coordinates: Coordinates): String? = lookUpAddress(coordinates)

    fun onStartTracking() {
        viewModelScope.launch { startTracking() }
    }

    val health: TrackingHealth? get() = status.value?.health

    private companion object {
        // Assez pour survivre à une rotation d'écran sans relancer les flux.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
