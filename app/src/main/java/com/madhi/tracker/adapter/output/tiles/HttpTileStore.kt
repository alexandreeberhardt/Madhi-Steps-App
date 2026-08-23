package com.madhi.tracker.adapter.output.tiles

import com.madhi.tracker.application.port.TileStore
import com.madhi.tracker.domain.TileId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.CacheControl
import okhttp3.Request
import java.io.IOException

/**
 * Les tuiles, servies depuis le disque quand elles y sont déjà, du réseau
 * sinon.
 *
 * **Le cache est interrogé en premier, toujours.** Ce n'est pas une
 * optimisation : une tuile ne change pas en un an de voyage, et chaque requête
 * évitée est de la batterie et du forfait données économisés dans un pays où
 * les deux se comptent. Une zone consultée une fois à l'hôtel reste lisible
 * trois jours plus tard au fond d'un fjord.
 *
 * Aucune exception ne sort d'ici : une tuile manquante laisse la carte sur son
 * fond uni, et le tracé, lui, vient de Room.
 */
class HttpTileStore(
    private val callFactory: Call.Factory,
    private val urlTemplate: String,
    override val attribution: String,
    private val userAgent: String,
    override val maxZoom: Int,
    private val ioDispatcher: CoroutineDispatcher,
) : TileStore {

    override val isEnabled: Boolean = urlTemplate.isNotBlank()

    override suspend fun tile(id: TileId): ByteArray? {
        if (!isEnabled) return null
        val url = urlTemplate
            .replace("{z}", id.zoom.toString())
            .replace("{x}", id.x.toString())
            .replace("{y}", id.y.toString())

        return withContext(ioDispatcher) {
            fetch(url, CacheControl.FORCE_CACHE) ?: fetch(url, CacheControl.FORCE_NETWORK)
        }
    }

    private fun fetch(url: String, cacheControl: CacheControl): ByteArray? = try {
        val request = Request.Builder()
            .url(url)
            // Les politiques d'usage des serveurs de tuiles imposent
            // d'identifier l'application. Une requête anonyme se fait bloquer,
            // et elle le mérite.
            .header("User-Agent", userAgent)
            .cacheControl(cacheControl)
            .build()

        callFactory.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body.bytes() else null
        }
    } catch (_: IOException) {
        // Hors réseau, serveur muet, disque plein : tous les mêmes
        // conséquences pour la carte, qui se passe de cette tuile.
        null
    } catch (_: IllegalStateException) {
        // Un appel déjà consommé. Ne doit pas arriver, ne doit surtout pas
        // faire tomber l'écran d'accueil.
        null
    }
}
