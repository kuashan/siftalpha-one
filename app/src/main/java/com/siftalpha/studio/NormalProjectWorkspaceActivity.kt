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
import androidx.compose.material3.Button
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
import com.siftalpha.studio.presentation.NormalProjectPrimaryActionPolicy
import com.siftalpha.studio.presentation.ProjectActionPolicy
import com.siftalpha.studio.presentation.ProjectUiSnapshot
import com.siftalpha.studio.runtime.RuntimeLifecycleOperation
import com.siftalpha.studio.runtime.RuntimeLifecycleResolver
import com.siftalpha.studio.runtime.RuntimeLifecycleState
import com.siftalpha.studio.runtime.RuntimeOperationAction
import com.siftalpha.studio.runtime.RuntimeOperationPhase
import com.siftalpha.studio.runtime.RuntimeOperationProvider
import com.siftalpha.studio.runtime.RuntimeOperationRecord
import com.siftalpha.studio.runtime.RuntimeResult
import com.siftalpha.studio.runtime.RuntimeWebUiStatus
import com.siftalpha.studio.runtime.TermuxResultBus
import com.siftalpha.studio.runtime.EmbeddedPythonRuntimeStateMapping
import com.siftalpha.studio.runtime.ExternalProviderPreflight
import com.siftalpha.studio.runtime.ProjectControlHub
import com.siftalpha.studio.runtime.ProjectRuntimeControlExecutor
import com.siftalpha.studio.runtime.ProjectRuntimeController
import com.siftalpha.studio.runtime.ProjectRuntimeSelection
import com.siftalpha.studio.runtime.ProjectRuntimeSelectionStore
import com.siftalpha.studio.runtime.ProjectRuntimeSelectionChangePolicy
import com.siftalpha.studio.runtime.PrepareProgressProbe
import com.siftalpha.studio.runtime.RuntimeOperationStore
import com.siftalpha.studio.runtime.ProjectSecretStore
import com.siftalpha.studio.runtime.RuntimeLifecycleStore
import com.siftalpha.studio.runtime.RuntimeState
import com.siftalpha.studio.runtime.SharedRuntimeLifecycleBridge
import com.siftalpha.studio.runtime.TermuxBackend
import com.siftalpha.studio.siftalphax.EmbeddedPythonEnvironmentManager
import com.siftalpha.studio.siftalphax.EmbeddedPythonSession
import com.siftalpha.studio.siftalphax.InternalAlpineEnvironmentManager
import com.siftalpha.studio.siftalphax.InternalAlpineSession
import com.siftalpha.studio.ui.components.StudioSectionCard
import com.siftalpha.studio.ui.theme.StudioTheme
import com.siftalpha.studio.ui.theme.StudioThemeTokens
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * R48 Normal Mode（普通模式） single-project shell.
 *
 * It contains no independent Runtime（运行时） implementation. RUN / STOP are delegated through
 * ProjectControlHub（项目控制枢纽） to the same controller/backend used by the existing Developer
 * Workspace（开发者工作区）.
 */
class NormalProjectWorkspaceActivity : StudioComposeActivity() {

    data class ScreenState(
        val projectName: String = "",
        val statusLabel: String = "",
        val busy: Boolean = false,
        val message: String? = null,
        val developerModeEnabled: Boolean = false,
        val runtimeState: RuntimeState = RuntimeState.UNKNOWN,
        val runtimeSelection: ProjectRuntimeSelection = ProjectRuntimeSelection.TERMUX,
        val runtimeSelectionCanChange: Boolean = true,
        val primaryAction: NormalProjectPrimaryActionPolicy.Action =
            NormalProjectPrimaryActionPolicy.Action.PREPARE_PROJECT,
    )

