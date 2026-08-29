package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.CaptureInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class CaptureIntervalTest {

    @Test
    fun `la valeur par defaut du produit est cinq minutes`() {
        assertEquals(CaptureInterval.FIVE, CaptureInterval.DEFAULT)
        assertEquals(5.minutes, CaptureInterval.DEFAULT.duration)
    }

    @Test
    fun `les paliers proposes vont du plus fin au plus grossier`() {
        assertEquals(listOf(5, 30, 60), CaptureInterval.PRESETS.map { it.minutes })
    }

    @Test
    fun `une cadence hors palier reste valide mais n'est pas un palier`() {
        // C'est tout l'objet d'« Autre » : la liste n'est plus fermee, elle
        // est seulement bornee.
        val choisie = CaptureInterval.ofMinutes(7)

        assertEquals(7, choisie.minutes)
        assertFalse(choisie.isPreset)
        assertTrue(CaptureInterval.THIRTY.isPreset)
    }

    @Test
    fun `les bornes tiennent d'une minute a vingt-quatre heures`() {
        assertNull(CaptureInterval.fromMinutes(0))
        assertNull(CaptureInterval.fromMinutes(-5))
        assertNull(CaptureInterval.fromMinutes(24 * 60 + 1))

        assertEquals(1, CaptureInterval.fromMinutes(1)?.minutes)
        assertEquals(1_440, CaptureInterval.fromMinutes(1_440)?.minutes)
    }

    @Test
    fun `une valeur persistee hors bornes ne produit pas d'intervalle invente`() {
        assertNull(CaptureInterval.fromMinutes(Int.MAX_VALUE))
    }
}
