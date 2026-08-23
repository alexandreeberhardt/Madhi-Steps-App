package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.Coordinates
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Projection Web Mercator, celle de toutes les tuiles cartographiques.
 *
 * La carte embarquée dessine le tracé sans fond de carte (ADR-006). Utiliser
 * malgré tout la projection des tuiles n'est pas gratuit : le jour où le VPS
 * sert un cache de tuiles, il suffira de peindre ces tuiles sous le tracé,
 * sans toucher à une seule ligne de géométrie.
 *
 * Les coordonnées projetées sont normalisées sur [0, 1] : indépendantes du
 * zoom, donc comparables et interpolables. La conversion en pixels est
 * l'affaire de [MapViewport].
 */
object MapProjection {

    /**
     * Mercator diverge aux pôles. La limite conventionnelle des tuiles rend
     * le monde carré ; au-delà, la projection renverrait l'infini.
     */
    const val MAX_LATITUDE = 85.05112878

    /** Taille d'une tuile, en pixels. Convention universelle du Web Mercator. */
    const val TILE_SIZE_PIXELS = 256.0

    /** Rayon terrestre utilisé par Web Mercator, en mètres. */
    private const val EARTH_RADIUS_METERS = 6_378_137.0

    fun normalizedX(longitude: Double): Double = (longitude + 180.0) / 360.0

    fun normalizedY(latitude: Double): Double {
        val clamped = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val radians = clamped * PI / 180.0
        return (1.0 - ln(tan(radians) + 1.0 / cos(radians)) / PI) / 2.0
    }

    fun longitudeAt(normalizedX: Double): Double = normalizedX * 360.0 - 180.0

    fun latitudeAt(normalizedY: Double): Double {
        val radians = atan(sinh(PI * (1.0 - 2.0 * normalizedY)))
        return radians * 180.0 / PI
    }

    /** Côté du monde, en pixels, à ce niveau de zoom. */
    fun worldSizePixels(zoom: Double): Double = TILE_SIZE_PIXELS * Math.pow(2.0, zoom)

    /**
     * Échelle réelle au centre de l'écran. Mercator étire les distances avec
     * la latitude : sans le cosinus, l'échelle affichée au Cap Nord serait
     * fausse d'un facteur trois.
     */
    fun metersPerPixel(latitude: Double, zoom: Double): Double {
        val clamped = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val circumference = 2.0 * PI * EARTH_RADIUS_METERS
        return circumference * cos(clamped * PI / 180.0) / worldSizePixels(zoom)
    }

    fun normalized(coordinates: Coordinates): NormalizedPoint = NormalizedPoint(
        x = normalizedX(coordinates.longitude),
        y = normalizedY(coordinates.latitude),
    )
}

/** Un point projeté, ramené sur le carré unité du Web Mercator. */
data class NormalizedPoint(val x: Double, val y: Double)
