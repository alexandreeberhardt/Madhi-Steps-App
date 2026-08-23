package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.Coordinates
import kotlin.math.ln
import kotlin.math.min

/** Une position en pixels dans la zone de dessin, origine en haut à gauche. */
data class ScreenPoint(val x: Double, val y: Double)

/**
 * Ce que la carte montre : un centre géographique et un niveau de zoom.
 *
 * Toute la géométrie de la carte vit ici, hors de Compose, pour une raison
 * pratique : un cadrage faux est une régression silencieuse à l'œil et
 * évidente en test. Le rendu, lui, n'a plus qu'à peindre des pixels.
 */
data class MapViewport(
    val center: Coordinates,
    val zoom: Double,
) {

    private val worldSizePixels: Double get() = MapProjection.worldSizePixels(zoom)

    /** Échelle au centre, pour l'échelle graphique dessinée sur la carte. */
    val metersPerPixel: Double get() = MapProjection.metersPerPixel(center.latitude, zoom)

    fun toScreen(coordinates: Coordinates, widthPixels: Double, heightPixels: Double): ScreenPoint =
        toScreen(MapProjection.normalized(coordinates), widthPixels, heightPixels)

    /**
     * Surcharge à partir d'un point déjà projeté.
     *
     * La projection Mercator coûte un logarithme et une tangente par point.
     * Le tracé, lui, ne bouge pas : le rendu la fait une fois pour toutes et
     * ne repasse ici que la multiplication et l'addition, à chaque image de
     * déplacement. C'est ce qui rend le glissement fluide à deux mille points.
     */
    fun toScreen(point: NormalizedPoint, widthPixels: Double, heightPixels: Double): ScreenPoint {
        val world = worldSizePixels
        val centerPoint = MapProjection.normalized(center)
        return ScreenPoint(
            x = (point.x - centerPoint.x) * world + widthPixels / 2.0,
            y = (point.y - centerPoint.y) * world + heightPixels / 2.0,
        )
    }

    /**
     * Déplace la carte comme le doigt : le paysage suit la main, donc le
     * centre part dans l'autre sens.
     */
    fun pannedBy(deltaXPixels: Double, deltaYPixels: Double): MapViewport {
        val world = worldSizePixels
        val centerPoint = MapProjection.normalized(center)
        return copy(
            center = coordinatesAt(
                normalizedX = centerPoint.x - deltaXPixels / world,
                normalizedY = centerPoint.y - deltaYPixels / world,
            ),
        )
    }

    /**
     * Zoome en gardant fixe le point du monde situé sous les doigts. Sans
     * cela, un pincement fait fuir la carte sous la main.
     */
    fun zoomedBy(
        scale: Double,
        focus: ScreenPoint,
        widthPixels: Double,
        heightPixels: Double,
    ): MapViewport {
        if (scale <= 0.0) return this
        val newZoom = (zoom + ln(scale) / LN_2).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom == zoom) return this

        val offsetX = focus.x - widthPixels / 2.0
        val offsetY = focus.y - heightPixels / 2.0
        val centerPoint = MapProjection.normalized(center)
        val anchorX = centerPoint.x + offsetX / worldSizePixels
        val anchorY = centerPoint.y + offsetY / worldSizePixels

        val newWorld = MapProjection.worldSizePixels(newZoom)
        return MapViewport(
            center = coordinatesAt(
                normalizedX = anchorX - offsetX / newWorld,
                normalizedY = anchorY - offsetY / newWorld,
            ),
            zoom = newZoom,
        )
    }

    private fun coordinatesAt(normalizedX: Double, normalizedY: Double): Coordinates = Coordinates(
        // Le tracé ne franchit pas les pôles ; buter sur la limite de Mercator
        // évite un centre que la projection ne saurait plus inverser.
        latitude = MapProjection.latitudeAt(normalizedY.coerceIn(0.0, 1.0)),
        longitude = MapProjection.longitudeAt(wrapLongitudeAxis(normalizedX)),
    )

    companion object {
        const val MIN_ZOOM = 1.0
        const val MAX_ZOOM = 19.0

        /**
         * Zoom retenu quand le tracé tient en un point : environ 150 m de
         * large sur un téléphone, soit la rue et ses abords.
         */
        const val SINGLE_POINT_ZOOM = 16.0

        private const val LN_2 = 0.6931471805599453

        /**
         * Le cadrage qui montre tout le tracé, marge comprise.
         *
         * Renvoie `null` sans point à montrer : la carte affiche alors son
         * message d'attente plutôt qu'un cadrage arbitraire au large du golfe
         * de Guinée.
         */
        fun fitting(
            coordinates: List<Coordinates>,
            widthPixels: Double,
            heightPixels: Double,
            paddingPixels: Double = 0.0,
        ): MapViewport? {
            if (coordinates.isEmpty()) return null

            val points = coordinates.map(MapProjection::normalized)
            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }

            val center = Coordinates(
                latitude = MapProjection.latitudeAt((minY + maxY) / 2.0),
                longitude = MapProjection.longitudeAt((minX + maxX) / 2.0),
            )

            // Une zone de dessin dégénérée arrive une image avant la mesure du
            // Canvas : mieux vaut un zoom par défaut qu'une division par zéro.
            val usableWidth = widthPixels - 2.0 * paddingPixels
            val usableHeight = heightPixels - 2.0 * paddingPixels
            if (usableWidth <= 0.0 || usableHeight <= 0.0) {
                return MapViewport(center, SINGLE_POINT_ZOOM)
            }

            val zoom = min(
                zoomFitting(maxX - minX, usableWidth),
                zoomFitting(maxY - minY, usableHeight),
            )
            return MapViewport(center, zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
        }

        /**
         * Zoom auquel [span] (fraction du monde) occupe exactement
         * [availablePixels]. Un tracé sans étendue sur un axe ne contraint
         * rien : c'est l'autre axe qui décide.
         */
        private fun zoomFitting(span: Double, availablePixels: Double): Double {
            if (span <= 0.0) return SINGLE_POINT_ZOOM
            return ln(availablePixels / (MapProjection.TILE_SIZE_PIXELS * span)) / LN_2
        }

        /**
         * Ramène l'axe des longitudes dans [0, 1[. Le monde est cylindrique :
         * dépasser l'antiméridien réapparaît de l'autre côté, ce qui vaut
         * mieux que rester bloqué au bord.
         */
        private fun wrapLongitudeAxis(normalizedX: Double): Double {
            val wrapped = normalizedX % 1.0
            return if (wrapped < 0.0) wrapped + 1.0 else wrapped
        }
    }
}
