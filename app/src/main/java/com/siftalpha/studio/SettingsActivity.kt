package com.siftalpha.studio

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.siftalpha.studio.ui.settings.SettingsScreen
import com.siftalpha.studio.ui.theme.StudioTheme

/** Shared host for new Compose-only Studio surfaces without changing legacy View activities. */
open class StudioComposeActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(StudioLanguage.wrap(newBase))
    }
}

class SettingsActivity : StudioComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var developerModeEnabled by remember {
                mutableStateOf(DeveloperModeStore(this@SettingsActivity).isEnabled())
            }
            StudioTheme {
                SettingsScreen(
                    currentLanguage = StudioLanguage.current(this@SettingsActivity).selfName,
                    currentBrowser = StudioBrowser.selectedLabel(this@SettingsActivity),
                    versionName = appVersionName(),
                    applicationId = packageName,
                    developerModeEnabled = developerModeEnabled,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onChangeLanguage = { StudioLanguage.showPicker(this@SettingsActivity) },
                    onChangeBrowser = { showBrowserPicker() },
                    onDeveloperModeChanged = { enabled ->
                        DeveloperModeStore(this@SettingsActivity).setEnabled(enabled)
                        developerModeEnabled = enabled
                    },
                )
            }
        }
    }

    private fun showBrowserPicker() {
        val browsers = StudioBrowser.discoverInstalled(this)
        if (browsers.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_browser_no_browser_title))
                .setMessage(getString(R.string.settings_browser_no_browser_message))
                .setPositiveButton(getString(R.string.common_confirm), null)
                .show()
            return
        }

        val selectedPackage = StudioBrowser.selectedPackage(this)
        val checked = browsers.indexOfFirst { it.packageName == selectedPackage }
        val labels = browsers.map { "${it.label}\n${it.packageName}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_browser_picker_title))
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                StudioBrowser.remember(this, browsers[which])
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun appVersionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
            .ifBlank { "?" }
}
