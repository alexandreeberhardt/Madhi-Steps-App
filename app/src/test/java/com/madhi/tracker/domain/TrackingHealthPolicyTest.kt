package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.TrackingHealth
import com.madhi.tracker.domain.model.TrackingIntent
import com.madhi.tracker.domain.model.TrackingProblem
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingHealthPolicyTest {

    private val tracking = TrackingIntent(enabled = true, captureInterval = CaptureInterval.FIVE)
    private val stopped = tracking.copy(enabled = false)

    private fun healthOf(
        intent: TrackingIntent = tracking,
        problems: List<TrackingProblem> = emptyList(),
        isOnline: Boolean = true,
    ) = TrackingHealthPolicy.evaluate(
        intent = intent,
        problems = problems,
        isOnline = isOnline,
        pendingCount = 0,
        lastPointAt = null,
    ).health

    @Test
    fun `suivi actif quand tout va bien`() {
        assertEquals(TrackingHealth.ACTIVE, healthOf())
    }

    @Test
    fun `hors ligne n'est pas une anomalie, c'est le mode normal du voyage`() {
        assertEquals(TrackingHealth.OFFLINE, healthOf(isOnline = false))
    }

    @Test
    fun `arret volontaire prime sur tout le reste`() {
        val health = healthOf(
            intent = stopped,
            problems = listOf(TrackingProblem.LOCATION_PERMISSION_MISSING),
            isOnline = false,
        )
        assertEquals(TrackingHealth.STOPPED, health)
    }

    @Test
    fun `un probleme qui fait perdre des positions demande une action`() {
        val health = healthOf(problems = listOf(TrackingProblem.AUTOSTART_BLOCKED))
        assertEquals(TrackingHealth.ACTION_REQUIRED, health)
    }

    @Test
    fun `une notification masquee ne declenche pas d'alerte rouge`() {
        // Le suivi continue de fonctionner. Afficher du rouge en permanence
        // apprendrait a ignorer le rouge.
        assertEquals(TrackingHealth.ACTIVE, healthOf(problems = listOf(TrackingProblem.NOTIFICATIONS_BLOCKED)))
    }

    @Test
    fun `une action necessaire prime sur le hors ligne`() {
        val health = healthOf(
            problems = listOf(TrackingProblem.LOCATION_DISABLED),
            isOnline = false,
        )
        assertEquals(TrackingHealth.ACTION_REQUIRED, health)
    }

    @Test
    fun `l'ecran principal ne montre que le probleme le plus urgent`() {
        val status = TrackingHealthPolicy.evaluate(
            intent = tracking,
            problems = listOf(
                TrackingProblem.BATTERY_OPTIMIZATION_ENABLED,
                TrackingProblem.LOCATION_PERMISSION_MISSING,
                TrackingProblem.AUTOSTART_BLOCKED,
            ),
            isOnline = true,
            pendingCount = 0,
            lastPointAt = null,
        )
        assertEquals(TrackingProblem.LOCATION_PERMISSION_MISSING, status.mostUrgentProblem)
    }

    @Test
    fun `les problemes en double sont fusionnes`() {
        val status = TrackingHealthPolicy.evaluate(
            intent = tracking,
            problems = listOf(TrackingProblem.AUTOSTART_BLOCKED, TrackingProblem.AUTOSTART_BLOCKED),
            isOnline = true,
            pendingCount = 0,
            lastPointAt = null,
        )
        assertEquals(1, status.problems.size)
    }
}
