package com.madhi.tracker.presentation

import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.presentation.common.captureIntervalLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureIntervalLabelTest {

    @Test
    fun `sous l'heure, la cadence se dit en minutes`() {
        assertEquals("1 min", captureIntervalLabel(CaptureInterval.ofMinutes(1)))
        assertEquals("5 min", captureIntervalLabel(CaptureInterval.FIVE))
        assertEquals("30 min", captureIntervalLabel(CaptureInterval.THIRTY))
        assertEquals("59 min", captureIntervalLabel(CaptureInterval.ofMinutes(59)))
    }

    @Test
    fun `une heure ronde se dit en heures`() {
        assertEquals("1 h", captureIntervalLabel(CaptureInterval.ONE_HOUR))
        assertEquals("24 h", captureIntervalLabel(CaptureInterval.ofMinutes(24 * 60)))
    }

    @Test
    fun `au-dela de l'heure, personne ne doit faire la division`() {
        // « 90 min » demande un calcul, « 1 h 30 » non.
        assertEquals("1 h 30", captureIntervalLabel(CaptureInterval.ofMinutes(90)))
        assertEquals("2 h 5", captureIntervalLabel(CaptureInterval.ofMinutes(125)))
    }
}
