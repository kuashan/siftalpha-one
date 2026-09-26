package com.siftalpha.studio

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import com.siftalpha.studio.project.WebProjectInspector
import com.siftalpha.studio.presentation.NormalProjectPrimaryActionPolicy
import com.siftalpha.studio.presentation.ProjectActionPolicy
import com.siftalpha.studio.presentation.ProjectUiSnapshot
import com.siftalpha.studio.presentation.PrepareWorkflowFacts
import com.siftalpha.studio.presentation.PrepareWorkflowPhase
import com.siftalpha.studio.presentation.RuntimeActivityIndicatorPolicy
import com.siftalpha.studio.presentation.PrepareWorkflowPresentationPolicy
import com.siftalpha.studio.runtime.RuntimeLifecycleOperation
import com.siftalpha.studio.runtime.RuntimeLifecycleResolver
import com.siftalpha.studio.runtime.RuntimeLifecycleState
import com.siftalpha.studio.runtime.RuntimeOperationAction
import com.siftalpha.studio.runtime.RuntimeOperationProvider
import com.siftalpha.studio.runtime.RuntimeResult
import com.siftalpha.studio.runtime.RuntimeAutoObservationPolicy
import com.siftalpha.studio.runtime.RuntimeObservationStep
import com.siftalpha.studio.runtime.PresentationTarget
import com.siftalpha.studio.runtime.PresentationTargetResolver
import com.siftalpha.studio.runtime.ResultWebStore
import com.siftalpha.studio.runtime.RichResultDocument
import com.siftalpha.studio.runtime.RichResultLifecyclePolicy
import com.siftalpha.studio.runtime.RichResultParser
import com.siftalpha.studio.runtime.AdaptiveResultAnalyzer
import com.siftalpha.studio.runtime.AdaptiveResultHtmlRenderer
import com.siftalpha.studio.runtime.RuntimeProgramOutputExtractor
import com.siftalpha.studio.runtime.RuntimeWebAvailabilityTracker
import com.siftalpha.studio.runtime.RuntimeWebDiscoveryScopePolicy
import com.siftalpha.studio.runtime.RuntimeWebHintPolicy
import com.siftalpha.studio.runtime.RuntimeWebLearnedEndpointStore
import com.siftalpha.studio.runtime.RuntimeWebLearnedLaunchStore
import com.siftalpha.studio.runtime.RuntimeWebStateStore
import com.siftalpha.studio.runtime.RuntimeConfigurationDiscoveryStore
import com.siftalpha.studio.runtime.RuntimeWebUiStatus
import com.siftalpha.studio.runtime.EmbeddedPythonRuntimeStateMapping
import com.siftalpha.studio.runtime.ProjectControlHub
import com.siftalpha.studio.runtime.ProjectOperationCoordinator
import com.siftalpha.studio.runtime.ProjectRuntimeControlExecutor
import com.siftalpha.studio.runtime.ProjectRuntimeController
import com.siftalpha.studio.runtime.ProjectRunRecoveryStore
import com.siftalpha.studio.runtime.ProjectRunWorkflowCoordinator
import com.siftalpha.studio.runtime.ProjectRuntimeSelection
import com.siftalpha.studio.runtime.ProjectRuntimeSelectionStore
import com.siftalpha.studio.runtime.ProjectRuntimeSelectionChangePolicy
import com.siftalpha.studio.runtime.ProjectEnvironmentResolution
import com.siftalpha.studio.runtime.ProjectSecretStore
import com.siftalpha.studio.runtime.RuntimeLifecycleStore
import com.siftalpha.studio.runtime.RuntimeState
import com.siftalpha.studio.runtime.SharedRuntimeLifecycleBridge
import com.siftalpha.studio.runtime.ExternalActionGate
import com.siftalpha.studio.runtime.ExternalProviderPreflightResult
import com.siftalpha.studio.runtime.ExternalProviderProbeCoordinator
import com.siftalpha.studio.runtime.ExternalProviderReadiness
import com.siftalpha.studio.runtime.TermuxContract
import com.siftalpha.studio.runtime.TermuxBackend
import com.siftalpha.studio.runtime.TermuxResultBus
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

    data class ConfigurationFieldState(
        val key: String,
        val description: String,
        val secret: Boolean,
        val required: Boolean,
        val configured: Boolean,
    )

    data class ScreenState(
        val projectName: String = "",
        val statusLabel: String = "",
        val busy: Boolean = false,
        val message: String? = null,
        val developerModeEnabled: Boolean = false,
        val runtimeState: RuntimeState = RuntimeState.UNKNOWN,
        val failureReason: String? = null,
        val runtimeSelection: ProjectRuntimeSelection = ProjectRuntimeSelection.TERMUX,
        val runtimeSelectionCanChange: Boolean = true,
        val externalReadiness: ExternalProviderReadiness? = null,
        val presentationTarget: PresentationTarget = PresentationTarget.NONE,
        val openEnabled: Boolean = false,
        val preparePhaseTitle: String? = null,
        val preparePhaseDetail: String? = null,
        val activityIndicatorVisible: Boolean = false,
        val primaryAction: NormalProjectPrimaryActionPolicy.Action =
            NormalProjectPrimaryActionPolicy.Action.PREPARE_PROJECT,
        val configurationFields: List<ConfigurationFieldState> = emptyList(),
    )

    private lateinit var gateway: V04ProjectGateway
    private lateinit var project: V04ProjectGateway.RuntimeProject
    private lateinit var runtime: ProjectRuntimeController
    private lateinit var selectionStore: ProjectRuntimeSelectionStore
    private lateinit var lifecycleStore: RuntimeLifecycleStore
    private lateinit var operationCoordinator: ProjectOperationCoordinator
    private lateinit var controlHub: ProjectControlHub
    private lateinit var sharedLifecycleBridge: SharedRuntimeLifecycleBridge
    private lateinit var externalBackend: TermuxBackend
    private lateinit var externalPreflight: ExternalProviderProbeCoordinator
    private lateinit var externalActionGate: ExternalActionGate
    private lateinit var backgroundReliabilityGuidance: BackgroundReliabilityGuidanceController
    private lateinit var prepareLiveProgress: PrepareLiveProgressController
    private lateinit var configurationUi: ProjectConfigurationUiController
    private lateinit var secretStore: ProjectSecretStore
    private lateinit var runRecoveryStore: ProjectRunRecoveryStore
    private lateinit var runWorkflow: ProjectRunWorkflowCoordinator
    private lateinit var webInspector: WebProjectInspector
    private lateinit var webStateStore: RuntimeWebStateStore
    private lateinit var webAvailability: RuntimeWebAvailabilityTracker
    private lateinit var webLearnedEndpointStore: RuntimeWebLearnedEndpointStore
    private lateinit var resultWebStore: ResultWebStore
    private var richResult: RichResultDocument? = null
    private var resultWebRef: ResultWebStore.ResultRef? = null
    private val manualRefreshExecutions = mutableSetOf<Int>()
    private val actionExecutor = Executors.newSingleThreadExecutor()
    private val prepareExecutor = Executors.newSingleThreadExecutor()
    private var internalPrepareFuture: Future<*>? = null
    private val screenState = mutableStateOf(ScreenState())

    private val completionListener: (ProjectOperationCoordinator.ExternalCompletion) -> Unit = { completion ->
        runOnUiThread {
            if (!::project.isInitialized || completion.projectId != project.summary.documentId) {
                return@runOnUiThread
            }
            if (
                completion.action == RuntimeOperationAction.PREPARE &&
                ::prepareLiveProgress.isInitialized
            ) {
                prepareLiveProgress.finish(project.folderName)
                updatePrepareCompletion()
            }

            captureExternalEvidence(completion.action, completion.result)
            val runtimeFinding = if (
                completion.action == RuntimeOperationAction.START &&
                ::runWorkflow.isInitialized
            ) {
                runWorkflow.observeRuntimeOutput(
                    project = project,
                    output = combinedRuntimeOutput(completion.result.stdout, completion.result.stderr),
                )
            } else {
                null
            }

            val wasManualRefresh = manualRefreshExecutions.remove(completion.result.executionId)
            if (completion.action == RuntimeOperationAction.STATUS && wasManualRefresh) {
                val state = lifecycleStore.read(project.summary.documentId).runtimeState
                val next = RuntimeAutoObservationPolicy.decide(
                    RuntimeAutoObservationPolicy.Input(
                        state = state,
                        hasPendingOperation = false,
                        finalLogsCompleted = false,
                    ),
                )
                if (
                    next == RuntimeObservationStep.REQUEST_FINAL_LOGS &&
                    dispatchExternalObservation(RuntimeOperationAction.LOGS, manual = true)
                ) {
                    return@runOnUiThread
                }
            }

            screenState.value = screenState.value.copy(busy = false)
            refreshSharedState(
                if (wasManualRefresh || completion.action == RuntimeOperationAction.LOGS) {
                    getString(R.string.normal_project_refresh_complete)
                } else {
                    screenState.value.message
                },
            )
            if (
                runtimeFinding?.recoveryReason == ProjectRunRecoveryStore.Reason.CLI_ARGUMENTS &&
                runtimeFinding.finding.missingEnvironmentNames.isEmpty() &&
                !runtimeFinding.finding.unnamedCredentialRequired
            ) {
                runProject()
            }
        }
    }

    private val timeoutListener: (ProjectOperationCoordinator.ExternalTimeout) -> Unit = { timeout ->
        runOnUiThread {
            if (::project.isInitialized && timeout.projectId == project.summary.documentId) {
                if (
                    timeout.action == RuntimeOperationAction.PREPARE &&
                    ::prepareLiveProgress.isInitialized
                ) {
                    prepareLiveProgress.finish(project.folderName)
                    screenState.value = screenState.value.copy(
                        preparePhaseTitle = getString(R.string.normal_prepare_phase_failed),
                        preparePhaseDetail = getString(R.string.normal_prepare_timeout_detail),
                    )
                }
                timeout.executionId?.let(manualRefreshExecutions::remove)
                screenState.value = screenState.value.copy(busy = false)
                refreshSharedState(getString(R.string.runtime_operation_timed_out))
            }
        }
    }

    private val progressResultListener: (RuntimeResult) -> Unit = { result ->
        runOnUiThread {
            if (::prepareLiveProgress.isInitialized) {
                prepareLiveProgress.consumeIfProbe(result)
            }
        }
    }

    private val preflightListener: (ExternalProviderPreflightResult) -> Unit = { result ->
        runOnUiThread {
            if (!::project.isInitialized) return@runOnUiThread
            screenState.value = screenState.value.copy(externalReadiness = result.readiness)
            if (result.ready) {
                resumeExternalActionGate()
            } else {
                refreshSharedState()
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
        operationCoordinator = ProjectOperationCoordinator.shared(this)
        runtime = ProjectRuntimeController(
            gateway = gateway,
            embeddedPythonSession = EmbeddedPythonSession.shared(this),
            embeddedPythonProjectStager = EmbeddedPythonProjectStager(this),
            embeddedPythonEnvironmentManager = EmbeddedPythonEnvironmentManager(this),
            internalAlpineEnvironmentManager = InternalAlpineEnvironmentManager(this),
            internalAlpineSession = InternalAlpineSession.shared(this),
        )
        secretStore = ProjectSecretStore(this)
        runRecoveryStore = ProjectRunRecoveryStore(this)
        configurationUi = ProjectConfigurationUiController(
            activity = this,
            inspector = ProjectConfigurationInspector(this),
            store = secretStore,
        ) {
            handleConfigurationControllerChanged()
        }
        externalBackend = TermuxBackend(this)
        externalPreflight = ExternalProviderProbeCoordinator.shared(this)
        externalActionGate = ExternalActionGate.shared(this)
        backgroundReliabilityGuidance = BackgroundReliabilityGuidanceController(this)
        webInspector = WebProjectInspector(this)
        webStateStore = RuntimeWebStateStore(this)
        webLearnedEndpointStore = RuntimeWebLearnedEndpointStore(this)
        resultWebStore = ResultWebStore(this)
        resultWebRef = resultWebStore.latest(project.summary.documentId)
        runWorkflow = ProjectRunWorkflowCoordinator(
            runtime = runtime,
            webInspector = webInspector,
            learnedWebLaunchStore = RuntimeWebLearnedLaunchStore(this),
            configurationDiscoveryStore = RuntimeConfigurationDiscoveryStore(this),
            recoveryStore = runRecoveryStore,
        )
        webAvailability = RuntimeWebAvailabilityTracker {
            if (::project.isInitialized) runOnUiThread { refreshSharedState() }
        }
        sharedLifecycleBridge = SharedRuntimeLifecycleBridge(lifecycleStore)
        controlHub = ProjectControlHub(
            selectionStore = selectionStore,
            executor = ProjectRuntimeControlExecutor(
                runtime = runtime,
                externalBackend = externalBackend,
                operationCoordinator = operationCoordinator,
                environmentReadyReader = { id, selected ->
                    lifecycleStore.read(id).environmentReadyFor(selected)
                },
                internalPrepareProgress = { progressText ->
                    runOnUiThread {
                        updateInternalPrepareProgress(progressText)
                    }
                },
            ),
            stateBridge = sharedLifecycleBridge,
            externalPreflight = externalPreflight,
        )
        prepareLiveProgress = PrepareLiveProgressController(
            context = this,
            backend = externalBackend,
            gateway = gateway,
            runtime = runtime,
            renderSnapshot = { folderName, snapshot ->
                if (::project.isInitialized && folderName == project.folderName) {
                    runOnUiThread {
                        updatePrepareProgress(snapshot.stage)
                    }
                }
            },
            render = { _, _ ->
                // Normal Mode presents product-facing phases. Raw prepare diagnostics remain in
                // Developer Workspace and the shared Runtime logs.
            },
        )

        enableEdgeToEdge()
        refreshSharedState()
        setContent {
            SiftAlphaNormalTheme {
                NormalProjectWorkspaceScreen(
                    state = screenState.value,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onPrepare = { prepareProject() },
                    onConfigure = { configureProject() },
                    onRun = { runProject() },
                    onStop = { stopProject() },
                    onRefresh = { refreshProject() },
                    onOpen = { openProjectPresentation() },
                    onSaveConfiguration = { values, runAfterSave ->
                        saveNormalConfiguration(values, runAfterSave)
                    },
                    onSelectRuntime = { selectRuntime(it) },
                    onOpenDeveloper = { openDeveloperWorkspace() },
                    onRequestPermission = { requestRunCommandPermission() },
                    onOpenTermux = { openTermux() },
                    onRecheckExternal = { recheckExternalProvider() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        operationCoordinator.addCompletionListener(completionListener)
        operationCoordinator.addTimeoutListener(timeoutListener)
        externalPreflight.addListener(preflightListener)
        TermuxResultBus.addListener(progressResultListener)
        if (::prepareLiveProgress.isInitialized) prepareLiveProgress.resume()
        if (::webAvailability.isInitialized) webAvailability.resume()
        if (::project.isInitialized) {
            if (shouldProbeExternalProvider()) {
                refreshExternalPreflight(retryIfNeeded = false)
            } else {
                screenState.value = screenState.value.copy(externalReadiness = null)
            }
            reconcileExternalPrepareProgress()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::project.isInitialized) {
            if (shouldProbeExternalProvider()) {
                refreshExternalPreflight(retryIfNeeded = true)
            } else {
                screenState.value = screenState.value.copy(externalReadiness = null)
            }
            refreshSharedState()
        }
    }

    override fun onStop() {
        if (::webAvailability.isInitialized) webAvailability.pause()
        if (::prepareLiveProgress.isInitialized) prepareLiveProgress.pause()
        TermuxResultBus.removeListener(progressResultListener)
        if (::operationCoordinator.isInitialized) {
            operationCoordinator.removeCompletionListener(completionListener)
            operationCoordinator.removeTimeoutListener(timeoutListener)
        }
        if (::externalPreflight.isInitialized) externalPreflight.removeListener(preflightListener)
        super.onStop()
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
                    val mappedState = EmbeddedPythonRuntimeStateMapping.toRuntimeState(snapshot)
                    if (
                        mappedState == RuntimeState.EXITED_ERROR &&
                        ::runWorkflow.isInitialized
                    ) {
                        runWorkflow.observeRuntimeOutput(
                            project = project,
                            output = combinedRuntimeOutput(snapshot.stdout, snapshot.stderr),
                        )
                    }
                    lifecycleStore.write(
                        projectKey = projectId,
                        environmentReady = current.environmentReadyFor(selection),
                        runtimeState = mappedState,
                        failureReason = if (mappedState == RuntimeState.EXITED_ERROR) {
                            snapshot.stderr.ifBlank { snapshot.stdout }.trim().take(240)
                        } else {
                            null
                        },
                        runtimeSelection = selection,
                    )
                }
        }

        val lifecycle = lifecycleStore.read(projectId)
        val operation = operationCoordinator.current(projectId)
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
            web = projectWebSnapshot(projectId, lifecycle.runtimeState),
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
        val pendingRunRecovery = if (::runWorkflow.isInitialized) {
            runWorkflow.pendingRecovery(projectId)
        } else {
            null
        }
        val primaryAction = if (
            pendingRunRecovery?.reason == ProjectRunRecoveryStore.Reason.CONFIGURATION &&
            lifecycle.runtimeState !in setOf(
                RuntimeState.PREPARING,
                RuntimeState.STARTING,
                RuntimeState.RUNNING,
            )
        ) {
            NormalProjectPrimaryActionPolicy.Action.CONFIGURE
        } else {
            NormalProjectPrimaryActionPolicy.resolve(policy)
        }
        val activityIndicatorVisible = RuntimeActivityIndicatorPolicy.shouldAnimate(
            RuntimeActivityIndicatorPolicy.Input(
                lifecycleState = lifecycleState,
                runtimeState = lifecycle.runtimeState,
                operationActive = operation != null,
            ),
        )
        val selectionCanChange = ProjectRuntimeSelectionChangePolicy.canChange(
            ProjectRuntimeSelectionChangePolicy.Input(
                runtimeState = lifecycle.runtimeState,
                operation = operation,
            ),
        )
        resultWebRef = resultWebStore.latest(projectId)
        val web = projectWebSnapshot(projectId, lifecycle.runtimeState)
        val presentationTarget = PresentationTargetResolver.resolve(
            webPresentationKnown = web.reachableUrl != null,
            richResultAvailable = richResult != null,
            resultWebAvailable = resultWebRef != null,
        )
        val configuredConfigurationNames =
            configuration.profile.configuredProjectEnvKeys + configuration.protectedKeys
        val requiredConfigurationByName =
            linkedMapOf<String, com.siftalpha.studio.project.ProjectConfigurationInspector.Requirement>()
        configuration.profile.required.forEach { requirement ->
            requiredConfigurationByName.putIfAbsent(requirement.name, requirement)
        }
        configuration.preflight.missingRequired.forEach { requirement ->
            requiredConfigurationByName.putIfAbsent(requirement.name, requirement)
        }
        val optionalConfigurationByName =
            linkedMapOf<String, com.siftalpha.studio.project.ProjectConfigurationInspector.Requirement>()
        configuration.profile.optional.forEach { requirement ->
            if (requirement.name !in requiredConfigurationByName) {
                optionalConfigurationByName.putIfAbsent(requirement.name, requirement)
            }
        }
        val configurationFields = buildList {
            requiredConfigurationByName.values.forEach { requirement ->
                add(
                    ConfigurationFieldState(
                        key = requirement.name,
                        description = requirement.description,
                        secret = requirement.secret,
                        required = true,
                        configured = requirement.name in configuredConfigurationNames,
                    ),
                )
            }
            optionalConfigurationByName.values.forEach { requirement ->
                add(
                    ConfigurationFieldState(
                        key = requirement.name,
                        description = requirement.description,
                        secret = requirement.secret,
                        required = false,
                        configured = requirement.name in configuredConfigurationNames,
                    ),
                )
            }
        }
        val missingRequiredCount = configuration.preflight.missingRequired.size
        val prepareTitle = if (environmentReady == true) {
            getString(R.string.normal_prepare_phase_ready)
        } else {
            screenState.value.preparePhaseTitle
        }
        val prepareDetail = if (environmentReady == true) {
            if (missingRequiredCount > 0) {
                getString(R.string.normal_prepare_config_required_detail, missingRequiredCount)
            } else {
                getString(R.string.normal_prepare_ready_detail)
            }
        } else {
            screenState.value.preparePhaseDetail
        }
        screenState.value = screenState.value.copy(
            projectName = project.summary.name,
            statusLabel = lifecycleState.uiLabel(this),
            message = message,
            developerModeEnabled = DeveloperModeStore(this).isEnabled(),
            runtimeState = lifecycle.runtimeState,
            failureReason = lifecycle.failureReason,
            runtimeSelection = selection,
            externalReadiness = if (selection == ProjectRuntimeSelection.TERMUX) {
                externalPreflight.current().readiness
            } else {
                null
            },
            runtimeSelectionCanChange = selectionCanChange,
            presentationTarget = presentationTarget,
            openEnabled = presentationTarget != PresentationTarget.NONE,
            preparePhaseTitle = prepareTitle,
            preparePhaseDetail = prepareDetail,
            activityIndicatorVisible = activityIndicatorVisible,
            primaryAction = primaryAction,
            configurationFields = configurationFields,
        )
    }

    private fun refreshExternalPreflight(retryIfNeeded: Boolean) {
        if (!::externalPreflight.isInitialized) return
        val result = if (retryIfNeeded) {
            val current = externalPreflight.current()
            if (
                current.readiness == ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED ||
                current.readiness == ExternalProviderReadiness.BRIDGE_UNRESPONSIVE ||
                current.readiness == ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED
            ) {
                externalPreflight.probe()
            } else {
                current
            }
        } else {
            externalPreflight.current()
        }
        screenState.value = screenState.value.copy(externalReadiness = result.readiness)
    }

    private fun shouldProbeExternalProvider(): Boolean =
        ::project.isInitialized &&
            (
                selectionStore.read(project.summary.documentId) == ProjectRuntimeSelection.TERMUX ||
                    externalActionGate.pending(project.summary.documentId) != null
                )

    private fun ensureExternalProviderFor(action: ExternalActionGate.Action): Boolean {
        val selection = selectionStore.read(project.summary.documentId)
        if (selection != ProjectRuntimeSelection.TERMUX) return true

        val decision = externalActionGate.request(
            projectId = project.summary.documentId,
            action = action,
            origin = ExternalActionGate.Origin.NORMAL_MODE,
        )
        if (decision is ExternalActionGate.Decision.Proceed) return true

        val readiness = when (decision) {
            is ExternalActionGate.Decision.Awaiting -> decision.readiness
            is ExternalActionGate.Decision.Rejected -> decision.readiness
            ExternalActionGate.Decision.Proceed -> ExternalProviderReadiness.READY
        }
        screenState.value = screenState.value.copy(externalReadiness = readiness)
        when (readiness) {
            ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED ->
                requestRunCommandPermission()

            ExternalProviderReadiness.TERMUX_NOT_INSTALLED,
            ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED,
            ExternalProviderReadiness.BRIDGE_UNRESPONSIVE,
            -> screenState.value = screenState.value.copy(
                externalReadiness = readiness,
                message = getString(R.string.normal_external_provider_open_termux),
            )

            ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED,
            ExternalProviderReadiness.BRIDGE_CHECKING,
            -> screenState.value = screenState.value.copy(
                externalReadiness = readiness,
                message = getString(R.string.normal_external_provider_checking),
            )

            ExternalProviderReadiness.UNAVAILABLE ->
                screenState.value = screenState.value.copy(
                    externalReadiness = readiness,
                    message = getString(R.string.normal_external_provider_unavailable),
                )

            ExternalProviderReadiness.READY -> Unit
        }
        return false
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
                operation = operationCoordinator.current(projectId),
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

    private fun prepareProject() {
        if (screenState.value.busy) return
        backgroundReliabilityGuidance.maybeProceed {
            prepareProjectAfterGuidance()
        }
    }

    private fun prepareProjectAfterGuidance() {
        if (screenState.value.busy) return
        if (!ensureExternalProviderFor(ExternalActionGate.Action.PREPARE)) return
        val selection = selectionStore.read(project.summary.documentId)
        screenState.value = screenState.value.copy(
            busy = true,
            message = getString(R.string.normal_project_preparing),
            runtimeState = RuntimeState.PREPARING,
            runtimeSelectionCanChange = false,
            activityIndicatorVisible = true,
            preparePhaseTitle = getString(R.string.normal_prepare_phase_detecting),
            preparePhaseDetail = getString(R.string.normal_prepare_detecting_detail),
            primaryAction = NormalProjectPrimaryActionPolicy.Action.STOP,
        )
        internalPrepareFuture = prepareExecutor.submit {
            val resolution = runCatching {
                runtime.detectEnvironment(
                    project = project,
                    resolveCompatibility = selection == ProjectRuntimeSelection.EMBEDDED_R,
                )
            }.getOrNull()
            if (resolution != null) {
                runOnUiThread {
                    updatePrepareResolution(resolution)
                }
            }

            val result = controlHub.prepare(project)
            runOnUiThread {
                internalPrepareFuture = null
                screenState.value = screenState.value.copy(busy = false)
                attachExternalPrepareProgress(result)
                updatePrepareResult(result)
                handleActionRequirement(result, ExternalActionGate.Action.PREPARE)
                refreshSharedState(resultMessage(result))
            }
        }
        if (selection == ProjectRuntimeSelection.EMBEDDED_R) refreshSharedState()
    }

    private fun updatePrepareResolution(resolution: ProjectEnvironmentResolution) {
        val facts = PrepareWorkflowPresentationPolicy.fromResolution(resolution)
        val runtimeLabel = when (facts.primaryRuntimeId?.lowercase()) {
            "python" -> getString(R.string.normal_prepare_runtime_python)
            "nodejs", "node.js", "node" -> getString(R.string.normal_prepare_runtime_node)
            else -> getString(R.string.normal_prepare_runtime_unknown)
        }
        val detail = when (facts.phase) {
            PrepareWorkflowPhase.BLOCKED -> getString(
                R.string.normal_prepare_blocked_detail,
                facts.blockingIssueCount,
            )
            PrepareWorkflowPhase.COMPATIBILITY_CONFIRMED -> getString(
                R.string.normal_prepare_compatibility_confirmed_detail,
                runtimeLabel,
                facts.directDependencyCount,
                facts.resolvedPackageCount ?: facts.directDependencyCount,
            )
            PrepareWorkflowPhase.COMPATIBILITY_FALLBACK -> getString(
                R.string.normal_prepare_compatibility_fallback_detail,
                runtimeLabel,
                facts.directDependencyCount,
            )
            else -> getString(
                R.string.normal_prepare_plan_ready_detail,
                runtimeLabel,
                facts.directDependencyCount,
            )
        }
        val title = when (facts.phase) {
            PrepareWorkflowPhase.BLOCKED -> getString(R.string.normal_prepare_phase_blocked)
            PrepareWorkflowPhase.COMPATIBILITY_CONFIRMED,
            PrepareWorkflowPhase.COMPATIBILITY_FALLBACK,
            -> getString(R.string.normal_prepare_phase_compatibility)
            else -> getString(R.string.normal_prepare_phase_compatibility)
        }
        screenState.value = screenState.value.copy(
            preparePhaseTitle = title,
            preparePhaseDetail = detail,
        )
    }

    private fun updatePrepareProgress(stage: String) {
        val phase = PrepareWorkflowPresentationPolicy.fromProgressStage(stage)
        val title = when (phase) {
            PrepareWorkflowPhase.INSTALLING_DEPENDENCIES ->
                getString(R.string.normal_prepare_phase_installing)
            PrepareWorkflowPhase.VERIFYING ->
                getString(R.string.normal_prepare_phase_verifying)
            else -> getString(R.string.normal_prepare_phase_environment)
        }
        val detail = when (phase) {
            PrepareWorkflowPhase.INSTALLING_DEPENDENCIES ->
                getString(R.string.normal_prepare_installing_detail)
            PrepareWorkflowPhase.VERIFYING ->
                getString(R.string.normal_prepare_verifying_detail)
            else -> getString(R.string.normal_prepare_environment_detail)
        }
        screenState.value = screenState.value.copy(
            preparePhaseTitle = title,
            preparePhaseDetail = detail,
        )
    }

    private fun updateInternalPrepareProgress(text: String) {
        val phase = PrepareWorkflowPresentationPolicy.fromInternalProgress(text) ?: return
        val title = when (phase) {
            PrepareWorkflowPhase.PREPARING_RUNTIME ->
                getString(R.string.normal_prepare_phase_environment)
            PrepareWorkflowPhase.INSTALLING_DEPENDENCIES ->
                getString(R.string.normal_prepare_phase_installing)
            PrepareWorkflowPhase.VERIFYING ->
                getString(R.string.normal_prepare_phase_verifying)
            else -> return
        }
        val detail = when (phase) {
            PrepareWorkflowPhase.PREPARING_RUNTIME ->
                getString(R.string.normal_prepare_environment_detail)
            PrepareWorkflowPhase.INSTALLING_DEPENDENCIES ->
                getString(R.string.normal_prepare_installing_detail)
            PrepareWorkflowPhase.VERIFYING ->
                getString(R.string.normal_prepare_verifying_detail)
            else -> return
        }
        screenState.value = screenState.value.copy(
            preparePhaseTitle = title,
            preparePhaseDetail = detail,
        )
    }

    private fun updatePrepareCompletion() {
        val selection = selectionStore.read(project.summary.documentId)
        val ready = lifecycleStore.read(project.summary.documentId).environmentReadyFor(selection) == true
        screenState.value = screenState.value.copy(
            preparePhaseTitle = getString(
                if (ready) R.string.normal_prepare_phase_ready else R.string.normal_prepare_phase_failed,
            ),
            preparePhaseDetail = getString(
                if (ready) R.string.normal_prepare_ready_detail else R.string.normal_prepare_failed_detail,
            ),
        )
    }

    private fun updatePrepareResult(result: ProjectControlHub.Result) {
        when (result) {
            is ProjectControlHub.Result.Completed -> if (result.action == ProjectControlHub.Action.PREPARE) {
                screenState.value = screenState.value.copy(
                    preparePhaseTitle = getString(
                        if (result.environmentReady) {
                            R.string.normal_prepare_phase_ready
                        } else {
                            R.string.normal_prepare_phase_failed
                        },
                    ),
                    preparePhaseDetail = getString(
                        if (result.environmentReady) {
                            R.string.normal_prepare_ready_detail
                        } else {
                            R.string.normal_prepare_failed_detail
                        },
                    ),
                )
            }
            is ProjectControlHub.Result.Rejected -> if (result.action == ProjectControlHub.Action.PREPARE) {
                screenState.value = screenState.value.copy(
                    preparePhaseTitle = getString(R.string.normal_prepare_phase_blocked),
                    preparePhaseDetail = getString(R.string.normal_prepare_blocked_generic),
                )
            }
            else -> Unit
        }
    }

    private fun handleConfigurationControllerChanged() {
        refreshSharedState(getString(R.string.normal_project_configuration_saved))
        if (!::runWorkflow.isInitialized || !::project.isInitialized) return
        val pending = runWorkflow.pendingRecovery(project.summary.documentId) ?: return
        if (pending.reason != ProjectRunRecoveryStore.Reason.CONFIGURATION) return
        val refreshed = configurationUi.snapshot(
            project.summary.documentId,
            project.folderName,
        )
        if (refreshed.preflight.missingRequired.isEmpty() && !screenState.value.busy) {
            runProject()
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

    private fun saveNormalConfiguration(
        values: Map<String, String>,
        runAfterSave: Boolean,
    ) {
        if (screenState.value.busy) return
        val fields = screenState.value.configurationFields
        val missingRequired = fields.firstOrNull { field ->
            field.required &&
                !field.configured &&
                values[field.key].orEmpty().isBlank()
        }
        if (missingRequired != null) {
            Toast.makeText(
                this,
                getString(R.string.runtime_configuration_blank_value, missingRequired.key),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        runCatching {
            values.forEach { (key, value) ->
                val normalized = value.trim()
                if (normalized.isNotBlank()) {
                    secretStore.saveEnvironmentValue(project.folderName, key, normalized)
                }
            }
        }.onFailure { error ->
            Toast.makeText(
                this,
                getString(R.string.runtime_configuration_save_failed) +
                    ": " + (error.message ?: error.javaClass.simpleName),
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        refreshSharedState(getString(R.string.normal_project_configuration_saved))
        val resumePendingRun =
            runAfterSave ||
                (::runWorkflow.isInitialized &&
                    runWorkflow.pendingRecovery(project.summary.documentId) != null)
        if (!resumePendingRun) return

        val refreshed = configurationUi.snapshot(
            project.summary.documentId,
            project.folderName,
        )
        if (refreshed.preflight.missingRequired.isEmpty()) {
            runProject()
        } else {
            refreshSharedState()
        }
    }

    private fun runProject() {
        if (screenState.value.busy) return
        backgroundReliabilityGuidance.maybeProceed {
            runProjectAfterGuidance()
        }
    }

    private fun runProjectAfterGuidance() {
        if (screenState.value.busy) return
        if (!ensureExternalProviderFor(ExternalActionGate.Action.RUN)) return
        val configuration = configurationUi.snapshot(
            project.summary.documentId,
            project.folderName,
        )
        screenState.value = screenState.value.copy(
            busy = true,
            message = getString(R.string.normal_project_starting),
            activityIndicatorVisible = true,
        )
        actionExecutor.execute {
            val preparation = runWorkflow.prepare(
                project = project,
                configuration = ProjectRunWorkflowCoordinator.ConfigurationFacts(
                    missingRequiredNames = configuration.preflight.missingRequired.map { it.name },
                    cliRequirements = configuration.cliRequirements,
                ),
                webHintPorts = webHintPorts(),
            )
            when (preparation) {
                is ProjectRunWorkflowCoordinator.Preparation.Ready ->
                    dispatchPreparedRun(preparation.request)

                is ProjectRunWorkflowCoordinator.Preparation.NeedsConfiguration ->
                    runOnUiThread {
                        screenState.value = screenState.value.copy(busy = false)
                        refreshSharedState(getString(R.string.runtime_policy_configuration_required))
                    }

                is ProjectRunWorkflowCoordinator.Preparation.NeedsLaunchInput ->
                    runOnUiThread {
                        screenState.value = screenState.value.copy(busy = false)
                        showRunInputDialog(preparation.plan)
                    }

                is ProjectRunWorkflowCoordinator.Preparation.Rejected ->
                    runOnUiThread {
                        screenState.value = screenState.value.copy(busy = false)
                        val message = when (preparation.reason) {
                            ProjectRunWorkflowCoordinator.RejectReason.PYTHON_LAUNCH_MISSING ->
                                getString(R.string.runtime_cli_missing)
                            ProjectRunWorkflowCoordinator.RejectReason.PYTHON_LAUNCH_INVALID ->
                                getString(R.string.runtime_cli_unsupported)
                        }
                        refreshSharedState(message)
                    }
            }
        }
    }

    private fun dispatchPreparedRun(request: ProjectControlHub.RunRequest) {
        val result = controlHub.run(project = project, request = request)
        val dispatched = result as? ProjectControlHub.Result.Dispatched
        if (dispatched?.action == ProjectControlHub.Action.RUN) {
            runWorkflow.completeRunDispatch(project.summary.documentId)
        }
        val internalFinding = inspectImmediateInternalRunFailure(dispatched)
        runOnUiThread {
            screenState.value = screenState.value.copy(busy = false)
            handleActionRequirement(result, ExternalActionGate.Action.RUN)
            refreshSharedState(resultMessage(result))
            if (
                internalFinding?.recoveryReason == ProjectRunRecoveryStore.Reason.CLI_ARGUMENTS &&
                internalFinding.finding.missingEnvironmentNames.isEmpty() &&
                !internalFinding.finding.unnamedCredentialRequired
            ) {
                runProject()
            }
        }
    }

    private fun inspectImmediateInternalRunFailure(
        dispatched: ProjectControlHub.Result.Dispatched?,
    ): ProjectRunWorkflowCoordinator.RuntimeFinding? {
        if (
            dispatched?.action != ProjectControlHub.Action.RUN ||
            dispatched.provider != RuntimeOperationProvider.INTERNAL
        ) {
            return null
        }
        val snapshot = runCatching {
            runtime.embeddedPythonSnapshotFor(project.summary.documentId)
        }.getOrNull() ?: return null
        if (EmbeddedPythonRuntimeStateMapping.toRuntimeState(snapshot) != RuntimeState.EXITED_ERROR) {
            return null
        }
        return runWorkflow.observeRuntimeOutput(
            project = project,
            output = combinedRuntimeOutput(snapshot.stdout, snapshot.stderr),
        )
    }

    private fun showRunInputDialog(plan: ProjectRunWorkflowCoordinator.LaunchInputPlan) {
        if (isFinishing || isDestroyed) return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dialogDp(20), dialogDp(4), dialogDp(20), dialogDp(4))
        }

        var targetSpinner: Spinner? = null
        if (plan.targets.size > 1) {
            content.addView(TextView(this@NormalProjectWorkspaceActivity).apply {
                text = getString(R.string.runtime_cli_select_entry)
                textSize = 14f
                setPadding(0, dialogDp(6), 0, dialogDp(4))
            })
            targetSpinner = Spinner(this@NormalProjectWorkspaceActivity).apply {
                adapter = ArrayAdapter(
                    this@NormalProjectWorkspaceActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    plan.targets.map { it.label },
                )
            }
            content.addView(targetSpinner)
        }

        val argumentInputs = linkedMapOf<String, EditText>()
        plan.requiredArguments.forEach { requirement ->
            content.addView(TextView(this@NormalProjectWorkspaceActivity).apply {
                text = requirement.token + " *"
                textSize = 14f
                setPadding(0, dialogDp(8), 0, dialogDp(2))
            })
            val input = EditText(this@NormalProjectWorkspaceActivity).apply {
                hint = getString(R.string.runtime_cli_required_value_hint, requirement.token)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                setSingleLine(true)
            }
            argumentInputs[requirement.token] = input
            content.addView(input)
        }

        content.addView(TextView(this@NormalProjectWorkspaceActivity).apply {
            text = getString(R.string.runtime_cli_additional_arguments)
            textSize = 14f
            setPadding(0, dialogDp(8), 0, dialogDp(2))
        })
        val additionalInput = EditText(this@NormalProjectWorkspaceActivity).apply {
            hint = getString(R.string.runtime_cli_arguments_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
        }
        content.addView(additionalInput)

        val scroll = ScrollView(this@NormalProjectWorkspaceActivity).apply { addView(content) }
        lateinit var dialog: AlertDialog
        dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.runtime_cli_arguments))
            .setView(scroll)
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.runtime_button_run), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedTargetId = when {
                    plan.targets.size == 1 -> plan.targets.single().id
                    else -> targetSpinner
                        ?.selectedItemPosition
                        ?.takeIf { it in plan.targets.indices }
                        ?.let { plan.targets[it].id }
                }
                val values = argumentInputs.mapValues { it.value.text?.toString().orEmpty() }
                when (
                    val resolved = runWorkflow.resolveInput(
                        plan = plan,
                        selectedTargetId = selectedTargetId,
                        values = values,
                        additionalArguments = additionalInput.text?.toString().orEmpty(),
                    )
                ) {
                    is ProjectRunWorkflowCoordinator.InputResolution.Invalid -> {
                        val token = resolved.token
                        if (token != null) {
                            Toast.makeText(
                                this,
                                getString(R.string.runtime_cli_required_blank, token),
                                Toast.LENGTH_SHORT,
                            ).show()
                            argumentInputs[token]?.requestFocus()
                        } else {
                            Toast.makeText(
                                this,
                                getString(R.string.runtime_cli_arguments_invalid),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }

                    is ProjectRunWorkflowCoordinator.InputResolution.Ready -> {
                        dialog.dismiss()
                        screenState.value = screenState.value.copy(
                            busy = true,
                            message = getString(R.string.normal_project_starting),
                            activityIndicatorVisible = true,
                        )
                        actionExecutor.execute {
                            dispatchPreparedRun(resolved.request)
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun combinedRuntimeOutput(stdout: String, stderr: String): String = buildString {
        append(stdout)
        if (stderr.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(stderr)
        }
    }

    private fun dialogDp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun stopProject() {
        externalActionGate.cancel(project.summary.documentId)
        val selection = selectionStore.read(project.summary.documentId)
        if (
            selection == ProjectRuntimeSelection.EMBEDDED_R &&
            screenState.value.runtimeState == RuntimeState.PREPARING
        ) {
            internalPrepareFuture?.cancel(true)
            internalPrepareFuture = null
            operationCoordinator.cancel(project.summary.documentId)
            sharedLifecycleBridge.cancelPrepare(project, selection)
            screenState.value = screenState.value.copy(
                busy = false,
                preparePhaseTitle = getString(R.string.normal_prepare_phase_stopped),
                preparePhaseDetail = getString(R.string.normal_prepare_stopped_detail),
            )
            refreshSharedState(getString(R.string.normal_project_prepare_stopped))
            return
        }
        if (screenState.value.busy) return
        if (
            selection == ProjectRuntimeSelection.TERMUX &&
            operationCoordinator.current(project.summary.documentId, includeHidden = true)?.action ==
                RuntimeOperationAction.PREPARE &&
            ::prepareLiveProgress.isInitialized
        ) {
            prepareLiveProgress.finish(project.folderName)
        }
        screenState.value = screenState.value.copy(
            busy = true,
            message = getString(R.string.normal_project_stopping),
        )
        actionExecutor.execute {
            val result = controlHub.stop(project)
            runOnUiThread {
                screenState.value = screenState.value.copy(busy = false)
                handleActionRequirement(result, null)
                refreshSharedState(resultMessage(result))
            }
        }
    }

    private fun attachExternalPrepareProgress(result: ProjectControlHub.Result) {
        val dispatched = result as? ProjectControlHub.Result.Dispatched ?: return
        if (
            dispatched.action == ProjectControlHub.Action.PREPARE &&
            dispatched.provider == RuntimeOperationProvider.EXTERNAL &&
            dispatched.executionId != null &&
            ::prepareLiveProgress.isInitialized
        ) {
            prepareLiveProgress.start(project.folderName, dispatched.executionId)
        }
    }

    private fun reconcileExternalPrepareProgress() {
        if (!::prepareLiveProgress.isInitialized || !::project.isInitialized) return
        val record = operationCoordinator.current(
            project.summary.documentId,
            includeHidden = true,
        ) ?: return
        if (
            record.provider == RuntimeOperationProvider.EXTERNAL &&
            record.action == RuntimeOperationAction.PREPARE &&
            record.executionId != null
        ) {
            prepareLiveProgress.start(project.folderName, record.executionId)
        }
    }

    private fun refreshProject() {
        if (screenState.value.busy) return
        val projectId = project.summary.documentId
        val selection = selectionStore.read(projectId)

        if (selection == ProjectRuntimeSelection.EMBEDDED_R) {
            screenState.value = screenState.value.copy(
                busy = true,
                message = getString(R.string.normal_project_refreshing),
            )
            actionExecutor.execute {
                val snapshot = runCatching { runtime.embeddedPythonSnapshotFor(projectId) }.getOrNull()
                runOnUiThread {
                    snapshot?.let {
                        val current = lifecycleStore.read(projectId)
                        val state = EmbeddedPythonRuntimeStateMapping.toRuntimeState(it)
                        lifecycleStore.write(
                            projectKey = projectId,
                            environmentReady = current.environmentReadyFor(selection),
                            runtimeState = state,
                            failureReason = if (state == RuntimeState.EXITED_ERROR) it.stderr.take(240) else null,
                            runtimeSelection = selection,
                        )
                        captureResultEvidence(
                            stdout = it.stdout,
                            stderr = it.stderr,
                            allowResultWeb = state == RuntimeState.EXITED_SUCCESS,
                        )
                    }
                    screenState.value = screenState.value.copy(busy = false)
                    refreshSharedState(getString(R.string.normal_project_refresh_complete))
                }
            }
            return
        }

        val currentOperation = operationCoordinator.current(projectId, includeHidden = true)
        if (currentOperation != null) {
            refreshSharedState()
            return
        }

        if (!ensureExternalProviderFor(ExternalActionGate.Action.REFRESH)) return

        val state = lifecycleStore.read(projectId).runtimeState
        val step = RuntimeAutoObservationPolicy.decide(
            RuntimeAutoObservationPolicy.Input(
                state = state,
                hasPendingOperation = false,
                finalLogsCompleted = false,
            ),
        )
        val action = when (step) {
            RuntimeObservationStep.REQUEST_FINAL_LOGS -> RuntimeOperationAction.LOGS
            RuntimeObservationStep.REQUEST_STATUS -> RuntimeOperationAction.STATUS
            RuntimeObservationStep.WAIT_FOR_PENDING,
            RuntimeObservationStep.STOP,
            -> null
        }
        if (action == null) {
            refreshSharedState(getString(R.string.normal_project_refresh_complete))
            return
        }
        dispatchExternalObservation(action, manual = true)
    }

    private fun dispatchExternalObservation(
        action: RuntimeOperationAction,
        manual: Boolean,
    ): Boolean {
        if (action != RuntimeOperationAction.STATUS && action != RuntimeOperationAction.LOGS) return false
        if (!externalPreflight.current().ready) {
            if (manual && !ensureExternalProviderFor(ExternalActionGate.Action.REFRESH)) {
                refreshSharedState()
                return false
            }
            if (!manual) {
                refreshExternalPreflight(retryIfNeeded = true)
                return false
            }
        }

        val operation = operationCoordinator.begin(
            projectId = project.summary.documentId,
            provider = RuntimeOperationProvider.EXTERNAL,
            action = action,
            userVisible = manual,
            runtimeSelection = ProjectRuntimeSelection.TERMUX,
        ) ?: return false

        return runCatching {
            val profile = webInspector.inspect(project.summary.documentId)
            val command = when (action) {
                RuntimeOperationAction.STATUS -> runtime.status(
                    project = project,
                    webLogDiscoveryAllowed = profile.enabled,
                    webHintPorts = webHintPorts(profile),
                )
                RuntimeOperationAction.LOGS -> runtime.logs(
                    project = project,
                    webLogDiscoveryAllowed = profile.enabled,
                    webHintPorts = webHintPorts(profile),
                )
                else -> error("Unsupported observation action")
            }
            val managed = runtime.wrapCancelableExternalActivity(
                project = project,
                action = if (action == RuntimeOperationAction.STATUS) {
                    ProjectRuntimeController.Action.STATUS
                } else {
                    ProjectRuntimeController.Action.LOGS
                },
                command = command,
            )
            val executionId = externalBackend.execute(managed)
            operationCoordinator.bindExternalExecution(
                projectId = project.summary.documentId,
                generation = operation.generation,
                executionId = executionId,
            )
            if (manual) {
                manualRefreshExecutions += executionId
                screenState.value = screenState.value.copy(
                    busy = true,
                    message = getString(R.string.normal_project_refreshing),
                )
            }
            true
        }.getOrElse {
            operationCoordinator.finish(
                projectId = project.summary.documentId,
                generation = operation.generation,
                phase = com.siftalpha.studio.runtime.RuntimeOperationPhase.FAILED,
            )
            if (manual) {
                screenState.value = screenState.value.copy(busy = false)
                refreshSharedState(it.message)
            }
            false
        }
    }

    private fun captureExternalEvidence(
        action: RuntimeOperationAction,
        result: RuntimeResult,
    ) {
        val profile = webInspector.inspect(project.summary.documentId)
        RuntimeWebDiscoveryScopePolicy.candidateFromOutput(
            output = result.stdout,
            webCapabilityEnabled = profile.enabled,
        )?.let { candidate ->
            webStateStore.rememberCandidateUrl(
                projectKey = project.summary.documentId,
                url = candidate.url,
                framework = profile.framework,
                source = candidate.source,
            )
        }

        val state = lifecycleStore.read(project.summary.documentId).runtimeState
        captureResultEvidence(
            stdout = result.stdout,
            stderr = result.stderr,
            allowResultWeb = action == RuntimeOperationAction.LOGS &&
                state == RuntimeState.EXITED_SUCCESS,
        )
    }

    private fun captureResultEvidence(
        stdout: String,
        stderr: String,
        allowResultWeb: Boolean,
    ) {
        val safeStdout = secretStore.redactRuntimeText(project.folderName, stdout)
        val safeStderr = secretStore.redactRuntimeText(project.folderName, stderr)
        val detected = RichResultParser.parse(safeStdout)
        richResult = RichResultLifecyclePolicy.merge(richResult, detected)

        if (!allowResultWeb) return
        val extracted = RuntimeProgramOutputExtractor.extract(
            stdout = safeStdout,
            stderr = safeStderr,
        )
        if (extracted.text.isBlank()) return

        val sourcePath = extracted.sourcePath
            ?: project.summary.entry.takeIf { it.endsWith(".py", ignoreCase = true) }
            ?: runCatching {
                gateway.resolveEmbeddedPythonEntrypoint(project.summary.documentId)
            }.getOrNull()
        val normalized = if (sourcePath != extracted.sourcePath) {
            extracted.copy(sourcePath = sourcePath)
        } else {
            extracted
        }
        val sourceText = sourcePath?.let { path ->
            runCatching {
                gateway.readProjectTextFile(project.summary.documentId, path)
            }.getOrNull()
        }
        val document = AdaptiveResultAnalyzer.analyze(
            projectName = project.summary.name,
            extracted = normalized,
            sourceText = sourceText,
        )
        val html = AdaptiveResultHtmlRenderer.render(document)
        resultWebRef = resultWebStore.saveIfChanged(
            projectKey = project.summary.documentId,
            projectName = project.summary.name,
            document = document,
            html = html,
        ).ref
    }

    private fun projectWebSnapshot(
        projectId: String,
        runtimeState: RuntimeState,
    ): ProjectUiSnapshot.Web {
        val profile = webInspector.inspect(projectId)
        val stored = webStateStore.snapshot(projectId)
        val configuredUrl = profile.configuredLocalUrl()
        val candidates = listOfNotNull(stored.candidateUrl, configuredUrl).distinct()
        val reachable = webAvailability.endpointReachable(
            projectKey = projectId,
            runtimeState = runtimeState,
            candidateUrls = candidates,
        )
        val reachableUrl = if (reachable == true) {
            webAvailability.reachableUrl(projectId, runtimeState, candidates)
                ?: webAvailability.lastKnownReachableUrl(projectId, candidates)
        } else {
            null
        }
        return ProjectUiSnapshot.Web.resolve(
            profileEnabled = profile.enabled,
            hasCandidateRuntimeUrl = stored.candidateUrl != null,
            hasConfiguredLocalUrl = configuredUrl != null,
            runtimeState = runtimeState,
            endpointReachable = reachable,
            reachableUrl = reachableUrl,
            framework = stored.framework ?: profile.framework,
        )
    }

    private fun webHintPorts(
        profile: WebProjectInspector.Profile = webInspector.inspect(project.summary.documentId),
    ): List<Int> = RuntimeWebHintPolicy.ports(
        detectedPort = profile.port,
        framework = profile.framework,
        learnedPort = webLearnedEndpointStore.read(project.summary.documentId)?.port,
        detectedSource = profile.source,
    )

    private fun openProjectPresentation() {
        val projectId = project.summary.documentId
        val lifecycle = lifecycleStore.read(projectId)
        resultWebRef = resultWebStore.latest(projectId)
        val web = projectWebSnapshot(projectId, lifecycle.runtimeState)
        val target = PresentationTargetResolver.resolve(
            webPresentationKnown = web.reachableUrl != null,
            richResultAvailable = richResult != null,
            resultWebAvailable = resultWebRef != null,
        )
        when (target) {
            PresentationTarget.WEB -> {
                val url = web.reachableUrl ?: return
                webAvailability.verifyNow(projectId, url) { listening ->
                    if (!listening) {
                        Toast.makeText(
                            this,
                            getString(R.string.runtime_web_not_listening_message, url),
                            Toast.LENGTH_LONG,
                        ).show()
                        return@verifyNow
                    }
                    val uri = Uri.parse(url)
                    val browser = StudioBrowser.selectedTarget(this, uri)
                    if (browser == null) {
                        Toast.makeText(
                            this,
                            R.string.runtime_rich_result_browser_required,
                            Toast.LENGTH_LONG,
                        ).show()
                        startActivity(Intent(this, SettingsActivity::class.java))
                        return@verifyNow
                    }
                    startActivity(
                        Intent(Intent.ACTION_VIEW, uri).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                            setPackage(browser.packageName)
                        },
                    )
                }
            }
            PresentationTarget.RESULT_WEB -> resultWebRef?.let { result ->
                startActivity(
                    Intent(this, ResultWebActivity::class.java).apply {
                        putExtra(ResultWebActivity.EXTRA_RESULT_ID, result.id)
                        putExtra(ResultWebActivity.EXTRA_PROJECT_NAME, project.summary.name)
                    },
                )
            }
            PresentationTarget.RICH_RESULT -> richResult?.takeUnless { it.isEmpty }?.let { result ->
                startActivity(
                    Intent(this, RichResultActivity::class.java).apply {
                        putExtra(RichResultActivity.EXTRA_PROJECT_NAME, project.summary.name)
                        putStringArrayListExtra(
                            RichResultActivity.EXTRA_LABELS,
                            ArrayList(result.items.map { it.label }),
                        )
                        putStringArrayListExtra(
                            RichResultActivity.EXTRA_URLS,
                            ArrayList(result.items.map { it.url }),
                        )
                    },
                )
            }
            PresentationTarget.NONE -> Toast.makeText(
                this,
                R.string.normal_project_open_unavailable,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun handleActionRequirement(
        result: ProjectControlHub.Result,
        action: ExternalActionGate.Action?,
    ) {
        val rejected = result as? ProjectControlHub.Result.Rejected ?: return
        if (rejected.failure != ProjectControlHub.Failure.EXTERNAL_PREFLIGHT_REQUIRED) return
        if (action != null) {
            externalActionGate.request(
                projectId = project.summary.documentId,
                action = action,
                origin = ExternalActionGate.Origin.NORMAL_MODE,
            )
        }
        when (rejected.readiness) {
            ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED -> requestRunCommandPermission()
            ExternalProviderReadiness.TERMUX_NOT_INSTALLED,
            ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED,
            -> screenState.value = screenState.value.copy(
                externalReadiness = rejected.readiness,
                message = getString(R.string.normal_external_provider_open_termux),
            )
            ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED,
            ExternalProviderReadiness.BRIDGE_CHECKING,
            ExternalProviderReadiness.BRIDGE_UNRESPONSIVE,
            ExternalProviderReadiness.READY,
            ExternalProviderReadiness.UNAVAILABLE,
            null,
            -> screenState.value = screenState.value.copy(externalReadiness = rejected.readiness)
        }
    }

    private fun requestRunCommandPermission() {
        if (!externalBackend.isTermuxInstalled()) {
            screenState.value = screenState.value.copy(
                externalReadiness = ExternalProviderReadiness.TERMUX_NOT_INSTALLED,
                message = getString(R.string.normal_external_provider_open_termux),
            )
            return
        }
        if (externalBackend.hasRunCommandPermission()) {
            externalPreflight.probe()
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
        if (requestCode != REQUEST_RUN_COMMAND) return
        if (externalBackend.hasRunCommandPermission()) {
            externalPreflight.probe()
        } else {
            screenState.value = screenState.value.copy(
                externalReadiness = ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED,
                message = getString(R.string.normal_external_provider_permission_required),
            )
        }
    }

    private fun resumeExternalActionGate() {
        val request = externalActionGate.claimReady(
            projectId = project.summary.documentId,
            origin = ExternalActionGate.Origin.NORMAL_MODE,
        ) ?: return
        when (request.action) {
            ExternalActionGate.Action.PREPARE -> prepareProjectAfterGuidance()
            ExternalActionGate.Action.RUN -> runProjectAfterGuidance()
            ExternalActionGate.Action.REFRESH -> refreshProject()
        }
    }

    private fun recheckExternalProvider() {
        val result = externalPreflight.probe()
        screenState.value = screenState.value.copy(
            externalReadiness = result.readiness,
            message = if (result.readiness == ExternalProviderReadiness.BRIDGE_CHECKING) {
                getString(R.string.normal_external_provider_checking)
            } else {
                screenState.value.message
            },
        )
    }

    private fun openTermux() {
        val launch = packageManager.getLaunchIntentForPackage(TermuxContract.PACKAGE_NAME)
        if (launch != null) {
            startActivity(launch)
        } else {
            Toast.makeText(this, getString(R.string.home_no_launchable_termux), Toast.LENGTH_SHORT).show()
        }
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

    companion object {
        private const val REQUEST_RUN_COMMAND = 7
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
    onRefresh: () -> Unit,
    onOpen: () -> Unit,
    onSaveConfiguration: (Map<String, String>, Boolean) -> Unit,
    onSelectRuntime: (ProjectRuntimeSelection) -> Unit,
    onOpenDeveloper: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onRecheckExternal: () -> Unit,
) {
    SiftAlphaNormalWorkspaceScreen(
        state = state,
        onBack = onBack,
        onPrepare = onPrepare,
        onConfigure = onConfigure,
        onRun = onRun,
        onStop = onStop,
        onRefresh = onRefresh,
        onOpen = onOpen,
        onSaveConfiguration = onSaveConfiguration,
        onSelectRuntime = onSelectRuntime,
        onOpenDeveloper = onOpenDeveloper,
        onRequestPermission = onRequestPermission,
        onOpenTermux = onOpenTermux,
        onRecheckExternal = onRecheckExternal,
    )
}
