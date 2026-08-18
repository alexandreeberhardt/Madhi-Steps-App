package com.madhi.tracker.adapter.output.network

import com.madhi.tracker.adapter.output.network.dto.LocationBatchRequest
import com.madhi.tracker.adapter.output.network.dto.LocationBatchResponse
import com.madhi.tracker.adapter.output.network.dto.LocationPointV1Dto
import com.madhi.tracker.application.port.BatchAcknowledgement
import com.madhi.tracker.application.port.DeviceCredentials
import com.madhi.tracker.application.port.LocationSyncGateway
import com.madhi.tracker.application.port.RejectedPoint
import com.madhi.tracker.domain.Outcome
import com.madhi.tracker.domain.error.SyncFailure
import com.madhi.tracker.domain.failure
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class HttpLocationSyncGateway @Inject constructor(
    private val callFactory: Call.Factory,
    private val credentials: DeviceCredentials,
    private val json: Json,
    private val baseUrl: String,
) : LocationSyncGateway {

    override suspend fun upload(points: List<LocationPoint>): Outcome<BatchAcknowledgement, SyncFailure> {
        if (points.isEmpty()) return success(BatchAcknowledgement(emptyList(), emptyList(), emptyList()))

        val deviceId = credentials.deviceId() ?: return failure(SyncFailure.NotActivated)
        val authorization = credentials.authorizationHeaderValue() ?: return failure(SyncFailure.NotActivated)

        val body = json.encodeToString(LocationBatchRequest(points.map { it.toDto(deviceId) }))

        val request = Request.Builder()
            .url("$baseUrl/locations/batch")
            .addHeader("Authorization", authorization)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return execute(request) { payload ->
            val response = json.decodeFromString<LocationBatchResponse>(payload)
            BatchAcknowledgement(
                accepted = response.accepted.map(::LocationId),
                duplicates = response.duplicates.map(::LocationId),
                rejected = response.rejected.map { RejectedPoint(LocationId(it.id), it.reason) },
            )
        }
    }

    private suspend fun <T> execute(request: Request, parse: (String) -> T): Outcome<T, SyncFailure> =
        withContext(Dispatchers.IO) {
            try {
                callFactory.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        success(parse(payload))
                    } else {
                        failure(response.code.toSyncFailure(response.header("Retry-After")))
                    }
                }
            } catch (e: SocketTimeoutException) {
                failure(SyncFailure.Timeout)
            } catch (e: UnknownHostException) {
                // Pas de DNS : dans les faits, pas de réseau utilisable.
                failure(SyncFailure.NoNetwork)
            } catch (e: IOException) {
                failure(SyncFailure.NoNetwork)
            } catch (e: Exception) {
                // Une réponse illisible est un problème de contrat, pas de
                // réseau. On la distingue pour ne pas boucler dessus.
                failure(SyncFailure.Unexpected(e::class.simpleName ?: "unknown"))
            }
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * Traduit les codes HTTP figés par `arch/03` §10 en catégories métier.
 * Aucune de ces branches ne supprime de point.
 */
internal fun Int.toSyncFailure(retryAfterHeader: String?): SyncFailure = when (this) {
    401, 403 -> SyncFailure.Unauthorized
    413 -> SyncFailure.BatchTooLarge
    429 -> SyncFailure.RateLimited(retryAfterHeader?.toLongOrNull()?.seconds)
    in 400..499 -> SyncFailure.RejectedPayload(this)
    in 500..599 -> SyncFailure.ServerError(this)
    else -> SyncFailure.Unexpected("http_$this")
}

private fun LocationPoint.toDto(deviceId: String) = LocationPointV1Dto(
    id = id.value,
    deviceId = deviceId,
    latitude = coordinates.latitude,
    longitude = coordinates.longitude,
    recordedAt = DateTimeFormatter.ISO_INSTANT.format(recordedAt.truncatedToSecond()),
    accuracyMeters = accuracyMeters,
    altitudeMeters = altitudeMeters,
    speedMps = speedMetersPerSecond,
    batteryPercent = batteryPercent,
)

private fun Instant.truncatedToSecond(): Instant = Instant.ofEpochSecond(epochSecond)
