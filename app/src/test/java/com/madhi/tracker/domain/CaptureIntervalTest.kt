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
    fun `les bornes du document sont respectees`() {
        assertEquals(listOf(2, 5, 10, 15, 30), CaptureInterval.entries.map { it.minutes })
    }

    @Test
    fun `une valeur persistee inconnue ne produit pas d'intervalle invente`() {
        assertNull(CaptureInterval.fromMinutes(7))
        assertEquals(CaptureInterval.TEN, CaptureInterval.fromMinutes(10))
    }

    @Test
    fun `seul deux minutes est signale comme couteux en batterie`() {
        assertTrue(CaptureInterval.TWO.hasSignificantBatteryCost)
        assertFalse(CaptureInterval.FIVE.hasSignificantBatteryCost)
    }
}
