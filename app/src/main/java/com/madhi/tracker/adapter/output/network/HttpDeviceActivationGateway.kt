package com.madhi.tracker.adapter.output.network

import com.madhi.tracker.adapter.output.network.dto.ActivationRequest
import com.madhi.tracker.adapter.output.network.dto.ActivationResponse
import com.madhi.tracker.application.port.DeviceActivationGateway
import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.ActivationFailure
import com.madhi.tracker.domain.failure
import com.madhi.tracker.domain.model.DeviceActivation
import com.madhi.tracker.domain.success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Échange le code d'activation contre le token appareil (ADR-004).
 *
 * Cet appel est le seul du projet à ne porter aucun en-tête
 * `Authorization` : c'est lui qui produit le token.
 */
@Singleton
class HttpDeviceActivationGateway @Inject constructor(
    private val callFactory: Call.Factory,
    private val json: Json,
    private val baseUrl: String,
    private val appVersion: String,
) : DeviceActivationGateway {

    override suspend fun activate(
        activationCode: String,
        deviceName: String,
    ): Outcome<DeviceActivation, ActivationFailure> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            ActivationRequest(
                activationCode = activationCode.trim(),
                deviceName = deviceName,
                appVersion = appVersion,
            ),
        )

        val request = Request.Builder()
            .url("$baseUrl/devices/activate")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            callFactory.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext failure(response.code.toActivationFailure(response.header("Retry-After")))
                }
                val activation = json.decodeFromString<ActivationResponse>(payload)
                success(
                    DeviceActivation(
                        deviceId = activation.deviceId,
                        deviceToken = activation.deviceToken,
                        tripId = activation.tripId,
                    ),
                )
            }
        } catch (e: SocketTimeoutException) {
            failure(ActivationFailure.NoNetwork)
        } catch (e: UnknownHostException) {
            failure(ActivationFailure.NoNetwork)
        } catch (e: IOException) {
            failure(ActivationFailure.NoNetwork)
        } catch (e: Exception) {
            failure(ActivationFailure.Unexpected(e::class.simpleName ?: "unknown"))
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun Int.toActivationFailure(retryAfterHeader: String?): ActivationFailure = when (this) {
    // Le contrat distingue un code malformé d'un code périmé : les deux
    // messages à afficher ne sont pas les mêmes.
    400 -> ActivationFailure.InvalidCode
    404, 410 -> ActivationFailure.ExpiredCode
    429 -> ActivationFailure.RateLimited(retryAfterHeader?.toLongOrNull()?.seconds)
    in 500..599 -> ActivationFailure.ServerError(this)
    else -> ActivationFailure.ServerError(this)
}
