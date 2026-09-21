package com.siftalpha.studio

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/**
 * Shared one-time reliability guidance for both Normal Mode and Developer Mode.
 *
 * It does not request battery-exemption permissions and does not contain OEM-specific routing.
 * "Remind me later" defers only for the current app process; a future app process may remind again.
 * Opening system settings acknowledges the guidance persistently.
 */
class BackgroundReliabilityGuidanceController(
    private val activity: Activity,
) {
    private val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun maybeProceed(onProceed: () -> Unit) {
        if (prefs.getBoolean(KEY_ACKNOWLEDGED, false) || deferredForProcess) {
            onProceed()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.background_reliability_title))
            .setMessage(activity.getString(R.string.background_reliability_message))
            .setNegativeButton(activity.getString(R.string.background_reliability_later)) { _, _ ->
                deferredForProcess = true
                onProceed()
            }
            .setPositiveButton(activity.getString(R.string.background_reliability_open_settings)) { _, _ ->
                prefs.edit().putBoolean(KEY_ACKNOWLEDGED, true).apply()
                openBatterySettings()
            }
            .show()
    }

    private fun openBatterySettings() {
        val opened = runCatching {
            activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            true
        }.getOrDefault(false)
        if (opened) return

        val fallback = runCatching {
            activity.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
            true
        }.getOrDefault(false)
        if (!fallback) {
            Toast.makeText(
                activity,
                activity.getString(R.string.background_reliability_settings_unavailable),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    companion object {
        private const val PREFS_NAME = "siftalpha_background_reliability_v1"
        private const val KEY_ACKNOWLEDGED = "acknowledged"

        @Volatile
        private var deferredForProcess: Boolean = false
    }
}
