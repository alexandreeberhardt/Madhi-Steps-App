package com.madhi.tracker.presentation

import com.madhi.tracker.application.usecase.LoadMapTile
import com.madhi.tracker.application.usecase.LookUpAddress
import com.madhi.tracker.application.usecase.ObserveTrack
import com.madhi.tracker.application.usecase.ObserveTrackingStatus
import com.madhi.tracker.application.usecase.StartTracking
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.TrackPeriod
import com.madhi.tracker.domain.model.TrackingHealth
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.domain.model.TrackingProblem
import com.madhi.tracker.fakes.FakeAddressLookup
import com.madhi.tracker.fakes.FakeCaptureScheduler
import com.madhi.tracker.fakes.FakeClock
import com.madhi.tracker.fakes.FakeDeviceCredentials
import com.madhi.tracker.fakes.FakeLocationStore
import com.madhi.tracker.fakes.FakeRebootJournalStore
import com.madhi.tracker.fakes.FakeSyncJournalStore
import com.madhi.tracker.fakes.FakeSyncScheduler
import com.madhi.tracker.fakes.FakeTileStore
import com.madhi.tracker.fakes.FakeTrackingEnvironment
import com.madhi.tracker.fakes.FakeTrackingIntentStore
import com.madhi.tracker.fakes.FakeTrackingRuntime
import com.madhi.tracker.fakes.RecordingEventLog
import com.madhi.tracker.domain.error.SyncFailure
import com.madhi.tracker.domain.model.DeviceActivation
import com.madhi.tracker.presentation.map.MainViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val intentStore = FakeTrackingIntentStore(
        TrackingIntent(enabled = true, captureInterval = CaptureInterval.FIVE),
    )
    private val locationStore = FakeLocationStore()
    private val syncJournalStore = FakeSyncJournalStore()
    private val rebootJournalStore = FakeRebootJournalStore()
    private val environment = FakeTrackingEnvironment()
    private val credentials = FakeDeviceCredentials()
    private val clock = FakeClock()
    private val runtime = FakeTrackingRuntime()
    private val captureScheduler = FakeCaptureScheduler()
    private val syncScheduler = FakeSyncScheduler()
    private val eventLog = RecordingEventLog()
    private val addressLookup = FakeAddressLookup()

    /**
     * L'état initial est nul le temps que le flux se calcule ; ce qui
     * intéresse le test est toujours la première valeur réelle.
     */
    private suspend fun firstStatus() = viewModel().status.filterNotNull().first()

    /**
     * La valeur d'un flux de la carte, une fois l'amont démarré.
     *
     * `first()` seul rendrait la valeur initiale du `stateIn` — une liste vide —
     * avant même que la base ait été lue, et le test passerait sans rien
     * prouver. Il faut un abonné : ces flux ne lisent la base que tant que
     * quelqu'un les regarde, c'est tout leur intérêt.
     */
    private fun <T> TestScope.observed(flow: StateFlow<T>): T {
        val job = launch { flow.collect {} }
        runCurrent()
        return flow.value.also { job.cancel() }
    }

    private suspend fun store(recordedAt: Instant, coordinates: Coordinates) {
        locationStore.save(
            LocationPoint(
                id = LocationId.random(),
                coordinates = coordinates,
                recordedAt = recordedAt,
            ),
        )
    }

    private fun viewModel() = MainViewModel(
        observeTrackingStatus = ObserveTrackingStatus(
            trackingIntentStore = intentStore,
            locationStore = locationStore,
            syncJournalStore = syncJournalStore,
            rebootJournalStore = rebootJournalStore,
            environment = environment,
            credentials = credentials,
            clock = clock,
        ),
        observeTrack = ObserveTrack(locationStore, clock),
        loadMapTile = LoadMapTile(FakeTileStore()),
        lookUpAddress = LookUpAddress(addressLookup),
        startTracking = StartTracking(intentStore, runtime, captureScheduler, syncScheduler, eventLog),
    )

    @Test
    fun `un systeme sain et active affiche le suivi actif`() = runTest {
        credentials.store(DeviceActivation("d", "t", "tr"))

        assertEquals(TrackingHealth.ACTIVE, firstStatus().health)
    }

    @Test
    fun `un appareil non active demande une action`() = runTest {
        val status = firstStatus()

        assertEquals(TrackingHealth.ACTION_REQUIRED, status.health)
        assertEquals(TrackingProblem.DEVICE_NOT_ACTIVATED, status.mostUrgentProblem)
    }

    @Test
    fun `hors ligne s'affiche comme un mode normal, pas comme une panne`() = runTest {
        credentials.store(DeviceActivation("d", "t", "tr"))
        environment.current = FakeTrackingEnvironment.healthy.copy(isOnline = false)

        assertEquals(TrackingHealth.OFFLINE, firstStatus().health)
    }

    @Test
    fun `un suivi arrete est affiche comme tel`() = runTest {
        credentials.store(DeviceActivation("d", "t", "tr"))
        intentStore.setEnabled(false)

        assertEquals(TrackingHealth.STOPPED, firstStatus().health)
    }

    @Test
    fun `un token refuse remonte jusqu'a l'accueil`() = runTest {
        // Les points s'accumulent sans partir : ce n'est pas une statistique
        // de diagnostic, c'est une action a mener.
        credentials.store(DeviceActivation("d", "t", "tr"))
        syncJournalStore.recordFailure(clock.instant, SyncFailure.Unauthorized)

        val status = firstStatus()

        assertEquals(TrackingHealth.ACTION_REQUIRED, status.health)
        assertEquals(TrackingProblem.AUTHENTICATION_FAILED, status.mostUrgentProblem)
    }

    @Test
    fun `le fond montre tout le voyage, la periode ne montre que la periode`() = runTest {
        // Une etape d'il y a trois jours, et une de ce matin. « Aujourd'hui »
        // ne doit rien savoir de la premiere ; le fond doit connaitre les deux.
        store(clock.instant.minus(3, ChronoUnit.DAYS), Coordinates(48.8566, 2.3522))
        store(clock.instant, Coordinates(45.7640, 4.8357))
        val viewModel = viewModel()
        viewModel.onPeriodSelected(TrackPeriod.TODAY)

        assertEquals(
            listOf(Coordinates(45.7640, 4.8357)),
            observed(viewModel.track).map { it.coordinates },
        )
        assertEquals(
            listOf(Coordinates(48.8566, 2.3522), Coordinates(45.7640, 4.8357)),
            observed(viewModel.backgroundTrack),
        )
    }

    @Test
    fun `sur tout le voyage, il n'y a pas de fond a dessiner`() = runTest {
        // Le fond serait le trace lui-meme : le dessiner deux fois ne montrerait
        // rien de plus et relirait la base pour rien.
        store(clock.instant, Coordinates(45.7640, 4.8357))
        val viewModel = viewModel()

        viewModel.onPeriodSelected(TrackPeriod.EVERYTHING)

        assertEquals(emptyList<Coordinates>(), observed(viewModel.backgroundTrack))
    }

    @Test
    fun `demarrer le suivi depuis l'accueil met a jour l'etat`() = runTest {
        credentials.store(DeviceActivation("d", "t", "tr"))
        intentStore.setEnabled(false)
        val viewModel = viewModel()

        viewModel.onStartTracking()

        assertEquals(TrackingHealth.ACTIVE, viewModel.status.filterNotNull().first().health)
    }
}
