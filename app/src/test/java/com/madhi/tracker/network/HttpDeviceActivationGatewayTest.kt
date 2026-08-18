package com.madhi.tracker.network

import com.madhi.tracker.adapter.output.network.HttpDeviceActivationGateway
import com.madhi.tracker.domain.error.ActivationFailure
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
import kotlin.time.Duration.Companion.seconds

class HttpDeviceActivationGatewayTest {

    private lateinit var server: MockWebServer
    private lateinit var gateway: HttpDeviceActivationGateway

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        gateway = HttpDeviceActivationGateway(
            callFactory = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false },
            baseUrl = server.url("/api/v1").toString().trimEnd('/'),
            appVersion = "0.1.0",
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
    fun `un code valide rend les identifiants de l'appareil`() = runTest {
        respond(200, """{"deviceId":"device-42","deviceToken":"tok","tripId":"trip-7"}""")

        val activation = gateway.activate("ABCD-1234", "OnePlus KB2005").valueOrNull()!!

        assertEquals("device-42", activation.deviceId)
        assertEquals("tok", activation.deviceToken)
        assertEquals("trip-7", activation.tripId)
    }

    @Test
    fun `la requete suit le contrat et ne porte aucune autorisation`() = runTest {
        respond(200, """{"deviceId":"d","deviceToken":"t","tripId":"tr"}""")

        gateway.activate("ABCD-1234", "OnePlus KB2005")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/devices/activate", request.url.encodedPath)
        // C'est l'appel qui produit le token : il ne peut pas en présenter un.
        assertFalse(request.headers.names().contains("Authorization"))

        val body = request.body!!.utf8()
        assertTrue(body.contains(""""activationCode":"ABCD-1234""""))
        assertTrue(body.contains(""""deviceName":"OnePlus KB2005""""))
        assertTrue(body.contains(""""appVersion":"0.1.0""""))
    }

    @Test
    fun `400 signale un code malforme`() = runTest {
        respond(400)

        assertEquals(ActivationFailure.InvalidCode, gateway.activate("x", "d").failureOrNull())
    }

    @Test
    fun `410 signale un code perime ou deja utilise`() = runTest {
        // Deux messages differents a afficher : re-saisir, ou demander un
        // nouveau code.
        respond(410)

        assertEquals(ActivationFailure.ExpiredCode, gateway.activate("x", "d").failureOrNull())
    }

    @Test
    fun `404 est traite comme un code inconnu, pas comme une panne`() = runTest {
        respond(404)

        assertEquals(ActivationFailure.ExpiredCode, gateway.activate("x", "d").failureOrNull())
    }

    @Test
    fun `429 respecte l'en-tete Retry-After`() = runTest {
        respond(429, headers = arrayOf("Retry-After" to "60"))

        assertEquals(ActivationFailure.RateLimited(60.seconds), gateway.activate("x", "d").failureOrNull())
    }

    @Test
    fun `503 est une erreur serveur temporaire`() = runTest {
        respond(503)

        assertEquals(ActivationFailure.ServerError(503), gateway.activate("x", "d").failureOrNull())
    }

    @Test
    fun `un serveur injoignable devient une absence de reseau`() = runTest {
        server.close()

        assertEquals(ActivationFailure.NoNetwork, gateway.activate("x", "d").failureOrNull())
    }

    @Test
    fun `une reponse illisible ne passe pas pour un succes`() = runTest {
        respond(200, "pas du json")

        val failure = gateway.activate("x", "d").failureOrNull()
        assertTrue(failure is ActivationFailure.Unexpected)
    }

    @Test
    fun `une reponse incomplete est refusee plutot que stockee a moitie`() = runTest {
        // Un token sans deviceId rendrait l'appareil inutilisable de facon
        // silencieuse : mieux vaut echouer tout de suite.
        respond(200, """{"deviceToken":"tok"}""")

        assertTrue(gateway.activate("x", "d").failureOrNull() is ActivationFailure.Unexpected)
    }

    @Test
    fun `un champ inconnu dans la reponse ne casse pas le client`() = runTest {
        respond(200, """{"deviceId":"d","deviceToken":"t","tripId":"tr","serverTime":"2026-08-19T10:00:00Z"}""")

        assertTrue(gateway.activate("x", "d").isSuccess)
    }
}
