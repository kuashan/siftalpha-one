package com.siftalpha.studio.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.ActivityManager
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
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * Android foreground owner for user-started app-private Runtime sessions.
 *
 * Embedded CPython can continue to use a foreground lease because its interpreter lives inside
 * the app process. Internal Alpine transfers its child Process lifecycle to this service: the
 * service-owned executor launches the child, the service holds Process/stdout/stderr handles,
 * monitors waitFor(), and routes project-scoped STOP requests.
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

data class InternalRuntimeOwnedProcess(
    val process: java.lang.Process,
    val runtimePid: Int?,
    val stdoutFile: File?,
    val stderrFile: File?,
    val telemetryFile: File? = null,
    val terminate: () -> Unit,
    val cleanup: () -> Unit,
)

data class InternalRuntimeOwnedStart(
    val foreground: InternalRuntimeForegroundDiagnostics,
    val runtimePid: Int?,
)

data class InternalRuntimeOwnershipDiagnostics(
    val owner: String?,
    val sessionOwnerServicePid: Int?,
    val processHeld: Boolean,
    val monitorActive: Boolean,
    val runtimePid: Int?,
    val stopRequested: Boolean,
)

internal object InternalRuntimeForegroundReadyPolicy {
    fun isReady(
        leaseActive: Boolean,
        foregroundActive: Boolean,
        wakeLockHeld: Boolean,
    ): Boolean = leaseActive && foregroundActive && wakeLockHeld
}

internal object InternalRuntimeOwnershipPolicy {
    fun matches(
        ownedProjectIdentity: String,
        ownedSessionLeaseId: String,
        requestedProjectIdentity: String,
        requestedSessionLeaseId: String,
    ): Boolean =
        ownedProjectIdentity == requestedProjectIdentity &&
            ownedSessionLeaseId == requestedSessionLeaseId
}

internal object InternalRuntimeBackgroundTelemetry {
    data class ProcSample(
        val state: String?,
        val cpuTicks: Long?,
        val cgroup: String?,
    )

    fun parseProcStat(stat: String): Pair<String?, Long?> {
        val close = stat.lastIndexOf(')')
        if (close < 0 || close + 2 >= stat.length) return null to null
        val fields = stat.substring(close + 2).trim().split(Regex("\\s+"))
        val state = fields.getOrNull(0)?.takeIf(String::isNotBlank)
        val userTicks = fields.getOrNull(11)?.toLongOrNull()
        val systemTicks = fields.getOrNull(12)?.toLongOrNull()
        val cpuTicks = if (userTicks != null && systemTicks != null) userTicks + systemTicks else null
        return state to cpuTicks
    }

    fun latestLoopbackUrl(text: String): String? =
        text.lineSequence()
            .filter { it.contains("SIFTALPHA_WEB_URL=") }
            .mapNotNull { RuntimeWebUrl.extractLocalHttpUrl(it) }
            .lastOrNull()

    fun readProc(pid: Int?): ProcSample {
        if (pid == null || pid <= 0) return ProcSample(null, null, null)
        val proc = File("/proc/${pid}")
        if (!proc.exists()) return ProcSample(null, null, null)
        val stat = runCatching { File(proc, "stat").readText() }.getOrNull().orEmpty()
        val (state, ticks) = parseProcStat(stat)
        val cgroup = runCatching { File(proc, "cgroup").readLines() }
            .getOrNull()
            ?.joinToString(";") { it.trim() }
            ?.replace(Regex("\\s+"), "_")
            ?.take(768)
        return ProcSample(state, ticks, cgroup)
    }

    fun readTail(file: File?, maxBytes: Int = 64 * 1024): String {
        if (file == null || !file.isFile || maxBytes <= 0) return ""
        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                val length = input.length()
                val count = minOf(length, maxBytes.toLong()).toInt()
                input.seek((length - count).coerceAtLeast(0L))
                val bytes = ByteArray(count)
                input.readFully(bytes)
                bytes.toString(Charsets.UTF_8)
            }
        }.getOrDefault("")
    }

    fun appendBounded(file: File?, text: String, maxBytes: Int = 256 * 1024) {
        if (file == null || text.isBlank() || maxBytes <= 0) return
        runCatching {
            file.parentFile?.mkdirs()
            if (file.isFile && file.length() > maxBytes) {
                val tail = readTail(file, maxBytes / 2)
                file.writeText(tail)
            }
            file.appendText(text)
        }
    }
}

