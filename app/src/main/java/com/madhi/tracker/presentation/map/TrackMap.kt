package com.madhi.tracker.presentation.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madhi.tracker.R
import com.madhi.tracker.domain.MapInsets
import com.madhi.tracker.domain.MapProjection
import com.madhi.tracker.domain.MapScaleBar
import com.madhi.tracker.domain.MapViewport
import com.madhi.tracker.domain.NormalizedPoint
import com.madhi.tracker.domain.PlacedTile
import com.madhi.tracker.domain.ScreenPoint
import com.madhi.tracker.domain.TileGrid
import com.madhi.tracker.domain.TileId
import com.madhi.tracker.domain.TrackPicking
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.SyncState
import com.madhi.tracker.domain.model.TrackPoint
import com.madhi.tracker.presentation.common.TrackColors
import com.madhi.tracker.presentation.common.pointTimeLabel
import java.time.Instant
import java.util.Locale

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
    backgroundTrack: List<Coordinates> = emptyList(),
    camera: MapCamera = rememberMapCamera(),
    loadTile: (suspend (TileId) -> ByteArray?)? = null,
    loadAddress: (suspend (Coordinates) -> String?)? = null,
    now: Instant = Instant.now(),
    attribution: String = "",
    maxTileZoom: Int = TileGrid.DEFAULT_MAX_TILE_ZOOM,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val inkColor = MaterialTheme.colorScheme.onSurface

    // L'échelle porte son propre fond, comme la légende et la mention légale.
    // Sans lui elle suivrait le thème du téléphone — gris pâle en thème
    // sombre — alors que les tuiles, elles, sont toujours claires.
    val chipColor = MaterialTheme.colorScheme.surface.copy(alpha = LEGEND_BACKGROUND_ALPHA)
    val markerRingColor = MaterialTheme.colorScheme.surface

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // La hauteur reelle de la legende, mesuree plutot que devinee : elle
    // change avec la taille de police du systeme, et le cadrage doit suivre.
    var legendHeight by remember { mutableStateOf(0) }

    // Idem pour la mention légale, dont la longueur dépend du fournisseur de
    // tuiles : celle de Thunderforest tient sur toute la largeur de l'écran.
    var attributionHeight by remember { mutableStateOf(0) }

    // La projection ne dépend que des points : la refaire à chaque image de
    // glissement gaspillerait deux mille logarithmes pour rien.
    val projected = remember(points) { points.map { MapProjection.normalized(it.coordinates) } }

    // Le fond suit le même chemin, et s'arrête là. Il n'entre ni dans
    // `automaticViewport` — la carte ne doit pas reculer pour montrer un mois
    // de trajet quand on demande la journée — ni dans le pointage, qui ne vise
    // que les points de la période.
    val backgroundProjected = remember(backgroundTrack) {
        backgroundTrack.map(MapProjection::normalized)
    }

    // Le point dont la bulle est ouverte. On retient le point et non son
    // indice : changer de période remplace la liste entière, et un indice
    // survivrait en désignant quelqu'un d'autre.
    var selected by remember { mutableStateOf<TrackPoint?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var lookingUpAddress by remember { mutableStateOf(false) }

    // Le tracé a changé sous la bulle — nouvelle période, autre pas de temps.
    // Un point qui n'est plus dessiné ne doit pas garder sa bulle ouverte.
    LaunchedEffect(points, selected) {
        if (selected != null && selected !in points) selected = null
    }

    // Indexé sur le seul point choisi. Prendre aussi `loadAddress` pour clé
    // relancerait la recherche a chaque recomposition si l'appelant passe une
    // lambda fabriquee sur place — une requete reseau par image de rendu.
    val currentLoadAddress by rememberUpdatedState(loadAddress)
    LaunchedEffect(selected) {
        address = null
        val point = selected ?: return@LaunchedEffect
        val lookUp = currentLoadAddress ?: return@LaunchedEffect
        lookingUpAddress = true
        address = lookUp(point.coordinates)
        lookingUpAddress = false
    }

    // Les gestes vivent dans une coroutine lancée une fois pour toutes, qui
    // fige ce qu'elle capture. Ces deux copies-ci sont tenues à jour, faute de
    // quoi un appui viserait le tracé d'il y a une heure — c'est exactement le
    // défaut que MapCamera a corrigé pour le cadrage.
    val currentPoints by rememberUpdatedState(points)
    val currentProjected by rememberUpdatedState(projected)

    val insets = with(density) {
        val margin = FIT_MARGIN.toPx().toDouble()
        MapInsets(
            left = margin,
            right = margin,
            top = margin + legendHeight,
            bottom = margin + SCALE_BAND.toPx() + attributionHeight,
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
    val viewport = camera.manual ?: automaticViewport

    // Le cadrage automatique est confié à la caméra après la composition : le
    // gestionnaire de gestes le relit là, et non dans une variable locale
    // qu'il aurait figée au premier passage.
    SideEffect { camera.followTrack(automaticViewport) }

    // Les tuiles visibles ne dependent que du cadrage et de la taille : les
    // recalculer a chaque image de glissement est du calcul entier, negligeable
    // a cote du decodage qu'on evite en gardant les memes identifiants.
    val visibleTiles = remember(viewport, canvasSize, maxTileZoom, density.density) {
        if (loadTile == null || viewport == null) {
            emptyList()
        } else {
            TileGrid.visible(
                viewport = viewport,
                widthPixels = canvasSize.width.toDouble(),
                heightPixels = canvasSize.height.toDouble(),
                maxTileZoom = maxTileZoom,
                pixelDensity = density.density.toDouble(),
            )
        }
    }
    val tile = rememberTiles(visibleTiles, loadTile ?: { null })

    Box(
        modifier = modifier
            .background(backgroundColor)
            // Une tuile déborde toujours des bords, c'est le principe du
            // découpage : sans découpe, elle se peint par-dessus le bandeau
            // d'état, que `drawBehind` ne borne pas.
            .clipToBounds()
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
                .pointerInput(camera) {
                    detectTapGestures { tap ->
                        val viewportNow = camera.viewport ?: return@detectTapGestures
                        val index = TrackPicking.nearest(
                            points = currentProjected,
                            tap = ScreenPoint(tap.x.toDouble(), tap.y.toDouble()),
                            viewport = viewportNow,
                            widthPixels = size.width.toDouble(),
                            heightPixels = size.height.toDouble(),
                            tolerancePixels = TAP_TOLERANCE.toPx().toDouble(),
                        )
                        // Appuyer à côté ferme la bulle : c'est le geste que
                        // tout le monde essaie en premier.
                        selected = index?.let(currentPoints::getOrNull)
                    }
                }
                .pointerInput(camera) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        camera.onGesture(
                            panXPixels = pan.x.toDouble(),
                            panYPixels = pan.y.toDouble(),
                            scale = zoom.toDouble(),
                            focus = ScreenPoint(
                                x = centroid.x.toDouble(),
                                y = centroid.y.toDouble(),
                            ),
                            widthPixels = size.width.toDouble(),
                            heightPixels = size.height.toDouble(),
                        )
                    }
                },
        ) {
            drawTiles(visibleTiles, tile)

            drawBackgroundTrack(
                screen = backgroundProjected.map { it.toOffset(viewport, size.width, size.height) },
                color = TrackColors.background,
                strokeWidth = BACKGROUND_STROKE.toPx(),
            )

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
                chipColor = chipColor,
                textMeasurer = textMeasurer,
                // L'échelle se pose au-dessus de la mention légale, qui
                // occupe le bas de la carte et la masquerait sinon.
                marginPixels = SCALE_MARGIN.toPx() + attributionHeight,
                maxWidthPixels = size.width * SCALE_MAX_WIDTH_FRACTION,
            )
        }

        if (attribution.isNotBlank()) {
            Attribution(
                text = attribution,
                // En bas à droite : l'échelle graphique occupe déjà le coin
                // gauche, et le bouton « Recentrer » lui laisse la place.
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .onSizeChanged { attributionHeight = it.height }
                    .padding(6.dp),
            )
        }

        Legend(
            hasPending = points.any { it.syncState == SyncState.PENDING },
            hasBackground = backgroundTrack.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .onSizeChanged { legendHeight = it.height }
                .padding(LEGEND_MARGIN),
        )

        selected?.let { point ->
            val anchor = viewport.toScreen(
                coordinates = point.coordinates,
                widthPixels = canvasSize.width.toDouble(),
                heightPixels = canvasSize.height.toDouble(),
            )
            PointBubble(
                point = point,
                address = address,
                lookingUpAddress = lookingUpAddress,
                addressAvailable = loadAddress != null,
                now = now,
                anchor = anchor,
                canvasSize = canvasSize,
                onClose = { selected = null },
            )
        }

        if (camera.isManual) {
            FilledTonalButton(
                onClick = camera::recenter,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = SCALE_MARGIN)
                    .padding(bottom = with(density) { attributionHeight.toDp() }),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_recenter),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Recentrer")
            }
        }
    }
}


