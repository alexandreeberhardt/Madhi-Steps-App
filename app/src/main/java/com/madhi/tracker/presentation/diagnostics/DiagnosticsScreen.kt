package com.madhi.tracker.presentation.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import com.madhi.tracker.application.usecase.DiagnosticsReport
import com.madhi.tracker.domain.model.TrackingHealth
import com.madhi.tracker.presentation.common.CaptureIntervalChips
import com.madhi.tracker.presentation.common.TrackingStatusColors
import java.time.Duration
import java.time.Instant

/**
 * Écran volontairement technique (`arch/09` §5) : c'est l'outil de
 * dépannage, pas l'écran d'accueil. Il affiche des faits bruts, sans
 * enrobage, parce qu'il sera lu à voix haute au téléphone depuis la Norvège.
 */
@Composable
fun DiagnosticsScreen(
    onRequestPermissions: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val report by viewModel.report.collectAsStateWithLifecycle()

    // Idem : au retour d'un écran de réglages système, le rapport doit
    // refléter le nouvel état sans intervention.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Sans cette marge, le contenu passe sous la barre d'état et
                // sous la barre de navigation gestuelle : le bouton principal
                // devient difficile à atteindre, ce qu'un test sur appareil
                // réel a montré avant qu'un test automatisé ne le puisse.
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Diagnostic", style = MaterialTheme.typography.headlineSmall)

            val current = report
            if (current == null) {
                Text("Lecture de l'état…")
                return@Column
            }

            HealthBanner(current)
            TrackingControls(current, viewModel)
            IntervalSelector(current, viewModel)
            CoverageCard(current)
            EnvironmentCard(current, onRequestPermissions)
            SyncCard(current)

            OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) {
                Text("Rafraîchir")
            }
        }
    }
}

@Composable
private fun HealthBanner(report: DiagnosticsReport) {
    val (color, label) = when (report.status.health) {
        TrackingHealth.ACTIVE -> TrackingStatusColors.active to "Suivi actif"
        TrackingHealth.OFFLINE -> TrackingStatusColors.degraded to "Hors ligne — trajet sauvegardé"
        TrackingHealth.ACTION_REQUIRED -> TrackingStatusColors.broken to "Action nécessaire"
        TrackingHealth.STOPPED -> Color.Gray to "Suivi arrêté"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
            report.status.mostUrgentProblem?.let {
                Text("À corriger : ${it.name}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Dernière position : ${relativeAge(report.status.lastPointAt, report.generatedAt)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TrackingControls(report: DiagnosticsReport, viewModel: DiagnosticsViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = viewModel::onStartTracking,
            enabled = !report.intent.enabled,
            modifier = Modifier.weight(1f),
        ) { Text("Démarrer") }

        OutlinedButton(
            onClick = viewModel::onStopTracking,
            enabled = report.intent.enabled,
            modifier = Modifier.weight(1f),
        ) { Text("Arrêter") }
    }
}

@Composable
private fun IntervalSelector(report: DiagnosticsReport, viewModel: DiagnosticsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Fréquence", style = MaterialTheme.typography.labelLarge)
        CaptureIntervalChips(
            selected = report.intent.captureInterval,
            onSelect = viewModel::onIntervalSelected,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CoverageCard(report: DiagnosticsReport) {
    DiagnosticCard("Couverture (dernière heure)") {
        val coverage = report.coverage
        Line("Points attendus", coverage.expected.toString())
        Line("Points enregistrés", coverage.actual.toString())
        Line(
            "Taux",
            coverage.ratio?.let { "${(it * 100).toInt()} %" } ?: "—",
            highlight = coverage.isDegraded,
        )
        Line("Service en cours", report.serviceRunning.yesNo())
    }
}

@Composable
private fun EnvironmentCard(report: DiagnosticsReport, onRequestPermissions: () -> Unit) {
    DiagnosticCard("Système") {
        val environment = report.environment
        Line("Localisation (précise)", environment.hasForegroundLocationPermission.yesNo(), highlight = !environment.hasForegroundLocationPermission)
        Line("Localisation (arrière-plan)", environment.hasBackgroundLocationPermission.yesNo(), highlight = !environment.hasBackgroundLocationPermission)
        Line("Notifications", environment.hasNotificationPermission.yesNo(), highlight = !environment.hasNotificationPermission)
        Line("GPS activé", environment.isLocationEnabled.yesNo(), highlight = !environment.isLocationEnabled)
        Line("Exemption batterie", environment.isIgnoringBatteryOptimizations.yesNo(), highlight = !environment.isIgnoringBatteryOptimizations)
        Line("Alarmes exactes", environment.canScheduleExactAlarms.yesNo(), highlight = !environment.canScheduleExactAlarms)
        Line("Réseau", environment.isOnline.yesNo())
        Line("Batterie", environment.batteryPercent?.let { "$it %" } ?: "—")

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
            Text("Vérifier les autorisations")
        }
    }
}

@Composable
private fun SyncCard(report: DiagnosticsReport) {
    DiagnosticCard("Synchronisation") {
        Line("Appareil activé", report.deviceActivated.yesNo(), highlight = !report.deviceActivated)
        Line("Points en attente", report.status.pendingCount.toString())
        Line("Plus ancien en attente", relativeAge(report.oldestPendingAt, report.generatedAt))
        Line("Dernier essai", relativeAge(report.syncJournal.lastAttemptAt, report.generatedAt))
        Line("Dernier succès", relativeAge(report.syncJournal.lastSuccessAt, report.generatedAt))
        Line("Dernière erreur", report.syncJournal.lastFailureCode ?: "—")
        Line("Échecs consécutifs", report.syncJournal.consecutiveFailures.toString())
    }
}

@Composable
private fun DiagnosticCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            content()
        }
    }
}

@Composable
private fun Line(label: String, value: String, highlight: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = if (highlight) TrackingStatusColors.broken else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun Boolean.yesNo(): String = if (this) "oui" else "NON"

private fun relativeAge(instant: Instant?, now: Instant): String {
    if (instant == null) return "jamais"
    val minutes = Duration.between(instant, now).toMinutes()
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 60 * 24 -> "il y a ${minutes / 60} h"
        else -> "il y a ${minutes / (60 * 24)} j"
    }
}
