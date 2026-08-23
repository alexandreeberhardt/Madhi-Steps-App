package com.madhi.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapScaleBarTest {

    @Test
    fun `l'echelle ne depasse jamais la place disponible`() {
        val bar = MapScaleBar.fit(metersPerPixel = 2.0, maxWidthPixels = 400.0)!!

        assertTrue(bar.widthPixels <= 400.0)
    }

    @Test
    fun `l'echelle prend la plus grande distance ronde qui tient`() {
        // 400 px a 2 m/px valent 800 m : 500 m tient, 1 km non.
        val bar = MapScaleBar.fit(metersPerPixel = 2.0, maxWidthPixels = 400.0)!!

        assertEquals(500, bar.distanceMeters)
        assertEquals(250.0, bar.widthPixels, 1e-9)
    }

    @Test
    fun `l'echelle suit le zoom`() {
        val zoomedOut = MapScaleBar.fit(metersPerPixel = 100.0, maxWidthPixels = 400.0)!!
        val zoomedIn = MapScaleBar.fit(metersPerPixel = 1.0, maxWidthPixels = 400.0)!!

        assertTrue(zoomedOut.distanceMeters > zoomedIn.distanceMeters)
    }

    @Test
    fun `au zoom maximum sur une place minuscule il n'y a pas d'echelle`() {
        // Un demi-metre de large : meme le plus petit pas rond deborderait,
        // et mieux vaut ne rien dessiner qu'une echelle fausse.
        assertNull(MapScaleBar.fit(metersPerPixel = 0.1, maxWidthPixels = 5.0))
    }

    @Test
    fun `une carte pas encore mesuree n'a pas d'echelle`() {
        assertNull(MapScaleBar.fit(metersPerPixel = 0.0, maxWidthPixels = 400.0))
        assertNull(MapScaleBar.fit(metersPerPixel = 5.0, maxWidthPixels = 0.0))
    }

    @Test
    fun `l'echelle du continent reste lisible`() {
        // Trace complet France - Cap Nord sur un telephone : environ 3000 km
        // de haut, soit une echelle en centaines de kilometres.
        val bar = MapScaleBar.fit(metersPerPixel = 2_000.0, maxWidthPixels = 400.0)

        assertNotNull(bar)
        assertEquals(500_000, bar!!.distanceMeters)
    }
}
