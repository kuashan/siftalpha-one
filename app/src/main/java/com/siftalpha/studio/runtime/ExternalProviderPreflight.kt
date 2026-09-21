package com.siftalpha.studio.runtime

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The small provider surface needed by the shared External Provider preflight.
 *
 * Android UI code may request the permission or open Termux, but it must not reimplement these
 * facts. TermuxBackend is the production implementation.
 */
interface ExternalProviderBridge {
    fun isTermuxInstalled(): Boolean
    fun hasRunCommandPermission(): Boolean
    fun execute(command: RuntimeCommand): Int
}

enum class ExternalProviderBridgeState {
    UNKNOWN,
    CHECKING,
    PASS,
    FAIL,
}

enum class ExternalProviderProbeResult {
    PASS,
    FAIL,
}

enum class ExternalProviderReadiness {
    TERMUX_NOT_INSTALLED,
    RUN_COMMAND_PERMISSION_REQUIRED,
    BRIDGE_CHECK_REQUIRED,
    BRIDGE_CHECKING,
    EXTERNAL_APPS_CONFIGURATION_REQUIRED,
    READY,
    UNAVAILABLE,
}

data class ExternalProviderFacts(
    val termuxInstalled: Boolean,
    val runCommandPermissionGranted: Boolean,
    val bridgeState: ExternalProviderBridgeState,
    val lastProbeAtEpochMs: Long? = null,
    val lastProbeExecutionId: Int? = null,
    val lastProbeResult: ExternalProviderProbeResult? = null,
    val detail: String? = null,
)

data class ExternalProviderPreflightResult(
    val readiness: ExternalProviderReadiness,
    val termuxInstalled: Boolean,
    val runCommandPermissionGranted: Boolean,
    val allowExternalApps: Boolean?,
    val bridgeResponsive: Boolean?,
    val lastProbeAtEpochMs: Long?,
    val probeExecutionId: Int? = null,
    val detail: String? = null,
) {
    val ready: Boolean
        get() = readiness == ExternalProviderReadiness.READY
}

interface ExternalProviderPreflightGate {
    fun ensureReady(): ExternalProviderPreflightResult
}

/** Pure readiness decision. It does not inspect Android, launch UI, or dispatch commands. */
object ExternalProviderPreflight {
    const val DEFAULT_PROBE_MAX_AGE_MS = 60_000L

    fun evaluate(
        facts: ExternalProviderFacts,
        nowEpochMs: Long,
        probeMaxAgeMs: Long = DEFAULT_PROBE_MAX_AGE_MS,
    ): ExternalProviderPreflightResult {
        if (!facts.termuxInstalled) {
            return result(facts, ExternalProviderReadiness.TERMUX_NOT_INSTALLED)
        }
        if (!facts.runCommandPermissionGranted) {
            return result(facts, ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED)
        }

        return when (facts.bridgeState) {
            ExternalProviderBridgeState.CHECKING ->
                result(facts, ExternalProviderReadiness.BRIDGE_CHECKING)

            ExternalProviderBridgeState.PASS -> {
                val probeFresh = facts.lastProbeAtEpochMs?.let { last ->
                    nowEpochMs >= last && nowEpochMs - last <= probeMaxAgeMs
                } == true
                if (probeFresh) {
                    result(
                        facts = facts,
                        readiness = ExternalProviderReadiness.READY,
                        allowExternalApps = true,
                        bridgeResponsive = true,
                    )
                } else {
                    result(facts, ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED)
                }
            }

            ExternalProviderBridgeState.FAIL -> result(
                facts = facts,
                readiness = ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED,
                allowExternalApps = false,
                bridgeResponsive = false,
            )

            ExternalProviderBridgeState.UNKNOWN ->
                result(facts, ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED)
        }
    }

    private fun result(
        facts: ExternalProviderFacts,
        readiness: ExternalProviderReadiness,
        allowExternalApps: Boolean? = when (facts.bridgeState) {
            ExternalProviderBridgeState.PASS -> true
            ExternalProviderBridgeState.FAIL -> false
            ExternalProviderBridgeState.UNKNOWN,
            ExternalProviderBridgeState.CHECKING,
            -> null
        },
        bridgeResponsive: Boolean? = when (facts.bridgeState) {
            ExternalProviderBridgeState.PASS -> true
            ExternalProviderBridgeState.FAIL -> false
            ExternalProviderBridgeState.UNKNOWN,
            ExternalProviderBridgeState.CHECKING,
            -> null
        },
    ): ExternalProviderPreflightResult = ExternalProviderPreflightResult(
        readiness = readiness,
        termuxInstalled = facts.termuxInstalled,
        runCommandPermissionGranted = facts.runCommandPermissionGranted,
        allowExternalApps = allowExternalApps,
        bridgeResponsive = bridgeResponsive,
        lastProbeAtEpochMs = facts.lastProbeAtEpochMs,
        probeExecutionId = facts.lastProbeExecutionId,
        detail = facts.detail,
    )
}

