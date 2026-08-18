package com.madhi.tracker.usecase

import com.madhi.tracker.application.usecase.ActivateDevice
import com.madhi.tracker.domain.error.ActivationFailure
import com.madhi.tracker.domain.failure
import com.madhi.tracker.fakes.FakeDeviceActivationGateway
import com.madhi.tracker.fakes.FakeDeviceCredentials
import com.madhi.tracker.fakes.RecordingEventLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivateDeviceTest {

    private val gateway = FakeDeviceActivationGateway()
    private val credentials = FakeDeviceCredentials()
    private val eventLog = RecordingEventLog()

    private val activateDevice = ActivateDevice(gateway, credentials, eventLog)

    @Test
    fun `un code valide stocke les identifiants de l'appareil`() = runTest {
        val result = activateDevice("ABCD-1234", "OnePlus KB2005")

        assertTrue(result.isSuccess)
        assertTrue(credentials.isActivated())
        assertEquals("device-42", credentials.deviceId())
        assertEquals("trip-7", credentials.tripId())
    }

    @Test
    fun `les espaces autour du code saisi sont ignores`() = runTest {
        activateDevice("  ABCD-1234  ", "OnePlus KB2005")

        assertEquals("ABCD-1234", gateway.lastCode)
    }

    @Test
    fun `un code vide est refuse sans appeler le serveur`() = runTest {
        // Le serveur limite le nombre d'essais : ne pas bruler une tentative
        // pour une saisie vide.
        val result = activateDevice("   ", "OnePlus KB2005")

        assertEquals(ActivationFailure.InvalidCode, result.failureOrNull())
        assertEquals(0, gateway.callCount)
    }

    @Test
    fun `un code expire ne stocke rien`() = runTest {
        gateway.response = failure(ActivationFailure.ExpiredCode)

        val result = activateDevice("ABCD-1234", "OnePlus KB2005")

        assertEquals(ActivationFailure.ExpiredCode, result.failureOrNull())
        assertFalse(credentials.isActivated())
    }

    @Test
    fun `une absence de reseau ne consomme pas le code localement`() = runTest {
        gateway.response = failure(ActivationFailure.NoNetwork)

        activateDevice("ABCD-1234", "OnePlus KB2005")

        assertNull(credentials.activation)
    }

    @Test
    fun `le code saisi n'apparait jamais dans le journal`() = runTest {
        gateway.response = failure(ActivationFailure.ExpiredCode)

        activateDevice("SECRET-CODE-9999", "OnePlus KB2005")

        assertTrue(eventLog.details.none { it.second?.contains("SECRET") == true })
    }

    @Test
    fun `le nom de l'appareil est transmis au serveur`() = runTest {
        activateDevice("ABCD-1234", "Xiaomi Redmi Note 11")

        assertEquals("Xiaomi Redmi Note 11", gateway.lastDeviceName)
    }
}
