package com.madhi.tracker.presentation

import com.madhi.tracker.application.usecase.ObserveTrackingStatus
import com.madhi.tracker.application.usecase.StartTracking
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.TrackingHealth
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.domain.model.TrackingProblem
import com.madhi.tracker.fakes.FakeCaptureScheduler
import com.madhi.tracker.fakes.FakeClock
import com.madhi.tracker.fakes.FakeDeviceCredentials
import com.madhi.tracker.fakes.FakeLocationStore
import com.madhi.tracker.fakes.FakeRebootJournalStore
import com.madhi.tracker.fakes.FakeSyncJournalStore
import com.madhi.tracker.fakes.FakeSyncScheduler
import com.madhi.tracker.fakes.FakeTrackingEnvironment
import com.madhi.tracker.fakes.FakeTrackingIntentStore
import com.madhi.tracker.fakes.FakeTrackingRuntime
import com.madhi.tracker.fakes.RecordingEventLog
import com.madhi.tracker.domain.error.SyncFailure
import com.madhi.tracker.domain.model.DeviceActivation
import com.madhi.tracker.presentation.map.MainViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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

    /**
     * L'état initial est nul le temps que le flux se calcule ; ce qui
     * intéresse le test est toujours la première valeur réelle.
     */
    private suspend fun firstStatus() = viewModel().status.filterNotNull().first()

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
    fun `demarrer le suivi depuis l'accueil met a jour l'etat`() = runTest {
        credentials.store(DeviceActivation("d", "t", "tr"))
        intentStore.setEnabled(false)
        val viewModel = viewModel()

        viewModel.onStartTracking()

        assertEquals(TrackingHealth.ACTIVE, viewModel.status.filterNotNull().first().health)
    }
}
