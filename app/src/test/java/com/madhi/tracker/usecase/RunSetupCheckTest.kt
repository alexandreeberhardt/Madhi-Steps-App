package com.madhi.tracker.usecase

import com.madhi.tracker.application.usecase.RunSetupCheck
import com.madhi.tracker.application.usecase.SyncPendingLocations
import com.madhi.tracker.domain.error.LocationAcquisitionFailure
import com.madhi.tracker.domain.error.SyncFailure
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.DeviceActivation
import com.madhi.tracker.domain.model.LocationFix
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.fakes.FakeClock
import com.madhi.tracker.fakes.FakeSyncJournalStore
import com.madhi.tracker.fakes.captureLocationWith
import com.madhi.tracker.fakes.recordLocationWith
import com.madhi.tracker.fakes.FakeDeviceCredentials
import com.madhi.tracker.fakes.FakeLocationSource
import com.madhi.tracker.fakes.FakeLocationStore
import com.madhi.tracker.fakes.FakeLocationSyncGateway
import com.madhi.tracker.fakes.FakeRebootJournalStore
import com.madhi.tracker.fakes.FakeSyncScheduler
import com.madhi.tracker.fakes.FakeTrackingEnvironment
import com.madhi.tracker.fakes.FakeTrackingIntentStore
import com.madhi.tracker.fakes.RecordingEventLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSetupCheckTest {

    private val clock = FakeClock()
    private val locationSource = FakeLocationSource()
    private val locationStore = FakeLocationStore()
    private val gateway = FakeLocationSyncGateway()
    private val credentials = FakeDeviceCredentials()
    private val eventLog = RecordingEventLog()

    private val runSetupCheck = RunSetupCheck(
        captureLocation = captureLocationWith(
            locationSource = locationSource,
            trackingIntentStore = FakeTrackingIntentStore(
                TrackingIntent(enabled = true, captureInterval = CaptureInterval.FIVE),
            ),
            recordLocation = recordLocationWith(locationStore = locationStore, eventLog = eventLog, clock = clock),
            eventLog = eventLog,
            clock = clock,
        ),
        syncPendingLocations = SyncPendingLocations(locationStore, gateway, eventLog, FakeSyncJournalStore(), clock),
        credentials = credentials,
    )

    private fun givenGpsWorks() {
        locationSource.willReturn(
            LocationFix(
                coordinates = Coordinates(48.85837, 2.29448),
                recordedAt = clock.instant,
                accuracyMeters = 12f,
                altitudeMeters = null,
                speedMetersPerSecond = null,
            ),
        )
    }

    private suspend fun givenActivated() {
        credentials.store(DeviceActivation("device-42", "token", "trip-7"))
    }

    @Test
    fun `tout fonctionne quand le GPS et le serveur repondent`() = runTest {
        givenGpsWorks()
        givenActivated()

        val result = runSetupCheck()

        assertTrue(result.locationOk)
        assertTrue(result.serverOk)
        assertTrue(result.isReady)
    }

    @Test
    fun `le test capture une vraie position, pas une simulation`() = runTest {
        givenGpsWorks()
        givenActivated()

        runSetupCheck()

        assertEquals(1, locationStore.points.size)
        assertEquals(1, gateway.uploadCount)
    }

    @Test
    fun `un appareil non active signale le serveur en echec sans tenter d'envoi`() = runTest {
        givenGpsWorks()

        val result = runSetupCheck()

        assertTrue(result.locationOk)
        assertFalse(result.serverOk)
        assertEquals("not_activated", result.serverDetail)
        assertEquals(0, gateway.uploadCount)
    }

    @Test
    fun `un GPS indisponible est rapporte avec sa cause`() = runTest {
        locationSource.willFail(LocationAcquisitionFailure.LocationDisabled)
        givenActivated()

        val result = runSetupCheck()

        assertFalse(result.locationOk)
        assertEquals("location_disabled", result.locationDetail)
    }

    @Test
    fun `un serveur injoignable est rapporte sans faire perdre la position`() = runTest {
        givenGpsWorks()
        givenActivated()
        gateway.failures.addLast(SyncFailure.NoNetwork)

        val result = runSetupCheck()

        assertTrue(result.locationOk)
        assertFalse(result.serverOk)
        assertEquals("no_network", result.serverDetail)
        // La position reste en attente : le test ne detruit rien.
        assertEquals(1, locationStore.pendingCount())
    }

    @Test
    fun `une file deja vide compte comme un succes serveur`() = runTest {
        // Rien a envoyer parce que tout est deja parti n'est pas un echec.
        locationSource.willFail(LocationAcquisitionFailure.Timeout)
        givenActivated()

        val result = runSetupCheck()

        assertFalse(result.locationOk)
        assertTrue(result.serverOk)
    }
}
