package com.siftalpha.studio.runtime

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

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
    UNRESPONSIVE,
}

enum class ExternalProviderProbeResult {
    PASS,
    FAIL,
}

enum class ExternalProviderProbeStage {
    BRIDGE,
    RUNTIME_CAPABILITY,
}

enum class ExternalProviderReadiness {
    TERMUX_NOT_INSTALLED,
    RUN_COMMAND_PERMISSION_REQUIRED,
    BRIDGE_CHECK_REQUIRED,
    BRIDGE_CHECKING,
    EXTERNAL_APPS_CONFIGURATION_REQUIRED,
    BRIDGE_UNRESPONSIVE,
    READY,
    UNAVAILABLE,
}

enum class ExternalProviderRecoveryAction {
    INSTALL_PROVIDER,
    REQUEST_RUN_COMMAND_PERMISSION,
    OPEN_PROVIDER,
    RECHECK_PROVIDER,
    WAIT_FOR_PROBE,
    NONE,
}

data class ExternalProviderDescriptor(
    val id: String,
    val displayName: String,
    val packageName: String,
    val installUrl: String,
)

object ExternalProviderCatalog {
    val TERMUX = ExternalProviderDescriptor(
        id = "termux",
        displayName = "Termux",
        packageName = TermuxContract.PACKAGE_NAME,
        installUrl = TermuxContract.OFFICIAL_INSTALL_URL,
    )
}

object ExternalProviderRecoveryPolicy {
    fun actionFor(readiness: ExternalProviderReadiness): ExternalProviderRecoveryAction = when (readiness) {
        ExternalProviderReadiness.TERMUX_NOT_INSTALLED ->
            ExternalProviderRecoveryAction.INSTALL_PROVIDER
        ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED ->
            ExternalProviderRecoveryAction.REQUEST_RUN_COMMAND_PERMISSION
        ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED,
        ExternalProviderReadiness.BRIDGE_UNRESPONSIVE,
        -> ExternalProviderRecoveryAction.OPEN_PROVIDER
        ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED ->
            ExternalProviderRecoveryAction.RECHECK_PROVIDER
        ExternalProviderReadiness.BRIDGE_CHECKING ->
            ExternalProviderRecoveryAction.WAIT_FOR_PROBE
        ExternalProviderReadiness.READY,
        ExternalProviderReadiness.UNAVAILABLE,
        -> ExternalProviderRecoveryAction.NONE
    }
}

data class ExternalProviderFacts(
    val termuxInstalled: Boolean,
    val runCommandPermissionGranted: Boolean,
    val bridgeState: ExternalProviderBridgeState,
    val probeStage: ExternalProviderProbeStage? = null,
    val lastProbeAtEpochMs: Long? = null,
    val lastProbeExecutionId: Int? = null,
    val lastProbeResult: ExternalProviderProbeResult? = null,
    val detail: String? = null,
    val setupComplete: Boolean = false,
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
    val probeStage: ExternalProviderProbeStage? = null,
    val lastProbeResult: ExternalProviderProbeResult? = null,
    val setupComplete: Boolean = false,
    val provider: ExternalProviderDescriptor = ExternalProviderCatalog.TERMUX,
    val recoveryAction: ExternalProviderRecoveryAction =
        ExternalProviderRecoveryPolicy.actionFor(readiness),
) {
    val ready: Boolean
        get() = readiness == ExternalProviderReadiness.READY
}

enum class ExternalRuntimeSetupStage {
    TERMUX,
    RUN_COMMAND_PERMISSION,
    EXTERNAL_APPS,
    PROOT_DISTRO,
    UBUNTU,
    FINAL_CHECK,
    READY,
}

data class ExternalRuntimeSetupStatus(
    val stage: ExternalRuntimeSetupStage,
    val termuxInstalled: Boolean,
    val runCommandPermissionGranted: Boolean,
    val allowExternalAppsReady: Boolean?,
    val prootDistroReady: Boolean?,
    val ubuntuReady: Boolean?,
    val ready: Boolean,
    val checking: Boolean,
    val detail: String? = null,
)

object ExternalRuntimeSetupGuide {
    private const val PROOT_MARKER = "SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=PROOT_DISTRO"
    private const val UBUNTU_MARKER = "SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=UBUNTU"

