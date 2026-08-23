package com.madhi.tracker.infrastructure.di

import android.content.Context
import com.madhi.tracker.adapter.output.tiles.HttpTileStore
import com.madhi.tracker.application.port.TileStore
import com.madhi.tracker.infrastructure.config.AppConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import okhttp3.Cache
import okhttp3.Call
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TileCalls

@Module
@InstallIn(SingletonComponent::class)
object TileModule {

    /**
     * Un client séparé de celui de l'API, et pour deux raisons.
     *
     * Il porte un cache disque, que le client de l'API ne doit surtout pas
     * avoir : une réponse de synchronisation servie depuis un cache serait un
     * bug de correction, pas de performance. Et ses délais sont courts —
     * une tuile qui tarde n'est pas une tuile perdue, l'écran s'en passe et la
     * redemandera au prochain déplacement, alors qu'un envoi de positions
     * mérite d'attendre.
     */
    @Provides
    @Singleton
    @TileCalls
    fun provideTileCallFactory(@ApplicationContext context: Context): Call.Factory =
        OkHttpClient.Builder()
            // `filesDir` et non `cacheDir` : Android vide le cache sous
            // pression de stockage, et il le ferait au pire moment — celui où
            // le téléphone est plein de photos, loin de tout réseau.
            .cache(Cache(File(context.filesDir, TILE_CACHE_DIRECTORY), TILE_CACHE_BYTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideTileStore(@TileCalls callFactory: Call.Factory): TileStore = HttpTileStore(
        callFactory = callFactory,
        urlTemplate = AppConfig.tileUrlTemplate,
        attribution = AppConfig.tileAttribution,
        userAgent = "MadhiTracker/${AppConfig.appVersion}",
        maxZoom = AppConfig.tileMaxZoom,
        ioDispatcher = Dispatchers.IO,
    )

    private const val TILE_CACHE_DIRECTORY = "map-tiles"

    /**
     * 192 Mo, soit de l'ordre de dix mille tuiles. Assez pour le corridor
     * d'une étape consultée à l'avance, sans hypothéquer le stockage d'un
     * téléphone d'entrée de gamme.
     */
    private const val TILE_CACHE_BYTES = 192L * 1024 * 1024
}
