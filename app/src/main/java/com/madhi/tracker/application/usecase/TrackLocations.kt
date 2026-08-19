package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.LocationSource
import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.domain.CaptureThrottle
import com.madhi.tracker.application.port.TrackingIntentStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * Le suivi continu : le sous-système de localisation cadence, l'application
 * enregistre.
 *
 * C'est le mécanisme principal depuis l'échec du test T1, où l'alarme
 * demandée exacte revenait avec une fenêtre de 225 secondes imposée par le
 * constructeur, sans que l'application puisse le détecter.
 *
 * Changer l'intervalle dans les réglages réabonne automatiquement : c'est le
 * rôle de `flatMapLatest`, qui referme le flux précédent — donc relâche le
 * récepteur — avant d'en ouvrir un nouveau.
 */
class TrackLocations @Inject constructor(
    private val locationSource: LocationSource,
    private val trackingIntentStore: TrackingIntentStore,
    private val recordLocation: RecordLocation,
    private val locationStore: LocationStore,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend operator fun invoke() {
        trackingIntentStore.observe()
            .distinctUntilChanged()
            .flatMapLatest { intent ->
                if (intent.enabled) {
                    locationSource.stream(intent.captureInterval.duration)
                } else {
                    emptyFlow()
                }
            }
            .collect { fix ->
                // Les deux fournisseurs livrent indépendamment : sans ce
                // filtre on enregistre deux points par intervalle.
                val redundant = CaptureThrottle.isRedundant(
                    lastRecordedAt = locationStore.lastRecordedAt(),
                    candidateAt = fix.recordedAt,
                    interval = trackingIntentStore.read().captureInterval,
                )
                if (!redundant) recordLocation(fix)
            }
    }
}
