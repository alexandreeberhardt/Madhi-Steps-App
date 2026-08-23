package com.madhi.tracker.presentation.map

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.madhi.tracker.domain.PlacedTile
import com.madhi.tracker.domain.TileId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Les tuiles déjà décodées, prêtes à peindre.
 *
 * Décoder un PNG coûte quelques millisecondes ; le refaire à chaque image de
 * glissement se verrait. Ce cache mémoire garde les dernières tuiles vues, et
 * le cache disque d'OkHttp garde les octets. Les deux ensemble font qu'un
 * aller-retour sur la carte ne redemande rien à personne.
 */
class TileCache(private val capacity: Int) {

    // Un LinkedHashMap en mode accès rend l'éviction du plus ancien gratuite.
    private val decoded = object : LinkedHashMap<TileId, ImageBitmap>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileId, ImageBitmap>) =
            size > capacity
    }

    /** Les tuiles demandées et pas encore revenues, pour ne pas les redemander. */
    private val inFlight = mutableSetOf<TileId>()

    operator fun get(id: TileId): ImageBitmap? = decoded[id]

    fun claim(id: TileId): Boolean = id !in decoded && inFlight.add(id)

    fun put(id: TileId, bitmap: ImageBitmap?) {
        inFlight -= id
        if (bitmap != null) decoded[id] = bitmap
    }
}

/**
 * Charge les tuiles visibles et rend celles qui sont prêtes.
 *
 * Une tuile absente ne bloque rien et ne se signale pas : la carte garde son
 * fond uni à cet endroit et le tracé passe par-dessus. C'est le comportement
 * attendu hors réseau, pas une panne.
 *
 * La valeur renvoyée change d'identité à chaque tuile arrivée, ce qui suffit à
 * redéclencher le dessin sans observer chaque entrée du cache.
 */
@Composable
fun rememberTiles(
    visible: List<PlacedTile>,
    load: suspend (TileId) -> ByteArray?,
): (TileId) -> ImageBitmap? {
    val cache = remember { TileCache(TILE_CACHE_CAPACITY) }
    var generation by remember { mutableStateOf(0) }

    LaunchedEffect(visible) {
        visible.forEach { placed ->
            if (!cache.claim(placed.id)) return@forEach
            launch {
                val bitmap = withContext(Dispatchers.Default) {
                    load(placed.id)?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                }
                cache.put(placed.id, bitmap)
                if (bitmap != null) generation++
            }
        }
    }

    return { id ->
        // Lire `generation` ici abonne le dessin aux arrivées de tuiles.
        generation.let { cache[id] }
    }
}

/**
 * Assez pour couvrir plusieurs écrans autour de la vue courante — soit
 * environ 16 Mo de bitmaps, ce qu'un appareil à 4 Go peut porter sans que la
 * carte devienne le plus gros poste mémoire de l'application.
 */
private const val TILE_CACHE_CAPACITY = 64
