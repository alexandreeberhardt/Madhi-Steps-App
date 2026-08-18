package com.madhi.tracker.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.net.toUri

/**
 * Demande les autorisations dans l'ordre imposé par Android.
 *
 * L'ordre n'est pas une préférence : depuis Android 11, la localisation en
 * arrière-plan ne peut pas être demandée en même temps que la localisation
 * précise. Elle doit l'être **après**, dans un second appel, et le système
 * renvoie alors l'utilisatrice vers les réglages.
 *
 * L'exemption d'optimisation de batterie et les alarmes exactes ne sont pas
 * des permissions runtime : ce sont des écrans système vers lesquels on ne
 * peut que rediriger.
 */
@Composable
fun rememberPermissionRequester(context: Context): () -> Unit {
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val hasForeground = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        // La demande d'arrière-plan n'a aucun sens tant que la localisation
        // au premier plan n'est pas accordée : le système la refuserait.
        if (hasForeground) {
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    return remember(context) {
        {
            foregroundLauncher.launch(
                buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.toTypedArray(),
            )
        }
    }
}

/**
 * Ouvre l'écran système d'exemption d'optimisation de batterie.
 *
 * C'est la demande la plus importante de l'application : sans exemption, la
 * cadence dérive en veille et le watchdog ne peut plus relancer le service
 * (ADR-007 §3.2).
 */
@SuppressLint("BatteryLife")
fun openBatteryOptimizationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData("package:${context.packageName}".toUri())
    context.startActivitySafely(intent)
}

fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData("package:${context.packageName}".toUri())
    context.startActivitySafely(intent)
}

/**
 * Écran de démarrage automatique de MIUI.
 *
 * Ce composant n'est documenté nulle part par Xiaomi : il peut disparaître à
 * la prochaine mise à jour de la surcouche. Le repli sur la fiche
 * d'application standard garantit que le bouton mène toujours quelque part.
 */
fun openAutostartSettings(context: Context) {
    val miuiAutostart = Intent().setClassName(
        "com.miui.securitycenter",
        "com.miui.permcenter.autostart.AutoStartManagementActivity",
    )
    if (!context.startActivitySafely(miuiAutostart)) {
        openApplicationDetails(context)
    }
}

fun openApplicationDetails(context: Context) {
    context.startActivitySafely(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null)),
    )
}

private fun Context.startActivitySafely(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) {
    // Écran système absent sur cet appareil : aucune fonctionnalité n'en dépend.
    false
}
