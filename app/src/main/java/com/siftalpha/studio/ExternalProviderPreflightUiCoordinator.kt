package com.siftalpha.studio

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.siftalpha.studio.runtime.ExternalProviderPreflight
import com.siftalpha.studio.runtime.ExternalProviderReadinessStatus
import com.siftalpha.studio.runtime.ExternalProviderRecoveryAction
import com.siftalpha.studio.runtime.ExternalProviderRecoveryPolicy
import com.siftalpha.studio.runtime.RuntimeResult
import com.siftalpha.studio.runtime.TermuxBackend
import com.siftalpha.studio.runtime.TermuxContract
import com.siftalpha.studio.runtime.TermuxResultBus

enum class ExternalProviderPreflightUiStage {
    CHECKING_PROVIDER,
    WAITING_PERMISSION,
    CHECKING_TERMUX,
    TERMUX_CONFIGURATION_REQUIRED,
    TERMUX_NOT_READY,
    READY,
    CANCELLED,
}

/**
 * Presentation recovery for Shared External Provider Preflight（共享外部执行环境前置检查）.
 *
 * Readiness facts remain owned by ExternalProviderPreflight. The bridge wait is bounded: if the
 * Termux RUN_COMMAND callback does not arrive within PROBE_TIMEOUT_MS, shared readiness records
 * BRIDGE_UNAVAILABLE and the UI can offer an explicit Open Termux recovery instead of hanging.
 */
