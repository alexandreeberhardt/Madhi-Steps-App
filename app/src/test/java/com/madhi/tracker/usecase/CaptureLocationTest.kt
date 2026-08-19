package com.madhi.tracker.usecase

import com.madhi.tracker.application.usecase.CaptureResult
import com.madhi.tracker.domain.LocationValidation
import com.madhi.tracker.domain.error.LocationAcquisitionFailure
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationFix
import com.madhi.tracker.domain.model.SyncState
import com.madhi.tracker.domain.model.TrackerEvent
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.fakes.FakeClock
import com.madhi.tracker.fakes.captureLocationWith
import com.madhi.tracker.fakes.recordLocationWith
import com.madhi.tracker.fakes.FakeLocationSource
import com.madhi.tracker.fakes.FakeLocationStore
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
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Aucun émulateur, aucun Robolectric, aucun mock : uniquement des doubles
 * simples. C'est le bénéfice concret de la direction des dépendances.
 */
class CaptureLocationTest {

    private val clock = FakeClock()
    private val locationSource = FakeLocationSource()
    private val locationStore = FakeLocationStore()
    private val intentStore = FakeTrackingIntentStore(
        TrackingIntent(enabled = true, captureInterval = CaptureInterval.FIVE),
    )
    private val environment = FakeTrackingEnvironment()
    private val rebootJournal = FakeRebootJournalStore()
    private val syncScheduler = FakeSyncScheduler()
    private val eventLog = RecordingEventLog()

    private val captureLocation = captureLocationWith(
        locationSource = locationSource,
        trackingIntentStore = intentStore,
        recordLocation = recordLocationWith(
            locationStore = locationStore,
            environment = environment,
            rebootJournalStore = rebootJournal,
            syncScheduler = syncScheduler,
            eventLog = eventLog,
            clock = clock,
        ),
        rebootJournalStore = rebootJournal,
        eventLog = eventLog,
        clock = clock,
    )

    private fun validFix(
        latitude: Double = 48.85837,
        recordedAt: Instant = clock.instant,
    ) = LocationFix(
        coordinates = Coordinates(latitude, 2.29448),
        recordedAt = recordedAt,
        accuracyMeters = 12f,
        altitudeMeters = 34.0,
        speedMetersPerSecond = 4.7f,
    )

    @Test
    fun `une position valide est enregistree en attente d'envoi`() = runTest {
        locationSource.willReturn(validFix())

        val result = captureLocation()

        assertTrue(result is CaptureResult.Captured)
        val stored = locationStore.points.values.single()
        assertEquals(SyncState.PENDING, stored.syncState)
        assertEquals(48.85837, stored.coordinates.latitude, 0.00001)
    }

    @Test
    fun `la position est en base avant que la synchronisation soit evoquee`() = runTest {
        // Le principe central du projet : le reseau ne conditionne jamais la
        // conservation d'un point.
        locationSource.willReturn(validFix())

        captureLocation()

        assertEquals(1, locationStore.pendingCount())
        assertEquals(1, syncScheduler.immediateRequests)
    }

    @Test
    fun `le niveau de batterie est joint au point`() = runTest {
        locationSource.willReturn(validFix())

        captureLocation()

        assertEquals(62, locationStore.points.values.single().batteryPercent)
    }

    @Test
    fun `une batterie inconnue ne bloque pas l'enregistrement`() = runTest {
        environment.current = FakeTrackingEnvironment.healthy.copy(batteryPercent = null)
        locationSource.willReturn(validFix())

        captureLocation()

        assertEquals(1, locationStore.points.size)
    }

    @Test
    fun `une acquisition ratee n'ecrit rien et ne demande pas de synchronisation`() = runTest {
        locationSource.willFail(LocationAcquisitionFailure.Timeout)

        val result = captureLocation()

        assertEquals(CaptureResult.Failed(LocationAcquisitionFailure.Timeout), result)
        assertTrue(locationStore.points.isEmpty())
        assertEquals(0, syncScheduler.immediateRequests)
    }

    @Test
    fun `un trou d'acquisition est journalise sans faire echouer la capture`() = runTest {
        // Tunnel, sous-sol, ciel bouche : incident normal, pas une panne.
        locationSource.willFail(LocationAcquisitionFailure.Timeout)

        captureLocation()

        assertTrue(eventLog.events.contains(TrackerEvent.ACQUISITION_FAILED))
        assertFalse(eventLog.events.contains(TrackerEvent.LOCATION_SAVED))
    }

    @Test
    fun `une position invalide est rejetee sans ecriture`() = runTest {
        locationSource.willReturn(validFix().copy(coordinates = Coordinates(0.0, 0.0)))

        val result = captureLocation()

        assertEquals(CaptureResult.Rejected(LocationValidation.Rejection.NullIsland), result)
        assertTrue(locationStore.points.isEmpty())
        assertEquals(0, syncScheduler.immediateRequests)
    }

    @Test
    fun `un horodatage incoherent est rejete`() = runTest {
        locationSource.willReturn(validFix(recordedAt = Instant.parse("1970-01-01T00:00:00Z")))

        val result = captureLocation()

        assertEquals(CaptureResult.Rejected(LocationValidation.Rejection.TimestampTooOld), result)
    }

    @Test
    fun `chaque capture laisse un signe de vie, meme quand elle echoue`() = runTest {
        // C'est ce signe de vie qui permettra de detecter un redemarrage non
        // traite par la suite.
        locationSource.willFail(LocationAcquisitionFailure.LocationDisabled)

        captureLocation()

        assertEquals(1, rebootJournal.aliveRecordings)
        assertEquals(clock.instant, rebootJournal.journal.lastSeenAt)
        assertEquals(clock.uptime, rebootJournal.journal.lastSeenUptime)
    }

    @Test
    fun `l'acquisition ne deborde jamais sur la capture suivante`() = runTest {
        intentStore.setCaptureInterval(CaptureInterval.TWO)
        locationSource.willReturn(validFix())

        captureLocation()

        // Deux minutes d'intervalle : une minute au plus pour acquerir.
        assertEquals(1.minutes, locationSource.lastTimeout)
    }

    @Test
    fun `le delai d'acquisition est plafonne sur les longs intervalles`() = runTest {
        intentStore.setCaptureInterval(CaptureInterval.THIRTY)
        locationSource.willReturn(validFix())

        captureLocation()

        // Au-dela, un fix froid n'arrivera plus : inutile de laisser le GPS allume.
        assertEquals(90.seconds, locationSource.lastTimeout)
    }

    @Test
    fun `deux captures successives produisent deux points distincts`() = runTest {
        locationSource.willReturn(validFix())
        captureLocation()
        clock.advance(5.minutes)
        locationSource.willReturn(validFix(latitude = 48.9, recordedAt = clock.instant))
        captureLocation()

        assertEquals(2, locationStore.points.size)
        assertEquals(2, locationStore.pendingCount())
    }
}
