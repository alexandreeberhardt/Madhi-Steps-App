package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.LocationStore
import com.madhi.tracker.domain.model.TrackPoint
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Le tracé récent affiché par l'écran d'accueil.
 *
 * `arch/09` §2 parle de « polyline du trajet récent », pas du voyage entier :
 * ce que la carte doit répondre, c'est « où suis-je et d'où est-ce que
 * j'arrive », pas « qu'ai-je fait il y a huit mois ». L'historique complet est
 * l'affaire du site familial, qui a un écran et un processeur pour ça.
 */
class ObserveRecentTrack @Inject constructor(
    private val locationStore: LocationStore,
) {

    operator fun invoke(limit: Int = RECENT_POINT_LIMIT): Flow<List<TrackPoint>> =
        locationStore.observeRecentTrack(limit)

    companion object {
        /**
         * Environ une semaine de voyage à la cadence par défaut de 5 minutes.
         *
         * Ce plafond protège deux choses à la fois : la mémoire d'un appareil
         * à 4 Go, et la lisibilité — au-delà, le tracé d'une semaine se
         * confond avec celui du mois précédent.
         */
        const val RECENT_POINT_LIMIT = 2_000
    }
}