class InternalRuntimeForegroundService : Service() {

    private class OwnedSession(
        val sessionLeaseId: String,
        val projectIdentity: String,
        val ownedProcess: InternalRuntimeOwnedProcess,
        @Volatile var stopRequested: Boolean = false,
        @Volatile var monitorActive: Boolean = true,
        @Volatile var lastSampleCpuTicks: Long? = null,
    )

    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundActive = false
    private val ownedSessions = ConcurrentHashMap<String, OwnedSession>()
    private val ownershipExecutor = Executors.newCachedThreadPool()

    @Volatile
    private var telemetrySamplingInFlight = false
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (projects.isEmpty()) return
            lastHeartbeatAtEpochMs = System.currentTimeMillis()
            scheduleBackgroundTelemetrySample()
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
        ownershipExecutor.shutdown()
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

    private fun scheduleBackgroundTelemetrySample() {
        if (telemetrySamplingInFlight || ownedSessions.isEmpty()) return
        telemetrySamplingInFlight = true
        ownershipExecutor.execute {
            try {
                sampleOwnedSessions()
            } finally {
                telemetrySamplingInFlight = false
            }
        }
    }

    private fun sampleOwnedSessions() {
        val now = System.currentTimeMillis()
        val powerManager = getSystemService(PowerManager::class.java)
        val importanceInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(importanceInfo)
        val serviceProc = InternalRuntimeBackgroundTelemetry.readProc(Process.myPid())

        ownedSessions.values.forEach { owned ->
            val runtimePid = owned.ownedProcess.runtimePid
            val runtimeProc = InternalRuntimeBackgroundTelemetry.readProc(runtimePid)
            val previousTicks = owned.lastSampleCpuTicks
            val cpuDelta = if (
                previousTicks != null &&
                runtimeProc.cpuTicks != null &&
                runtimeProc.cpuTicks >= previousTicks
            ) {
                runtimeProc.cpuTicks - previousTicks
            } else {
                null
            }
            owned.lastSampleCpuTicks = runtimeProc.cpuTicks

            val stdout = owned.ownedProcess.stdoutFile
            val stdoutTail = InternalRuntimeBackgroundTelemetry.readTail(stdout)
            val webUrl = InternalRuntimeBackgroundTelemetry.latestLoopbackUrl(stdoutTail)
            val webReachable = webUrl?.let {
                RuntimeWebEndpointProbe.isListening(it, BACKGROUND_WEB_PROBE_TIMEOUT_MS)
            }
            val batteryIgnored = runCatching {
                powerManager.isIgnoringBatteryOptimizations(packageName)
            }.getOrNull()

            val sample = buildString {
                appendLine("=== SiftAlpha Background Continuity Sample ===")
                appendLine("SIFTALPHA_X_BACKGROUND_SAMPLE_EPOCH_MS=${now}")
                appendLine("SIFTALPHA_X_BACKGROUND_PROJECT_ID=${owned.projectIdentity}")
                appendLine("SIFTALPHA_X_BACKGROUND_SESSION_ID=${owned.sessionLeaseId}")
                appendLine("SIFTALPHA_X_BACKGROUND_SERVICE_PID=${Process.myPid()}")
                appendLine("SIFTALPHA_X_BACKGROUND_SERVICE_IMPORTANCE=${importanceInfo.importance}")
                appendLine("SIFTALPHA_X_BACKGROUND_SERVICE_PROC_STATE=${serviceProc.state ?: "UNKNOWN"}")
                appendLine("SIFTALPHA_X_BACKGROUND_SERVICE_CGROUP=${serviceProc.cgroup ?: "UNKNOWN"}")
                appendLine("SIFTALPHA_X_BACKGROUND_RUNTIME_PID=${runtimePid ?: -1}")
                appendLine("SIFTALPHA_X_BACKGROUND_RUNTIME_PID_ALIVE=${yesNo(runtimePid != null && File("/proc/${runtimePid}").exists())}")
                appendLine("SIFTALPHA_X_BACKGROUND_RUNTIME_PROC_STATE=${runtimeProc.state ?: "UNKNOWN"}")
                appendLine("SIFTALPHA_X_BACKGROUND_RUNTIME_CPU_TICKS=${runtimeProc.cpuTicks ?: -1L}")
                appendLine("SIFTALPHA_X_BACKGROUND_RUNTIME_CPU_TICKS_DELTA=${cpuDelta ?: -1L}")
                appendLine("SIFTALPHA_X_BACKGROUND_RUNTIME_CGROUP=${runtimeProc.cgroup ?: "UNKNOWN"}")
                appendLine("SIFTALPHA_X_BACKGROUND_STDOUT_BYTES=${stdout?.length() ?: -1L}")
                appendLine("SIFTALPHA_X_BACKGROUND_STDOUT_MTIME_MS=${stdout?.lastModified() ?: -1L}")
                appendLine("SIFTALPHA_X_BACKGROUND_WEB_URL=${webUrl ?: "NONE"}")
                appendLine(
                    "SIFTALPHA_X_BACKGROUND_WEB_LOOPBACK_REACHABLE=" + when (webReachable) {
                        true -> "YES"
                        false -> "NO"
                        null -> "UNKNOWN"
                    },
                )
                appendLine("SIFTALPHA_X_BACKGROUND_WAKE_LOCK_HELD=${yesNo(wakeLock?.isHeld == true)}")
                appendLine("SIFTALPHA_X_BACKGROUND_POWER_SAVE_MODE=${yesNo(powerManager.isPowerSaveMode)}")
                appendLine("SIFTALPHA_X_BACKGROUND_DEVICE_IDLE_MODE=${yesNo(powerManager.isDeviceIdleMode)}")
                appendLine(
                    "SIFTALPHA_X_BACKGROUND_BATTERY_OPTIMIZATION_IGNORED=" + when (batteryIgnored) {
                        true -> "YES"
                        false -> "NO"
                        null -> "UNKNOWN"
                    },
                )
                appendLine("=== End Background Continuity Sample ===")
            }
            InternalRuntimeBackgroundTelemetry.appendBounded(
                owned.ownedProcess.telemetryFile,
                sample,
            )
        }
    }

