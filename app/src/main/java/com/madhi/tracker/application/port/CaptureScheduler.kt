package com.madhi.tracker.application.port

import kotlin.time.Duration

/**
 * Le métronome d'acquisition. Implémenté par AlarmManager, mais les use
 * cases n'ont pas à le savoir : ils expriment « prochaine capture dans X ».
 */
interface CaptureScheduler {
    fun scheduleNext(delay: Duration)
    fun cancel()

    /** Les alarmes exactes peuvent être refusées par le système (Android 12+). */
    fun canScheduleExactly(): Boolean
}
