package com.madhi.tracker.fakes

import com.madhi.tracker.application.port.CaptureScheduler
import com.madhi.tracker.application.port.Clock
import com.madhi.tracker.application.port.EnvironmentSnapshot
import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.application.port.LocationSource
import com.madhi.tracker.application.port.BatchAcknowledgement
import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.application.port.TileStore
import com.madhi.tracker.application.port.LocationSyncGateway
import com.madhi.tracker.application.port.DeviceActivationGateway
import com.madhi.tracker.application.port.DeviceCredentials
import com.madhi.tracker.application.port.OnboardingStore
import com.madhi.tracker.application.port.SyncJournalStore
import com.madhi.tracker.application.port.RejectedPoint
import com.madhi.tracker.application.port.RebootJournalStore
import com.madhi.tracker.application.port.SyncScheduler
import com.madhi.tracker.application.port.TrackingEnvironment
import com.madhi.tracker.application.port.TrackingIntentStore
import com.madhi.tracker.application.port.TrackingRuntime
import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.LocationAcquisitionFailure
import com.madhi.tracker.domain.error.ActivationFailure
import com.madhi.tracker.domain.error.SyncFailure
import com.madhi.tracker.domain.model.DeviceActivation
import com.madhi.tracker.domain.failure
import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.DeviceVendor
import com.madhi.tracker.domain.model.LocationFix
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.RebootJournal
import com.madhi.tracker.domain.model.SyncJournal
import com.madhi.tracker.domain.model.SyncState
import com.madhi.tracker.domain.model.TrackerEvent
import com.madhi.tracker.domain.TileId
import com.madhi.tracker.domain.model.TrackPoint
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.domain.success
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
    /** Positions poussées par le flux ; les tests y émettent à la demande. */
    val streamed = MutableSharedFlow<LocationFix>(extraBufferCapacity = 64)
    var lastStreamInterval: Duration? = null
    var streamSubscriptions: Int = 0

    override fun stream(interval: Duration): Flow<LocationFix> {
        lastStreamInterval = interval
        streamSubscriptions++
        return streamed
    }

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

    override suspend fun countRecordedSince(since: Instant): Int =
        points.values.count { !it.recordedAt.isBefore(since) }

    override suspend fun lastRecordedAt(): Instant? = points.values.maxOfOrNull { it.recordedAt }

    override suspend fun oldestPendingRecordedAt(): Instant? =
        points.values.filter { it.syncState == SyncState.PENDING }.minOfOrNull { it.recordedAt }

    override fun observePendingCount(): Flow<Int> = MutableStateFlow(0)

    override fun observeLastRecordedAt(): Flow<Instant?> = MutableStateFlow(null)

    override fun observeRecentTrack(limit: Int): Flow<List<TrackPoint>> = MutableStateFlow(
        points.values
            .sortedBy { it.recordedAt }
            .takeLast(limit)
            .map { TrackPoint(it.coordinates, it.recordedAt, it.syncState) },
    )
}

/**
 * Une carte sans fond, comme lorsque aucun serveur de tuiles n'est configuré :
 * c'est la valeur par défaut du dépôt, donc le cas à tester par défaut.
 */
