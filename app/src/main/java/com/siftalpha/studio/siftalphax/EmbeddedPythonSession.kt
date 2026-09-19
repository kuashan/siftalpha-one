package com.siftalpha.studio.siftalphax

import android.content.Context
import android.util.Log
import com.siftalpha.studio.runtime.InternalRuntimeForegroundService
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-scoped manager for the PoC. It allows one active interpreter session at a time and
 * retains the last terminal result while the app process remains alive.
 */

internal object EmbeddedPythonForegroundLeasePolicy {
    fun shouldRelease(
        current: EmbeddedPythonSnapshot,
        sessionId: String,
        generation: Long,
    ): Boolean {
        val replacedByNewSession =
            current.sessionId.isNotBlank() &&
                (current.sessionId != sessionId || current.generation != generation)
        val thisSessionTerminal =
            current.sessionId == sessionId &&
                current.generation == generation &&
                EmbeddedPythonStatePolicy.isTerminal(current.state)
        return replacedByNewSession || thisSessionTerminal
    }
}

class EmbeddedPythonSession private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val nextGeneration = AtomicLong(0L)
    private val foregroundLeaseMonitor = Executors.newSingleThreadExecutor()
    private var boundEnvironmentKey: String? = null

    @Synchronized
    fun snapshot(): EmbeddedPythonSnapshot =
        EmbeddedPythonSnapshotParser.parse(EmbeddedPythonBridge.nativeSnapshot())

    @Synchronized
    fun start(scenario: EmbeddedPythonScenario): EmbeddedPythonSnapshot =
        start(scenario.fixture)

    @Synchronized
    fun start(fixture: EmbeddedPythonProjectFixture): EmbeddedPythonSnapshot {
        val current = snapshot()
        check(EmbeddedPythonStatePolicy.canStart(current.state)) {
            "Only one embedded Python session may be active; current state is " + current.state
        }
        val sessionId = "siftalpha-x-" + UUID.randomUUID()
        val generation = nextGeneration.incrementAndGet()
        val home = prepareRuntime()
        val stagedRoot = EmbeddedPythonFiles.stageProjectFixture(appContext, fixture, sessionId)
        val spec = EmbeddedPythonExecutionSpec(
            projectIdentity = fixture.projectIdentity,
            executionRoot = stagedRoot,
            entrypoint = fixture.entrypoint,
            workingDirectory = fixture.workingDirectory,
            runtimeKind = EmbeddedPythonRuntimeKind.CPYTHON,
            sessionId = sessionId,
            generation = generation,
        )
        return start(spec, home)
    }

    /** Prepare the process-scoped CPython assets before M creates a project staging copy. */
    @Synchronized
    fun prepareRuntime(): File = EmbeddedPythonFiles.prepare(appContext)

    /** Start an explicit M-provided project target using the same R/native path as fixtures. */
    @Synchronized
    fun start(
        projectIdentity: String,
        executionRoot: File,
        entrypoint: String,
        workingDirectory: String = ".",
        environmentSitePackages: File? = null,
        environmentKey: String? = null,
    ): EmbeddedPythonSnapshot {
        val current = snapshot()
        check(EmbeddedPythonStatePolicy.canStart(current.state)) {
            "Only one embedded Python session may be active; current state is " + current.state
        }
        val sessionId = "siftalpha-x-" + UUID.randomUUID()
        val generation = nextGeneration.incrementAndGet()
        val home = prepareRuntime()
        val spec = EmbeddedPythonExecutionSpec(
            projectIdentity = projectIdentity,
            executionRoot = executionRoot,
            entrypoint = entrypoint,
            workingDirectory = workingDirectory,
            runtimeKind = EmbeddedPythonRuntimeKind.CPYTHON,
            sessionId = sessionId,
            generation = generation,
            environmentSitePackages = environmentSitePackages,
            environmentKey = environmentKey,
        )
        return start(spec, home)
    }

    private fun start(
        spec: EmbeddedPythonExecutionSpec,
        home: File,
    ): EmbeddedPythonSnapshot {
        val invalid = spec.nativeValidationErrors()
        check(invalid.isEmpty()) {
            "Invalid embedded Python execution specification: " + invalid.joinToString(",")
        }
        val requestedEnvironmentKey = spec.environmentKey
        val existingEnvironmentKey = boundEnvironmentKey
        if (requestedEnvironmentKey != null) {
            check(existingEnvironmentKey == null || existingEnvironmentKey == requestedEnvironmentKey) {
                "EMBEDDED_R_PROCESS_ENVIRONMENT_RESTART_REQUIRED"
            }
        }
        val foreground = try {
            InternalRuntimeForegroundService.acquireAndAwaitReady(appContext, spec.sessionId)
        } catch (error: Throwable) {
            throw IllegalStateException("INTERNAL_RUNTIME_FOREGROUND_SERVICE_FAILED", error)
        }

        val nativeStarted = runCatching {
            EmbeddedPythonBridge.nativeStart(
                home.absolutePath,
                spec.projectIdentity,
                spec.executionRoot.absolutePath,
                spec.environmentSitePackages?.absolutePath.orEmpty(),
                spec.entrypointFile.absolutePath,
                spec.workingDirectoryFile.absolutePath,
                spec.sessionId,
                spec.generation,
            )
        }.getOrElse { error ->
            InternalRuntimeForegroundService.release(appContext, spec.sessionId)
            throw error
        }
        if (!nativeStarted) {
            InternalRuntimeForegroundService.release(appContext, spec.sessionId)
            error("Embedded CPython did not accept the session")
        }
        monitorForegroundLease(spec.sessionId, spec.generation)

        if (requestedEnvironmentKey != null && boundEnvironmentKey == null) {
            boundEnvironmentKey = requestedEnvironmentKey
        }
        Log.i(
            TAG,
            "SIFTALPHA_X_ENGINE=CPYTHON SIFTALPHA_X_TERMUX=NOT_USED " +
                "SIFTALPHA_X_RUN_COMMAND=NOT_USED SIFTALPHA_X_PROOT=NOT_USED " +
                "SIFTALPHA_X_PROJECT_ID=" + spec.projectIdentity + " " +
                "SIFTALPHA_X_ENVIRONMENT_KEY=" + spec.environmentKey.orEmpty() + " " +
                "SIFTALPHA_X_SESSION_ID=" + spec.sessionId + " " +
                "SIFTALPHA_X_GENERATION=" + spec.generation + " " +
                "SIFTALPHA_X_FGS_ACTIVE=" + (if (foreground.foregroundActive) "YES" else "NO") + " " +
                "SIFTALPHA_X_WAKE_LOCK_HELD=" + (if (foreground.wakeLockHeld) "YES" else "NO") + " " +
                "SIFTALPHA_X_RUNTIME_LAUNCH_AFTER_FGS=YES " +
                "SIFTALPHA_X_SERVICE_PID=" + (foreground.servicePid ?: -1),
        )
        return snapshot()
    }

    private fun monitorForegroundLease(sessionId: String, generation: Long) {
        foregroundLeaseMonitor.execute {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val current = runCatching { snapshot() }.getOrNull()
                    if (
                        current != null &&
                        EmbeddedPythonForegroundLeasePolicy.shouldRelease(
                            current = current,
                            sessionId = sessionId,
                            generation = generation,
                        )
                    ) {
                        break
                    }
                    Thread.sleep(FOREGROUND_LEASE_POLL_MS)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                InternalRuntimeForegroundService.release(appContext, sessionId)
            }
        }
    }

    @Synchronized
    fun requestStop(): Boolean = EmbeddedPythonBridge.nativeRequestStop()

    companion object {
        private const val TAG = "SiftAlphaX"
        private const val FOREGROUND_LEASE_POLL_MS = 250L

        @Volatile
        private var sharedInstance: EmbeddedPythonSession? = null

        fun shared(context: Context): EmbeddedPythonSession =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: EmbeddedPythonSession(context).also { sharedInstance = it }
            }
    }
}
