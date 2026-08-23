package com.madhi.tracker.domain

import kotlin.math.floor
import kotlin.math.pow

/** Une tuile du découpage standard XYZ : niveau de zoom, colonne, ligne. */
data class TileId(val zoom: Int, val x: Int, val y: Int)

/** Une tuile et l'endroit exact où la poser dans la zone de dessin. */
data class PlacedTile(
    val id: TileId,
    val left: Double,
    val top: Double,
    val size: Double,
)

/**
 * Quelles tuiles couvrent la vue, et où les poser.
 *
 * Le zoom d'un viewport est continu — un pincement s'arrête où il veut — alors
 * que les tuiles n'existent qu'à des niveaux entiers. On prend le niveau
 * inférieur et on agrandit les tuiles pour combler l'écart : une tuile un peu
 * floue vaut mieux qu'une carte qui saute d'un niveau à l'autre pendant le
 * geste.
 */
object TileGrid {

    /**
     * Au-delà, les serveurs de tuiles courants ne fournissent plus rien. On
     * continue d'agrandir le dernier niveau disponible plutôt que de demander
     * des tuiles qui répondront 404.
     */
    const val MAX_TILE_ZOOM = 19

    /**
     * Plafond de sécurité. Une zone de dessin absurde ou un zoom incohérent
     * ne doit pas déclencher des milliers de requêtes réseau.
     */
    private const val MAX_TILES = 64

    fun visible(
        viewport: MapViewport,
        widthPixels: Double,
        heightPixels: Double,
    ): List<PlacedTile> {
        if (widthPixels <= 0.0 || heightPixels <= 0.0) return emptyList()

        val zoom = floor(viewport.zoom).toInt().coerceIn(0, MAX_TILE_ZOOM)
        val tilesPerAxis = 2.0.pow(zoom)
        val world = MapProjection.worldSizePixels(viewport.zoom)
        val tileSize = world / tilesPerAxis
        val center = MapProjection.normalized(viewport.center)

        // Colonne et ligne de la tuile qui touche chaque bord de l'écran.
        val firstColumn = floor((center.x - widthPixels / 2.0 / world) * tilesPerAxis).toInt()
        val lastColumn = floor((center.x + widthPixels / 2.0 / world) * tilesPerAxis).toInt()
        val firstRow = floor((center.y - heightPixels / 2.0 / world) * tilesPerAxis).toInt()
        val lastRow = floor((center.y + heightPixels / 2.0 / world) * tilesPerAxis).toInt()

        val axisCount = tilesPerAxis.toInt()
        val placed = mutableListOf<PlacedTile>()

        for (row in firstRow..lastRow) {
            // Le monde ne se répète pas verticalement : au-delà des pôles il
            // n'y a pas de tuile, et il ne faut surtout pas en inventer une.
            if (row < 0 || row >= axisCount) continue

            for (column in firstColumn..lastColumn) {
                if (placed.size >= MAX_TILES) return placed

                placed += PlacedTile(
                    // Le monde est cylindrique : passer l'antiméridien
                    // ramène à la colonne d'en face.
                    id = TileId(zoom = zoom, x = wrapColumn(column, axisCount), y = row),
                    // La position, elle, garde la colonne non repliée, sinon
                    // la tuile irait se poser à l'autre bout de l'écran.
                    left = (column / tilesPerAxis - center.x) * world + widthPixels / 2.0,
                    top = (row / tilesPerAxis - center.y) * world + heightPixels / 2.0,
                    size = tileSize,
                )
            }
        }
        return placed
    }

    private fun wrapColumn(column: Int, axisCount: Int): Int =
        ((column % axisCount) + axisCount) % axisCount
}
