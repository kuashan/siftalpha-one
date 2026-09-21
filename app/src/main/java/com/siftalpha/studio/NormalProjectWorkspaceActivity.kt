package com.siftalpha.studio

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.siftalpha.studio.project.EmbeddedPythonProjectStager
import com.siftalpha.studio.project.ProjectConfigurationInspector
import com.siftalpha.studio.project.V04ProjectGateway
import com.siftalpha.studio.runtime.EmbeddedPythonRuntimeStateMapping
import com.siftalpha.studio.runtime.ProjectControlHub
import com.siftalpha.studio.runtime.ProjectRuntimeControlExecutor
import com.siftalpha.studio.runtime.ProjectRuntimeController
import com.siftalpha.studio.runtime.ProjectRuntimeSelection
import com.siftalpha.studio.runtime.ProjectRuntimeSelectionStore
import com.siftalpha.studio.runtime.ProjectSecretStore
import com.siftalpha.studio.runtime.RuntimeLifecycleStore
import com.siftalpha.studio.runtime.RuntimeState
import com.siftalpha.studio.runtime.SharedRuntimeLifecycleBridge
import com.siftalpha.studio.runtime.TermuxBackend
import com.siftalpha.studio.siftalphax.EmbeddedPythonEnvironmentManager
import com.siftalpha.studio.siftalphax.EmbeddedPythonSession
import com.siftalpha.studio.siftalphax.InternalAlpineEnvironmentManager
import com.siftalpha.studio.siftalphax.InternalAlpineSession
import com.siftalpha.studio.ui.components.StudioPrimaryAction
import com.siftalpha.studio.ui.components.StudioSectionCard
import com.siftalpha.studio.ui.theme.StudioTheme
import com.siftalpha.studio.ui.theme.StudioThemeTokens
import java.util.concurrent.Executors

/**
 * R48 Normal Mode（普通模式） single-project shell.
 *
 * It contains no independent Runtime（运行时） implementation. RUN / STOP are delegated through
 * ProjectControlHub（项目控制枢纽） to the same controller/backend used by the existing Developer
 * Workspace（开发者工作区）.
 */
class NormalProjectWorkspaceActivity : StudioComposeActivity() {

    private data class ScreenState(
        val projectName: String = "",
        val statusLabel: String = "",
        val busy: Boolean = false,
        val message: String? = null,
        val developerModeEnabled: Boolean = false,
        val runtimeState: RuntimeState = RuntimeState.UNKNOWN,
    )