    private lateinit var gateway: V04ProjectGateway
    private lateinit var project: V04ProjectGateway.RuntimeProject
    private lateinit var runtime: ProjectRuntimeController
    private lateinit var selectionStore: ProjectRuntimeSelectionStore
    private lateinit var lifecycleStore: RuntimeLifecycleStore
    private lateinit var operationStore: RuntimeOperationStore
    private lateinit var controlHub: ProjectControlHub
    private lateinit var sharedLifecycleBridge: SharedRuntimeLifecycleBridge
    private lateinit var externalBackend: TermuxBackend
    private lateinit var externalProviderPreflight: ExternalProviderPreflight
    private lateinit var externalProviderUi: ExternalProviderPreflightUiCoordinator
    private lateinit var prepareLiveProgress: PrepareLiveProgressController
    private lateinit var configurationUi: ProjectConfigurationUiController
    private val actionExecutor = Executors.newSingleThreadExecutor()
    private val prepareExecutor = Executors.newSingleThreadExecutor()
    private var internalPrepareFuture: Future<*>? = null
    private var externalPrepareExecutionId: Int? = null
    private var externalStopExecutionId: Int? = null
    private var externalPrepareCancelRequested: Boolean = false
    private val screenState = mutableStateOf(ScreenState())

    private val resultListener: (RuntimeResult) -> Unit = { result ->
        runOnUiThread {
            if (::prepareLiveProgress.isInitialized && prepareLiveProgress.consumeIfProbe(result)) {
                return@runOnUiThread
            }
            when (result.executionId) {
                externalPrepareExecutionId -> handleExternalPrepareResult(result)
                externalStopExecutionId -> handleExternalStopResult(result)
                else -> Unit
            }
        }
    }

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
        operationStore = RuntimeOperationStore(this)
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
        externalBackend = TermuxBackend(this)
        externalProviderPreflight = ExternalProviderPreflight(this, externalBackend)
        externalProviderUi = ExternalProviderPreflightUiCoordinator(
            activity = this,
            backend = externalBackend,
            preflight = externalProviderPreflight,
            onStage = { stage ->
                runOnUiThread { handleExternalPreflightStage(stage) }
            },
        )
        prepareLiveProgress = PrepareLiveProgressController(
            context = this,
            backend = externalBackend,
            gateway = gateway,
            runtime = runtime,
            renderSnapshot = { _, snapshot ->
                runOnUiThread { handlePrepareProgress(snapshot) }
            },
        ) { _, _ -> Unit }
        sharedLifecycleBridge = SharedRuntimeLifecycleBridge(lifecycleStore)
        controlHub = ProjectControlHub(
            selectionStore = selectionStore,
            executor = ProjectRuntimeControlExecutor(
                runtime = runtime,
                externalBackend = externalBackend,
                externalProviderPreflight = externalProviderPreflight,
                internalPrepareProgress = {
                    runOnUiThread {
                        if (::project.isInitialized) {
                            screenState.value = screenState.value.copy(
                                statusLabel = getString(R.string.runtime_lifecycle_preparing),
                                message = getString(R.string.normal_project_preparing),
                                runtimeState = RuntimeState.PREPARING,
                                runtimeSelectionCanChange = false,
                                primaryAction = NormalProjectPrimaryActionPolicy.Action.STOP,
                            )
                        }
                    }
                },
            ),
            stateBridge = sharedLifecycleBridge,
        )

