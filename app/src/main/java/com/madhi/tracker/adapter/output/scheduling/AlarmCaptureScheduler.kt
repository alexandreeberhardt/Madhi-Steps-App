package com.madhi.tracker.adapter.output.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.madhi.tracker.adapter.input.tracking.CaptureAlarmReceiver
import com.madhi.tracker.application.port.CaptureScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * Le métronome du suivi.
 *
 * `setExactAndAllowWhileIdle` est le seul mécanisme qui traverse le Doze de
 * façon fiable. Un foreground service ne suffit pas : il maintient le
 * processus vivant mais n'empêche pas la suspension du CPU, si bien qu'une
 * coroutine qui dort dérive de plusieurs dizaines de minutes en veille
 * profonde (ADR-002).
 *
 * La base de temps est `ELAPSED_REALTIME_WAKEUP` et non `RTC` : un
 * changement d'heure — passage de fuseau en traversant l'Europe, correction
 * NTP — ne doit pas décaler ni annuler la prochaine acquisition.
 */
@Singleton
class AlarmCaptureScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CaptureScheduler {

    private val alarmManager: AlarmManager?
        get() = ContextCompat.getSystemService(context, AlarmManager::class.java)

    override fun scheduleNext(delay: Duration) {
        val alarms = alarmManager ?: return
        val triggerAt = SystemClock.elapsedRealtime() + delay.inWholeMilliseconds

        // Une alarme exacte peut être refusée par le système. Le repli n'est
        // pas équivalent — la cadence dérive — mais un suivi imprécis vaut
        // mieux qu'un suivi arrêté.
        if (canScheduleExactly()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent())
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent())
        }
    }

    override fun cancel() {
        alarmManager?.cancel(pendingIntent())
    }

    override fun canScheduleExactly(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager?.canScheduleExactAlarms() == true
    }

    /**
     * `FLAG_UPDATE_CURRENT` avec un code de requête constant : il n'existe à
     * tout moment qu'une seule alarme de capture. Reprogrammer remplace la
     * précédente au lieu d'en empiler une seconde.
     */
    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, CaptureAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 1001
    }
}
