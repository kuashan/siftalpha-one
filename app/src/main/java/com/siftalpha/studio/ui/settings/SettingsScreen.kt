package com.siftalpha.studio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.siftalpha.studio.R
import com.siftalpha.studio.ui.components.StudioPrimaryAction
import com.siftalpha.studio.ui.components.StudioSectionCard
import com.siftalpha.studio.ui.theme.StudioTheme
import com.siftalpha.studio.ui.theme.StudioThemeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentLanguage: String,
    currentBrowser: String?,
    versionName: String,
    applicationId: String,
    developerModeEnabled: Boolean,
    onBack: () -> Unit,
    onChangeLanguage: () -> Unit,
    onChangeBrowser: () -> Unit,
    onDeveloperModeChanged: (Boolean) -> Unit,
) {
    val spacing = StudioThemeTokens.spacing
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(R.string.common_back))
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
            StudioSectionCard {
                Text(
                    text = stringResource(R.string.settings_language_section),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = stringResource(R.string.settings_language_summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                Text(
                    text = stringResource(R.string.settings_language_current, currentLanguage),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                StudioPrimaryAction(
                    label = stringResource(R.string.settings_language_change),
                    onClick = onChangeLanguage,
                )
            }

            StudioSectionCard {
                Text(
                    text = stringResource(R.string.settings_browser_section),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = stringResource(R.string.settings_browser_summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                Text(
                    text = stringResource(
                        R.string.settings_browser_current,
                        currentBrowser ?: stringResource(R.string.settings_browser_not_selected),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                StudioPrimaryAction(
                    label = stringResource(R.string.settings_browser_change),
                    onClick = onChangeBrowser,
                )
            }

            StudioSectionCard {
                Text(
                    text = stringResource(R.string.settings_developer_mode_section),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = stringResource(R.string.settings_developer_mode_summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(
                            if (developerModeEnabled) {
                                R.string.settings_developer_mode_on
                            } else {
                                R.string.settings_developer_mode_off
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = developerModeEnabled,
                        onCheckedChange = onDeveloperModeChanged,
                    )
                }
            }

            StudioSectionCard {
                Text(
                    text = stringResource(R.string.settings_about_section),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = stringResource(R.string.settings_about_summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                Text(
                    text = stringResource(R.string.settings_version, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_application_id, applicationId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SettingsScreenLightPreview() {
    StudioTheme(darkTheme = false) {
        SettingsScreen(
            currentLanguage = "English",
            currentBrowser = "Chrome",
            versionName = "0.8.0-alpha3",
            applicationId = "com.siftalpha.studio",
            developerModeEnabled = false,
            onBack = {},
            onChangeLanguage = {},
            onChangeBrowser = {},
            onDeveloperModeChanged = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SettingsScreenDarkPreview() {
    StudioTheme(darkTheme = true) {
        SettingsScreen(
            currentLanguage = "English",
            currentBrowser = null,
            versionName = "0.8.0-alpha3",
            applicationId = "com.siftalpha.studio",
            developerModeEnabled = true,
            onBack = {},
            onChangeLanguage = {},
            onChangeBrowser = {},
        )
    }
}
