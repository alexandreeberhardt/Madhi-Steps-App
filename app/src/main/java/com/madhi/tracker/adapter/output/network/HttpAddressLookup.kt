package com.madhi.tracker.adapter.output.network

import com.madhi.tracker.application.port.AddressLookup
import com.madhi.tracker.application.port.DeviceCredentials
import com.madhi.tracker.domain.model.Coordinates
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Request
import java.util.Locale

/**
 * L'adresse d'un point, demandée au serveur du voyage.
 *
 * Aucune exception ne sort d'ici. Une adresse introuvable, un serveur qui a
 * l'option éteinte, un fjord sans réseau : trois `null`, et trois fois le même
 * affichage — l'heure et les coordonnées, qui n'ont jamais eu besoin du
 * réseau.
 *
 * Le cache mémoire n'est pas une optimisation de confort. Sans lui, revenir
 * trois fois sur le même point du tracé ferait trois requêtes, dans un forfait
 * données qui se compte à l'étranger. Il vit le temps du processus : la carte
 * ne mérite pas un fichier de plus à faire vivre pendant un an.
 */
class HttpAddressLookup(
    private val callFactory: Call.Factory,
    private val credentials: DeviceCredentials,
    private val json: Json,
    private val baseUrl: String,
    private val ioDispatcher: CoroutineDispatcher,
    private val cacheSize: Int = DEFAULT_CACHE_SIZE,
) : AddressLookup {

    // Un LinkedHashMap en mode accès rend l'éviction du plus ancien gratuite,
    // comme pour les tuiles décodées.
    private val cache = object : LinkedHashMap<String, String?>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>) =
            size > cacheSize
    }
    private val mutex = Mutex()

    override suspend fun address(coordinates: Coordinates): String? {
        val key = cacheKey(coordinates)
        mutex.withLock { if (cache.containsKey(key)) return cache[key] }

        val authorization = credentials.authorizationHeaderValue() ?: return null
        val address = withContext(ioDispatcher) { fetch(coordinates, authorization) }

        // Seules les réussites sont retenues. D'ici, un fjord sans réseau et
        // un point sans adresse rendent le même `null`, et mettre le second en
        // cache mettrait le premier avec lui — la bulle resterait vide jusqu'à
        // la fin du voyage. C'est le serveur, lui, qui sait faire la
        // différence et qui garde les réponses vides.
        if (address != null) mutex.withLock { cache[key] = address }
        return address
    }

    private fun fetch(coordinates: Coordinates, authorization: String): String? {
        val request = Request.Builder()
            .url(
                "$baseUrl/reverse-geocode" +
                    "?lat=${format(coordinates.latitude)}&lon=${format(coordinates.longitude)}",
            )
            .addHeader("Authorization", authorization)
            .get()
            .build()

        return try {
            callFactory.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val payload = response.body?.string().orEmpty()
                json.decodeFromString<ReverseGeocodeResponseDto>(payload).address.ifBlank { null }
            }
        } catch (e: Exception) {
            // Y compris une réponse illisible : rien ici ne vaut la peine
            // d'interrompre l'affichage d'une bulle.
            null
        }
    }

    /**
     * Les coordonnées arrondies à cinq décimales, soit environ un mètre. Deux
     * appuis sur le même point du tracé partagent leur réponse ; le serveur,
     * lui, regroupe plus large.
     */
    private fun cacheKey(coordinates: Coordinates): String =
        "${format(coordinates.latitude)},${format(coordinates.longitude)}"

    /**
     * Toujours le point décimal, quelle que soit la langue du téléphone. En
     * français, `toString()` d'un `Double` formaté par la locale écrirait
     * `48,8566`, que le serveur lirait comme deux paramètres.
     */
    private fun format(value: Double): String = String.format(Locale.US, "%.5f", value)

    private companion object {
        /** De l'ordre de ce qu'une session de consultation touche de points. */
        const val DEFAULT_CACHE_SIZE = 256
    }
}

@Serializable
internal data class ReverseGeocodeResponseDto(val address: String)
