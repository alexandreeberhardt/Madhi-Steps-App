package com.madhi.tracker.presentation

import com.madhi.tracker.application.usecase.ChangeCaptureInterval
import com.madhi.tracker.application.usecase.StartTracking
import com.madhi.tracker.application.usecase.StopTracking
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.DeviceActivation
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.fakes.FakeCaptureScheduler
import com.madhi.tracker.fakes.FakeDeviceCredentials
import com.madhi.tracker.fakes.FakeLocationStore
import com.madhi.tracker.fakes.FakeSyncJournalStore
import com.madhi.tracker.fakes.FakeSyncScheduler
import com.madhi.tracker.fakes.FakeTrackingIntentStore
import com.madhi.tracker.fakes.FakeTrackingRuntime
import com.madhi.tracker.fakes.RecordingEventLog
import com.madhi.tracker.presentation.settings.SettingsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val intentStore = FakeTrackingIntentStore(
        TrackingIntent(enabled = true, captureInterval = CaptureInterval.FIVE),
    )
    private val locationStore = FakeLocationStore()
    private val syncJournalStore = FakeSyncJournalStore()
    private val credentials = FakeDeviceCredentials()
    private val runtime = FakeTrackingRuntime()
    private val captureScheduler = FakeCaptureScheduler()
    private val syncScheduler = FakeSyncScheduler()
    private val eventLog = RecordingEventLog()

    private fun viewModel() = SettingsViewModel(
        trackingIntentStore = intentStore,
        credentials = credentials,
        locationStore = locationStore,
        syncJournalStore = syncJournalStore,
        changeCaptureInterval = ChangeCaptureInterval(intentStore, captureScheduler, eventLog),
        startTracking = StartTracking(intentStore, runtime, captureScheduler, syncScheduler, eventLog),
        stopTracking = StopTracking(intentStore, runtime, captureScheduler, syncScheduler, eventLog),
    )

    @Test
    fun `l'etat initial reflete l'intention persistee`() = runTest {
        val state = viewModel().state.value

        assertTrue(state.loaded)
        assertTrue(state.intent.enabled)
        assertEquals(CaptureInterval.FIVE, state.intent.captureInterval)
    }

    @Test
    fun `changer la frequence la persiste et rafraichit l'ecran`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntervalSelected(CaptureInterval.FIFTEEN)

        assertEquals(CaptureInterval.FIFTEEN, viewModel.state.value.intent.captureInterval)
        assertEquals(CaptureInterval.FIFTEEN, intentStore.read().captureInterval)
    }

    @Test
    fun `desactiver le tracking ne supprime aucun point en attente`() = runTest {
        // La crainte legitime que l'ecran doit lever : couper le suivi ne
        // fait pas perdre le trajet deja enregistre.
        val viewModel = viewModel()
        val pendingBefore = locationStore.points.size

        viewModel.onToggleTracking()

        assertFalse(viewModel.state.value.intent.enabled)
        assertEquals(pendingBefore, locationStore.points.size)
    }

    @Test
    fun `desactiver le tracking laisse la synchronisation planifiee`() = runTest {
        syncScheduler.periodicScheduled = false

        viewModel().onToggleTracking()

        assertTrue(syncScheduler.periodicScheduled)
    }

    @Test
    fun `reactiver le tracking relance le service`() = runTest {
        val viewModel = viewModel()
        viewModel.onToggleTracking()

        viewModel.onToggleTracking()

        assertTrue(viewModel.state.value.intent.enabled)
        assertTrue(runtime.isRunning())
    }

    @Test
    fun `l'etat d'activation de l'appareil est affiche`() = runTest {
        assertFalse(viewModel().state.value.deviceActivated)

        credentials.store(DeviceActivation("d", "t", "tr"))

        assertTrue(viewModel().state.value.deviceActivated)
    }
}
