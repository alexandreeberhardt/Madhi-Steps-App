package com.madhi.tracker.presentation

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.SyncState
import com.madhi.tracker.domain.model.TrackPoint
import com.madhi.tracker.presentation.common.MadhiTrackerTheme
import com.madhi.tracker.presentation.map.MapCamera
import com.madhi.tracker.presentation.map.TrackMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Le cadrage de la carte, vu depuis l'écran.
 *
 * Ce défaut ne se voyait dans aucun test de `domain/` : la géométrie était
 * juste, c'est le câblage Compose qui gardait une copie périmée du cadrage
 * automatique. Le reproduire demande de composer la carte pour de vrai et de
 * la toucher.
 */
@RunWith(AndroidJUnit4::class)
// Le telephone cible tourne sous Android 13.
@Config(sdk = [33])
class TrackMapCameraTest {

    @get:Rule
    val compose = createComposeRule()

    private val camera = MapCamera()

    private val start = Instant.parse("2026-08-18T08:00:00Z")

    /** Deux points voisins : la carte se cadre sur une rue. */
    private val quartier = track(
        Coordinates(48.8566, 2.3522),
        Coordinates(48.8576, 2.3532),
    )

    /** Le voyage entier : la carte doit reculer de plusieurs niveaux de zoom. */
    private val voyage = track(
        Coordinates(48.8566, 2.3522),
        Coordinates(59.9139, 10.7522),
        Coordinates(71.1706, 25.7833),
    )

    @Test
    fun `recentrer puis toucher la carte ne ramene pas le cadrage d'avant`() {
        var points by mutableStateOf(quartier)

        compose.setContent {
            MadhiTrackerTheme {
                TrackMap(
                    points = points,
                    modifier = Modifier.size(360.dp, 640.dp).testTag(MAP),
                    camera = camera,
                )
            }
        }
        compose.waitForIdle()

        // La main prend le contrôle du cadrage.
        drag()
        assertTrue("le glissement aurait dû prendre la main", camera.isManual)

        // Le tracé s'allonge : le cadrage automatique recule de plusieurs
        // niveaux de zoom. C'est le voyage qui avance, un point à la fois.
        points = voyage
        compose.waitForIdle()

        val avantRecentrage = requireViewport()
        compose.onNodeWithText("Recentrer").performClick()
        compose.waitForIdle()

        val apresRecentrage = requireViewport()
        assertTrue(
            "le test ne prouve rien si le recentrage ne change pas l'échelle",
            avantRecentrage.zoom - apresRecentrage.zoom > 1.0,
        )

        // Le geste qui déclenchait le défaut : effleurer la carte juste après
        // avoir recentré. Le cadrage doit repartir de ce qui est affiché.
        drag()

        val apresToucher = requireViewport()
        assertEquals(apresRecentrage.zoom, apresToucher.zoom, ZOOM_TOLERANCE)
    }

    /**
     * Le fond est décoratif, et rien d'autre.
     *
     * C'est la règle qui se casserait le plus discrètement : il suffirait qu'un
     * jour le fond entre dans `MapViewport.fitting` pour que « Aujourd'hui »
     * recule jusqu'à montrer tout le voyage — la carte serait juste, et la
     * fonctionnalité perdue.
     */
    @Test
    fun `le fond ne deplace pas le cadrage de la periode`() {
        var fond by mutableStateOf(emptyList<Coordinates>())

        compose.setContent {
            MadhiTrackerTheme {
                TrackMap(
                    points = quartier,
                    backgroundTrack = fond,
                    modifier = Modifier.size(360.dp, 640.dp).testTag(MAP),
                    camera = camera,
                )
            }
        }
        compose.waitForIdle()
        val avantLeFond = requireViewport()

        // Le voyage entier apparaît derrière le quartier. La carte ne doit pas
        // reculer d'un pixel pour lui.
        fond = voyage.map { it.coordinates }
        compose.waitForIdle()

        assertEquals(avantLeFond, requireViewport())
    }

    @Test
    fun `le bouton recentrer n'apparait que quand la main a pris le controle`() {
        compose.setContent {
            MadhiTrackerTheme {
                TrackMap(
                    points = quartier,
                    modifier = Modifier.size(360.dp, 640.dp).testTag(MAP),
                    camera = camera,
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Recentrer").assertDoesNotExist()

        drag()
        compose.onNodeWithText("Recentrer").assertIsDisplayed()
        compose.onNodeWithText("Recentrer").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Recentrer").assertDoesNotExist()
        assertNotNull(camera.viewport)
    }

    private fun drag() {
        compose.onNodeWithTag(MAP).performTouchInput {
            // Assez long pour dépasser le seuil de déplacement de Compose,
            // assez court pour rester dans la carte.
            swipe(start = center, end = center + Offset(80f, 0f), durationMillis = 200)
        }
        compose.waitForIdle()
    }

    private fun requireViewport() =
        requireNotNull(camera.viewport) { "la carte n'a aucun cadrage" }

    private fun track(vararg coordinates: Coordinates): List<TrackPoint> =
        coordinates.mapIndexed { index, position ->
            TrackPoint(
                coordinates = position,
                recordedAt = start.plusSeconds(index * 300L),
                syncState = SyncState.SYNCED,
            )
        }

    private companion object {
        const val MAP = "carte"
        const val ZOOM_TOLERANCE = 0.01
    }
}
