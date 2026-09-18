package com.siftalpha.studio.siftalphax

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-scoped manager for the PoC. It allows one active interpreter session at a time and
 * retains the last terminal result while the app process remains alive.
 */
class EmbeddedPythonSession private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val nextGeneration = AtomicLong(0L)
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
        check(
            EmbeddedPythonBridge.nativeStart(
                home.absolutePath,
                spec.projectIdentity,
                spec.executionRoot.absolutePath,
                spec.environmentSitePackages?.absolutePath.orEmpty(),
                spec.entrypointFile.absolutePath,
                spec.workingDirectoryFile.absolutePath,
                spec.sessionId,
                spec.generation,
            ),
        ) {
            "Embedded CPython did not accept the session"
        }
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
                "SIFTALPHA_X_GENERATION=" + spec.generation,
        )
        return snapshot()
    }

    @Synchronized
    fun requestStop(): Boolean = EmbeddedPythonBridge.nativeRequestStop()

    companion object {
        private const val TAG = "SiftAlphaX"
        @Volatile
        private var sharedInstance: EmbeddedPythonSession? = null

        fun shared(context: Context): EmbeddedPythonSession =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: EmbeddedPythonSession(context).also { sharedInstance = it }
            }
    }
}
