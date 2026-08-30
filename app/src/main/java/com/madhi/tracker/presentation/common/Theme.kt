package com.madhi.tracker.presentation.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Palette fixe, sans couleur dynamique Material You.
 *
 * L'écran principal encode un état critique par la couleur (suivi actif,
 * hors ligne, action nécessaire). Une palette qui suit le fond d'écran
 * rendrait ce code couleur instable, et l'écran doit rester lisible en plein
 * soleil sur un guidon.
 */
private val TrackingActive = Color(0xFF2E7D32)
private val TrackingDegraded = Color(0xFFE65100)
private val TrackingBroken = Color(0xFFC62828)

/**
 * Le code couleur du tracé, et la seule chose que la carte dit de la
 * synchronisation : bleu, le serveur a le point ; orange, il n'est encore
 * que sur le téléphone. Même orange que l'état « hors ligne » du bandeau,
 * pour qu'un seul mot de vocabulaire visuel suffise.
 *
 * Ces deux teintes sont choisies pour rester lisibles sur le fond clair
 * comme sur le fond sombre de la carte, et ne changent donc pas avec le
 * thème.
 */
private val TrackSynced = Color(0xFF1E88E5)
private val TrackPending = Color(0xFFF57C00)

/**
 * Le reste du voyage, dessiné en fond derrière la période choisie.
 *
 * Gris et translucide, parce qu'il n'est là que pour situer : sur « aujourd'hui »
 * on doit voir d'un coup d'oeil où la journée se place dans le trajet, sans que
 * le trajet vienne disputer la lecture de la journée. Il ne code aucun état, il
 * ne se touche pas, et il ne doit jamais se confondre avec les deux teintes
 * ci-dessus.
 */
private val TrackBackground = Color(0x99616161)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E7A),
    onPrimary = Color.White,
    error = TrackingBroken,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ECCE8),
    onPrimary = Color(0xFF00344A),
    error = Color(0xFFFF8A80),
)

/** Couleurs d'état du suivi, stables quel que soit le thème. */
object TrackingStatusColors {
    val active = TrackingActive
    val degraded = TrackingDegraded
    val broken = TrackingBroken
}

/** Couleurs du tracé, stables quel que soit le thème. */
object TrackColors {
    val synced = TrackSynced
    val pending = TrackPending
    val background = TrackBackground
}

@Composable
fun MadhiTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
