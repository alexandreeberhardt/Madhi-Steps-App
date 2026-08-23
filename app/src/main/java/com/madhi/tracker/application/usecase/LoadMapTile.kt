package com.madhi.tracker.application.usecase

import com.madhi.tracker.application.port.TileStore
import com.madhi.tracker.domain.TileId
import javax.inject.Inject

/**
 * Une tuile de fond de carte, ou rien.
 *
 * Rien est une réponse acceptable et fréquente : la carte doit rester utile
 * sans fond, puisque le tracé, lui, vient toujours de la base locale.
 */
class LoadMapTile @Inject constructor(
    private val tileStore: TileStore,
) {

    val isEnabled: Boolean get() = tileStore.isEnabled

    val attribution: String get() = tileStore.attribution

    val maxZoom: Int get() = tileStore.maxZoom

    suspend operator fun invoke(id: TileId): ByteArray? = tileStore.tile(id)
}
