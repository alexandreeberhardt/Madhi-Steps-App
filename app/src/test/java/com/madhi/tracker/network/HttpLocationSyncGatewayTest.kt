package com.madhi.tracker.network

import com.madhi.tracker.adapter.output.network.HttpLocationSyncGateway
import com.madhi.tracker.application.port.DeviceCredentials
import com.madhi.tracker.domain.error.SyncFailure
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.DeviceActivation
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * On ne teste pas OkHttp : on teste notre intégration — la traduction des
 * codes HTTP en catégories métier, et la conformité du payload au contrat.
 */
class HttpLocationSyncGatewayTest {

    private lateinit var server: MockWebServer
    private lateinit var gateway: HttpLocationSyncGateway

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    private val credentials = object : DeviceCredentials {
        override suspend fun store(activation: DeviceActivation) = Unit
        override suspend fun isActivated() = true
        override suspend fun deviceId() = "device-42"
        override suspend fun tripId() = "trip-7"
        override suspend fun authorizationHeaderValue() = "Bearer secret-token"
    }

    private val point = LocationPoint(
        id = LocationId("3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6071"),
        coordinates = Coordinates(48.85837, 2.29448),
        recordedAt = Instant.parse("2026-08-18T14:32:07Z"),
        accuracyMeters = 12.4f,
        batteryPercent = 62,
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        gateway = HttpLocationSyncGateway(
            callFactory = OkHttpClient(),
            credentials = credentials,
            json = json,
            baseUrl = server.url("/api/v1").toString().trimEnd('/'),
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun respond(code: Int, body: String = "{}", vararg headers: Pair<String, String>) {
        val builder = MockResponse.Builder().code(code).body(body)
        headers.forEach { builder.addHeader(it.first, it.second) }
        server.enqueue(builder.build())
    }

    @Test
    fun `envoie le payload attendu par le contrat`() = runTest {
        respond(200, """{"accepted":["${point.id.value}"],"duplicates":[],"rejected":[]}""")

        gateway.upload(listOf(point))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/locations/batch", request.url.encodedPath)
        assertEquals("Bearer secret-token", request.headers["Authorization"])

        val body = request.body!!.utf8()
        assertTrue(body.contains(""""id":"${point.id.value}""""))
        assertTrue(body.contains(""""deviceId":"device-42""""))
        assertTrue(body.contains(""""recordedAt":"2026-08-18T14:32:07Z""""))
        assertTrue(body.contains(""""batteryPercent":62"""))
    }

    @Test
    fun `un champ optionnel inconnu est absent du payload plutot que nul`() = runTest {
        respond(200)

        gateway.upload(listOf(point.copy(altitudeMeters = null, speedMetersPerSecond = null)))

        val body = server.takeRequest().body!!.utf8()
        assertFalse(body.contains("altitudeMeters"))
        assertFalse(body.contains("speedMps"))
        assertFalse(body.contains("null"))
    }

    @Test
    fun `les doublons sont rendus comme detenus par le serveur`() = runTest {
        respond(200, """{"accepted":[],"duplicates":["${point.id.value}"],"rejected":[]}""")

        val acknowledgement = gateway.upload(listOf(point)).valueOrNull()!!

        assertEquals(listOf(point.id), acknowledgement.stored)
    }

    @Test
    fun `les points refuses sont rendus avec leur motif`() = runTest {
        respond(
            200,
            """{"accepted":[],"duplicates":[],"rejected":[{"id":"${point.id.value}","reason":"invalid_coordinates"}]}""",
        )

        val acknowledgement = gateway.upload(listOf(point)).valueOrNull()!!

        assertTrue(acknowledgement.stored.isEmpty())
        assertEquals("invalid_coordinates", acknowledgement.rejected.single().reason)
    }

    @Test
    fun `un champ inconnu dans la reponse ne casse pas le client`() = runTest {
        // Le serveur V2 pourra enrichir sa reponse sans casser l'app V1.
        respond(200, """{"accepted":["${point.id.value}"],"serverTime":"2026-08-18T14:32:09Z"}""")

        assertTrue(gateway.upload(listOf(point)).isSuccess)
    }

    @Test
    fun `401 devient une erreur d'authentification non reessayable`() = runTest {
        respond(401)

        val failure = gateway.upload(listOf(point)).failureOrNull()!!

        assertEquals(SyncFailure.Unauthorized, failure)
        assertFalse(failure.isRetryable)
    }

    @Test
    fun `403 devient aussi une erreur d'authentification`() = runTest {
        respond(403)
        assertEquals(SyncFailure.Unauthorized, gateway.upload(listOf(point)).failureOrNull())
    }

    @Test
    fun `413 demande de reduire le lot`() = runTest {
        respond(413)
        assertEquals(SyncFailure.BatchTooLarge, gateway.upload(listOf(point)).failureOrNull())
    }

    @Test
    fun `429 respecte l'en-tete Retry-After`() = runTest {
        respond(429, headers = arrayOf("Retry-After" to "120"))

        assertEquals(SyncFailure.RateLimited(120.seconds), gateway.upload(listOf(point)).failureOrNull())
    }

    @Test
    fun `429 sans en-tete reste exploitable`() = runTest {
        respond(429)
        assertEquals(SyncFailure.RateLimited(null), gateway.upload(listOf(point)).failureOrNull())
    }

    @Test
    fun `400 est un probleme de donnees, pas un incident reseau`() = runTest {
        respond(400)

        val failure = gateway.upload(listOf(point)).failureOrNull()!!

        assertEquals(SyncFailure.RejectedPayload(400), failure)
        assertFalse(failure.isRetryable)
    }

    @Test
    fun `503 est temporaire et reessayable`() = runTest {
        respond(503)

        val failure = gateway.upload(listOf(point)).failureOrNull()!!

        assertEquals(SyncFailure.ServerError(503), failure)
        assertTrue(failure.isRetryable)
    }

    @Test
    fun `un serveur injoignable devient une absence de reseau`() = runTest {
        server.close()

        val failure = gateway.upload(listOf(point)).failureOrNull()!!

        assertTrue(failure.isRetryable)
    }

    @Test
    fun `une reponse illisible est signalee sans etre confondue avec un succes`() = runTest {
        respond(200, "ceci n'est pas du json")

        val failure = gateway.upload(listOf(point)).failureOrNull()!!

        assertTrue(failure is SyncFailure.Unexpected)
    }

    @Test
    fun `un lot vide ne declenche aucun appel reseau`() = runTest {
        val acknowledgement = gateway.upload(emptyList()).valueOrNull()!!

        assertTrue(acknowledgement.stored.isEmpty())
        assertEquals(0, server.requestCount)
    }
}
