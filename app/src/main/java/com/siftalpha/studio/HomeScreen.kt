package com.siftalpha.studio

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.siftalpha.studio.project.ProjectStore
import com.siftalpha.studio.runtime.RuntimeState
import com.siftalpha.studio.ui.components.StudioPrimaryAction
import com.siftalpha.studio.ui.components.StudioSectionCard
import com.siftalpha.studio.ui.components.StudioStatusBadge
import com.siftalpha.studio.ui.components.StudioStatusTone
import com.siftalpha.studio.ui.theme.StudioTheme
import com.siftalpha.studio.ui.theme.StudioThemeTokens

enum class HomeBridgeState {
    READY,
    WAITING_PERMISSION,
    DETECTING,
    CONNECTED,
    ABNORMAL,
    UNAVAILABLE,
    RUNNING,
    SEND_FAILED,
}

data class HomeState(
    val rootSelected: Boolean = false,
    val rootName: String? = null,
    val projects: List<ProjectStore.ProjectSummary> = emptyList(),
    val projectRuntimeStates: Map<String, RuntimeState> = emptyMap(),
    val projectError: String? = null,
    val termuxInstalled: Boolean = false,
    val permissionGranted: Boolean = false,
    val bridgeState: HomeBridgeState = HomeBridgeState.READY,
    val commandOutput: String = "",
    val developerModeEnabled: Boolean = false,
)

private enum class ProjectFilter {
    ALL,
    PYTHON,
    NODE,
}

private enum class ProjectType {
    PYTHON,
    NODE,
    UNKNOWN,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    versionName: String,
    onSettings: () -> Unit,
    onConnectAcode: () -> Unit,
    onChooseRoot: () -> Unit,
    onNewProject: () -> Unit,
    onImportProject: () -> Unit,
    onImportGitHub: () -> Unit,
    onCreateProjectNormal: (String, String) -> Unit,
    onImportGitHubNormal: (String, String, String) -> Unit,
    onDeleteProjectNormal: (ProjectStore.ProjectSummary) -> Unit,
    onUpdateProjectDescriptionNormal: (ProjectStore.ProjectSummary, String) -> Unit,
    onUserStorage: () -> Unit,
    onRefreshProjects: () -> Unit,
    onOpenProject: (ProjectStore.ProjectSummary) -> Unit,
    onShowDetails: (ProjectStore.ProjectSummary) -> Unit,
    onDeleteProject: (ProjectStore.ProjectSummary) -> Unit,
    onRuntimeCenter: () -> Unit,
    onEmbeddedPython: () -> Unit,
    onEnvironment: () -> Unit,
    onTerminal: () -> Unit,
    onProbeEnvironment: () -> Unit,
    onTestTermux: () -> Unit,
    onRequestPermission: () -> Unit,
    onCopySetup: () -> Unit,
    onOpenTermux: () -> Unit,
    onCopyOutput: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterIndex by rememberSaveable { mutableIntStateOf(ProjectFilter.ALL.ordinal) }
    var projectManagementOpen by rememberSaveable { mutableStateOf(false) }
    var importOpen by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var normalCreateProjectOpen by rememberSaveable { mutableStateOf(false) }
    var normalGitHubImportOpen by rememberSaveable { mutableStateOf(false) }
    var normalDetailsProject by remember { mutableStateOf<ProjectStore.ProjectSummary?>(null) }
    var normalDeleteProject by remember { mutableStateOf<ProjectStore.ProjectSummary?>(null) }
    val selectedFilter = ProjectFilter.entries.getOrElse(filterIndex) { ProjectFilter.ALL }
    val visibleProjects = state.projects.filter { project ->
        matchesProject(project, selectedFilter, query)
    }
    val spacing = StudioThemeTokens.spacing
    val projectDirectoryReady = state.rootSelected && state.projectError == null
    val termuxLabel = when {
        !state.termuxInstalled -> stringResource(R.string.home_termux_permission_missing)
        !state.permissionGranted -> stringResource(R.string.home_termux_installed_permission_missing)
        else -> stringResource(R.string.home_termux_installed_permission_granted)
    }
    val bridgeLabel = stringResource(bridgeLabelResource(state.bridgeState))
    val bridgeTone = when (state.bridgeState) {
        HomeBridgeState.CONNECTED -> StudioStatusTone.Success
        HomeBridgeState.ABNORMAL,
        HomeBridgeState.UNAVAILABLE,
        HomeBridgeState.SEND_FAILED,
        -> StudioStatusTone.Warning
        else -> StudioStatusTone.Neutral
    }

