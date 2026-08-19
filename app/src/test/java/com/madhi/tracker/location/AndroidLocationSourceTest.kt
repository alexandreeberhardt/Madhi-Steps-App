package com.madhi.tracker.location

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.madhi.tracker.adapter.output.location.AndroidLocationSource
import com.madhi.tracker.application.port.Clock
import com.madhi.tracker.fakes.RecordingEventLog
import com.madhi.tracker.domain.error.LocationAcquisitionFailure
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
// Le telephone cible tourne sous Android 13.
@Config(sdk = [33])
class AndroidLocationSourceTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val locationManager =
        application.getSystemService(Application.LOCATION_SERVICE) as LocationManager
    private val shadowLocationManager: ShadowLocationManager get() = shadowOf(locationManager)

    private val now: Instant = Instant.parse("2026-08-18T12:00:00Z")

    private val clock = object : Clock {
        override fun now(): Instant = now
        override fun uptime() = 1.seconds
    }

    private val source = AndroidLocationSource(application, RecordingEventLog(), clock)

    @Before
    fun setUp() {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    }

    /**
     * Robolectric met le Looper principal en pause : sans `idle()`, le rappel
     * de LocationManager ne serait jamais delivre.
     */
    private fun deliver(location: Location) {
        shadowLocationManager.simulateLocation(location)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun gpsLocation(
        latitude: Double = 48.85837,
        longitude: Double = 2.29448,
        timeMillis: Long = now.toEpochMilli(),
    ) = Location(LocationManager.GPS_PROVIDER).apply {
        this.latitude = latitude
        this.longitude = longitude
        this.time = timeMillis
        this.accuracy = 12f
        this.altitude = 34.0
        this.speed = 4.7f
    }

    @Test
    fun rend_la_position_mesuree_avec_ses_metadonnees() = runTest {
        val acquisition = async { source.acquire(timeout = 60.seconds) }
        runCurrent()
        deliver(gpsLocation())

        val fix = acquisition.await().valueOrNull()!!

        assertEquals(48.85837, fix.coordinates.latitude, 0.00001)
        assertEquals(2.29448, fix.coordinates.longitude, 0.00001)
        assertEquals(12f, fix.accuracyMeters)
        assertEquals(34.0, fix.altitudeMeters!!, 0.001)
        assertEquals(4.7f, fix.speedMetersPerSecond)
    }

    @Test
    fun conserve_l_horodatage_de_la_mesure_et_non_l_heure_de_traitement() = runTest {
        val measuredAt = now.minusSeconds(45)

        val acquisition = async { source.acquire(timeout = 60.seconds) }
        runCurrent()
        deliver(gpsLocation(timeMillis = measuredAt.toEpochMilli()))

        assertEquals(measuredAt, acquisition.await().valueOrNull()!!.recordedAt)
    }

    @Test
    fun retombe_sur_l_horloge_quand_le_fix_n_a_pas_d_horodatage() = runTest {
        val acquisition = async { source.acquire(timeout = 60.seconds) }
        runCurrent()
        deliver(gpsLocation(timeMillis = 0L))

        assertEquals(now, acquisition.await().valueOrNull()!!.recordedAt)
    }

    @Test
    fun signale_la_permission_manquante_sans_allumer_le_gps() = runTest {
        shadowOf(application).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        val outcome = source.acquire(timeout = 60.seconds)

        assertEquals(LocationAcquisitionFailure.PermissionMissing, outcome.failureOrNull())
        assertTrue(shadowLocationManager.requestLocationUpdateListeners.isEmpty())
    }

    @Test
    fun signale_la_localisation_desactivee() = runTest {
        shadowLocationManager.setLocationEnabled(false)

        val outcome = source.acquire(timeout = 60.seconds)

        assertEquals(LocationAcquisitionFailure.LocationDisabled, outcome.failureOrNull())
    }

    @Test
    fun abandonne_au_bout_du_delai_plutot_que_de_laisser_le_gps_allume() = runTest {
        val acquisition = async { source.acquire(timeout = 60.seconds) }
        runCurrent()
        assertTrue(shadowLocationManager.requestLocationUpdateListeners.isNotEmpty())

        advanceTimeBy(61.seconds)

        assertEquals(LocationAcquisitionFailure.Timeout, acquisition.await().failureOrNull())
        // Le point critique : sans liberation, le recepteur GPS resterait
        // allume et viderait la batterie en une nuit.
        assertTrue(shadowLocationManager.requestLocationUpdateListeners.isEmpty())
    }

    @Test
    fun libere_le_gps_apres_une_acquisition_reussie() = runTest {
        val acquisition = async { source.acquire(timeout = 60.seconds) }
        runCurrent()
        deliver(gpsLocation())
        acquisition.await()

        assertTrue(shadowLocationManager.requestLocationUpdateListeners.isEmpty())
    }

    @Test
    fun se_rabat_sur_le_fournisseur_reseau_quand_le_gps_est_indisponible() = runTest {
        // Mieux vaut un point a cinq cents metres que pas de point du tout.
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)

        val acquisition = async { source.acquire(timeout = 60.seconds) }
        runCurrent()
        deliver(
            Location(LocationManager.NETWORK_PROVIDER).apply {
                latitude = 48.85
                longitude = 2.29
                time = now.toEpochMilli()
            },
        )

        assertNull(acquisition.await().failureOrNull())
    }
}
