package com.madhi.tracker.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madhi.tracker.R
import com.madhi.tracker.domain.model.TrackPeriod
import com.madhi.tracker.domain.model.TrackingHealth
import com.madhi.tracker.domain.model.TrackingProblem
import com.madhi.tracker.domain.model.TrackingStatus
import com.madhi.tracker.presentation.common.TrackingStatusColors
import com.madhi.tracker.presentation.common.relativeAge
import java.time.Instant

/**
 * L'écran d'accueil de la V1.
 *
 * La carte occupe la zone centrale, comme le veut `arch/09` §2, et le bandeau
 * bas reste compact : l'état du suivi, l'âge de la dernière position, et une
 * action seulement si elle est nécessaire.
 *
 * La carte dessine le tracé sans fond cartographique (ADR-006) : ce qui vient
 * de Room s'affiche hors ligne, ce que des tuiles ne feraient pas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    onFixProblem: (TrackingProblem) -> Unit,
    now: Instant = Instant.now(),
    viewModel: MainViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val track by viewModel.track.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voyage") },
                actions = {
                    // L'icône est tracée dans `res/drawable`, pas importée : la
                    // bibliothèque d'icônes Material entière pour un engrenage
                    // serait une dépendance mal placée. Le mot reste à côté —
                    // un engrenage seul se confond avec bien des choses.
                    TextButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Réglages")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TrackMap(
                points = track,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                loadTile = if (viewModel.tilesEnabled) viewModel::tile else null,
                loadAddress = viewModel::address,
                now = now,
                attribution = viewModel.tileAttribution,
                maxTileZoom = viewModel.tileMaxZoom,
            )

            HorizontalDivider()

            PeriodSelector(
                selected = period,
                onSelect = viewModel::onPeriodSelected,
            )

            StatusBar(
                status = status,
                now = now,
                onStartTracking = viewModel::onStartTracking,
                onFixProblem = onFixProblem,
            )
        }
    }
}

/**
 * Le choix de ce que la carte montre.
 *
 * Posé sous la carte plutôt que par-dessus : `arch/09` §2 veut une carte qui
 * occupe l'écran, et trois boutons flottants lui mangeraient un coin. Il est
 * en tête du bandeau parce qu'il gouverne la carte, pas l'état du suivi.
 *
 * Les libellés sont ceux du site familial (`site/features/period.js`), à une
 * exception près : le site s'arrête à trente jours faute de pouvoir en servir
 * davantage en un appel. Ici la base est locale.
 */
@Composable
private fun PeriodSelector(
    selected: TrackPeriod,
    onSelect: (TrackPeriod) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Défilante plutôt qu'ajustée au pixel : les trois libellés tiennent
            // tout juste sur un écran de 360 points, et la première personne
            // qui agrandit la police du système verrait la rangée se briser.
            .horizontalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrackPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(periodLabel(period), maxLines = 1) },
            )
        }
    }
}

private fun periodLabel(period: TrackPeriod): String = when (period) {
    TrackPeriod.TODAY -> "Aujourd'hui"
    TrackPeriod.LAST_24H -> "24 h"
    TrackPeriod.SEVEN_DAYS -> "7 jours"
    TrackPeriod.EVERYTHING -> "Tout le voyage"
}

@Composable
private fun StatusBar(
    status: TrackingStatus?,
    now: Instant,
    onStartTracking: () -> Unit,
    onFixProblem: (TrackingProblem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (status == null) {
            Text("Lecture de l'état…", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(indicatorColor(status.health)),
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(headline(status), style = MaterialTheme.typography.titleMedium)
        }

        Text(
            "Dernière position : ${relativeAge(status.lastPointAt, now)}",
            style = MaterialTheme.typography.bodyMedium,
        )

        // Une seule action, et seulement quand elle sert à quelque chose
        // (`arch/09` §2). Pas de bouton « synchroniser » ni de compteur de
        // file : ils vivent dans les réglages.
        when (status.health) {
            TrackingHealth.STOPPED -> Button(
                onClick = onStartTracking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Démarrer le suivi") }

            TrackingHealth.ACTION_REQUIRED -> status.mostUrgentProblem?.let { problem ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        problemExplanation(problem),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                    )
                    Button(
                        onClick = { onFixProblem(problem) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Corriger") }
                }
            }

            else -> Unit
        }
    }
}

private fun indicatorColor(health: TrackingHealth): Color = when (health) {
    TrackingHealth.ACTIVE -> TrackingStatusColors.active
    TrackingHealth.OFFLINE -> TrackingStatusColors.degraded
    TrackingHealth.ACTION_REQUIRED -> TrackingStatusColors.broken
    TrackingHealth.STOPPED -> Color.Gray
}

private fun headline(status: TrackingStatus): String = when (status.health) {
    TrackingHealth.ACTIVE -> "Suivi actif"
    // Le message rassure au lieu d'alerter : être hors réseau est le mode de
    // fonctionnement normal du voyage, pas une panne.
    TrackingHealth.OFFLINE -> "Hors ligne — trajet sauvegardé sur le téléphone"
    TrackingHealth.ACTION_REQUIRED -> "Action nécessaire"
    TrackingHealth.STOPPED -> "Suivi arrêté"
}

/**
 * Dit quoi faire, pas ce qui ne va pas. La voyageuse n'a pas à traduire un
 * nom de permission Android en geste concret.
 */
private fun problemExplanation(problem: TrackingProblem): String = when (problem) {
    TrackingProblem.DEVICE_NOT_ACTIVATED ->
        "Ce téléphone n'est pas encore relié au voyage. Saisis le code d'activation."
    TrackingProblem.LOCATION_PERMISSION_MISSING ->
        "L'accès à la position a été retiré. Il faut le réautoriser."
    TrackingProblem.BACKGROUND_LOCATION_PERMISSION_MISSING ->
        "Le suivi s'arrête dès que l'écran s'éteint. Choisis « Toujours autoriser »."
    TrackingProblem.LOCATION_DISABLED ->
        "La localisation du téléphone est désactivée. Rallume-la."
    TrackingProblem.AUTOSTART_BLOCKED ->
        "Le téléphone a redémarré sans relancer le suivi. Autorise le démarrage automatique."
    TrackingProblem.BATTERY_OPTIMIZATION_ENABLED ->
        "L'économie d'énergie interrompt le suivi. Il faut faire une exception."
    TrackingProblem.EXACT_ALARM_NOT_PERMITTED ->
        "Les rappels précis sont bloqués, le suivi devient irrégulier."
    TrackingProblem.AUTHENTICATION_FAILED ->
        "Les positions ne partent plus : ce téléphone doit être réactivé."
    TrackingProblem.NOTIFICATIONS_BLOCKED ->
        "Les notifications sont bloquées, l'état du suivi n'est plus visible."
}