    private fun yesNo(value: Boolean): String = if (value) "YES" else "NO"

    private fun launchOwnedSession(
        sessionLeaseId: String,
        projectIdentity: String,
        foreground: InternalRuntimeForegroundDiagnostics,
        launcher: () -> InternalRuntimeOwnedProcess,
        onStarted: (InternalRuntimeOwnedStart) -> Unit,
        onFinished: (exitCode: Int, stopRequested: Boolean, finishedAtEpochMs: Long) -> Unit,
    ): InternalRuntimeOwnedStart {
        val future = ownershipExecutor.submit<InternalRuntimeOwnedStart> {
            launchOwnedSessionOnServiceExecutor(
                sessionLeaseId = sessionLeaseId,
                projectIdentity = projectIdentity,
                foreground = foreground,
                launcher = launcher,
                onStarted = onStarted,
                onFinished = onFinished,
            )
        }
        return try {
            future.get()
        } catch (interrupted: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            throw IllegalStateException("INTERNAL_RUNTIME_FOREGROUND_OWNERSHIP_INTERRUPTED", interrupted)
        } catch (wrapped: ExecutionException) {
            throw (wrapped.cause ?: wrapped)
        }
    }

    private fun launchOwnedSessionOnServiceExecutor(
        sessionLeaseId: String,
        projectIdentity: String,
        foreground: InternalRuntimeForegroundDiagnostics,
        launcher: () -> InternalRuntimeOwnedProcess,
        onStarted: (InternalRuntimeOwnedStart) -> Unit,
        onFinished: (exitCode: Int, stopRequested: Boolean, finishedAtEpochMs: Long) -> Unit,
    ): InternalRuntimeOwnedStart {
        require(sessionLeaseId.isNotBlank())
        require(projectIdentity.isNotBlank())
        check(projects.contains(sessionLeaseId)) { "INTERNAL_RUNTIME_FOREGROUND_LEASE_MISSING" }

        val launched = launcher()
        val owned = OwnedSession(
            sessionLeaseId = sessionLeaseId,
            projectIdentity = projectIdentity,
            ownedProcess = launched,
        )
        val previous = ownedSessions.putIfAbsent(sessionLeaseId, owned)
        if (previous != null) {
            runCatching { launched.terminate() }
            runCatching { launched.cleanup() }
            error("INTERNAL_RUNTIME_FOREGROUND_SESSION_ALREADY_OWNED")
        }

        val started = InternalRuntimeOwnedStart(
            foreground = foreground,
            runtimePid = launched.runtimePid,
        )
        try {
            onStarted(started)
            scheduleBackgroundTelemetrySample()
            ownershipExecutor.execute {
                val exitCode = runCatching { launched.process.waitFor() }.getOrElse { -1 }
                runCatching { launched.cleanup() }
                owned.monitorActive = false
                ownedSessions.remove(sessionLeaseId, owned)
                try {
                    onFinished(exitCode, owned.stopRequested, System.currentTimeMillis())
                } finally {
                    release(applicationContext, sessionLeaseId)
                }
            }
        } catch (error: Throwable) {
            owned.monitorActive = false
            owned.stopRequested = true
            ownedSessions.remove(sessionLeaseId, owned)
            runCatching { launched.terminate() }
            runCatching { launched.cleanup() }
            throw error
        }
        return started
    }

