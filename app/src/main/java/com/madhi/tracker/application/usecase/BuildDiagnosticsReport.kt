package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.Clock
import com.madhi.tracker.application.port.DeviceCredentials
import com.madhi.tracker.application.port.EnvironmentSnapshot
import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.application.port.RebootJournalStore
import com.madhi.tracker.application.port.SyncJournalStore
import com.madhi.tracker.application.port.TrackingEnvironment
import com.madhi.tracker.application.port.TrackingIntentStore
import com.madhi.tracker.application.port.TrackingRuntime
import com.madhi.tracker.domain.RebootDetection
import com.madhi.tracker.domain.TrackingCoverage
import com.madhi.tracker.domain.TrackingHealthPolicy
import com.madhi.tracker.domain.model.SyncJournal
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.domain.model.TrackingProblem
import com.madhi.tracker.domain.model.TrackingStatus
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Rassemble tout ce qu'il faut savoir quand le suivi semble bloqué.
 *
 * C'est l'outil de dépannage à distance : ce que la voyageuse pourra lire au
 * téléphone quand on lui demandera « qu'est-ce que ça affiche ? ».
 */
class BuildDiagnosticsReport @Inject constructor(
    private val trackingIntentStore: TrackingIntentStore,
    private val locationStore: LocationStore,
    private val syncJournalStore: SyncJournalStore,
    private val rebootJournalStore: RebootJournalStore,
    private val environment: TrackingEnvironment,
    private val trackingRuntime: TrackingRuntime,
    private val credentials: DeviceCredentials,
    private val clock: Clock,
) {

    suspend operator fun invoke(coverageWindow: Duration = DEFAULT_COVERAGE_WINDOW): DiagnosticsReport {
        val intent = trackingIntentStore.read()
        val snapshot = environment.snapshot()
        val now = clock.now()

        val autostartBlocked = RebootDetection.rebootWasMissed(
            journal = rebootJournalStore.read(),
            now = now,
            uptime = clock.uptime(),
        )

        val problems = detectProblems(snapshot, autostartBlocked, credentials.isActivated())

        val coverage = TrackingCoverage.evaluate(
            window = coverageWindow,
            interval = intent.captureInterval,
            actualCount = locationStore.countRecordedSince(now.minusMillis(coverageWindow.inWholeMilliseconds)),
        )

        return DiagnosticsReport(
            status = TrackingHealthPolicy.evaluate(
                intent = intent,
                problems = problems,
                isOnline = snapshot.isOnline,
                pendingCount = locationStore.pendingCount(),
                lastPointAt = locationStore.lastRecordedAt(),
            ),
            intent = intent,
            environment = snapshot,
            syncJournal = syncJournalStore.read(),
            serviceRunning = trackingRuntime.isRunning(),
            deviceActivated = credentials.isActivated(),
            oldestPendingAt = locationStore.oldestPendingRecordedAt(),
            coverage = coverage,
            coverageWindow = coverageWindow,
            generatedAt = now,
        )
    }

    private fun detectProblems(
        snapshot: EnvironmentSnapshot,
        autostartBlocked: Boolean,
        activated: Boolean,
    ): List<TrackingProblem> = buildList {
        if (!activated) add(TrackingProblem.DEVICE_NOT_ACTIVATED)
        if (!snapshot.hasForegroundLocationPermission) add(TrackingProblem.LOCATION_PERMISSION_MISSING)
        if (!snapshot.hasBackgroundLocationPermission) {
            add(TrackingProblem.BACKGROUND_LOCATION_PERMISSION_MISSING)
        }
        if (!snapshot.isLocationEnabled) add(TrackingProblem.LOCATION_DISABLED)
        if (autostartBlocked) add(TrackingProblem.AUTOSTART_BLOCKED)
        if (!snapshot.isIgnoringBatteryOptimizations) add(TrackingProblem.BATTERY_OPTIMIZATION_ENABLED)
        if (!snapshot.canScheduleExactAlarms) add(TrackingProblem.EXACT_ALARM_NOT_PERMITTED)
        if (!snapshot.hasNotificationPermission) add(TrackingProblem.NOTIFICATIONS_BLOCKED)
    }

    private companion object {
        // Une heure : assez long pour que le verdict ait du sens à cinq
        // minutes de cadence, assez court pour refléter l'état actuel.
        val DEFAULT_COVERAGE_WINDOW = 1.hours
    }
}

data class DiagnosticsReport(
    val status: TrackingStatus,
    val intent: TrackingIntent,
    val environment: EnvironmentSnapshot,
    val syncJournal: SyncJournal,
    val serviceRunning: Boolean,
    val deviceActivated: Boolean,
    val oldestPendingAt: Instant?,
    val coverage: TrackingCoverage.Coverage,
    val coverageWindow: Duration,
    val generatedAt: Instant,
)
