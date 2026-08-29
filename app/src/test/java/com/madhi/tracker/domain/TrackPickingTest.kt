package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackPickingTest {

    private val paris = Coordinates(latitude = 48.8566, longitude = 2.3522)
    private val viewport = MapViewport(center = paris, zoom = 14.0)

    private val trace = listOf(
        paris,
        paris.copy(longitude = paris.longitude + 0.004),
        paris.copy(longitude = paris.longitude + 0.008),
    ).map(MapProjection::normalized)

    @Test
    fun `un appui sur un point le designe`() {
        val cible = viewport.toScreen(trace[1], WIDTH, HEIGHT)

        val index = TrackPicking.nearest(trace, cible, viewport, WIDTH, HEIGHT, TOLERANCE)

        assertEquals(1, index)
    }

    @Test
    fun `un appui un peu a cote designe quand meme le point vise`() {
        // Un point dessine fait six pixels de large, un doigt en couvre une
        // cinquantaine : viser au pixel serait injouable.
        val cible = viewport.toScreen(trace[0], WIDTH, HEIGHT)
        val approximatif = ScreenPoint(cible.x + 12.0, cible.y - 9.0)

        assertEquals(0, TrackPicking.nearest(trace, approximatif, viewport, WIDTH, HEIGHT, TOLERANCE))
    }

    @Test
    fun `un appui loin de tout ne designe rien`() {
        // C'est ce qui permet de fermer la bulle en touchant la carte a cote.
        val ailleurs = ScreenPoint(WIDTH - 10.0, 10.0)

        assertNull(TrackPicking.nearest(trace, ailleurs, viewport, WIDTH, HEIGHT, TOLERANCE))
    }

    @Test
    fun `entre deux points, le plus proche gagne`() {
        val premier = viewport.toScreen(trace[0], WIDTH, HEIGHT)
        val second = viewport.toScreen(trace[1], WIDTH, HEIGHT)
        val presqueLeSecond = ScreenPoint((premier.x + 3 * second.x) / 4.0, second.y)

        assertEquals(1, TrackPicking.nearest(trace, presqueLeSecond, viewport, WIDTH, HEIGHT, LARGE_TOLERANCE))
    }

    @Test
    fun `a distance egale, le plus recent l'emporte`() {
        // C'est la fin du trace qu'on regarde, et c'est elle qui est dessinee
        // par-dessus : designer le point cache dessous surprendrait.
        val superposes = listOf(paris, paris).map(MapProjection::normalized)
        val cible = viewport.toScreen(superposes[0], WIDTH, HEIGHT)

        assertEquals(1, TrackPicking.nearest(superposes, cible, viewport, WIDTH, HEIGHT, TOLERANCE))
    }

    @Test
    fun `un trace vide ne designe rien`() {
        assertNull(TrackPicking.nearest(emptyList(), ScreenPoint(0.0, 0.0), viewport, WIDTH, HEIGHT, TOLERANCE))
    }

    private companion object {
        const val WIDTH = 1080.0
        const val HEIGHT = 1920.0
        const val TOLERANCE = 60.0
        const val LARGE_TOLERANCE = 400.0
    }
}
