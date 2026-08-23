package com.madhi.tracker.domain.model

import java.time.Instant

/**
 * Un point du tracé, réduit à ce que la carte dessine.
 *
 * Volontairement plus maigre que [LocationPoint] : la carte relit des
 * milliers de points à chaque nouvelle position, et lire la précision, la
 * vitesse, la batterie et le journal d'erreurs pour les jeter aussitôt
 * coûterait ce que l'appareil du voyage n'a pas — 4 Go de RAM et une base
 * qui grossit pendant un an.
 */
data class TrackPoint(
    val coordinates: Coordinates,
    val recordedAt: Instant,
    val syncState: SyncState,
)
