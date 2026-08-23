package com.madhi.tracker.presentation.map

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.madhi.tracker.domain.PlacedTile
import com.madhi.tracker.domain.TileId
import kotlinx.coroutines.Dispatchers
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

    operator fun get(id: TileId): ImageBitmap? = decoded[id]

    fun put(id: TileId, bitmap: ImageBitmap) {
        decoded[id] = bitmap
    }
}

/**
 * Charge les tuiles visibles et rend celles qui sont prêtes.
 *
 * **Un effet par tuile, indexé sur son identifiant.** La première version
 * tenait à la main la liste des tuiles « en cours de chargement », pour ne pas
 * les redemander ; elle a rempli un écran sur trente. Une coroutine annulée
 * avant même d'avoir démarré n'exécute pas son `finally` : la tuile restait
 * marquée en cours pour toujours, et n'était jamais redemandée. Aucune erreur
 * nulle part, juste un fond qui ne venait pas.
 *
 * Confier ce cycle de vie à Compose supprime la classe entière du problème :
 * l'effet d'une tuile vit exactement tant qu'elle est visible, et repart si
 * elle revient. Il n'y a plus de comptabilité à tenir juste.
 *
 * Une tuile absente ne bloque rien et ne se signale pas : la carte garde son
 * fond uni à cet endroit et le tracé passe par-dessus. C'est le comportement
 * attendu hors réseau, pas une panne.
 */
@Composable
fun rememberTiles(
    visible: List<PlacedTile>,
    load: suspend (TileId) -> ByteArray?,
): (TileId) -> ImageBitmap? {
    val cache = remember { TileCache(TILE_CACHE_CAPACITY) }

    // Change d'identité à chaque tuile arrivée, ce qui suffit à redéclencher
    // le dessin sans observer chaque entrée du cache.
    var generation by remember { mutableIntStateOf(0) }

    visible.forEach { placed ->
        key(placed.id) {
            LaunchedEffect(placed.id) {
                if (cache[placed.id] != null) return@LaunchedEffect
                val bytes = load(placed.id) ?: return@LaunchedEffect
                val bitmap = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } ?: return@LaunchedEffect

                cache.put(placed.id, bitmap)
                generation++
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