    fun evaluate(result: ExternalProviderPreflightResult): ExternalRuntimeSetupStatus {
        val detail = result.detail.orEmpty()
        if (!result.termuxInstalled) {
            return status(result, ExternalRuntimeSetupStage.TERMUX)
        }
        if (!result.runCommandPermissionGranted) {
            return status(result, ExternalRuntimeSetupStage.RUN_COMMAND_PERMISSION)
        }
        if (result.setupComplete) {
            return ExternalRuntimeSetupStatus(
                stage = ExternalRuntimeSetupStage.READY,
                termuxInstalled = true,
                runCommandPermissionGranted = true,
                allowExternalAppsReady = true,
                prootDistroReady = true,
                ubuntuReady = true,
                ready = true,
                checking = false,
                detail = result.detail,
            )
        }

        val runtimeStage =
            result.probeStage == ExternalProviderProbeStage.RUNTIME_CAPABILITY
        val failed = result.lastProbeResult == ExternalProviderProbeResult.FAIL
        if (runtimeStage && failed && PROOT_MARKER in detail) {
            return ExternalRuntimeSetupStatus(
                stage = ExternalRuntimeSetupStage.PROOT_DISTRO,
                termuxInstalled = true,
                runCommandPermissionGranted = true,
                allowExternalAppsReady = true,
                prootDistroReady = false,
                ubuntuReady = null,
                ready = false,
                checking = false,
                detail = result.detail,
            )
        }
        if (runtimeStage && failed && UBUNTU_MARKER in detail) {
            return ExternalRuntimeSetupStatus(
                stage = ExternalRuntimeSetupStage.UBUNTU,
                termuxInstalled = true,
                runCommandPermissionGranted = true,
                allowExternalAppsReady = true,
                prootDistroReady = true,
                ubuntuReady = false,
                ready = false,
                checking = false,
                detail = result.detail,
            )
        }

        val bridgeFailed =
            result.probeStage == ExternalProviderProbeStage.BRIDGE &&
                result.lastProbeResult == ExternalProviderProbeResult.FAIL
        if (
            bridgeFailed ||
            result.readiness == ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED ||
            result.readiness == ExternalProviderReadiness.BRIDGE_UNRESPONSIVE
        ) {
            return ExternalRuntimeSetupStatus(
                stage = ExternalRuntimeSetupStage.EXTERNAL_APPS,
                termuxInstalled = true,
                runCommandPermissionGranted = true,
                allowExternalAppsReady = false,
                prootDistroReady = null,
                ubuntuReady = null,
                ready = false,
                checking = false,
                detail = result.detail,
            )
        }

        return ExternalRuntimeSetupStatus(
            stage = ExternalRuntimeSetupStage.FINAL_CHECK,
            termuxInstalled = true,
            runCommandPermissionGranted = true,
            allowExternalAppsReady = when {
                runtimeStage -> true
                result.allowExternalApps == true -> true
                else -> null
            },
            prootDistroReady = null,
            ubuntuReady = null,
            ready = false,
            checking = result.readiness == ExternalProviderReadiness.BRIDGE_CHECKING,
            detail = result.detail,
        )
    }

    private fun status(
        result: ExternalProviderPreflightResult,
        stage: ExternalRuntimeSetupStage,
    ) = ExternalRuntimeSetupStatus(
        stage = stage,
        termuxInstalled = result.termuxInstalled,
        runCommandPermissionGranted = result.runCommandPermissionGranted,
        allowExternalAppsReady = null,
        prootDistroReady = null,
        ubuntuReady = null,
        ready = false,
        checking = false,
        detail = result.detail,
    )
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
                val runtimeCapabilityProven =
                    facts.probeStage == ExternalProviderProbeStage.RUNTIME_CAPABILITY &&
                        facts.lastProbeResult == ExternalProviderProbeResult.PASS
                if (probeFresh && runtimeCapabilityProven) {
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

            ExternalProviderBridgeState.UNRESPONSIVE -> result(
                facts = facts,
                readiness = ExternalProviderReadiness.BRIDGE_UNRESPONSIVE,
                bridgeResponsive = false,
            )

            ExternalProviderBridgeState.UNKNOWN ->
                result(facts, ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED)
        }
    }

    private fun result(
        facts: ExternalProviderFacts,
        readiness: ExternalProviderReadiness,
        allowExternalApps: Boolean? = when {
            readiness == ExternalProviderReadiness.TERMUX_NOT_INSTALLED ||
                readiness == ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED -> null
            facts.bridgeState == ExternalProviderBridgeState.PASS -> true
            facts.bridgeState == ExternalProviderBridgeState.FAIL -> false
            else -> null
        },
        bridgeResponsive: Boolean? = when {
            readiness == ExternalProviderReadiness.TERMUX_NOT_INSTALLED ||
                readiness == ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED -> null
            facts.bridgeState == ExternalProviderBridgeState.PASS -> true
            facts.bridgeState == ExternalProviderBridgeState.FAIL ||
                facts.bridgeState == ExternalProviderBridgeState.UNRESPONSIVE -> false
            else -> null
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
        probeStage = facts.probeStage,
        lastProbeResult = facts.lastProbeResult,
        setupComplete = facts.setupComplete,
    )
}

