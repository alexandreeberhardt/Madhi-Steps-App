package com.madhi.tracker.presentation

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.madhi.tracker.domain.ScreenPoint
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.SyncState
import com.madhi.tracker.domain.model.TrackPoint
import com.madhi.tracker.presentation.common.MadhiTrackerTheme
import com.madhi.tracker.presentation.map.MapCamera
import com.madhi.tracker.presentation.map.TrackMap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * La bulle d'un point du tracé, vue depuis l'écran.
 *
 * Ce que le test verifie n'est pas le dessin mais la chaine : un appui tombe
 * sur le bon point, la bulle s'ouvre, l'adresse arrive, et un appui a cote
 * referme.
 */
@RunWith(AndroidJUnit4::class)
// Le telephone cible tourne sous Android 13.
@Config(sdk = [33])
class TrackMapBubbleTest {

    @get:Rule
    val compose = createComposeRule()

    private val camera = MapCamera()

    private val depart = Instant.parse("2026-08-26T06:00:00Z")
    private val maintenant = Instant.parse("2026-08-26T09:30:00Z")

    private val trace = listOf(
        Coordinates(48.8566, 2.3522),
        Coordinates(48.8600, 2.3600),
        Coordinates(48.8650, 2.3700),
    ).mapIndexed { index, position ->
        TrackPoint(
            coordinates = position,
            recordedAt = depart.plusSeconds(index * 300L),
            syncState = SyncState.SYNCED,
        )
    }

    private val adresses = mapOf(
        trace[1].coordinates to "12 Rue de la Paix, Paris, France",
    )

    @Test
    fun `toucher un point ouvre sa bulle avec son adresse`() {
        afficher()

        toucher(trace[1])

        compose.onNodeWithText("12 Rue de la Paix, Paris, France").assertIsDisplayed()
        compose.onNodeWithText("48.86000, 2.36000").assertIsDisplayed()
    }

    @Test
    fun `un point sans adresse connue garde une bulle utile`() {
        // Le cas normal du voyage : hors reseau, personne ne sait dire
        // l'adresse, et la bulle doit rester lisible sans elle.
        afficher()

        toucher(trace[2])

        compose.onNodeWithText("Adresse indisponible hors ligne.").assertIsDisplayed()
        compose.onNodeWithText("48.86500, 2.37000").assertIsDisplayed()
    }

    @Test
    fun `toucher la carte a cote referme la bulle`() {
        afficher()
        toucher(trace[1])
        compose.onNodeWithText("12 Rue de la Paix, Paris, France").assertIsDisplayed()

        // Un coin de la carte, loin de tout point du trace.
        compose.onNodeWithTag(MAP).performTouchInput { click(Offset(4f, 4f)) }
        compose.waitForIdle()

        compose.onNodeWithText("12 Rue de la Paix, Paris, France").assertDoesNotExist()
    }

    private fun afficher() {
        compose.setContent {
            MadhiTrackerTheme {
                TrackMap(
                    points = trace,
                    modifier = Modifier.size(360.dp, 640.dp).testTag(MAP),
                    camera = camera,
                    loadAddress = { adresses[it] },
                    now = maintenant,
                )
            }
        }
        compose.waitForIdle()
    }

    /** Appuie a l'endroit exact ou la carte a dessine ce point. */
    private fun toucher(point: TrackPoint) {
        val taille = compose.onNodeWithTag(MAP).fetchSemanticsNode().size
        val cible: ScreenPoint = requireNotNull(camera.viewport).toScreen(
            coordinates = point.coordinates,
            widthPixels = taille.width.toDouble(),
            heightPixels = taille.height.toDouble(),
        )

        compose.onNodeWithTag(MAP).performTouchInput {
            click(Offset(cible.x.toFloat(), cible.y.toFloat()))
        }
        compose.waitForIdle()
    }

    private companion object {
        const val MAP = "carte"
    }
}
