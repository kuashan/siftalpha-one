package com.siftalpha.studio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import com.siftalpha.studio.R
import com.siftalpha.studio.runtime.ExternalRuntimeSetupStage
import com.siftalpha.studio.runtime.ExternalRuntimeSetupStatus
import com.siftalpha.studio.ui.components.StudioPrimaryAction
import com.siftalpha.studio.ui.components.StudioSectionCard
import com.siftalpha.studio.ui.theme.StudioThemeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalRuntimeSetupScreen(
    status: ExternalRuntimeSetupStatus,
    externalAppsCommand: String,
    storageAccessCommand: String,
    prootCommand: String,
    ubuntuCommand: String,
    onBack: () -> Unit,
    onDownloadTermux: () -> Unit,
    onRequestPermission: () -> Unit,
    onCopyExternalAppsCommand: () -> Unit,
    onCopyStorageAccessCommand: () -> Unit,
    onCopyProotCommand: () -> Unit,
    onCopyUbuntuCommand: () -> Unit,
    onOpenTermux: () -> Unit,
    onRecheck: () -> Unit,
) {
    val spacing = StudioThemeTokens.spacing
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_external_runtime_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large, vertical = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            Text(
                text = stringResource(R.string.settings_external_runtime_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SetupStepCard(
                title = stringResource(R.string.settings_external_step_termux),
                complete = status.termuxInstalled,
                detail = if (status.termuxInstalled) {
                    stringResource(R.string.settings_external_status_complete)
                } else {
                    stringResource(R.string.settings_external_termux_missing)
                },
            ) {
                if (!status.termuxInstalled) {
                    StudioPrimaryAction(
                        label = stringResource(R.string.settings_external_download_termux),
                        onClick = onDownloadTermux,
                    )
                }
            }

            SetupStepCard(
                title = stringResource(R.string.settings_external_step_bridge),
                complete = status.allowExternalAppsReady == true,
                detail = when {
                    !status.termuxInstalled -> stringResource(R.string.settings_external_wait_termux)
                    !status.runCommandPermissionGranted ->
                        stringResource(R.string.settings_external_permission_missing)
                    status.allowExternalAppsReady == true ->
                        stringResource(R.string.settings_external_status_complete)
                    status.stage == ExternalRuntimeSetupStage.EXTERNAL_APPS ->
                        stringResource(R.string.settings_external_apps_missing)
                    else -> stringResource(R.string.settings_external_detecting)
                },
            ) {
                if (status.termuxInstalled && !status.runCommandPermissionGranted) {
                    StudioPrimaryAction(
                        label = stringResource(R.string.settings_external_grant_permission),
                        onClick = onRequestPermission,
                    )
                } else if (status.stage == ExternalRuntimeSetupStage.EXTERNAL_APPS) {
                    CommandBlock(
                        command = externalAppsCommand,
                        copyLabel = stringResource(R.string.settings_external_copy_setup),
                        onCopy = onCopyExternalAppsCommand,
                    )
                    Spacer(modifier = Modifier.height(spacing.medium))
                    OutlinedButton(
                        onClick = onOpenTermux,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_external_open_termux))
                    }
                }
            }

            SetupStepCard(
                title = stringResource(R.string.settings_external_step_storage),
                complete = status.storageAccessReady == true,
                detail = when {
                    status.storageAccessReady == true ->
                        stringResource(R.string.settings_external_status_complete)
                    status.stage == ExternalRuntimeSetupStage.STORAGE_ACCESS ->
                        stringResource(R.string.settings_external_storage_missing)
                    else -> stringResource(R.string.settings_external_wait_previous)
                },
            ) {
                if (status.stage == ExternalRuntimeSetupStage.STORAGE_ACCESS) {
                    CommandBlock(
                        command = storageAccessCommand,
                        copyLabel = stringResource(R.string.settings_external_copy_storage),
                        onCopy = onCopyStorageAccessCommand,
                    )
                    Spacer(modifier = Modifier.height(spacing.medium))
                    OutlinedButton(
                        onClick = onOpenTermux,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_external_open_termux))
                    }
                }
            }

            SetupStepCard(
                title = stringResource(R.string.settings_external_step_proot),
                complete = status.prootDistroReady == true,
                detail = when {
                    status.prootDistroReady == true ->
                        stringResource(R.string.settings_external_status_complete)
                    status.stage == ExternalRuntimeSetupStage.PROOT_DISTRO ->
                        stringResource(R.string.settings_external_proot_missing)
                    else -> stringResource(R.string.settings_external_wait_previous)
                },
            ) {
                if (status.stage == ExternalRuntimeSetupStage.PROOT_DISTRO) {
                    CommandBlock(
                        command = prootCommand,
                        copyLabel = stringResource(R.string.settings_external_copy_install),
                        onCopy = onCopyProotCommand,
                    )
                    Spacer(modifier = Modifier.height(spacing.medium))
                    OutlinedButton(
                        onClick = onOpenTermux,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_external_open_termux))
                    }
                }
            }

            SetupStepCard(
                title = stringResource(R.string.settings_external_step_ubuntu),
                complete = status.ubuntuReady == true,
                detail = when {
                    status.ubuntuReady == true ->
                        stringResource(R.string.settings_external_status_complete)
                    status.stage == ExternalRuntimeSetupStage.UBUNTU ->
                        stringResource(R.string.settings_external_ubuntu_missing)
                    else -> stringResource(R.string.settings_external_wait_previous)
                },
            ) {
                if (status.stage == ExternalRuntimeSetupStage.UBUNTU) {
                    CommandBlock(
                        command = ubuntuCommand,
                        copyLabel = stringResource(R.string.settings_external_copy_install),
                        onCopy = onCopyUbuntuCommand,
                    )
                    Spacer(modifier = Modifier.height(spacing.medium))
                    OutlinedButton(
                        onClick = onOpenTermux,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_external_open_termux))
                    }
                }
            }

            SetupStepCard(
                title = stringResource(R.string.settings_external_step_final),
                complete = status.ready,
                detail = when {
                    status.ready -> stringResource(R.string.settings_external_ready)
                    status.checking -> stringResource(R.string.settings_external_detecting)
                    else -> stringResource(R.string.settings_external_final_pending)
                },
            ) {
                if (status.checking) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    StudioPrimaryAction(
                        label = stringResource(R.string.settings_external_recheck),
                        onClick = onRecheck,
                    )
                }
                if (!status.detail.isNullOrBlank() && !status.ready) {
                    Spacer(modifier = Modifier.height(spacing.small))
                    Text(
                        text = status.detail.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupStepCard(
    title: String,
    complete: Boolean,
    detail: String,
    content: @Composable () -> Unit,
) {
    val spacing = StudioThemeTokens.spacing
    StudioSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    if (complete) {
                        R.string.settings_external_status_done
                    } else {
                        R.string.settings_external_status_pending
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (complete) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(modifier = Modifier.height(spacing.small))
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(spacing.medium))
        content()
    }
}

@Composable
private fun CommandBlock(
    command: String,
    copyLabel: String,
    onCopy: () -> Unit,
) {
    val spacing = StudioThemeTokens.spacing
    SelectionContainer {
        Text(
            text = command,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
    Spacer(modifier = Modifier.height(spacing.medium))
    StudioPrimaryAction(
        label = copyLabel,
        onClick = onCopy,
    )
}
