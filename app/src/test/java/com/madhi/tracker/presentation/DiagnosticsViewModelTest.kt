package com.madhi.tracker.presentation

import com.madhi.tracker.application.usecase.BuildDiagnosticsReport
import com.madhi.tracker.application.usecase.ChangeCaptureInterval
import com.madhi.tracker.application.usecase.RestoreTracking
import com.madhi.tracker.application.usecase.StartTracking
import com.madhi.tracker.application.usecase.StopTracking
import com.madhi.tracker.domain.model.CaptureInterval
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
import com.madhi.tracker.presentation.diagnostics.DiagnosticsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DiagnosticsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeClock()
    private val intentStore = FakeTrackingIntentStore()
    private val runtime = FakeTrackingRuntime()
    private val captureScheduler = FakeCaptureScheduler()
    private val syncScheduler = FakeSyncScheduler()
    private val locationStore = FakeLocationStore()
    private val eventLog = RecordingEventLog()

    private fun viewModel() = DiagnosticsViewModel(
        buildDiagnosticsReport = BuildDiagnosticsReport(
            trackingIntentStore = intentStore,
            locationStore = locationStore,
            syncJournalStore = FakeSyncJournalStore(),
            rebootJournalStore = FakeRebootJournalStore(),
            environment = FakeTrackingEnvironment(),
            trackingRuntime = runtime,
            credentials = FakeDeviceCredentials(),
            clock = clock,
        ),
        startTracking = StartTracking(intentStore, runtime, captureScheduler, syncScheduler, eventLog),
        stopTracking = StopTracking(intentStore, runtime, captureScheduler, syncScheduler, eventLog),
        changeCaptureInterval = ChangeCaptureInterval(intentStore, captureScheduler, eventLog),
        restoreTracking = RestoreTracking(
            trackingIntentStore = intentStore,
            trackingRuntime = runtime,
            captureScheduler = captureScheduler,
            syncScheduler = syncScheduler,
            rebootJournalStore = FakeRebootJournalStore(),
            locationStore = locationStore,
            eventLog = eventLog,
            clock = clock,
        ),
    )

    @Test
    fun `ouvrir le diagnostic tente un rattrapage puis affiche un rapport`() = runTest {
        val viewModel = viewModel()

        assertTrue(syncScheduler.periodicScheduled)
        assertNotNull(viewModel.report.value)
    }

    @Test
    fun `changer la cadence rafraichit le rapport affiche`() = runTest {
        intentStore.setEnabled(true)
        val viewModel = viewModel()

        viewModel.onIntervalSelected(CaptureInterval.ofMinutes(2))

        assertEquals(CaptureInterval.ofMinutes(2), viewModel.report.value!!.intent.captureInterval)
        assertEquals(CaptureInterval.ofMinutes(2).duration, captureScheduler.lastDelay)
    }

    @Test
    fun `arreter le suivi laisse la synchronisation periodique planifiee`() = runTest {
        val viewModel = viewModel()

        viewModel.onStopTracking()

        assertEquals(false, viewModel.report.value!!.intent.enabled)
        assertTrue(syncScheduler.periodicScheduled)
    }
}
