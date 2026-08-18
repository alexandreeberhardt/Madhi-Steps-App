package com.madhi.tracker.usecase

import com.madhi.tracker.application.usecase.CaptureLocation
import com.madhi.tracker.application.usecase.RunScheduledCapture
import com.madhi.tracker.domain.error.LocationAcquisitionFailure
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationFix
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.fakes.FakeCaptureScheduler
import com.madhi.tracker.fakes.FakeClock
import com.madhi.tracker.fakes.FakeLocationSource
import com.madhi.tracker.fakes.FakeLocationStore
import com.madhi.tracker.fakes.FakeRebootJournalStore
import com.madhi.tracker.fakes.FakeSyncScheduler
import com.madhi.tracker.fakes.FakeTrackingEnvironment
import com.madhi.tracker.fakes.FakeTrackingIntentStore
import com.madhi.tracker.fakes.RecordingEventLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 * La chaîne d'alarmes est ce qui fait vivre le suivi pendant un an. Un seul
 * maillon manquant l'arrête définitivement, sans erreur visible.
 */
class RunScheduledCaptureTest {

    private val clock = FakeClock()
    private val locationSource = FakeLocationSource()
    private val locationStore = FakeLocationStore()
    private val intentStore = FakeTrackingIntentStore(
        TrackingIntent(enabled = true, captureInterval = CaptureInterval.FIVE),
    )
    private val captureScheduler = FakeCaptureScheduler()

    private val runScheduledCapture = RunScheduledCapture(
        captureLocation = CaptureLocation(
            locationSource = locationSource,
            locationStore = locationStore,
            trackingIntentStore = intentStore,
            environment = FakeTrackingEnvironment(),
            rebootJournalStore = FakeRebootJournalStore(),
            syncScheduler = FakeSyncScheduler(),
            eventLog = RecordingEventLog(),
            clock = clock,
        ),
        captureScheduler = captureScheduler,
        trackingIntentStore = intentStore,
    )

    private fun validFix() = LocationFix(
        coordinates = Coordinates(48.85837, 2.29448),
        recordedAt = clock.instant,
        accuracyMeters = 12f,
        altitudeMeters = null,
        speedMetersPerSecond = null,
    )

    @Test
    fun `une capture reussie programme la suivante`() = runTest {
        locationSource.willReturn(validFix())

        runScheduledCapture()

        assertEquals(5.minutes, captureScheduler.lastDelay)
    }

    @Test
    fun `une acquisition ratee programme quand meme la suivante`() = runTest {
        // Le cas qui tue un suivi : un tunnel, un echec, et plus jamais
        // d'alarme pour le reste du voyage.
        locationSource.willFail(LocationAcquisitionFailure.Timeout)

        runScheduledCapture()

        assertEquals(5.minutes, captureScheduler.lastDelay)
    }

    @Test
    fun `une position invalide programme quand meme la suivante`() = runTest {
        locationSource.willReturn(validFix().copy(coordinates = Coordinates(0.0, 0.0)))

        runScheduledCapture()

        assertEquals(5.minutes, captureScheduler.lastDelay)
    }

    @Test
    fun `la localisation desactivee programme quand meme la suivante`() = runTest {
        // La voyageuse peut rallumer le GPS a tout moment : il faut etre la
        // pour le prochain creneau.
        locationSource.willFail(LocationAcquisitionFailure.LocationDisabled)

        runScheduledCapture()

        assertEquals(5.minutes, captureScheduler.lastDelay)
    }

    @Test
    fun `la chaine se poursuit sur une longue serie d'echecs`() = runTest {
        locationSource.willFail(LocationAcquisitionFailure.Timeout)

        repeat(100) { runScheduledCapture() }

        assertEquals(100, captureScheduler.scheduledDelays.size)
        assertTrue(captureScheduler.scheduledDelays.all { it == 5.minutes })
    }

    @Test
    fun `le nouvel intervalle est pris en compte des la capture suivante`() = runTest {
        locationSource.willReturn(validFix())
        intentStore.setCaptureInterval(CaptureInterval.FIFTEEN)

        runScheduledCapture()

        assertEquals(15.minutes, captureScheduler.lastDelay)
    }

    @Test
    fun `un arret pendant l'acquisition annule la chaine au lieu de la poursuivre`() = runTest {
        locationSource.willReturn(validFix())
        intentStore.setEnabled(false)

        runScheduledCapture()

        assertEquals(1, captureScheduler.cancellations)
        assertTrue(captureScheduler.scheduledDelays.isEmpty())
    }

    @Test
    fun `une exception inattendue ne laisse pas la chaine sans alarme`() = runTest {
        val explosive = object : com.madhi.tracker.application.port.LocationSource {
            override suspend fun acquire(timeout: kotlin.time.Duration) =
                throw IllegalStateException("panne inattendue du fournisseur")
        }
        val useCase = RunScheduledCapture(
            captureLocation = CaptureLocation(
                locationSource = explosive,
                locationStore = locationStore,
                trackingIntentStore = intentStore,
                environment = FakeTrackingEnvironment(),
                rebootJournalStore = FakeRebootJournalStore(),
                syncScheduler = FakeSyncScheduler(),
                eventLog = RecordingEventLog(),
                clock = clock,
            ),
            captureScheduler = captureScheduler,
            trackingIntentStore = intentStore,
        )

        runCatching { useCase() }

        // L'exception remonte pour etre visible, mais l'alarme suivante est
        // programmee avant : le suivi survit a un bug.
        assertEquals(5.minutes, captureScheduler.lastDelay)
    }
}
