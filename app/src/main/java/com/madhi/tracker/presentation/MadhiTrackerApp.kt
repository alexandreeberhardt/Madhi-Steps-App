package com.madhi.tracker.presentation

import android.content.Context
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.madhi.tracker.domain.model.TrackingProblem
import com.madhi.tracker.presentation.activation.ActivationScreen
import com.madhi.tracker.presentation.diagnostics.DiagnosticsScreen
import com.madhi.tracker.presentation.map.MainScreen
import com.madhi.tracker.presentation.onboarding.OnboardingScreen
import com.madhi.tracker.presentation.settings.SettingsScreen

private const val ROUTE_MAIN = "main"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_DIAGNOSTICS = "diagnostics"
private const val ROUTE_ACTIVATION = "activation"

/**
 * Trois destinations en pile, sans barre de navigation permanente
 * (`arch/09` §5) : accueil, réglages, diagnostic. La navigation Jetpack
 * n'est utilisée que pour la gestion correcte du bouton retour système,
 * qu'un état maison gérerait mal.
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

        RootDestination.Diagnostics -> {
            val navController = rememberNavController()
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

            NavHost(navController = navController, startDestination = ROUTE_MAIN) {
                composable(ROUTE_MAIN) {
                    MainScreen(
                        onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                        onFixProblem = { problem ->
                            resolveProblem(
                                problem = problem,
                                context = context,
                                permissions = permissions,
                                vendor = viewModel.vendor,
                                openActivation = { navController.navigate(ROUTE_ACTIVATION) },
                            )
                        },
                    )
                }

                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDiagnostics = { navController.navigate(ROUTE_DIAGNOSTICS) },
                        onOpenActivation = { navController.navigate(ROUTE_ACTIVATION) },
                    )
                }

                composable(ROUTE_ACTIVATION) {
                    ActivationScreen(
                        onBack = { navController.popBackStack() },
                        deviceName = deviceName,
                    )
                }

                composable(ROUTE_DIAGNOSTICS) {
                    DiagnosticsScreen(onRequestPermissions = permissions.requestForeground)
                }
            }
        }
    }
}

/**
 * Le bouton « Corriger » mène directement à l'écran qui règle le problème.
 *
 * C'est l'intention de `arch/09` §4 : la voyageuse ne doit pas avoir à
 * chercher dans les réglages Android, ni à traduire un nom de permission en
 * geste concret.
 */
private fun resolveProblem(
    problem: TrackingProblem,
    context: Context,
    permissions: PermissionRequests,
    vendor: com.madhi.tracker.domain.model.DeviceVendor,
    openActivation: () -> Unit,
) {
    when (problem) {
        TrackingProblem.LOCATION_PERMISSION_MISSING -> permissions.requestForeground()
        TrackingProblem.BACKGROUND_LOCATION_PERMISSION_MISSING -> permissions.requestBackground()
        TrackingProblem.LOCATION_DISABLED -> openLocationSettings(context)
        TrackingProblem.BATTERY_OPTIMIZATION_ENABLED -> openBatteryOptimizationSettings(context)
        TrackingProblem.EXACT_ALARM_NOT_PERMITTED -> openExactAlarmSettings(context)
        TrackingProblem.AUTOSTART_BLOCKED -> openVendorSettings(context, vendor)
        TrackingProblem.NOTIFICATIONS_BLOCKED -> openNotificationSettings(context)

        // Les deux se règlent par une saisie de code : un token révoqué se
        // corrige exactement comme une première activation.
        TrackingProblem.DEVICE_NOT_ACTIVATED,
        TrackingProblem.AUTHENTICATION_FAILED,
        -> openActivation()
    }
}
