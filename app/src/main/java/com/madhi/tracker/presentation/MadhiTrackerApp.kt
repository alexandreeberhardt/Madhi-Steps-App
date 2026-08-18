package com.madhi.tracker.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.madhi.tracker.presentation.diagnostics.DiagnosticsScreen

/**
 * Navigation réduite au strict nécessaire tant que l'écran principal
 * carte-first n'existe pas. Le diagnostic est le premier écran livré parce
 * que c'est lui qui permet de valider le suivi sur le terrain.
 */
@Composable
fun MadhiTrackerApp() {
    val context = LocalContext.current
    val requestPermissions = rememberPermissionRequester(context)

    DiagnosticsScreen(onRequestPermissions = requestPermissions)
}
