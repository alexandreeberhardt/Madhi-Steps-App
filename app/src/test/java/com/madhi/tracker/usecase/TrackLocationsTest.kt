package com.madhi.tracker.usecase

import com.madhi.tracker.application.usecase.TrackLocations
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationFix
import com.madhi.tracker.domain.model.SyncState
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.fakes.FakeClock
import com.madhi.tracker.fakes.FakeLocationSource
import com.madhi.tracker.fakes.FakeLocationStore
import com.madhi.tracker.fakes.FakeSyncScheduler
import com.madhi.tracker.fakes.FakeTrackingIntentStore
import com.madhi.tracker.fakes.recordLocationWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Le mécanisme principal du suivi depuis l'échec de T1 : c'est le flux du
 * fournisseur de localisation qui cadence, plus l'alarme.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackLocationsTest {

    private val clock = FakeClock()
    private val locationSource = FakeLocationSource()
    private val locationStore = FakeLocationStore()
    private val syncScheduler = FakeSyncScheduler()
    private val intentStore = FakeTrackingIntentStore(
        TrackingIntent(enabled = true, captureInterval = CaptureInterval.FIVE),
    )

    private val trackLocations = TrackLocations(
        locationSource = locationSource,
        trackingIntentStore = intentStore,
        recordLocation = recordLocationWith(
            locationStore = locationStore,
            syncScheduler = syncScheduler,
            clock = clock,
        ),
        locationStore = locationStore,
    )

    private fun fixAt(latitude: Double, minutesLater: Long = 0) = LocationFix(
        coordinates = Coordinates(latitude, 2.29),
        recordedAt = clock.instant.plusSeconds(minutesLater * 60),
        accuracyMeters = 12f,
        altitudeMeters = null,
        speedMetersPerSecond = null,
    )

    @Test
    fun `chaque position livree par le flux est enregistree en attente d'envoi`() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { trackLocations() }

        // Espacees d'un intervalle : deux livraisons rapprochees seraient
        // ecartees par le filtre anti-doublon, et c'est voulu.
        locationSource.streamed.emit(fixAt(48.85))
        locationSource.streamed.emit(fixAt(48.86, minutesLater = 5))

        assertEquals(2, locationStore.points.size)
        assertTrue(locationStore.points.values.all { it.syncState == SyncState.PENDING })
        job.cancelAndJoin()
    }

    @Test
    fun `le flux est ouvert a la cadence configuree`() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { trackLocations() }

        assertEquals(5.minutes, locationSource.lastStreamInterval)
        job.cancelAndJoin()
    }

    @Test
    fun `changer d'intervalle rouvre le flux a la nouvelle cadence`() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { trackLocations() }
        assertEquals(1, locationSource.streamSubscriptions)

        intentStore.setCaptureInterval(CaptureInterval.ofMinutes(15))

        // Le flux precedent est referme — donc le recepteur relache — avant
        // qu'un nouveau soit ouvert.
        assertEquals(2, locationSource.streamSubscriptions)
        assertEquals(15.minutes, locationSource.lastStreamInterval)
        job.cancelAndJoin()
    }

    @Test
    fun `un intervalle reecrit a l'identique ne rouvre pas le flux`() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { trackLocations() }

        intentStore.setCaptureInterval(CaptureInterval.FIVE)

        assertEquals(1, locationSource.streamSubscriptions)
        job.cancelAndJoin()
    }

    @Test
    fun `le flux ne demarre pas quand le suivi est arrete`() = runTest {
        val stoppedIntentStore = FakeTrackingIntentStore(
            TrackingIntent(enabled = false, captureInterval = CaptureInterval.FIVE),
        )
        val useCase = TrackLocations(
            locationSource = locationSource,
            trackingIntentStore = stoppedIntentStore,
            recordLocation = recordLocationWith(
                locationStore = locationStore,
                syncScheduler = syncScheduler,
                clock = clock,
            ),
            locationStore = locationStore,
        )

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }

        assertEquals(0, locationSource.streamSubscriptions)
        locationSource.streamed.emit(fixAt(48.85))
        assertTrue(locationStore.points.isEmpty())
        job.cancelAndJoin()
    }

    @Test
    fun `changer la cadence suivi arrete ne demarre pas le flux`() = runTest {
        val stoppedIntentStore = FakeTrackingIntentStore(
            TrackingIntent(enabled = false, captureInterval = CaptureInterval.FIVE),
        )
        val useCase = TrackLocations(
            locationSource = locationSource,
            trackingIntentStore = stoppedIntentStore,
            recordLocation = recordLocationWith(
                locationStore = locationStore,
                syncScheduler = syncScheduler,
                clock = clock,
            ),
            locationStore = locationStore,
        )
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { useCase() }

        stoppedIntentStore.setCaptureInterval(CaptureInterval.ofMinutes(2))

        assertEquals(0, locationSource.streamSubscriptions)
        job.cancelAndJoin()
    }

    @Test
    fun `arreter le suivi coupe le flux et ignore les positions tardives`() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { trackLocations() }
        locationSource.streamed.emit(fixAt(48.85))

        intentStore.setEnabled(false)
        locationSource.streamed.emit(fixAt(48.86))

        // Une position livree apres l'arret volontaire ne doit pas relancer
        // le tracé : elle appartient a un abonnement en cours de fermeture.
        assertEquals(1, locationStore.points.size)
        job.cancelAndJoin()
    }

    @Test
    fun `chaque position declenche une demande d'envoi`() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { trackLocations() }

        locationSource.streamed.emit(fixAt(48.85))

        assertEquals(1, syncScheduler.immediateRequests)
        job.cancelAndJoin()
    }

    @Test
    fun `une position invalide est ecartee sans interrompre le flux`() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { trackLocations() }

        locationSource.streamed.emit(fixAt(48.85).copy(coordinates = Coordinates(0.0, 0.0)))
        locationSource.streamed.emit(fixAt(48.86))

        // Le point zero-zero est refuse, le suivant passe : le flux survit.
        assertEquals(1, locationStore.points.size)
        job.cancelAndJoin()
    }

    @Test
    fun `la seconde livraison du meme intervalle est ecartee`() = runTest {
        // Les deux fournisseurs livrent independamment : sur appareil reel,
        // cela produisait 205 % de la couverture attendue.
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { trackLocations() }

        locationSource.streamed.emit(fixAt(48.85))
        locationSource.streamed.emit(fixAt(48.86, minutesLater = 1))

        assertEquals(1, locationStore.points.size)
        job.cancelAndJoin()
    }
}
