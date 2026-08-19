package com.madhi.tracker.usecase

import com.madhi.tracker.application.usecase.BuildDiagnosticsReport
import com.madhi.tracker.domain.error.SyncFailure
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.DeviceActivation
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.RebootJournal
import com.madhi.tracker.domain.model.TrackingHealth
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.domain.model.TrackingProblem
import com.madhi.tracker.fakes.FakeClock
import com.madhi.tracker.fakes.FakeDeviceCredentials
import com.madhi.tracker.fakes.FakeLocationStore
import com.madhi.tracker.fakes.FakeRebootJournalStore
import com.madhi.tracker.fakes.FakeSyncJournalStore
import com.madhi.tracker.fakes.FakeTrackingEnvironment
import com.madhi.tracker.fakes.FakeTrackingIntentStore
import com.madhi.tracker.fakes.FakeTrackingRuntime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours

class BuildDiagnosticsReportTest {

    private val clock = FakeClock()
    private val intentStore = FakeTrackingIntentStore(
        TrackingIntent(enabled = true, captureInterval = CaptureInterval.FIVE),
    )
    private val locationStore = FakeLocationStore()
    private val syncJournalStore = FakeSyncJournalStore()
    private val rebootJournalStore = FakeRebootJournalStore()
    private val environment = FakeTrackingEnvironment()
    private val runtime = FakeTrackingRuntime()
    private val credentials = FakeDeviceCredentials()

    private val buildReport = BuildDiagnosticsReport(
        trackingIntentStore = intentStore,
        locationStore = locationStore,
        syncJournalStore = syncJournalStore,
        rebootJournalStore = rebootJournalStore,
        environment = environment,
        trackingRuntime = runtime,
        credentials = credentials,
        clock = clock,
    )

    @Test
    fun `le rapport expose l'etat qui permet de depanner un backlog a distance`() = runTest {
        credentials.activation = DeviceActivation("device-1", "token-secret", "trip-1")
        runtime.running = true
        givenPointRecorded(minutesAgo = 45)
        givenPointRecorded(minutesAgo = 10)
        syncJournalStore.recordFailure(clock.now(), SyncFailure.Timeout)

        val report = buildReport()

        assertEquals(TrackingHealth.ACTIVE, report.status.health)
        assertEquals(2, report.status.pendingCount)
        assertEquals(clock.instant.minusSeconds(45 * 60), report.oldestPendingAt)
        assertEquals(clock.instant.minusSeconds(10 * 60), report.status.lastPointAt)
        assertEquals(12, report.coverage.expected)
        assertEquals(2, report.coverage.actual)
        assertTrue(report.coverage.isDegraded)
        assertTrue(report.serviceRunning)
        assertTrue(report.deviceActivated)
        assertEquals(clock.instant, report.generatedAt)
    }

    @Test
    fun `un token refuse apparait comme probleme prioritaire`() = runTest {
        credentials.activation = DeviceActivation("device-1", "token-secret", "trip-1")
        syncJournalStore.recordFailure(clock.now(), SyncFailure.Unauthorized)

        val report = buildReport()

        assertEquals(TrackingHealth.ACTION_REQUIRED, report.status.health)
        assertTrue(report.status.problems.contains(TrackingProblem.AUTHENTICATION_FAILED))
    }

    @Test
    fun `hors ligne avec des points en attente n'est pas presente comme une panne`() = runTest {
        credentials.activation = DeviceActivation("device-1", "token-secret", "trip-1")
        environment.current = FakeTrackingEnvironment.healthy.copy(isOnline = false)
        givenPointRecorded(minutesAgo = 5)

        val report = buildReport()

        assertEquals(TrackingHealth.OFFLINE, report.status.health)
        assertTrue(report.status.problems.isEmpty())
        assertEquals(1, report.status.pendingCount)
    }

    @Test
    fun `un redemarrage rate apparait dans le rapport de diagnostic`() = runTest {
        credentials.activation = DeviceActivation("device-1", "token-secret", "trip-1")
        rebootJournalStore.journal = RebootJournal(
            lastSeenAt = clock.instant.minusSeconds(3600),
            lastSeenUptime = 12.hours,
        )
        clock.uptime = 5.hours

        val report = buildReport()

        assertEquals(TrackingHealth.ACTION_REQUIRED, report.status.health)
        assertTrue(report.status.problems.contains(TrackingProblem.AUTOSTART_BLOCKED))
    }

    private suspend fun givenPointRecorded(minutesAgo: Long) {
        locationStore.save(
            LocationPoint(
                id = LocationId.random(),
                coordinates = Coordinates(48.85, 2.29),
                recordedAt = clock.instant.minusSeconds(minutesAgo * 60),
            ),
        )
    }
}
