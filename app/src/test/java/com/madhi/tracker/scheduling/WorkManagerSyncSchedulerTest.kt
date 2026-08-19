package com.madhi.tracker.scheduling

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.model.WorkSpec
import androidx.work.testing.WorkManagerTestInitHelper
import com.madhi.tracker.adapter.input.sync.SyncWorker
import com.madhi.tracker.adapter.output.scheduling.WorkManagerSyncScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WorkManagerSyncSchedulerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerSyncScheduler

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerSyncScheduler(context)
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `une demande immediate remplace un travail herite bloque en backoff`() {
        val legacy = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(5, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniqueWork(
            SyncWorker.IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            legacy,
        ).result.get()
        markBackedOff(legacy.stringId)

        scheduler.requestImmediateSync()

        val infos = workInfos(SyncWorker.IMMEDIATE_WORK_NAME)
        val active = infos.single { !it.state.isFinished }
        val activeSpec = workSpec(active.id.toString())

        val legacyInfo = infoFor(legacy.stringId)
        assertTrue(legacyInfo == null || legacyInfo.state == WorkInfo.State.CANCELLED)
        assertNotEquals(legacy.id, active.id)
        assertEquals(WorkInfo.State.ENQUEUED, active.state)
        assertEquals(0, active.initialDelayMillis)
        assertEquals(0, active.runAttemptCount)
        assertEquals(NetworkType.CONNECTED, active.constraints.requiredNetworkType)
        assertFalse(activeSpec.isBackedOff)
        assertFalse(activeSpec.backOffOnSystemInterruptions ?: false)
    }

    @Test
    fun `plusieurs demandes immediates ne creent jamais une file de rattrapage`() {
        repeat(5) { scheduler.requestImmediateSync() }

        val infos = workInfos(SyncWorker.IMMEDIATE_WORK_NAME)
        val active = infos.filter { !it.state.isFinished }

        // Après une rafale de captures, il ne faut qu'un seul rattrapage :
        // empiler les workers augmente la radio sans rendre les points plus sûrs.
        assertEquals(1, active.size)
        assertTrue(infos.size <= 5)
        assertEquals(0, active.single().runAttemptCount)
    }

    @Test
    fun `la planification periodique met a jour un travail herite au lieu de le garder`() {
        val legacy = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            legacy,
        ).result.get()

        scheduler.ensurePeriodicSyncScheduled()

        val info = workInfos(SyncWorker.PERIODIC_WORK_NAME).single()
        val spec = workSpec(info.id.toString())

        assertEquals(legacy.id, info.id)
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(1, info.generation)
        assertEquals(0, info.runAttemptCount)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(15.minutesInMillis, info.periodicityInfo!!.repeatIntervalMillis)
        assertFalse(spec.isBackedOff)
        assertFalse(spec.backOffOnSystemInterruptions ?: false)
    }

    private fun markBackedOff(id: String) {
        val dao = workDatabase().workSpecDao()
        dao.incrementWorkSpecRunAttemptCount(id)
        assertTrue(workSpec(id).isBackedOff)
    }

    private fun workInfos(name: String): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(name).get()

    private fun infoFor(id: String): WorkInfo? =
        workManager.getWorkInfoById(java.util.UUID.fromString(id)).get()

    private fun workSpec(id: String): WorkSpec =
        workDatabase().workSpecDao().getWorkSpec(id)!!

    private fun workDatabase() = WorkManagerImpl.getInstance(context).workDatabase

    private val Int.minutesInMillis: Long get() = TimeUnit.MINUTES.toMillis(toLong())
}
