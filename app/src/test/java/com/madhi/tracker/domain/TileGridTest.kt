package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileGridTest {

    private val paris = Coordinates(latitude = 48.8566, longitude = 2.3522)

    @Test
    fun `une zone de dessin pas encore mesuree ne demande aucune tuile`() {
        // Une image avant la mesure du Canvas. Demander des tuiles la
        // declencherait des requetes reseau pour un cadrage qui n'existe pas.
        val viewport = MapViewport(paris, 12.0)

        assertTrue(TileGrid.visible(viewport, 0.0, 0.0).isEmpty())
        assertTrue(TileGrid.visible(viewport, WIDTH, 0.0).isEmpty())
    }

    @Test
    fun `les tuiles couvrent toute la zone de dessin`() {
        val viewport = MapViewport(paris, 12.0)

        val tiles = TileGrid.visible(viewport, WIDTH, HEIGHT)

        assertTrue(tiles.isNotEmpty())
        assertTrue("aucune tuile ne couvre le coin haut gauche", covers(tiles, 0.0, 0.0))
        assertTrue("aucune tuile ne couvre le coin bas droit", covers(tiles, WIDTH - 1, HEIGHT - 1))
        assertTrue("aucune tuile ne couvre le centre", covers(tiles, WIDTH / 2, HEIGHT / 2))
    }

    @Test
    fun `le zoom entier retenu est celui juste en dessous du zoom courant`() {
        // Les tuiles n'existent qu'a des niveaux entiers ; un pincement
        // s'arrete ou il veut. On agrandit le niveau inferieur.
        val tiles = TileGrid.visible(MapViewport(paris, 12.7), WIDTH, HEIGHT)

        assertTrue(tiles.all { it.id.zoom == 12 })
        // Agrandies, donc plus larges que les 256 px nominaux.
        assertTrue(tiles.first().size > MapProjection.TILE_SIZE_PIXELS)
    }

    @Test
    fun `une tuile se pose la ou le viewport projette son coin`() {
        // C'est la condition pour que le trace tombe au bon endroit sur le
        // fond : les deux doivent partager exactement la meme projection.
        val viewport = MapViewport(paris, 11.0)
        val tile = TileGrid.visible(viewport, WIDTH, HEIGHT).first()

        val tilesPerAxis = 1 shl tile.id.zoom
        val corner = viewport.toScreen(
            point = NormalizedPoint(
                x = tile.id.x.toDouble() / tilesPerAxis,
                y = tile.id.y.toDouble() / tilesPerAxis,
            ),
            widthPixels = WIDTH,
            heightPixels = HEIGHT,
        )

        assertEquals(corner.x, tile.left, 1e-6)
        assertEquals(corner.y, tile.top, 1e-6)
    }

    @Test
    fun `au zoom le plus large le monde se repete horizontalement, pas verticalement`() {
        // A ce niveau, le monde entier est plus petit que l'ecran. Il se
        // repete de gauche a droite, comme sur toutes les cartes glissantes,
        // mais il n'y a rien au-dessus du pole.
        val tiles = TileGrid.visible(MapViewport(paris, 0.0), WIDTH, HEIGHT)

        assertTrue(tiles.all { it.id == TileId(0, 0, 0) })
        assertTrue("le monde doit se repeter en largeur", tiles.size > 1)
        // Une seule ligne, donc une seule ordonnee de pose.
        assertEquals(1, tiles.map { it.top }.distinct().size)
    }

    @Test
    fun `les colonnes se replient a l'antimeridien`() {
        // Le monde est cylindrique : depasser le 180e degre revient de
        // l'autre cote, et surtout pas sur une colonne inexistante.
        val nearDateLine = Coordinates(latitude = 0.0, longitude = 179.9)

        val tiles = TileGrid.visible(MapViewport(nearDateLine, 3.0), WIDTH, HEIGHT)

        val tilesPerAxis = 1 shl 3
        assertTrue(tiles.all { it.id.x in 0 until tilesPerAxis })
        assertTrue("le repli doit ramener a la colonne zero", tiles.any { it.id.x == 0 })
    }

    @Test
    fun `il n'y a pas de tuile au-dela des poles`() {
        // Verticalement le monde ne se repete pas : inventer une ligne
        // demanderait une tuile que le serveur n'a pas.
        val nearPole = Coordinates(latitude = 84.9, longitude = 0.0)

        val tiles = TileGrid.visible(MapViewport(nearPole, 2.0), WIDTH, HEIGHT)

        val tilesPerAxis = 1 shl 2
        assertTrue(tiles.all { it.id.y in 0 until tilesPerAxis })
    }

    @Test
    fun `au-dela du dernier niveau servi on agrandit plutot que de demander le vide`() {
        // Un fond auto-heberge s'arrete bas. Demander un z16 a une source qui
        // s'arrete a z8 ne rapporterait que des 404 et un fond disparu.
        val tiles = TileGrid.visible(MapViewport(paris, 16.0), WIDTH, HEIGHT, maxTileZoom = 8)

        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.all { it.id.zoom == 8 })
        // Agrandies d'un facteur 2^8 : le fond devient flou, mais il est la.
        assertTrue(tiles.first().size > MapProjection.TILE_SIZE_PIXELS * 100)
    }

    @Test
    fun `un ecran dense descend d'un niveau de zoom par doublement de densite`() {
        // Une tuile fait 256 pixels logiques. Posee telle quelle sur un ecran
        // a 400 points par pouce, elle est grande comme un timbre et ses noms
        // de villes sont illisibles.
        val viewport = MapViewport(paris, 12.0)

        assertEquals(12, TileGrid.visible(viewport, WIDTH, HEIGHT, pixelDensity = 1.0).first().id.zoom)
        assertEquals(11, TileGrid.visible(viewport, WIDTH, HEIGHT, pixelDensity = 2.0).first().id.zoom)
        assertEquals(10, TileGrid.visible(viewport, WIDTH, HEIGHT, pixelDensity = 4.0).first().id.zoom)
    }

    @Test
    fun `une tuile couvre au moins la densite de l'ecran`() {
        // Le contrat utile : une tuile doit occuper au moins 256 x densite
        // pixels d'ecran, pour que ses etiquettes retrouvent la taille
        // physique pour laquelle elles ont ete dessinees. Les niveaux de zoom
        // etant entiers, on depasse forcement un peu.
        val viewport = MapViewport(paris, 12.0)
        val densite = 3.0

        val normale = TileGrid.visible(viewport, WIDTH, HEIGHT, pixelDensity = 1.0).first()
        val dense = TileGrid.visible(viewport, WIDTH, HEIGHT, pixelDensity = densite).first()

        assertTrue(
            "tuile de ${dense.size} px pour une densite de $densite",
            dense.size >= normale.size * densite,
        )
        assertTrue(dense.size <= normale.size * densite * 2)
    }

    @Test
    fun `un ecran dense demande moins de tuiles pour la meme surface`() {
        val viewport = MapViewport(paris, 12.0)

        val normale = TileGrid.visible(viewport, WIDTH, HEIGHT, pixelDensity = 1.0)
        val dense = TileGrid.visible(viewport, WIDTH, HEIGHT, pixelDensity = 3.0)

        assertTrue("${dense.size} contre ${normale.size}", dense.size < normale.size)
    }

    @Test
    fun `une densite absurde ne fait pas sortir du monde`() {
        val viewport = MapViewport(paris, 12.0)

        val tiles = TileGrid.visible(viewport, WIDTH, HEIGHT, pixelDensity = 100_000.0)

        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.all { it.id.zoom >= 0 })
    }

    @Test
    fun `le nombre de tuiles reste plafonne`() {
        // Un cadrage aberrant ne doit pas declencher des milliers de requetes.
        val tiles = TileGrid.visible(MapViewport(paris, 12.0), 20_000.0, 20_000.0)

        assertTrue("${tiles.size} tuiles demandees", tiles.size <= 64)
    }

    private fun covers(tiles: List<PlacedTile>, x: Double, y: Double): Boolean = tiles.any {
        x >= it.left && x < it.left + it.size && y >= it.top && y < it.top + it.size
    }

    private companion object {
        const val WIDTH = 1080.0
        const val HEIGHT = 1600.0
    }
}
