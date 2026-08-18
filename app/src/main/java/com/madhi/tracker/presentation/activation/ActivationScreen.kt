package com.madhi.tracker.presentation.activation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madhi.tracker.presentation.common.TrackingStatusColors

/**
 * Réactivation en cours de voyage, accessible depuis les réglages et depuis
 * le bouton « Corriger » de l'accueil.
 *
 * Le cas réel : le token est révoqué ou l'appareil remplacé, les positions
 * s'accumulent sans partir. Sans cet écran, il faudrait réinstaller
 * l'application — et donc risquer de perdre les points en attente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(
    onBack: () -> Unit,
    deviceName: String,
    viewModel: ActivationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activation") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.succeeded) {
                Text("Appareil activé", style = MaterialTheme.typography.titleMedium, color = TrackingStatusColors.active)
                Text(
                    "Les positions en attente vont repartir dès qu'il y a du réseau.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Terminé") }
                return@Column
            }

            Text(
                if (state.alreadyActivated) {
                    "Ce téléphone est déjà relié au voyage. Saisir un nouveau code le " +
                        "reliera à nouveau — utile si les envois sont refusés."
                } else {
                    "Saisis le code d'activation qui t'a été communiqué. Il relie ce " +
                        "téléphone au voyage et n'est utilisable qu'une fois."
                },
                style = MaterialTheme.typography.bodyLarge,
            )

            ActivationForm(
                code = state.code,
                onCodeChange = viewModel::onCodeChange,
                onSubmit = { viewModel.onSubmit(deviceName) },
                busy = state.busy,
                error = state.error,
                submitLabel = if (state.alreadyActivated) "Réactiver" else "Activer",
            )

            Text(
                "Les positions déjà enregistrées ne sont jamais supprimées par une " +
                    "réactivation.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