/**
 * The one shared persisted provider-readiness store. It stores facts from the latest probe, not a
 * permanent "ready" switch. Every PREPARE/RUN asks the coordinator to re-evaluate freshness.
 */
interface ExternalProviderReadinessStateStore {
    fun read(): ExternalProviderFacts
    fun recordHostFacts(termuxInstalled: Boolean, runCommandPermissionGranted: Boolean)
    fun markProbeDispatched(executionId: Int)
    fun recordProbe(
        executionId: Int,
        result: ExternalProviderProbeResult,
        atEpochMs: Long,
        detail: String? = null,
    )
    fun recordProbeDispatchFailure(atEpochMs: Long, detail: String)
}

class ExternalProviderReadinessStore internal constructor(
    private val prefs: SharedPreferences,
) : ExternalProviderReadinessStateStore {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    override fun read(): ExternalProviderFacts = ExternalProviderFacts(
        termuxInstalled = prefs.getBoolean(FIELD_TERMUX_INSTALLED, false),
        runCommandPermissionGranted = prefs.getBoolean(FIELD_PERMISSION, false),
        bridgeState = prefs.getString(FIELD_BRIDGE_STATE, null)?.let {
            runCatching { ExternalProviderBridgeState.valueOf(it) }.getOrNull()
        } ?: ExternalProviderBridgeState.UNKNOWN,
        lastProbeAtEpochMs = prefs.getLong(FIELD_LAST_PROBE_AT, 0L).takeIf { it > 0L },
        lastProbeExecutionId = prefs.getInt(FIELD_LAST_PROBE_ID, 0).takeIf { it > 0 },
        lastProbeResult = prefs.getString(FIELD_LAST_PROBE_RESULT, null)?.let {
            runCatching { ExternalProviderProbeResult.valueOf(it) }.getOrNull()
        },
        detail = prefs.getString(FIELD_DETAIL, null),
    )

    override fun recordHostFacts(
        termuxInstalled: Boolean,
        runCommandPermissionGranted: Boolean,
    ) {
        prefs.edit()
            .putBoolean(FIELD_TERMUX_INSTALLED, termuxInstalled)
            .putBoolean(FIELD_PERMISSION, runCommandPermissionGranted)
            .apply()
    }

    override fun markProbeDispatched(executionId: Int) {
        prefs.edit()
            .putString(FIELD_BRIDGE_STATE, ExternalProviderBridgeState.CHECKING.name)
            .putInt(FIELD_LAST_PROBE_ID, executionId)
            .remove(FIELD_DETAIL)
            .apply()
    }

    override fun recordProbe(
        executionId: Int,
        result: ExternalProviderProbeResult,
        atEpochMs: Long,
        detail: String? = null,
    ) {
        prefs.edit()
            .putString(
                FIELD_BRIDGE_STATE,
                if (result == ExternalProviderProbeResult.PASS) {
                    ExternalProviderBridgeState.PASS.name
                } else {
                    ExternalProviderBridgeState.FAIL.name
                },
            )
            .putString(FIELD_LAST_PROBE_RESULT, result.name)
            .putInt(FIELD_LAST_PROBE_ID, executionId)
            .putLong(FIELD_LAST_PROBE_AT, atEpochMs)
            .apply {
                if (detail.isNullOrBlank()) remove(FIELD_DETAIL)
                else putString(FIELD_DETAIL, detail.trim().take(MAX_DETAIL_LENGTH))
            }
            .apply()
    }

    override fun recordProbeDispatchFailure(atEpochMs: Long, detail: String) {
        prefs.edit()
            .putString(FIELD_BRIDGE_STATE, ExternalProviderBridgeState.FAIL.name)
            .putString(FIELD_LAST_PROBE_RESULT, ExternalProviderProbeResult.FAIL.name)
            .putLong(FIELD_LAST_PROBE_AT, atEpochMs)
            .remove(FIELD_LAST_PROBE_ID)
            .putString(FIELD_DETAIL, detail.trim().take(MAX_DETAIL_LENGTH))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "siftalpha_external_provider_readiness_v1"
        private const val FIELD_TERMUX_INSTALLED = "termux_installed"
        private const val FIELD_PERMISSION = "run_command_permission"
        private const val FIELD_BRIDGE_STATE = "bridge_state"
        private const val FIELD_LAST_PROBE_AT = "last_probe_at"
        private const val FIELD_LAST_PROBE_ID = "last_probe_id"
        private const val FIELD_LAST_PROBE_RESULT = "last_probe_result"
        private const val FIELD_DETAIL = "detail"
        private const val MAX_DETAIL_LENGTH = 240
    }
}