/**
 * Ce qu'un point du tracé a à dire : quand, et où.
 *
 * L'heure et les coordonnées viennent de la base locale et s'affichent
 * toujours. L'adresse, elle, demande le réseau et le serveur du voyage : son
 * absence est le cas normal en route, et se dit sans dramatiser. Une bulle
 * sans adresse reste une bulle utile.
 *
 * Elle se pose au-dessus du point, et passe en dessous quand il n'y a plus la
 * place — un point en haut de l'écran ne doit pas pousser sa bulle hors du
 * cadre.
 */
@Composable
private fun BoxScope.PointBubble(
    point: TrackPoint,
    address: String?,
    lookingUpAddress: Boolean,
    addressAvailable: Boolean,
    now: Instant,
    anchor: ScreenPoint,
    canvasSize: IntSize,
    onClose: () -> Unit,
) {
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(LocalDensity.current) { BUBBLE_MARGIN.toPx() }
    val gap = with(LocalDensity.current) { BUBBLE_GAP.toPx() }

    Card(
        modifier = Modifier
            .align(Alignment.TopStart)
            .widthIn(max = BUBBLE_MAX_WIDTH)
            .onSizeChanged { bubbleSize = it }
            .offset {
                IntOffset(
                    x = horizontalPlacement(anchor.x, bubbleSize.width, canvasSize.width, margin),
                    y = verticalPlacement(anchor.y, bubbleSize.height, canvasSize.height, margin, gap),
                )
            }
            // Toucher la bulle la ferme, comme toucher la carte à côté. Sans
            // cela l'appui traverserait jusqu'au tracé et sélectionnerait le
            // point caché dessous.
            .clickable(onClick = onClose),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                pointTimeLabel(point.recordedAt, now),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                addressLine(address, lookingUpAddress, addressAvailable),
                style = MaterialTheme.typography.bodySmall,
                color = if (address == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                formatCoordinates(point.coordinates),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Centrée sur le point, sans jamais déborder du cadre. */
private fun horizontalPlacement(
    anchorX: Double,
    bubbleWidth: Int,
    canvasWidth: Int,
    margin: Float,
): Int {
    val centered = anchorX - bubbleWidth / 2.0
    val maximum = canvasWidth - bubbleWidth - margin
    // Une bulle plus large que la carte n'a pas d'intervalle où se ranger :
    // on la colle à gauche plutôt que d'inverser les bornes.
    if (maximum <= margin) return margin.toInt()
    return centered.coerceIn(margin.toDouble(), maximum.toDouble()).toInt()
}

/** Au-dessus du point, ou en dessous s'il n'y a plus la place au-dessus. */
private fun verticalPlacement(
    anchorY: Double,
    bubbleHeight: Int,
    canvasHeight: Int,
    margin: Float,
    gap: Float,
): Int {
    val above = anchorY - bubbleHeight - gap
    if (above >= margin) return above.toInt()

    val below = anchorY + gap
    val maximum = canvasHeight - bubbleHeight - margin
    if (maximum <= margin) return margin.toInt()
    return below.coerceIn(margin.toDouble(), maximum.toDouble()).toInt()
}

/**
 * Dit ce qui se passe, jamais « erreur ». Hors réseau est le mode normal du
 * voyage, pas une panne.
 */
private fun addressLine(address: String?, looking: Boolean, available: Boolean): String = when {
    address != null -> address
    looking -> "Recherche de l'adresse…"
    !available -> "Adresse non configurée."
    else -> "Adresse indisponible hors ligne."
}

/**
 * Cinq décimales, soit environ un mètre : au-delà, le GPS n'en sait rien.
 *
 * Point décimal et non virgule, même en français : une virgule décimale et une
 * virgule séparatrice dans la même ligne donneraient « 48,85660, 2,35220 »,
 * que personne ne sait relire. Les coordonnées s'écrivent avec un point.
 */
private fun formatCoordinates(coordinates: Coordinates): String = String.format(
    Locale.US,
    "%.5f, %.5f",
    coordinates.latitude,
    coordinates.longitude,
)

/**
 * Le fond de carte, tuile par tuile.
 *
 * Ce qui manque ne se dessine pas et ne se signale pas : hors réseau, une
 * carte à trous reste plus utile qu'un damier de rectangles « en cours de
 * chargement » qui ne se rempliront jamais.
 */
private fun DrawScope.drawTiles(visible: List<PlacedTile>, tile: (TileId) -> ImageBitmap?) {
    visible.forEach { placed ->
        val bitmap = tile(placed.id) ?: return@forEach
        drawImage(
            image = bitmap,
            dstOffset = IntOffset(placed.left.toInt(), placed.top.toInt()),
            // Arrondi vers le haut : deux tuiles voisines arrondies vers le bas
            // laissent une couture d'un pixel entre elles, très visible sur un
            // fond clair.
            dstSize = IntSize(
                width = (placed.left + placed.size).toInt() - placed.left.toInt() + 1,
                height = (placed.top + placed.size).toInt() - placed.top.toInt() + 1,
            ),
        )
    }
}

/**
 * La mention légale des données cartographiques. Discrète, mais jamais
 * absente : les licences de données l'imposent, elle n'est pas négociable.
 */
@Composable
private fun Attribution(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = LEGEND_BACKGROUND_ALPHA))
            .padding(horizontal = 4.dp),
    )
}

