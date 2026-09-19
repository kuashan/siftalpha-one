package com.siftalpha.studio.runtime

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
import com.siftalpha.studio.MainActivity
import com.siftalpha.studio.R

/**
 * Android process-liveness lease for user-started app-private Runtime sessions.
 *
 * This service does not execute projects, own PIDs, stop sessions, or supervise Runtime state.
 * Internal Runtime implementations keep their existing lifecycle; a unique session lease only
 * keeps the hosting app process out of the cached/frozen state while that session is active.
 */
class InternalRuntimeForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        runningService = this
        ensureChannel()
        promote(projects.size().coerceAtLeast(1))
        if (projects.isEmpty()) stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshNotification()
        if (projects.isEmpty()) stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (runningService === this) runningService = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.internal_runtime_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun refreshNotification() {
        val count = projects.size()
        if (count <= 0) return
        promote(count)
    }

    private fun promote(count: Int) {
        val notification = notification(count)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(count: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.siftalpha_launcher)
            .setContentTitle(getString(R.string.internal_runtime_notification_title))
            .setContentText(getString(R.string.internal_runtime_notification_text, count))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "siftalpha_internal_runtime"
        private const val NOTIFICATION_ID = 0x5341
        private val projects = InternalRuntimeProjectSet()

        @Volatile
        private var runningService: InternalRuntimeForegroundService? = null

        fun acquire(context: Context, sessionLeaseId: String) {
            require(sessionLeaseId.isNotBlank())
            if (!projects.acquire(sessionLeaseId)) return
            try {
                val service = runningService
                if (service != null) {
                    service.refreshNotification()
                    return
                }
                val appContext = context.applicationContext
                appContext.startForegroundService(
                    Intent(appContext, InternalRuntimeForegroundService::class.java),
                )
            } catch (error: Throwable) {
                // A failed service start must not leave a phantom lease that prevents the next
                // legitimate Internal Runtime session from starting foreground protection.
                projects.release(sessionLeaseId)
                throw error
            }
        }

        fun release(context: Context, sessionLeaseId: String) {
            if (!projects.release(sessionLeaseId)) return
            val appContext = context.applicationContext
            if (projects.isEmpty()) {
                appContext.stopService(Intent(appContext, InternalRuntimeForegroundService::class.java))
            } else {
                runningService?.refreshNotification()
            }
        }
    }
}

internal class InternalRuntimeProjectSet {
    private val ids = linkedSetOf<String>()

    @Synchronized
    fun acquire(id: String): Boolean {
        require(id.isNotBlank())
        return ids.add(id)
    }

    @Synchronized
    fun release(id: String): Boolean = ids.remove(id)

    @Synchronized
    fun size(): Int = ids.size

    @Synchronized
    fun isEmpty(): Boolean = ids.isEmpty()
}
