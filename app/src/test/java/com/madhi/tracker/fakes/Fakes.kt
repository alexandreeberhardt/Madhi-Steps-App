package com.madhi.tracker.fakes

import com.madhi.tracker.application.port.Clock
import com.madhi.tracker.application.port.EnvironmentSnapshot
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.application.port.LocationSource
import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.application.port.RebootJournalStore
import com.madhi.tracker.application.port.SyncScheduler
import com.madhi.tracker.application.port.TrackingEnvironment
import com.madhi.tracker.application.port.TrackingIntentStore
import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.LocationAcquisitionFailure
import com.madhi.tracker.domain.failure
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.LocationFix
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.RebootJournal
import com.madhi.tracker.domain.model.SyncState
import com.madhi.tracker.domain.model.TrackerEvent
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.domain.success
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Des doubles simples plutôt que des mocks : un test qui se lit comme un
 * scénario vaut mieux qu'une pile de vérifications d'appels.
 */

class FakeClock(
    var instant: Instant = Instant.parse("2026-08-18T12:00:00Z"),
    var uptime: Duration = 3.hours,
) : Clock {
    override fun now(): Instant = instant
    override fun uptime(): Duration = uptime

    fun advance(duration: Duration) {
        instant = instant.plusMillis(duration.inWholeMilliseconds)
        uptime += duration
    }
}

class FakeLocationSource : LocationSource {
    /** Réponses servies dans l'ordre ; la dernière est répétée si besoin. */
    var responses: MutableList<Outcome<LocationFix, LocationAcquisitionFailure>> = mutableListOf()
    var lastTimeout: Duration? = null
    var acquisitionCount: Int = 0

    override suspend fun acquire(timeout: Duration): Outcome<LocationFix, LocationAcquisitionFailure> {
        lastTimeout = timeout
        acquisitionCount++
        if (responses.isEmpty()) return failure(LocationAcquisitionFailure.Timeout)
        return if (responses.size == 1) responses.first() else responses.removeAt(0)
    }

    fun willReturn(fix: LocationFix) {
        responses = mutableListOf(success(fix))
    }

    fun willFail(reason: LocationAcquisitionFailure) {
        responses = mutableListOf(failure(reason))
    }
}

class FakeLocationStore : LocationStore {
    val points = linkedMapOf<LocationId, LocationPoint>()

    override suspend fun save(point: LocationPoint) {
        // Reproduit le INSERT OR IGNORE de Room : un identifiant connu n'est
        // jamais réécrit, donc jamais ramené en attente.
        points.putIfAbsent(point.id, point)
    }

    override suspend fun oldestPending(limit: Int): List<LocationPoint> =
        points.values.filter { it.syncState == SyncState.PENDING }
            .sortedBy { it.recordedAt }
            .take(limit)

    override suspend fun markSynced(ids: List<LocationId>, at: Instant) {
        ids.forEach { id ->
            points[id]?.takeIf { it.syncState == SyncState.PENDING }?.let {
                points[id] = it.copy(syncState = SyncState.SYNCED, lastAttemptAt = at, lastErrorCode = null)
            }
        }
    }

    override suspend fun recordFailedAttempt(ids: List<LocationId>, at: Instant, errorCode: String) {
        ids.forEach { id ->
            points[id]?.takeIf { it.syncState == SyncState.PENDING }?.let {
                points[id] = it.copy(
                    attemptCount = it.attemptCount + 1,
                    lastAttemptAt = at,
                    lastErrorCode = errorCode,
                )
            }
        }
    }

    override suspend fun pendingCount(): Int = points.values.count { it.syncState == SyncState.PENDING }

    override suspend fun lastRecordedAt(): Instant? = points.values.maxOfOrNull { it.recordedAt }

    override suspend fun oldestPendingRecordedAt(): Instant? =
        points.values.filter { it.syncState == SyncState.PENDING }.minOfOrNull { it.recordedAt }

    override fun observePendingCount(): Flow<Int> = MutableStateFlow(0)

    override fun observeLastRecordedAt(): Flow<Instant?> = MutableStateFlow(null)
}

class FakeTrackingIntentStore(initial: TrackingIntent = TrackingIntent.INITIAL) : TrackingIntentStore {
    private val state = MutableStateFlow(initial)

    override suspend fun read(): TrackingIntent = state.value

    override suspend fun setEnabled(enabled: Boolean) {
        state.value = state.value.copy(enabled = enabled)
    }

    override suspend fun setCaptureInterval(interval: CaptureInterval) {
        state.value = state.value.copy(captureInterval = interval)
    }

    override fun observe(): Flow<TrackingIntent> = state
}

class FakeTrackingEnvironment(
    var current: EnvironmentSnapshot = healthy,
) : TrackingEnvironment {
    override fun snapshot(): EnvironmentSnapshot = current
    override fun observe(): Flow<EnvironmentSnapshot> = MutableStateFlow(current)

    companion object {
        val healthy = EnvironmentSnapshot(
            hasForegroundLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasNotificationPermission = true,
            isLocationEnabled = true,
            isIgnoringBatteryOptimizations = true,
            canScheduleExactAlarms = true,
            isOnline = true,
            batteryPercent = 62,
        )
    }
}

class FakeRebootJournalStore : RebootJournalStore {
    var journal: RebootJournal = RebootJournal.EMPTY
    var aliveRecordings: Int = 0

    override suspend fun read(): RebootJournal = journal

    override suspend fun recordAlive(at: Instant, uptime: Duration) {
        aliveRecordings++
        journal = journal.copy(lastSeenAt = at, lastSeenUptime = uptime)
    }

    override suspend fun recordBootHandled(at: Instant) {
        journal = journal.copy(bootHandledAt = at)
    }
}

class FakeSyncScheduler : SyncScheduler {
    var periodicScheduled: Boolean = false
    var immediateRequests: Int = 0

    override fun ensurePeriodicSyncScheduled() {
        periodicScheduled = true
    }

    override fun requestImmediateSync() {
        immediateRequests++
    }
}

class RecordingEventLog : EventLog {
    val events = mutableListOf<TrackerEvent>()
    val details = mutableListOf<Pair<TrackerEvent, String?>>()

    override fun record(event: TrackerEvent, detail: String?) {
        events += event
        details += event to detail
    }
}
