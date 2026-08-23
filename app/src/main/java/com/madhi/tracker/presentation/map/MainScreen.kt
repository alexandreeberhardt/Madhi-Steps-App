package com.madhi.tracker.presentation.map

import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voyage") },
                actions = {
                    // Un libellé plutôt qu'une icône : la bibliothèque
                    // d'icônes Material entière pour un seul engrenage serait
                    // une dépendance mal placée, et le mot est plus clair.
                    TextButton(onClick = onOpenSettings) { Text("Réglages") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TrackMap(
                points = track,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                loadTile = if (viewModel.tilesEnabled) viewModel::tile else null,
                attribution = viewModel.tileAttribution,
                maxTileZoom = viewModel.tileMaxZoom,
            )

            HorizontalDivider()

            StatusBar(
                status = status,
                now = now,
                onStartTracking = viewModel::onStartTracking,
                onFixProblem = onFixProblem,
            )
        }
    }
}

@Composable
private fun StatusBar(
    status: TrackingStatus?,
    now: Instant,
    onStartTracking: () -> Unit,
    onFixProblem: (TrackingProblem) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
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