/**
 * Le reste du voyage, en fond.
 *
 * Un seul trait, plus fin et sans ses points : c'est un repère, pas une donnée
 * qu'on interroge. Il se dessine avant le tracé de la période, qui doit rester
 * le seul objet net de la carte. Il n'a pas non plus à être découpé par état de
 * synchronisation : il ne dit rien de ce qui est parti ou non.
 */
private fun DrawScope.drawBackgroundTrack(
    screen: List<Offset>,
    color: Color,
    strokeWidth: Float,
) {
    if (screen.size < 2) return

    val path = Path().apply {
        moveTo(screen[0].x, screen[0].y)
        for (index in 1 until screen.size) lineTo(screen[index].x, screen[index].y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
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
    chipColor: Color,
    textMeasurer: TextMeasurer,
    marginPixels: Float,
    maxWidthPixels: Float,
) {
    val bar = MapScaleBar.fit(metersPerPixel, maxWidthPixels.toDouble()) ?: return

    val baseline = size.height - marginPixels
    val left = marginPixels
    val right = left + bar.widthPixels.toFloat()
    val tick = marginPixels / 2f

    val label = textMeasurer.measure(
        text = formatDistance(bar.distanceMeters),
        style = TextStyle(color = inkColor, fontSize = SCALE_LABEL_SIZE),
    )

    val padding = tick / 2f
    val top = baseline - tick - label.size.height - padding
    drawRoundRect(
        color = chipColor,
        topLeft = Offset(left - padding, top),
        size = Size(
            width = maxOf(right - left, label.size.width.toFloat()) + 2 * padding,
            height = baseline - top + padding,
        ),
        cornerRadius = CornerRadius(padding, padding),
    )

    drawLine(inkColor, Offset(left, baseline), Offset(right, baseline), strokeWidth = 2f)
    drawLine(inkColor, Offset(left, baseline - tick), Offset(left, baseline), strokeWidth = 2f)
    drawLine(inkColor, Offset(right, baseline - tick), Offset(right, baseline), strokeWidth = 2f)

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
private fun Legend(hasPending: Boolean, hasBackground: Boolean, modifier: Modifier = Modifier) {
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
        // Sans cette ligne, un trait gris apparaîtrait sur la carte sans que
        // rien ne dise ce qu'il est.
        if (hasBackground) LegendEntry(TrackColors.background, "Reste du voyage")
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

/**
 * Le trait du fond : plus fin que le tracé de la période, pour qu'un regard
 * distingue les deux sans avoir à comparer les couleurs — en plein soleil sur
 * un guidon, l'épaisseur se lit avant la teinte.
 */
private val BACKGROUND_STROKE: Dp = 2.dp
/**
 * Un point du tracé. Un peu plus gros qu'avant : ils sont désormais des cibles
 * qu'on vise du doigt, et non plus seulement des marques de cadence.
 */
private val POINT_RADIUS: Dp = 3.dp

/**
 * De quel écart un appui peut manquer un point sans le manquer vraiment.
 *
 * Un point dessiné fait six pixels de large, un doigt en couvre une
 * cinquantaine. Viser au pixel serait injouable — surtout sur un guidon.
 */
private val TAP_TOLERANCE: Dp = 22.dp

private val BUBBLE_MAX_WIDTH: Dp = 260.dp
private val BUBBLE_MARGIN: Dp = 12.dp

/** L'écart entre le point visé et sa bulle : assez pour ne pas le masquer. */
private val BUBBLE_GAP: Dp = 14.dp
private val MARKER_RADIUS: Dp = 7.dp
private val MARKER_RING: Dp = 3.dp
/**
 * Marge de l'échelle graphique, et donc du bouton « Recentrer » : les deux se
 * posent sur la même ligne de base, au-dessus de la mention légale. Le bouton
 * flottait dix-huit points plus haut, sans rien pour justifier l'écart.
 */
private val SCALE_MARGIN: Dp = 16.dp

private val SCALE_LABEL_SIZE = 11.sp
private const val SCALE_MAX_WIDTH_FRACTION = 0.4f
private const val LEGEND_BACKGROUND_ALPHA = 0.85f
