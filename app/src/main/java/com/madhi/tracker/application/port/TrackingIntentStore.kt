package com.madhi.tracker.application.port

import com.madhi.tracker.domain.model.CaptureInterval
import com.madhi.tracker.domain.model.TrackingIntent
import kotlinx.coroutines.flow.Flow

/**
 * L'intention survit au processus, au redémarrage et à la mise à jour de
 * l'APK. C'est elle qui distingue « application fermée » de « suivi arrêté ».
 */
interface TrackingIntentStore {
    suspend fun read(): TrackingIntent
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setCaptureInterval(interval: CaptureInterval)
    fun observe(): Flow<TrackingIntent>
}
