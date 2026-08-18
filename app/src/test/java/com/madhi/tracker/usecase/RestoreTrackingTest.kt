package com.madhi.tracker.usecase

import com.madhi.tracker.application.usecase.RestoreTracking
import com.madhi.tracker.application.usecase.RestoreTrigger
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.RebootJournal
import com.madhi.tracker.domain.model.TrackerEvent
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.fakes.FakeCaptureScheduler
import com.madhi.tracker.fakes.FakeClock
import com.madhi.tracker.fakes.FakeLocationStore
import com.madhi.tracker.fakes.FakeRebootJournalStore
import com.madhi.tracker.fakes.FakeSyncScheduler
import com.madhi.tracker.fakes.FakeTrackingIntentStore
import com.madhi.tracker.fakes.FakeTrackingRuntime
import com.madhi.tracker.fakes.RecordingEventLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class RestoreTrackingTest {

    private val clock = FakeClock()
    private val intentStore = FakeTrackingIntentStore(
        TrackingIntent(enabled = true, captureInterval = CaptureInterval.FIVE),
    )
    private val runtime = FakeTrackingRuntime()
    private val captureScheduler = FakeCaptureScheduler()
    private val syncScheduler = FakeSyncScheduler()
    private val rebootJournal = FakeRebootJournalStore()
    private val locationStore = FakeLocationStore()
    private val eventLog = RecordingEventLog()

    private val restoreTracking = RestoreTracking(
        trackingIntentStore = intentStore,
        trackingRuntime = runtime,
        captureScheduler = captureScheduler,
        syncScheduler = syncScheduler,
        rebootJournalStore = rebootJournal,
        locationStore = locationStore,
        eventLog = eventLog,
        clock = clock,
    )

    private suspend fun givenPointRecorded(minutesAgo: Long) {
        locationStore.save(
            LocationPoint(
                id = LocationId.random(),
                coordinates = Coordinates(48.85, 2.29),
                recordedAt = clock.instant.minusSeconds(minutesAgo * 60),
            ),
        )
    }

    @Test
    fun `apres redemarrage, le suivi actif repart`() = runTest {
        val result = restoreTracking(RestoreTrigger.BOOT)

        assertTrue(result.trackingResumed)
        assertTrue(runtime.isRunning())
        assertTrue(eventLog.events.contains(TrackerEvent.TRACKING_RECOVERED_AFTER_BOOT))
    }

    @Test
    fun `apres redemarrage, le suivi arrete volontairement ne repart pas`() = runTest {
        // L'intention de l'utilisatrice prime sur le rattrapage automatique.
        intentStore.setEnabled(false)

        val result = restoreTracking(RestoreTrigger.BOOT)

        assertFalse(result.trackingResumed)
        assertFalse(runtime.isRunning())
    }

    @Test
    fun `la synchronisation est replanifiee meme suivi arrete`() = runTest {
        intentStore.setEnabled(false)

        restoreTracking(RestoreTrigger.BOOT)

        assertTrue(syncScheduler.periodicScheduled)
    }

    @Test
    fun `le passage du receveur de demarrage est trace`() = runTest {
        restoreTracking(RestoreTrigger.BOOT)

        assertEquals(clock.instant, rebootJournal.journal.bootHandledAt)
    }

    @Test
    fun `un redemarrage non traite est detecte a l'ouverture de l'application`() = runTest {
        // Le scenario MIUI et OxygenOS : demarrage automatique bloque, notre
        // receveur n'a jamais ete appele.
        rebootJournal.journal = RebootJournal(
            lastSeenAt = clock.instant.minusSeconds(3600),
            lastSeenUptime = 10.hours,
        )
        clock.uptime = 5.minutes

        val result = restoreTracking(RestoreTrigger.APP_OPENED)

        assertTrue(result.autostartBlockedDetected)
        assertTrue(eventLog.events.contains(TrackerEvent.AUTOSTART_BLOCKED_DETECTED))
    }

    @Test
    fun `aucune alerte quand le receveur de demarrage a fait son travail`() = runTest {
        restoreTracking(RestoreTrigger.BOOT)
        clock.advance(10.minutes)

        val result = restoreTracking(RestoreTrigger.APP_OPENED)

        assertFalse(result.autostartBlockedDetected)
    }

    @Test
    fun `le watchdog ressuscite un service tue par la surcouche`() = runTest {
        restoreTracking(RestoreTrigger.BOOT)
        runtime.running = false // la surcouche a tue le service

        val result = restoreTracking(RestoreTrigger.WATCHDOG)

        assertTrue(result.serviceWasRevived)
        assertTrue(runtime.isRunning())
        assertTrue(eventLog.events.contains(TrackerEvent.TRACKING_SERVICE_REVIVED))
    }

    @Test
    fun `un service deja en cours n'est pas relance inutilement`() = runTest {
        restoreTracking(RestoreTrigger.BOOT)
        val startsAfterBoot = runtime.starts

        val result = restoreTracking(RestoreTrigger.WATCHDOG)

        assertFalse(result.serviceWasRevived)
        assertEquals(startsAfterBoot, runtime.starts)
    }

    @Test
    fun `ouvrir l'application ne repousse pas la prochaine capture`() = runTest {
        // Le piege : consulter l'ecran toutes les quatre minutes ne doit pas
        // empecher toute capture d'avoir lieu.
        givenPointRecorded(minutesAgo = 2)

        restoreTracking(RestoreTrigger.APP_OPENED)
        restoreTracking(RestoreTrigger.APP_OPENED)
        restoreTracking(RestoreTrigger.APP_OPENED)

        assertTrue(captureScheduler.scheduledDelays.all { it == 3.minutes })
    }

    @Test
    fun `une longue interruption declenche une capture immediate`() = runTest {
        givenPointRecorded(minutesAgo = 600)

        restoreTracking(RestoreTrigger.BOOT)

        assertEquals(Duration.ZERO, captureScheduler.lastDelay)
    }

    @Test
    fun `la mise a jour de l'APK replanifie tout`() = runTest {
        // arch/01 §8 : apres mise a jour, verifier que le worker est toujours
        // planifie et le recreer si necessaire.
        syncScheduler.periodicScheduled = false

        restoreTracking(RestoreTrigger.PACKAGE_REPLACED)

        assertTrue(syncScheduler.periodicScheduled)
        assertTrue(runtime.isRunning())
    }

    @Test
    fun `la reprise est idempotente`() = runTest {
        givenPointRecorded(minutesAgo = 1)

        repeat(10) { restoreTracking(RestoreTrigger.WATCHDOG) }

        assertEquals(1, runtime.starts)
        assertTrue(captureScheduler.scheduledDelays.all { it == 4.minutes })
    }

    @Test
    fun `chaque reprise laisse un signe de vie`() = runTest {
        restoreTracking(RestoreTrigger.APP_OPENED)

        assertEquals(clock.instant, rebootJournal.journal.lastSeenAt)
        assertEquals(clock.uptime, rebootJournal.journal.lastSeenUptime)
    }
}
