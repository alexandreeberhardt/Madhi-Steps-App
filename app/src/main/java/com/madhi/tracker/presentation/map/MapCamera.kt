package com.madhi.tracker.presentation.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.madhi.tracker.domain.MapViewport
import com.madhi.tracker.domain.ScreenPoint

/**
 * Ce que la carte regarde, et qui l'a décidé.
 *
 * Deux cadrages coexistent : celui que la carte calcule pour montrer tout le
 * tracé, et celui que la main a choisi. Le second l'emporte tant qu'il existe ;
 * « Recentrer » l'efface et rend la main au premier.
 *
 * L'état est sorti de [TrackMap] pour une raison précise : le gestionnaire de
 * gestes de Compose est une coroutine lancée une fois pour toutes, qui capture
 * les variables de la composition où elle a démarré. Tant que le cadrage
 * automatique était une variable locale, le geste repartait de sa valeur
 * périmée — recentrer, puis effleurer la carte, ramenait l'échelle d'avant.
 * Ici il n'y a plus de copie à périmer : le geste relit l'état.
 */
@Stable
class MapCamera {

    /** Le cadrage choisi à la main, ou `null` si la carte suit le tracé. */
    var manual by mutableStateOf<MapViewport?>(null)
        private set

    /** Le cadrage qui montre tout le tracé, recalculé par la carte. */
    var automatic by mutableStateOf<MapViewport?>(null)
        private set

    /** Ce que la carte affiche vraiment. `null` tant qu'il n'y a rien à montrer. */
    val viewport: MapViewport? get() = manual ?: automatic

    /** Vrai quand la main a pris le contrôle, donc quand « Recentrer » a un sens. */
    val isManual: Boolean get() = manual != null

    /**
     * Le cadrage automatique a changé — nouveau point, écran redimensionné,
     * légende mesurée. Il ne déloge jamais un cadrage choisi à la main : la
     * carte ne doit pas sauter sous les doigts à chaque position reçue.
     */
    fun followTrack(viewport: MapViewport?) {
        automatic = viewport
    }

    /** Rend la main au cadrage automatique. */
    fun recenter() {
        manual = null
    }

    /**
     * Un geste de déplacement et de zoom, appliqué à ce qui est affiché
     * maintenant — pas à ce qui l'était quand le geste a été armé.
     */
    fun onGesture(
        panXPixels: Double,
        panYPixels: Double,
        scale: Double,
        focus: ScreenPoint,
        widthPixels: Double,
        heightPixels: Double,
    ) {
        val current = viewport ?: return
        manual = current
            .pannedBy(panXPixels, panYPixels)
            .zoomedBy(scale, focus, widthPixels, heightPixels)
    }
}

@Composable
fun rememberMapCamera(): MapCamera = remember { MapCamera() }
