package com.madhi.tracker.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madhi.tracker.presentation.activation.ActivationForm
import com.madhi.tracker.presentation.common.TrackingStatusColors

/**
 * Le parcours de première configuration de `arch/09` §6.
 *
 * Une seule demande par écran, avec sa raison. Enchaîner six autorisations
 * sans explication garantit qu'au moins une sera refusée par réflexe — et
 * chacune d'elles est nécessaire au suivi.
 */
@Composable
fun OnboardingScreen(
    onRequestForegroundPermissions: () -> Unit,
    onRequestBackgroundPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenVendorSettings: () -> Unit,
    onFinished: () -> Unit,
    deviceName: String,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Les autorisations et les réglages système changent dans une autre
    // activité — dialogue Android ou écran de paramètres. La reprise de
    // l'écran est le seul instant où l'on peut constater le résultat.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshEnvironment() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LinearProgressIndicator(
                progress = { (state.step.ordinal + 1f) / OnboardingStep.entries.size },
                modifier = Modifier.fillMaxWidth(),
            )

            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep(viewModel)
                OnboardingStep.LOCATION -> LocationStep(state, viewModel, onRequestForegroundPermissions)
                OnboardingStep.BACKGROUND_LOCATION -> BackgroundLocationStep(state, viewModel, onRequestBackgroundPermission)
                OnboardingStep.ACTIVATION -> ActivationStep(state, viewModel, deviceName)
                OnboardingStep.BATTERY -> BatteryStep(state, viewModel, onOpenBatterySettings, onOpenVendorSettings)
                OnboardingStep.CHECK -> CheckStep(state, viewModel, onFinished)
            }
        }
    }
}

@Composable
private fun WelcomeStep(viewModel: OnboardingViewModel) {
    StepLayout(
        title = "Suivi du voyage",
        body = "Cette application enregistre ta position toutes les cinq minutes et " +
            "l'envoie à ta famille dès qu'il y a du réseau. Sans réseau, tout est " +
            "gardé sur le téléphone, rien n'est perdu.\n\n" +
            "La configuration prend deux minutes. Elle ne sera à refaire que si tu " +
            "changes de téléphone.",
        primaryLabel = "Commencer",
        onPrimary = { viewModel.goTo(OnboardingStep.LOCATION) },
    )
}

@Composable
private fun LocationStep(
    state: OnboardingState,
    viewModel: OnboardingViewModel,
    onRequest: () -> Unit,
) {
    val granted = state.environment.hasForegroundLocationPermission
    StepLayout(
        title = "Localisation",
        body = "L'application a besoin d'accéder à ta position pour enregistrer ton trajet. " +
            "C'est sa seule fonction : rien d'autre n'est collecté.",
        status = if (granted) "Autorisation accordée" else null,
        primaryLabel = if (granted) "Continuer" else "Autoriser",
        onPrimary = {
            if (granted) viewModel.goTo(OnboardingStep.BACKGROUND_LOCATION) else onRequest()
        },
        secondaryLabel = if (granted) null else "Vérifier à nouveau",
        onSecondary = viewModel::refreshEnvironment,
    )
}

@Composable
private fun BackgroundLocationStep(
    state: OnboardingState,
    viewModel: OnboardingViewModel,
    onRequest: () -> Unit,
) {
    val granted = state.environment.hasBackgroundLocationPermission
    StepLayout(
        title = "Localisation en arrière-plan",
        body = "Android va te demander de choisir « Toujours autoriser ». C'est ce qui " +
            "permet au suivi de continuer quand l'écran est éteint et quand " +
            "l'application est fermée — c'est-à-dire presque tout le temps.\n\n" +
            "Sans cette autorisation, le suivi s'arrête dès que tu ranges le téléphone.",
        status = if (granted) "Autorisation accordée" else null,
        primaryLabel = if (granted) "Continuer" else "Autoriser",
        onPrimary = {
            if (granted) viewModel.goTo(OnboardingStep.ACTIVATION) else onRequest()
        },
        secondaryLabel = if (granted) null else "Vérifier à nouveau",
        onSecondary = viewModel::refreshEnvironment,
    )
}

