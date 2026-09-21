package com.siftalpha.studio

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import com.siftalpha.studio.runtime.ExternalProviderPreflight
import com.siftalpha.studio.runtime.ExternalProviderReadinessStatus
import com.siftalpha.studio.runtime.RuntimeResult
import com.siftalpha.studio.runtime.TermuxBackend
import com.siftalpha.studio.runtime.TermuxContract
import com.siftalpha.studio.runtime.TermuxResultBus

/**
 * Presentation recovery for Shared External Provider Preflight（共享外部执行环境前置检查）.
 *
 * Readiness facts remain owned by ExternalProviderPreflight. Both Normal Mode（普通模式） and
 * Developer Workspace（开发者工作区） reuse this one UI coordinator to request user actions and
 * resume the exact PREPARE / RUN callback only after a fresh bridge probe reaches READY（就绪）.
 */
class ExternalProviderPreflightUiCoordinator(
    private val activity: Activity,
    private val backend: TermuxBackend,
    private val preflight: ExternalProviderPreflight,
) {
    private var pendingAction: (() -> Unit)? = null
    private var probeExecutionId: Int? = null
    private var listening = false
    private var retryOnResume = false
    private var dialogShowing = false

    private val probeListener: (RuntimeResult) -> Unit = listener@{ result ->
        if (result.executionId != probeExecutionId) return@listener
        activity.runOnUiThread { handleProbeResult(result) }
    }

    fun runWhenReady(action: () -> Unit) {
        if (pendingAction != null || probeExecutionId != null || dialogShowing) return
        pendingAction = action
        retryOnResume = false

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
            TermuxResultBus.consume(executionId)?.let(probeListener)
        }
    }

    fun onResume() {
        if (!retryOnResume || pendingAction == null) return
        retryOnResume = false
        preflight.invalidateBridgeEvidence()
        evaluate()
    }

    fun onStop() {
        if (listening) {
            TermuxResultBus.removeListener(probeListener)
            listening = false
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != REQUEST_RUN_COMMAND) return false

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
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
            ExternalProviderReadinessStatus.RUN_COMMAND_PERMISSION_REQUIRED -> showPermissionDialog()
            ExternalProviderReadinessStatus.BRIDGE_PROBE_REQUIRED -> startBridgeProbe()
            ExternalProviderReadinessStatus.TERMUX_CONFIGURATION_REQUIRED -> showTermuxConfigurationDialog()
            ExternalProviderReadinessStatus.BRIDGE_UNAVAILABLE -> showBridgeUnavailableDialog()
            ExternalProviderReadinessStatus.READY -> proceed()
        }
    }

    private fun startBridgeProbe() {
        if (probeExecutionId != null || pendingAction == null) return
        val executionId = runCatching {
            backend.execute(preflight.probeCommand())
        }.getOrElse {
            showBridgeUnavailableDialog()
            return
        }
        probeExecutionId = executionId
        TermuxResultBus.consume(executionId)?.let(probeListener)
    }

    private fun handleProbeResult(result: RuntimeResult) {
        val executionId = probeExecutionId ?: return
        if (result.executionId != executionId) return
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

    private fun showBridgeUnavailableDialog() {
        showActionDialog(
            title = activity.getString(R.string.external_provider_bridge_unavailable_title),
            message = activity.getString(R.string.external_provider_bridge_unavailable_message),
            positiveLabel = activity.getString(R.string.external_provider_open_termux),
            onPositive = ::openTermuxForRetry,
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
        val action = pendingAction ?: return
        pendingAction = null
        retryOnResume = false
        action()
    }

    private fun clearPendingAction() {
        pendingAction = null
        retryOnResume = false
    }

    companion object {
        private const val REQUEST_RUN_COMMAND = 741
    }
}
