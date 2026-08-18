package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.Clock
import com.madhi.tracker.application.port.DeviceCredentials
import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.application.port.RebootJournalStore
import com.madhi.tracker.application.port.SyncJournalStore
import com.madhi.tracker.application.port.TrackingEnvironment
import com.madhi.tracker.application.port.TrackingIntentStore
import com.madhi.tracker.domain.RebootDetection
import com.madhi.tracker.domain.TrackingHealthPolicy
import com.madhi.tracker.domain.TrackingProblemDetection
import com.madhi.tracker.domain.model.TrackingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * L'état du suivi tel que l'écran d'accueil le montre, en flux.
 *
 * L'accueil doit rester juste sans intervention : brancher le réseau, couper
 * le GPS ou arrêter le suivi doit se voir immédiatement, sans que
 * l'utilisatrice ait à rafraîchir quoi que ce soit.
 */
class ObserveTrackingStatus @Inject constructor(
    private val trackingIntentStore: TrackingIntentStore,
    private val locationStore: LocationStore,
    private val syncJournalStore: SyncJournalStore,
    private val rebootJournalStore: RebootJournalStore,
    private val environment: TrackingEnvironment,
    private val credentials: DeviceCredentials,
    private val clock: Clock,
) {

    operator fun invoke(): Flow<TrackingStatus> = combine(
        trackingIntentStore.observe(),
        environment.observe(),
        locationStore.observePendingCount(),
        locationStore.observeLastRecordedAt(),
        syncJournalStore.observe(),
    ) { intent, snapshot, pendingCount, lastRecordedAt, syncJournal ->
        val problems = TrackingProblemDetection.detect(
            hasForegroundLocationPermission = snapshot.hasForegroundLocationPermission,
            hasBackgroundLocationPermission = snapshot.hasBackgroundLocationPermission,
            hasNotificationPermission = snapshot.hasNotificationPermission,
            isLocationEnabled = snapshot.isLocationEnabled,
            isIgnoringBatteryOptimizations = snapshot.isIgnoringBatteryOptimizations,
            canScheduleExactAlarms = snapshot.canScheduleExactAlarms,
            autostartBlocked = RebootDetection.rebootWasMissed(
                journal = rebootJournalStore.read(),
                now = clock.now(),
                uptime = clock.uptime(),
            ),
            deviceActivated = credentials.isActivated(),
            // Un token refusé fait s'accumuler les points sans qu'aucun ne
            // parte : c'est un problème visible, pas une simple statistique.
            authenticationFailed = syncJournal.lastFailureCode == "unauthorized",
        )

        TrackingHealthPolicy.evaluate(
            intent = intent,
            problems = problems,
            isOnline = snapshot.isOnline,
            pendingCount = pendingCount,
            lastPointAt = lastRecordedAt,
        )
    }.distinctUntilChanged()
}
