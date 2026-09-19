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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import com.siftalpha.studio.MainActivity
import com.siftalpha.studio.R

/**
 * Android process-liveness lease for user-started app-private Runtime sessions.
 *
 * This service does not execute projects, own PIDs, stop sessions, or supervise Runtime state.
 * Internal Runtime implementations keep their existing lifecycle; a unique session lease only
 * keeps the hosting app process out of the cached/frozen state while that session is active.
 */
data class InternalRuntimeForegroundDiagnostics(
    val leaseActive: Boolean,
    val foregroundActive: Boolean,
    val wakeLockHeld: Boolean,
    val servicePid: Int?,
    val readyAtEpochMs: Long,
    val heartbeatAtEpochMs: Long,
) {
    val ready: Boolean
        get() = InternalRuntimeForegroundReadyPolicy.isReady(
            leaseActive = leaseActive,
            foregroundActive = foregroundActive,
            wakeLockHeld = wakeLockHeld,
        )
}

internal object InternalRuntimeForegroundReadyPolicy {
    fun isReady(
        leaseActive: Boolean,
        foregroundActive: Boolean,
        wakeLockHeld: Boolean,
    ): Boolean = leaseActive && foregroundActive && wakeLockHeld
}

class InternalRuntimeForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundActive = false
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (projects.isEmpty()) return
            lastHeartbeatAtEpochMs = System.currentTimeMillis()
            signalReadyChanged()
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        readyAtEpochMs = 0L
        lastHeartbeatAtEpochMs = 0L
        runningService = this
        ensureChannel()
        refreshNotification()
        if (projects.isEmpty()) stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshNotification()
        if (projects.isEmpty()) stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        foregroundActive = false
        releaseWakeLock()
        if (runningService === this) runningService = null
        signalReadyChanged()
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
        if (count <= 0) {
            signalReadyChanged()
            return
        }
        // Establish the Android foreground-service state before holding the CPU awake.
        // Runtime launchers wait for both facts before creating the actual Runtime process.
        promote(count)
        foregroundActive = true
        syncWakeLock()
        if (readyAtEpochMs <= 0L && wakeLock?.isHeld == true) {
            readyAtEpochMs = System.currentTimeMillis()
        }
        lastHeartbeatAtEpochMs = System.currentTimeMillis()
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
        signalReadyChanged()
    }

    private fun syncWakeLock() {
        val count = projects.size()
        if (!InternalRuntimePowerPolicy.shouldHoldWakeLock(count)) {
            releaseWakeLock()
            return
        }
        val current = wakeLock
        if (current?.isHeld == true) return
        val manager = getSystemService(PowerManager::class.java)
        val created = current ?: manager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG,
        ).apply {
            setReferenceCounted(false)
        }.also { wakeLock = it }
        if (!created.isHeld) created.acquire()
    }

    private fun releaseWakeLock() {
        val current = wakeLock ?: return
        if (current.isHeld) current.release()
        wakeLock = null
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
        private const val WAKE_LOCK_TAG = "SiftAlpha:InternalRuntime"
        private const val READY_TIMEOUT_MS = 5_000L
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private val projects = InternalRuntimeProjectSet()
        private val readyMonitor = Object()

        @Volatile
        private var runningService: InternalRuntimeForegroundService? = null

        @Volatile
        private var readyAtEpochMs = 0L

        @Volatile
        private var lastHeartbeatAtEpochMs = 0L

        fun acquire(context: Context, sessionLeaseId: String) {
            acquireAndAwaitReady(context, sessionLeaseId)
        }

        fun acquireAndAwaitReady(
            context: Context,
            sessionLeaseId: String,
            timeoutMs: Long = READY_TIMEOUT_MS,
        ): InternalRuntimeForegroundDiagnostics {
            require(sessionLeaseId.isNotBlank())
            require(timeoutMs > 0L)
            val added = projects.acquire(sessionLeaseId)
            try {
                val service = runningService
                if (service != null) {
                    service.refreshNotification()
                } else {
                    val appContext = context.applicationContext
                    appContext.startForegroundService(
                        Intent(appContext, InternalRuntimeForegroundService::class.java),
                    )
                }

                diagnostics(sessionLeaseId).takeIf { it.ready }?.let { return it }
                check(Looper.myLooper() != Looper.getMainLooper()) {
                    "INTERNAL_RUNTIME_FOREGROUND_READY_WAIT_REQUIRES_BACKGROUND_THREAD"
                }

                val deadline = SystemClock.elapsedRealtime() + timeoutMs
                synchronized(readyMonitor) {
                    while (true) {
                        diagnostics(sessionLeaseId).takeIf { it.ready }?.let { return it }
                        val remaining = deadline - SystemClock.elapsedRealtime()
                        if (remaining <= 0L) {
                            error("INTERNAL_RUNTIME_FOREGROUND_READY_TIMEOUT")
                        }
                        readyMonitor.wait(remaining)
                    }
                }
            } catch (error: Throwable) {
                if (added) {
                    projects.release(sessionLeaseId)
                    val appContext = context.applicationContext
                    if (projects.isEmpty()) {
                        appContext.stopService(
                            Intent(appContext, InternalRuntimeForegroundService::class.java),
                        )
                    } else {
                        runningService?.refreshNotification()
                    }
                }
                throw error
            }
        }

        fun diagnostics(sessionLeaseId: String): InternalRuntimeForegroundDiagnostics {
            val service = runningService
            return InternalRuntimeForegroundDiagnostics(
                leaseActive = projects.contains(sessionLeaseId),
                foregroundActive = service?.foregroundActive == true,
                wakeLockHeld = service?.wakeLock?.isHeld == true,
                servicePid = service?.let { Process.myPid() },
                readyAtEpochMs = readyAtEpochMs,
                heartbeatAtEpochMs = lastHeartbeatAtEpochMs,
            )
        }

        private fun signalReadyChanged() {
            synchronized(readyMonitor) {
                readyMonitor.notifyAll()
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
            signalReadyChanged()
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

    @Synchronized
    fun contains(id: String): Boolean = ids.contains(id)
}


internal object InternalRuntimePowerPolicy {
    fun shouldHoldWakeLock(activeLeaseCount: Int): Boolean {
        require(activeLeaseCount >= 0)
        return activeLeaseCount > 0
    }
}
