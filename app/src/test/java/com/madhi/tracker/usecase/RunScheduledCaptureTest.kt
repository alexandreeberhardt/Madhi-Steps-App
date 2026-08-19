package com.madhi.tracker.usecase

import com.madhi.tracker.application.usecase.CaptureLocation
import com.madhi.tracker.application.usecase.CaptureResult
import com.madhi.tracker.application.usecase.RunScheduledCapture
import com.madhi.tracker.domain.error.LocationAcquisitionFailure
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationFix
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.TrackerEvent
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.fakes.FakeCaptureScheduler
import com.madhi.tracker.fakes.FakeClock
import com.madhi.tracker.fakes.FakeLocationSource
import com.madhi.tracker.fakes.FakeLocationStore
import com.madhi.tracker.fakes.FakeRebootJournalStore
import com.madhi.tracker.fakes.FakeTrackingIntentStore
import com.madhi.tracker.fakes.RecordingEventLog
import com.madhi.tracker.fakes.captureLocationWith
import com.madhi.tracker.fakes.recordLocationWith
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Depuis l'échec de T1, l'alarme n'est plus le métronome : elle surveille
 * que le flux du fournisseur de localisation livre bien des positions.
 */
class RunScheduledCaptureTest {

    private val clock = FakeClock()
    private val locationSource = FakeLocationSource()
    private val locationStore = FakeLocationStore()
    private val intentStore = FakeTrackingIntentStore(
        TrackingIntent(enabled = true, captureInterval = CaptureInterval.FIVE),
    )
    private val captureScheduler = FakeCaptureScheduler()
    private val eventLog = RecordingEventLog()

    private val runScheduledCapture = RunScheduledCapture(
        captureLocation = captureLocationWith(
            locationSource = locationSource,
            trackingIntentStore = intentStore,
            recordLocation = recordLocationWith(locationStore = locationStore, clock = clock),
            rebootJournalStore = FakeRebootJournalStore(),
            eventLog = eventLog,
            clock = clock,
        ),
        captureScheduler = captureScheduler,
        trackingIntentStore = intentStore,
        locationStore = locationStore,
        eventLog = eventLog,
        clock = clock,
    )

    private fun validFix() = LocationFix(
        coordinates = Coordinates(48.85837, 2.29448),
        recordedAt = clock.instant,
        accuracyMeters = 12f,
        altitudeMeters = null,
        speedMetersPerSecond = null,
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
    fun `ne rallume pas le GPS quand le flux vient de livrer`() = runTest {
        givenPointRecorded(minutesAgo = 2)
        locationSource.willReturn(validFix())

        val result = runScheduledCapture()

        assertNull(result)
        assertEquals(0, locationSource.acquisitionCount)
    }

    @Test
    fun `tolere un retard d'un intervalle sans intervenir`() = runTest {
        // Le systeme a le droit de decaler une livraison : l'objectif produit
        // dit « environ » cinq minutes.
        givenPointRecorded(minutesAgo = 7)

        assertNull(runScheduledCapture())
        assertEquals(0, locationSource.acquisitionCount)
    }

    @Test
    fun `capture quand le flux s'est tu au-dela de deux intervalles`() = runTest {
        givenPointRecorded(minutesAgo = 12)
        locationSource.willReturn(validFix())

        val result = runScheduledCapture()

        assertTrue(result is CaptureResult.Captured)
        assertEquals(1, locationSource.acquisitionCount)
        assertTrue(eventLog.events.contains(TrackerEvent.STREAM_SILENT))
    }

    @Test
    fun `capture quand aucune position n'existe encore`() = runTest {
        locationSource.willReturn(validFix())

        assertTrue(runScheduledCapture() is CaptureResult.Captured)
    }

    @Test
    fun `surveille trois fois moins souvent que la cadence de capture`() = runTest {
        givenPointRecorded(minutesAgo = 1)

        runScheduledCapture()

        assertEquals(15.minutes, captureScheduler.lastDelay)
    }

    @Test
    fun `une acquisition ratee reprogramme quand meme la surveillance`() = runTest {
        // Le cas qui tue un suivi : un echec, et plus jamais de verification.
        givenPointRecorded(minutesAgo = 30)
        locationSource.willFail(LocationAcquisitionFailure.Timeout)

        runScheduledCapture()

        assertEquals(15.minutes, captureScheduler.lastDelay)
    }

    @Test
    fun `la surveillance se poursuit sur une longue serie d'echecs`() = runTest {
        locationSource.willFail(LocationAcquisitionFailure.Timeout)

        repeat(50) { runScheduledCapture() }

        assertEquals(50, captureScheduler.scheduledDelays.size)
        assertTrue(captureScheduler.scheduledDelays.all { it == 15.minutes })
    }

    @Test
    fun `un arret pendant la verification annule la surveillance`() = runTest {
        givenPointRecorded(minutesAgo = 1)
        intentStore.setEnabled(false)

        runScheduledCapture()

        assertEquals(1, captureScheduler.cancellations)
        assertTrue(captureScheduler.scheduledDelays.isEmpty())
    }

    @Test
    fun `une alarme heritee ne capture pas quand le suivi est deja arrete`() = runTest {
        // StopTracking annule l'alarme, mais Android peut encore livrer un
        // PendingIntent deja parti. L'intention utilisatrice doit primer.
        intentStore.setEnabled(false)
        locationSource.willReturn(validFix())

        val result = runScheduledCapture()

        assertNull(result)
        assertEquals(0, locationSource.acquisitionCount)
        assertEquals(1, captureScheduler.cancellations)
    }

    @Test
    fun `l'intervalle configure change la cadence de surveillance`() = runTest {
        givenPointRecorded(minutesAgo = 1)
        intentStore.setCaptureInterval(CaptureInterval.TWO)

        runScheduledCapture()

        assertEquals(6.minutes, captureScheduler.lastDelay)
    }

    @Test
    fun `une exception inattendue ne laisse pas la surveillance sans alarme`() = runTest {
        val explosive = object : com.madhi.tracker.application.port.LocationSource {
            override fun stream(interval: kotlin.time.Duration) = throw IllegalStateException("panne")
            override suspend fun acquire(timeout: kotlin.time.Duration) = throw IllegalStateException("panne")
        }
        val useCase = RunScheduledCapture(
            captureLocation = CaptureLocation(
                locationSource = explosive,
                trackingIntentStore = intentStore,
                recordLocation = recordLocationWith(locationStore = locationStore, clock = clock),
                rebootJournalStore = FakeRebootJournalStore(),
                eventLog = eventLog,
                clock = clock,
            ),
            captureScheduler = captureScheduler,
            trackingIntentStore = intentStore,
            locationStore = locationStore,
            eventLog = eventLog,
            clock = clock,
        )

        runCatching { useCase() }

        // L'exception remonte pour etre visible, mais l'alarme suivante est
        // programmee avant : la surveillance survit a un bug.
        assertEquals(15.minutes, captureScheduler.lastDelay)
    }
}
