package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewportTest {

    private val paris = Coordinates(latitude = 48.8566, longitude = 2.3522)
    private val lyon = Coordinates(latitude = 45.7640, longitude = 4.8357)
    private val nordkapp = Coordinates(latitude = 71.1706, longitude = 25.7833)

    private val viewport = MapViewport(center = paris, zoom = 12.0)

    @Test
    fun `le centre de la carte tombe au centre de l'ecran`() {
        val screen = viewport.toScreen(paris, WIDTH, HEIGHT)

        assertEquals(WIDTH / 2.0, screen.x, TOLERANCE)
        assertEquals(HEIGHT / 2.0, screen.y, TOLERANCE)
    }

    @Test
    fun `l'est va a droite et le nord va en haut`() {
        val east = viewport.toScreen(paris.copy(longitude = paris.longitude + 0.1), WIDTH, HEIGHT)
        val north = viewport.toScreen(paris.copy(latitude = paris.latitude + 0.1), WIDTH, HEIGHT)

        assertTrue(east.x > WIDTH / 2.0)
        assertTrue(north.y < HEIGHT / 2.0)
    }

    @Test
    fun `projeter un point deja projete donne le meme pixel`() {
        val direct = viewport.toScreen(lyon, WIDTH, HEIGHT)
        val preprojected = viewport.toScreen(MapProjection.normalized(lyon), WIDTH, HEIGHT)

        assertEquals(direct, preprojected)
    }

    @Test
    fun `glisser le doigt deplace le paysage avec la main`() {
        // Tirer vers la droite doit amener a l'ecran ce qui etait a l'ouest.
        val panned = viewport.pannedBy(deltaXPixels = 100.0, deltaYPixels = 0.0)

        assertTrue(panned.center.longitude < viewport.center.longitude)
    }

    @Test
    fun `un glissement suivi de son inverse revient au point de depart`() {
        val roundTrip = viewport.pannedBy(120.0, -80.0).pannedBy(-120.0, 80.0)

        assertEquals(viewport.center.latitude, roundTrip.center.latitude, 1e-9)
        assertEquals(viewport.center.longitude, roundTrip.center.longitude, 1e-9)
    }

    @Test
    fun `le zoom garde immobile le point sous les doigts`() {
        // C'est toute la difficulte du pincement : sans ancrage, la carte fuit
        // sous la main.
        val focus = ScreenPoint(x = 300.0, y = 200.0)
        val before = geographicPointUnder(viewport, focus)

        val zoomed = viewport.zoomedBy(scale = 2.0, focus = focus, widthPixels = WIDTH, heightPixels = HEIGHT)
        val after = geographicPointUnder(zoomed, focus)

        assertEquals(before.latitude, after.latitude, 1e-6)
        assertEquals(before.longitude, after.longitude, 1e-6)
    }

    @Test
    fun `doubler l'echelle ajoute un niveau de zoom`() {
        val zoomed = viewport.zoomedBy(2.0, ScreenPoint(WIDTH / 2, HEIGHT / 2), WIDTH, HEIGHT)

        assertEquals(13.0, zoomed.zoom, 1e-9)
    }

    @Test
    fun `le zoom reste dans ses bornes`() {
        val tooFar = viewport.zoomedBy(1e-9, ScreenPoint(WIDTH / 2, HEIGHT / 2), WIDTH, HEIGHT)
        val tooClose = viewport.zoomedBy(1e9, ScreenPoint(WIDTH / 2, HEIGHT / 2), WIDTH, HEIGHT)

        assertEquals(MapViewport.MIN_ZOOM, tooFar.zoom, 1e-9)
        assertEquals(MapViewport.MAX_ZOOM, tooClose.zoom, 1e-9)
    }

    @Test
    fun `une echelle nulle ou negative ne change rien`() {
        assertEquals(viewport, viewport.zoomedBy(0.0, ScreenPoint(0.0, 0.0), WIDTH, HEIGHT))
        assertEquals(viewport, viewport.zoomedBy(-1.0, ScreenPoint(0.0, 0.0), WIDTH, HEIGHT))
    }

    @Test
    fun `sans point a montrer il n'y a pas de cadrage`() {
        // Le cadrage par defaut d'une carte vide tomberait au large du golfe
        // de Guinee : mieux vaut que l'ecran dise qu'il n'a rien.
        assertNull(MapViewport.fitting(emptyList(), WIDTH, HEIGHT))
    }

    @Test
    fun `le cadrage automatique fait tenir tout le trace a l'ecran`() {
        val track = listOf(paris, lyon, nordkapp)
        val fitted = MapViewport.fitting(track, WIDTH, HEIGHT, MapInsets.uniform(PADDING))!!

        track.forEach { point ->
            val screen = fitted.toScreen(point, WIDTH, HEIGHT)
            assertTrue("$point sort a gauche", screen.x >= PADDING - 1.0)
            assertTrue("$point sort a droite", screen.x <= WIDTH - PADDING + 1.0)
            assertTrue("$point sort en haut", screen.y >= PADDING - 1.0)
            assertTrue("$point sort en bas", screen.y <= HEIGHT - PADDING + 1.0)
        }
    }

    @Test
    fun `le cadrage automatique centre le trace`() {
        val fitted = MapViewport.fitting(listOf(paris, nordkapp), WIDTH, HEIGHT, MapInsets.uniform(PADDING))!!

        val first = fitted.toScreen(paris, WIDTH, HEIGHT)
        val second = fitted.toScreen(nordkapp, WIDTH, HEIGHT)

        assertEquals(WIDTH / 2.0, (first.x + second.x) / 2.0, 1.0)
        assertEquals(HEIGHT / 2.0, (first.y + second.y) / 2.0, 1.0)
    }

    @Test
    fun `un trace reduit a un point ne zoome pas a l'infini`() {
        // Sans garde-fou, une etendue nulle demanderait un zoom infini au
        // premier point du voyage.
        val fitted = MapViewport.fitting(listOf(paris), WIDTH, HEIGHT, MapInsets.uniform(PADDING))!!

        assertEquals(MapViewport.SINGLE_POINT_ZOOM, fitted.zoom, 1e-9)
        assertEquals(paris.latitude, fitted.center.latitude, 1e-6)
        assertEquals(paris.longitude, fitted.center.longitude, 1e-6)
    }

    @Test
    fun `une zone de dessin pas encore mesuree donne un cadrage utilisable`() {
        // Compose rend une premiere image avant de connaitre la taille.
        val fitted = MapViewport.fitting(listOf(paris, lyon), widthPixels = 0.0, heightPixels = 0.0)

        assertNotNull(fitted)
        assertEquals(MapViewport.SINGLE_POINT_ZOOM, fitted!!.zoom, 1e-9)
    }

    @Test
    fun `une marge plus grande que l'ecran ne fait pas exploser le cadrage`() {
        val fitted = MapViewport.fitting(listOf(paris, lyon), WIDTH, HEIGHT, MapInsets.uniform(WIDTH))

        assertNotNull(fitted)
        assertTrue(fitted!!.zoom in MapViewport.MIN_ZOOM..MapViewport.MAX_ZOOM)
    }

    @Test
    fun `le trace evite la legende en haut et l'echelle en bas`() {
        // Le defaut constate sur le OnePlus : avec une marge uniforme, le
        // marqueur de position actuelle se glissait sous la legende.
        val legend = 160.0
        val scaleBar = 200.0
        val insets = MapInsets(left = 40.0, top = legend, right = 40.0, bottom = scaleBar)
        val track = listOf(paris, lyon, nordkapp)

        val fitted = MapViewport.fitting(track, WIDTH, HEIGHT, insets)!!

        track.forEach { point ->
            val screen = fitted.toScreen(point, WIDTH, HEIGHT)
            assertTrue("$point passe sous la legende", screen.y >= legend - 1.0)
            assertTrue("$point passe sous l'echelle", screen.y <= HEIGHT - scaleBar + 1.0)
            assertTrue("$point sort a gauche", screen.x >= 40.0 - 1.0)
            assertTrue("$point sort a droite", screen.x <= WIDTH - 40.0 + 1.0)
        }
    }

    @Test
    fun `des marges inegales centrent le trace dans la zone libre`() {
        val insets = MapInsets(top = 400.0)
        val fitted = MapViewport.fitting(listOf(paris, lyon), WIDTH, HEIGHT, insets)!!

        val first = fitted.toScreen(paris, WIDTH, HEIGHT)
        val second = fitted.toScreen(lyon, WIDTH, HEIGHT)

        // Le centre vise est celui de la zone libre, pas celui de l'ecran.
        assertEquals(400.0 + (HEIGHT - 400.0) / 2.0, (first.y + second.y) / 2.0, 1.0)
    }

    @Test
    fun `l'echelle affichee est celle du centre de la carte`() {
        assertEquals(
            MapProjection.metersPerPixel(paris.latitude, 12.0),
            viewport.metersPerPixel,
            1e-9,
        )
    }

    /** Retrouve le point du monde affiche a une position d'ecran donnee. */
    private fun geographicPointUnder(viewport: MapViewport, screen: ScreenPoint): Coordinates {
        val world = MapProjection.worldSizePixels(viewport.zoom)
        val center = MapProjection.normalized(viewport.center)
        return Coordinates(
            latitude = MapProjection.latitudeAt(center.y + (screen.y - HEIGHT / 2.0) / world),
            longitude = MapProjection.longitudeAt(center.x + (screen.x - WIDTH / 2.0) / world),
        )
    }

    private companion object {
        const val WIDTH = 1080.0
        const val HEIGHT = 1600.0
        const val PADDING = 48.0
        const val TOLERANCE = 1e-9
    }
}
