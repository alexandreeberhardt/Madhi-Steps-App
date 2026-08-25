package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.Clock
import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.domain.TrackWindow
import com.madhi.tracker.domain.model.TrackPeriod
import com.madhi.tracker.domain.model.TrackPoint
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId
import javax.inject.Inject

/**
 * Le tracé affiché par la carte, pour la période demandée.
 *
 * La borne est calculée à l'abonnement et non à chaque émission : « aujourd'hui »
 * ne doit pas se redéfinir sous les yeux de la voyageuse parce qu'une position
 * vient d'arriver. Le passage de minuit se rattrape au retour sur l'écran, ce
 * qui suffit.
 */
class ObserveTrack @Inject constructor(
    private val locationStore: LocationStore,
    private val clock: Clock,
) {

    operator fun invoke(
        period: TrackPeriod,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<TrackPoint>> = locationStore.observeTrack(
        since = TrackWindow.since(period, clock.now(), zone),
        bucketMillis = TrackWindow.bucketMillis(period),
    )
}
