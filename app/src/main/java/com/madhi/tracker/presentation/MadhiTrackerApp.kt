package com.madhi.tracker.presentation

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madhi.tracker.presentation.diagnostics.DiagnosticsScreen
import com.madhi.tracker.presentation.onboarding.OnboardingScreen

/**
 * Navigation volontairement minimale : deux destinations, choisies une fois
 * au démarrage. Une bibliothèque de navigation n'apporterait rien tant que
 * l'écran principal carte-first n'existe pas (ADR-006).
 */
@Composable
fun MadhiTrackerApp(viewModel: RootViewModel = hiltViewModel()) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = rememberPermissionRequests()

    when (destination) {
        RootDestination.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        RootDestination.Onboarding -> OnboardingScreen(
            onRequestForegroundPermissions = permissions.requestForeground,
            onRequestBackgroundPermission = permissions.requestBackground,
            onOpenBatterySettings = { openBatteryOptimizationSettings(context) },
            onOpenVendorSettings = { openVendorSettings(context, viewModel.vendor) },
            onFinished = viewModel::onOnboardingFinished,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
        )

        RootDestination.Diagnostics -> DiagnosticsScreen(
            onRequestPermissions = permissions.requestForeground,
        )
    }
}