    private lateinit var gateway: V04ProjectGateway
    private lateinit var project: V04ProjectGateway.RuntimeProject
    private lateinit var runtime: ProjectRuntimeController
    private lateinit var selectionStore: ProjectRuntimeSelectionStore
    private lateinit var lifecycleStore: RuntimeLifecycleStore
    private lateinit var controlHub: ProjectControlHub
    private lateinit var configurationUi: ProjectConfigurationUiController
    private val actionExecutor = Executors.newSingleThreadExecutor()
    private val screenState = mutableStateOf(ScreenState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectId = intent.getStringExtra(V04Activity.EXTRA_PROJECT_DOCUMENT_ID)
        if (projectId.isNullOrBlank()) {
            finish()
            return
        }

        gateway = V04ProjectGateway(this)
        project = gateway.projects().firstOrNull { it.summary.documentId == projectId }
            ?: run {
                Toast.makeText(this, getString(R.string.normal_project_missing), Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        selectionStore = ProjectRuntimeSelectionStore(this)
        lifecycleStore = RuntimeLifecycleStore(this)
        runtime = ProjectRuntimeController(
            gateway = gateway,
            embeddedPythonSession = EmbeddedPythonSession.shared(this),
            embeddedPythonProjectStager = EmbeddedPythonProjectStager(this),
            embeddedPythonEnvironmentManager = EmbeddedPythonEnvironmentManager(this),
            internalAlpineEnvironmentManager = InternalAlpineEnvironmentManager(this),
            internalAlpineSession = InternalAlpineSession.shared(this),
        )
        configurationUi = ProjectConfigurationUiController(
            activity = this,
            inspector = ProjectConfigurationInspector(this),
            store = ProjectSecretStore(this),
        ) {
            refreshSharedState()
        }
        controlHub = ProjectControlHub(
            selectionStore = selectionStore,
            executor = ProjectRuntimeControlExecutor(
                runtime = runtime,
                externalBackend = TermuxBackend(this),
            ),
            stateBridge = SharedRuntimeLifecycleBridge(lifecycleStore),
        )

        enableEdgeToEdge()
        refreshSharedState()
        setContent {
            StudioTheme {
                NormalProjectWorkspaceScreen(
                    state = screenState.value,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onRun = { runProject() },
                    onStop = { stopProject() },
                    onOpenDeveloper = { openDeveloperWorkspace() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::project.isInitialized) refreshSharedState()
    }

    override fun onDestroy() {
        actionExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun refreshSharedState(message: String? = screenState.value.message) {
        if (!::project.isInitialized || !::lifecycleStore.isInitialized) return
        val projectId = project.summary.documentId
        val selection = selectionStore.read(projectId)

        if (selection == ProjectRuntimeSelection.EMBEDDED_R) {
            runCatching { runtime.embeddedPythonSnapshotFor(projectId) }
                .getOrNull()
                ?.let { snapshot ->
                    val current = lifecycleStore.read(projectId)
                    lifecycleStore.write(
                        projectKey = projectId,
                        environmentReady = current.environmentReadyFor(selection),
                        runtimeState = EmbeddedPythonRuntimeStateMapping.toRuntimeState(snapshot),
                        failureReason = null,
                        runtimeSelection = selection,
                    )
                }
        }

        val lifecycle = lifecycleStore.read(projectId)
        screenState.value = screenState.value.copy(
            projectName = project.summary.name,
            statusLabel = lifecycle.runtimeState.uiLabel(
                this,
                lifecycle.environmentReadyFor(selection),
            ),
            message = message,
            developerModeEnabled = DeveloperModeStore(this).isEnabled(),
            runtimeState = lifecycle.runtimeState,
        )
    }

    private fun runProject() {
        if (screenState.value.busy) return
        val configuration = configurationUi.snapshot(
            project.summary.documentId,
            project.folderName,
        )
        screenState.value = screenState.value.copy(
            busy = true,
            message = getString(R.string.normal_project_starting),
        )
        actionExecutor.execute {
            val result = controlHub.run(
                project = project,
                request = ProjectControlHub.RunRequest(
                    requiredConfiguration = configuration.preflight.missingRequired.isNotEmpty(),
                ),
            )
            runOnUiThread {
                screenState.value = screenState.value.copy(busy = false)
                refreshSharedState(resultMessage(result))
            }
        }
    }

    private fun stopProject() {
        if (screenState.value.busy) return
        screenState.value = screenState.value.copy(
            busy = true,
            message = getString(R.string.normal_project_stopping),
        )
        actionExecutor.execute {
            val result = controlHub.stop(project)
            runOnUiThread {
                screenState.value = screenState.value.copy(busy = false)
                refreshSharedState(resultMessage(result))
            }
        }
    }

    private fun resultMessage(result: ProjectControlHub.Result): String = when (result) {
        is ProjectControlHub.Result.Dispatched -> when (result.action) {
            ProjectControlHub.Action.RUN -> getString(R.string.normal_project_run_dispatched)
            ProjectControlHub.Action.STOP -> getString(R.string.normal_project_stop_dispatched)
        }
        is ProjectControlHub.Result.NoOp -> getString(
            R.string.normal_project_noop,
            result.detail,
        )
        is ProjectControlHub.Result.Rejected -> getString(
            R.string.normal_project_rejected,
            result.detail,
        )
    }

    private fun openDeveloperWorkspace() {
        if (!DeveloperModeStore(this).isEnabled()) return
        startActivity(
            Intent(this, ProjectWorkspaceActivity::class.java).apply {
                putExtra(V04Activity.EXTRA_PROJECT_DOCUMENT_ID, project.summary.documentId)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalProjectWorkspaceScreen(
    state: NormalProjectWorkspaceActivity.ScreenState,
    onBack: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onOpenDeveloper: () -> Unit,
) {
    val spacing = StudioThemeTokens.spacing
    val runtimeActive = state.runtimeState == RuntimeState.STARTING ||
        state.runtimeState == RuntimeState.RUNNING

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(text = state.projectName) },
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
                .padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            StudioSectionCard {
                Text(
                    text = stringResource(R.string.normal_project_status_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = state.statusLabel,
                    style = MaterialTheme.typography.bodyLarge,
                )
                state.message?.takeIf { it.isNotBlank() }?.let { message ->
                    Spacer(modifier = Modifier.height(spacing.small))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            StudioSectionCard {
                Text(
                    text = stringResource(R.string.normal_project_actions_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                if (runtimeActive) {
                    StudioPrimaryAction(
                        label = stringResource(R.string.runtime_button_stop),
                        onClick = onStop,
                        enabled = !state.busy,
                    )
                } else {
                    StudioPrimaryAction(
                        label = stringResource(R.string.runtime_button_run),
                        onClick = onRun,
                        enabled = !state.busy,
                    )
                }
            }

            if (state.developerModeEnabled) {
                StudioSectionCard {
                    Text(
                        text = stringResource(R.string.normal_project_developer_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(spacing.small))
                    Text(
                        text = stringResource(R.string.normal_project_developer_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(spacing.medium))
                    OutlinedButton(
                        onClick = onOpenDeveloper,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.normal_project_open_developer))
                    }
                }
            }
        }
    }
}