        enableEdgeToEdge()
        refreshSharedState()
        setContent {
            StudioTheme {
                NormalProjectWorkspaceScreen(
                    state = screenState.value,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onPrepare = { prepareProject() },
                    onConfigure = { configureProject() },
                    onRun = { runProject() },
                    onStop = { stopProject() },
                    onSelectRuntime = { selectRuntime(it) },
                    onOpenDeveloper = { openDeveloperWorkspace() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        TermuxResultBus.addListener(resultListener)
        externalProviderUi.onStart()
        prepareLiveProgress.resume()
        if (::project.isInitialized) {
            reconcileExternalPrepareOperation()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::externalProviderUi.isInitialized) externalProviderUi.onResume()
        if (::project.isInitialized) refreshSharedState()
    }

    override fun onStop() {
        if (::prepareLiveProgress.isInitialized) prepareLiveProgress.pause()
        if (::externalProviderUi.isInitialized) externalProviderUi.onStop()
        TermuxResultBus.removeListener(resultListener)
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::externalProviderUi.isInitialized) {
            externalProviderUi.onRequestPermissionsResult(requestCode, grantResults)
        }
    }

    override fun onDestroy() {
        internalPrepareFuture?.cancel(true)
        prepareExecutor.shutdownNow()
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
        val operation = operationStore.read(projectId)?.takeUnless { it.terminal }
        val configuration = configurationUi.snapshot(
            project.summary.documentId,
            project.folderName,
        )
        val environmentReady = lifecycle.environmentReadyFor(selection)
        val lifecycleState = RuntimeLifecycleResolver.resolve(
            environmentReady = environmentReady,
            runtimeState = lifecycle.runtimeState,
            operation = operation?.action?.toLifecycleOperation() ?: RuntimeLifecycleOperation.NONE,
            configurationRequired = configuration.preflight.missingRequired.isNotEmpty(),
            processActive = lifecycle.runtimeState in setOf(
                RuntimeState.PREPARING,
                RuntimeState.STARTING,
                RuntimeState.RUNNING,
            ),
        )
        val snapshot = ProjectUiSnapshot(
            identity = ProjectUiSnapshot.Identity(
                documentId = project.summary.documentId,
                folderName = project.folderName,
                displayName = project.summary.name,
                sourceUrl = project.sourceUrl,
            ),
            runtime = ProjectUiSnapshot.Runtime.fromPlannerSelection(
                selection = project.runtimeSelection,
                supported = selection == ProjectRuntimeSelection.EMBEDDED_R ||
                    (externalBackend.isAvailable() && runtime.runtimeSupported()),
                stopCapability = lifecycle.runtimeState in setOf(
                    RuntimeState.PREPARING,
                    RuntimeState.STARTING,
                    RuntimeState.RUNNING,
                ),
            ),
            environment = ProjectUiSnapshot.Environment.from(environmentReady),
            configuration = ProjectUiSnapshot.Configuration(
                requiredCount = configuration.preflight.requiredCount,
                configuredRequiredCount = configuration.preflight.configuredRequiredCount,
                missingRequiredNames = configuration.preflight.missingRequired.map { it.name },
                credentialCandidateCount = configuration.preflight.credentialCandidateCount,
                runtimeConfigurationDiscovered = configuration.runtimeConfigurationDiscovered,
                optionalMissingCount = configuration.preflight.optionalMissingCount,
                optionalConfiguredCount = configuration.preflight.optionalConfiguredCount,
            ),
            lifecycle = lifecycle.runtimeState,
            web = ProjectUiSnapshot.Web(
                expected = false,
                status = RuntimeWebUiStatus.AUTO_DETECT,
                endpointReachable = null,
            ),
            pending = operation?.action?.toUiOperation()?.let { action ->
                ProjectUiSnapshot.PendingOperation(action, operation.executionId)
            },
            evidence = ProjectUiSnapshot.Evidence(
                lifecycle = if (lifecycle.runtimeState == RuntimeState.UNKNOWN) {
                    ProjectUiSnapshot.LifecycleEvidence.NONE
                } else {
                    ProjectUiSnapshot.LifecycleEvidence.CACHED
                },
                environment = if (environmentReady == null) {
                    ProjectUiSnapshot.EnvironmentEvidence.NONE
                } else {
                    ProjectUiSnapshot.EnvironmentEvidence.CACHED
                },
            ),
            lifecycleState = lifecycleState,
            failureReason = lifecycle.failureReason,
        )
        val policy = ProjectActionPolicy.resolve(snapshot, selection)
        val primaryAction = NormalProjectPrimaryActionPolicy.resolve(policy)
        val selectionCanChange = ProjectRuntimeSelectionChangePolicy.canChange(
            ProjectRuntimeSelectionChangePolicy.Input(
                runtimeState = lifecycle.runtimeState,
                operation = operation,
            ),
        )
        screenState.value = screenState.value.copy(
            projectName = project.summary.name,
            statusLabel = lifecycleState.uiLabel(this),
            message = message,
            developerModeEnabled = DeveloperModeStore(this).isEnabled(),
            runtimeState = lifecycle.runtimeState,
            runtimeSelection = selection,
            runtimeSelectionCanChange = selectionCanChange,
            primaryAction = primaryAction,
        )
    }

    private fun selectRuntime(selection: ProjectRuntimeSelection) {
        if (screenState.value.busy) return
        val projectId = project.summary.documentId
        val current = selectionStore.read(projectId)
        if (current == selection) return

        refreshSharedState()
        val lifecycle = lifecycleStore.read(projectId)
        val canChange = ProjectRuntimeSelectionChangePolicy.canChange(
            ProjectRuntimeSelectionChangePolicy.Input(
                runtimeState = lifecycle.runtimeState,
                operation = operationStore.read(projectId),
            ),
        )
        if (!canChange) {
            refreshSharedState(getString(R.string.normal_runtime_selection_busy))
            return
        }

        selectionStore.write(projectId, selection)
        refreshSharedState(
            getString(
                R.string.normal_runtime_selection_saved,
                runtimeSelectionLabel(selection),
            ),
        )
    }

    private fun runtimeSelectionLabel(selection: ProjectRuntimeSelection): String = when (selection) {
        ProjectRuntimeSelection.EMBEDDED_R -> getString(R.string.normal_runtime_internal)
        ProjectRuntimeSelection.TERMUX -> getString(R.string.normal_runtime_external)
    }

    private fun prepareProject(externalPreflightPassed: Boolean = false) {
        if (screenState.value.busy) return
        val selection = selectionStore.read(project.summary.documentId)
        if (selection == ProjectRuntimeSelection.TERMUX && !externalPreflightPassed) {
            externalProviderUi.runWhenReady {
                prepareProject(externalPreflightPassed = true)
            }
            return
        }
        screenState.value = screenState.value.copy(
            busy = false,
            statusLabel = getString(R.string.runtime_lifecycle_preparing),
            message = getString(R.string.normal_project_preparing),
            runtimeState = RuntimeState.PREPARING,
            runtimeSelectionCanChange = false,
            primaryAction = NormalProjectPrimaryActionPolicy.Action.STOP,
        )
        internalPrepareFuture = prepareExecutor.submit {
            val result = controlHub.prepare(project)
            runOnUiThread {
                internalPrepareFuture = null
                when (result) {
                    is ProjectControlHub.Result.Dispatched -> {
                        if (
                            result.action == ProjectControlHub.Action.PREPARE &&
                            result.provider == RuntimeOperationProvider.EXTERNAL &&
                            result.executionId != null
                        ) {
                            registerExternalPrepare(result.executionId)
                            screenState.value = screenState.value.copy(busy = false)
                            refreshSharedState(getString(R.string.normal_project_preparing))
                            return@runOnUiThread
                        }
                    }
                    else -> Unit
                }
                screenState.value = screenState.value.copy(busy = false)
                refreshSharedState(resultMessage(result))
            }
        }
        if (selection == ProjectRuntimeSelection.EMBEDDED_R) {
            screenState.value = screenState.value.copy(
                primaryAction = NormalProjectPrimaryActionPolicy.Action.STOP,
            )
        }
    }

    private fun configureProject() {
        if (screenState.value.busy) return
        configurationUi.showConfiguration(
            projectName = project.summary.name,
            projectDocumentId = project.summary.documentId,
            folderName = project.folderName,
        ) {
            refreshSharedState(getString(R.string.normal_project_configuration_saved))
        }
    }

    private fun runProject(externalPreflightPassed: Boolean = false) {
        if (screenState.value.busy) return
        val selection = selectionStore.read(project.summary.documentId)
        if (selection == ProjectRuntimeSelection.TERMUX && !externalPreflightPassed) {
            externalProviderUi.runWhenReady {
                runProject(externalPreflightPassed = true)
            }
            return
        }
        val configuration = configurationUi.snapshot(
            project.summary.documentId,
            project.folderName,
        )
        screenState.value = screenState.value.copy(
            busy = true,
            statusLabel = getString(R.string.runtime_lifecycle_starting),
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
        val selection = selectionStore.read(project.summary.documentId)
        if (
            selection == ProjectRuntimeSelection.EMBEDDED_R &&
            screenState.value.runtimeState == RuntimeState.PREPARING
        ) {
            internalPrepareFuture?.cancel(true)
            internalPrepareFuture = null
            sharedLifecycleBridge.cancelPrepare(project, selection)
            screenState.value = screenState.value.copy(busy = false)
            refreshSharedState(getString(R.string.normal_project_prepare_stopped))
            return
        }
        if (screenState.value.busy) return
        externalPrepareCancelRequested =
            selection == ProjectRuntimeSelection.TERMUX &&
                screenState.value.runtimeState == RuntimeState.PREPARING
        if (externalPrepareCancelRequested && ::prepareLiveProgress.isInitialized) {
            prepareLiveProgress.finish(project.folderName)
        }
        screenState.value = screenState.value.copy(
            busy = true,
            message = getString(R.string.normal_project_stopping),
        )
        actionExecutor.execute {
            val result = controlHub.stop(project)
            runOnUiThread {
                if (
                    result is ProjectControlHub.Result.Dispatched &&
                    result.action == ProjectControlHub.Action.STOP &&
                    result.provider == RuntimeOperationProvider.EXTERNAL &&
                    result.executionId != null
                ) {
                    externalStopExecutionId = result.executionId
                    TermuxResultBus.consume(result.executionId)?.let(resultListener)
                    if (externalStopExecutionId != null) {
                        refreshSharedState(getString(R.string.normal_project_stopping))
                    }
                } else {
                    screenState.value = screenState.value.copy(busy = false)
                    refreshSharedState(resultMessage(result))
                }
            }
        }
    }

    private fun handleExternalPreflightStage(stage: ExternalProviderPreflightUiStage) {
        if (!::project.isInitialized) return
        when (stage) {
            ExternalProviderPreflightUiStage.CHECKING_PROVIDER ->
                showNormalTransientStatus(R.string.normal_preflight_checking_provider)
            ExternalProviderPreflightUiStage.WAITING_PERMISSION ->
                showNormalTransientStatus(R.string.normal_preflight_waiting_permission)
            ExternalProviderPreflightUiStage.CHECKING_TERMUX ->
                showNormalTransientStatus(R.string.normal_preflight_checking_termux)
            ExternalProviderPreflightUiStage.TERMUX_CONFIGURATION_REQUIRED ->
                showNormalTransientStatus(R.string.normal_preflight_termux_configuration)
            ExternalProviderPreflightUiStage.TERMUX_NOT_READY ->
                showNormalTransientStatus(R.string.normal_preflight_waiting_termux)
            ExternalProviderPreflightUiStage.READY -> {
                screenState.value = screenState.value.copy(busy = false)
            }
            ExternalProviderPreflightUiStage.CANCELLED -> {
                screenState.value = screenState.value.copy(busy = false)
                refreshSharedState(message = null)
            }
        }
    }

    private fun showNormalTransientStatus(messageRes: Int) {
        val message = getString(messageRes)
        screenState.value = screenState.value.copy(
            busy = true,
            statusLabel = message,
            message = message,
            runtimeSelectionCanChange = false,
        )
    }

    private fun handlePrepareProgress(snapshot: PrepareProgressProbe.Snapshot) {
        val messageRes = when (snapshot.stage) {
            "STARTING" -> R.string.runtime_prepare_live_stage_starting
            "CREATE_OR_REUSE_VENV" -> R.string.runtime_prepare_live_stage_venv
            "INSTALL_REQUIREMENTS" -> R.string.runtime_prepare_live_stage_requirements
            "INSTALL_PYPROJECT" -> R.string.runtime_prepare_live_stage_pyproject
            "FINALIZING" -> R.string.runtime_prepare_live_stage_finalizing
            else -> R.string.runtime_prepare_live_stage_unknown
        }
        screenState.value = screenState.value.copy(
            statusLabel = getString(R.string.runtime_lifecycle_preparing),
            message = getString(messageRes),
            runtimeState = RuntimeState.PREPARING,
            runtimeSelectionCanChange = false,
            primaryAction = NormalProjectPrimaryActionPolicy.Action.STOP,
        )
    }

    private fun resultMessage(result: ProjectControlHub.Result): String = when (result) {
        is ProjectControlHub.Result.Completed -> when (result.action) {
            ProjectControlHub.Action.PREPARE -> if (result.environmentReady) {
                getString(R.string.normal_project_prepare_ready)
            } else {
                getString(R.string.normal_project_prepare_failed)
            }
            ProjectControlHub.Action.RUN -> getString(R.string.normal_project_run_dispatched)
            ProjectControlHub.Action.STOP -> getString(R.string.normal_project_stop_dispatched)
        }
        is ProjectControlHub.Result.Dispatched -> when (result.action) {
            ProjectControlHub.Action.PREPARE -> getString(R.string.normal_project_preparing)
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

    private fun registerExternalPrepare(executionId: Int) {
        externalPrepareExecutionId = executionId
        externalPrepareCancelRequested = false
        val projectId = project.summary.documentId
        val now = System.currentTimeMillis()
        val generation = operationStore.lastGeneration(projectId) + 1L
        operationStore.write(
            RuntimeOperationRecord(
                projectId = projectId,
                provider = RuntimeOperationProvider.EXTERNAL,
                action = RuntimeOperationAction.PREPARE,
                executionId = executionId,
                generation = generation,
                startedAtEpochMs = now,
                deadlineAtEpochMs = null,
                phase = RuntimeOperationPhase.ACTIVE,
            ),
        )
        prepareLiveProgress.start(project.folderName, executionId)
        TermuxResultBus.consume(executionId)?.let(resultListener)
    }

    private fun reconcileExternalPrepareOperation() {
        val record = operationStore.read(project.summary.documentId) ?: return
        if (
            !record.terminal &&
            record.provider == RuntimeOperationProvider.EXTERNAL &&
            record.action == RuntimeOperationAction.PREPARE &&
            record.executionId != null
        ) {
            externalPrepareExecutionId = record.executionId
            prepareLiveProgress.start(project.folderName, record.executionId)
            TermuxResultBus.consume(record.executionId)?.let(resultListener)
        }
    }

    private fun handleExternalPrepareResult(result: RuntimeResult) {
        if (result.executionId != externalPrepareExecutionId) return
        prepareLiveProgress.finish(project.folderName)
        TermuxResultBus.consume(result.executionId)
        val prepared = result.exitCode == 0 &&
            result.internalErrorMessage.isBlank() &&
            "SIFTALPHA_ENV=READY" in result.stdout
        val failure = if (prepared) {
            null
        } else {
            result.internalErrorMessage
                .ifBlank { result.stderr }
                .ifBlank { "External prepare failed (exitCode=" + result.exitCode + ")" }
                .trim()
                .take(240)
        }
        sharedLifecycleBridge.completeExternalPrepare(
            project = project,
            selection = ProjectRuntimeSelection.TERMUX,
            prepared = prepared,
            failureReason = failure,
        )
        operationStore.clear(project.summary.documentId)
        externalPrepareExecutionId = null
        screenState.value = screenState.value.copy(busy = false)
        refreshSharedState(
            if (prepared) {
                getString(R.string.normal_project_prepare_ready)
            } else {
                getString(R.string.normal_project_prepare_failed)
            },
        )
    }

    private fun handleExternalStopResult(result: RuntimeResult) {
        if (result.executionId != externalStopExecutionId) return
        TermuxResultBus.consume(result.executionId)
        val success = result.exitCode == 0 && result.internalErrorMessage.isBlank()
        externalStopExecutionId = null
        if (success) {
            if (externalPrepareCancelRequested) {
                externalPrepareExecutionId?.let(TermuxResultBus::consume)
                externalPrepareExecutionId = null
                operationStore.clear(project.summary.documentId)
                sharedLifecycleBridge.cancelPrepare(
                    project = project,
                    selection = ProjectRuntimeSelection.TERMUX,
                )
            } else {
                sharedLifecycleBridge.completeExternalStop(
                    project = project,
                    selection = ProjectRuntimeSelection.TERMUX,
                )
            }
        }
        externalPrepareCancelRequested = false
        screenState.value = screenState.value.copy(busy = false)
        refreshSharedState(
            if (success) {
                getString(R.string.normal_project_stop_dispatched)
            } else {
                getString(R.string.normal_project_rejected, result.internalErrorMessage)
            },
        )
    }

    private fun RuntimeOperationAction.toLifecycleOperation(): RuntimeLifecycleOperation = when (this) {
        RuntimeOperationAction.PREPARE -> RuntimeLifecycleOperation.PREPARE
        RuntimeOperationAction.START -> RuntimeLifecycleOperation.START
        RuntimeOperationAction.STATUS -> RuntimeLifecycleOperation.STATUS
        RuntimeOperationAction.LOGS -> RuntimeLifecycleOperation.LOGS
        RuntimeOperationAction.STOP -> RuntimeLifecycleOperation.STOP
        RuntimeOperationAction.CLEAN -> RuntimeLifecycleOperation.CLEAN
    }

    private fun RuntimeOperationAction.toUiOperation(): ProjectUiSnapshot.Operation = when (this) {
        RuntimeOperationAction.PREPARE -> ProjectUiSnapshot.Operation.PREPARE
        RuntimeOperationAction.START -> ProjectUiSnapshot.Operation.START
        RuntimeOperationAction.STATUS -> ProjectUiSnapshot.Operation.STATUS
        RuntimeOperationAction.LOGS -> ProjectUiSnapshot.Operation.LOGS
        RuntimeOperationAction.STOP -> ProjectUiSnapshot.Operation.STOP
        RuntimeOperationAction.CLEAN -> ProjectUiSnapshot.Operation.CLEAN
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
    onPrepare: () -> Unit,
    onConfigure: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onSelectRuntime: (ProjectRuntimeSelection) -> Unit,
    onOpenDeveloper: () -> Unit,
) {
    val spacing = StudioThemeTokens.spacing
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
                    text = stringResource(R.string.normal_runtime_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = stringResource(
                        R.string.normal_runtime_current,
                        when (state.runtimeSelection) {
                            ProjectRuntimeSelection.EMBEDDED_R ->
                                stringResource(R.string.normal_runtime_internal)
                            ProjectRuntimeSelection.TERMUX ->
                                stringResource(R.string.normal_runtime_external)
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    OutlinedButton(
                        onClick = { onSelectRuntime(ProjectRuntimeSelection.EMBEDDED_R) },
                        enabled = state.runtimeSelectionCanChange &&
                            state.runtimeSelection != ProjectRuntimeSelection.EMBEDDED_R &&
                            !state.busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.normal_runtime_internal_short))
                    }
                    OutlinedButton(
                        onClick = { onSelectRuntime(ProjectRuntimeSelection.TERMUX) },
                        enabled = state.runtimeSelectionCanChange &&
                            state.runtimeSelection != ProjectRuntimeSelection.TERMUX &&
                            !state.busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.normal_runtime_external_short))
                    }
                }
                if (!state.runtimeSelectionCanChange) {
                    Spacer(modifier = Modifier.height(spacing.small))
                    Text(
                        text = stringResource(R.string.normal_runtime_selection_locked),
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
                when (state.primaryAction) {
                    NormalProjectPrimaryActionPolicy.Action.PREPARE_PROJECT -> {
                        Button(
                            onClick = onPrepare,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.normal_project_prepare_action))
                        }
                    }
                    NormalProjectPrimaryActionPolicy.Action.CONFIGURE -> {
                        Button(
                            onClick = onConfigure,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.normal_project_configure_action))
                        }
                    }
                    NormalProjectPrimaryActionPolicy.Action.RUN -> {
                        Button(
                            onClick = onRun,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.runtime_button_run))
                        }
                    }
                    NormalProjectPrimaryActionPolicy.Action.STOP -> {
                        Button(
                            onClick = onStop,
                            enabled = !state.busy || state.runtimeState == RuntimeState.PREPARING,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.runtime_button_stop))
                        }
                    }
                    NormalProjectPrimaryActionPolicy.Action.NONE -> {
                        Text(
                            text = stringResource(R.string.normal_project_waiting_action),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
