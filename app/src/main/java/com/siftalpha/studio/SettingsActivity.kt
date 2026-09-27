package com.siftalpha.studio

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.siftalpha.studio.runtime.ExternalProviderPreflightResult
import com.siftalpha.studio.runtime.ExternalProviderProbeCoordinator
import com.siftalpha.studio.runtime.ExternalRuntimeSetupGuide
import com.siftalpha.studio.runtime.TermuxBackend
import com.siftalpha.studio.runtime.TermuxContract
import com.siftalpha.studio.ui.settings.ExternalRuntimeSetupScreen
import com.siftalpha.studio.ui.settings.SettingsScreen
import com.siftalpha.studio.ui.theme.StudioTheme

/** Shared host for new Compose-only Studio surfaces without changing legacy View activities. */
open class StudioComposeActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(StudioLanguage.wrap(newBase))
    }
}

class SettingsActivity : StudioComposeActivity() {
    private lateinit var externalPreflight: ExternalProviderProbeCoordinator
    private lateinit var externalBackend: TermuxBackend
    private var externalSetupVisible by mutableStateOf(false)
    private var externalSetupResult by mutableStateOf<ExternalProviderPreflightResult?>(null)

    private val externalPreflightListener: (ExternalProviderPreflightResult) -> Unit = { result ->
        runOnUiThread {
            externalSetupResult = result
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        externalBackend = TermuxBackend(applicationContext)
        externalPreflight = ExternalProviderProbeCoordinator.shared(applicationContext)
        externalSetupResult = externalPreflight.current()
        externalSetupVisible = intent.getBooleanExtra(EXTRA_OPEN_EXTERNAL_RUNTIME_SETUP, false)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (externalSetupVisible) {
                        externalSetupVisible = false
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )

        val normalMode = intent.getBooleanExtra(EXTRA_NORMAL_MODE, false)
        setContent {
            var developerModeEnabled by remember {
                mutableStateOf(DeveloperModeStore(this@SettingsActivity).isEnabled())
            }
            val screen: @Composable () -> Unit = {
                if (externalSetupVisible) {
                    ExternalRuntimeSetupScreen(
                        status = ExternalRuntimeSetupGuide.evaluate(
                            externalSetupResult ?: externalPreflight.current(),
                        ),
                        externalAppsCommand = TermuxBackend.FIRST_RUN_SETUP_COMMAND,
                        storageAccessCommand = TermuxBackend.STORAGE_ACCESS_SETUP_COMMAND,
                        prootCommand = TermuxBackend.PROOT_DISTRO_INSTALL_COMMAND,
                        ubuntuCommand = TermuxBackend.UBUNTU_INSTALL_COMMAND,
                        onBack = { onBackPressedDispatcher.onBackPressed() },
                        onDownloadTermux = { openOfficialTermuxInstallPage() },
                        onRequestPermission = { requestRunCommandPermission() },
                        onCopyExternalAppsCommand = {
                            copySetupCommand(TermuxBackend.FIRST_RUN_SETUP_COMMAND)
                        },
                        onCopyStorageAccessCommand = {
                            copySetupCommand(TermuxBackend.STORAGE_ACCESS_SETUP_COMMAND)
                        },
                        onCopyProotCommand = {
                            copySetupCommand(TermuxBackend.PROOT_DISTRO_INSTALL_COMMAND)
                        },
                        onCopyUbuntuCommand = {
                            copySetupCommand(TermuxBackend.UBUNTU_INSTALL_COMMAND)
                        },
                        onOpenTermux = { openTermux() },
                        onRecheck = { refreshExternalRuntimeSetup(forceProbe = true) },
                    )
                } else {
                    SettingsScreen(
                        currentLanguage = StudioLanguage.current(this@SettingsActivity).selfName,
                        currentBrowser = StudioBrowser.selectedLabel(this@SettingsActivity),
                        versionName = appVersionName(),
                        applicationId = packageName,
                        developerModeEnabled = developerModeEnabled,
                        onBack = { onBackPressedDispatcher.onBackPressed() },
                        onChangeLanguage = { StudioLanguage.showPicker(this@SettingsActivity) },
                        onChangeBrowser = { showBrowserPicker() },
                        onOpenExternalRuntimeSetup = {
                            externalSetupVisible = true
                            refreshExternalRuntimeSetup(forceProbe = true)
                        },
                        onDeveloperModeChanged = { enabled ->
                            DeveloperModeStore(this@SettingsActivity).setEnabled(enabled)
                            developerModeEnabled = enabled
                        },
                    )
                }
            }
            if (normalMode) {
                SiftAlphaNormalTheme { screen() }
            } else {
                StudioTheme { screen() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        externalPreflight.addListener(externalPreflightListener)
    }

    override fun onResume() {
        super.onResume()
        if (externalSetupVisible) {
            refreshExternalRuntimeSetup(forceProbe = true)
        }
    }

    override fun onStop() {
        externalPreflight.removeListener(externalPreflightListener)
        super.onStop()
    }

    private fun refreshExternalRuntimeSetup(forceProbe: Boolean) {
        val current = externalPreflight.current()
        externalSetupResult = current
        if (!current.termuxInstalled || !current.runCommandPermissionGranted) return
        if (current.readiness == com.siftalpha.studio.runtime.ExternalProviderReadiness.BRIDGE_CHECKING) {
            return
        }
        externalSetupResult = if (forceProbe || !current.ready) {
            externalPreflight.probe()
        } else {
            current
        }
    }

    private fun requestRunCommandPermission() {
        if (!externalBackend.isTermuxInstalled()) {
            openOfficialTermuxInstallPage()
            return
        }
        if (externalBackend.hasRunCommandPermission()) {
            refreshExternalRuntimeSetup(forceProbe = true)
            return
        }
        requestPermissions(
            arrayOf(TermuxContract.RUN_COMMAND_PERMISSION),
            REQUEST_RUN_COMMAND,
        )
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RUN_COMMAND) {
            refreshExternalRuntimeSetup(forceProbe = true)
        }
    }

    private fun copySetupCommand(command: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.settings_external_command_clip_label), command),
        )
        Toast.makeText(
            this,
            getString(R.string.settings_external_command_copied),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun openTermux() {
        val launch = packageManager.getLaunchIntentForPackage(TermuxContract.PACKAGE_NAME)
        if (launch != null) {
            startActivity(launch)
        } else {
            openOfficialTermuxInstallPage()
        }
    }

    private fun openOfficialTermuxInstallPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TermuxContract.OFFICIAL_INSTALL_URL)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(
                    this,
                    getString(R.string.runtime_termux_download_failed),
                    Toast.LENGTH_SHORT,
                ).show()
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

    companion object {
        const val EXTRA_NORMAL_MODE = "settings.extra.NORMAL_MODE"
        const val EXTRA_OPEN_EXTERNAL_RUNTIME_SETUP = "settings.extra.OPEN_EXTERNAL_RUNTIME_SETUP"
        private const val REQUEST_RUN_COMMAND = 7401
    }
}
