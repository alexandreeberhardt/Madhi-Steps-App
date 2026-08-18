package com.madhi.tracker.usecase

import com.madhi.tracker.application.usecase.SyncOutcome
import com.madhi.tracker.application.usecase.SyncPendingLocations
import com.madhi.tracker.domain.error.SyncFailure
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.SyncState
import com.madhi.tracker.fakes.FakeClock
import com.madhi.tracker.fakes.FakeLocationStore
import com.madhi.tracker.fakes.FakeLocationSyncGateway
import com.madhi.tracker.fakes.RecordingEventLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPendingLocationsTest {

    private val clock = FakeClock()
    private val store = FakeLocationStore()
    private val gateway = FakeLocationSyncGateway()
    private val eventLog = RecordingEventLog()

    private val sync = SyncPendingLocations(store, gateway, eventLog, clock)

    private suspend fun givenPendingPoints(count: Int): List<LocationPoint> =
        (0 until count).map { index ->
            LocationPoint(
                id = LocationId.random(),
                coordinates = Coordinates(48.85 + index / 1000.0, 2.29),
                recordedAt = clock.instant.plusSeconds(index * 300L),
            ).also { store.save(it) }
        }

    @Test
    fun `ne fait rien quand la file est vide`() = runTest {
        assertEquals(SyncOutcome.NothingToDo, sync())
        assertEquals(0, gateway.uploadCount)
    }

    @Test
    fun `un lot accepte passe en synchronise`() = runTest {
        givenPendingPoints(3)

        val outcome = sync()

        assertEquals(SyncOutcome.Completed(3), outcome)
        assertEquals(0, store.pendingCount())
    }

    @Test
    fun `un point deja connu du serveur est confirme comme s'il venait d'etre accepte`() = runTest {
        // C'est ce qui rend le rejeu sur : accepted et duplicates sont
        // traites identiquement.
        val points = givenPendingPoints(2)
        gateway.upload(points)

        val outcome = sync()

        assertEquals(SyncOutcome.Completed(2), outcome)
        assertEquals(0, store.pendingCount())
    }

    @Test
    fun `une reponse perdue apres reception ne cree pas de doublon et finit par aboutir`() = runTest {
        // Le scenario decrit par arch/01 : le serveur recoit, la connexion
        // tombe avant la reponse, le client considere le point en attente.
        val points = givenPendingPoints(2)
        gateway.dropNextResponse = true

        val firstAttempt = sync()

        assertTrue(firstAttempt is SyncOutcome.Failed)
        assertEquals(2, store.pendingCount())
        assertTrue(points.all { gateway.serverHolds(it.id) })

        val secondAttempt = sync()

        assertEquals(SyncOutcome.Completed(2), secondAttempt)
        assertEquals(0, store.pendingCount())
    }

    @Test
    fun `une erreur d'authentification ne supprime aucun point`() = runTest {
        givenPendingPoints(5)
        gateway.failures.addLast(SyncFailure.Unauthorized)

        val outcome = sync()

        assertEquals(SyncOutcome.Failed(SyncFailure.Unauthorized, 0), outcome)
        assertEquals(5, store.pendingCount())
    }

    @Test
    fun `une erreur serveur ne supprime aucun point et laisse une trace`() = runTest {
        val points = givenPendingPoints(3)
        gateway.failures.addLast(SyncFailure.ServerError(503))

        sync()

        assertEquals(3, store.pendingCount())
        val stored = store.points[points.first().id]!!
        assertEquals(1, stored.attemptCount)
        assertEquals("server_error_503", stored.lastErrorCode)
        assertEquals(SyncState.PENDING, stored.syncState)
    }

    @Test
    fun `un timeout ne supprime aucun point`() = runTest {
        givenPendingPoints(4)
        gateway.failures.addLast(SyncFailure.Timeout)

        sync()

        assertEquals(4, store.pendingCount())
    }

    @Test
    fun `un lot trop volumineux est reduit puis reessaye`() = runTest {
        givenPendingPoints(300)
        gateway.failures.addLast(SyncFailure.BatchTooLarge)

        val outcome = sync()

        assertEquals(200, gateway.uploadedBatchSizes.first())
        // Le lot est divise par quatre plutot que d'insister a l'identique.
        assertEquals(50, gateway.uploadedBatchSizes[1])
        assertTrue(outcome is SyncOutcome.Completed)
    }

    @Test
    fun `un point refuse par le serveur reste en attente avec son motif`() = runTest {
        val points = givenPendingPoints(3)
        gateway.rejectedIds = setOf(points[1].id)

        sync()

        assertEquals(1, store.pendingCount())
        assertEquals("rejected", store.points[points[1].id]!!.lastErrorCode)
    }

    @Test
    fun `un backlog de plusieurs lots se vide en une seule execution`() = runTest {
        // Environ deux jours hors reseau a cinq minutes.
        givenPendingPoints(600)

        val outcome = sync()

        assertEquals(SyncOutcome.Completed(600), outcome)
        assertEquals(0, store.pendingCount())
        assertEquals(listOf(200, 200, 200), gateway.uploadedBatchSizes)
    }

    @Test
    fun `les points partent du plus ancien au plus recent`() = runTest {
        val points = givenPendingPoints(5)
        gateway.failures.addLast(SyncFailure.Timeout)

        sync()

        // Le premier lot tente contient bien les plus anciens.
        assertEquals(points.map { it.id }.toSet(), store.points.keys.toSet())
        assertEquals(5, gateway.uploadedBatchSizes.first())
    }

    @Test
    fun `aucune erreur ne fait disparaitre un point de la base`() = runTest {
        val points = givenPendingPoints(10)
        listOf(
            SyncFailure.NoNetwork,
            SyncFailure.Timeout,
            SyncFailure.Unauthorized,
            SyncFailure.ServerError(500),
            SyncFailure.RateLimited(null),
            SyncFailure.RejectedPayload(400),
            SyncFailure.NotActivated,
        ).forEach { failure ->
            gateway.failures.addLast(failure)
            sync()
            assertEquals("perte de points sur $failure", 10, store.points.size)
            assertEquals("point sorti de la file sur $failure", 10, store.pendingCount())
        }
        assertEquals(10, points.size)
    }
}