/**
 * The one shared persisted provider-readiness store. It stores facts from the latest probe, not a
 * permanent "ready" switch. Every PREPARE/RUN asks the coordinator to re-evaluate freshness.
 */
interface ExternalProviderReadinessStateStore {
    fun read(): ExternalProviderFacts
    fun recordHostFacts(termuxInstalled: Boolean, runCommandPermissionGranted: Boolean)
    fun markProbeDispatched(executionId: Int, stage: ExternalProviderProbeStage)
    fun recordProbe(
        executionId: Int,
        result: ExternalProviderProbeResult,
        atEpochMs: Long,
        detail: String? = null,
    )
    fun recordProbeDispatchFailure(
        atEpochMs: Long,
        detail: String,
        stage: ExternalProviderProbeStage,
    )
    fun recordProbeTimeout(executionId: Int, atEpochMs: Long, detail: String)

    fun recordRuntimeCapabilityProof(
        atEpochMs: Long,
        detail: String? = null,
    ) = Unit
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
        probeStage = prefs.getString(FIELD_PROBE_STAGE, null)?.let {
            runCatching { ExternalProviderProbeStage.valueOf(it) }.getOrNull()
        },
        lastProbeAtEpochMs = prefs.getLong(FIELD_LAST_PROBE_AT, 0L).takeIf { it > 0L },
        lastProbeExecutionId = prefs.getInt(FIELD_LAST_PROBE_ID, 0).takeIf { it > 0 },
        lastProbeResult = prefs.getString(FIELD_LAST_PROBE_RESULT, null)?.let {
            runCatching { ExternalProviderProbeResult.valueOf(it) }.getOrNull()
        },
        detail = prefs.getString(FIELD_DETAIL, null),
        setupComplete = prefs.getBoolean(FIELD_SETUP_COMPLETE, false),
    )

    override fun recordHostFacts(
        termuxInstalled: Boolean,
        runCommandPermissionGranted: Boolean,
    ) {
        val effectivePermission = termuxInstalled && runCommandPermissionGranted
        prefs.edit()
            .putBoolean(FIELD_TERMUX_INSTALLED, termuxInstalled)
            .putBoolean(FIELD_PERMISSION, effectivePermission)
            .apply {
                if (!termuxInstalled || !effectivePermission) {
                    putString(FIELD_BRIDGE_STATE, ExternalProviderBridgeState.UNKNOWN.name)
                    remove(FIELD_PROBE_STAGE)
                    remove(FIELD_LAST_PROBE_AT)
                    remove(FIELD_LAST_PROBE_ID)
                    remove(FIELD_LAST_PROBE_RESULT)
                    remove(FIELD_DETAIL)
                    putBoolean(FIELD_SETUP_COMPLETE, false)
                }
            }
            .apply()
    }

    override fun markProbeDispatched(
        executionId: Int,
        stage: ExternalProviderProbeStage,
    ) {
        prefs.edit()
            .putString(FIELD_BRIDGE_STATE, ExternalProviderBridgeState.CHECKING.name)
            .putString(FIELD_PROBE_STAGE, stage.name)
            .putInt(FIELD_LAST_PROBE_ID, executionId)
            .remove(FIELD_DETAIL)
            .apply()
    }

    override fun recordProbe(
        executionId: Int,
        result: ExternalProviderProbeResult,
        atEpochMs: Long,
        detail: String?,
    ) {
        val currentStage = read().probeStage
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
                if (
                    result == ExternalProviderProbeResult.PASS &&
                    currentStage == ExternalProviderProbeStage.RUNTIME_CAPABILITY
                ) {
                    putBoolean(FIELD_SETUP_COMPLETE, true)
                } else if (result == ExternalProviderProbeResult.FAIL) {
                    putBoolean(FIELD_SETUP_COMPLETE, false)
                }
                if (detail.isNullOrBlank()) remove(FIELD_DETAIL)
                else putString(FIELD_DETAIL, detail.trim().take(MAX_DETAIL_LENGTH))
            }
            .apply()
    }

    override fun recordProbeDispatchFailure(
        atEpochMs: Long,
        detail: String,
        stage: ExternalProviderProbeStage,
    ) {
        prefs.edit()
            .putString(FIELD_BRIDGE_STATE, ExternalProviderBridgeState.FAIL.name)
            .putString(FIELD_PROBE_STAGE, stage.name)
            .putString(FIELD_LAST_PROBE_RESULT, ExternalProviderProbeResult.FAIL.name)
            .putLong(FIELD_LAST_PROBE_AT, atEpochMs)
            .remove(FIELD_LAST_PROBE_ID)
            .putString(FIELD_DETAIL, detail.trim().take(MAX_DETAIL_LENGTH))
            .apply()
    }

    override fun recordProbeTimeout(executionId: Int, atEpochMs: Long, detail: String) {
        val current = read()
        if (
            current.bridgeState != ExternalProviderBridgeState.CHECKING ||
            current.lastProbeExecutionId != executionId
        ) {
            return
        }
        prefs.edit()
            .putString(FIELD_BRIDGE_STATE, ExternalProviderBridgeState.UNRESPONSIVE.name)
            .putInt(FIELD_LAST_PROBE_ID, executionId)
            .remove(FIELD_LAST_PROBE_RESULT)
            .putLong(FIELD_LAST_PROBE_AT, atEpochMs)
            .putString(FIELD_DETAIL, detail.trim().take(MAX_DETAIL_LENGTH))
            .apply()
    }

    override fun recordRuntimeCapabilityProof(
        atEpochMs: Long,
        detail: String?,
    ) {
        prefs.edit()
            .putString(FIELD_BRIDGE_STATE, ExternalProviderBridgeState.PASS.name)
            .putString(FIELD_PROBE_STAGE, ExternalProviderProbeStage.RUNTIME_CAPABILITY.name)
            .putString(FIELD_LAST_PROBE_RESULT, ExternalProviderProbeResult.PASS.name)
            .putLong(FIELD_LAST_PROBE_AT, atEpochMs)
            .remove(FIELD_LAST_PROBE_ID)
            .putBoolean(FIELD_SETUP_COMPLETE, true)
            .apply {
                if (detail.isNullOrBlank()) remove(FIELD_DETAIL)
                else putString(FIELD_DETAIL, detail.trim().take(MAX_DETAIL_LENGTH))
            }
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "siftalpha_external_provider_readiness_v1"
        private const val FIELD_TERMUX_INSTALLED = "termux_installed"
        private const val FIELD_PERMISSION = "run_command_permission"
        private const val FIELD_BRIDGE_STATE = "bridge_state"
        private const val FIELD_PROBE_STAGE = "probe_stage"
        private const val FIELD_LAST_PROBE_AT = "last_probe_at"
        private const val FIELD_LAST_PROBE_ID = "last_probe_id"
        private const val FIELD_LAST_PROBE_RESULT = "last_probe_result"
        private const val FIELD_DETAIL = "detail"
        private const val FIELD_SETUP_COMPLETE = "setup_complete"
        private const val MAX_DETAIL_LENGTH = 240
    }
}

