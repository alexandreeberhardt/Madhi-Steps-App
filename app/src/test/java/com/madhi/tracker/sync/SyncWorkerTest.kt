package com.madhi.tracker.sync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.madhi.tracker.adapter.input.sync.SyncWorker
import com.madhi.tracker.application.port.TrackingIntentStore
import com.madhi.tracker.application.usecase.RestoreTracking
import com.madhi.tracker.application.usecase.SyncPendingLocations
import com.madhi.tracker.domain.error.SyncFailure
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.fakes.FakeCaptureScheduler
import com.madhi.tracker.fakes.FakeClock
import com.madhi.tracker.fakes.FakeLocationStore
import com.madhi.tracker.fakes.FakeLocationSyncGateway
import com.madhi.tracker.fakes.FakeRebootJournalStore
import com.madhi.tracker.fakes.FakeSyncScheduler
import com.madhi.tracker.fakes.FakeTrackingIntentStore
import com.madhi.tracker.fakes.FakeTrackingRuntime
import com.madhi.tracker.fakes.RecordingEventLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SyncWorkerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val store = FakeLocationStore()
    private val gateway = FakeLocationSyncGateway()
    private val eventLog = RecordingEventLog()

    @Test
    fun `un echec reseau ne renvoie jamais retry a WorkManager`() = runTest {
        assertWorkerSucceedsAfter(SyncFailure.NoNetwork)
    }

    @Test
    fun `un timeout de synchronisation ne renvoie jamais retry a WorkManager`() = runTest {
        assertWorkerSucceedsAfter(SyncFailure.Timeout)
    }

    @Test
    fun `une erreur serveur ne renvoie jamais retry a WorkManager`() = runTest {
        assertWorkerSucceedsAfter(SyncFailure.ServerError(503))
    }

    @Test
    fun `une panne du watchdog n'empeche pas de vider le backlog`() = runTest {
        givenPendingPoint()

        val result = worker(restoreTrackingWithBrokenIntent()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, store.pendingCount())
        assertEquals(1, gateway.uploadCount)
    }

    private suspend fun assertWorkerSucceedsAfter(failure: SyncFailure) {
        givenPendingPoint()
        gateway.failures.addLast(failure)

        val result = worker().doWork()

        // Le backoff WorkManager a deja bloque le rattrapage sur appareil :
        // le retry periodique doit venir de la cadence fixe, pas d'un TIMING_DELAY.
        assertTrue(result is ListenableWorker.Result.Success)
    }

    private suspend fun givenPendingPoint() {
        store.save(
            LocationPoint(
                id = LocationId.random(),
                coordinates = Coordinates(48.85837, 2.29448),
                recordedAt = clock.now(),
            ),
        )
    }

    private fun worker(restoreTracking: RestoreTracking = defaultRestoreTracking()): SyncWorker =
        TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(workerFactory(restoreTracking))
            .build()

    private fun workerFactory(restoreTracking: RestoreTracking) = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? {
            if (workerClassName != SyncWorker::class.java.name) return null

            return SyncWorker(
                appContext,
                workerParameters,
                SyncPendingLocations(store, gateway, eventLog, clock),
                restoreTracking,
            )
        }
    }

    private fun defaultRestoreTracking() = RestoreTracking(
        trackingIntentStore = FakeTrackingIntentStore(TrackingIntent.INITIAL),
        trackingRuntime = FakeTrackingRuntime(),
        captureScheduler = FakeCaptureScheduler(),
        syncScheduler = FakeSyncScheduler(),
        rebootJournalStore = FakeRebootJournalStore(),
        locationStore = store,
        eventLog = eventLog,
        clock = clock,
    )

    private fun restoreTrackingWithBrokenIntent() = RestoreTracking(
        trackingIntentStore = object : TrackingIntentStore {
            override suspend fun read(): TrackingIntent = throw IllegalStateException("datastore indisponible")
            override suspend fun setEnabled(enabled: Boolean) = Unit
            override suspend fun setCaptureInterval(interval: com.madhi.tracker.domain.model.CaptureInterval) = Unit
            override fun observe(): Flow<TrackingIntent> = emptyFlow()
        },
        trackingRuntime = FakeTrackingRuntime(),
        captureScheduler = FakeCaptureScheduler(),
        syncScheduler = FakeSyncScheduler(),
        rebootJournalStore = FakeRebootJournalStore(),
        locationStore = store,
        eventLog = eventLog,
        clock = clock,
    )
}