    if (projectManagementOpen && state.developerModeEnabled) {
        val rootLabel = when {
            !state.rootSelected -> stringResource(R.string.home_project_root_unselected)
            state.projectError != null -> stringResource(R.string.home_root_access_failed)
            else -> stringResource(
                R.string.home_root_selected,
                state.rootName ?: stringResource(R.string.home_none),
            )
        }
        AlertDialog(
            onDismissRequest = { projectManagementOpen = false },
            title = {
                Text(
                    text = stringResource(
                        if (state.developerModeEnabled) {
                            R.string.home_section_project_management
                        } else {
                            R.string.home_project_location_title
                        },
                    ),
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    Text(
                        text = rootLabel,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (state.projectError != null) {
                        Text(
                            text = stringResource(
                                R.string.home_root_read_failed,
                                state.projectError,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    StudioPrimaryAction(
                        label = stringResource(R.string.home_connect_acode),
                        onClick = {
                            projectManagementOpen = false
                            onConnectAcode()
                        },
                    )
                    OutlinedButton(
                        onClick = {
                            projectManagementOpen = false
                            onChooseRoot()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.home_choose_other_root))
                    }
                    if (state.developerModeEnabled) {
                        OutlinedButton(
                            onClick = {
                                projectManagementOpen = false
                                onNewProject()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.home_new_python_project))
                        }
                        OutlinedButton(
                            onClick = {
                                projectManagementOpen = false
                                onRefreshProjects()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.home_refresh_projects))
                        }
                    }
                    Text(
                        text = stringResource(
                            if (state.developerModeEnabled) {
                                R.string.home_root_help
                            } else {
                                R.string.home_project_location_help
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { projectManagementOpen = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (importOpen && state.developerModeEnabled) {
        AlertDialog(
            onDismissRequest = { importOpen = false },
            title = {
                Text(text = stringResource(R.string.home_import_title))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    Text(
                        text = stringResource(R.string.home_import_summary),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.home_import_supported),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StudioPrimaryAction(
                        label = stringResource(R.string.home_import_choose_file),
                        onClick = {
                            importOpen = false
                            onImportProject()
                        },
                    )
                    OutlinedButton(
                        onClick = {
                            importOpen = false
                            onImportGitHub()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.home_import_github))
                    }
                    OutlinedButton(
                        onClick = {
                            importOpen = false
                            onNewProject()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.home_import_new_project))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { importOpen = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (moreOpen && state.developerModeEnabled) {
        AlertDialog(
            onDismissRequest = { moreOpen = false },
            title = {
                Text(text = stringResource(R.string.home_more_title))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    OutlinedButton(
                        onClick = {
                            moreOpen = false
                            onRefreshProjects()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.home_refresh_projects))
                    }
                    OutlinedButton(
                        onClick = {
                            moreOpen = false
                            onNewProject()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.home_new_python_project))
                    }
                    OutlinedButton(
                        onClick = {
                            moreOpen = false
                            onUserStorage()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.home_user_storage))
                    }
                    OutlinedButton(
                        onClick = {
                            moreOpen = false
                            onSettings()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.home_action_settings))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { moreOpen = false }) {
                    Text(text = stringResource(R.string.common_close))
                }
            },
        )
    }

    if (!state.developerModeEnabled) {
        if (projectManagementOpen) {
            SiftAlphaNormalProjectLocationDialog(
                state = state,
                onDismiss = { projectManagementOpen = false },
                onConnectAcode = {
                    projectManagementOpen = false
                    onConnectAcode()
                },
                onChooseRoot = {
                    projectManagementOpen = false
                    onChooseRoot()
                },
            )
        }

        if (importOpen) {
            SiftAlphaNormalImportDialog(
                onDismiss = { importOpen = false },
                onChooseFile = {
                    importOpen = false
                    onImportProject()
                },
                onGitHub = {
                    importOpen = false
                    normalGitHubImportOpen = true
                },
                onNewProject = {
                    importOpen = false
                    normalCreateProjectOpen = true
                },
            )
        }

        if (moreOpen) {
            SiftAlphaNormalMoreDialog(
                onDismiss = { moreOpen = false },
                onRefreshProjects = {
                    moreOpen = false
                    onRefreshProjects()
                },
                onNewProject = {
                    moreOpen = false
                    normalCreateProjectOpen = true
                },
                onUserStorage = {
                    moreOpen = false
                    onUserStorage()
                },
                onSettings = {
                    moreOpen = false
                    onSettings()
                },
            )
        }

        if (normalCreateProjectOpen) {
            SiftAlphaNormalCreateProjectDialog(
                onDismiss = { normalCreateProjectOpen = false },
                onCreate = { name, description ->
                    normalCreateProjectOpen = false
                    onCreateProjectNormal(name, description)
                },
            )
        }

        if (normalGitHubImportOpen) {
            SiftAlphaNormalGitHubImportDialog(
                onDismiss = { normalGitHubImportOpen = false },
                onImport = { url, branch, name ->
                    normalGitHubImportOpen = false
                    onImportGitHubNormal(url, branch, name)
                },
            )
        }

        normalDetailsProject?.let { project ->
            SiftAlphaNormalProjectDetailsDialog(
                project = project,
                onDismiss = { normalDetailsProject = null },
                onSaveDescription = { description ->
                    normalDetailsProject = null
                    onUpdateProjectDescriptionNormal(project, description)
                },
            )
        }

        normalDeleteProject?.let { project ->
            SiftAlphaNormalDeleteProjectDialog(
                project = project,
                onDismiss = { normalDeleteProject = null },
                onConfirm = {
                    normalDeleteProject = null
                    onDeleteProjectNormal(project)
                },
            )
        }
    }

    if (!state.developerModeEnabled) {
        SiftAlphaNormalHomeScreen(
            state = state,
            visibleProjects = visibleProjects,
            query = query,
            filterIndex = filterIndex,
            projectDirectoryReady = projectDirectoryReady,
            onQueryChange = { query = it },
            onFilterChange = { filterIndex = it },
            onImport = { importOpen = true },
            onNewProject = { normalCreateProjectOpen = true },
            onProjectLocation = { projectManagementOpen = true },
            onOpenProject = onOpenProject,
            onShowDetails = { project -> normalDetailsProject = project },
            onDeleteProject = { project -> normalDeleteProject = project },
            onMore = { moreOpen = true },
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.home_subtitle, versionName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (state.developerModeEnabled) {
                        TextButton(onClick = onSettings) {
                            Text(text = stringResource(R.string.home_action_settings))
                        }
                    } else {
                        TextButton(onClick = { moreOpen = true }) {
                            Text(text = stringResource(R.string.home_more_title))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.developerModeEnabled) {
                NavigationBar(windowInsets = WindowInsets.navigationBars) {
                    NavigationBarItem(
                        selected = true,
                        onClick = { },
                        icon = {
                            Text(text = stringResource(R.string.home_nav_home_icon))
                        },
                        label = {
                            Text(text = stringResource(R.string.home_nav_home))
                        },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onRuntimeCenter,
                        icon = {
                            Text(text = stringResource(R.string.home_nav_runtime_icon))
                        },
                        label = {
                            Text(text = stringResource(R.string.home_nav_runtime))
                        },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onEnvironment,
                        icon = {
                            Text(text = stringResource(R.string.home_nav_environment_icon))
                        },
                        label = {
                            Text(text = stringResource(R.string.home_nav_environment))
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = spacing.large,
                top = spacing.large,
                end = spacing.large,
                bottom = spacing.xxLarge,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            item {
                StudioSectionCard {
                    Text(
                        text = stringResource(R.string.home_w1c_summary),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (state.developerModeEnabled) {
                        Spacer(modifier = Modifier.height(spacing.medium))
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                            StudioStatusBadge(
                                label = termuxLabel,
                                tone = if (state.termuxInstalled && state.permissionGranted) {
                                    StudioStatusTone.Success
                                } else {
                                    StudioStatusTone.Warning
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            StudioStatusBadge(
                                label = bridgeLabel,
                                tone = bridgeTone,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (state.developerModeEnabled) {
                item {
                    StudioSectionCard {
                        StudioPrimaryAction(
                            label = stringResource(R.string.home_section_project_management),
                            onClick = { projectManagementOpen = true },
                        )
                    }
                }
                item {
                    StudioSectionCard {
                        Text(
                            text = stringResource(R.string.home_quick_actions),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(spacing.medium))
                        StudioPrimaryAction(
                            label = stringResource(R.string.home_runtime_center),
                            onClick = onRuntimeCenter,
                        )
                        OutlinedButton(
                            onClick = onEnvironment,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.home_runtime_storage_manager))
                        }
                        OutlinedButton(
                            onClick = onTerminal,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.home_terminal))
                        }
                        OutlinedButton(
                            onClick = onEmbeddedPython,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.home_siftalpha_x_experimental))
                        }
                    }
                }
            } else {
                item {
                    StudioSectionCard {
                        Text(
                            text = stringResource(R.string.home_project_entry_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(spacing.medium))
                        StudioPrimaryAction(
                            label = stringResource(R.string.home_import_title),
                            onClick = { importOpen = true },
                        )
                        OutlinedButton(
                            onClick = { projectManagementOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (state.rootSelected && state.projectError == null) {
                                    stringResource(
                                        R.string.home_project_location_current,
                                        state.rootName ?: stringResource(R.string.home_none),
                                    )
                                } else {
                                    stringResource(R.string.home_project_location_action)
                                },
                            )
                        }
                    }
                }
            }

            item {
                StudioSectionCard {
                    Text(
                        text = stringResource(R.string.home_all_projects, state.projects.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(spacing.medium))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(text = stringResource(R.string.home_project_search_hint))
                        },
                        singleLine = true,
                        enabled = projectDirectoryReady,
                    )
                    Spacer(modifier = Modifier.height(spacing.small))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        ProjectFilter.entries.forEachIndexed { index, option ->
                            FilterChip(
                                selected = selectedFilter == option,
                                onClick = { filterIndex = index },
                                enabled = projectDirectoryReady,
                                label = {
                                    Text(text = stringResource(projectFilterLabelResource(option)))
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(spacing.small))
                    Text(
                        text = stringResource(
                            R.string.home_project_count_filtered,
                            visibleProjects.size,
                            state.projects.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when {
                !state.rootSelected -> {
                    item {
                        Text(
                            text = stringResource(R.string.home_root_help),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.projectError != null -> {
                    item {
                        Text(
                            text = stringResource(
                                R.string.home_root_read_failed,
                                state.projectError,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                state.projects.isEmpty() -> {
                    item {
                        Text(
                            text = stringResource(R.string.home_projects_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                visibleProjects.isEmpty() -> {
                    item {
                        Text(
                            text = stringResource(R.string.home_no_matching_projects),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    items(
                        items = visibleProjects,
                        key = { it.documentId },
                    ) { project ->
                        ProjectCard(
                            project = project,
                            onOpen = { onOpenProject(project) },
                            onDetails = { onShowDetails(project) },
                            onDelete = { onDeleteProject(project) },
                        )
                    }
                }
            }

            if (state.developerModeEnabled) {
                item {
                    StudioSectionCard {
                        Text(
                            text = stringResource(R.string.home_section_runtime_bridge),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(spacing.small))
                        Text(
                            text = termuxLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(spacing.medium))
                        StudioPrimaryAction(
                            label = stringResource(R.string.home_probe_environment),
                            onClick = onProbeEnvironment,
                        )
                        OutlinedButton(
                            onClick = onTestTermux,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.home_test_termux))
                        }
                        OutlinedButton(
                            onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (state.permissionGranted) {
                                    stringResource(R.string.home_permission_granted_button)
                                } else {
                                    stringResource(R.string.home_request_permission)
                                },
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        ) {
                            OutlinedButton(
                                onClick = onCopySetup,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = stringResource(R.string.home_copy_termux_setup))
                            }
                            OutlinedButton(
                                onClick = onOpenTermux,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = stringResource(R.string.home_open_termux))
                            }
                        }
                    }
                }

                item {
                    StudioSectionCard {
                        Text(
                            text = stringResource(R.string.home_section_command_output),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(spacing.small))
                        Text(
                            text = state.commandOutput.ifBlank {
                                stringResource(R.string.home_no_command)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 24,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.commandOutput.isNotBlank()) {
                            Spacer(modifier = Modifier.height(spacing.small))
                            OutlinedButton(
                                onClick = onCopyOutput,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = stringResource(R.string.home_copy_output))
                            }
                        }
                    }
                }

                item {
                    StudioSectionCard {
                        Text(
                            text = stringResource(R.string.home_section_stage),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(spacing.small))
                        Text(
                            text = stringResource(R.string.home_stage_text, versionName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectStore.ProjectSummary,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = StudioThemeTokens.spacing
    val type = projectTypeOf(project)
    val typeLabel = stringResource(projectTypeLabelResource(type))
    val configurationLabel = stringResource(
        if (project.run.isBlank()) {
            R.string.home_project_not_configured
        } else {
            R.string.home_project_configured
        },
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(spacing.large)) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (project.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(spacing.xSmall))
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(spacing.small))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xSmall)) {
                StudioStatusBadge(
                    label = typeLabel,
                    tone = StudioStatusTone.Neutral,
                )
                StudioStatusBadge(
                    label = configurationLabel,
                    tone = if (project.run.isBlank()) {
                        StudioStatusTone.Warning
                    } else {
                        StudioStatusTone.Success
                    },
                )
            }
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                text = stringResource(R.string.home_source, project.source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = project.entry.ifBlank {
                    stringResource(R.string.home_none)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = project.run.ifBlank {
                    stringResource(R.string.home_none)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.home_open))
            }
            Spacer(modifier = Modifier.height(spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                OutlinedButton(
                    onClick = onDetails,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.home_details))
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.home_delete))
                }
            }
        }
    }
}

private fun matchesProject(
    project: ProjectStore.ProjectSummary,
    filter: ProjectFilter,
    rawQuery: String,
): Boolean {
    val query = rawQuery.trim()
    val queryMatches = query.isBlank() || listOf(
        project.name,
        project.description,
        project.source,
        project.entry,
        project.run,
    ).any { it.contains(query, ignoreCase = true) }
    if (!queryMatches) return false

    return when (filter) {
        ProjectFilter.ALL -> true
        ProjectFilter.PYTHON -> projectTypeOf(project) == ProjectType.PYTHON
        ProjectFilter.NODE -> projectTypeOf(project) == ProjectType.NODE
    }
}

private fun projectTypeOf(project: ProjectStore.ProjectSummary): ProjectType {
    val entry = project.entry.lowercase()
    val run = project.run.lowercase()
    return when {
        entry.endsWith(".py") || run.startsWith("python") -> ProjectType.PYTHON
        entry == "package.json" || run.contains("npm") || run.contains("node") -> ProjectType.NODE
        else -> ProjectType.UNKNOWN
    }
}

private fun projectFilterLabelResource(filter: ProjectFilter): Int = when (filter) {
    ProjectFilter.ALL -> R.string.home_filter_all
    ProjectFilter.PYTHON -> R.string.home_filter_python
    ProjectFilter.NODE -> R.string.home_filter_node
}

private fun projectTypeLabelResource(type: ProjectType): Int = when (type) {
    ProjectType.PYTHON -> R.string.home_project_type_python
    ProjectType.NODE -> R.string.home_project_type_node
    ProjectType.UNKNOWN -> R.string.home_project_type_unknown
}

private fun bridgeLabelResource(state: HomeBridgeState): Int = when (state) {
    HomeBridgeState.READY -> R.string.home_bridge_ready_probe
    HomeBridgeState.WAITING_PERMISSION -> R.string.home_bridge_wait_permission
    HomeBridgeState.DETECTING -> R.string.home_bridge_detecting
    HomeBridgeState.CONNECTED -> R.string.home_bridge_connected
    HomeBridgeState.ABNORMAL -> R.string.home_bridge_abnormal
    HomeBridgeState.UNAVAILABLE -> R.string.home_bridge_unavailable
    HomeBridgeState.RUNNING -> R.string.home_bridge_command_running
    HomeBridgeState.SEND_FAILED -> R.string.home_bridge_send_failed
}