/**
 * Shared command-bridge probe coordinator. It is the only component that dispatches
 * TermuxBackend.CONNECTION_TEST and consumes its result for readiness purposes.
 */
class ExternalProviderProbeCoordinator internal constructor(
    private val bridge: ExternalProviderBridge,
    private val store: ExternalProviderReadinessStateStore,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val probeMaxAgeMs: Long = ExternalProviderPreflight.DEFAULT_PROBE_MAX_AGE_MS,
) : ExternalProviderPreflightGate {
    private val lock = Any()
    private val listeners = CopyOnWriteArraySet<(ExternalProviderPreflightResult) -> Unit>()
    private var listening = false

    private val resultListener: (RuntimeResult) -> Unit = resultListener@{ result ->
        val expected = synchronized(lock) {
            store.read().takeIf { it.bridgeState == ExternalProviderBridgeState.CHECKING }
                ?.lastProbeExecutionId
        }
        if (expected == null || expected != result.executionId) return@resultListener

        val passed = result.exitCode == 0 &&
            result.internalErrorMessage.isBlank() &&
            "SIFTALPHA_TERMUX_BRIDGE_OK" in result.stdout
        val detail = if (passed) {
            null
        } else {
            result.internalErrorMessage
                .ifBlank { result.stderr }
                .ifBlank { "CONNECTION_TEST_FAILED(exitCode=${result.exitCode})" }
        }
        store.recordProbe(
            executionId = result.executionId,
            result = if (passed) ExternalProviderProbeResult.PASS else ExternalProviderProbeResult.FAIL,
            atEpochMs = nowEpochMs(),
            detail = detail,
        )
        TermuxResultBus.consume(result.executionId)
        val current = current()
        listeners.forEach { listener -> runCatching { listener(current) } }
    }

    init {
        ensureListening()
    }

    fun addListener(listener: (ExternalProviderPreflightResult) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (ExternalProviderPreflightResult) -> Unit) {
        listeners -= listener
    }

    fun current(): ExternalProviderPreflightResult {
        val installed = runCatching { bridge.isTermuxInstalled() }.getOrDefault(false)
        val permission = installed && runCatching { bridge.hasRunCommandPermission() }.getOrDefault(false)
        store.recordHostFacts(installed, permission)
        return ExternalProviderPreflight.evaluate(store.read(), nowEpochMs(), probeMaxAgeMs)
    }

    /** Re-evaluate and launch one shared CONNECTION_TEST when the bridge fact is missing/stale. */
    override fun ensureReady(): ExternalProviderPreflightResult {
        val initial = current()
        if (
            initial.readiness != ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED ||
            !initial.termuxInstalled ||
            !initial.runCommandPermissionGranted
        ) {
            return initial
        }
        return dispatchProbe()
    }

    /** Explicit retry used after the UI asks the user to open Termux or finish setup. */
    fun probe(): ExternalProviderPreflightResult {
        val initial = current()
        if (!initial.termuxInstalled || !initial.runCommandPermissionGranted) return initial
        return dispatchProbe()
    }

    private fun dispatchProbe(): ExternalProviderPreflightResult {
        ensureListening()
        val facts = store.read()
        if (facts.bridgeState == ExternalProviderBridgeState.CHECKING) {
            return ExternalProviderPreflight.evaluate(facts, nowEpochMs(), probeMaxAgeMs)
        }
        return try {
            val executionId = bridge.execute(TermuxBackend.CONNECTION_TEST)
            store.markProbeDispatched(executionId)
            TermuxResultBus.consume(executionId)?.let(resultListener)
            ExternalProviderPreflight.evaluate(store.read(), nowEpochMs(), probeMaxAgeMs)
        } catch (error: Throwable) {
            val detail = error.message ?: error.javaClass.simpleName
            store.recordProbeDispatchFailure(nowEpochMs(), detail)
            current()
        }
    }

    private fun ensureListening() {
        synchronized(lock) {
            if (listening) return
            TermuxResultBus.addListener(resultListener)
            listening = true
        }
        // A result may have arrived while the Activity was away but is still in the shared cache.
        store.read().lastProbeExecutionId?.let { executionId ->
            TermuxResultBus.consume(executionId)?.let(resultListener)
        }
    }

    companion object {
        @Volatile
        private var sharedInstance: ExternalProviderProbeCoordinator? = null

        fun shared(context: Context): ExternalProviderProbeCoordinator =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: ExternalProviderProbeCoordinator(
                    bridge = TermuxBackend(context.applicationContext),
                    store = ExternalProviderReadinessStore(context.applicationContext),
                ).also { sharedInstance = it }
            }
    }
}
