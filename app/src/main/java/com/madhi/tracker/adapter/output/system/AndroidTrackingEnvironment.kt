package com.madhi.tracker.adapter.output.system

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.madhi.tracker.application.port.EnvironmentSnapshot
import com.madhi.tracker.application.port.TrackingEnvironment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import android.location.LocationManager as AndroidLocationManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lecture seule sur l'état du système.
 *
 * Trois des réglages qui décident réellement de la survie du suivi sur MIUI
 * — démarrage automatique, économiseur de batterie propriétaire,
 * verrouillage dans les récentes — ne sont pas lisibles par une API
 * publique. L'application ne peut donc pas tout constater : elle en détecte
 * les conséquences ailleurs (`RebootDetection`), et guide vers les réglages.
 */
@Singleton
class AndroidTrackingEnvironment @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TrackingEnvironment {

    override fun snapshot(): EnvironmentSnapshot = EnvironmentSnapshot(
        hasForegroundLocationPermission = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION),
        hasBackgroundLocationPermission = isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        hasNotificationPermission = hasNotificationPermission(),
        isLocationEnabled = isLocationEnabled(),
        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
        canScheduleExactAlarms = canScheduleExactAlarms(),
        isOnline = isOnline(),
        batteryPercent = batteryPercent(),
    )

    /**
     * Seul le réseau est réellement observable en continu. Les permissions et
     * les réglages système changent hors de l'application : les écrans les
     * relisent à la reprise plutôt que de prétendre les surveiller.
     */
    override fun observe(): Flow<EnvironmentSnapshot> = callbackFlow {
        val connectivity = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
        trySend(snapshot())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(snapshot())
            }

            override fun onLost(network: Network) {
                trySend(snapshot())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(snapshot())
            }
        }

        connectivity?.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity?.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isGranted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    private fun isLocationEnabled(): Boolean {
        val manager = ContextCompat.getSystemService(context, AndroidLocationManager::class.java)
        return manager != null && LocationManagerCompat.isLocationEnabled(manager)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val power = ContextCompat.getSystemService(context, PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun canScheduleExactAlarms(): Boolean {
        // Avant Android 12, les alarmes exactes ne demandaient aucune autorisation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarms = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return false
        return alarms.canScheduleExactAlarms()
    }

    private fun isOnline(): Boolean {
        val connectivity = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
            ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        // NET_CAPABILITY_VALIDATED distingue un réseau réellement utilisable
        // d'un portail captif d'hôtel ou d'un wifi sans sortie internet.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun batteryPercent(): Int? {
        val battery = ContextCompat.getSystemService(context, BatteryManager::class.java) ?: return null
        return battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
    }
}
