package com.madhi.tracker.infrastructure.di

import com.madhi.tracker.adapter.output.network.HttpAddressLookup
import com.madhi.tracker.adapter.output.network.HttpDeviceActivationGateway
import com.madhi.tracker.adapter.output.network.HttpLocationSyncGateway
import com.madhi.tracker.adapter.output.persistence.credentials.KeystoreDeviceCredentials
import com.madhi.tracker.adapter.output.scheduling.WorkManagerSyncScheduler
import com.madhi.tracker.application.port.AddressLookup
import com.madhi.tracker.application.port.DeviceActivationGateway
import com.madhi.tracker.application.port.DeviceCredentials
import com.madhi.tracker.application.port.LocationSyncGateway
import com.madhi.tracker.application.port.SyncScheduler
import com.madhi.tracker.infrastructure.config.AppConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // Le serveur peut ajouter des champs en V2 sans casser ce client
        // (arch/00 §5, règle de compatibilité ascendante).
        ignoreUnknownKeys = true
        // Un champ optionnel absent n'est pas la même chose qu'un champ nul.
        explicitNulls = false
        encodeDefaults = false
    }

    @Provides
    @Singleton
    fun provideCallFactory(): Call.Factory = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Les tentatives sont pilotées par le use case et WorkManager, qui
        // savent, eux, distinguer une erreur réessayable d'une erreur définitive.
        .retryOnConnectionFailure(false)
        .build()

    @Provides
    @Singleton
    fun provideLocationSyncGateway(
        callFactory: Call.Factory,
        credentials: DeviceCredentials,
        json: Json,
    ): LocationSyncGateway = HttpLocationSyncGateway(callFactory, credentials, json, AppConfig.apiBaseUrl)

    @Provides
    @Singleton
    fun provideAddressLookup(
        callFactory: Call.Factory,
        credentials: DeviceCredentials,
        json: Json,
    ): AddressLookup = HttpAddressLookup(
        callFactory = callFactory,
        credentials = credentials,
        json = json,
        baseUrl = AppConfig.apiBaseUrl,
        ioDispatcher = Dispatchers.IO,
    )

    @Provides
    @Singleton
    fun provideDeviceActivationGateway(
        callFactory: Call.Factory,
        json: Json,
    ): DeviceActivationGateway =
        HttpDeviceActivationGateway(callFactory, json, AppConfig.apiBaseUrl, AppConfig.appVersion)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindings {

    @Binds
    abstract fun bindDeviceCredentials(credentials: KeystoreDeviceCredentials): DeviceCredentials

    @Binds
    abstract fun bindSyncScheduler(scheduler: WorkManagerSyncScheduler): SyncScheduler
}
