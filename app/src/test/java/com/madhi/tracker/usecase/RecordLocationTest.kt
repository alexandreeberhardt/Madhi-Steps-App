package com.madhi.tracker.usecase

import com.madhi.tracker.application.port.SyncScheduler
import com.madhi.tracker.application.usecase.CaptureResult
import com.madhi.tracker.application.usecase.RecordLocation
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationFix
import com.madhi.tracker.domain.model.SyncState
import com.madhi.tracker.domain.model.TrackerEvent
import com.madhi.tracker.fakes.FakeClock
import com.madhi.tracker.fakes.FakeLocationStore
import com.madhi.tracker.fakes.FakeRebootJournalStore
import com.madhi.tracker.fakes.FakeTrackingEnvironment
import com.madhi.tracker.fakes.RecordingEventLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordLocationTest {

    private val clock = FakeClock()
    private val locationStore = FakeLocationStore()
    private val environment = FakeTrackingEnvironment()
    private val rebootJournalStore = FakeRebootJournalStore()
    private val syncScheduler = StoreAwareSyncScheduler(locationStore)
    private val eventLog = RecordingEventLog()

    private val recordLocation = RecordLocation(
        locationStore,
        environment,
        rebootJournalStore,
        syncScheduler,
        eventLog,
        clock,
    )

    @Test
    fun `une position valide est enregistree avant toute demande de synchronisation`() = runTest {
        environment.current = FakeTrackingEnvironment.healthy.copy(batteryPercent = 37)

        val result = recordLocation(validFix())

        assertTrue(result is CaptureResult.Captured)
        val point = locationStore.points.values.single()
        assertEquals(SyncState.PENDING, point.syncState)
        assertEquals(37, point.batteryPercent)
        assertEquals(1, syncScheduler.immediateRequests)
    }

    @Test
    fun `une position invalide ne part jamais en synchronisation`() = runTest {
        val result = recordLocation(validFix().copy(coordinates = Coordinates(0.0, 0.0)))

        assertTrue(result is CaptureResult.Rejected)
        assertTrue(locationStore.points.isEmpty())
        assertEquals(0, syncScheduler.immediateRequests)
        assertTrue(eventLog.details.contains(TrackerEvent.LOCATION_REJECTED to "null_island"))
    }

    @Test
    fun `chaque position laisse un signe de vie pour detecter un redemarrage manque`() = runTest {
        recordLocation(validFix())

        assertEquals(clock.instant, rebootJournalStore.journal.lastSeenAt)
        assertEquals(clock.uptime, rebootJournalStore.journal.lastSeenUptime)
    }

    private fun validFix() = LocationFix(
        coordinates = Coordinates(48.85837, 2.29448),
        recordedAt = clock.instant,
        accuracyMeters = 12f,
        altitudeMeters = 34.0,
        speedMetersPerSecond = 4.7f,
    )

    private class StoreAwareSyncScheduler(
        private val locationStore: FakeLocationStore,
    ) : SyncScheduler {
        var immediateRequests = 0

        override fun ensurePeriodicSyncScheduled() = Unit

        override fun requestImmediateSync() {
            // Le reseau ne doit etre evoque qu'une fois le point durablement
            // en file locale : sinon une panne ici pourrait couter la position.
            check(locationStore.points.isNotEmpty())
            immediateRequests++
        }
    }
}
