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
import kotlinx.coroutines.test.runTest
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

    private fun worker(): SyncWorker =
        TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(workerFactory())
            .build()

    private fun workerFactory() = object : WorkerFactory() {
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
                RestoreTracking(
                    trackingIntentStore = FakeTrackingIntentStore(TrackingIntent.INITIAL),
                    trackingRuntime = FakeTrackingRuntime(),
                    captureScheduler = FakeCaptureScheduler(),
                    syncScheduler = FakeSyncScheduler(),
                    rebootJournalStore = FakeRebootJournalStore(),
                    locationStore = store,
                    eventLog = eventLog,
                    clock = clock,
                ),
            )
        }
    }
}
