package com.madhi.tracker.presentation

import com.madhi.tracker.application.usecase.RestoreTracking
import com.madhi.tracker.domain.model.DeviceVendor
import com.madhi.tracker.fakes.FakeCaptureScheduler
import com.madhi.tracker.fakes.FakeClock
import com.madhi.tracker.fakes.FakeLocationStore
import com.madhi.tracker.fakes.FakeOnboardingStore
import com.madhi.tracker.fakes.FakeRebootJournalStore
import com.madhi.tracker.fakes.FakeSyncScheduler
import com.madhi.tracker.fakes.FakeTrackingEnvironment
import com.madhi.tracker.fakes.FakeTrackingIntentStore
import com.madhi.tracker.fakes.FakeTrackingRuntime
import com.madhi.tracker.fakes.RecordingEventLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RootViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val onboardingStore = FakeOnboardingStore()
    private val environment = FakeTrackingEnvironment()
    private val syncScheduler = FakeSyncScheduler()

    private fun viewModel() = RootViewModel(
        onboardingStore = onboardingStore,
        restoreTracking = RestoreTracking(
            trackingIntentStore = FakeTrackingIntentStore(),
            trackingRuntime = FakeTrackingRuntime(),
            captureScheduler = FakeCaptureScheduler(),
            syncScheduler = syncScheduler,
            rebootJournalStore = FakeRebootJournalStore(),
            locationStore = FakeLocationStore(),
            eventLog = RecordingEventLog(),
            clock = FakeClock(),
        ),
        environment = environment,
    )

    @Test
    fun `une ouverture avant installation affiche l'onboarding apres rattrapage`() = runTest {
        val viewModel = viewModel()

        assertTrue(syncScheduler.periodicScheduled)
        assertEquals(RootDestination.Onboarding, viewModel.destination.value)
    }

    @Test
    fun `une ouverture apres installation va directement au diagnostic`() = runTest {
        onboardingStore.markCompleted()

        assertEquals(RootDestination.Diagnostics, viewModel().destination.value)
    }

    @Test
    fun `le constructeur expose est relu depuis l'environnement courant`() {
        val viewModel = viewModel()
        environment.current = FakeTrackingEnvironment.healthy.copy(vendor = DeviceVendor.XIAOMI)

        assertEquals(DeviceVendor.XIAOMI, viewModel.vendor)
    }

    @Test
    fun `finir l'onboarding envoie vers le diagnostic sans attendre une recreation`() {
        val viewModel = viewModel()

        viewModel.onOnboardingFinished()

        assertEquals(RootDestination.Diagnostics, viewModel.destination.value)
    }
}