internal fun interface ExternalProviderProbeTimeoutHandle {
    fun cancel()
}

internal fun interface ExternalProviderProbeTimeoutScheduler {
    fun schedule(delayMs: Long, task: () -> Unit): ExternalProviderProbeTimeoutHandle
}

private class DefaultExternalProviderProbeTimeoutScheduler : ExternalProviderProbeTimeoutScheduler {
    private val executor by lazy {
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "SiftAlpha-ExternalProbeTimeout").apply { isDaemon = true }
        }
    }

    override fun schedule(
        delayMs: Long,
        task: () -> Unit,
    ): ExternalProviderProbeTimeoutHandle {
        val future: ScheduledFuture<*> = executor.schedule(
            { task() },
            delayMs.coerceAtLeast(1L),
            TimeUnit.MILLISECONDS,
        )
        return ExternalProviderProbeTimeoutHandle { future.cancel(false) }
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
    private val probeResponseTimeoutMs: Long = DEFAULT_PROBE_RESPONSE_TIMEOUT_MS,
    private val capabilityProbeResponseTimeoutMs: Long = DEFAULT_CAPABILITY_PROBE_RESPONSE_TIMEOUT_MS,
    private val timeoutScheduler: ExternalProviderProbeTimeoutScheduler =
        DefaultExternalProviderProbeTimeoutScheduler(),
) : ExternalProviderPreflightGate {
    private val lock = Any()
    private val listeners = CopyOnWriteArraySet<(ExternalProviderPreflightResult) -> Unit>()
    private val timeoutHandles = mutableMapOf<Int, ExternalProviderProbeTimeoutHandle>()
    private val timedOutProbeExecutions = LinkedHashSet<Int>()
    private var listening = false

    private val resultListener: (RuntimeResult) -> Unit = resultListener@{ result ->
        var nextStage: ExternalProviderProbeStage? = null
        val accepted = synchronized(lock) {
            timeoutHandles.remove(result.executionId)?.cancel()
            if (timedOutProbeExecutions.remove(result.executionId)) {
                false
            } else {
                val facts = store.read()
                if (
                    facts.bridgeState != ExternalProviderBridgeState.CHECKING ||
                    facts.lastProbeExecutionId != result.executionId
                ) {
                    false
                } else {
                    val stage = facts.probeStage ?: ExternalProviderProbeStage.BRIDGE
                    val marker = when (stage) {
                        ExternalProviderProbeStage.BRIDGE -> "SIFTALPHA_TERMUX_BRIDGE_OK"
                        ExternalProviderProbeStage.RUNTIME_CAPABILITY ->
                            "SIFTALPHA_EXTERNAL_RUNTIME_OK"
                    }
                    val passed = result.exitCode == 0 &&
                        result.internalErrorMessage.isBlank() &&
                        marker in result.stdout
                    if (passed && stage == ExternalProviderProbeStage.BRIDGE) {
                        nextStage = ExternalProviderProbeStage.RUNTIME_CAPABILITY
                    } else {
                        val detail = if (passed) {
                            null
                        } else {
                            result.internalErrorMessage
                                .ifBlank { result.stderr }
                                .ifBlank {
                                    "EXTERNAL_PROVIDER_PROBE_FAILED(stage=${stage.name},exitCode=${result.exitCode})"
                                }
                        }
                        store.recordProbe(
                            executionId = result.executionId,
                            result = if (passed) {
                                ExternalProviderProbeResult.PASS
                            } else {
                                ExternalProviderProbeResult.FAIL
                            },
                            atEpochMs = nowEpochMs(),
                            detail = detail,
                        )
                    }
                    true
                }
            }
        }
        TermuxResultBus.consume(result.executionId)
        if (!accepted) return@resultListener

        val current = nextStage?.let {
            dispatchProbeStage(it, replaceChecking = true)
        } ?: current()
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

    /**
     * Prove both layers before declaring READY:
     * RUN_COMMAND bridge response, then real bash -> proot-distro -> Ubuntu response.
     */
    override fun ensureReady(): ExternalProviderPreflightResult {
        val initial = current()
        if (!initial.termuxInstalled || !initial.runCommandPermissionGranted) {
            return initial
        }
        val shouldProbe =
            initial.readiness == ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED ||
                (
                    initial.setupComplete &&
                        initial.readiness == ExternalProviderReadiness.BRIDGE_UNRESPONSIVE
                    )
        return if (shouldProbe) {
            dispatchProbeStage(ExternalProviderProbeStage.BRIDGE)
        } else {
            initial
        }
    }

    /** Explicit retry used after the UI asks the user to open Termux or finish setup. */
    fun probe(): ExternalProviderPreflightResult {
        val initial = current()
        if (!initial.termuxInstalled || !initial.runCommandPermissionGranted) return initial
        return dispatchProbeStage(ExternalProviderProbeStage.BRIDGE)
    }

    private fun dispatchProbeStage(
        stage: ExternalProviderProbeStage,
        replaceChecking: Boolean = false,
    ): ExternalProviderPreflightResult {
        ensureListening()
        val facts = store.read()
        if (!replaceChecking && facts.bridgeState == ExternalProviderBridgeState.CHECKING) {
            return ExternalProviderPreflight.evaluate(facts, nowEpochMs(), probeMaxAgeMs)
        }
        return try {
            val command = when (stage) {
                ExternalProviderProbeStage.BRIDGE -> TermuxBackend.CONNECTION_TEST
                ExternalProviderProbeStage.RUNTIME_CAPABILITY ->
                    TermuxBackend.RUNTIME_CAPABILITY_TEST
            }
            val executionId = bridge.execute(command)
            store.markProbeDispatched(executionId, stage)
            scheduleProbeTimeout(executionId, stage)
            TermuxResultBus.consume(executionId)?.let(resultListener)
            ExternalProviderPreflight.evaluate(store.read(), nowEpochMs(), probeMaxAgeMs)
        } catch (error: Throwable) {
            val detail = error.message ?: error.javaClass.simpleName
            store.recordProbeDispatchFailure(nowEpochMs(), detail, stage)
            current()
        }
    }

    private fun scheduleProbeTimeout(
        executionId: Int,
        stage: ExternalProviderProbeStage,
    ) {
        val timeoutMs = when (stage) {
            ExternalProviderProbeStage.BRIDGE -> probeResponseTimeoutMs
            ExternalProviderProbeStage.RUNTIME_CAPABILITY -> capabilityProbeResponseTimeoutMs
        }
        val timeoutDetail = when (stage) {
            ExternalProviderProbeStage.BRIDGE -> PROBE_TIMEOUT_DETAIL
            ExternalProviderProbeStage.RUNTIME_CAPABILITY -> CAPABILITY_PROBE_TIMEOUT_DETAIL
        }
        val handle = timeoutScheduler.schedule(timeoutMs) {
            val shouldNotify = synchronized(lock) {
                timeoutHandles.remove(executionId)
                val facts = store.read()
                if (
                    facts.bridgeState != ExternalProviderBridgeState.CHECKING ||
                    facts.lastProbeExecutionId != executionId
                ) {
                    false
                } else {
                    timedOutProbeExecutions += executionId
                    while (timedOutProbeExecutions.size > MAX_TIMED_OUT_EXECUTIONS) {
                        timedOutProbeExecutions.firstOrNull()?.let(timedOutProbeExecutions::remove)
                    }
                    store.recordProbeTimeout(
                        executionId = executionId,
                        atEpochMs = nowEpochMs(),
                        detail = timeoutDetail,
                    )
                    true
                }
            }
            if (shouldNotify) {
                val current = current()
                listeners.forEach { listener -> runCatching { listener(current) } }
            }
        }
        synchronized(lock) {
            timeoutHandles.remove(executionId)?.cancel()
            timeoutHandles[executionId] = handle
        }
    }

    private fun ensureListening() {
        synchronized(lock) {
            if (listening) return
            TermuxResultBus.addListener(resultListener)
            listening = true
        }
        store.read().lastProbeExecutionId?.let { executionId ->
            TermuxResultBus.consume(executionId)?.let(resultListener)
        }
    }

    companion object {
        const val DEFAULT_PROBE_RESPONSE_TIMEOUT_MS = 3_000L
        const val DEFAULT_CAPABILITY_PROBE_RESPONSE_TIMEOUT_MS = 8_000L
        const val PROBE_TIMEOUT_DETAIL = "BRIDGE_PROBE_NO_RESPONSE_WITHIN_3S"
        const val CAPABILITY_PROBE_TIMEOUT_DETAIL =
            "RUNTIME_CAPABILITY_PROBE_NO_RESPONSE_WITHIN_8S"
        private const val MAX_TIMED_OUT_EXECUTIONS = 32

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