    private fun requestStopOwnedSession(
        projectIdentity: String,
        sessionLeaseId: String,
    ): Boolean {
        val owned = ownedSessions[sessionLeaseId] ?: return false
        if (!InternalRuntimeOwnershipPolicy.matches(
                ownedProjectIdentity = owned.projectIdentity,
                ownedSessionLeaseId = owned.sessionLeaseId,
                requestedProjectIdentity = projectIdentity,
                requestedSessionLeaseId = sessionLeaseId,
            )
        ) {
            return false
        }

        val shouldTerminate = synchronized(owned) {
            if (owned.stopRequested) {
                false
            } else {
                owned.stopRequested = true
                true
            }
        }
        if (!shouldTerminate) return true
        return runCatching {
            owned.ownedProcess.terminate()
            true
        }.getOrDefault(false)
    }

    private fun ownershipDiagnostics(sessionLeaseId: String): InternalRuntimeOwnershipDiagnostics {
        val owned = ownedSessions[sessionLeaseId]
        return InternalRuntimeOwnershipDiagnostics(
            owner = owned?.let { OWNER_FOREGROUND_SERVICE },
            sessionOwnerServicePid = owned?.let { Process.myPid() },
            processHeld = owned != null,
            monitorActive = owned?.monitorActive == true,
            runtimePid = owned?.ownedProcess?.runtimePid,
            stopRequested = owned?.stopRequested == true,
        )
    }

    companion object {
        private const val CHANNEL_ID = "siftalpha_internal_runtime"
        private const val NOTIFICATION_ID = 0x5341
        private const val WAKE_LOCK_TAG = "SiftAlpha:InternalRuntime"
        private const val READY_TIMEOUT_MS = 5_000L
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val BACKGROUND_WEB_PROBE_TIMEOUT_MS = 350
        const val OWNER_FOREGROUND_SERVICE = "FOREGROUND_SERVICE"
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

        fun launchAndOwn(
            context: Context,
            sessionLeaseId: String,
            projectIdentity: String,
            launcher: () -> InternalRuntimeOwnedProcess,
            onStarted: (InternalRuntimeOwnedStart) -> Unit,
            onFinished: (exitCode: Int, stopRequested: Boolean, finishedAtEpochMs: Long) -> Unit,
        ): InternalRuntimeOwnedStart {
            val foreground = acquireAndAwaitReady(context, sessionLeaseId)
            try {
                val service = runningService
                    ?: error("INTERNAL_RUNTIME_FOREGROUND_SERVICE_NOT_ACTIVE")
                return service.launchOwnedSession(
                    sessionLeaseId = sessionLeaseId,
                    projectIdentity = projectIdentity,
                    foreground = foreground,
                    launcher = launcher,
                    onStarted = onStarted,
                    onFinished = onFinished,
                )
            } catch (error: Throwable) {
                release(context, sessionLeaseId)
                throw error
            }
        }

        fun requestStopOwnedSession(
            projectIdentity: String,
            sessionLeaseId: String,
        ): Boolean =
            runningService?.requestStopOwnedSession(projectIdentity, sessionLeaseId) == true

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

        fun ownershipDiagnostics(sessionLeaseId: String): InternalRuntimeOwnershipDiagnostics =
            runningService?.ownershipDiagnostics(sessionLeaseId)
                ?: InternalRuntimeOwnershipDiagnostics(
                    owner = null,
                    sessionOwnerServicePid = null,
                    processHeld = false,
                    monitorActive = false,
                    runtimePid = null,
                    stopRequested = false,
                )

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
