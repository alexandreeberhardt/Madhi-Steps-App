package com.madhi.tracker.adapter.input.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.madhi.tracker.R
import com.madhi.tracker.application.usecase.RunScheduledCapture
import com.madhi.tracker.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * Le service ne contient aucune règle métier : il démarre, affiche sa
 * notification, et délègue chaque capture à [RunScheduledCapture].
 *
 * Son rôle réel n'est pas d'exécuter du code, c'est d'exister. Tant qu'un
 * service de premier plan de type `location` tourne, le processus reste
 * vivant et conserve le droit d'accéder à la localisation en arrière-plan.
 */
@AndroidEntryPoint
class TrackingForegroundService : Service() {

    @Inject
    lateinit var runScheduledCapture: RunScheduledCapture

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Une seule acquisition à la fois : deux GPS simultanés ne servent à rien. */
    private val captureLock = Mutex()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundIfNeeded()

        if (intent?.action == ACTION_CAPTURE) {
            capture()
        }

        // START_STICKY demande au système de relancer le service s'il a été
        // tué. MIUI l'ignore souvent ; le watchdog WorkManager prend alors le
        // relais (ADR-007 §3.2). Cela ne coûte rien de le demander.
        return START_STICKY
    }

    private fun capture() {
        scope.launch {
            // Le service ne garantit pas que le CPU reste éveillé pendant
            // l'acquisition : sans ce verrou, l'appareil peut se rendormir au
            // milieu d'un fix et le laisser inachevé.
            val wakeLock = acquireWakeLock()
            try {
                captureLock.withLock { runScheduledCapture() }
            } catch (e: Exception) {
                // Une capture ratée ne doit jamais tuer le service : la
                // prochaine alarme est déjà programmée par le use case.
            } finally {
                if (wakeLock?.isHeld == true) wakeLock.release()
            }
        }
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? {
        val power = ContextCompat.getSystemService(this, PowerManager::class.java) ?: return null
        return power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            // Le délai est une ceinture de sécurité : si une exception
            // échappait au bloc finally, le système relâcherait quand même.
            acquire(WAKE_LOCK_TIMEOUT.inWholeMilliseconds)
        }
    }

    private fun startForegroundIfNeeded() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            },
        )
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setSmallIcon(R.drawable.ic_tracking_notification)
            .setOngoing(true)
            // Discrète mais lisible : c'est le seul indicateur permanent que
            // le suivi fonctionne, il ne doit ni déranger ni disparaître.
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.tracking_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "MadhiTracker:capture"
        private val WAKE_LOCK_TIMEOUT = 2.minutes

        const val ACTION_START = "com.madhi.tracker.action.START"
        const val ACTION_CAPTURE = "com.madhi.tracker.action.CAPTURE"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, intent(context, ACTION_START))
        }

        fun requestCapture(context: Context) {
            ContextCompat.startForegroundService(context, intent(context, ACTION_CAPTURE))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrackingForegroundService::class.java))
        }

        private fun intent(context: Context, action: String) =
            Intent(context, TrackingForegroundService::class.java).apply { this.action = action }
    }
}
