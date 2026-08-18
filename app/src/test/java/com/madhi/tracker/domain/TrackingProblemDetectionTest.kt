package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.TrackingProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingProblemDetectionTest {

    private fun detect(
        foreground: Boolean = true,
        background: Boolean = true,
        notifications: Boolean = true,
        locationEnabled: Boolean = true,
        batteryExempt: Boolean = true,
        exactAlarms: Boolean = true,
        autostartBlocked: Boolean = false,
        activated: Boolean = true,
        authFailed: Boolean = false,
    ) = TrackingProblemDetection.detect(
        hasForegroundLocationPermission = foreground,
        hasBackgroundLocationPermission = background,
        hasNotificationPermission = notifications,
        isLocationEnabled = locationEnabled,
        isIgnoringBatteryOptimizations = batteryExempt,
        canScheduleExactAlarms = exactAlarms,
        autostartBlocked = autostartBlocked,
        deviceActivated = activated,
        authenticationFailed = authFailed,
    )

    @Test
    fun `un systeme sain ne signale rien`() {
        assertTrue(detect().isEmpty())
    }

    @Test
    fun `chaque manque produit son probleme`() {
        assertEquals(listOf(TrackingProblem.DEVICE_NOT_ACTIVATED), detect(activated = false))
        assertEquals(listOf(TrackingProblem.LOCATION_PERMISSION_MISSING), detect(foreground = false))
        assertEquals(listOf(TrackingProblem.BACKGROUND_LOCATION_PERMISSION_MISSING), detect(background = false))
        assertEquals(listOf(TrackingProblem.LOCATION_DISABLED), detect(locationEnabled = false))
        assertEquals(listOf(TrackingProblem.AUTOSTART_BLOCKED), detect(autostartBlocked = true))
        assertEquals(listOf(TrackingProblem.BATTERY_OPTIMIZATION_ENABLED), detect(batteryExempt = false))
        assertEquals(listOf(TrackingProblem.EXACT_ALARM_NOT_PERMITTED), detect(exactAlarms = false))
        assertEquals(listOf(TrackingProblem.AUTHENTICATION_FAILED), detect(authFailed = true))
        assertEquals(listOf(TrackingProblem.NOTIFICATIONS_BLOCKED), detect(notifications = false))
    }

    @Test
    fun `l'ordre place toujours le plus urgent en premier`() {
        val problems = detect(
            batteryExempt = false,
            foreground = false,
            notifications = false,
            activated = false,
        )

        assertEquals(TrackingProblem.DEVICE_NOT_ACTIVATED, problems.first())
    }

    @Test
    fun `un token refuse est un probleme visible, pas une statistique`() {
        // Les points s'accumulent sans qu'aucun ne parte : la voyageuse doit
        // le savoir avant que le backlog atteigne plusieurs milliers.
        val problems = detect(authFailed = true)

        assertTrue(problems.single().causesDataLoss)
    }

    @Test
    fun `une notification bloquee n'est pas classee comme perte de donnees`() {
        assertFalse(detect(notifications = false).single().causesDataLoss)
    }
}
