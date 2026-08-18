package com.madhi.tracker.presentation

import com.madhi.tracker.application.usecase.ActivateDevice
import com.madhi.tracker.domain.error.ActivationFailure
import com.madhi.tracker.domain.failure
import com.madhi.tracker.domain.model.DeviceActivation
import com.madhi.tracker.fakes.FakeDeviceActivationGateway
import com.madhi.tracker.fakes.FakeDeviceCredentials
import com.madhi.tracker.fakes.FakeSyncScheduler
import com.madhi.tracker.fakes.RecordingEventLog
import com.madhi.tracker.presentation.activation.ActivationViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActivationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val gateway = FakeDeviceActivationGateway()
    private val credentials = FakeDeviceCredentials()
    private val syncScheduler = FakeSyncScheduler()
    private val eventLog = RecordingEventLog()

    private fun viewModel() = ActivationViewModel(
        activateDevice = ActivateDevice(gateway, credentials, eventLog),
        credentials = credentials,
        syncScheduler = syncScheduler,
    )

    @Test
    fun `une activation reussie declenche immediatement un envoi`() = runTest {
        // C'est souvent la raison meme de la reactivation : un backlog
        // bloque par un token refuse doit repartir sans attendre le worker.
        val viewModel = viewModel()
        viewModel.onCodeChange("ABCD-1234")

        viewModel.onSubmit("OnePlus KB2005")

        assertTrue(viewModel.state.value.succeeded)
        assertEquals(1, syncScheduler.immediateRequests)
    }

    @Test
    fun `un echec affiche la cause et ne declenche aucun envoi`() = runTest {
        gateway.response = failure(ActivationFailure.ExpiredCode)
        val viewModel = viewModel()
        viewModel.onCodeChange("ABCD-1234")

        viewModel.onSubmit("OnePlus KB2005")

        assertEquals(ActivationFailure.ExpiredCode, viewModel.state.value.error)
        assertFalse(viewModel.state.value.succeeded)
        assertEquals(0, syncScheduler.immediateRequests)
    }

    @Test
    fun `modifier le code efface l'erreur precedente`() = runTest {
        gateway.response = failure(ActivationFailure.InvalidCode)
        val viewModel = viewModel()
        viewModel.onCodeChange("MAUVAIS")
        viewModel.onSubmit("d")

        viewModel.onCodeChange("ABCD-1234")

        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `un appareil deja active est reconnu comme tel`() = runTest {
        credentials.store(DeviceActivation("d", "t", "tr"))

        assertTrue(viewModel().state.value.alreadyActivated)
    }

    @Test
    fun `une reactivation remplace le token sans toucher aux points`() = runTest {
        credentials.store(DeviceActivation("ancien", "vieux-token", "trip"))
        val viewModel = viewModel()
        viewModel.onCodeChange("NOUVEAU-CODE")

        viewModel.onSubmit("OnePlus KB2005")

        assertEquals("device-42", credentials.deviceId())
    }
}
