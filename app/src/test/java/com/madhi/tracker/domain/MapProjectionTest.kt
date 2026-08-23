package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapProjectionTest {

    @Test
    fun `le meridien de Greenwich et l'equateur tombent au centre du monde`() {
        assertEquals(0.5, MapProjection.normalizedX(0.0), TOLERANCE)
        assertEquals(0.5, MapProjection.normalizedY(0.0), TOLERANCE)
    }

    @Test
    fun `les bords du monde sont zero et un`() {
        assertEquals(0.0, MapProjection.normalizedX(-180.0), TOLERANCE)
        assertEquals(1.0, MapProjection.normalizedX(180.0), TOLERANCE)
        assertEquals(0.0, MapProjection.normalizedY(MapProjection.MAX_LATITUDE), TOLERANCE)
        assertEquals(1.0, MapProjection.normalizedY(-MapProjection.MAX_LATITUDE), TOLERANCE)
    }

    @Test
    fun `au-dela de la limite de Mercator la projection reste dans le monde`() {
        // Le pole nord n'a pas de projection finie. Buter sur la limite vaut
        // mieux que renvoyer l'infini a la fonction de dessin.
        assertEquals(0.0, MapProjection.normalizedY(90.0), TOLERANCE)
        assertEquals(1.0, MapProjection.normalizedY(-90.0), TOLERANCE)
    }

    @Test
    fun `la projection est reversible sur tout le trajet du voyage`() {
        // De la France au Cap Nord : c'est la plage que l'application doit
        // savoir rendre sans deriver.
        listOf(43.3, 48.9, 55.7, 63.4, 71.17).forEach { latitude ->
            val roundTrip = MapProjection.latitudeAt(MapProjection.normalizedY(latitude))
            assertEquals(latitude, roundTrip, TOLERANCE)
        }
        listOf(-9.1, 0.0, 2.35, 25.78).forEach { longitude ->
            val roundTrip = MapProjection.longitudeAt(MapProjection.normalizedX(longitude))
            assertEquals(longitude, roundTrip, TOLERANCE)
        }
    }

    @Test
    fun `l'echelle se resserre quand on monte vers le nord`() {
        // Mercator etire les distances avec la latitude : au Cap Nord, un
        // pixel couvre trois fois moins de terrain qu'a l'equateur. Une
        // echelle qui l'ignorerait mentirait d'un facteur trois.
        val equator = MapProjection.metersPerPixel(latitude = 0.0, zoom = 12.0)
        val nordkapp = MapProjection.metersPerPixel(latitude = 71.17, zoom = 12.0)

        assertTrue(nordkapp < equator / 3.0)
    }

    @Test
    fun `doubler le zoom divise l'echelle par deux`() {
        val coarse = MapProjection.metersPerPixel(latitude = 48.9, zoom = 10.0)
        val fine = MapProjection.metersPerPixel(latitude = 48.9, zoom = 11.0)

        assertEquals(coarse / 2.0, fine, TOLERANCE)
    }

    @Test
    fun `le monde fait une tuile au zoom zero`() {
        assertEquals(MapProjection.TILE_SIZE_PIXELS, MapProjection.worldSizePixels(0.0), TOLERANCE)
        assertEquals(MapProjection.TILE_SIZE_PIXELS * 4, MapProjection.worldSizePixels(2.0), TOLERANCE)
    }

    @Test
    fun `projeter des coordonnees revient a projeter chaque axe`() {
        val paris = Coordinates(latitude = 48.8566, longitude = 2.3522)
        val projected = MapProjection.normalized(paris)

        assertEquals(MapProjection.normalizedX(paris.longitude), projected.x, TOLERANCE)
        assertEquals(MapProjection.normalizedY(paris.latitude), projected.y, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
