package com.madhi.tracker.usecase

import com.madhi.tracker.application.usecase.ChangeCaptureInterval
import com.madhi.tracker.application.usecase.StartTracking
import com.madhi.tracker.application.usecase.StopTracking
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.TrackerEvent
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.fakes.FakeCaptureScheduler
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
import kotlin.time.Duration.Companion.minutes

class TrackingLifecycleTest {

    private val intentStore = FakeTrackingIntentStore()
    private val runtime = FakeTrackingRuntime()
    private val captureScheduler = FakeCaptureScheduler()
    private val syncScheduler = FakeSyncScheduler()
    private val eventLog = RecordingEventLog()

    private val startTracking = StartTracking(intentStore, runtime, captureScheduler, syncScheduler, eventLog)
    private val stopTracking = StopTracking(intentStore, runtime, captureScheduler, syncScheduler, eventLog)
    private val changeInterval = ChangeCaptureInterval(intentStore, captureScheduler, eventLog)

    @Test
    fun `demarrer persiste l'intention et lance le service`() = runTest {
        startTracking()

        assertTrue(intentStore.read().enabled)
        assertTrue(runtime.isRunning())
    }

    @Test
    fun `demarrer capture immediatement plutot que d'attendre l'intervalle`() = runTest {
        startTracking()

        assertEquals(Duration.ZERO, captureScheduler.lastDelay)
    }

    @Test
    fun `demarrer planifie aussi la synchronisation periodique`() = runTest {
        startTracking()

        assertTrue(syncScheduler.periodicScheduled)
        assertTrue(eventLog.events.contains(TrackerEvent.TRACKING_STARTED))
    }

    @Test
    fun `arreter coupe la collecte et le service`() = runTest {
        startTracking()

        stopTracking()

        assertFalse(intentStore.read().enabled)
        assertFalse(runtime.isRunning())
        assertEquals(1, captureScheduler.cancellations)
    }

    @Test
    fun `arreter le suivi laisse la synchronisation du backlog planifiee`() = runTest {
        // arch/01 §2 : desactiver le tracking n'empeche pas les points en
        // attente de partir.
        startTracking()
        syncScheduler.periodicScheduled = false

        stopTracking()

        assertTrue(syncScheduler.periodicScheduled)
    }

    @Test
    fun `changer d'intervalle prend effet immediatement`() = runTest {
        startTracking()

        changeInterval(CaptureInterval.TWO)

        // Sans reprogrammation, passer de 30 a 2 minutes n'aurait d'effet
        // qu'une demi-heure plus tard.
        assertEquals(2.minutes, captureScheduler.lastDelay)
        assertEquals(CaptureInterval.TWO, intentStore.read().captureInterval)
    }

    @Test
    fun `changer d'intervalle suivi arrete ne reveille pas le metronome`() = runTest {
        changeInterval(CaptureInterval.TEN)

        assertEquals(CaptureInterval.TEN, intentStore.read().captureInterval)
        assertTrue(captureScheduler.scheduledDelays.isEmpty())
    }

    @Test
    fun `l'intention survit a un arret puis un redemarrage`() = runTest {
        startTracking()
        stopTracking()
        startTracking()

        assertEquals(TrackingIntent(enabled = true, captureInterval = CaptureInterval.FIVE), intentStore.read())
    }
}
