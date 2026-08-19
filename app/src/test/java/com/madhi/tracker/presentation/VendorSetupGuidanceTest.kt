package com.madhi.tracker.presentation

import com.madhi.tracker.domain.model.DeviceVendor
import com.madhi.tracker.presentation.onboarding.VendorSetupGuidance
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorSetupGuidanceTest {

    @Test
    fun `chaque constructeur connu a des consignes concretes`() {
        val knownVendors = DeviceVendor.entries - DeviceVendor.OTHER

        knownVendors.forEach { vendor ->
            assertTrue("$vendor sans consigne", VendorSetupGuidance.stepsFor(vendor).isNotEmpty())
        }
    }

    @Test
    fun `un constructeur inconnu ne donne pas de fausses consignes`() {
        assertTrue(VendorSetupGuidance.stepsFor(DeviceVendor.OTHER).isEmpty())
    }

    @Test
    fun `aucune consigne constructeur n'a de chemin ou de consequence vide`() {
        DeviceVendor.entries.flatMap(VendorSetupGuidance::stepsFor).forEach { step ->
            assertTrue("titre vide", step.title.isNotBlank())
            assertTrue("chemin vide pour ${step.title}", step.path.isNotBlank())
            assertTrue("consequence vide pour ${step.title}", step.consequence.isNotBlank())
        }
    }
}