class FakeTileStore(
    override val isEnabled: Boolean = false,
    override val attribution: String = "",
    private val tiles: Map<TileId, ByteArray> = emptyMap(),
) : TileStore {
    override suspend fun tile(id: TileId): ByteArray? = tiles[id]
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
            vendor = DeviceVendor.OTHER,
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

class FakeCaptureScheduler : CaptureScheduler {
    val scheduledDelays = mutableListOf<Duration>()
    var cancellations: Int = 0

    val lastDelay: Duration? get() = scheduledDelays.lastOrNull()

    override fun scheduleNext(delay: Duration) {
        scheduledDelays += delay
    }

    override fun cancel() {
        cancellations++
    }
}

class FakeTrackingRuntime : TrackingRuntime {
    var running: Boolean = false
    var starts: Int = 0
    var stops: Int = 0

    override fun start() {
        running = true
        starts++
    }

    override fun stop() {
        running = false
        stops++
    }

    override fun isRunning(): Boolean = running
}

/**
 * Simule un serveur qui applique l'idempotence : il retient les
 * identifiants déjà reçus et les renvoie en `duplicates`, exactement comme
 * le prévoit `arch/03` §9.
 */
class FakeLocationSyncGateway : LocationSyncGateway {
    private val storedOnServer = mutableSetOf<LocationId>()

    /** Échecs servis dans l'ordre avant de reprendre le comportement normal. */
    val failures: ArrayDeque<SyncFailure> = ArrayDeque()

    /** Simule une réponse perdue : le serveur reçoit, le client ne voit rien. */
    var dropNextResponse: Boolean = false

    var rejectedIds: Set<LocationId> = emptySet()
    val uploadedBatchSizes = mutableListOf<Int>()
    var uploadCount: Int = 0

    override suspend fun upload(points: List<LocationPoint>): Outcome<BatchAcknowledgement, SyncFailure> {
        uploadCount++
        uploadedBatchSizes += points.size

        failures.removeFirstOrNull()?.let { return failure(it) }

        val accepted = mutableListOf<LocationId>()
        val duplicates = mutableListOf<LocationId>()
        val rejected = mutableListOf<RejectedPoint>()

        points.forEach { point ->
            when {
                point.id in rejectedIds -> rejected += RejectedPoint(point.id, "invalid_coordinates")
                !storedOnServer.add(point.id) -> duplicates += point.id
                else -> accepted += point.id
            }
        }

        if (dropNextResponse) {
            dropNextResponse = false
            // Le serveur a bien enregistre : c'est la reponse qui se perd.
            return failure(SyncFailure.Timeout)
        }

        return success(BatchAcknowledgement(accepted, duplicates, rejected))
    }

    fun serverHolds(id: LocationId): Boolean = id in storedOnServer
}

class FakeDeviceActivationGateway : DeviceActivationGateway {
    var response: Outcome<DeviceActivation, ActivationFailure> =
        success(DeviceActivation(deviceId = "device-42", deviceToken = "token-secret", tripId = "trip-7"))

    var lastCode: String? = null
    var lastDeviceName: String? = null
    var callCount: Int = 0

    override suspend fun activate(
        activationCode: String,
        deviceName: String,
    ): Outcome<DeviceActivation, ActivationFailure> {
        callCount++
        lastCode = activationCode
        lastDeviceName = deviceName
        return response
    }
}

class FakeDeviceCredentials : DeviceCredentials {
    var activation: DeviceActivation? = null

    override suspend fun store(activation: DeviceActivation) {
        this.activation = activation
    }

    override suspend fun isActivated(): Boolean = activation != null
    override suspend fun deviceId(): String? = activation?.deviceId
    override suspend fun tripId(): String? = activation?.tripId
    override suspend fun authorizationHeaderValue(): String? = activation?.let { "Bearer ${it.deviceToken}" }
}

class FakeOnboardingStore : OnboardingStore {
    private val state = MutableStateFlow(false)
    override suspend fun isCompleted(): Boolean = state.value
    override suspend fun markCompleted() { state.value = true }
    override fun observe(): Flow<Boolean> = state
}

class FakeSyncJournalStore : SyncJournalStore {
    private val state = MutableStateFlow(SyncJournal.EMPTY)

    override suspend fun read(): SyncJournal = state.value

    override suspend fun recordAttempt(at: Instant, batchSize: Int) {
        state.value = state.value.copy(lastAttemptAt = at, lastBatchSize = batchSize)
    }

    override suspend fun recordSuccess(at: Instant) {
        state.value = state.value.copy(lastSuccessAt = at, lastFailureCode = null, consecutiveFailures = 0)
    }

    override suspend fun recordFailure(at: Instant, failure: com.madhi.tracker.domain.error.SyncFailure) {
        state.value = state.value.copy(
            lastAttemptAt = at,
            lastFailureCode = failure.code,
            consecutiveFailures = state.value.consecutiveFailures + 1,
        )
    }

    override fun observe(): Flow<SyncJournal> = state
}
