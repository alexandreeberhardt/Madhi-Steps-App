package com.madhi.tracker.presentation.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madhi.tracker.domain.MapInsets
import com.madhi.tracker.domain.MapProjection
import com.madhi.tracker.domain.MapScaleBar
import com.madhi.tracker.domain.MapViewport
import com.madhi.tracker.domain.NormalizedPoint
import com.madhi.tracker.domain.ScreenPoint
import com.madhi.tracker.domain.model.SyncState
import com.madhi.tracker.domain.model.TrackPoint
import com.madhi.tracker.presentation.common.TrackColors

/**
 * La carte de l'écran d'accueil : le tracé récent, sans fond de carte.
 *
 * Il n'y a volontairement aucune tuile (ADR-006). Hors ligne — le mode normal
 * du voyage — un fond de carte non pré-téléchargé serait gris, et servir des
 * tuiles à une application mobile demanderait un cache sur le VPS qui
 * n'existe pas. Ce qui vient de Room, lui, s'affiche toujours : le tracé, la
 * position actuelle, et l'échelle qui dit à quelle distance on regarde.
 *
 * La géométrie vit dans `domain/Map*` et se teste sans Android ; ici il n'y a
 * que des pixels et des gestes.
 */
@Composable
fun TrackMap(
    points: List<TrackPoint>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val inkColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerRingColor = MaterialTheme.colorScheme.surface

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // La hauteur reelle de la legende, mesuree plutot que devinee : elle
    // change avec la taille de police du systeme, et le cadrage doit suivre.
    var legendHeight by remember { mutableStateOf(0) }

    // Un cadrage choisi à la main survit à l'arrivée d'une nouvelle position ;
    // sinon la carte sauterait sous les doigts toutes les cinq minutes.
    var manualViewport by remember { mutableStateOf<MapViewport?>(null) }

    // La projection ne dépend que des points : la refaire à chaque image de
    // glissement gaspillerait deux mille logarithmes pour rien.
    val projected = remember(points) { points.map { MapProjection.normalized(it.coordinates) } }

    val insets = with(density) {
        val margin = FIT_MARGIN.toPx().toDouble()
        MapInsets(
            left = margin,
            right = margin,
            top = margin + legendHeight,
            bottom = margin + SCALE_BAND.toPx().toDouble(),
        )
    }
    val automaticViewport = remember(points, canvasSize, insets) {
        MapViewport.fitting(
            coordinates = points.map { it.coordinates },
            widthPixels = canvasSize.width.toDouble(),
            heightPixels = canvasSize.height.toDouble(),
            insets = insets,
        )
    }
    val viewport = manualViewport ?: automaticViewport

    Box(
        modifier = modifier
            .background(backgroundColor)
            .onSizeChanged { canvasSize = it },
    ) {
        if (viewport == null) {
            Text(
                text = "Aucune position enregistrée pour l'instant.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            return@Box
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val current = manualViewport ?: automaticViewport ?: return@detectTransformGestures
                        val width = size.width.toDouble()
                        val height = size.height.toDouble()
                        manualViewport = current
                            .pannedBy(pan.x.toDouble(), pan.y.toDouble())
                            .zoomedBy(
                                scale = zoom.toDouble(),
                                focus = ScreenPoint(
                                    x = centroid.x.toDouble(),
                                    y = centroid.y.toDouble(),
                                ),
                                widthPixels = width,
                                heightPixels = height,
                            )
                    }
                },
        ) {
            val screen = projected.map { it.toOffset(viewport, size.width, size.height) }
            drawTrack(points, screen, strokeWidth = TRACK_STROKE.toPx())
            drawPointDots(points, screen, radius = POINT_RADIUS.toPx())
            screen.lastOrNull()?.let { last ->
                drawCurrentPosition(
                    center = last,
                    color = points.last().color(),
                    ringColor = markerRingColor,
                    radius = MARKER_RADIUS.toPx(),
                    ringWidth = MARKER_RING.toPx(),
                )
            }
            drawScaleBar(
                metersPerPixel = viewport.metersPerPixel,
                inkColor = inkColor,
                textMeasurer = textMeasurer,
                marginPixels = SCALE_MARGIN.toPx(),
                maxWidthPixels = size.width * SCALE_MAX_WIDTH_FRACTION,
            )
        }

        Legend(
            hasPending = points.any { it.syncState == SyncState.PENDING },
            modifier = Modifier
                .align(Alignment.TopStart)
                .onSizeChanged { legendHeight = it.height }
                .padding(LEGEND_MARGIN),
        )

        if (manualViewport != null) {
            FilledTonalButton(
                onClick = { manualViewport = null },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Text("Recentrer") }
        }
    }
}

/**
 * Le tracé, découpé en tronçons d'un même état de synchronisation.
 *
 * Un tronçon par segment coûterait deux mille appels de dessin par image.
 * Comme la synchronisation avance dans l'ordre du voyage, il n'y a en
 * pratique que deux tronçons : ce qui est parti, et la queue restée sur le
 * téléphone.
 */
