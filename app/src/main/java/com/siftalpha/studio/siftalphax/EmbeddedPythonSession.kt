package com.siftalpha.studio.siftalphax

import android.content.Context
import android.util.Log
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-scoped manager for the PoC. It allows one active interpreter session at a time and
 * retains the last terminal result while the app process remains alive.
 */
class EmbeddedPythonSession private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val nextGeneration = AtomicLong(0L)

    @Synchronized
    fun snapshot(): EmbeddedPythonSnapshot =
        EmbeddedPythonSnapshotParser.parse(EmbeddedPythonBridge.nativeSnapshot())

    @Synchronized
    fun start(scenario: EmbeddedPythonScenario): EmbeddedPythonSnapshot {
        val current = snapshot()
        check(EmbeddedPythonStatePolicy.canStart(current.state)) {
            "Only one embedded Python session may be active; current state is ${current.state}"
        }
        val home = EmbeddedPythonFiles.prepare(appContext)
        val sessionId = "siftalpha-x-${UUID.randomUUID()}"
        val generation = nextGeneration.incrementAndGet()
        check(EmbeddedPythonBridge.nativeStart(home.absolutePath, scenario.script, sessionId, generation)) {
            "Embedded CPython did not accept the session"
        }
        Log.i(
            TAG,
            "SIFTALPHA_X_ENGINE=CPYTHON SIFTALPHA_X_TERMUX=NOT_USED " +
                "SIFTALPHA_X_RUN_COMMAND=NOT_USED SIFTALPHA_X_PROOT=NOT_USED " +
                "SIFTALPHA_X_SESSION_ID=$sessionId SIFTALPHA_X_GENERATION=$generation",
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
