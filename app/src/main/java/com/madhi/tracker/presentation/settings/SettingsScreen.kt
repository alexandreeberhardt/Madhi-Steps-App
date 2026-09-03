package com.madhi.tracker.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madhi.tracker.presentation.common.CaptureIntervalChips
import com.madhi.tracker.presentation.common.relativeAge
import java.time.Instant

/**
 * Réglages volontairement utilitaires (`arch/09` §5).
 *
 * Aucune préférence esthétique, aucune option qui ne change rien au
 * fonctionnement réel. Tout ce qui est ici agit sur le suivi ou aide à le
 * dépanner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenActivation: () -> Unit,
    now: Instant = Instant.now(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } },
            )
        },
    ) { padding ->
        // Replié par défaut, et à chaque retour sur l'écran : l'état courant
        // est ce qu'on vient voir, le reste est du dépannage.
        var autresOuverts by rememberSaveable { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section("Suivi") {
                Text("Fréquence de localisation", style = MaterialTheme.typography.bodyLarge)
                CaptureIntervalChips(
                    selected = state.intent.captureInterval,
                    onSelect = viewModel::onIntervalSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("Synchronisation") {
                Line("Points en attente", state.pendingCount.toString())
                Line("Plus ancien en attente", relativeAge(state.oldestPendingAt, now))
                Line("Dernier envoi réussi", relativeAge(state.syncJournal.lastSuccessAt, now))
            }

            AutresReglages(expanded = autresOuverts, onToggle = { autresOuverts = !autresOuverts })

            if (autresOuverts) {
                Section("Appareil") {
                    Line("Relié au voyage", if (state.deviceActivated) "oui" else "non")
                    OutlinedButton(onClick = onOpenActivation, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (state.deviceActivated) {
                                "Réactiver l'appareil"
                            } else {
                                "Activer l'appareil"
                            },
                        )
                    }
                }

                Section("Diagnostic") {
                    Text(
                        "État détaillé du GPS, du réseau, des autorisations et du serveur.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                        Text("Ouvrir le diagnostic")
                    }
                }

                Section("Application") {
                    Line("Version", state.appVersion)
                    OutlinedButton(
                        onClick = { uriHandler.openUri(state.updatePageUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Installer la mise à jour")
                    }
                    Text(
                        "Les positions sont envoyées uniquement au serveur du voyage. " +
                            "Aucun service tiers ne les reçoit, et aucune coordonnée n'est " +
                            "écrite dans les journaux techniques.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                HorizontalDivider()

                OutlinedButton(
                    onClick = viewModel::onToggleTracking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.intent.enabled) {
                            "Désactiver le tracking"
                        } else {
                            "Activer le tracking"
                        },
                    )
                }
                // Cette précision évite la crainte légitime de perdre son trajet
                // en coupant le suivi (`arch/09` §5).
                Text(
                    "Désactiver arrête la collecte de nouvelles positions. Les positions " +
                        "déjà enregistrées sont conservées et continuent d'être envoyées.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Le reste des réglages, replié.
 *
 * Ce qu'on vient chercher ici au quotidien tient en deux choses : la cadence,
 * et l'état de la synchronisation. Activer l'appareil, ouvrir le diagnostic,
 * couper le suivi sont des gestes de dépannage — rares, et jamais urgents au
 * point de justifier qu'ils encombrent l'écran tous les jours.
 */
@Composable
private fun AutresReglages(expanded: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(if (expanded) "Masquer les autres réglages" else "Autres réglages")
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
