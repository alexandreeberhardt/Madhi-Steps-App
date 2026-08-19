package com.madhi.tracker.adapter.output.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import com.madhi.tracker.application.port.Clock
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.application.port.LocationSource
import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.LocationAcquisitionFailure
import com.madhi.tracker.domain.failure
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationFix
import com.madhi.tracker.domain.model.TrackerEvent
import com.madhi.tracker.domain.success
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration

/**
 * Localisation via l'API système `LocationManager`, sans Google Play
 * Services (ADR-001).
 */
@Singleton
class AndroidLocationSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val eventLog: EventLog,
    private val clock: Clock,
) : LocationSource {

    private val locationManager: LocationManager?
        get() = ContextCompat.getSystemService(context, LocationManager::class.java)

    /**
     * Abonnement continu à la cadence demandée.
     *
     * `LocationRequestCompat` exprime l'intervalle **et** une exigence de
     * qualité : le système peut alors éteindre le récepteur entre deux
     * points, ce qu'une boucle applicative ne saurait pas faire. C'est aussi
     * ce qui rend cette voie moins coûteuse en batterie qu'un réveil forcé
     * du processeur toutes les cinq minutes.
     */
    override fun stream(interval: Duration): Flow<LocationFix> = callbackFlow {
        val manager = locationManager
        if (manager == null || !hasLocationPermission()) {
            eventLog.record(TrackerEvent.ACQUISITION_FAILED, "stream_permission_missing")
            close()
            return@callbackFlow
        }

        val request = LocationRequestCompat.Builder(interval.inWholeMilliseconds)
            // Ne jamais livrer plus vite que l'intervalle demandé. La valeur
            // précédente, la moitié de l'intervalle, autorisait explicitement
            // une livraison deux fois trop rapide — l'inverse de l'intention.
            .setMinUpdateIntervalMillis(interval.inWholeMilliseconds)
            // Précision suffisante plutôt que maximale (`arch/01` §4).
            .setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
            .build()

        val listener = LocationListenerCompat { location -> trySend(location.toFix()) }

        val providers = subscribableProviders(manager)
        if (providers.isEmpty()) {
            eventLog.record(TrackerEvent.ACQUISITION_FAILED, "stream_no_provider")
            close()
            return@callbackFlow
        }

        providers.forEach { provider ->
            try {
                LocationManagerCompat.requestLocationUpdates(
                    manager,
                    provider,
                    request,
                    ContextCompat.getMainExecutor(context),
                    listener,
                )
            } catch (e: SecurityException) {
                eventLog.record(TrackerEvent.ACQUISITION_FAILED, "stream_security")
            }
        }
        eventLog.record(TrackerEvent.STREAM_STARTED, providers.joinToString("+"))

        awaitClose {
            try {
                LocationManagerCompat.removeUpdates(manager, listener)
            } catch (e: SecurityException) {
                // La permission a pu être révoquée pendant l'abonnement : les
                // mises à jour sont de toute façon arrêtées, et laisser
                // remonter ferait planter à la fermeture du service.
            }
            eventLog.record(TrackerEvent.STREAM_STOPPED)
        }
    }

    override suspend fun acquire(timeout: Duration): Outcome<LocationFix, LocationAcquisitionFailure> {
        if (!hasLocationPermission()) return failure(LocationAcquisitionFailure.PermissionMissing)

        val manager = locationManager ?: return failure(LocationAcquisitionFailure.LocationDisabled)
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            return failure(LocationAcquisitionFailure.LocationDisabled)
        }

        val location = withTimeoutOrNull(timeout) { awaitSingleUpdate(manager) }
            ?: return failure(LocationAcquisitionFailure.Timeout)

        return success(location.toFix())
    }

    /**
     * S'abonner aux deux fournisseurs plutôt qu'au seul GPS.
     *
     * Le test T1 a passé une nuit entière sans qu'un fix GPS n'aboutisse en
     * intérieur. Le fournisseur réseau donne alors une position approximative,
     * ce qui vaut mieux qu'un trou : une position à cinq cents mètres reste
     * exploitable pour un trajet à vélo.
     */
    private fun subscribableProviders(manager: LocationManager): List<String> = buildList {
        if (manager.isProviderEnabledSafely(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
        if (manager.isProviderEnabledSafely(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Traduit le rappel de `LocationManager` en appel suspendu annulable.
     *
     * `invokeOnCancellation` est le point critique : sans lui, un délai
     * dépassé laisserait le GPS enregistré, donc allumé indéfiniment.
     */
    private suspend fun awaitSingleUpdate(manager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val provider = subscribableProviders(manager).firstOrNull()
            if (provider == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdatesSafely(this)
                    if (continuation.isActive) continuation.resume(location)
                }

                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) {
                    manager.removeUpdatesSafely(this)
                    if (continuation.isActive) continuation.resume(null)
                }
            }

            continuation.invokeOnCancellation { manager.removeUpdatesSafely(listener) }

            try {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            } catch (e: SecurityException) {
                // La permission a pu être révoquée entre la vérification et l'appel.
                manager.removeUpdatesSafely(listener)
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun Location.toFix(): LocationFix = LocationFix(
        coordinates = Coordinates(latitude, longitude),
        // L'horodatage du fix prime : il correspond au moment de la mesure,
        // pas à celui du traitement.
        recordedAt = if (time > 0L) Instant.ofEpochMilli(time) else clock.now(),
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        altitudeMeters = if (hasAltitude()) altitude else null,
        speedMetersPerSecond = if (hasSpeed()) speed else null,
    )
}

/**
 * Le retrait des mises à jour ne doit jamais faire échouer une acquisition :
 * si le GPS est déjà relâché, l'erreur n'apporte rien.
 */
private fun LocationManager.removeUpdatesSafely(listener: LocationListener) {
    try {
        removeUpdates(listener)
    } catch (e: SecurityException) {
        // Permission révoquée : les mises à jour sont de toute façon arrêtées.
    }
}

private fun LocationManager.isProviderEnabledSafely(provider: String): Boolean = try {
    isProviderEnabled(provider)
} catch (e: SecurityException) {
    false
} catch (e: IllegalArgumentException) {
    // Certains appareils n'exposent pas le fournisseur réseau.
    false
}