private fun DrawScope.drawTrack(
    points: List<TrackPoint>,
    screen: List<Offset>,
    strokeWidth: Float,
) {
    if (screen.size < 2) return

    var runStart = 0
    while (runStart < screen.size - 1) {
        // La couleur d'un segment est celle de son point d'arrivée : c'est
        // l'état du trajet une fois parcouru.
        val state = points[runStart + 1].syncState
        var runEnd = runStart + 1
        while (runEnd < screen.size - 1 && points[runEnd + 1].syncState == state) runEnd++

        val path = Path().apply {
            moveTo(screen[runStart].x, screen[runStart].y)
            for (index in runStart + 1..runEnd) lineTo(screen[index].x, screen[index].y)
        }
        drawPath(
            path = path,
            color = state.color(),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        runStart = runEnd
    }
}

/**
 * Les points eux-mêmes, en deux appels groupés — un par état. À faible zoom
 * ils se fondent dans le trait ; à fort zoom ils montrent la cadence de
 * capture, donc les trous.
 */
private fun DrawScope.drawPointDots(
    points: List<TrackPoint>,
    screen: List<Offset>,
    radius: Float,
) {
    SyncState.entries.forEach { state ->
        val offsets = screen.filterIndexed { index, _ -> points[index].syncState == state }
        if (offsets.isEmpty()) return@forEach
        drawPoints(
            points = offsets,
            pointMode = PointMode.Points,
            color = state.color(),
            strokeWidth = radius * 2f,
            cap = StrokeCap.Round,
        )
    }
}

/** La position actuelle : un disque cerclé, lisible même posé sur le tracé. */
private fun DrawScope.drawCurrentPosition(
    center: Offset,
    color: Color,
    ringColor: Color,
    radius: Float,
    ringWidth: Float,
) {
    drawCircle(color = ringColor, radius = radius + ringWidth, center = center)
    drawCircle(color = color, radius = radius, center = center)
}

/** L'échelle graphique, en bas à gauche. Sans elle, le tracé n'a pas de taille. */
private fun DrawScope.drawScaleBar(
    metersPerPixel: Double,
    inkColor: Color,
    textMeasurer: TextMeasurer,
    marginPixels: Float,
    maxWidthPixels: Float,
) {
    val bar = MapScaleBar.fit(metersPerPixel, maxWidthPixels.toDouble()) ?: return

    val baseline = size.height - marginPixels
    val left = marginPixels
    val right = left + bar.widthPixels.toFloat()
    val tick = marginPixels / 2f

    drawLine(inkColor, Offset(left, baseline), Offset(right, baseline), strokeWidth = 2f)
    drawLine(inkColor, Offset(left, baseline - tick), Offset(left, baseline), strokeWidth = 2f)
    drawLine(inkColor, Offset(right, baseline - tick), Offset(right, baseline), strokeWidth = 2f)

    val label = textMeasurer.measure(
        text = formatDistance(bar.distanceMeters),
        style = TextStyle(color = inkColor, fontSize = SCALE_LABEL_SIZE),
    )
    drawText(label, topLeft = Offset(left, baseline - tick - label.size.height))
}

/** Mètres en dessous du kilomètre, kilomètres au-dessus. Jamais de décimale. */
private fun formatDistance(meters: Int): String =
    if (meters < 1_000) "$meters m" else "${meters / 1_000} km"

/**
 * La légende du code couleur.
 *
 * Ce n'est pas une statistique décorative au sens de `arch/09` §3 : sans
 * elle, deux couleurs sur la carte ne veulent rien dire. L'entrée « en
 * attente » n'apparaît que lorsqu'il y a effectivement quelque chose en
 * attente — au quotidien, la carte n'a donc qu'une ligne.
 */
@Composable
private fun Legend(hasPending: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = LEGEND_BACKGROUND_ALPHA))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendEntry(TrackColors.synced, "Envoyé")
        if (hasPending) LegendEntry(TrackColors.pending, "Sur le téléphone")
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun NormalizedPoint.toOffset(viewport: MapViewport, width: Float, height: Float): Offset {
    val screen = viewport.toScreen(this, width.toDouble(), height.toDouble())
    return Offset(screen.x.toFloat(), screen.y.toFloat())
}

private fun SyncState.color(): Color =
    if (this == SyncState.SYNCED) TrackColors.synced else TrackColors.pending

private fun TrackPoint.color(): Color = syncState.color()

private val FIT_MARGIN: Dp = 24.dp
private val LEGEND_MARGIN: Dp = 12.dp

/**
 * Hauteur reservee a l'echelle graphique : sa marge, sa graduation et son
 * etiquette. Constante parce qu'elle est dessinee dans le Canvas, donc jamais
 * mesuree par la composition.
 */
private val SCALE_BAND: Dp = 48.dp
private val TRACK_STROKE: Dp = 3.dp
private val POINT_RADIUS: Dp = 2.dp
private val MARKER_RADIUS: Dp = 7.dp
private val MARKER_RING: Dp = 3.dp
private val SCALE_MARGIN: Dp = 16.dp
private val SCALE_LABEL_SIZE = 11.sp
private const val SCALE_MAX_WIDTH_FRACTION = 0.4f
private const val LEGEND_BACKGROUND_ALPHA = 0.85f