@Composable
private fun ActivationStep(
    state: OnboardingState,
    viewModel: OnboardingViewModel,
    deviceName: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Activation", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Saisis le code d'activation qui t'a été communiqué. Il relie ce " +
                "téléphone au voyage et n'est utilisable qu'une fois.",
            style = MaterialTheme.typography.bodyLarge,
        )

        ActivationForm(
            code = state.activationCode,
            onCodeChange = viewModel::onActivationCodeChanged,
            onSubmit = { viewModel.onActivate(deviceName) },
            busy = state.activating,
            error = state.activationError,
            submitLabel = "Activer",
        )

        // L'activation peut échouer faute de réseau au moment de la
        // configuration. Le suivi, lui, fonctionne sans : les positions
        // s'accumulent localement et partiront après activation.
        TextButton(onClick = { viewModel.goTo(OnboardingStep.BATTERY) }) {
            Text("Je n'ai pas de code pour l'instant")
        }
    }
}

@Composable
private fun BatteryStep(
    state: OnboardingState,
    viewModel: OnboardingViewModel,
    onOpenBatterySettings: () -> Unit,
    onOpenVendorSettings: () -> Unit,
) {
    val steps = VendorSetupGuidance.stepsFor(state.environment.vendor)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Économie d'énergie", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Android et ton téléphone mettent les applications en veille pour " +
                "économiser la batterie. Il faut faire une exception, sinon le suivi " +
                "s'arrête pendant la nuit sans prévenir.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Exemption Android", fontWeight = FontWeight.Bold)
                Text(
                    if (state.environment.isIgnoringBatteryOptimizations) {
                        "Déjà accordée."
                    } else {
                        "À accorder : c'est le réglage le plus important de tous."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Ouvrir le réglage")
                }
            }
        }

        steps.forEach { step ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(step.title, fontWeight = FontWeight.Bold)
                    Text(step.path, style = MaterialTheme.typography.bodySmall)
                    Text(step.consequence, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (steps.isNotEmpty()) {
            OutlinedButton(onClick = onOpenVendorSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Ouvrir les réglages de l'application")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::refreshEnvironment, modifier = Modifier.weight(1f)) {
                Text("Vérifier")
            }
            Button(onClick = { viewModel.goTo(OnboardingStep.CHECK) }, modifier = Modifier.weight(1f)) {
                Text("Continuer")
            }
        }
    }
}

@Composable
private fun CheckStep(
    state: OnboardingState,
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Vérification", style = MaterialTheme.typography.headlineSmall)
        Text(
            "On enregistre une position et on tente un envoi, pour vérifier que " +
                "tout fonctionne réellement sur ce téléphone.",
            style = MaterialTheme.typography.bodyLarge,
        )

        when {
            state.checking -> CircularProgressIndicator()

            state.checkResult != null -> {
                val result = state.checkResult
                CheckLine("Position enregistrée", result.locationOk, result.locationDetail)
                CheckLine("Envoi au serveur", result.serverOk, result.serverDetail)
                if (!result.isReady) {
                    Text(
                        "Tu peux continuer quand même : les positions sont gardées sur le " +
                            "téléphone et repartiront toutes seules. Le détail reste dans " +
                            "l'écran Diagnostic.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            else -> Unit
        }

        Button(onClick = viewModel::onRunSetupCheck, enabled = !state.checking, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.checkResult == null) "Lancer le test" else "Refaire le test")
        }

        if (state.checkResult != null) {
            Button(onClick = { viewModel.onFinish(onFinished) }, modifier = Modifier.fillMaxWidth()) {
                Text("Démarrer le suivi")
            }
        }
    }
}

@Composable
private fun CheckLine(label: String, ok: Boolean, detail: String?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            if (ok) "OK" else (detail ?: "échec"),
            color = if (ok) TrackingStatusColors.active else TrackingStatusColors.broken,
        )
    }
}

@Composable
private fun StepLayout(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    status: String? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyLarge)
        status?.let { Text(it, color = TrackingStatusColors.active) }
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primaryLabel) }
        if (secondaryLabel != null && onSecondary != null) {
            OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondaryLabel) }
        }
    }
}
