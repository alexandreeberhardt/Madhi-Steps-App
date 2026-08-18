package com.madhi.tracker.adapter.output.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.madhi.tracker.application.port.Clock
import com.madhi.tracker.application.port.LocationSource
import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.LocationAcquisitionFailure
import com.madhi.tracker.domain.failure
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationFix
import com.madhi.tracker.domain.success
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration

/**
 * Acquisition ponctuelle via l'API système `LocationManager`.
 *
 * Le choix de `LocationManager` plutôt que Fused Location Provider est une
 * contrainte produit, pas une préférence technique : le suivi ne doit
 * dépendre ni de Google Play Services ni du Play Store (ADR-001).
 *
 * Le GPS n'est allumé que le temps d'un point. Une acquisition qui n'aboutit
 * pas est abandonnée au bout du délai imparti — laisser le récepteur allumé
 * en espérant un fix est le plus sûr moyen de vider la batterie en une nuit.
 */
@Singleton
class AndroidLocationSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : LocationSource {

    private val locationManager: LocationManager?
        get() = ContextCompat.getSystemService(context, LocationManager::class.java)

    override suspend fun acquire(timeout: Duration): Outcome<LocationFix, LocationAcquisitionFailure> {
        if (!hasLocationPermission()) return failure(LocationAcquisitionFailure.PermissionMissing)

        val manager = locationManager
            ?: return failure(LocationAcquisitionFailure.LocationDisabled)

        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            return failure(LocationAcquisitionFailure.LocationDisabled)
        }

        val location = withTimeoutOrNull(timeout) { awaitSingleUpdate(manager) }
            ?: return failure(LocationAcquisitionFailure.Timeout)

        return success(location.toFix())
    }

    /**
     * Traduit le rappel de `LocationManager` en appel suspendu annulable.
     *
     * `invokeOnCancellation` est le point critique : sans lui, un délai
     * dépassé ou une coroutine annulée laisserait le GPS enregistré et donc
     * allumé indéfiniment.
     */
    private suspend fun awaitSingleUpdate(manager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val provider = chooseProvider(manager)
            if (provider == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdatesSafely(this)
                    if (continuation.isActive) continuation.resume(location)
                }

                // Obligatoires avant Android 12, sans quoi certains appareils
                // lèvent une AbstractMethodError sur le rappel du fournisseur.
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

    /**
     * Le GPS est préféré parce que sa précision est celle qui donne un tracé
     * exploitable. Le fournisseur réseau sert de repli lorsque le GPS est
     * indisponible : mieux vaut un point à cinq cents mètres que pas de point.
     */
    private fun chooseProvider(manager: LocationManager): String? = when {
        manager.isProviderEnabledSafely(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        manager.isProviderEnabledSafely(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun Location.toFix(): LocationFix = LocationFix(
        coordinates = Coordinates(latitude, longitude),
        // L'horodatage du fix prime sur l'heure courante : il correspond au
        // moment où la position a été mesurée, pas à celui où on la traite.
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