class ExternalProviderPreflightUiCoordinator(
    private val activity: Activity,
    private val backend: TermuxBackend,
    private val preflight: ExternalProviderPreflight,
    private val onStage: (ExternalProviderPreflightUiStage) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingAction: (() -> Unit)? = null
    private var probeExecutionId: Int? = null
    private var listening = false
    private var retryOnResume = false
    private var dialogShowing = false
    private var probeTimeoutRunnable: Runnable? = null
    private var permissionGrantedThisFlow = false
    private var plainOpenAttempted = false

    private val probeListener: (RuntimeResult) -> Unit = listener@{ result ->
        if (result.executionId != probeExecutionId) return@listener
        activity.runOnUiThread { handleProbeResult(result) }
    }

    fun runWhenReady(action: () -> Unit) {
        if (pendingAction != null || probeExecutionId != null || dialogShowing) return
        pendingAction = action
        retryOnResume = false
        permissionGrantedThisFlow = false
        plainOpenAttempted = false
        onStage(ExternalProviderPreflightUiStage.CHECKING_PROVIDER)

        // PREPARE / RUN require a current real bridge response rather than only cached READY evidence.
        preflight.invalidateBridgeEvidence()
        evaluate()
    }

    fun onStart() {
        if (!listening) {
            TermuxResultBus.addListener(probeListener)
            listening = true
        }
        probeExecutionId?.let { executionId ->
            val cached = TermuxResultBus.consume(executionId)
            if (cached != null) {
                probeListener(cached)
            } else {
                scheduleProbeTimeout()
            }
        }
    }

    fun onResume() {
        if (!retryOnResume || pendingAction == null) return
        retryOnResume = false
        onStage(ExternalProviderPreflightUiStage.CHECKING_PROVIDER)
        preflight.invalidateBridgeEvidence()
        evaluate()
    }

    fun onStop() {
        cancelProbeTimeout()
        if (listening) {
            TermuxResultBus.removeListener(probeListener)
            listening = false
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != REQUEST_RUN_COMMAND) return false

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            permissionGrantedThisFlow = true
            onStage(ExternalProviderPreflightUiStage.CHECKING_PROVIDER)
            preflight.invalidateBridgeEvidence()
            evaluate()
        } else {
            clearPendingAction()
            showInformationalDialog(
                title = activity.getString(R.string.external_provider_permission_denied_title),
                message = activity.getString(R.string.external_provider_permission_denied_message),
            )
        }
        return true
    }

    private fun evaluate() {
        if (pendingAction == null || activity.isFinishing || activity.isDestroyed) return

        when (preflight.inspect().status) {
            ExternalProviderReadinessStatus.TERMUX_NOT_INSTALLED -> {
                clearPendingAction()
                showInformationalDialog(
                    title = activity.getString(R.string.external_provider_termux_missing_title),
                    message = activity.getString(R.string.external_provider_termux_missing_message),
                )
            }
            ExternalProviderReadinessStatus.RUN_COMMAND_PERMISSION_REQUIRED -> {
                onStage(ExternalProviderPreflightUiStage.WAITING_PERMISSION)
                showPermissionDialog()
            }
            ExternalProviderReadinessStatus.BRIDGE_PROBE_REQUIRED -> startBridgeProbe()
            ExternalProviderReadinessStatus.TERMUX_CONFIGURATION_REQUIRED -> {
                onStage(ExternalProviderPreflightUiStage.TERMUX_CONFIGURATION_REQUIRED)
                showTermuxConfigurationDialog()
            }
            ExternalProviderReadinessStatus.BRIDGE_UNAVAILABLE -> {
                onStage(ExternalProviderPreflightUiStage.TERMUX_NOT_READY)
                showBridgeUnavailableRecovery()
            }
            ExternalProviderReadinessStatus.READY -> proceed()
        }
    }

    private fun startBridgeProbe() {
        if (probeExecutionId != null || pendingAction == null) return
        onStage(ExternalProviderPreflightUiStage.CHECKING_TERMUX)
        val executionId = runCatching {
            backend.execute(preflight.probeCommand())
        }.getOrElse {
            preflight.recordProbeTimeout()
            onStage(ExternalProviderPreflightUiStage.TERMUX_NOT_READY)
            showBridgeUnavailableRecovery()
            return
        }
        probeExecutionId = executionId
        val cached = TermuxResultBus.consume(executionId)
        if (cached != null) {
            probeListener(cached)
        } else {
            scheduleProbeTimeout()
        }
    }

    private fun scheduleProbeTimeout() {
        if (probeExecutionId == null || probeTimeoutRunnable != null) return
        val runnable = Runnable { handleProbeTimeout() }
        probeTimeoutRunnable = runnable
        handler.postDelayed(runnable, PROBE_TIMEOUT_MS)
    }

    private fun cancelProbeTimeout() {
        probeTimeoutRunnable?.let(handler::removeCallbacks)
        probeTimeoutRunnable = null
    }

    private fun handleProbeTimeout() {
        probeTimeoutRunnable = null
        val executionId = probeExecutionId ?: return

        // Resolve a race in favor of a real callback if it arrived at the timeout boundary.
        TermuxResultBus.consume(executionId)?.let {
            handleProbeResult(it)
            return
        }

        probeExecutionId = null
        preflight.recordProbeTimeout()
        onStage(ExternalProviderPreflightUiStage.TERMUX_NOT_READY)
        showBridgeUnavailableRecovery()
    }

    private fun handleProbeResult(result: RuntimeResult) {
        val executionId = probeExecutionId ?: return
        if (result.executionId != executionId) return
        cancelProbeTimeout()
        TermuxResultBus.consume(executionId)
        probeExecutionId = null
        preflight.recordProbe(result)
        evaluate()
    }

    private fun showPermissionDialog() {
        showActionDialog(
            title = activity.getString(R.string.external_provider_permission_title),
            message = activity.getString(R.string.external_provider_permission_message),
            positiveLabel = activity.getString(R.string.external_provider_allow),
            onPositive = {
                runCatching {
                    activity.requestPermissions(
                        arrayOf(TermuxContract.RUN_COMMAND_PERMISSION),
                        REQUEST_RUN_COMMAND,
                    )
                }.onFailure {
                    clearPendingAction()
                    showInformationalDialog(
                        title = activity.getString(R.string.external_provider_permission_denied_title),
                        message = activity.getString(R.string.external_provider_permission_denied_message),
                    )
                }
            },
        )
    }

    private fun showTermuxConfigurationDialog() {
        showActionDialog(
            title = activity.getString(R.string.external_provider_configuration_title),
            message = activity.getString(R.string.external_provider_configuration_message),
            positiveLabel = activity.getString(R.string.external_provider_open_termux),
            onPositive = {
                copyTermuxSetupCommand()
                openTermuxForRetry()
            },
        )
    }

    private fun showBridgeUnavailableRecovery() {
        when (
            ExternalProviderRecoveryPolicy.bridgeUnavailableAction(
                setupPreviouslyVerified = preflight.setupPreviouslyVerified(),
                permissionGrantedThisFlow = permissionGrantedThisFlow,
                plainOpenAttempted = plainOpenAttempted,
            )
        ) {
            ExternalProviderRecoveryAction.OPEN_TERMUX -> showBridgeUnavailableDialog()
            ExternalProviderRecoveryAction.SETUP_AND_OPEN_TERMUX -> showSetupRecoveryDialog()
        }
    }

    private fun showBridgeUnavailableDialog() {
        showActionDialog(
            title = activity.getString(R.string.external_provider_bridge_unavailable_title),
            message = activity.getString(R.string.external_provider_bridge_unavailable_message),
            positiveLabel = activity.getString(R.string.external_provider_open_termux),
            onPositive = {
                plainOpenAttempted = true
                openTermuxForRetry()
            },
        )
    }

    private fun showSetupRecoveryDialog() {
        showActionDialog(
            title = activity.getString(R.string.external_provider_setup_recovery_title),
            message = activity.getString(R.string.external_provider_setup_recovery_message),
            positiveLabel = activity.getString(R.string.external_provider_open_termux),
            onPositive = {
                plainOpenAttempted = true
                copyTermuxSetupCommand()
                openTermuxForRetry()
            },
        )
    }

    private fun showActionDialog(
        title: String,
        message: String,
        positiveLabel: String,
        onPositive: () -> Unit,
    ) {
        if (dialogShowing || activity.isFinishing || activity.isDestroyed) return
        dialogShowing = true
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(activity.getString(R.string.common_cancel)) { _, _ ->
                dialogShowing = false
                clearPendingAction()
            }
            .setPositiveButton(positiveLabel) { _, _ ->
                dialogShowing = false
                onPositive()
            }
            .create()
        dialog.setOnCancelListener {
            dialogShowing = false
            clearPendingAction()
        }
        dialog.setOnDismissListener { dialogShowing = false }
        dialog.show()
    }

    private fun showInformationalDialog(title: String, message: String) {
        if (dialogShowing || activity.isFinishing || activity.isDestroyed) return
        dialogShowing = true
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(activity.getString(R.string.common_confirm), null)
            .create()
        dialog.setOnDismissListener { dialogShowing = false }
        dialog.show()
    }

    private fun copyTermuxSetupCommand() {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                activity.getString(R.string.external_provider_setup_command_label),
                TermuxBackend.FIRST_RUN_SETUP_COMMAND,
            ),
        )
        Toast.makeText(
            activity,
            activity.getString(R.string.external_provider_setup_copied),
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun openTermuxForRetry() {
        val launch = activity.packageManager.getLaunchIntentForPackage(TermuxContract.PACKAGE_NAME)
        if (launch == null) {
            clearPendingAction()
            Toast.makeText(
                activity,
                activity.getString(R.string.external_provider_termux_open_failed),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        retryOnResume = true
        activity.startActivity(launch)
    }

    private fun proceed() {
        cancelProbeTimeout()
        onStage(ExternalProviderPreflightUiStage.READY)
        val action = pendingAction ?: return
        pendingAction = null
        retryOnResume = false
        permissionGrantedThisFlow = false
        plainOpenAttempted = false
        action()
    }

    private fun clearPendingAction() {
        cancelProbeTimeout()
        pendingAction = null
        retryOnResume = false
        permissionGrantedThisFlow = false
        plainOpenAttempted = false
        onStage(ExternalProviderPreflightUiStage.CANCELLED)
    }

    companion object {
        private const val REQUEST_RUN_COMMAND = 741
        internal const val PROBE_TIMEOUT_MS = 5_000L
    }
}
