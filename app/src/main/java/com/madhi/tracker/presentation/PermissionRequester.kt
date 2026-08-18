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
import com.madhi.tracker.domain.model.DeviceVendor

/**
 * Les demandes d'autorisation, séparées parce qu'Android l'impose.
 *
 * Depuis Android 11, la localisation en arrière-plan ne peut pas être
 * demandée en même temps que la localisation précise : elle doit l'être
 * dans un second appel, une fois la première accordée. C'est aussi pourquoi
 * l'onboarding leur consacre deux écrans distincts.
 */
class PermissionRequests(
    val requestForeground: () -> Unit,
    val requestBackground: () -> Unit,
)

@Composable
fun rememberPermissionRequests(): PermissionRequests {
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    return remember(foregroundLauncher, backgroundLauncher) {
        PermissionRequests(
            requestForeground = {
                foregroundLauncher.launch(
                    buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }.toTypedArray(),
                )
            },
            requestBackground = {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            },
        )
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
    if (!context.startActivitySafely(intent)) openApplicationDetails(context)
}

fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData("package:${context.packageName}".toUri())
    if (!context.startActivitySafely(intent)) openApplicationDetails(context)
}

/**
 * Raccourci vers l'écran propriétaire du constructeur quand il en existe un.
 *
 * Ces composants ne sont documentés par personne et peuvent disparaître à
 * la prochaine mise à jour de la surcouche. Le repli sur la fiche
 * d'application standard garantit que le bouton mène toujours quelque part :
 * aucune fonctionnalité ne dépend de leur présence.
 */
fun openVendorSettings(context: Context, vendor: DeviceVendor) {
    val component = when (vendor) {
        DeviceVendor.XIAOMI ->
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"
        DeviceVendor.ONEPLUS_OPPO ->
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        else -> null
    }

    val opened = component?.let { (pkg, cls) ->
        context.startActivitySafely(Intent().setClassName(pkg, cls))
    } ?: false

    if (!opened) openApplicationDetails(context)
}

fun openLocationSettings(context: Context) {
    if (!context.startActivitySafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))) {
        openApplicationDetails(context)
    }
}

fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    if (!context.startActivitySafely(intent)) openApplicationDetails(context)
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
    // Écran système absent sur cet appareil : le repli prend le relais.
    false
}
