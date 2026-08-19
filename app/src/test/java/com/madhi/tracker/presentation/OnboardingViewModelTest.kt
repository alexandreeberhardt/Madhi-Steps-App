package com.madhi.tracker.presentation

import com.madhi.tracker.application.usecase.ActivateDevice
import com.madhi.tracker.application.usecase.CaptureLocation
import com.madhi.tracker.application.usecase.RunSetupCheck
import com.madhi.tracker.application.usecase.StartTracking
import com.madhi.tracker.application.usecase.SyncPendingLocations
import com.madhi.tracker.domain.model.DeviceVendor
import com.madhi.tracker.fakes.FakeCaptureScheduler
import com.madhi.tracker.fakes.FakeClock
import com.madhi.tracker.fakes.FakeDeviceActivationGateway
import com.madhi.tracker.fakes.FakeDeviceCredentials
import com.madhi.tracker.fakes.FakeLocationSource
import com.madhi.tracker.fakes.FakeLocationStore
import com.madhi.tracker.fakes.FakeLocationSyncGateway
import com.madhi.tracker.fakes.FakeOnboardingStore
import com.madhi.tracker.fakes.FakeRebootJournalStore
import com.madhi.tracker.fakes.FakeSyncJournalStore
import com.madhi.tracker.fakes.FakeSyncScheduler
import com.madhi.tracker.fakes.FakeTrackingEnvironment
import com.madhi.tracker.fakes.FakeTrackingIntentStore
import com.madhi.tracker.fakes.FakeTrackingRuntime
import com.madhi.tracker.fakes.RecordingEventLog
import com.madhi.tracker.fakes.recordLocationWith
import com.madhi.tracker.presentation.onboarding.OnboardingStep
import com.madhi.tracker.presentation.onboarding.OnboardingViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeClock()
    private val environment = FakeTrackingEnvironment()
    private val locationStore = FakeLocationStore()
    private val intentStore = FakeTrackingIntentStore()
    private val runtime = FakeTrackingRuntime()
    private val captureScheduler = FakeCaptureScheduler()
    private val syncScheduler = FakeSyncScheduler()
    private val onboardingStore = FakeOnboardingStore()
    private val eventLog = RecordingEventLog()

    private fun viewModel() = OnboardingViewModel(
        environment = environment,
        activateDevice = ActivateDevice(FakeDeviceActivationGateway(), FakeDeviceCredentials(), eventLog),
        runSetupCheck = RunSetupCheck(
            captureLocation = CaptureLocation(
                locationSource = FakeLocationSource(),
                trackingIntentStore = intentStore,
                recordLocation = recordLocationWith(locationStore = locationStore, clock = clock),
                rebootJournalStore = FakeRebootJournalStore(),
                eventLog = eventLog,
                clock = clock,
            ),
            syncPendingLocations = SyncPendingLocations(
                locationStore,
                FakeLocationSyncGateway(),
                eventLog,
                FakeSyncJournalStore(),
                clock,
            ),
            credentials = FakeDeviceCredentials(),
        ),
        startTracking = StartTracking(intentStore, runtime, captureScheduler, syncScheduler, eventLog),
        onboardingStore = onboardingStore,
    )

    @Test
    fun `revenir d'un ecran Android relit les permissions et le constructeur`() {
        val viewModel = viewModel()
        environment.current = FakeTrackingEnvironment.healthy.copy(
            hasBackgroundLocationPermission = false,
            vendor = DeviceVendor.ONEPLUS_OPPO,
        )

        viewModel.refreshEnvironment()

        assertEquals(false, viewModel.state.value.environment.hasBackgroundLocationPermission)
        assertEquals(DeviceVendor.ONEPLUS_OPPO, viewModel.state.value.environment.vendor)
    }

    @Test
    fun `changer d'etape relit aussi l'etat systeme`() {
        val viewModel = viewModel()
        environment.current = FakeTrackingEnvironment.healthy.copy(hasForegroundLocationPermission = false)

        viewModel.goTo(OnboardingStep.LOCATION)

        assertEquals(OnboardingStep.LOCATION, viewModel.state.value.step)
        assertEquals(false, viewModel.state.value.environment.hasForegroundLocationPermission)
    }

    @Test
    fun `terminer l'installation demarre le suivi et memorise l'onboarding`() = runTest {
        var done = false

        viewModel().onFinish { done = true }

        assertTrue(done)
        assertTrue(onboardingStore.isCompleted())
        assertTrue(runtime.running)
        assertEquals(1, captureScheduler.scheduledDelays.size)
        assertTrue(syncScheduler.periodicScheduled)
    }
}
