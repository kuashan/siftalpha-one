package com.siftalpha.studio

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.siftalpha.studio.project.EmbeddedPythonProjectStager
import com.siftalpha.studio.project.ProjectConfigurationInspector
import com.siftalpha.studio.project.SharedGitHubImportService
import com.siftalpha.studio.project.ConfigurationSource
import com.siftalpha.studio.project.ProjectSecretPolicyInspector
import com.siftalpha.studio.project.PythonCliArgumentKind
import com.siftalpha.studio.project.PythonCliRequirement
import com.siftalpha.studio.project.V04ProjectGateway
import com.siftalpha.studio.project.WebProjectInspector
import com.siftalpha.studio.presentation.ProjectActionPolicy
import com.siftalpha.studio.presentation.ProjectUiSnapshot
import com.siftalpha.studio.presentation.RuntimeActivityIndicatorPolicy
import com.siftalpha.studio.runtime.EmbeddedPythonRuntimeStateMapping
import com.siftalpha.studio.runtime.EmbeddedPythonObservationPolicy
import com.siftalpha.studio.runtime.EmbeddedProjectPollRegistry
import com.siftalpha.studio.runtime.ProjectActivityRegistry
import com.siftalpha.studio.runtime.ProjectRuntimeController
import com.siftalpha.studio.runtime.ProjectRuntimeExecutionPlanner
import com.siftalpha.studio.runtime.ProjectRuntimeSelection
import com.siftalpha.studio.runtime.ProjectRuntimeSelectionStore
import com.siftalpha.studio.runtime.ProjectSecretStore
import com.siftalpha.studio.runtime.PresentationTarget
import com.siftalpha.studio.runtime.RichResultDetectionPolicy
import com.siftalpha.studio.runtime.PresentationTargetResolver
import com.siftalpha.studio.runtime.PythonCliLaunchResolver
import com.siftalpha.studio.runtime.PythonLaunchInvocation
import com.siftalpha.studio.runtime.PythonNativeWebLaunchCandidate
import com.siftalpha.studio.runtime.RuntimeArgumentParser
import com.siftalpha.studio.runtime.RuntimeCommand
import com.siftalpha.studio.runtime.RuntimeControlPath
import com.siftalpha.studio.runtime.RuntimeControlReason
import com.siftalpha.studio.runtime.RuntimeControlRequest
import com.siftalpha.studio.runtime.RuntimeFailureReason
import com.siftalpha.studio.runtime.RuntimeLifecycleOperation
import com.siftalpha.studio.runtime.RuntimeLifecycleResolver
import com.siftalpha.studio.runtime.RuntimeLifecycleState
import com.siftalpha.studio.runtime.RuntimePresentationState
import com.siftalpha.studio.runtime.RuntimeLifecycleStore
import com.siftalpha.studio.runtime.RuntimeForegroundRecoveryGate
import com.siftalpha.studio.runtime.RuntimeKind
import com.siftalpha.studio.runtime.RuntimeOperationAction
import com.siftalpha.studio.runtime.RuntimeOperationPhase
import com.siftalpha.studio.runtime.RuntimeOperationProvider
import com.siftalpha.studio.runtime.RuntimeOperationRecord
import com.siftalpha.studio.runtime.ProjectOperationCoordinator
import com.siftalpha.studio.runtime.ExternalResultDisposition
import com.siftalpha.studio.runtime.ExternalActionGate
import com.siftalpha.studio.runtime.ExternalProviderPreflightResult
import com.siftalpha.studio.runtime.ExternalProviderProbeCoordinator
import com.siftalpha.studio.runtime.ExternalProviderReadiness
import com.siftalpha.studio.runtime.ExternalProviderResumePolicy
import com.siftalpha.studio.runtime.RuntimeOwnership
import com.siftalpha.studio.runtime.RuntimeOwnershipPolicy
import com.siftalpha.studio.runtime.RuntimeResult
import com.siftalpha.studio.runtime.RuntimeState
import com.siftalpha.studio.runtime.RuntimeAutoObservationPolicy
import com.siftalpha.studio.runtime.RuntimeObservationStep
import com.siftalpha.studio.runtime.ObservationDispatchDecision
import com.siftalpha.studio.runtime.ObservationPresentationPolicy
import com.siftalpha.studio.runtime.RuntimeWebObservationProbePolicy
import com.siftalpha.studio.runtime.ProjectStatusGuidancePolicy
import com.siftalpha.studio.runtime.ProjectStatusPresentationPolicy
import com.siftalpha.studio.runtime.RuntimeWebAvailabilityTracker
import com.siftalpha.studio.runtime.RuntimeWebCandidateSource
import com.siftalpha.studio.runtime.RuntimeWebDetectionCadence
import com.siftalpha.studio.runtime.RuntimeWebDiscoveryScopePolicy
import com.siftalpha.studio.runtime.RuntimeWebEndpointAuthorityPolicy
import com.siftalpha.studio.runtime.RuntimeWebEndpointClassifier
import com.siftalpha.studio.runtime.RuntimeWebHintPolicy
import com.siftalpha.studio.runtime.RuntimeWebLearnedEndpointPolicy
import com.siftalpha.studio.runtime.RuntimeWebLearnedEndpointStore
import com.siftalpha.studio.runtime.RuntimeWebLearnedLaunchStore
import com.siftalpha.studio.runtime.RuntimeWebStateStore
import com.siftalpha.studio.runtime.RuntimeWebUiStatus
import com.siftalpha.studio.runtime.RuntimeWebUrl
import com.siftalpha.studio.runtime.RichResultDocument
import com.siftalpha.studio.runtime.RichResultLifecyclePolicy
import com.siftalpha.studio.runtime.RichResultParser
import com.siftalpha.studio.runtime.AdaptiveResultAnalyzer
import com.siftalpha.studio.runtime.AdaptiveResultHtmlRenderer
import com.siftalpha.studio.runtime.ResultWebStore
import com.siftalpha.studio.runtime.RuntimeProgramOutputExtractor
import com.siftalpha.studio.runtime.TermuxContract
import com.siftalpha.studio.runtime.TermuxBackend
import com.siftalpha.studio.siftalphax.EmbeddedPythonEnvironmentManager
import com.siftalpha.studio.siftalphax.EmbeddedPythonSession
import com.siftalpha.studio.siftalphax.InternalAlpineEnvironmentManager
import com.siftalpha.studio.siftalphax.InternalAlpineSession
import com.siftalpha.studio.siftalphax.InternalAlpineWebObservation
import com.siftalpha.studio.siftalphax.EmbeddedPythonSnapshot
import com.siftalpha.studio.siftalphax.EmbeddedPythonState
import com.siftalpha.studio.siftalphax.InternalPythonBackend
import com.siftalpha.studio.runtime.TermuxResultBus
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors

/** Import/get project -> isolated venv -> dependencies -> run/stop/status/logs. */
open class V04Activity : StudioActivity() {

    private data class Pending(
        val action: ProjectRuntimeController.Action,
        val folderName: String,
        val documentId: String? = null,
        val cloneSpec: ProjectRuntimeController.GitHubCloneSpec? = null,
        val openBrowserAfterLogs: Boolean = false,
        val browserConfiguredUrl: String? = null,
        val browserFramework: String? = null,
        val webLogDiscoveryAllowed: Boolean = false,
        val automaticObservation: Boolean = false,
        val observationGeneration: Long? = null,
    )

    private data class ExternalObservation(
        val project: V04ProjectGateway.RuntimeProject,
        val generation: Long,
        val webLogDiscoveryAllowed: Boolean,
        val webHintPorts: List<Int>,
        var finalLogsRequested: Boolean = false,
        var finalLogsCompleted: Boolean = false,
        var webLogProbeCount: Int = 0,
        var statusesSinceWebLogProbe: Int = 0,
    )

    private data class InternalWebObservationRecord(
        val sessionId: String,
        val generation: Long,
        val observation: InternalAlpineWebObservation,
    )

    private data class InternalWebDiscoveryRetryRecord(
        val sessionId: String,
        val generation: Long,
        val missCount: Int,
        val nextEligibleAtEpochMs: Long,
    )

    private data class DeferredManualAction(
        val project: V04ProjectGateway.RuntimeProject,
        val action: ProjectRuntimeController.Action,
        val openBrowserAfterLogs: Boolean = false,
        val browserConfiguredUrl: String? = null,
        val browserFramework: String? = null,
        val silentRecovery: Boolean = false,
        val controlRequest: RuntimeControlRequest? = null,
        val launchInvocation: PythonLaunchInvocation? = null,
        val webLogDiscoveryAllowed: Boolean = false,
        val webHintPorts: List<Int> = emptyList(),
    )

    private data class BrowserTarget(
        val label: String,
        val packageName: String,
    )

    private data class ProjectCardData(
        val project: V04ProjectGateway.RuntimeProject,
        val configurationSnapshot: ProjectConfigurationUiController.Snapshot,
        val webProfile: WebProjectInspector.Profile,
    )

    private data class ProjectRefreshResult(
        val rootSelected: Boolean,
        val runtimeSupported: Boolean,
        val cards: List<ProjectCardData> = emptyList(),
        val errorMessage: String? = null,
    )

    private lateinit var backend: TermuxBackend
    private lateinit var gateway: V04ProjectGateway
    private lateinit var runtime: ProjectRuntimeController
    private lateinit var secretStore: ProjectSecretStore
    private lateinit var secretPolicyInspector: ProjectSecretPolicyInspector
    private lateinit var configurationInspector: ProjectConfigurationInspector
    private lateinit var configurationUi: ProjectConfigurationUiController
    private lateinit var webInspector: WebProjectInspector
    private lateinit var webStateStore: RuntimeWebStateStore
    private lateinit var webLearnedEndpointStore: RuntimeWebLearnedEndpointStore
    private lateinit var webLearnedLaunchStore: RuntimeWebLearnedLaunchStore
    private lateinit var webAvailability: RuntimeWebAvailabilityTracker
    private lateinit var projectOutputs: ProjectOutputPanelController
    private lateinit var prepareLiveProgress: PrepareLiveProgressController
    private lateinit var lifecycleStore: RuntimeLifecycleStore
    private lateinit var operationCoordinator: ProjectOperationCoordinator
    private lateinit var externalPreflight: ExternalProviderProbeCoordinator
    private lateinit var externalActionGate: ExternalActionGate
    private lateinit var backgroundReliabilityGuidance: BackgroundReliabilityGuidanceController
    private lateinit var projectRuntimeSelectionStore: ProjectRuntimeSelectionStore
    private val recoveryProjects = mutableSetOf<String>()
    private val failureReasons = mutableMapOf<String, String>()
    private val webProfileCache = mutableMapOf<String, WebProjectInspector.Profile>()
    /** Activity-lifetime Rich Result cache; raw output remains owned by ProjectOutputPanelController. */
    private val richResults = mutableMapOf<String, RichResultDocument>()
    private lateinit var resultWebStore: ResultWebStore
    private val resultWebResults = mutableMapOf<String, ResultWebStore.ResultRef>()
    private val resultWebSuppressedProjects = mutableSetOf<String>()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshExecutor = Executors.newSingleThreadExecutor()
    private val webRecognitionExecutor = Executors.newSingleThreadExecutor()
    private val webRecognitionInFlight = mutableSetOf<String>()
    private var refreshScheduled = false
    private var refreshInFlight = false
    private var refreshGeneration = 0L
    // A user/import requested disk refresh must survive refresh coalescing. Ordinary lifecycle,
    // Runtime, Web and configuration callbacks stay cache-only.
    private var forceProjectInspectionPending = false
    private var activityStarted = false
    private val persistedRuntimeRecoveryGate = RuntimeForegroundRecoveryGate()
    private val externalObservations = mutableMapOf<String, ExternalObservation>()
    private val externalObservationGenerations = mutableMapOf<String, Long>()
    private val externalObservationRunnables = mutableMapOf<String, Runnable>()
    private val externalGateDeferredActions =
        mutableMapOf<String, Pair<Long, DeferredManualAction>>()
    private val deferredManualActions = mutableMapOf<String, DeferredManualAction>()
    private val embeddedStartExecutor = Executors.newSingleThreadExecutor()
    private val embeddedObservationExecutor = Executors.newSingleThreadExecutor()
    private val internalWebObservationExecutor = Executors.newSingleThreadExecutor()
    private val internalWebObservationInFlight = mutableSetOf<String>()
    private val internalWebObservationCache = mutableMapOf<String, InternalWebObservationRecord>()
    private val internalWebDiscoveryRetries = mutableMapOf<String, InternalWebDiscoveryRetryRecord>()
    private val embeddedStartInFlight = mutableSetOf<String>()
    private val operationDeadlineRunnables = mutableMapOf<String, Runnable>()
    private val projectActivities = ProjectActivityRegistry()
    private val externalActivityTokens = mutableMapOf<Int, ProjectActivityRegistry.Token>()
    private val cancelledExternalExecutions = mutableSetOf<Int>()
    private val embeddedLastSnapshots = mutableMapOf<String, EmbeddedPythonSnapshot>()
    private val embeddedConfigurationFindings = mutableSetOf<String>()
    private val embeddedLastOutputRenderAt = mutableMapOf<String, Long>()
    private val embeddedRuntimeOwnership = mutableMapOf<String, RuntimeOwnership>()
    private val embeddedPolls =
        EmbeddedProjectPollRegistry<V04ProjectGateway.RuntimeProject>()
    private val embeddedPollRunnables = mutableMapOf<String, Runnable>()
    private lateinit var rootState: TextView
    private lateinit var externalProviderDiagnostics: TextView
    private lateinit var projectList: LinearLayout
    private lateinit var output: TextView

    private val selectedProjectDocumentId: String?
        get() = intent.getStringExtra(EXTRA_PROJECT_DOCUMENT_ID)

    private val pending: MutableMap<Int, Pending>
        get() = PENDING_TASKS
    private val states: MutableMap<String, String>
        get() = RUNTIME_STATES
    private val typedStates: MutableMap<String, RuntimeState>
        get() = RUNTIME_TYPED_STATES
    private val environmentStates: MutableMap<String, Boolean>
        get() = RUNTIME_ENVIRONMENT_READY

    private fun environmentStateKey(
        projectKey: String,
        selection: ProjectRuntimeSelection,
    ): String = selection.name + ":" + projectKey

    private fun environmentReady(
        projectKey: String,
        selection: ProjectRuntimeSelection = projectRuntimeSelectionStore.read(projectKey),
    ): Boolean? = environmentStates[environmentStateKey(projectKey, selection)]

    private fun setEnvironmentReady(
        projectKey: String,
        selection: ProjectRuntimeSelection,
        ready: Boolean?,
    ) {
        val key = environmentStateKey(projectKey, selection)
        if (ready == null) {
            environmentStates.remove(key)
        } else {
            environmentStates[key] = ready
        }
    }

    private fun clearEnvironmentStates(projectKey: String) {
        ProjectRuntimeSelection.entries.forEach { selection ->
            environmentStates.remove(environmentStateKey(projectKey, selection))
        }
    }

    private val resultListener: (RuntimeResult) -> Unit = { result ->
        runOnUiThread {
            if (::prepareLiveProgress.isInitialized && prepareLiveProgress.consumeIfProbe(result)) {
                return@runOnUiThread
            }
            // STOP removes cancelled project operations from the visible Pending table immediately.
            // A late external callback still has to be consumed, but must never re-open or mutate UI state.
            if (cancelledExternalExecutions.remove(result.executionId)) {
                pending.remove(result.executionId)
                TermuxResultBus.consume(result.executionId)
                externalActivityTokens.remove(result.executionId)?.let(projectActivities::finish)
                return@runOnUiThread
            }
            // A very fast Termux command can publish before the UI has registered its Pending item.
            // Never consume such an unmatched result: registerPending() will immediately reconcile it.
            val item = pending.remove(result.executionId) ?: return@runOnUiThread
            if (
                item.documentId != null &&
                operationCoordinator.consumeExternalResultDisposition(result.executionId) ==
                    ExternalResultDisposition.FENCED
            ) {
                // The shared coordinator has already superseded or timed out this generation.
                // Consume the late result without allowing the old UI item to mutate current state.
                TermuxResultBus.consume(result.executionId)
                externalActivityTokens.remove(result.executionId)?.let(projectActivities::finish)
                return@runOnUiThread
            }
            TermuxResultBus.consume(result.executionId)
            externalActivityTokens.remove(result.executionId)?.let(projectActivities::finish)
            if (item.automaticObservation && !isCurrentAutomaticObservation(item)) {
                // A STOP, CLEAN, new START, or a newer observation generation superseded this
                // delayed result. Consume it, but never let it mutate the new project state.
                return@runOnUiThread
            }
            val safeResult = if (::secretStore.isInitialized) {
                result.copy(
                    stdout = secretStore.redactRuntimeText(item.folderName, result.stdout),
                    stderr = secretStore.redactRuntimeText(item.folderName, result.stderr),
                    internalErrorMessage = secretStore.redactRuntimeText(
                        item.folderName,
                        result.internalErrorMessage,
                    ),
                )
            } else {
                result
            }
            finishOperation(
                projectId = item.documentId ?: item.folderName,
                generation = null,
                phase = externalOperationPhase(safeResult),
                executionId = result.executionId,
            )
            if (item.action == ProjectRuntimeController.Action.CLONE_GITHUB) {
                renderResult(safeResult)
            } else if (!item.automaticObservation || item.action == ProjectRuntimeController.Action.LOGS) {
                // Automatic STATUS is an internal observation and must not overwrite or expand the
                // user's raw-log panel. Final LOGS remains available as collapsed diagnostics.
                renderProjectResult(
                    folderName = item.folderName,
                    result = safeResult,
                    expand = ObservationPresentationPolicy.shouldExpandRawLogs(item.automaticObservation),
                )
            }
            handleResult(item, safeResult)
        }
    }

    private val externalPreflightListener: (ExternalProviderPreflightResult) -> Unit = { result ->
        runOnUiThread {
            updateExternalProviderDiagnostics()
            if (result.ready) {
                resumeExternalActionGate()
            }
            if (!isFinishing && !isDestroyed && ::projectList.isInitialized) refresh()
        }
    }

    private val operationTimeoutListener: (ProjectOperationCoordinator.ExternalTimeout) -> Unit = { timeout ->
        runOnUiThread {
            if (!::lifecycleStore.isInitialized) return@runOnUiThread
            val projectId = timeout.projectId
            pending.entries
                .filter { it.value.documentId == projectId }
                .map { it.key }
                .forEach { executionId ->
                    cancelledExternalExecutions += executionId
                    pending.remove(executionId)
                    TermuxResultBus.consume(executionId)
                    externalActivityTokens.remove(executionId)?.let(projectActivities::finish)
                }
            projectActivities.cancelProject(projectId)
            runCatching { gateway.projects().firstOrNull { it.summary.documentId == projectId } }
                .getOrNull()
                ?.let { timedOutProject ->
                    if (::prepareLiveProgress.isInitialized) {
                        prepareLiveProgress.finish(timedOutProject.folderName)
                    }
                }
            recoveryProjects.remove(projectId)
            failureReasons[projectId] = timeout.failureReason
            typedStates[projectId] = lifecycleStore.read(projectId).runtimeState
            states[projectId] = getString(R.string.runtime_operation_timed_out)
            if (!isFinishing && !isDestroyed && ::projectList.isInitialized) refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clearLocalizedStateCacheIfNeeded()
        backend = TermuxBackend(this)
        gateway = V04ProjectGateway(this)
        projectRuntimeSelectionStore = ProjectRuntimeSelectionStore(this)
        runtime = ProjectRuntimeController(
            gateway = gateway,
            embeddedPythonSession = EmbeddedPythonSession.shared(this),
            embeddedPythonProjectStager = EmbeddedPythonProjectStager(this),
            embeddedPythonEnvironmentManager = EmbeddedPythonEnvironmentManager(this),
            internalAlpineEnvironmentManager = InternalAlpineEnvironmentManager(this),
            internalAlpineSession = InternalAlpineSession.shared(this),
        )
        secretStore = ProjectSecretStore(this)
        lifecycleStore = RuntimeLifecycleStore(this)
        operationCoordinator = ProjectOperationCoordinator.shared(this)
        externalPreflight = ExternalProviderProbeCoordinator.shared(this)
        externalActionGate = ExternalActionGate.shared(this)
        backgroundReliabilityGuidance = BackgroundReliabilityGuidanceController(this)
        secretPolicyInspector = ProjectSecretPolicyInspector(this)
        configurationInspector = ProjectConfigurationInspector(this)
        webInspector = WebProjectInspector(this)
        webStateStore = RuntimeWebStateStore(this)
        webLearnedEndpointStore = RuntimeWebLearnedEndpointStore(this)
        webLearnedLaunchStore = RuntimeWebLearnedLaunchStore(this)
        webAvailability = RuntimeWebAvailabilityTracker {
            if (!isFinishing && !isDestroyed && ::projectList.isInitialized) {
                refresh()
            }
        }
        projectOutputs = ProjectOutputPanelController(this)
        resultWebStore = ResultWebStore(this)
        configurationUi = ProjectConfigurationUiController(
            activity = this,
            inspector = configurationInspector,
            store = secretStore,
        ) {
            if (!isFinishing && !isDestroyed && ::projectList.isInitialized) {
                refresh()
            }
        }
        setContentView(buildUi())
        prepareLiveProgress = PrepareLiveProgressController(
            context = this,
            backend = backend,
            gateway = gateway,
            runtime = runtime,
        ) { folderName, liveText ->
            if (::projectOutputs.isInitialized) {
                projectOutputs.write(folderName, liveText, expand = true)
            }
        }
        refresh()
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        if (::webAvailability.isInitialized) webAvailability.resume()
        TermuxResultBus.addListener(resultListener)
        externalPreflight.addListener(externalPreflightListener)
        if (::prepareLiveProgress.isInitialized) prepareLiveProgress.resume()
        operationCoordinator.addTimeoutListener(operationTimeoutListener)
        pending.keys.toList().forEach { id ->
            TermuxResultBus.consume(id)?.let(resultListener)
        }
        reconcilePersistedOperations()
        if (persistedRuntimeRecoveryGate.consumeInitialRecovery()) {
            recoverPersistedRuntimeStates()
        }
        resumeExternalObservations()
    }

    override fun onResume() {
        super.onResume()
        if (::externalActionGate.isInitialized && ::externalPreflight.isInitialized) {
            val readiness = externalPreflight.current()
            if (readiness.ready) {
                resumeExternalActionGate()
            } else if (
                ExternalProviderResumePolicy.shouldRetry(
                    readiness = readiness.readiness,
                    hasDeferredAction = externalGateDeferredActions.isNotEmpty(),
                )
            ) {
                externalPreflight.probe()
            }
        }
        if (::projectList.isInitialized) refresh()
        resumeEmbeddedPolling()
        resumeExternalObservations()
    }

    override fun onStop() {
        activityStarted = false
        embeddedPollRunnables.values.forEach(refreshHandler::removeCallbacks)
        // Observation state is retained per project, but all delayed UI work is paused with the Activity.
        // The Runtime itself continues in its provider-owned process/session layer.
        refreshHandler.removeCallbacksAndMessages(null)
        refreshScheduled = false
        if (::webAvailability.isInitialized) webAvailability.pause()
        if (::prepareLiveProgress.isInitialized) prepareLiveProgress.pause()
        if (::operationCoordinator.isInitialized) {
            operationCoordinator.removeTimeoutListener(operationTimeoutListener)
        }
        TermuxResultBus.removeListener(resultListener)
        if (::externalPreflight.isInitialized) {
            externalPreflight.removeListener(externalPreflightListener)
        }
        super.onStop()
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacksAndMessages(null)
        embeddedPolls.clear()
        embeddedPollRunnables.clear()
        externalObservations.keys.toList().forEach(::invalidateExternalObservation)
        externalObservationRunnables.clear()
        deferredManualActions.clear()
        externalGateDeferredActions.clear()
        operationDeadlineRunnables.values.forEach(refreshHandler::removeCallbacks)
        operationDeadlineRunnables.clear()
        embeddedStartExecutor.shutdownNow()
        embeddedObservationExecutor.shutdownNow()
        internalWebObservationExecutor.shutdownNow()
        webRecognitionInFlight.clear()
        webRecognitionExecutor.shutdownNow()
        refreshExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun clearLocalizedStateCacheIfNeeded() {
        val tag = StudioLanguage.current(this).tag
        if (RUNTIME_STATES_LANGUAGE_TAG != tag) {
            RUNTIME_STATES.clear()
            RUNTIME_STATES_LANGUAGE_TAG = tag
        }
    }

    private fun buildUi(): android.view.View {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(16, 19, 24)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(30))
        }
        scroll.addView(root)

        root.addView(text(getString(R.string.app_name), 25f, true).apply { setTextColor(Color.WHITE) })
        root.addView(text(getString(R.string.runtime_center_subtitle, appVersionName()), 13f, false).apply {
            setTextColor(Color.rgb(160, 166, 178))
            setPadding(0, dp(2), 0, dp(8))
        })
        root.addView(button(getString(R.string.runtime_center_back)) { finish() })

        rootState = text(getString(R.string.runtime_center_reading_root), 13f, false).apply {
            setTextColor(Color.rgb(190, 194, 204))
            setPadding(0, dp(6), 0, dp(8))
        }
        root.addView(rootState)

        root.addView(section(getString(R.string.runtime_external_provider_diagnostics_title)))
        externalProviderDiagnostics = text("", 12f, false).apply {
            setTextColor(Color.rgb(190, 194, 204))
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(externalProviderDiagnostics)
        updateExternalProviderDiagnostics()

        // Import belongs to the collection-level Runtime Center. A single-project workspace
        // keeps attention on the selected project and never offers unrelated import actions.
        if (selectedProjectDocumentId == null) {
            root.addView(section(getString(R.string.runtime_center_section_import)))
            val importRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            importRow.addView(smallButton(getString(R.string.runtime_center_import_py)) { chooseFile(false) }, weight())
            importRow.addView(
                smallButton(getString(R.string.runtime_center_import_zip)) { chooseFile(true) },
                weight().apply { marginStart = dp(5) },
            )
            importRow.addView(smallButton("GitHub") { showGitHubDialog() }, weight().apply { marginStart = dp(5) })
            root.addView(importRow)
            root.addView(button(getString(R.string.runtime_center_refresh)) {
                refresh(forceProjectInspection = true)
            }.apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(7)
            })
        }

        root.addView(section(getString(R.string.runtime_center_section_system_output)))
        output = TextView(this).apply {
            text = getString(R.string.runtime_center_no_command)
            textSize = 12.5f
            setTextColor(Color.rgb(224, 228, 236))
            setBackgroundColor(Color.rgb(24, 28, 35))
            typeface = Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setTextIsSelectable(true)
        }
        root.addView(output)

        root.addView(section(getString(R.string.runtime_center_section_run)))
        projectList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(projectList)
        return scroll
    }

    private fun refresh(forceProjectInspection: Boolean = false) {
        if (!::projectList.isInitialized) return

        // Coalesce onStart/onResume/result/probe callbacks. Only explicit project-content changes
        // consume the expensive SAF inspection path; normal UI/runtime refreshes reuse persisted
        // project/configuration/Web inspection caches.
        refreshGeneration += 1
        if (forceProjectInspection) forceProjectInspectionPending = true
        if (refreshScheduled || refreshInFlight) return
        refreshScheduled = true
        val selectedId = selectedProjectDocumentId
        refreshHandler.postDelayed({
            refreshScheduled = false
            val requestGeneration = refreshGeneration
            val forceInspection = forceProjectInspectionPending
            forceProjectInspectionPending = false
            refreshInFlight = true
            refreshExecutor.execute {
                val result = runCatching {
                    loadRefreshResult(selectedId, forceInspection)
                }.getOrElse { error ->
                    ProjectRefreshResult(
                        rootSelected = true,
                        runtimeSupported = false,
                        errorMessage = error.message ?: error.javaClass.simpleName,
                    )
                }
                runOnUiThread {
                    refreshInFlight = false
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (requestGeneration != refreshGeneration) {
                        refresh()
                        return@runOnUiThread
                    }
                    renderRefreshResult(result)
                }
            }
        }, REFRESH_DEBOUNCE_MS)
    }

    private fun loadRefreshResult(
        selectedId: String?,
        forceProjectInspection: Boolean,
    ): ProjectRefreshResult {
        val rootSelected = gateway.rootUri() != null
        if (!rootSelected) {
            return ProjectRefreshResult(
                rootSelected = false,
                runtimeSupported = false,
            )
        }

        val runtimeSupported = runCatching { runtime.runtimeSupported() }.getOrDefault(false)
        return try {
            val projects = gateway.projects(forceRefresh = forceProjectInspection).let { allProjects ->
                selectedId?.let { id ->
                    allProjects.filter { it.summary.documentId == id }
                } ?: allProjects
            }
            val cards = projects.map { project ->
                ProjectCardData(
                    project = project,
                    configurationSnapshot = configurationUi.snapshot(
                        project.summary.documentId,
                        project.folderName,
                        forceProjectInspection = forceProjectInspection,
                    ),
                    webProfile = runCatching {
                        webInspector.inspect(
                            project.summary.documentId,
                            forceRefresh = forceProjectInspection,
                        )
                    }.getOrElse {
                        WebProjectInspector.Profile(false, null, "none", null, null)
                    },
                )
            }
            ProjectRefreshResult(
                rootSelected = true,
                runtimeSupported = runtimeSupported,
                cards = cards,
            )
        } catch (error: Throwable) {
            ProjectRefreshResult(
                rootSelected = true,
                runtimeSupported = runtimeSupported,
                errorMessage = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun renderRefreshResult(result: ProjectRefreshResult) {
        projectOutputs.beginRefresh()
        updateExternalProviderDiagnostics()
        if (!result.rootSelected) {
            projectOutputs.retainOnly(emptySet())
            rootState.text = getString(R.string.runtime_center_root_unselected)
            replaceProjectViews(listOf(hint(getString(R.string.runtime_center_root_required))))
            return
        }

        rootState.text = getString(
            if (result.runtimeSupported) {
                R.string.runtime_center_runtime_available
            } else {
                R.string.runtime_center_runtime_files_only
            },
        )
        if (result.errorMessage != null) {
            projectOutputs.retainOnly(emptySet())
            replaceProjectViews(
                listOf(
                    hint(
                        getString(
                            R.string.runtime_center_read_projects_failed,
                            result.errorMessage,
                        ),
                    ),
                ),
            )
            return
        }

        result.cards.forEach { data ->
            reconcileWebDiscoveryScope(
                projectKey = data.project.summary.documentId,
                webCapabilityEnabled = data.webProfile.enabled,
            )
        }
        val projects = result.cards.map { it.project }
        result.cards.firstOrNull { data ->
            val snapshot = runCatching {
                runtime.embeddedPythonSnapshotFor(data.project.summary.documentId)
            }.getOrNull()
            snapshot != null &&
                ownsEmbeddedObservation(data.project.summary.documentId, snapshot) &&
                isEmbeddedActive(snapshot)
        }?.project?.let(::scheduleEmbeddedPolling)
        projectOutputs.retainOnly(projects.map { it.folderName }.toSet())
        if (result.cards.isEmpty()) {
            replaceProjectViews(listOf(hint(getString(R.string.runtime_center_projects_empty))))
            return
        }

        // Build detached card views first. The visible list is replaced only after all cards are
        // ready, so a refresh never exposes an empty white/blank workspace.
        val nextViews = mutableListOf<android.view.View>()
        if (selectedProjectDocumentId == null) {
            nextViews += text(
                getString(R.string.runtime_center_all_projects, result.cards.size),
                14f,
                true,
            ).apply {
                setTextColor(Color.rgb(170, 224, 190))
                setPadding(0, 0, 0, dp(8))
            }
        }
        result.cards.forEach { data ->
            nextViews += card(
                project = data.project,
                configurationSnapshot = data.configurationSnapshot,
                webProfile = data.webProfile,
                runtimeSupported = result.runtimeSupported,
            )
        }
        replaceProjectViews(nextViews)
    }

    private fun updateExternalProviderDiagnostics() {
        if (!::externalProviderDiagnostics.isInitialized || !::externalPreflight.isInitialized) return
        val result = externalPreflight.current()
        val permission = if (result.runCommandPermissionGranted) "GRANTED" else "REQUIRED"
        externalProviderDiagnostics.text = getString(
            R.string.runtime_external_provider_diagnostics_format,
            if (result.termuxInstalled) "YES" else "NO",
            permission,
            diagnosticValue(result.allowExternalApps),
            diagnosticValue(result.bridgeResponsive),
            if (result.ready) "YES" else "NO",
        )
    }

    private fun diagnosticValue(value: Boolean?): String = when (value) {
        true -> "YES"
        false -> "NO"
        null -> "UNKNOWN"
    }

    private fun replaceProjectViews(views: List<android.view.View>) {
        projectList.removeAllViews()
        views.forEach { projectList.addView(it) }
    }

    private fun card(
        project: V04ProjectGateway.RuntimeProject,
        configurationSnapshot: ProjectConfigurationUiController.Snapshot,
        webProfile: WebProjectInspector.Profile,
        runtimeSupported: Boolean,
    ): android.view.View {
        val summary = project.summary
        val stateKey = summary.documentId
        webProfileCache[stateKey] = webProfile
        restoreStoredState(stateKey)
        val embeddedCandidate = runCatching {
            runtime.embeddedPythonSnapshotFor(stateKey)
        }.getOrNull()
        val embeddedSnapshot = embeddedCandidate?.takeIf {
            ownsEmbeddedObservation(stateKey, it)
        }
        val embeddedRuntimeState = embeddedSnapshot?.let { EmbeddedPythonRuntimeStateMapping.toRuntimeState(it) }
        val typedState = embeddedRuntimeState ?: typedStates[stateKey] ?: RuntimeState.UNKNOWN
        val runtimeSelection = projectRuntimeSelectionStore.read(stateKey)
        val embeddedObservationOwned = embeddedSnapshot != null
        val embeddedActive = embeddedSnapshot?.let(::isEmbeddedActive) == true
        val webSnapshot = webStateStore.snapshot(stateKey)
        val configuredWebUrl = webProfile.configuredLocalUrl()
        // Project configuration, learned endpoints and framework defaults are hints only. Android
        // endpoint verification starts only after Runtime ownership discovery publishes a candidate.
        val candidateWebUrls = listOfNotNull(webSnapshot.candidateUrl).distinct()
        val persistedVerifiedWebUrl = webSnapshot.verifiedUrl?.takeIf { it in candidateWebUrls }
        if (
            persistedVerifiedWebUrl != null &&
            ::webAvailability.isInitialized
        ) {
            webAvailability.restoreVerifiedIdentity(
                projectKey = stateKey,
                url = persistedVerifiedWebUrl,
                checkedAtEpochMs = webSnapshot.verifiedAtEpochMs,
            )
        }
        val endpointReachable = if (
            candidateWebUrls.isEmpty() &&
            typedState == RuntimeState.RUNNING &&
            webProfile.enabled
        ) {
            null
        } else if (::webAvailability.isInitialized) {
            webAvailability.endpointReachable(stateKey, typedState, candidateWebUrls)
        } else {
            null
        }
        val reachableWebUrl = if (::webAvailability.isInitialized) {
            webAvailability.reachableUrl(stateKey, typedState, candidateWebUrls)
        } else {
            null
        }
        if (
            endpointReachable == true &&
            reachableWebUrl != null
        ) {
            if (webSnapshot.verifiedUrl != reachableWebUrl) {
                webStateStore.rememberVerifiedUrl(stateKey, reachableWebUrl)
            }
            if (
                ::webLearnedEndpointStore.isInitialized &&
                RuntimeWebLearnedEndpointPolicy.canLearn(webSnapshot.source, reachableWebUrl)
            ) {
                webLearnedEndpointStore.rememberOwnedVerified(
                    projectKey = stateKey,
                    url = reachableWebUrl,
                    authorityFingerprint = webEndpointAuthorityFingerprint(webProfile),
                )
            }
            if (::webLearnedLaunchStore.isInitialized) {
                webLearnedLaunchStore.markVerified(stateKey)
            }
        }
        val lastKnownWebUrl = if (::webAvailability.isInitialized) {
            webAvailability.lastKnownReachableUrl(stateKey, candidateWebUrls)
        } else {
            null
        }
        val verifiedWebUrl = reachableWebUrl ?: lastKnownWebUrl ?: persistedVerifiedWebUrl
        val reachableWebFramework = when (reachableWebUrl ?: verifiedWebUrl) {
            webSnapshot.candidateUrl -> webSnapshot.framework ?: webProfile.framework
            configuredWebUrl -> webProfile.framework ?: webSnapshot.framework
            else -> webProfile.framework ?: webSnapshot.framework
        }
        val web = ProjectUiSnapshot.Web.resolve(
            profileEnabled = webProfile.enabled,
            hasCandidateRuntimeUrl = !webSnapshot.candidateUrl.isNullOrBlank(),
            hasConfiguredLocalUrl = configuredWebUrl != null,
            runtimeState = typedState,
            endpointReachable = endpointReachable,
            verifiedWebIdentity = verifiedWebUrl != null,
            // Preserve the last verified project-owned URL as the presentation target while the
            // fresh foreground lifecycle probe runs. Browser launch still calls verifyNow().
            reachableUrl = verifiedWebUrl,
            framework = reachableWebFramework,
        )
        val webUiStatus = web.status
        val richResult = richResults[stateKey]
        val resultWeb = if (stateKey in resultWebSuppressedProjects) {
            null
        } else {
            resultWebResults[stateKey]
                ?: if (::resultWebStore.isInitialized) {
                    resultWebStore.latest(stateKey)?.also { resultWebResults[stateKey] = it }
                } else {
                    null
                }
        }
        val presentationTarget = PresentationTargetResolver.resolve(
            webPresentationKnown = verifiedWebUrl != null,
            richResultAvailable = richResult != null,
            resultWebAvailable = resultWeb != null,
        )
        val pendingItem = pending.values.firstOrNull { item ->
            (item.documentId == summary.documentId ||
                (item.documentId == null && item.folderName == project.folderName)) &&
                ObservationPresentationPolicy.isUserVisiblePending(item.automaticObservation)
        }
        val trackedOperation = currentOperation(stateKey)
        val deferredManualAction = deferredManualActions[stateKey]
        val internalCleanPending = ProjectRuntimeController.Action.CLEAN.takeIf {
            ProjectActivityRegistry.Kind.CLEAN in projectActivities.activeKinds(stateKey)
        }
        val visiblePendingAction = pendingItem?.action ?: deferredManualAction?.action ?:
            internalCleanPending ?: trackedOperation?.action?.toControllerAction()
        val pendingExecutionId = pendingItem?.let { visibleItem ->
            pending.entries.firstOrNull { it.value === visibleItem }?.key
        } ?: trackedOperation?.executionId
        val configurationRequired =
            configurationSnapshot.preflight.missingRequired.isNotEmpty()
        var lifecycleEnvironmentReady = environmentReady(stateKey, runtimeSelection)
        if (
            runtimeSelection == ProjectRuntimeSelection.EMBEDDED_R &&
            lifecycleEnvironmentReady == true &&
            !runtime.embeddedPythonEnvironmentReady(project)
        ) {
            setEnvironmentReady(stateKey, ProjectRuntimeSelection.EMBEDDED_R, false)
            lifecycleEnvironmentReady = false
        }
        val lifecycleState = RuntimeLifecycleResolver.resolve(
            environmentReady = lifecycleEnvironmentReady,
            runtimeState = typedState,
            operation = visiblePendingAction?.toLifecycleOperation()
                ?: RuntimeLifecycleOperation.NONE,
            configurationRequired = configurationRequired,
            processActive = typedState in setOf(
                RuntimeState.PREPARING,
                RuntimeState.STARTING,
                RuntimeState.RUNNING,
            ),
            recoveryInProgress = recoveryProjects.contains(stateKey),
        )
        val snapshot = ProjectUiSnapshot(
            identity = ProjectUiSnapshot.Identity(
                documentId = summary.documentId,
                folderName = project.folderName,
                displayName = summary.name,
                sourceUrl = project.sourceUrl,
            ),
            runtime = ProjectUiSnapshot.Runtime.fromPlannerSelection(
                selection = project.runtimeSelection,
                supported = runtimeSupported,
                stopCapability = typedState in setOf(
                    RuntimeState.PREPARING,
                    RuntimeState.STARTING,
                    RuntimeState.RUNNING,
                ),
            ),
            environment = ProjectUiSnapshot.Environment.from(lifecycleEnvironmentReady),
            configuration = ProjectUiSnapshot.Configuration(
                requiredCount = configurationSnapshot.preflight.requiredCount,
                configuredRequiredCount = configurationSnapshot.preflight.configuredRequiredCount,
                missingRequiredNames = configurationSnapshot.preflight.missingRequired.map { it.name },
                credentialCandidateCount = configurationSnapshot.preflight.credentialCandidateCount,
                runtimeConfigurationDiscovered = configurationSnapshot.runtimeConfigurationDiscovered,
                optionalMissingCount = configurationSnapshot.preflight.optionalMissingCount,
                optionalConfiguredCount = configurationSnapshot.preflight.optionalConfiguredCount,
            ),
            lifecycle = typedState,
            web = web,
            pending = visiblePendingAction?.toUiOperation()?.let { operation ->
                ProjectUiSnapshot.PendingOperation(operation, pendingExecutionId)
            },
            evidence = ProjectUiSnapshot.Evidence(
                lifecycle = if (typedState == RuntimeState.UNKNOWN) {
                    ProjectUiSnapshot.LifecycleEvidence.NONE
                } else {
                    ProjectUiSnapshot.LifecycleEvidence.CACHED
                },
                environment = when (lifecycleEnvironmentReady) {
                    null -> ProjectUiSnapshot.EnvironmentEvidence.NONE
                    else -> ProjectUiSnapshot.EnvironmentEvidence.CACHED
                },
                web = when (endpointReachable) {
                    true -> ProjectUiSnapshot.WebEvidence.VERIFIED
                    false -> ProjectUiSnapshot.WebEvidence.UNREACHABLE
                    null -> if (candidateWebUrls.isEmpty()) {
                        ProjectUiSnapshot.WebEvidence.NONE
                    } else {
                        ProjectUiSnapshot.WebEvidence.PROBE_IN_FLIGHT
                    }
                },
            ),
            lifecycleState = lifecycleState,
            recoveryInProgress = recoveryProjects.contains(stateKey),
            failureReason = failureReasons[stateKey],
        )
        val policy = ProjectActionPolicy.resolve(snapshot, runtimeSelection)
        val presentationState = snapshot.displayedLifecycle
        val activityIndicatorVisible = RuntimeActivityIndicatorPolicy.shouldAnimate(
            RuntimeActivityIndicatorPolicy.Input(
                lifecycleState = snapshot.lifecycleState,
                runtimeState = typedState,
                operationActive = visiblePendingAction != null,
            ),
        )

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(24, 28, 35))
                cornerRadius = dp(9).toFloat()
                setStroke(dp(1), Color.rgb(48, 54, 64))
            }
        }
        box.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(9) }

        if (selectedProjectDocumentId != null) {
            box.addView(section(getString(R.string.runtime_workspace_overview)))
        }
        box.addView(text("📁 ${summary.name}", 16f, true).apply { setTextColor(Color.WHITE) })
        box.addView(text(getString(R.string.runtime_center_source, summary.source), 12f, false).apply {
            setTextColor(Color.rgb(150, 157, 169))
            setPadding(0, dp(3), 0, 0)
        })
        box.addView(text(
            getString(R.string.runtime_selection_current, runtimeSelectionLabel(runtimeSelection)),
            12f,
            false,
        ).apply {
            setTextColor(Color.rgb(170, 204, 235))
            setPadding(0, dp(2), 0, 0)
        })
        box.addView(smallButton(getString(R.string.runtime_selection_change)) {
            showRuntimeSelectionDialog(project)
        }.apply {
            isEnabled = typedState !in ACTIVE_RUNTIME_STATES &&
                !pending.values.any { item ->
                    item.documentId == stateKey ||
                        (item.documentId == null && item.folderName == project.folderName)
                } &&
                stateKey !in embeddedStartInFlight &&
                !projectActivities.hasActive(stateKey)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(4) }
        })
        box.addView(text("▶ ${summary.run}", 12f, false).apply {
            setTextColor(Color.rgb(150, 157, 169))
            typeface = Typeface.MONOSPACE
        })

        if (selectedProjectDocumentId != null) {
            box.addView(section(getString(R.string.runtime_workspace_environment)))
        }
        val environmentLabel = when {
            lifecycleState == RuntimeLifecycleState.PREPARING ->
                getString(R.string.runtime_lifecycle_preparing)
            lifecycleState == RuntimeLifecycleState.CLEANING ->
                getString(R.string.runtime_lifecycle_cleaning)
            lifecycleEnvironmentReady == true -> getString(R.string.runtime_state_env_ready)
            lifecycleEnvironmentReady == false -> getString(R.string.runtime_state_env_not_ready)
            else -> getString(R.string.runtime_environment_unknown)
        }
        box.addView(text(getString(R.string.runtime_environment_label, environmentLabel), 12f, false).apply {
            setTextColor(
                if (lifecycleEnvironmentReady == true) {
                    Color.rgb(170, 224, 190)
                } else {
                    Color.rgb(150, 157, 169)
                },
            )
            setPadding(0, dp(2), 0, 0)
        })

        box.addView(text(configurationUi.summaryText(configurationSnapshot), 12f, false).apply {
            setTextColor(
                when {
                    configurationUi.statusIsWarning(configurationSnapshot) -> Color.rgb(240, 184, 120)
                    configurationUi.hasOptionalReminders(configurationSnapshot) -> Color.rgb(170, 204, 235)
                    configurationSnapshot.preflight.requiredCount > 0 -> Color.rgb(170, 224, 190)
                    else -> Color.rgb(150, 157, 169)
                },
            )
            setPadding(0, dp(2), 0, 0)
        })

        val terminalState = RuntimePresentationState.terminalStateForLabel(typedState)
        val stateLabel = when {
            snapshot.recoveryInProgress -> snapshot.lifecycleState.uiLabel(this)
            snapshot.lifecycleState == RuntimeLifecycleState.RUN_FAILED &&
                snapshot.failureReason != null -> snapshot.lifecycleState.uiLabel(this)
            terminalState != null -> terminalState.uiLabel(this, lifecycleEnvironmentReady)
            presentationState != typedState -> presentationState.uiLabel(this)
            states[stateKey] != null && snapshot.lifecycleState in setOf(
                RuntimeLifecycleState.DETECTING,
                RuntimeLifecycleState.STARTING,
                RuntimeLifecycleState.CHECKING,
                RuntimeLifecycleState.STOPPING,
                RuntimeLifecycleState.CLEANING,
            ) ->
                states.getValue(stateKey)
            else -> snapshot.lifecycleState.uiLabel(this)
        }
        box.addView(text(getString(R.string.runtime_center_state, stateLabel), 12f, false).apply {
            setTextColor(
                if (snapshot.lifecycleState == RuntimeLifecycleState.RUN_FAILED) {
                    Color.rgb(240, 184, 120)
                } else {
                    Color.rgb(170, 224, 190)
                },
            )
            setPadding(0, dp(2), 0, 0)
        })
        if (activityIndicatorVisible) {
            box.addView(
                ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    isIndeterminate = true
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(6),
                    ).apply {
                        topMargin = dp(6)
                        bottomMargin = dp(4)
                    }
                },
            )
        }
        val statusGuidance = ProjectStatusGuidancePolicy.resolve(
            state = typedState,
            webEndpointVerified = reachableWebUrl != null,
            richResultAvailable = richResult != null,
        )
        if (
            ProjectStatusPresentationPolicy.summarySource(statusGuidance) ==
                ProjectStatusPresentationPolicy.SummarySource.GUIDANCE
        ) {
            statusGuidance?.let { guidance ->
                box.addView(
                    text(
                        getString(
                            R.string.runtime_policy_summary_label,
                            guidance.localizedText(),
                        ),
                        12f,
                        false,
                    ).apply {
                        setTextColor(Color.rgb(170, 204, 235))
                        setPadding(0, dp(2), 0, dp(2))
                    },
                )
            }
        }
        failureReasons[stateKey]?.let { reason ->
            box.addView(text(getString(R.string.runtime_failure_reason, reason), 12f, false).apply {
                setTextColor(Color.rgb(240, 184, 120))
                setPadding(0, dp(2), 0, dp(2))
            })
        }
        resultWeb?.let { result ->
            box.addView(
                text(
                    getString(R.string.runtime_result_web_ready, result.summary),
                    13f,
                    true,
                ).apply {
                    setTextColor(Color.rgb(170, 224, 190))
                    setPadding(0, dp(2), 0, dp(2))
                },
            )
        }
        richResult?.let { document ->
            box.addView(text(getString(R.string.runtime_rich_result_count, document.items.size), 13f, true).apply {
                setTextColor(Color.rgb(170, 224, 190))
                setPadding(0, dp(2), 0, dp(2))
            })
        }
        box.addView(text(webProfileLabel(webUiStatus), 12f, false).apply {
            setTextColor(
                if (webProfile.enabled || !webSnapshot.candidateUrl.isNullOrBlank()) {
                    Color.rgb(170, 204, 235)
                } else {
                    Color.rgb(150, 157, 169)
                },
            )
            setPadding(0, dp(2), 0, dp(5))
        })
        if (
            ProjectStatusPresentationPolicy.summarySource(statusGuidance) ==
                ProjectStatusPresentationPolicy.SummarySource.ACTION_POLICY
        ) {
            val policyExplanation = buildString {
                append(getString(R.string.runtime_policy_summary_label, policy.summary.localizedText()))
                policy.disableReason?.let { reason ->
                    append('\n')
                    append(getString(R.string.runtime_policy_disabled_reason, reason.localizedText()))
                }
            }
            box.addView(text(policyExplanation, 12f, false).apply {
                setTextColor(
                    if (policy.disableReason == null) {
                        Color.rgb(170, 224, 190)
                    } else {
                        Color.rgb(240, 184, 120)
                    },
                )
                setPadding(0, 0, 0, dp(5))
            })
        }

        // The single-project workspace presents the policy-selected next action first. The
        // secondary controls below remain available for observation and recovery, but the user
        // never needs to infer whether Prepare, Configure, Start, or Stop is appropriate.
        if (selectedProjectDocumentId != null) {
            when (policy.primaryAction) {
                ProjectActionPolicy.Action.PREPARE -> {
                    box.addView(button(getString(R.string.runtime_button_prepare)) {
                        confirmPrepare(project)
                    })
                }
                ProjectActionPolicy.Action.START -> {
                    box.addView(button(getString(R.string.runtime_button_run)) {
                        confirmRun(project)
                    })
                }
                ProjectActionPolicy.Action.STOP -> {
                    box.addView(button(getString(R.string.runtime_button_stop)) {
                        stopProjectActivities(project)
                    })
                }
                ProjectActionPolicy.Action.CONFIGURE -> {
                    box.addView(button(getString(R.string.runtime_configuration_button)) {
                        configurationUi.showConfiguration(
                            projectName = summary.name,
                            projectDocumentId = summary.documentId,
                            folderName = project.folderName,
                            onCompleted = { retryProjectAfterConfiguration(project, webProfile) },
                        )
                    })
                }
                ProjectActionPolicy.Action.STATUS -> {
                    box.addView(button(getString(R.string.runtime_button_status)) {
                        if (runtimeSelection == ProjectRuntimeSelection.EMBEDDED_R) {
                            refreshEmbeddedProject(project, EmbeddedPythonObservationPolicy.ManualAction.STATUS)
                        } else {
                            dispatch(
                                project,
                                ProjectRuntimeController.Action.STATUS,
                                webLogDiscoveryAllowed = webProfile.enabled,
                            )
                        }
                    })
                }
                null,
                ProjectActionPolicy.Action.EDIT,
                ProjectActionPolicy.Action.OPEN_SOURCE,
                ProjectActionPolicy.Action.LOGS,
                ProjectActionPolicy.Action.CLEAN,
                ProjectActionPolicy.Action.OPEN_BROWSER,
                -> Unit
            }
        }

        if (selectedProjectDocumentId != null) {
            box.addView(section(getString(R.string.runtime_workspace_controls)))
        }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(smallButton(getString(R.string.runtime_button_edit)) { openEditor(project) }, weight())
        val prepareButton = smallButton(getString(R.string.runtime_button_prepare)) { confirmPrepare(project) }
            .apply {
                isEnabled = policy.isEnabled(ProjectActionPolicy.Action.PREPARE)
            }
        row1.addView(prepareButton, weight().apply { marginStart = dp(5) })
        val runButton = smallButton(getString(R.string.runtime_button_run)) { confirmRun(project) }
            .apply { isEnabled = policy.isEnabled(ProjectActionPolicy.Action.START) }
        row1.addView(runButton, weight().apply { marginStart = dp(5) })
        box.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, 0)
        }
        val stopButton = smallButton(getString(R.string.runtime_button_stop)) {
                stopProjectActivities(project)
            }.apply {
                isEnabled = projectActivities.hasActive(stateKey) ||
                    embeddedActive ||
                    policy.isEnabled(ProjectActionPolicy.Action.STOP)
            }
        row2.addView(stopButton, weight())
        val statusButton = smallButton(getString(R.string.runtime_button_status)) {
                if (runtimeSelection == ProjectRuntimeSelection.EMBEDDED_R) {
                    refreshEmbeddedProject(project, EmbeddedPythonObservationPolicy.ManualAction.STATUS)
                } else {
                    dispatch(
                        project,
                        ProjectRuntimeController.Action.STATUS,
                        webLogDiscoveryAllowed = webProfile.enabled,
                    )
                }
            }.apply { isEnabled = embeddedObservationOwned || policy.isEnabled(ProjectActionPolicy.Action.STATUS) }
        row2.addView(statusButton, weight().apply { marginStart = dp(5) })
        val logsButton = smallButton(getString(R.string.runtime_button_refresh_logs)) {
                if (runtimeSelection == ProjectRuntimeSelection.EMBEDDED_R) {
                    refreshEmbeddedProject(project, EmbeddedPythonObservationPolicy.ManualAction.LOGS)
                } else {
                    dispatch(
                        project,
                        ProjectRuntimeController.Action.LOGS,
                        webLogDiscoveryAllowed = webProfile.enabled,
                    )
                }
            }.apply { isEnabled = embeddedObservationOwned || policy.isEnabled(ProjectActionPolicy.Action.LOGS) }
        row2.addView(logsButton, weight().apply { marginStart = dp(5) })
        box.addView(row2)

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, 0)
        }
        val openButton = smallButton(getString(R.string.runtime_button_open)) {
            openPresentation(
                projectKey = summary.documentId,
                projectName = summary.name,
                folderName = project.folderName,
                runtimeState = typedState,
                webUrl = verifiedWebUrl,
                webFramework = reachableWebFramework,
                richResult = richResult,
                resultWeb = resultWeb,
            )
        }.apply {
            isEnabled = when (presentationTarget) {
                PresentationTarget.WEB -> policy.isEnabled(ProjectActionPolicy.Action.OPEN_BROWSER)
                PresentationTarget.RESULT_WEB -> true
                PresentationTarget.RICH_RESULT -> true
                PresentationTarget.NONE -> false
            }
            alpha = if (isEnabled) 1f else 0.55f
        }
        row3.addView(openButton, weight())
        row3.addView(
            smallButton(getString(R.string.runtime_configuration_button)) {
                configurationUi.showConfiguration(
                    projectName = summary.name,
                    projectDocumentId = summary.documentId,
                    folderName = project.folderName,
                    onCompleted = { retryProjectAfterConfiguration(project, webProfile) },
                )
            }.apply {
                isEnabled = policy.isEnabled(ProjectActionPolicy.Action.CONFIGURE)
            },
            weight().apply { marginStart = dp(5) },
        )
        box.addView(row3)

        box.addView(smallButton(getString(R.string.runtime_button_clean)) { confirmClean(project) }.apply {
            isEnabled = policy.isEnabled(ProjectActionPolicy.Action.CLEAN)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(5) }
        })

        if (!project.sourceUrl.isNullOrBlank()) {
            box.addView(smallButton(getString(R.string.runtime_button_open_source)) { openSource(project.sourceUrl) }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(5) }
            })
        }
        if (selectedProjectDocumentId != null) {
            box.addView(section(getString(R.string.runtime_workspace_output)))
        }
        projectOutputs.attach(project.folderName, box)
        return box
    }

    private fun selectedRuntimeSelection(project: V04ProjectGateway.RuntimeProject): ProjectRuntimeSelection =
        projectRuntimeSelectionStore.read(project.summary.documentId)

    private fun selectedRuntimeControlRequest(project: V04ProjectGateway.RuntimeProject): RuntimeControlRequest =
        selectedRuntimeSelection(project).controlRequest

    private fun runtimeSelectionLabel(selection: ProjectRuntimeSelection): String = getString(
        when (selection) {
            ProjectRuntimeSelection.EMBEDDED_R -> R.string.runtime_selection_embedded_r
            ProjectRuntimeSelection.TERMUX -> R.string.runtime_selection_termux
        },
    )

    private fun reconcileWebDiscoveryScope(projectKey: String, webCapabilityEnabled: Boolean) {
        if (
            webStateStore.clearIfOutOfScope(projectKey, webCapabilityEnabled) &&
            ::webAvailability.isInitialized
        ) {
            webAvailability.invalidate(projectKey)
        }
    }

    private fun ProjectStatusGuidancePolicy.Message.localizedText(): String = getString(
        when (this) {
            ProjectStatusGuidancePolicy.Message.RUNNING_OBSERVING ->
                R.string.runtime_guidance_running_observing
            ProjectStatusGuidancePolicy.Message.RUNNING_WEB_AVAILABLE ->
                R.string.runtime_guidance_running_web_available
            ProjectStatusGuidancePolicy.Message.RESULT_READY ->
                R.string.runtime_guidance_result_ready
            ProjectStatusGuidancePolicy.Message.COMPLETED_OUTPUT_AVAILABLE ->
                R.string.runtime_guidance_completed_output
            ProjectStatusGuidancePolicy.Message.STOPPED_INCOMPLETE ->
                R.string.runtime_guidance_stopped_incomplete
        },
    )

    private fun webProfileLabel(uiStatus: RuntimeWebUiStatus): String =
        getString(R.string.runtime_web_label, uiStatus.uiLabel(this))

    private fun ProjectActionPolicy.MessageKey.localizedText(): String = getString(
        when (this) {
            ProjectActionPolicy.MessageKey.PENDING_OPERATION -> R.string.runtime_policy_pending_operation
            ProjectActionPolicy.MessageKey.RUNTIME_HOST_UNAVAILABLE ->
                R.string.runtime_policy_runtime_host_unavailable
            ProjectActionPolicy.MessageKey.RUNTIME_SELECTION_REQUIRED ->
                R.string.runtime_policy_runtime_selection_required
            ProjectActionPolicy.MessageKey.RUNTIME_ACTIVE -> R.string.runtime_policy_runtime_active
            ProjectActionPolicy.MessageKey.ENVIRONMENT_PREPARING ->
                R.string.runtime_policy_environment_preparing
            ProjectActionPolicy.MessageKey.WEB_ENDPOINT_PENDING -> R.string.runtime_policy_web_endpoint_pending
            ProjectActionPolicy.MessageKey.RUNTIME_RECOVERING -> R.string.runtime_policy_runtime_recovering
            ProjectActionPolicy.MessageKey.CONFIGURATION_REQUIRED ->
                R.string.runtime_policy_configuration_required
            ProjectActionPolicy.MessageKey.ENVIRONMENT_PREPARE_REQUIRED ->
                R.string.runtime_policy_environment_prepare_required
            ProjectActionPolicy.MessageKey.ENVIRONMENT_STATUS_REQUIRED ->
                R.string.runtime_policy_environment_status_required
            ProjectActionPolicy.MessageKey.RUNTIME_STATUS_REQUIRED ->
                R.string.runtime_policy_runtime_status_required
            ProjectActionPolicy.MessageKey.READY_TO_RUN -> R.string.runtime_policy_ready_to_run
        },
    )

    private fun ProjectActionPolicy.DisableReason.localizedText(): String = getString(
        when (this) {
            ProjectActionPolicy.DisableReason.PENDING_OPERATION ->
                R.string.runtime_policy_reason_pending_operation
            ProjectActionPolicy.DisableReason.RUNTIME_RECOVERY ->
                R.string.runtime_policy_reason_runtime_recovering
            ProjectActionPolicy.DisableReason.RUNTIME_HOST_UNAVAILABLE ->
                R.string.runtime_policy_reason_runtime_host_unavailable
            ProjectActionPolicy.DisableReason.RUNTIME_SELECTION_REQUIRED ->
                R.string.runtime_policy_reason_runtime_selection_required
            ProjectActionPolicy.DisableReason.PROCESS_ACTIVE ->
                R.string.runtime_policy_reason_process_active
            ProjectActionPolicy.DisableReason.PROCESS_NOT_ACTIVE ->
                R.string.runtime_policy_reason_process_not_active
            ProjectActionPolicy.DisableReason.UNKNOWN_RUNTIME_STATE ->
                R.string.runtime_policy_reason_unknown_runtime_state
            ProjectActionPolicy.DisableReason.ENVIRONMENT_UNKNOWN ->
                R.string.runtime_policy_reason_environment_unknown
            ProjectActionPolicy.DisableReason.ENVIRONMENT_NOT_READY ->
                R.string.runtime_policy_reason_environment_not_ready
            ProjectActionPolicy.DisableReason.ENVIRONMENT_ALREADY_READY ->
                R.string.runtime_policy_reason_environment_already_ready
            ProjectActionPolicy.DisableReason.REQUIRED_CONFIGURATION_MISSING ->
                R.string.runtime_policy_reason_required_configuration_missing
            ProjectActionPolicy.DisableReason.WEB_NOT_AVAILABLE ->
                R.string.runtime_policy_reason_web_not_available
            ProjectActionPolicy.DisableReason.SOURCE_NOT_AVAILABLE ->
                R.string.runtime_policy_reason_source_not_available
        },
    )

    private fun restoreStoredState(stateKey: String) {
        if (!::lifecycleStore.isInitialized) return
        val cached = lifecycleStore.read(stateKey)
        ProjectRuntimeSelection.entries.forEach { selection ->
            val key = environmentStateKey(stateKey, selection)
            if (!environmentStates.containsKey(key)) {
                cached.environmentReadyFor(selection)?.let { environmentStates[key] = it }
            }
        }
        if (!typedStates.containsKey(stateKey) && cached.runtimeState != RuntimeState.UNKNOWN) {
            typedStates[stateKey] = cached.runtimeState
        }
        if (!failureReasons.containsKey(stateKey)) {
            cached.failureReason?.let { failureReasons[stateKey] = it }
        }
        // Reading cached state must not mutate foreground-recovery control state.
        // Recovery is entered explicitly from recoverPersistedRuntimeStates() once per newly
        // created Activity instance, never merely because a card refresh happens while stopped.
    }

    private fun recoverPersistedRuntimeStates() {
        if (!activityStarted || !::lifecycleStore.isInitialized) return
        val projects = runCatching { gateway.projects() }.getOrElse { return }
        if (!runtime.runtimeSupported()) {
            projects.forEach { project ->
                val key = project.summary.documentId
                if (lifecycleStore.read(key).runtimeState in ACTIVE_RUNTIME_STATES) {
                    recoveryProjects.remove(key)
                    typedStates[key] = RuntimeState.UNKNOWN
                }
            }
            refresh()
            return
        }
        projects.forEach { project ->
            val key = project.summary.documentId
            restoreStoredState(key)
            val cached = lifecycleStore.read(key)
            if (projectRuntimeSelectionStore.read(key) != ProjectRuntimeSelection.TERMUX) {
                // Internal owns its own environment/session state. Reconcile it directly and never
                // enter the External/Termux RECOVERING state machine.
                recoveryProjects.remove(key)
                runCatching { runtime.embeddedPythonEnvironmentReady(project) }
                    .getOrNull()
                    ?.let { ready ->
                        setEnvironmentReady(key, ProjectRuntimeSelection.EMBEDDED_R, ready)
                    }
                refreshEmbeddedProject(project)
                persistRuntimeState(key, ProjectRuntimeSelection.EMBEDDED_R)
                return@forEach
            }
            if (cached.runtimeState in ACTIVE_RUNTIME_STATES) {
                recoveryProjects += key
                if (externalObservations[key] == null) {
                    beginExternalObservation(
                        project = project,
                        webLogDiscoveryAllowed = runCatching {
                            webInspector.inspect(key).enabled
                        }.getOrDefault(false),
                    )
                }
                if (pending.values.none { item ->
                        item.documentId == key ||
                            (item.documentId == null && item.folderName == project.folderName)
                    }) {
                    dispatch(
                        project = project,
                        action = ProjectRuntimeController.Action.STATUS,
                        silentRecovery = true,
                        webLogDiscoveryAllowed = runCatching {
                            webInspector.inspect(key).enabled
                        }.getOrDefault(false),
                    )
                }
            }
        }
        refresh()
    }

    private fun persistRuntimeState(
        stateKey: String,
        selection: ProjectRuntimeSelection = projectRuntimeSelectionStore.read(stateKey),
        persistedRuntimeState: RuntimeState = typedStates[stateKey] ?: RuntimeState.UNKNOWN,
    ) {
        if (!::lifecycleStore.isInitialized) return
        lifecycleStore.write(
            projectKey = stateKey,
            environmentReady = environmentReady(stateKey, selection),
            runtimeState = persistedRuntimeState,
            failureReason = failureReasons[stateKey],
            runtimeSelection = selection,
        )
    }

    private fun canDispatch(
        project: V04ProjectGateway.RuntimeProject,
        action: ProjectRuntimeController.Action,
        controlPath: RuntimeControlPath = RuntimeControlPath.EXTERNAL_PROVIDER,
    ): Boolean {
        val stateKey = project.summary.documentId
        val trackedOperation = currentOperation(stateKey, includeHidden = true)
        val projectPending = pending.values.any { item ->
            item.documentId == stateKey ||
                (item.documentId == null && item.folderName == project.folderName)
        }
        if (projectPending && action != ProjectRuntimeController.Action.STOP) {
            return false
        }
        if (
            recoveryProjects.contains(stateKey) &&
            action != ProjectRuntimeController.Action.STATUS &&
            action != ProjectRuntimeController.Action.STOP
        ) {
            return false
        }
        if (trackedOperation != null && action != ProjectRuntimeController.Action.STOP) {
            // Recovery STATUS is the one deliberate exception: it reconciles an operation record
            // left by Activity/process recreation and does not begin a second user operation.
            if (!(action == ProjectRuntimeController.Action.STATUS && recoveryProjects.contains(stateKey))) {
                return false
            }
        }
        if (
            trackedOperation?.action == RuntimeOperationAction.STOP &&
            action == ProjectRuntimeController.Action.STOP
        ) {
            return false
        }
        val currentState = typedStates[stateKey] ?: RuntimeState.UNKNOWN
        if (action == ProjectRuntimeController.Action.START) {
            if (currentState in ACTIVE_RUNTIME_STATES) return false
            val selection = when (controlPath) {
                RuntimeControlPath.EMBEDDED_R -> ProjectRuntimeSelection.EMBEDDED_R
                RuntimeControlPath.EXTERNAL_PROVIDER -> ProjectRuntimeSelection.TERMUX
                RuntimeControlPath.REJECTED -> return false
            }
            if (environmentReady(stateKey, selection) != true) return false
            val configuration = configurationUi.snapshot(stateKey, project.folderName)
            if (configuration.preflight.missingRequired.isNotEmpty()) {
                refresh()
                return false
            }
        }
        if (action == ProjectRuntimeController.Action.PREPARE &&
            currentState in ACTIVE_RUNTIME_STATES
        ) {
            return false
        }
        if (
            action == ProjectRuntimeController.Action.STOP &&
            currentState !in ACTIVE_RUNTIME_STATES &&
            !projectPending &&
            !projectActivities.hasActive(stateKey) &&
            !externalObservations.containsKey(stateKey)
        ) {
            return false
        }
        return true
    }

    private fun ProjectRuntimeController.Action.toRuntimeOperationAction(): RuntimeOperationAction? = when (this) {
        ProjectRuntimeController.Action.PREPARE -> RuntimeOperationAction.PREPARE
        ProjectRuntimeController.Action.START -> RuntimeOperationAction.START
        ProjectRuntimeController.Action.STATUS -> RuntimeOperationAction.STATUS
        ProjectRuntimeController.Action.LOGS -> RuntimeOperationAction.LOGS
        ProjectRuntimeController.Action.STOP -> RuntimeOperationAction.STOP
        ProjectRuntimeController.Action.CLEAN -> RuntimeOperationAction.CLEAN
        ProjectRuntimeController.Action.CLONE_GITHUB -> null
    }

    private fun RuntimeOperationAction.toControllerAction(): ProjectRuntimeController.Action = when (this) {
        RuntimeOperationAction.PREPARE -> ProjectRuntimeController.Action.PREPARE
        RuntimeOperationAction.START -> ProjectRuntimeController.Action.START
        RuntimeOperationAction.STATUS -> ProjectRuntimeController.Action.STATUS
        RuntimeOperationAction.LOGS -> ProjectRuntimeController.Action.LOGS
        RuntimeOperationAction.STOP -> ProjectRuntimeController.Action.STOP
        RuntimeOperationAction.CLEAN -> ProjectRuntimeController.Action.CLEAN
    }

    private fun currentOperation(
        projectId: String,
        includeHidden: Boolean = false,
    ): RuntimeOperationRecord? {
        if (!::operationCoordinator.isInitialized) return null
        return operationCoordinator.current(projectId, includeHidden)
    }

    private fun beginOperation(
        project: V04ProjectGateway.RuntimeProject,
        action: RuntimeOperationAction,
        provider: RuntimeOperationProvider,
        executionId: Int? = null,
        userVisible: Boolean = true,
    ): RuntimeOperationRecord? {
        val record = operationCoordinator.begin(
            projectId = project.summary.documentId,
            provider = provider,
            action = action,
            executionId = executionId,
            userVisible = userVisible,
            runtimeSelection = when (provider) {
                RuntimeOperationProvider.INTERNAL -> ProjectRuntimeSelection.EMBEDDED_R
                RuntimeOperationProvider.EXTERNAL -> ProjectRuntimeSelection.TERMUX
            },
        ) ?: return null
        scheduleOperationDeadline(project, record)
        return record
    }

    private fun finishOperation(
        projectId: String,
        generation: Long?,
        phase: RuntimeOperationPhase,
        executionId: Int? = null,
    ) {
        if (!::operationCoordinator.isInitialized) return
        operationCoordinator.finish(
            projectId = projectId,
            generation = generation,
            phase = phase,
            executionId = executionId,
        )
        operationDeadlineRunnables.remove(projectId)?.let(refreshHandler::removeCallbacks)
    }

    /**
     * Reconciliation is intentionally separate from operation pending. A recreated Activity
     * never treats an old operation record as RECOVERING forever: External records get one real
     * STATUS probe, while Internal state is read from its app-owned snapshot.
     */
    private fun reconcilePersistedOperations() {
        if (!::operationCoordinator.isInitialized || !::gateway.isInitialized) return
        val projects = runCatching { gateway.projects() }.getOrElse { return }
        projects.forEach { project ->
            val key = project.summary.documentId
            val record = operationCoordinator.persisted(key) ?: return@forEach
            if (record.terminal) {
                operationCoordinator.clearPersisted(key)
                return@forEach
            }
            if (record.deadlineAtEpochMs?.let { it <= System.currentTimeMillis() } == true) {
                expireOperation(project, record)
                return@forEach
            }
            when (record.provider) {
                RuntimeOperationProvider.EXTERNAL -> {
                    // Keep the shared operation and its watchdog across Activity recreation.
                    // The coordinator owns reconciliation; this Activity must not erase an
                    // in-flight PREPARE/START/STOP merely because the surface was recreated.
                    operationCoordinator.current(key, includeHidden = true)
                }
                RuntimeOperationProvider.INTERNAL -> {
                    operationCoordinator.clearPersisted(key)
                    refreshEmbeddedProject(project)
                }
            }
        }
    }

    private fun cancelOperation(projectId: String) {
        operationCoordinator.cancel(projectId)
        operationDeadlineRunnables.remove(projectId)?.let(refreshHandler::removeCallbacks)
    }

    private fun scheduleOperationDeadline(
        project: V04ProjectGateway.RuntimeProject,
        record: RuntimeOperationRecord,
    ) {
        // External deadlines are owned by ProjectOperationCoordinator. Keeping a second Activity
        // watchdog would create two competing timeout/state writers.
        if (record.provider == RuntimeOperationProvider.EXTERNAL) return
        operationDeadlineRunnables[record.projectId]?.let(refreshHandler::removeCallbacks)
        val deadline = record.deadlineAtEpochMs ?: return
        lateinit var runnable: Runnable
        runnable = Runnable {
            val current = currentOperation(record.projectId, includeHidden = true)
            if (current?.generation != record.generation) return@Runnable
            val remaining = deadline - System.currentTimeMillis()
            if (remaining > 0L) {
                refreshHandler.postDelayed(runnable, remaining)
            } else {
                expireOperation(project, record)
            }
        }
        operationDeadlineRunnables[record.projectId] = runnable
        refreshHandler.postDelayed(runnable, (deadline - System.currentTimeMillis()).coerceAtLeast(1L))
    }

    private fun expireOperation(
        project: V04ProjectGateway.RuntimeProject,
        record: RuntimeOperationRecord,
    ) {
        // External completion and timeout belong to the shared ProjectOperationCoordinator.
        // Only Internal records reach this legacy Activity deadline path.
        if (record.provider == RuntimeOperationProvider.EXTERNAL) return
        val current = currentOperation(record.projectId, includeHidden = true)
        if (current?.generation != record.generation) return
        finishOperation(record.projectId, record.generation, RuntimeOperationPhase.TIMED_OUT)
        pending.entries
            .filter { it.value.documentId == record.projectId || it.value.folderName == project.folderName }
            .map { it.key }
            .forEach { executionId ->
                cancelledExternalExecutions += executionId
                pending.remove(executionId)
                externalActivityTokens.remove(executionId)?.let(projectActivities::finish)
            }
        projectActivities.cancelProject(record.projectId)
        if (::prepareLiveProgress.isInitialized) prepareLiveProgress.finish(project.folderName)
        recoveryProjects.remove(record.projectId)
        failureReasons[record.projectId] = "RUNTIME_OPERATION_TIMED_OUT:${record.action.name}"
        if (record.action == RuntimeOperationAction.PREPARE ||
            record.action == RuntimeOperationAction.CLEAN
        ) {
            typedStates[record.projectId] = RuntimeState.ENVIRONMENT_ERROR
        } else if (record.action == RuntimeOperationAction.START ||
            record.action == RuntimeOperationAction.STOP
        ) {
            typedStates[record.projectId] = RuntimeState.EXITED_ERROR
        }
        states[record.projectId] = getString(R.string.runtime_operation_timed_out)
        refresh()
        if (!record.userVisible && externalObservations.containsKey(record.projectId)) {
            scheduleExternalObservation(project, delayMs = EXTERNAL_OBSERVATION_INTERVAL_MS)
        }
    }

    private fun internalOperationPhase(result: Result<*>): RuntimeOperationPhase {
        if (result.isSuccess) return RuntimeOperationPhase.SUCCESS
        val error = result.exceptionOrNull()
        return if (
            error is CancellationException ||
            error is InterruptedException ||
            error?.message?.contains("CANCEL", ignoreCase = true) == true
        ) {
            RuntimeOperationPhase.CANCELLED
        } else if (error is com.siftalpha.studio.runtime.InterruptibleProjectTreeDelete.TimedOut) {
            RuntimeOperationPhase.TIMED_OUT
        } else {
            RuntimeOperationPhase.FAILED
        }
    }

    private fun externalOperationPhase(result: RuntimeResult): RuntimeOperationPhase = when {
        "SIFTALPHA_OPERATION_RESULT=TIMED_OUT" in result.stdout ||
            "SIFTALPHA_ERROR=OPERATION_TIMEOUT" in result.stdout -> RuntimeOperationPhase.TIMED_OUT
        result.exitCode == 0 && result.internalErrorMessage.isBlank() -> RuntimeOperationPhase.SUCCESS
        else -> RuntimeOperationPhase.FAILED
    }

    private fun ProjectRuntimeController.Action.toUiOperation(): ProjectUiSnapshot.Operation? = when (this) {
        ProjectRuntimeController.Action.PREPARE -> ProjectUiSnapshot.Operation.PREPARE
        ProjectRuntimeController.Action.START -> ProjectUiSnapshot.Operation.START
        ProjectRuntimeController.Action.STOP -> ProjectUiSnapshot.Operation.STOP
        ProjectRuntimeController.Action.STATUS -> ProjectUiSnapshot.Operation.STATUS
        ProjectRuntimeController.Action.LOGS -> ProjectUiSnapshot.Operation.LOGS
        ProjectRuntimeController.Action.CLEAN -> ProjectUiSnapshot.Operation.CLEAN
        ProjectRuntimeController.Action.CLONE_GITHUB -> null
    }

    private fun ProjectRuntimeController.Action.toLifecycleOperation(): RuntimeLifecycleOperation = when (this) {
        ProjectRuntimeController.Action.PREPARE -> RuntimeLifecycleOperation.PREPARE
        ProjectRuntimeController.Action.START -> RuntimeLifecycleOperation.START
        ProjectRuntimeController.Action.STOP -> RuntimeLifecycleOperation.STOP
        ProjectRuntimeController.Action.STATUS -> RuntimeLifecycleOperation.STATUS
        ProjectRuntimeController.Action.LOGS -> RuntimeLifecycleOperation.LOGS
        ProjectRuntimeController.Action.CLEAN -> RuntimeLifecycleOperation.CLEAN
        ProjectRuntimeController.Action.CLONE_GITHUB -> RuntimeLifecycleOperation.NONE
    }

    private fun openEditor(project: V04ProjectGateway.RuntimeProject) {
        startActivity(
            Intent(this, ProjectEditorActivity::class.java).apply {
                putExtra(ProjectEditorActivity.EXTRA_PROJECT_NAME, project.summary.name)
                putExtra(ProjectEditorActivity.EXTRA_PROJECT_DOCUMENT_ID, project.summary.documentId)
            },
        )
    }

    private fun showSecretsDialog(project: V04ProjectGateway.RuntimeProject) {
        val policy = runCatching { secretPolicyInspector.inspect(project.summary.documentId) }.getOrNull()
        if (policy?.binanceApi == ProjectSecretPolicyInspector.BinanceApiPolicy.NOT_REQUIRED) {
            toast(getString(R.string.runtime_api_not_required))
            return
        }
        val configured = runCatching { secretStore.hasBinanceSecrets(project.folderName) }.getOrDefault(false)
        val apiKey = EditText(this).apply {
            hint = getString(R.string.runtime_api_key_hint)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        val apiSecret = EditText(this).apply {
            hint = getString(R.string.runtime_api_secret_hint)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), 0)
            addView(apiKey)
            addView(apiSecret)
        }
        val message = buildString {
            append(getString(R.string.runtime_api_dialog_message))
            if (configured) append(getString(R.string.runtime_api_dialog_configured))
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.runtime_api_dialog_title, project.summary.name))
            .setMessage(message)
            .setView(layout)
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.runtime_api_save), null)
        if (configured) {
            builder.setNeutralButton(getString(R.string.runtime_api_clear)) { _, _ ->
                runCatching { secretStore.clearBinanceSecrets(project.folderName) }
                    .onSuccess {
                        toast(getString(R.string.runtime_api_cleared))
                        refresh()
                    }
                    .onFailure {
                        errorDialog(
                            getString(R.string.runtime_api_clear_failed),
                            it.message ?: it.javaClass.simpleName,
                        )
                    }
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val key = apiKey.text.toString().trim()
                val secret = apiSecret.text.toString().trim()
                if (key.isBlank() || secret.isBlank()) {
                    toast(getString(R.string.runtime_api_missing_both))
                    return@setOnClickListener
                }
                runCatching { secretStore.saveBinanceSecrets(project.folderName, key, secret) }
                    .onSuccess {
                        toast(getString(R.string.runtime_api_saved))
                        dialog.dismiss()
                        refresh()
                    }
                    .onFailure {
                        errorDialog(
                            getString(R.string.runtime_api_save_failed),
                            it.message ?: it.javaClass.simpleName,
                        )
                    }
            }
        }
        dialog.show()
    }

    private fun scheduleWebRecognition(project: V04ProjectGateway.RuntimeProject) {
        if (!::webInspector.isInitialized) return
        val projectKey = project.summary.documentId
        if (!webRecognitionInFlight.add(projectKey)) return
        webRecognitionExecutor.submit {
            val profile = runCatching {
                webInspector.inspect(projectKey, forceRefresh = true)
            }.getOrNull()
            runOnUiThread {
                webRecognitionInFlight.remove(projectKey)
                if (profile != null) {
                    webProfileCache[projectKey] = profile
                }
                if (!isFinishing && !isDestroyed && ::projectList.isInitialized) {
                    refresh()
                }
            }
        }
    }

    private fun confirmPrepare(project: V04ProjectGateway.RuntimeProject) {
        val controlRequest = selectedRuntimeControlRequest(project)
        if (controlRequest == RuntimeControlRequest.EXTERNAL_PROVIDER && !ensureRuntime()) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.runtime_prepare_title, project.summary.name))
            .setMessage(getString(R.string.runtime_prepare_message))
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.runtime_prepare_start)) { _, _ ->
                backgroundReliabilityGuidance.maybeProceed {
                    dispatch(
                        project = project,
                        action = ProjectRuntimeController.Action.PREPARE,
                        controlRequest = controlRequest,
                    )
                }
            }
            .show()
    }

    private fun confirmClean(project: V04ProjectGateway.RuntimeProject) {
        val selection = selectedRuntimeSelection(project)
        if (selection == ProjectRuntimeSelection.TERMUX && !ensureRuntime()) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.runtime_clean_title, project.summary.name))
            .setMessage(getString(R.string.runtime_clean_message))
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.runtime_clean_confirm)) { _, _ ->
                if (selection == ProjectRuntimeSelection.EMBEDDED_R) {
                    cleanEmbeddedProject(project)
                } else {
                    dispatch(project, ProjectRuntimeController.Action.CLEAN)
                }
            }
            .show()
    }

    private fun webEndpointAuthorityFingerprint(
        profile: WebProjectInspector.Profile,
    ): String = RuntimeWebLearnedEndpointPolicy.authorityFingerprint(
        enabled = profile.enabled,
        framework = profile.framework,
        source = profile.source,
        host = profile.host,
        detectedPort = profile.port,
    )

    private fun startProject(
        project: V04ProjectGateway.RuntimeProject,
        webProfile: WebProjectInspector.Profile? = null,
        controlRequest: RuntimeControlRequest? = null,
        launchInvocation: PythonLaunchInvocation? = null,
    ) {
        val profile = webProfile ?: runCatching {
            webInspector.inspect(project.summary.documentId)
        }.getOrNull()
        profile?.let { webProfileCache[project.summary.documentId] = it }
        val learnedPort = webLearnedEndpointStore.read(
            projectKey = project.summary.documentId,
            authorityFingerprint = profile?.let(::webEndpointAuthorityFingerprint).orEmpty(),
        )?.port
        val webHintPorts = RuntimeWebHintPolicy.ports(
            detectedPort = profile?.port,
            framework = profile?.framework,
            learnedPort = learnedPort,
            detectedSource = profile?.source,
        )
        backgroundReliabilityGuidance.maybeProceed {
            dispatch(
                project = project,
                action = ProjectRuntimeController.Action.START,
                browserConfiguredUrl = profile?.configuredLocalUrl(),
                browserFramework = profile?.framework,
                controlRequest = controlRequest ?: selectedRuntimeControlRequest(project),
                launchInvocation = launchInvocation,
                webLogDiscoveryAllowed = profile?.enabled == true,
                webHintPorts = webHintPorts,
            )
        }
    }

    private fun confirmRun(project: V04ProjectGateway.RuntimeProject) {
        val controlRequest = selectedRuntimeControlRequest(project)
        if (!ensureRuntime(requireExternalProvider = controlRequest == RuntimeControlRequest.EXTERNAL_PROVIDER)) {
            return
        }
        val webProfile = runCatching { webInspector.inspect(project.summary.documentId) }.getOrNull()
        val resolvedSelection =
            project.runtimeSelection as? ProjectRuntimeExecutionPlanner.Selection.Resolved
        val configurationSnapshot = configurationUi.snapshot(
            project.summary.documentId,
            project.folderName,
        )
        val requiredCli = configurationSnapshot.cliRequirements.filter { it.required }
        val webLaunchAllowed =
            webProfile == null ||
                webProfile.enabled ||
                webProfile.source != "config"
        val nativeWebResolution = if (
            webLaunchAllowed &&
            resolvedSelection?.primary == RuntimeKind.PYTHON
        ) {
            runCatching {
                runtime.resolvePythonNativeWebLaunchResolution(
                    project = project,
                    webProjectEnabled = true,
                )
            }.getOrNull()
        } else {
            null
        }
        val nativeWebProfile = when {
            nativeWebResolution != null ->
                webProfile?.takeIf { it.enabled }
                    ?: WebProjectInspector.Profile(
                        enabled = true,
                        framework = null,
                        source = "runtime-source",
                        host = null,
                        port = null,
                    )
            else -> webProfile?.takeIf { it.enabled }
        }
        val learnedNativeWebLaunch = if (
            nativeWebResolution != null &&
            ::webLearnedLaunchStore.isInitialized
        ) {
            webLearnedLaunchStore.readVerified(
                projectKey = project.summary.documentId,
                sourceFingerprint = nativeWebResolution.sourceFingerprint,
            )
        } else {
            null
        }
        val nativeWebLaunch = learnedNativeWebLaunch ?: nativeWebResolution?.candidate
        if (
            nativeWebLaunch != null &&
            nativeWebProfile != null &&
            nativeWebResolution != null
        ) {
            showPythonNativeWebRunConfirmation(
                project = project,
                webProfile = nativeWebProfile,
                controlRequest = controlRequest,
                candidate = nativeWebLaunch,
                sourceFingerprint = nativeWebResolution.sourceFingerprint,
            )
            return
        }

        val effectiveWebEnabled = nativeWebProfile != null
        val isPythonCliCandidate =
            webProfile != null &&
                resolvedSelection?.primary == RuntimeKind.PYTHON &&
                (!effectiveWebEnabled || requiredCli.isNotEmpty())

        if (isPythonCliCandidate) {
            val cliWebProfile = checkNotNull(webProfile)
            val resolution = runCatching {
                runtime.resolvePythonLaunch(
                    project = project,
                    cliRequirements = requiredCli,
                )
            }.getOrElse {
                errorDialog(
                    getString(R.string.runtime_cli_pyproject_invalid),
                    getString(R.string.runtime_cli_pyproject_invalid),
                )
                return
            }
            when (resolution) {
                is PythonCliLaunchResolver.Resolution.DeclaredRun ->
                    showGenericRunConfirmation(project, webProfile, controlRequest)
                is PythonCliLaunchResolver.Resolution.ConsoleScripts -> {
                    if (resolution.names.size == 1) {
                        showPythonArgumentsDialog(
                            project = project,
                            webProfile = cliWebProfile,
                            controlRequest = controlRequest,
                            invocationFactory = { args ->
                                PythonLaunchInvocation.consoleScript(resolution.names.single(), args)
                            },
                            entryLabel = resolution.names.single(),
                            requirements = requiredCli,
                        )
                    } else {
                        showPythonConsoleScriptSelection(
                            project = project,
                            webProfile = cliWebProfile,
                            controlRequest = controlRequest,
                            names = resolution.names,
                            requirements = requiredCli,
                        )
                    }
                }
                is PythonCliLaunchResolver.Resolution.PythonFile ->
                    showPythonArgumentsDialog(
                        project = project,
                        webProfile = cliWebProfile,
                        controlRequest = controlRequest,
                        invocationFactory = { args ->
                            PythonLaunchInvocation.pythonFile(resolution.entrypoint, args)
                        },
                        entryLabel = resolution.entrypoint,
                        requirements = requiredCli,
                    )
                PythonCliLaunchResolver.Resolution.Missing ->
                    errorDialog(
                        getString(R.string.runtime_cli_missing),
                        getString(R.string.runtime_cli_missing),
                    )
                is PythonCliLaunchResolver.Resolution.Invalid -> {
                    val message = when (resolution.reason) {
                        PythonCliLaunchResolver.InvalidReason.TOML_PARSE_FAILED ->
                            getString(R.string.runtime_cli_pyproject_invalid)
                        PythonCliLaunchResolver.InvalidReason.SCRIPT_ENTRY_UNSUPPORTED ->
                            getString(R.string.runtime_cli_unsupported)
                        PythonCliLaunchResolver.InvalidReason.SCRIPT_NAME_UNSUPPORTED ->
                            getString(R.string.runtime_cli_unsupported)
                    }
                    errorDialog(getString(R.string.runtime_cli_unsupported), message)
                }
            }
            return
        }

        showGenericRunConfirmation(project, webProfile, controlRequest)
    }

    private fun showPythonNativeWebRunConfirmation(
        project: V04ProjectGateway.RuntimeProject,
        webProfile: WebProjectInspector.Profile,
        controlRequest: RuntimeControlRequest,
        candidate: PythonNativeWebLaunchCandidate,
        sourceFingerprint: String,
    ) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.runtime_native_web_run_title, project.summary.name))
            .setMessage(
                getString(
                    R.string.runtime_native_web_run_message,
                    candidate.displayCommand(),
                ),
            )
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.runtime_button_run)) { _, _ ->
                if (::webLearnedLaunchStore.isInitialized) {
                    webLearnedLaunchStore.rememberDiscovered(
                        projectKey = project.summary.documentId,
                        candidate = candidate,
                        sourceFingerprint = sourceFingerprint,
                    )
                }
                startProject(
                    project = project,
                    webProfile = webProfile,
                    controlRequest = controlRequest,
                    launchInvocation = candidate.toInvocation(),
                )
            }
            .show()
    }
    private fun showGenericRunConfirmation(
        project: V04ProjectGateway.RuntimeProject,
        webProfile: WebProjectInspector.Profile?,
        controlRequest: RuntimeControlRequest,
    ) {
        val configurationNote = "\n\n" + configurationUi.summaryText(
            configurationUi.snapshot(project.summary.documentId, project.folderName),
        )
        val webNote = if (webProfile?.enabled == true) {
            getString(R.string.runtime_run_web_detected)
        } else {
            ""
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.runtime_run_title, project.summary.name))
            .setMessage(getString(R.string.runtime_run_message, project.summary.run, configurationNote, webNote))
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.runtime_button_run)) { _, _ ->
                startProject(project, webProfile, controlRequest)
            }
            .show()
    }

    private fun showPythonConsoleScriptSelection(
        project: V04ProjectGateway.RuntimeProject,
        webProfile: WebProjectInspector.Profile,
        controlRequest: RuntimeControlRequest,
        names: List<String>,
        requirements: List<PythonCliRequirement> = emptyList(),
    ) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.runtime_cli_select_entry))
            .setMessage(getString(R.string.runtime_cli_multiple_entries))
            .setItems(names.toTypedArray()) { _, which ->
                if (which in names.indices) {
                    val name = names[which]
                    showPythonArgumentsDialog(
                        project = project,
                        webProfile = webProfile,
                        controlRequest = controlRequest,
                        invocationFactory = { args ->
                            PythonLaunchInvocation.consoleScript(name, args)
                        },
                        entryLabel = name,
                        requirements = requirements,
                    )
                }
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showPythonArgumentsDialog(
        project: V04ProjectGateway.RuntimeProject,
        webProfile: WebProjectInspector.Profile,
        controlRequest: RuntimeControlRequest,
        invocationFactory: (List<String>) -> PythonLaunchInvocation,
        entryLabel: String,
        requirements: List<PythonCliRequirement> = emptyList(),
    ) {
        val required = requirements.filter { it.required }
        val structured = required.filter { requirement ->
            requirement.kind == PythonCliArgumentKind.POSITIONAL ||
                requirement.source != ConfigurationSource.RUNTIME_DIAGNOSTIC
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(4))
        }
        val fields = structured.map { requirement ->
            content.addView(TextView(this).apply {
                text = requirement.token + " *"
                textSize = 15f
                setPadding(0, dp(8), 0, dp(2))
            })
            val input = EditText(this).apply {
                hint = getString(R.string.runtime_cli_required_value_hint, requirement.token)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                setSingleLine(true)
            }
            content.addView(input)
            requirement to input
        }

        if (required.any { it !in structured }) {
            content.addView(TextView(this).apply {
                text = required.filter { it !in structured }.joinToString(
                    prefix = getString(R.string.runtime_configuration_cli_required_section) + "\n",
                    separator = "\n",
                ) { "• " + it.token }
                textSize = 13f
                setPadding(0, dp(8), 0, dp(4))
            })
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.runtime_cli_additional_arguments)
            textSize = 14f
            setPadding(0, dp(8), 0, dp(2))
        })
        val additionalInput = EditText(this).apply {
            hint = getString(R.string.runtime_cli_arguments_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
        }
        content.addView(additionalInput)

        val scroll = ScrollView(this).apply { addView(content) }
        lateinit var dialog: AlertDialog
        dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.runtime_cli_arguments))
            .setMessage(getString(R.string.runtime_cli_entry_label, entryLabel))
            .setView(scroll)
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.runtime_button_run), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val structuredArguments = mutableListOf<String>()
                for ((requirement, input) in fields) {
                    val value = input.text?.toString().orEmpty()
                    if (value.isBlank()) {
                        toast(getString(R.string.runtime_cli_required_blank, requirement.token))
                        input.requestFocus()
                        return@setOnClickListener
                    }
                    when (requirement.kind) {
                        PythonCliArgumentKind.POSITIONAL -> structuredArguments += value
                        PythonCliArgumentKind.OPTION -> {
                            structuredArguments += requirement.token
                            structuredArguments += value
                        }
                    }
                }

                when (val parsed = RuntimeArgumentParser.parse(additionalInput.text?.toString().orEmpty())) {
                    is RuntimeArgumentParser.Result.Success -> {
                        val allArguments = structuredArguments + parsed.arguments
                        val invocation = runCatching {
                            invocationFactory(allArguments)
                        }.getOrElse {
                            errorDialog(
                                getString(R.string.runtime_cli_unsupported),
                                it.message ?: getString(R.string.runtime_cli_unsupported),
                            )
                            return@setOnClickListener
                        }
                        dialog.dismiss()
                        startProject(project, webProfile, controlRequest, invocation)
                    }
                    is RuntimeArgumentParser.Result.Invalid ->
                        errorDialog(
                            getString(R.string.runtime_cli_arguments_invalid),
                            getString(R.string.runtime_cli_arguments_invalid),
                        )
                }
            }
        }
        dialog.show()
    }

    private fun showRuntimeSelectionDialog(project: V04ProjectGateway.RuntimeProject) {
        val stateKey = project.summary.documentId
        val busy = typedStates[stateKey] in ACTIVE_RUNTIME_STATES ||
            pending.values.any { item ->
                item.documentId == stateKey ||
                    (item.documentId == null && item.folderName == project.folderName)
            } ||
            stateKey in embeddedStartInFlight ||
            projectActivities.hasActive(stateKey)
        if (busy) {
            toast(getString(R.string.runtime_selection_active))
            return
        }

        val choices = arrayOf(
            ProjectRuntimeSelection.EMBEDDED_R,
            ProjectRuntimeSelection.TERMUX,
        )
        val labels = choices.map(::runtimeSelectionLabel).toTypedArray()
        val checked = choices.indexOf(selectedRuntimeSelection(project))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.runtime_selection_title, project.summary.name))
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                if (which in choices.indices) {
                    val selected = choices[which]
                    projectRuntimeSelectionStore.write(stateKey, selected)
                    toast(getString(R.string.runtime_selection_saved, runtimeSelectionLabel(selected)))
                    dialog.dismiss()
                    refresh()
                }
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun prepareEmbeddedProject(project: V04ProjectGateway.RuntimeProject) {
        val stateKey = project.summary.documentId
        if (stateKey in embeddedStartInFlight) return
        val operation = beginOperation(
            project = project,
            action = RuntimeOperationAction.PREPARE,
            provider = RuntimeOperationProvider.INTERNAL,
        ) ?: return
        embeddedStartInFlight += stateKey
        setEnvironmentReady(stateKey, ProjectRuntimeSelection.EMBEDDED_R, null)
        typedStates[stateKey] = RuntimeState.PREPARING
        states[stateKey] = getString(R.string.runtime_action_preparing)
        failureReasons.remove(stateKey)
        persistRuntimeState(
            stateKey,
            ProjectRuntimeSelection.EMBEDDED_R,
            persistedRuntimeState = RuntimeState.UNKNOWN,
        )
        projectOutputs.write(
            project.folderName,
            listOf(
                getString(R.string.runtime_prepare_live_title),
                "",
                "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                "SIFTALPHA_X_PROJECT_ID=" + stateKey,
                "SIFTALPHA_X_ENVIRONMENT_STAGE=PREPARING",
            ).joinToString("\n"),
            expand = true,
            forceFollowTail = true,
        )
        refresh()
        val token = projectActivities.begin(
            stateKey,
            ProjectActivityRegistry.Kind.PREPARE,
        )
        val future = embeddedStartExecutor.submit {
            val result = runCatching {
                runtime.prepareEmbeddedPythonEnvironment(project) { liveText ->
                    val safeLiveText = runCatching {
                        secretStore.redactRuntimeText(project.folderName, liveText)
                    }.getOrElse {
                        liveText.lineSequence()
                            .map { line -> line.trimEnd() }
                            .filter { line -> line.startsWith("SIFTALPHA_X_") }
                            .joinToString("\n")
                    }
                    runOnUiThread {
                        if (
                            !isFinishing &&
                            !isDestroyed &&
                            ProjectActivityRegistry.Kind.PREPARE in projectActivities.activeKinds(stateKey)
                        ) {
                            projectOutputs.write(
                                project.folderName,
                                listOf(
                                    getString(R.string.runtime_prepare_live_title),
                                    "",
                                    safeLiveText,
                                ).joinToString("\n"),
                                expand = true,
                                forceFollowTail = true,
                            )
                        }
                    }
                }
            }
            runOnUiThread {
                embeddedStartInFlight.remove(stateKey)
                finishOperation(
                    projectId = stateKey,
                    generation = operation.generation,
                    phase = internalOperationPhase(result),
                )
                if (!projectActivities.finish(token)) return@runOnUiThread
                if (isFinishing || isDestroyed) return@runOnUiThread
                val prepared = result.getOrNull()
                if (prepared != null) {
                    setEnvironmentReady(stateKey, ProjectRuntimeSelection.EMBEDDED_R, prepared.ready)
                    typedStates[stateKey] = RuntimeState.UNKNOWN
                    states[stateKey] = if (prepared.ready) {
                        getString(R.string.runtime_state_not_running)
                    } else {
                        getString(R.string.runtime_state_env_not_ready)
                    }
                    failureReasons.remove(stateKey)
                    projectOutputs.write(
                        project.folderName,
                        listOf(
                            "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                            "SIFTALPHA_X_PROJECT_ID=" + stateKey,
                            "SIFTALPHA_X_ENVIRONMENT_READY=" + prepared.ready,
                            "SIFTALPHA_X_ENVIRONMENT_OUTCOME=" + prepared.outcome.name,
                            "SIFTALPHA_X_INTERNAL_BACKEND=" + prepared.backend.name,
                        ).joinToString("\n"),
                        expand = true,
                    )
                    persistRuntimeState(stateKey, ProjectRuntimeSelection.EMBEDDED_R)
                    refresh()
                } else {
                    setEnvironmentReady(stateKey, ProjectRuntimeSelection.EMBEDDED_R, false)
                    typedStates[stateKey] = RuntimeState.ENVIRONMENT_ERROR
                    states[stateKey] = getString(R.string.runtime_prepare_failed)
                    val message = result.exceptionOrNull()?.message ?: getString(R.string.runtime_unavailable)
                    failureReasons[stateKey] = message
                    projectOutputs.write(
                        project.folderName,
                        listOf(
                            "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                            "SIFTALPHA_X_PROJECT_ID=" + stateKey,
                            "SIFTALPHA_X_ENVIRONMENT_STAGE=FAILED",
                            message,
                        ).joinToString("\n"),
                        expand = true,
                    )
                    persistRuntimeState(stateKey, ProjectRuntimeSelection.EMBEDDED_R)
                    refresh()
                    errorDialog(getString(R.string.runtime_prepare_failed), message)
                }
            }
        }
        if (!projectActivities.attachCancel(token) { future.cancel(true) }) {
            future.cancel(true)
        }
    }

    private fun cleanEmbeddedProject(project: V04ProjectGateway.RuntimeProject) {
        val stateKey = project.summary.documentId
        if (projectActivities.hasActive(stateKey)) return
        val activeSnapshot = runCatching { runtime.embeddedPythonSnapshotFor(stateKey) }.getOrNull()
        if (activeSnapshot != null && isEmbeddedActive(activeSnapshot)) {
            toast(getString(R.string.runtime_embedded_r_active))
            return
        }
        val operation = beginOperation(
            project = project,
            action = RuntimeOperationAction.CLEAN,
            provider = RuntimeOperationProvider.INTERNAL,
        ) ?: return

        states[stateKey] = getString(R.string.runtime_action_cleaning)
        failureReasons.remove(stateKey)
        projectOutputs.write(
            project.folderName,
            listOf(
                "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                "SIFTALPHA_X_PROJECT_ID=" + stateKey,
                "SIFTALPHA_X_ENVIRONMENT_STAGE=CLEANING",
            ).joinToString("\n"),
            expand = true,
        )
        refresh()

        val token = projectActivities.begin(
            stateKey,
            ProjectActivityRegistry.Kind.CLEAN,
        )
        val future = embeddedStartExecutor.submit {
            val result = runCatching {
                runtime.cleanEmbeddedPythonEnvironment(project)
            }
            runOnUiThread {
                finishOperation(
                    projectId = stateKey,
                    generation = operation.generation,
                    phase = internalOperationPhase(result),
                )
                if (!projectActivities.finish(token)) return@runOnUiThread
                if (isFinishing || isDestroyed) return@runOnUiThread
                recoveryProjects.remove(stateKey)
                embeddedStartInFlight.remove(stateKey)
                val success = result.isSuccess
                val readyAfterClean = runCatching {
                    runtime.embeddedPythonEnvironmentReady(project)
                }.getOrDefault(false)
                setEnvironmentReady(
                    stateKey,
                    ProjectRuntimeSelection.EMBEDDED_R,
                    readyAfterClean,
                )
                if (success && !readyAfterClean) {
                    typedStates[stateKey] = RuntimeState.UNKNOWN
                    states[stateKey] = getString(R.string.runtime_state_env_not_ready)
                    failureReasons.remove(stateKey)
                    richResults.remove(stateKey)
                    embeddedLastSnapshots.remove(stateKey)
                    if (::webStateStore.isInitialized) webStateStore.clear(stateKey)
                    if (::webAvailability.isInitialized) webAvailability.invalidate(stateKey)
                    configurationUi.clearRuntimeDiscovery(project.folderName)
                    projectOutputs.write(
                        project.folderName,
                        listOf(
                            "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                            "SIFTALPHA_X_PROJECT_ID=" + stateKey,
                            "SIFTALPHA_X_ENVIRONMENT_STAGE=CLEANED",
                            "SIFTALPHA_X_ENVIRONMENT_READY=false",
                        ).joinToString("\n"),
                        expand = true,
                    )
                } else {
                    typedStates[stateKey] = RuntimeState.ENVIRONMENT_ERROR
                    states[stateKey] = getString(R.string.runtime_clean_failed)
                    val message = result.exceptionOrNull()?.message
                        ?: "INTERNAL_ENVIRONMENT_STILL_READY_AFTER_CLEAN"
                    failureReasons[stateKey] = message
                    projectOutputs.write(
                        project.folderName,
                        listOf(
                            "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                            "SIFTALPHA_X_PROJECT_ID=" + stateKey,
                            "SIFTALPHA_X_ENVIRONMENT_STAGE=CLEAN_FAILED",
                            message,
                        ).joinToString("\n"),
                        expand = true,
                    )
                    errorDialog(getString(R.string.runtime_clean_failed), message)
                }
                persistRuntimeState(stateKey, ProjectRuntimeSelection.EMBEDDED_R)
                refresh()
            }
        }
        if (!projectActivities.attachCancel(token) { future.cancel(true) }) {
            future.cancel(true)
        }
    }

    private fun startEmbeddedProject(
        project: V04ProjectGateway.RuntimeProject,
        requiredConfiguration: Boolean = false,
        launchInvocation: PythonLaunchInvocation? = null,
    ) {
        val stateKey = project.summary.documentId
        val route = runCatching {
            runtime.resolveControlPath(
                project = project,
                action = ProjectRuntimeController.Action.START,
                request = RuntimeControlRequest.EMBEDDED_R,
                requiredConfiguration = requiredConfiguration,
            )
        }.getOrElse {
            errorDialog(
                getString(R.string.runtime_embedded_r_start_failed),
                it.message ?: getString(R.string.runtime_unavailable),
            )
            return
        }
        if (route.path != RuntimeControlPath.EMBEDDED_R || stateKey in embeddedStartInFlight) {
            if (route.path != RuntimeControlPath.EMBEDDED_R) {
                errorDialog(
                    getString(R.string.runtime_embedded_r_start_failed),
                    embeddedControlReasonMessage(route.reason),
                )
            }
            return
        }
        if (!runtime.embeddedPythonCanStart(project)) {
            toast(getString(R.string.runtime_embedded_r_active))
            return
        }
        val operation = beginOperation(
            project = project,
            action = RuntimeOperationAction.START,
            provider = RuntimeOperationProvider.INTERNAL,
        ) ?: return
        invalidateInternalWebDiscovery(stateKey)
        embeddedStartInFlight += stateKey
        richResults.remove(stateKey)
        resultWebResults.remove(stateKey)
        resultWebSuppressedProjects += stateKey
        typedStates[stateKey] = RuntimeState.STARTING
        states[stateKey] = getString(R.string.runtime_action_starting)
        failureReasons.remove(stateKey)
        projectOutputs.write(
            project.folderName,
            listOf(
                "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                "SIFTALPHA_X_PROJECT_ID=" + stateKey,
                "SIFTALPHA_X_STAGE=STARTING",
            ).joinToString("\n"),
            expand = true,
        )
        refresh()
        val token = projectActivities.begin(
            stateKey,
            ProjectActivityRegistry.Kind.START,
        )
        val future = embeddedStartExecutor.submit {
            val result = runCatching {
                runtime.startEmbeddedPython(
                    project = project,
                    requiredConfiguration = requiredConfiguration,
                    pythonLaunchInvocation = launchInvocation,
                )
            }
            runOnUiThread {
                embeddedStartInFlight.remove(stateKey)
                finishOperation(
                    projectId = stateKey,
                    generation = operation.generation,
                    phase = internalOperationPhase(result),
                )
                if (!projectActivities.finish(token)) return@runOnUiThread
                if (isFinishing || isDestroyed) return@runOnUiThread
                val snapshot = result.getOrNull()
                if (snapshot != null) {
                    embeddedRuntimeOwnership[stateKey] = RuntimeOwnershipPolicy.afterAcceptedStart(
                        RuntimeControlRequest.EMBEDDED_R,
                    )
                    embeddedLastSnapshots.remove(stateKey)
                    syncEmbeddedSnapshot(project, snapshot)
                    if (isEmbeddedActive(snapshot)) scheduleEmbeddedPolling(project)
                    refresh()
                } else {
                    typedStates[stateKey] = RuntimeState.ENVIRONMENT_ERROR
                    states[stateKey] = getString(R.string.runtime_start_failed)
                    val error = result.exceptionOrNull()
                    val message = embeddedStartErrorMessage(error)
                    projectOutputs.write(
                        project.folderName,
                        listOf(
                            "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                            "SIFTALPHA_X_PROJECT_ID=" + stateKey,
                            "SIFTALPHA_X_STAGE=FAILED",
                            message,
                        ).joinToString("\n"),
                        expand = true,
                    )
                    refresh()
                    errorDialog(getString(R.string.runtime_embedded_r_start_failed), message)
                }
            }
        }
        if (!projectActivities.attachCancel(token) { future.cancel(true) }) {
            future.cancel(true)
        }
    }

    private fun stopProjectActivities(project: V04ProjectGateway.RuntimeProject) {
        val stateKey = project.summary.documentId
        externalActionGate.cancel(stateKey)
        externalGateDeferredActions.remove(stateKey)
        val projectPending = pending.entries.filter { (_, item) ->
            item.documentId == stateKey ||
                (item.documentId == null && item.folderName == project.folderName)
        }
        projectPending.forEach { (executionId, _) ->
            cancelledExternalExecutions += executionId
            pending.remove(executionId)
        }

        if (::prepareLiveProgress.isInitialized) {
            prepareLiveProgress.finish(project.folderName)
        }
        invalidateExternalObservation(stateKey)
        deferredManualActions.remove(stateKey)
        recoveryProjects.remove(stateKey)
        cancelOperation(stateKey)

        val cancelledInternal = projectActivities.cancelProject(stateKey)
        projectPending.forEach { (executionId, _) ->
            externalActivityTokens.remove(executionId)
        }
        if (cancelledInternal > 0) {
            embeddedStartInFlight.remove(stateKey)
        }

        val embeddedSnapshot = embeddedLastSnapshots[stateKey]
            ?.takeIf { ownsEmbeddedObservation(stateKey, it) }
            ?: runCatching {
                runtime.embeddedPythonSnapshotFor(stateKey)
            }.getOrNull()
        val embeddedRunning = embeddedSnapshot != null && isEmbeddedActive(embeddedSnapshot)

        if (embeddedRunning) {
            requestEmbeddedStop(project)
            return
        }

        if (selectedRuntimeSelection(project) == ProjectRuntimeSelection.EMBEDDED_R) {
            val ready = runCatching {
                runtime.embeddedPythonEnvironmentReady(project)
            }.getOrDefault(false)
            setEnvironmentReady(stateKey, ProjectRuntimeSelection.EMBEDDED_R, ready)
            val terminalState = embeddedSnapshot
                ?.let(EmbeddedPythonRuntimeStateMapping::toRuntimeState)
                ?.takeIf { it !in ACTIVE_RUNTIME_STATES }
                ?: RuntimeState.STOPPED_BY_USER
            typedStates[stateKey] = terminalState
            states[stateKey] = terminalState.uiLabel(this)
            failureReasons.remove(stateKey)
            projectOutputs.write(
                project.folderName,
                listOf(
                    "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                    "SIFTALPHA_X_PROJECT_ID=" + stateKey,
                    "SIFTALPHA_X_STOP_REQUEST=" + if (cancelledInternal > 0) {
                        "LOCAL_ACTIVITY_CANCELLED"
                    } else {
                        "NO_ACTIVE_INTERNAL_ACTIVITY"
                    },
                    "SIFTALPHA_X_ENVIRONMENT_READY=" + ready,
                ).joinToString("\n"),
                expand = true,
            )
            persistRuntimeState(stateKey, ProjectRuntimeSelection.EMBEDDED_R)
            refresh()
            return
        }

        // External commands cannot be recalled through Android's RUN_COMMAND contract. Their
        // callbacks are invalidated above; the project-scoped STOP command terminates any owned
        // PREPARE or runtime process tree inside Termux/PRoot without touching other projects.
        dispatch(
            project = project,
            action = ProjectRuntimeController.Action.STOP,
            controlRequest = RuntimeControlRequest.EXTERNAL_PROVIDER,
        )
    }

    private fun requestEmbeddedStop(project: V04ProjectGateway.RuntimeProject) {
        val stateKey = project.summary.documentId
        val operation = beginOperation(
            project = project,
            action = RuntimeOperationAction.STOP,
            provider = RuntimeOperationProvider.INTERNAL,
        ) ?: return

        invalidateInternalWebDiscovery(stateKey)
        states[stateKey] = getString(R.string.runtime_action_stopping)
        projectOutputs.write(
            project.folderName,
            listOf(
                "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                "SIFTALPHA_X_PROJECT_ID=" + stateKey,
                "SIFTALPHA_X_STOP_REQUEST=DISPATCHING",
            ).joinToString("\n"),
            expand = true,
        )
        refresh()

        embeddedObservationExecutor.execute {
            val accepted = runCatching {
                runtime.requestEmbeddedPythonStop(stateKey)
            }.getOrDefault(false)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!accepted) {
                    finishOperation(
                        projectId = stateKey,
                        generation = operation.generation,
                        phase = RuntimeOperationPhase.FAILED,
                    )
                    toast(getString(R.string.runtime_stop_failed))
                    refreshEmbeddedProject(project)
                    return@runOnUiThread
                }

                projectOutputs.write(
                    project.folderName,
                    listOf(
                        "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                        "SIFTALPHA_X_PROJECT_ID=" + stateKey,
                        "SIFTALPHA_X_STOP_REQUEST=ACCEPTED",
                    ).joinToString("\n"),
                    expand = true,
                )
                scheduleEmbeddedPolling(project)
            }
        }
    }

    private fun refreshEmbeddedProject(
        project: V04ProjectGateway.RuntimeProject,
        manualAction: EmbeddedPythonObservationPolicy.ManualAction? = null,
    ) {
        val stateKey = project.summary.documentId
        val operation = when (manualAction) {
            EmbeddedPythonObservationPolicy.ManualAction.STATUS -> beginOperation(
                project = project,
                action = RuntimeOperationAction.STATUS,
                provider = RuntimeOperationProvider.INTERNAL,
            )
            EmbeddedPythonObservationPolicy.ManualAction.LOGS -> beginOperation(
                project = project,
                action = RuntimeOperationAction.LOGS,
                provider = RuntimeOperationProvider.INTERNAL,
            )
            null -> null
        }
        if (manualAction != null && operation == null) return

        if (manualAction != null) {
            states[stateKey] = getString(R.string.runtime_action_checking)
            refresh()
        }

        embeddedObservationExecutor.execute {
            val checkedEnvironmentResolution = if (
                manualAction == EmbeddedPythonObservationPolicy.ManualAction.STATUS
            ) {
                runCatching {
                    runtime.detectEnvironment(project, resolveCompatibility = true)
                }.getOrNull()
            } else {
                null
            }
            val checkedEnvironmentReady = if (
                manualAction == EmbeddedPythonObservationPolicy.ManualAction.STATUS
            ) {
                runCatching { runtime.embeddedPythonEnvironmentReady(project) }.getOrNull()
            } else {
                null
            }
            val snapshot = runCatching {
                runtime.embeddedPythonSnapshotFor(stateKey)
            }.getOrNull()

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                if (manualAction == EmbeddedPythonObservationPolicy.ManualAction.STATUS) {
                    setEnvironmentReady(
                        stateKey,
                        ProjectRuntimeSelection.EMBEDDED_R,
                        checkedEnvironmentReady,
                    )
                }

                if (snapshot == null) {
                    invalidateEmbeddedPolling(stateKey)
                    typedStates[stateKey] = RuntimeState.UNKNOWN
                    states[stateKey] = getString(R.string.runtime_state_not_running)
                    failureReasons.remove(stateKey)
                    val diagnostics = mutableListOf(
                        "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                        "SIFTALPHA_X_PROJECT_ID=" + stateKey,
                        "SIFTALPHA_X_STATE=IDLE",
                        "SIFTALPHA_X_RUNTIME_PHASE=IDLE",
                        "SIFTALPHA_X_PROJECT_STATUS=NOT_STARTED",
                    )
                    if (manualAction == EmbeddedPythonObservationPolicy.ManualAction.STATUS) {
                        checkedEnvironmentResolution?.let { resolution ->
                            diagnostics += resolution.diagnosticLines()
                        }
                        diagnostics += "SIFTALPHA_X_ENVIRONMENT_READY=" + (
                            checkedEnvironmentReady?.toString() ?: "UNKNOWN"
                        )
                    }
                    when (manualAction) {
                        EmbeddedPythonObservationPolicy.ManualAction.STATUS -> {
                            diagnostics += "SIFTALPHA_X_STATUS_CHECK=PASS"
                            diagnostics += "SIFTALPHA_X_STATUS_SOURCE=INTERNAL_SNAPSHOT"
                        }
                        EmbeddedPythonObservationPolicy.ManualAction.LOGS -> {
                            diagnostics += "SIFTALPHA_X_LOG_REFRESH=PASS"
                            diagnostics += "SIFTALPHA_X_LOG_SOURCE=INTERNAL_SNAPSHOT"
                        }
                        null -> Unit
                    }
                    projectOutputs.write(
                        project.folderName,
                        diagnostics.joinToString("\n"),
                        expand = true,
                    )
                    if (manualAction == EmbeddedPythonObservationPolicy.ManualAction.STATUS) {
                        persistRuntimeState(stateKey, ProjectRuntimeSelection.EMBEDDED_R)
                    }
                    operation?.let {
                        finishOperation(stateKey, it.generation, RuntimeOperationPhase.SUCCESS)
                    }
                    refresh()
                    return@runOnUiThread
                }

                if (!ownsEmbeddedObservation(stateKey, snapshot)) {
                    invalidateEmbeddedPolling(stateKey)
                    operation?.let {
                        finishOperation(stateKey, it.generation, RuntimeOperationPhase.FAILED)
                    }
                    return@runOnUiThread
                }
                if (embeddedRuntimeOwnership[stateKey] != RuntimeOwnership.EXTERNAL_PROVIDER) {
                    embeddedRuntimeOwnership[stateKey] = RuntimeOwnershipPolicy.afterAcceptedStart(
                        RuntimeControlRequest.EMBEDDED_R,
                    )
                }

                val cardChanged = syncEmbeddedSnapshot(project, snapshot, manualAction)
                if (manualAction == EmbeddedPythonObservationPolicy.ManualAction.STATUS) {
                    persistRuntimeState(stateKey, ProjectRuntimeSelection.EMBEDDED_R)
                }
                val hadManualOperation = operation != null
                operation?.let {
                    finishOperation(stateKey, it.generation, RuntimeOperationPhase.SUCCESS)
                }
                if (cardChanged || hadManualOperation) refresh()
                if (isEmbeddedActive(snapshot)) scheduleEmbeddedPolling(project)
            }
        }
    }

    private fun syncEmbeddedSnapshot(
        project: V04ProjectGateway.RuntimeProject,
        snapshot: EmbeddedPythonSnapshot,
        manualAction: EmbeddedPythonObservationPolicy.ManualAction? = null,
    ): Boolean {
        val stateKey = project.summary.documentId
        val internalWebObservation = internalWebObservationCache[stateKey]
            ?.takeIf {
                it.sessionId == snapshot.sessionId &&
                    it.generation == snapshot.generation
            }
            ?.observation
        val internalWebChanged = reconcileInternalWebDiscovery(
            projectKey = stateKey,
            snapshot = snapshot,
            observation = internalWebObservation,
        )
        if (snapshot.engine == InternalPythonBackend.ALPINE && isEmbeddedActive(snapshot)) {
            scheduleInternalWebObservation(project, snapshot)
        }

        val runtimeLogWebChanged = if (
            ::webInspector.isInitialized &&
            ::webStateStore.isInitialized &&
            webStateStore.snapshot(stateKey).candidateUrl == null
        ) {
            val safeStdout = if (::secretStore.isInitialized) {
                secretStore.redactRuntimeText(project.folderName, snapshot.stdout)
            } else {
                snapshot.stdout
            }
            val safeStderr = if (::secretStore.isInitialized) {
                secretStore.redactRuntimeText(project.folderName, snapshot.stderr)
            } else {
                snapshot.stderr
            }
            val webCapabilityEnabled = webProfileCache[stateKey]?.enabled == true
            val candidate = RuntimeWebDiscoveryScopePolicy.candidateFromStreams(
                stdout = safeStdout,
                stderr = safeStderr,
                webCapabilityEnabled = webCapabilityEnabled,
            )
            if (candidate != null) {
                val existingWeb = webStateStore.snapshot(stateKey)
                val changed = existingWeb.candidateUrl != candidate.url ||
                    existingWeb.source != candidate.source
                if (changed) {
                    webStateStore.rememberCandidateUrl(
                        projectKey = stateKey,
                        url = candidate.url,
                        framework = existingWeb.framework,
                        source = candidate.source,
                    )
                }
                changed
            } else {
                false
            }
        } else {
            false
        }

        val previous = embeddedLastSnapshots[stateKey]
        val shouldPresent = EmbeddedPythonObservationPolicy.shouldPresent(
            previous = previous,
            current = snapshot,
            manualAction = manualAction,
        )
        if (!shouldPresent && !internalWebChanged && !runtimeLogWebChanged) {
            return false
        }

        val structuralChanged = EmbeddedPythonObservationPolicy.requiresCardRefresh(previous, snapshot)
        embeddedLastSnapshots[stateKey] = snapshot
        val richResultChanged = updateEmbeddedRichResult(project, snapshot)
        val resultWebChanged = if (snapshot.state == EmbeddedPythonState.SUCCEEDED) {
            updateResultWeb(
                project = project,
                stdout = snapshot.stdout,
                stderr = snapshot.stderr,
                explicitSourcePath = snapshot.entrypoint,
            )
        } else {
            false
        }
        if (resultWebChanged && ::projectOutputs.isInitialized) {
            projectOutputs.collapse(project.folderName)
        }

        val mappedState = EmbeddedPythonRuntimeStateMapping.toRuntimeState(snapshot)
        typedStates[stateKey] = mappedState
        if (
            snapshot.state == EmbeddedPythonState.FAILED &&
            snapshot.exitCode != null &&
            ::configurationUi.isInitialized
        ) {
            val findingKey = snapshot.sessionId + ":" + snapshot.generation
            if (embeddedConfigurationFindings.add(findingKey)) {
                configurationUi.showRuntimeFindingIfAny(
                    projectName = project.summary.name,
                    projectDocumentId = project.summary.documentId,
                    folderName = project.folderName,
                    output = buildString {
                        append(snapshot.stdout)
                        if (snapshot.stderr.isNotBlank()) {
                            append('\n')
                            append(snapshot.stderr)
                        }
                    },
                    presentDialog = true,
                )
            }
        }
        currentOperation(stateKey)
            ?.takeIf { it.action == RuntimeOperationAction.STOP && mappedState !in ACTIVE_RUNTIME_STATES }
            ?.let { operation ->
                finishOperation(
                    projectId = stateKey,
                    generation = operation.generation,
                    phase = RuntimeOperationPhase.SUCCESS,
                )
            }
        states[stateKey] = mappedState.uiLabel(this)
        if (mappedState == RuntimeState.EXITED_ERROR && snapshot.stderr.isNotBlank()) {
            failureReasons[stateKey] = snapshot.stderr.trim().lineSequence().lastOrNull().orEmpty()
        } else {
            failureReasons.remove(stateKey)
        }

        val now = System.currentTimeMillis()
        val outputDue = EmbeddedPythonObservationPolicy.shouldRenderOutput(
            lastRenderedAtEpochMs = embeddedLastOutputRenderAt[stateKey],
            nowEpochMs = now,
            intervalMs = EMBEDDED_OUTPUT_RENDER_INTERVAL_MS,
            manualAction = manualAction,
            structuralChanged = structuralChanged,
            active = isEmbeddedActive(snapshot),
        )
        if (::projectOutputs.isInitialized && outputDue) {
            val internalWebDiagnostics = internalWebObservation
                ?.diagnosticLines()
                ?.joinToString("\n")
            projectOutputs.write(
                project.folderName,
                buildString {
                    append(EmbeddedPythonObservationPolicy.outputText(snapshot, manualAction))
                    if (!internalWebDiagnostics.isNullOrBlank()) {
                        append("\n\n")
                        append(internalWebDiagnostics)
                    }
                },
                expand = true,
                forceFollowTail = isEmbeddedActive(snapshot),
            )
            embeddedLastOutputRenderAt[stateKey] = now
        }

        return structuralChanged ||
            internalWebChanged ||
            runtimeLogWebChanged ||
            richResultChanged ||
            resultWebChanged
    }

    private fun scheduleInternalWebObservation(
        project: V04ProjectGateway.RuntimeProject,
        snapshot: EmbeddedPythonSnapshot,
    ) {
        if (!activityStarted || isFinishing || isDestroyed) return
        if (snapshot.engine != InternalPythonBackend.ALPINE || !isEmbeddedActive(snapshot)) return
        if (!::webStateStore.isInitialized) return
        val stateKey = project.summary.documentId
        if (webStateStore.snapshot(stateKey).candidateUrl != null) {
            internalWebDiscoveryRetries.remove(stateKey)
            return
        }
        val currentRetry = internalWebDiscoveryRetries[stateKey]
            ?.takeIf {
                it.sessionId == snapshot.sessionId &&
                    it.generation == snapshot.generation
            }
        if (currentRetry != null && System.currentTimeMillis() < currentRetry.nextEligibleAtEpochMs) {
            return
        }
        if (currentRetry == null) {
            internalWebDiscoveryRetries.remove(stateKey)
        }
        if (!internalWebObservationInFlight.add(stateKey)) return

        val expectedSessionId = snapshot.sessionId
        val expectedGeneration = snapshot.generation
        val cachedProfile = webProfileCache[stateKey]
        internalWebObservationExecutor.execute {
            val resolvedProfile = cachedProfile ?: runCatching {
                webInspector.inspect(stateKey)
            }.getOrNull()
            val learnedPort = webLearnedEndpointStore.read(
                projectKey = stateKey,
                authorityFingerprint = resolvedProfile?.let(::webEndpointAuthorityFingerprint).orEmpty(),
            )?.port
            val hintPorts = RuntimeWebHintPolicy.ports(
                detectedPort = resolvedProfile?.port,
                framework = resolvedProfile?.framework,
                learnedPort = learnedPort,
                detectedSource = resolvedProfile?.source,
            )
            val observation = runCatching {
                runtime.internalAlpineWebObservationFor(snapshot, hintPorts)
            }.getOrNull()?.let { raw ->
                raw.copy(
                    ports = RuntimeWebEndpointAuthorityPolicy.rank(raw.ports) { port ->
                        RuntimeWebEndpointClassifier.classifyLoopback(port)
                    },
                )
            }
            refreshHandler.post {
                internalWebObservationInFlight.remove(stateKey)
                resolvedProfile?.let { webProfileCache[stateKey] = it }
                if (!activityStarted || isFinishing || isDestroyed || observation == null) {
                    return@post
                }
                val latest = runCatching {
                    runtime.embeddedPythonSnapshotFor(stateKey)
                }.getOrNull() ?: return@post
                if (
                    latest.sessionId != expectedSessionId ||
                    latest.generation != expectedGeneration ||
                    !isEmbeddedActive(latest)
                ) {
                    return@post
                }
                internalWebObservationCache[stateKey] = InternalWebObservationRecord(
                    sessionId = expectedSessionId,
                    generation = expectedGeneration,
                    observation = observation,
                )
                if (observation.ports.isEmpty()) {
                    val previousRetry = internalWebDiscoveryRetries[stateKey]
                        ?.takeIf {
                            it.sessionId == expectedSessionId &&
                                it.generation == expectedGeneration
                        }
                    val missCount = (previousRetry?.missCount ?: 0) + 1
                    internalWebDiscoveryRetries[stateKey] = InternalWebDiscoveryRetryRecord(
                        sessionId = expectedSessionId,
                        generation = expectedGeneration,
                        missCount = missCount,
                        nextEligibleAtEpochMs = System.currentTimeMillis() +
                            RuntimeWebDetectionCadence.internalDiscoveryRetryDelay(missCount),
                    )
                } else {
                    internalWebDiscoveryRetries.remove(stateKey)
                }
                if (
                    reconcileInternalWebDiscovery(
                        projectKey = stateKey,
                        snapshot = latest,
                        observation = observation,
                    )
                ) {
                    refresh()
                }
            }
        }
    }

    private fun reconcileInternalWebDiscovery(
        projectKey: String,
        snapshot: EmbeddedPythonSnapshot,
        observation: InternalAlpineWebObservation?,
    ): Boolean {
        if (!::webStateStore.isInitialized) return false
        val current = webStateStore.snapshot(projectKey)
        if (snapshot.state in setOf(
                EmbeddedPythonState.SUCCEEDED,
                EmbeddedPythonState.FAILED,
                EmbeddedPythonState.STOPPED,
            )
        ) {
            internalWebObservationCache.remove(projectKey)
            internalWebObservationInFlight.remove(projectKey)
            internalWebDiscoveryRetries.remove(projectKey)
            if (::webAvailability.isInitialized) webAvailability.invalidate(projectKey)
            if (current.candidateUrl == null) return false
            webStateStore.clear(projectKey)
            return true
        }

        val port = observation?.ports?.firstOrNull() ?: return false
        val candidate = "http://127.0.0.1:$port"
        if (current.source != RuntimeWebCandidateSource.PID_SOCKET && current.candidateUrl != null) {
            // An explicit or runtime-log candidate from this same execution is already a
            // stronger observation. Do not oscillate its source back to procfs on every poll.
            return false
        }
        if (current.candidateUrl == candidate) {
            return false
        }
        webStateStore.rememberCandidateUrl(
            projectKey = projectKey,
            url = candidate,
            framework = current.framework,
            source = RuntimeWebCandidateSource.PID_SOCKET,
        )
        if (::webAvailability.isInitialized) webAvailability.invalidate(projectKey)
        return true
    }

    private fun invalidateInternalWebDiscovery(projectKey: String) {
        internalWebObservationCache.remove(projectKey)
        internalWebObservationInFlight.remove(projectKey)
        internalWebDiscoveryRetries.remove(projectKey)
        if (::webStateStore.isInitialized) webStateStore.clear(projectKey)
        if (::webAvailability.isInitialized) webAvailability.invalidate(projectKey)
    }

    private fun scheduleEmbeddedPolling(project: V04ProjectGateway.RuntimeProject) {
        val stateKey = project.summary.documentId
        embeddedPolls.track(stateKey, project)
        if (!activityStarted) return
        val runnable = embeddedPollRunnableFor(stateKey)
        refreshHandler.removeCallbacks(runnable)
        refreshHandler.post(runnable)
    }

    private fun resumeEmbeddedPolling() {
        if (!activityStarted) return
        embeddedPolls.trackedKeys().forEach { stateKey ->
            val runnable = embeddedPollRunnableFor(stateKey)
            refreshHandler.removeCallbacks(runnable)
            refreshHandler.post(runnable)
        }
    }

    private fun embeddedPollRunnableFor(stateKey: String): Runnable =
        embeddedPollRunnables.getOrPut(stateKey) {
            object : Runnable {
                override fun run() {
                    val project = embeddedPolls.project(stateKey) ?: return
                    if (!activityStarted || isFinishing || isDestroyed) return
                    if (embeddedRuntimeOwnership[stateKey] == RuntimeOwnership.EXTERNAL_PROVIDER) {
                        invalidateEmbeddedPolling(stateKey)
                        return
                    }
                    if (!embeddedPolls.begin(stateKey)) return

                    embeddedObservationExecutor.execute {
                        val snapshot = runCatching {
                            runtime.embeddedPythonSnapshotFor(stateKey)
                        }.getOrNull()
                        runOnUiThread {
                            embeddedPolls.finish(stateKey)
                            if (!activityStarted || isFinishing || isDestroyed) return@runOnUiThread
                            val currentProject = embeddedPolls.project(stateKey) ?: return@runOnUiThread
                            if (embeddedRuntimeOwnership[stateKey] == RuntimeOwnership.EXTERNAL_PROVIDER) {
                                invalidateEmbeddedPolling(stateKey)
                                return@runOnUiThread
                            }
                            if (snapshot == null) {
                                embeddedPollRunnables[stateKey]?.let { next ->
                                    refreshHandler.postDelayed(next, EMBEDDED_POLL_INTERVAL_MS)
                                }
                                return@runOnUiThread
                            }

                            val cardChanged = syncEmbeddedSnapshot(currentProject, snapshot)
                            if (cardChanged) refresh()
                            if (isEmbeddedActive(snapshot)) {
                                embeddedPollRunnables[stateKey]?.let { next ->
                                    refreshHandler.postDelayed(next, EMBEDDED_POLL_INTERVAL_MS)
                                }
                            } else {
                                invalidateEmbeddedPolling(stateKey)
                            }
                        }
                    }
                }
            }
        }

    private fun invalidateEmbeddedPolling(projectDocumentId: String) {
        embeddedPolls.untrack(projectDocumentId)
        embeddedPollRunnables.remove(projectDocumentId)?.let(refreshHandler::removeCallbacks)
    }

    private fun ownsEmbeddedObservation(
        projectDocumentId: String,
        snapshot: EmbeddedPythonSnapshot,
    ): Boolean = RuntimeOwnershipPolicy.ownsEmbeddedSnapshot(
        ownership = embeddedRuntimeOwnership[projectDocumentId] ?: RuntimeOwnership.UNKNOWN,
        state = snapshot.state,
    )

    private fun markExternalRuntimeOwner(projectDocumentId: String) {
        embeddedRuntimeOwnership[projectDocumentId] = RuntimeOwnershipPolicy.afterAcceptedStart(
            RuntimeControlRequest.EXTERNAL_PROVIDER,
        )
        embeddedLastSnapshots.remove(projectDocumentId)
        embeddedLastOutputRenderAt.remove(projectDocumentId)
        internalWebObservationCache.remove(projectDocumentId)
        internalWebObservationInFlight.remove(projectDocumentId)
        internalWebDiscoveryRetries.remove(projectDocumentId)
        invalidateEmbeddedPolling(projectDocumentId)
    }

    private fun isEmbeddedActive(snapshot: EmbeddedPythonSnapshot): Boolean =
        snapshot.state == EmbeddedPythonState.STARTING || snapshot.state == EmbeddedPythonState.RUNNING

    private fun embeddedStartErrorMessage(error: Throwable?): String =
        if (error?.message == "EMBEDDED_R_ENTRYPOINT_UNRESOLVED") {
            getString(R.string.runtime_embedded_r_entrypoint_unresolved)
        } else {
            error?.message ?: getString(R.string.runtime_unavailable)
        }

    private fun embeddedControlReasonMessage(reason: RuntimeControlReason): String = when (reason) {
        RuntimeControlReason.ENTRYPOINT_UNRESOLVED ->
            getString(R.string.runtime_embedded_r_entrypoint_unresolved)
        else -> getString(R.string.runtime_embedded_r_capability_unavailable, reason.name)
    }

    private fun dispatch(
        project: V04ProjectGateway.RuntimeProject,
        action: ProjectRuntimeController.Action,
        openBrowserAfterLogs: Boolean = false,
        browserConfiguredUrl: String? = null,
        browserFramework: String? = null,
        silentRecovery: Boolean = false,
        controlRequest: RuntimeControlRequest? = null,
        launchInvocation: PythonLaunchInvocation? = null,
        webLogDiscoveryAllowed: Boolean = false,
        webHintPorts: List<Int> = emptyList(),
        automaticObservation: Boolean = false,
        observationGeneration: Long? = null,
    ): Boolean {
        val stateKey = project.summary.documentId
        if (action != ProjectRuntimeController.Action.STOP) when (
            ObservationPresentationPolicy.dispatchDecision(
                automaticObservation = automaticObservation,
                automaticPending = hasAutomaticPendingOperation(stateKey, project.folderName),
                deferredManualAction = deferredManualActions.containsKey(stateKey),
            )
        ) {
            ObservationDispatchDecision.DEFER_UNTIL_AUTOMATIC_COMPLETES -> {
                deferredManualActions.putIfAbsent(
                    stateKey,
                    DeferredManualAction(
                        project = project,
                        action = action,
                        openBrowserAfterLogs = openBrowserAfterLogs,
                        browserConfiguredUrl = browserConfiguredUrl,
                        browserFramework = browserFramework,
                        silentRecovery = silentRecovery,
                        controlRequest = controlRequest,
                        launchInvocation = launchInvocation,
                        webLogDiscoveryAllowed = webLogDiscoveryAllowed,
                        webHintPorts = webHintPorts,
                    ),
                )
                refresh()
                return true
            }
            ObservationDispatchDecision.WAITING_FOR_DEFERRED_ACTION -> return true
            ObservationDispatchDecision.DISPATCH_NOW -> Unit
        }
        val effectiveControlRequest = controlRequest ?: if (
            action == ProjectRuntimeController.Action.START ||
            action == ProjectRuntimeController.Action.PREPARE
        ) {
            selectedRuntimeControlRequest(project)
        } else {
            RuntimeControlRequest.EXTERNAL_PROVIDER
        }
        val requiredConfiguration = action == ProjectRuntimeController.Action.START &&
            effectiveControlRequest == RuntimeControlRequest.EMBEDDED_R &&
            runCatching {
                configurationUi.snapshot(
                    project.summary.documentId,
                    project.folderName,
                ).preflight.requiredCount > 0
            }.getOrDefault(true)
        val route = try {
            runtime.resolveControlPath(
                project = project,
                action = action,
                request = effectiveControlRequest,
                requiredConfiguration = requiredConfiguration,
            )
        } catch (e: Throwable) {
            errorDialog(
                getString(R.string.runtime_command_generation_failed),
                e.message ?: getString(R.string.runtime_unavailable),
            )
            return false
        }
        if (route.path == RuntimeControlPath.REJECTED) {
            errorDialog(
                getString(R.string.runtime_embedded_r_start_failed),
                embeddedControlReasonMessage(route.reason),
            )
            return false
        }
        if (
            route.path == RuntimeControlPath.EXTERNAL_PROVIDER &&
            (action == ProjectRuntimeController.Action.PREPARE ||
                action == ProjectRuntimeController.Action.START)
        ) {
            val gateAction = if (action == ProjectRuntimeController.Action.PREPARE) {
                ExternalActionGate.Action.PREPARE
            } else {
                ExternalActionGate.Action.RUN
            }
            val deferred = DeferredManualAction(
                project = project,
                action = action,
                openBrowserAfterLogs = openBrowserAfterLogs,
                browserConfiguredUrl = browserConfiguredUrl,
                browserFramework = browserFramework,
                silentRecovery = silentRecovery,
                controlRequest = effectiveControlRequest,
                launchInvocation = launchInvocation,
                webLogDiscoveryAllowed = webLogDiscoveryAllowed,
                webHintPorts = webHintPorts,
            )
            if (!ensureExternalProviderReady(project, gateAction, deferred)) {
                return false
            }
        }
        if (!canDispatch(project, action, route.path)) return false
        if (action == ProjectRuntimeController.Action.PREPARE && !automaticObservation) {
            scheduleWebRecognition(project)
        }
        if (action == ProjectRuntimeController.Action.START ||
            action == ProjectRuntimeController.Action.STOP ||
            action == ProjectRuntimeController.Action.CLEAN
        ) {
            invalidateExternalObservation(stateKey)
        }
        if (action == ProjectRuntimeController.Action.START) {
            webStateStore.clear(stateKey)
            webAvailability.invalidate(stateKey)
        }
        if (route.path == RuntimeControlPath.EMBEDDED_R) {
            when (action) {
                ProjectRuntimeController.Action.PREPARE ->
                    prepareEmbeddedProject(project)
                ProjectRuntimeController.Action.START ->
                    startEmbeddedProject(
                        project = project,
                        requiredConfiguration = requiredConfiguration,
                        launchInvocation = launchInvocation,
                    )
                ProjectRuntimeController.Action.STOP ->
                    requestEmbeddedStop(project)
                else -> Unit
            }
            return true
        }
        if (!ensureRuntime()) return false
        // The card is rebuilt after every state transition, but an old dialog or click callback can
        // still arrive after that rebuild. Re-check the stable project identity at the side-effect
        // boundary so one project cannot acquire two mutable Runtime operations.
        if (!canDispatch(project, action)) return false
        if (silentRecovery) recoveryProjects += stateKey
        val command = try {
            when (action) {
                ProjectRuntimeController.Action.PREPARE -> runtime.prepare(project)
                ProjectRuntimeController.Action.START ->
                    if (launchInvocation == null) {
                        runtime.start(
                            project = project,
                            pythonLaunchInvocation = null,
                            webLogDiscoveryAllowed = webLogDiscoveryAllowed,
                            webHintPorts = webHintPorts,
                        )
                    } else {
                        runtime.start(
                            project = project,
                            pythonLaunchInvocation = launchInvocation,
                            webLogDiscoveryAllowed = webLogDiscoveryAllowed,
                            webHintPorts = webHintPorts,
                        )
                    }
                ProjectRuntimeController.Action.STOP -> runtime.stop(project)
                ProjectRuntimeController.Action.STATUS -> runtime.status(
                    project = project,
                    webLogDiscoveryAllowed = webLogDiscoveryAllowed,
                    webHintPorts = webHintPorts,
                )
                ProjectRuntimeController.Action.LOGS -> runtime.logs(
                    project = project,
                    webLogDiscoveryAllowed = webLogDiscoveryAllowed,
                    webHintPorts = webHintPorts,
                )
                ProjectRuntimeController.Action.CLEAN -> runtime.clean(project)
                ProjectRuntimeController.Action.CLONE_GITHUB -> error("clone requires spec")
            }
        } catch (e: Throwable) {
            errorDialog(
                getString(R.string.runtime_command_generation_failed),
                e.message ?: getString(R.string.runtime_unavailable),
            )
            return false
        }
        val managedCommand = runtime.wrapCancelableExternalActivity(
            project = project,
            action = action,
            command = command,
        )
        val id = send(managedCommand, renderToSystemOutput = false)
        if (id == null) {
            if (silentRecovery) {
                recoveryProjects.remove(stateKey)
                typedStates[stateKey] = RuntimeState.UNKNOWN
                failureReasons[stateKey] = getString(R.string.runtime_detection_failed)
                persistRuntimeState(stateKey)
                refresh()
            }
            return false
        }
        val operation = if (!silentRecovery) {
            val actionContract = action.toRuntimeOperationAction()
            actionContract?.let {
                beginOperation(
                    project = project,
                    action = it,
                    provider = RuntimeOperationProvider.EXTERNAL,
                    executionId = id,
                    userVisible = !automaticObservation,
                )
            }
        } else {
            null
        }
        if (!silentRecovery && action.toRuntimeOperationAction() != null && operation == null) {
            // The command was dispatched only after the stable identity checks above, so a
            // persisted live record here means a stale UI callback raced this dispatch. Do not
            // let its result mutate a newer operation.
            cancelledExternalExecutions += id
            return false
        }
        if (action != ProjectRuntimeController.Action.STOP) {
            val activityKind = if (automaticObservation) {
                ProjectActivityRegistry.Kind.OBSERVATION
            } else {
                when (action) {
                    ProjectRuntimeController.Action.PREPARE -> ProjectActivityRegistry.Kind.PREPARE
                    ProjectRuntimeController.Action.START -> ProjectActivityRegistry.Kind.START
                    ProjectRuntimeController.Action.STATUS -> ProjectActivityRegistry.Kind.STATUS
                    ProjectRuntimeController.Action.LOGS -> ProjectActivityRegistry.Kind.LOGS
                    ProjectRuntimeController.Action.CLEAN -> ProjectActivityRegistry.Kind.CLEAN
                    ProjectRuntimeController.Action.STOP,
                    ProjectRuntimeController.Action.CLONE_GITHUB,
                    -> null
                }
            }
            if (activityKind != null) {
                val token = projectActivities.begin(stateKey, activityKind) {
                    cancelledExternalExecutions += id
                }
                externalActivityTokens[id] = token
            }
        }
        if (action == ProjectRuntimeController.Action.START) {
            richResults.remove(stateKey)
            resultWebResults.remove(stateKey)
            resultWebSuppressedProjects += stateKey
            markExternalRuntimeOwner(stateKey)
            beginExternalObservation(
                project = project,
                webLogDiscoveryAllowed = webLogDiscoveryAllowed,
                webHintPorts = webHintPorts,
            )
        }
        if (
            ::webAvailability.isInitialized &&
            (action == ProjectRuntimeController.Action.STOP ||
                action == ProjectRuntimeController.Action.CLEAN)
        ) {
            webAvailability.invalidate(project.summary.documentId)
        }
        if (!silentRecovery && !automaticObservation) {
            projectOutputs.write(
                project.folderName,
                getString(R.string.runtime_command_sent, id),
                expand = true,
            )
        }
        if (action == ProjectRuntimeController.Action.PREPARE && ::prepareLiveProgress.isInitialized) {
            prepareLiveProgress.start(project.folderName, id)
        }
        val pendingItem = Pending(
            action = action,
            documentId = project.summary.documentId,
            folderName = project.folderName,
            openBrowserAfterLogs = openBrowserAfterLogs,
            browserConfiguredUrl = browserConfiguredUrl,
            browserFramework = browserFramework,
            webLogDiscoveryAllowed = webLogDiscoveryAllowed,
            automaticObservation = automaticObservation,
            observationGeneration = observationGeneration,
        )
        if (!automaticObservation) {
            states[stateKey] = if (silentRecovery) {
                getString(R.string.runtime_lifecycle_recovering)
            } else when (action) {
                ProjectRuntimeController.Action.PREPARE -> getString(R.string.runtime_action_preparing)
                ProjectRuntimeController.Action.START -> getString(R.string.runtime_action_starting)
                ProjectRuntimeController.Action.STOP -> getString(R.string.runtime_action_stopping)
                ProjectRuntimeController.Action.STATUS -> getString(R.string.runtime_action_checking)
                ProjectRuntimeController.Action.LOGS -> getString(R.string.runtime_action_checking)
                ProjectRuntimeController.Action.CLEAN -> getString(R.string.runtime_action_cleaning)
                ProjectRuntimeController.Action.CLONE_GITHUB -> getString(R.string.runtime_action_importing)
            }
            when (action) {
                ProjectRuntimeController.Action.START -> typedStates[stateKey] = RuntimeState.STARTING
                ProjectRuntimeController.Action.PREPARE -> typedStates[stateKey] = RuntimeState.PREPARING
                else -> Unit
            }
            // A new command supersedes the previous terminal failure while it is being reconciled.
            // Persist STARTING/PREPARING before the callback arrives so a process-death or Activity
            // recreation can still issue a real STATUS recovery probe.
            failureReasons.remove(stateKey)
            persistRuntimeState(stateKey)
            refresh()
        }
        registerPending(id, pendingItem)
        // Preserve the existing fast-result reconciliation order, but expose a still-pending
        // operation when the result has not already been consumed synchronously.
        if (!automaticObservation && pending.containsKey(id)) refresh()
        return true
    }

    private fun registerPending(id: Int, item: Pending) {
        pending[id] = item
        TermuxResultBus.consume(id)?.let(resultListener)
    }

    private fun drainDeferredManualAction(stateKey: String) {
        val deferred = deferredManualActions.remove(stateKey) ?: return
        dispatch(
            project = deferred.project,
            action = deferred.action,
            openBrowserAfterLogs = deferred.openBrowserAfterLogs,
            browserConfiguredUrl = deferred.browserConfiguredUrl,
            browserFramework = deferred.browserFramework,
            silentRecovery = deferred.silentRecovery,
            controlRequest = deferred.controlRequest,
            launchInvocation = deferred.launchInvocation,
            webLogDiscoveryAllowed = deferred.webLogDiscoveryAllowed,
            webHintPorts = deferred.webHintPorts,
        )
    }

    private fun updateEnvironmentState(stateKey: String, stdout: String): Boolean? {
        when {
            "SIFTALPHA_ENV=READY" in stdout ->
                setEnvironmentReady(stateKey, ProjectRuntimeSelection.TERMUX, true)
            "SIFTALPHA_ENV=NOT_READY" in stdout || "SIFTALPHA_ENV=CLEANED" in stdout ->
                setEnvironmentReady(stateKey, ProjectRuntimeSelection.TERMUX, false)
        }
        return environmentReady(stateKey, ProjectRuntimeSelection.TERMUX)
    }

    private fun showRuntimeConfigurationFinding(
        item: Pending,
        result: RuntimeResult,
        presentDialog: Boolean = true,
    ): Boolean {
        if (!::configurationUi.isInitialized) return false
        val project = runCatching {
            gateway.projects().firstOrNull {
                it.summary.documentId == item.documentId ||
                    (item.documentId == null && it.folderName == item.folderName)
            }
        }.getOrNull() ?: return false
        val text = buildString {
            append(result.stdout)
            if (result.stderr.isNotBlank()) {
                append('\n')
                append(result.stderr)
            }
        }
        return configurationUi.showRuntimeFindingIfAny(
            projectName = project.summary.name,
            projectDocumentId = project.summary.documentId,
            folderName = project.folderName,
            output = text,
            presentDialog = presentDialog,
            onConfigurationCompleted = { retryProjectAfterConfiguration(project) },
        )
    }

    private fun retryProjectAfterConfiguration(
        project: V04ProjectGateway.RuntimeProject,
        webProfile: WebProjectInspector.Profile? = null,
    ) {
        val stateKey = project.summary.documentId
        // Configuration can be opened before preparation. In that state saving or skipping values
        // must not issue a doomed runtime command; the normal Prepare -> Run flow remains intact.
        if (environmentReady(stateKey) != true) return
        if (pending.values.any { item ->
                item.documentId == project.summary.documentId ||
                    (item.documentId == null && item.folderName == project.folderName)
            }) {
            return
        }
        if (typedStates[stateKey] in setOf(
                RuntimeState.PREPARING,
                RuntimeState.STARTING,
                RuntimeState.RUNNING,
            )
        ) {
            return
        }
        startProject(project, webProfile)
    }

    private fun handleResult(item: Pending, result: RuntimeResult) {
        val stateKey = item.documentId ?: item.folderName
        val stdout = result.stdout
        val success = result.exitCode == 0 && result.internalErrorMessage.isBlank()
        val timedOut = "SIFTALPHA_OPERATION_RESULT=TIMED_OUT" in stdout ||
            "SIFTALPHA_ERROR=OPERATION_TIMEOUT" in stdout
        val runtimeState = RuntimeState.fromOutput(stdout)
        if (runtimeState != RuntimeState.UNKNOWN) {
            typedStates[stateKey] = runtimeState
        }
        val failureReason = when {
            !success -> RuntimeFailureReason.summarize(
                exitCode = result.exitCode,
                internalErrorMessage = result.internalErrorMessage,
                stdout = result.stdout,
                stderr = result.stderr,
            )
            runtimeState == RuntimeState.EXITED_ERROR -> RuntimeFailureReason.summarize(
                exitCode = RuntimeState.extractExitCode(stdout) ?: 1,
                internalErrorMessage = result.internalErrorMessage,
                stdout = result.stdout,
                stderr = result.stderr,
            )
            else -> null
        }
        if (failureReason.isNullOrBlank()) {
            failureReasons.remove(stateKey)
        } else {
            failureReasons[stateKey] = failureReason!!
        }
        val richResultChanged = if (
            RichResultDetectionPolicy.shouldInspectOutput(
                action = item.action,
                webLogDiscoveryAllowed = item.webLogDiscoveryAllowed,
            )
        ) {
            mergeRichResult(stateKey, stdout)
        } else {
            false
        }
        if (richResultChanged && ::projectOutputs.isInitialized) {
            projectOutputs.collapse(item.folderName)
        }
        val resultWebChanged = if (
            runtimeState == RuntimeState.EXITED_SUCCESS &&
            item.action == ProjectRuntimeController.Action.LOGS
        ) {
            updateExternalResultWeb(item, result)
        } else {
            false
        }
        if (resultWebChanged && ::projectOutputs.isInitialized) {
            projectOutputs.collapse(item.folderName)
        }
        val runtimeCandidate = RuntimeWebDiscoveryScopePolicy.candidateFromOutput(
            output = stdout,
            webCapabilityEnabled = item.webLogDiscoveryAllowed,
        )
        runtimeCandidate?.let { candidate ->
            webStateStore.rememberCandidateUrl(
                projectKey = item.documentId ?: item.folderName,
                url = candidate.url,
                framework = item.browserFramework,
                source = candidate.source,
            )
        }
        updateEnvironmentState(stateKey, stdout)

        when (item.action) {
            ProjectRuntimeController.Action.CLONE_GITHUB -> {
                if (success && "SIFTALPHA_CLONE=OK" in stdout) {
                    item.cloneSpec?.let { attachCloneMetadata(it) }
                } else {
                    states[stateKey] = getString(R.string.runtime_github_import_failed)
                    refresh()
                    runtimeError(result)
                }
            }
            ProjectRuntimeController.Action.PREPARE -> {
                if (::prepareLiveProgress.isInitialized) prepareLiveProgress.finish(item.folderName)
                val prepared = success && "SIFTALPHA_ENV=READY" in stdout
                states[stateKey] = if (prepared) {
                    getString(R.string.runtime_state_not_running)
                } else {
                    getString(R.string.runtime_prepare_failed)
                }
                if (prepared) {
                    setEnvironmentReady(stateKey, ProjectRuntimeSelection.TERMUX, true)
                    typedStates[stateKey] = RuntimeState.UNKNOWN
                } else {
                    typedStates[stateKey] = RuntimeState.ENVIRONMENT_ERROR
                }
                refresh()
                if (!success) runtimeError(result)
            }
            ProjectRuntimeController.Action.START -> {
                if (!success && runtimeState == RuntimeState.UNKNOWN) {
                    typedStates[stateKey] = RuntimeState.EXITED_ERROR
                }
                states[stateKey] = when (runtimeState) {
                    RuntimeState.RUNNING,
                    RuntimeState.EXITED_SUCCESS,
                    RuntimeState.EXITED_ERROR,
                    RuntimeState.STOPPED_BY_USER,
                    RuntimeState.ENVIRONMENT_ERROR -> runtimeState.uiLabel(this)
                    else -> when {
                        "SIFTALPHA_STATUS=COMPLETED_OR_EXITED" in stdout ->
                            getString(R.string.runtime_run_completed)
                        success -> getString(R.string.runtime_started)
                        else -> getString(R.string.runtime_start_failed)
                    }
                }

                val sourceOrConfiguredUrl = item.browserConfiguredUrl
                if (success && runtimeState == RuntimeState.RUNNING && sourceOrConfiguredUrl != null) {
                    webStateStore.rememberCandidateUrl(
                        item.documentId ?: item.folderName,
                        sourceOrConfiguredUrl,
                        item.browserFramework,
                    )
                }
                refresh()

                val configurationFindingShown = showRuntimeConfigurationFinding(item, result)
                if (!success && !configurationFindingShown) runtimeError(result)
            }
            ProjectRuntimeController.Action.STOP -> {
                // An explicit STOP result closes any stale foreground-recovery marker. Without
                // this, the policy keeps every runtime action disabled even after the process has
                // been stopped and the next START can never be dispatched.
                recoveryProjects.remove(stateKey)
                states[stateKey] = if (success) {
                    val stoppedState = if (runtimeState == RuntimeState.UNKNOWN) {
                        RuntimeState.STOPPED_BY_USER
                    } else {
                        runtimeState
                    }
                    typedStates[stateKey] = stoppedState
                    stoppedState.uiLabel(this)
                } else {
                    getString(R.string.runtime_stop_failed)
                }
                refresh()
                if (!success) runtimeError(result)
            }
            ProjectRuntimeController.Action.STATUS -> {
                val recovering = recoveryProjects.contains(stateKey)
                if (recovering) {
                    showRuntimeConfigurationFinding(
                        item = item,
                        result = result,
                        presentDialog = false,
                    )
                    recoveryProjects.remove(stateKey)
                }
                if (runtimeState != RuntimeState.UNKNOWN) {
                    typedStates[stateKey] = runtimeState
                } else if (success && item.automaticObservation) {
                    // A transiently incomplete STATUS response must not turn a still-running
                    // project into an environment error or a false stopped state.
                    typedStates[stateKey] = typedStates[stateKey] ?: RuntimeState.UNKNOWN
                } else if (success) {
                    typedStates[stateKey] = RuntimeState.STOPPED_BY_USER
                } else {
                    typedStates[stateKey] = RuntimeState.ENVIRONMENT_ERROR
                }
                states[stateKey] = when {
                    recovering && typedStates[stateKey] == RuntimeState.RUNNING ->
                        getString(R.string.runtime_lifecycle_running)
                    runtimeState != RuntimeState.UNKNOWN -> runtimeState.uiLabel(this)
                    success && item.automaticObservation ->
                        states[stateKey] ?: getString(R.string.runtime_state_not_checked)
                    success -> getString(R.string.runtime_lifecycle_stopped)
                    else -> getString(R.string.runtime_lifecycle_run_failed)
                }
                refresh()
                if (!success && !recovering && !item.automaticObservation) runtimeError(result)
            }
            ProjectRuntimeController.Action.LOGS -> {
                if (runtimeState != RuntimeState.UNKNOWN || richResultChanged) {
                    if (runtimeState != RuntimeState.UNKNOWN) {
                        states[stateKey] = runtimeState.uiLabel(this)
                    }
                    refresh()
                }
                val configurationFindingShown = showRuntimeConfigurationFinding(
                    item = item,
                    result = result,
                    presentDialog = !item.automaticObservation,
                )
                if (!success) {
                    if (!item.automaticObservation && !configurationFindingShown) runtimeError(result)
                } else if (item.openBrowserAfterLogs) {
                    if (runtimeState != RuntimeState.RUNNING) {
                        errorDialog(
                            getString(R.string.runtime_web_not_running_title),
                            getString(R.string.runtime_web_not_running_message),
                        )
                    } else {
                        openBrowserFromRuntimeLogs(item, stdout)
                    }
                }
            }
            ProjectRuntimeController.Action.CLEAN -> {
                states[stateKey] = getString(
                    if (success) R.string.runtime_state_not_running else R.string.runtime_clean_failed,
                )
                if (success) {
                    typedStates[stateKey] = RuntimeState.UNKNOWN
                    setEnvironmentReady(stateKey, ProjectRuntimeSelection.TERMUX, false)
                    configurationUi.clearRuntimeDiscovery(item.folderName)
                }
                refresh()
                if (!success) runtimeError(result)
            }
        }
        if (timedOut) {
            states[stateKey] = getString(R.string.runtime_operation_timed_out)
            failureReasons[stateKey] = "RUNTIME_OPERATION_TIMED_OUT:${item.action.name}"
        }
        if (item.automaticObservation) {
            // A manual request accepted during automatic observation gets the first safe
            // non-overlapping dispatch slot after the automatic result is reconciled.
            drainDeferredManualAction(stateKey)
        }
        continueExternalObservation(
            item = item,
            runtimeState = runtimeState,
            success = success,
        )
        persistRuntimeState(stateKey)
    }

    private fun beginExternalObservation(
        project: V04ProjectGateway.RuntimeProject,
        webLogDiscoveryAllowed: Boolean,
        webHintPorts: List<Int> = emptyList(),
    ) {
        val stateKey = project.summary.documentId
        val resolvedHints = if (webHintPorts.isNotEmpty()) {
            webHintPorts
        } else {
            val profile = webProfileCache[stateKey] ?: runCatching {
                webInspector.inspect(stateKey)
            }.getOrNull()?.also { webProfileCache[stateKey] = it }
            RuntimeWebHintPolicy.ports(
                detectedPort = profile?.port,
                framework = profile?.framework,
                learnedPort = webLearnedEndpointStore.read(
                    projectKey = stateKey,
                    authorityFingerprint = profile?.let(::webEndpointAuthorityFingerprint).orEmpty(),
                )?.port,
                detectedSource = profile?.source,
            )
        }
        externalObservationRunnables[stateKey]?.let(refreshHandler::removeCallbacks)
        val generation = (externalObservationGenerations[stateKey] ?: 0L) + 1L
        externalObservationGenerations[stateKey] = generation
        externalObservations[stateKey] = ExternalObservation(
            project = project,
            generation = generation,
            webLogDiscoveryAllowed = webLogDiscoveryAllowed,
            webHintPorts = resolvedHints,
        )
    }

    private fun invalidateExternalObservation(projectKey: String) {
        externalObservationGenerations[projectKey] =
            (externalObservationGenerations[projectKey] ?: 0L) + 1L
        externalObservations.remove(projectKey)
        externalObservationRunnables[projectKey]?.let(refreshHandler::removeCallbacks)
    }

    private fun stopExternalObservation(projectKey: String) {
        invalidateExternalObservation(projectKey)
    }

    private fun isCurrentAutomaticObservation(item: Pending): Boolean {
        val generation = item.observationGeneration ?: return false
        val stateKey = item.documentId ?: item.folderName
        return externalObservations[stateKey]?.generation == generation
    }

    private fun hasPendingOperation(projectKey: String, folderName: String): Boolean =
        pending.values.any { item ->
            item.documentId == projectKey ||
                (item.documentId == null && item.folderName == folderName)
        }

    private fun hasAutomaticPendingOperation(projectKey: String, folderName: String): Boolean =
        pending.values.any { item ->
            item.automaticObservation &&
                (item.documentId == projectKey ||
                    (item.documentId == null && item.folderName == folderName))
        }

    private fun resumeExternalObservations() {
        if (!activityStarted || isFinishing || isDestroyed) return
        externalObservations.values.toList().forEach { observation ->
            scheduleExternalObservation(observation.project, delayMs = 0L)
        }
    }

    private fun scheduleExternalObservation(
        project: V04ProjectGateway.RuntimeProject,
        delayMs: Long? = null,
    ) {
        val stateKey = project.summary.documentId
        val observation = externalObservations[stateKey] ?: return
        if (!activityStarted || isFinishing || isDestroyed) return
        val resolvedDelayMs = delayMs ?: RuntimeWebDetectionCadence.externalObservationDelay(
            webDiscoveryStillUseful = webObservationProbeAllowed(observation),
        )
        val runnable = externalObservationRunnables.getOrPut(stateKey) {
            Runnable { runExternalObservation(stateKey) }
        }
        refreshHandler.removeCallbacks(runnable)
        refreshHandler.postDelayed(runnable, resolvedDelayMs.coerceAtLeast(0L))
        // Keep the local read above as an ownership guard: a replaced generation must not reuse
        // a runnable that was scheduled for an older project observation.
        if (externalObservations[stateKey]?.generation != observation.generation) {
            refreshHandler.removeCallbacks(runnable)
        }
    }

    private fun runExternalObservation(projectKey: String) {
        val observation = externalObservations[projectKey] ?: return
        if (!activityStarted || isFinishing || isDestroyed) return
        val state = typedStates[projectKey] ?: RuntimeState.UNKNOWN
        when (
            RuntimeAutoObservationPolicy.decide(
                RuntimeAutoObservationPolicy.Input(
                    state = state,
                    hasPendingOperation = hasPendingOperation(
                        projectKey = projectKey,
                        folderName = observation.project.folderName,
                    ),
                    finalLogsCompleted = observation.finalLogsCompleted,
                ),
            )
        ) {
            RuntimeObservationStep.WAIT_FOR_PENDING ->
                scheduleExternalObservation(observation.project)
            RuntimeObservationStep.REQUEST_STATUS -> {
                val sent = dispatch(
                    project = observation.project,
                    action = ProjectRuntimeController.Action.STATUS,
                    webLogDiscoveryAllowed = observation.webLogDiscoveryAllowed,
                    webHintPorts = observation.webHintPorts,
                    automaticObservation = true,
                    observationGeneration = observation.generation,
                )
                if (!sent) scheduleExternalObservation(observation.project)
            }
            RuntimeObservationStep.REQUEST_FINAL_LOGS ->
                requestExternalFinalLogs(observation)
            RuntimeObservationStep.STOP ->
                stopExternalObservation(projectKey)
        }
    }

    private fun requestExternalFinalLogs(observation: ExternalObservation) {
        val stateKey = observation.project.summary.documentId
        if (observation.finalLogsRequested) return
        if (hasPendingOperation(stateKey, observation.project.folderName)) {
            scheduleExternalObservation(observation.project)
            return
        }
        observation.finalLogsRequested = true
        val sent = dispatch(
            project = observation.project,
            action = ProjectRuntimeController.Action.LOGS,
            webLogDiscoveryAllowed = observation.webLogDiscoveryAllowed,
            webHintPorts = observation.webHintPorts,
            automaticObservation = true,
            observationGeneration = observation.generation,
        )
        if (!sent) {
            observation.finalLogsRequested = false
            scheduleExternalObservation(observation.project)
        }
    }

    private fun webObservationProbeAllowed(observation: ExternalObservation): Boolean {
        val stateKey = observation.project.summary.documentId
        val snapshot = webStateStore.snapshot(stateKey)
        val candidateUrls = listOfNotNull(snapshot.candidateUrl).distinct()
        val endpointVerified = ::webAvailability.isInitialized &&
            webAvailability.reachableUrl(
                projectKey = stateKey,
                runtimeState = typedStates[stateKey] ?: RuntimeState.UNKNOWN,
                candidateUrls = candidateUrls,
            ) != null
        return RuntimeWebObservationProbePolicy.shouldProbe(
            RuntimeWebObservationProbePolicy.Input(
                webLogDiscoveryAllowed = observation.webLogDiscoveryAllowed,
                runtimeHintDiscoveryAllowed = observation.webHintPorts.isNotEmpty(),
                probeCount = observation.webLogProbeCount,
                maxProbeCount = EXTERNAL_WEB_LOG_PROBE_MAX,
                candidateExists = candidateUrls.isNotEmpty(),
                endpointVerified = endpointVerified,
            ),
        )
    }

    private fun requestExternalWebLogs(observation: ExternalObservation) {
        val stateKey = observation.project.summary.documentId
        if (!webObservationProbeAllowed(observation)) {
            scheduleExternalObservation(observation.project)
            return
        }
        if (hasPendingOperation(stateKey, observation.project.folderName)) {
            scheduleExternalObservation(observation.project)
            return
        }
        observation.webLogProbeCount += 1
        observation.statusesSinceWebLogProbe = 0
        val sent = dispatch(
            project = observation.project,
            action = ProjectRuntimeController.Action.LOGS,
            webLogDiscoveryAllowed = observation.webLogDiscoveryAllowed,
            webHintPorts = observation.webHintPorts,
            automaticObservation = true,
            observationGeneration = observation.generation,
        )
        if (!sent) {
            observation.webLogProbeCount -= 1
            scheduleExternalObservation(observation.project)
        }
    }

    private fun continueExternalObservation(
        item: Pending,
        runtimeState: RuntimeState,
        success: Boolean,
    ) {
        val stateKey = item.documentId ?: item.folderName
        val observation = externalObservations[stateKey] ?: return
        if (
            item.observationGeneration != null &&
            item.observationGeneration != observation.generation
        ) {
            return
        }

        if (item.action == ProjectRuntimeController.Action.LOGS && observation.finalLogsRequested) {
            // Final LOGS is deliberately one-shot, including a failed LOGS command. The terminal
            // state and failure reason are already reconciled from STATUS/START output.
            observation.finalLogsCompleted = true
            stopExternalObservation(stateKey)
            return
        }

        if (item.action == ProjectRuntimeController.Action.START) {
            if (!success) {
                stopExternalObservation(stateKey)
            } else if (runtimeState == RuntimeState.EXITED_SUCCESS ||
                runtimeState == RuntimeState.EXITED_ERROR
            ) {
                requestExternalFinalLogs(observation)
            } else if (
                observation.webLogProbeCount == 0 &&
                webObservationProbeAllowed(observation)
            ) {
                requestExternalWebLogs(observation)
            } else {
                scheduleExternalObservation(observation.project)
            }
            return
        }

        if (!success && item.action == ProjectRuntimeController.Action.STATUS) {
            // A transient STATUS transport failure must not terminate a still-owned observation.
            scheduleExternalObservation(observation.project)
            return
        }

        val currentState = typedStates[stateKey] ?: runtimeState
        when (item.action) {
            ProjectRuntimeController.Action.STATUS -> {
                when (currentState) {
                    RuntimeState.RUNNING,
                    RuntimeState.STARTING,
                    RuntimeState.UNKNOWN -> {
                        observation.statusesSinceWebLogProbe += 1
                        if (
                            observation.statusesSinceWebLogProbe >= EXTERNAL_WEB_LOG_PROBE_EVERY_STATUS &&
                            webObservationProbeAllowed(observation)
                        ) {
                            requestExternalWebLogs(observation)
                        } else {
                            scheduleExternalObservation(observation.project)
                        }
                    }
                    RuntimeState.EXITED_SUCCESS,
                    RuntimeState.EXITED_ERROR -> requestExternalFinalLogs(observation)
                    RuntimeState.STOPPED_BY_USER,
                    RuntimeState.ENVIRONMENT_ERROR,
                    RuntimeState.PREPARING -> stopExternalObservation(stateKey)
                }
            }
            ProjectRuntimeController.Action.LOGS ->
                scheduleExternalObservation(observation.project)
            else -> Unit
        }
    }

    private fun updateExternalResultWeb(
        item: Pending,
        result: RuntimeResult,
    ): Boolean {
        val projectKey = item.documentId ?: return false
        val project = gateway.projects().firstOrNull { it.summary.documentId == projectKey }
            ?: return false
        return updateResultWeb(
            project = project,
            stdout = result.stdout,
            stderr = result.stderr,
        )
    }

    private fun updateResultWeb(
        project: V04ProjectGateway.RuntimeProject,
        stdout: String,
        stderr: String,
        explicitSourcePath: String? = null,
    ): Boolean {
        if (!::resultWebStore.isInitialized) return false
        val extracted = RuntimeProgramOutputExtractor.extract(
            stdout = stdout,
            stderr = stderr,
            explicitSourcePath = explicitSourcePath,
        )
        if (extracted.text.isBlank()) return false

        val sourcePath = extracted.sourcePath
            ?: project.summary.entry
                .takeIf { it.endsWith(".py", ignoreCase = true) }
            ?: runCatching {
                gateway.resolveEmbeddedPythonEntrypoint(project.summary.documentId)
            }.getOrNull()
        val normalizedExtracted = if (sourcePath != extracted.sourcePath) {
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
            extracted = normalizedExtracted,
            sourceText = sourceText,
        )
        val html = AdaptiveResultHtmlRenderer.render(document)
        val saved = resultWebStore.saveIfChanged(
            projectKey = project.summary.documentId,
            projectName = project.summary.name,
            document = document,
            html = html,
        )
        val previous = resultWebResults[project.summary.documentId]
        resultWebResults[project.summary.documentId] = saved.ref
        resultWebSuppressedProjects.remove(project.summary.documentId)
        return saved.changed || previous != saved.ref
    }

    private fun openResultWeb(
        projectName: String,
        result: ResultWebStore.ResultRef?,
    ) {
        if (result == null) return
        startActivity(
            Intent(this, ResultWebActivity::class.java).apply {
                putExtra(ResultWebActivity.EXTRA_RESULT_ID, result.id)
                putExtra(ResultWebActivity.EXTRA_PROJECT_NAME, projectName)
            },
        )
    }

    private fun mergeRichResult(stateKey: String, output: String): Boolean {
        val previous = richResults[stateKey]
        val detected = RichResultParser.parse(output)
        val merged = RichResultLifecyclePolicy.merge(previous, detected)
        if (merged == previous) return false
        if (merged == null) {
            richResults.remove(stateKey)
        } else {
            richResults[stateKey] = merged
        }
        return true
    }

    private fun openPresentation(
        projectKey: String,
        projectName: String,
        folderName: String,
        runtimeState: RuntimeState,
        webUrl: String?,
        webFramework: String?,
        richResult: RichResultDocument?,
        resultWeb: ResultWebStore.ResultRef?,
    ) {
        when (
            PresentationTargetResolver.resolve(
                webPresentationKnown = webUrl != null,
                richResultAvailable = richResult != null,
                resultWebAvailable = resultWeb != null,
            )
        ) {
            PresentationTarget.WEB -> openBrowserForProject(
                projectKey = projectKey,
                folderName = folderName,
                runtimeState = runtimeState,
                url = webUrl,
                framework = webFramework,
            )
            PresentationTarget.RESULT_WEB -> openResultWeb(projectName, resultWeb)
            PresentationTarget.RICH_RESULT -> openRichResultViewer(projectName, richResult)
            PresentationTarget.NONE -> Unit
        }
    }

    private fun openRichResultViewer(projectName: String, richResult: RichResultDocument?) {
        if (richResult == null || richResult.isEmpty) return
        startActivity(
            Intent(this, RichResultActivity::class.java).apply {
                putExtra(RichResultActivity.EXTRA_PROJECT_NAME, projectName)
                putStringArrayListExtra(
                    RichResultActivity.EXTRA_LABELS,
                    ArrayList(richResult.items.map { it.label }),
                )
                putStringArrayListExtra(
                    RichResultActivity.EXTRA_URLS,
                    ArrayList(richResult.items.map { it.url }),
                )
            },
        )
    }

    private fun updateEmbeddedRichResult(
        project: V04ProjectGateway.RuntimeProject,
        snapshot: EmbeddedPythonSnapshot,
    ): Boolean {
        val safeOutput = if (::secretStore.isInitialized) {
            secretStore.redactRuntimeText(project.folderName, snapshot.stdout)
        } else {
            snapshot.stdout
        }
        val changed = mergeRichResult(project.summary.documentId, safeOutput)
        if (changed && ::projectOutputs.isInitialized) {
            projectOutputs.collapse(project.folderName)
        }
        return changed
    }

    private fun openBrowserForProject(
        projectKey: String,
        folderName: String,
        runtimeState: RuntimeState,
        url: String?,
        framework: String?,
    ) {
        if (runtimeState != RuntimeState.RUNNING) {
            errorDialog(
                getString(R.string.runtime_web_not_running_title),
                getString(R.string.runtime_web_not_running_message),
            )
            return
        }
        if (url == null) {
            errorDialog(
                getString(R.string.runtime_web_not_found_title),
                getString(R.string.runtime_web_not_found_running),
            )
            return
        }
        if (!::webAvailability.isInitialized) return
        webAvailability.verifyNow(projectKey, url) { listening ->
            if (!listening) {
                errorDialog(
                    getString(R.string.runtime_web_not_listening_title),
                    getString(R.string.runtime_web_not_listening_message, url),
                )
                return@verifyNow
            }
            openBrowserUrl(projectKey, folderName, url, framework)
        }
    }

    private fun openBrowserFromRuntimeLogs(item: Pending, stdout: String) {
        val logUrl = RuntimeWebDiscoveryScopePolicy.candidateFromOutput(
            output = stdout,
            webCapabilityEnabled = item.webLogDiscoveryAllowed,
        )?.url
        val projectKey = item.documentId ?: item.folderName
        val stored = webStateStore.snapshot(projectKey)
        val storedUrl = stored.candidateUrl?.takeIf {
            RuntimeWebDiscoveryScopePolicy.canUseCandidate(
                webCapabilityEnabled = item.webLogDiscoveryAllowed,
                source = stored.source,
            )
        }
        val url = logUrl ?: storedUrl ?: item.browserConfiguredUrl
        if (url == null) {
            errorDialog(
                getString(R.string.runtime_web_not_found_title),
                getString(R.string.runtime_web_not_found_logs),
            )
            return
        }
        openBrowserForProject(
            projectKey = projectKey,
            folderName = item.folderName,
            runtimeState = RuntimeState.RUNNING,
            url = url,
            framework = item.browserFramework ?: stored.framework,
        )
    }

    private fun openBrowserUrl(projectKey: String, folderName: String, url: String, framework: String?) {
        val validatedUrl = RuntimeWebUrl.extractLocalHttpUrl("SIFTALPHA_WEB_URL=$url")
        if (validatedUrl == null) {
            errorDialog(
                getString(R.string.runtime_web_invalid_title),
                getString(R.string.runtime_web_invalid_message, url),
            )
            return
        }
        webStateStore.rememberCandidateUrl(
            projectKey = projectKey,
            url = validatedUrl,
            framework = framework,
            source = RuntimeWebCandidateSource.EXPLICIT,
        )

        val uri = Uri.parse(validatedUrl)
        val browsers = discoverInstalledBrowsers(uri)
        if (browsers.isEmpty()) {
            errorDialog(
                getString(R.string.runtime_no_browser_title),
                getString(R.string.runtime_no_browser_message, validatedUrl),
            )
            return
        }

        val selectedPackage = StudioBrowser.selectedPackage(this)
        val target = browsers.firstOrNull { it.packageName == selectedPackage }
        if (target == null) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_browser_required_title))
                .setMessage(getString(R.string.settings_browser_required_message))
                .setNegativeButton(getString(R.string.common_cancel), null)
                .setPositiveButton(getString(R.string.settings_title)) { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(target.packageName)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                errorDialog(
                    getString(R.string.runtime_browser_failed_title),
                    getString(
                        R.string.runtime_browser_failed_message,
                        target.label,
                        target.packageName,
                        validatedUrl,
                    ),
                )
            }
    }

    @Suppress("DEPRECATION")
    private fun discoverInstalledBrowsers(actualUri: Uri): List<BrowserTarget> {
        val candidates = linkedMapOf<String, BrowserTarget>()

        val probes = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com/")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            },
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            },
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
        )

        probes.forEach { probe ->
            packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
                .forEach { info ->
                    val packageName = info.activityInfo?.packageName ?: return@forEach
                    val label = runCatching { info.loadLabel(packageManager).toString().trim() }
                        .getOrNull()
                        .orEmpty()
                        .ifBlank { packageName }
                    candidates.putIfAbsent(packageName, BrowserTarget(label, packageName))
                }
        }

        return candidates.values
            .filter { candidate ->
                val actualIntent = Intent(Intent.ACTION_VIEW, actualUri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    setPackage(candidate.packageName)
                }
                packageManager.resolveActivity(actualIntent, PackageManager.MATCH_DEFAULT_ONLY) != null
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun send(command: RuntimeCommand, renderToSystemOutput: Boolean = true): Int? {
        if (!ensureRuntime()) return null
        return try {
            val id = backend.execute(command)
            if (renderToSystemOutput) {
                output.text = getString(R.string.runtime_command_sent, id)
            }
            id
        } catch (e: Throwable) {
            errorDialog(
                getString(R.string.runtime_command_send_failed),
                e.message ?: e.javaClass.simpleName,
            )
            null
        }
    }

    private fun formatResult(result: RuntimeResult): String = buildString {
        appendLine("executionId = ${result.executionId}")
        appendLine("exitCode = ${result.exitCode}")
        if (result.stdout.isNotBlank()) {
            appendLine("\n--- stdout ---")
            append(result.stdout.trimEnd())
        }
        if (result.stderr.isNotBlank()) {
            appendLine("\n\n--- stderr ---")
            append(result.stderr.trimEnd())
        }
        if (result.internalErrorMessage.isNotBlank()) {
            appendLine("\n\ntermuxError = ${result.internalErrorMessage}")
        }
    }

    private fun renderResult(result: RuntimeResult) {
        output.text = formatResult(result)
    }

    private fun renderProjectResult(
        folderName: String,
        result: RuntimeResult,
        expand: Boolean = true,
    ) {
        projectOutputs.write(folderName, formatResult(result), expand = expand)
    }

    private fun runtimeError(result: RuntimeResult) {
        val text = (result.stdout + "\n" + result.stderr).trim()
        val message = when {
            "SIFTALPHA_ERROR=SHARED_STORAGE_UNAVAILABLE" in text ->
                getString(R.string.runtime_error_shared_storage)
            "SIFTALPHA_ERROR=PROOT_DISTRO_MISSING" in text ->
                getString(R.string.runtime_error_proot_missing)
            "SIFTALPHA_ERROR=PYTHON_MISSING" in text ->
                getString(R.string.runtime_error_python_missing)
            "SIFTALPHA_ERROR=PYTHON_CONSOLE_SCRIPT_MISSING" in text ->
                getString(R.string.runtime_error_python_console_script_missing)
            "SIFTALPHA_ERROR=ENV_NOT_READY" in text ->
                getString(R.string.runtime_error_env_not_ready)
            "SIFTALPHA_ERROR=TMUX_MISSING" in text ->
                getString(R.string.runtime_error_tmux_missing)
            "SIFTALPHA_ERROR=PROJECT_EXISTS" in text ->
                getString(R.string.runtime_error_project_exists)
            "SIFTALPHA_ERROR=SECRET_PAYLOAD_INVALID" in text ->
                getString(R.string.runtime_error_secret_invalid)
            "SIFTALPHA_ERROR=SECRET_DECODE_FAILED" in text ->
                getString(R.string.runtime_error_secret_decode)
            else -> text.takeLast(1600).ifBlank {
                getString(R.string.runtime_error_command_failed, result.exitCode)
            }
        }
        errorDialog(getString(R.string.runtime_operation_failed), message)
    }

    private fun chooseFile(isZip: Boolean) {
        if (gateway.rootUri() == null) {
            toast(getString(R.string.runtime_choose_root_first))
            return
        }
        val kind = if (isZip) "ZIP" else "PY"
        output.text = "IMPORT_STAGE=OPEN_PICKER\nIMPORT_KIND=$kind"
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            type = "*/*"
        }
        try {
            startActivityForResult(picker, if (isZip) REQUEST_ZIP else REQUEST_PY)
        } catch (e: Throwable) {
            output.text = "IMPORT_STAGE=PICKER_FAILED\nIMPORT_KIND=$kind\nERROR=${e.javaClass.simpleName}: ${e.message.orEmpty()}"
            errorDialog(
                getString(R.string.runtime_picker_open_failed),
                e.message ?: e.javaClass.simpleName,
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PY && requestCode != REQUEST_ZIP) return
        if (resultCode != RESULT_OK) {
            if (::output.isInitialized) output.text = "IMPORT_STAGE=PICKER_CANCELLED"
            return
        }
        try {
            val uri = data?.data ?: error(getString(R.string.runtime_picker_missing_uri))
            val grantedFlags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if ((data.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0 && grantedFlags != 0) {
                runCatching { contentResolver.takePersistableUriPermission(uri, grantedFlags) }
            }
            val isZip = requestCode == REQUEST_ZIP
            val filename = displayName(uri) ?: if (isZip) "project.zip" else "script.py"
            output.text = "IMPORT_STAGE=FILE_SELECTED\nIMPORT_KIND=${if (isZip) "ZIP" else "PY"}\nFILE=$filename"
            showImportDialog(uri, isZip, filename)
        } catch (e: Throwable) {
            val message = "${e.javaClass.simpleName}: ${e.message ?: getString(R.string.runtime_unknown_error)}"
            if (::output.isInitialized) output.text = "IMPORT_STAGE=FILE_RESULT_FAILED\nERROR=$message"
            errorDialog(getString(R.string.runtime_picker_read_failed), message)
        }
    }

    private fun showImportDialog(uri: Uri, zip: Boolean, knownFilename: String? = null) {
        val filename = knownFilename ?: displayName(uri) ?: if (zip) "project.zip" else "script.py"
        if (zip && !filename.lowercase().endsWith(".zip")) {
            errorDialog(
                getString(R.string.runtime_choose_zip_title),
                getString(R.string.runtime_current_selection, filename),
            )
            return
        }
        if (!zip && !filename.lowercase().endsWith(".py")) {
            errorDialog(
                getString(R.string.runtime_choose_py_title),
                getString(R.string.runtime_current_selection, filename),
            )
            return
        }
        val base = if (zip) filename.substringBeforeLast('.') else filename.removeSuffix(".py")
        val name = EditText(this).apply {
            setText(suggestName(base))
            setSelection(text.length)
            setSingleLine(true)
        }
        val importLabel = getString(R.string.runtime_import_action)
        AlertDialog.Builder(this)
            .setTitle(
                getString(
                    if (zip) R.string.runtime_import_zip_title else R.string.runtime_import_py_title,
                ),
            )
            .setMessage(getString(R.string.runtime_import_message, filename))
            .setView(name)
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(importLabel) { _, _ ->
                safeUiAction(importLabel) {
                    importLocal(uri, filename, name.text.toString().trim(), zip)
                }
            }
            .show()
    }

    private fun importLocal(uri: Uri, filename: String, projectName: String, zip: Boolean) {
        if (!PROJECT_NAME.matches(projectName)) {
            errorDialog(
                getString(R.string.runtime_project_name_invalid),
                getString(R.string.runtime_project_name_rule),
            )
            return
        }
        output.text = buildString {
            appendLine("IMPORT_STAGE=IMPORTING")
            appendLine("IMPORT_KIND=${if (zip) "ZIP" else "PY"}")
            appendLine("FILE=$filename")
            append("PROJECT=$projectName")
        }
        Thread {
            try {
                val project = if (zip) {
                    gateway.importZip(uri, projectName, filename)
                } else {
                    gateway.importPython(uri, projectName, filename)
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    output.text = buildString {
                        appendLine("IMPORT_STAGE=COMPLETE")
                        appendLine("PROJECT=${project.summary.name}")
                        appendLine("ENTRY=${project.summary.entry}")
                        append("SOURCE=${project.summary.source}")
                    }
                    toast(getString(R.string.runtime_project_imported, project.summary.name))
                    refresh(forceProjectInspection = true)
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val message = "${e.javaClass.simpleName}: ${e.message ?: getString(R.string.runtime_unknown_error)}"
                    output.text = "IMPORT_STAGE=FAILED\nERROR=$message"
                    errorDialog(getString(R.string.runtime_import_failed), message)
                }
            }
        }.start()
    }

    private fun showGitHubDialog() {
        if (!ensureRuntime()) return
        val url = EditText(this).apply {
            hint = "https://github.com/owner/repository"
            setSingleLine(true)
        }
        val branch = EditText(this).apply {
            setText("main")
            hint = getString(R.string.runtime_github_branch_hint)
            setSingleLine(true)
        }
        val name = EditText(this).apply {
            hint = getString(R.string.runtime_github_name_hint)
            setSingleLine(true)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), 0)
            addView(url)
            addView(branch)
            addView(name)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.runtime_github_title))
            .setMessage(getString(R.string.runtime_github_message))
            .setView(layout)
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.runtime_github_start)) { _, _ ->
                try {
                    val spec = parseGitHub(url.text.toString(), branch.text.toString(), name.text.toString())
                    require(!gateway.folderExists(spec.projectName)) {
                        getString(R.string.runtime_github_project_exists, spec.projectName)
                    }
                    val id = send(runtime.cloneGitHub(spec)) ?: return@setPositiveButton
                    states[spec.projectName] = getString(R.string.runtime_github_importing)
                    output.text = getString(
                        R.string.runtime_github_importing_detail,
                        spec.sourceUrl,
                        spec.branch,
                    )
                    registerPending(
                        id,
                        Pending(
                            action = ProjectRuntimeController.Action.CLONE_GITHUB,
                            folderName = spec.projectName,
                            cloneSpec = spec,
                        ),
                    )
                } catch (e: Throwable) {
                    errorDialog(
                        getString(R.string.runtime_github_parameters_invalid),
                        e.message ?: getString(R.string.runtime_github_cannot_import),
                    )
                }
            }
            .show()
    }

    private fun parseGitHub(
        raw: String,
        rawBranch: String,
        rawName: String,
    ): ProjectRuntimeController.GitHubCloneSpec = SharedGitHubImportService.parse(
        raw = raw,
        rawBranch = rawBranch,
        rawName = rawName,
    ) { reason ->
        when (reason) {
            SharedGitHubImportService.ParseError.ENTER_ADDRESS ->
                getString(R.string.runtime_github_enter_address)
            SharedGitHubImportService.ParseError.ONLY_GITHUB_SUPPORTED ->
                getString(R.string.runtime_github_only_supported)
            SharedGitHubImportService.ParseError.ADDRESS_RULE ->
                getString(R.string.runtime_github_address_rule)
            SharedGitHubImportService.ParseError.PROJECT_NAME_RULE ->
                getString(R.string.runtime_project_name_rule)
        }
    }

    private fun attachCloneMetadata(spec: ProjectRuntimeController.GitHubCloneSpec) {
        output.append("\n\n${getString(R.string.runtime_registering_source)}")
        Thread {
            val attached = runCatching {
                SharedGitHubImportService.attachMetadata(gateway, spec)
            }
            runOnUiThread {
                val project = attached.getOrNull()
                if (project != null) {
                    val stateKey = project.summary.documentId
                    states[stateKey] = getString(R.string.runtime_state_not_checked)
                    typedStates[stateKey] = RuntimeState.UNKNOWN
                    clearEnvironmentStates(stateKey)
                    output.append("\n${getString(R.string.runtime_import_completed, spec.sourceUrl)}")
                    toast(getString(R.string.runtime_project_imported, spec.projectName))
                } else {
                    output.append(
                        "\n${getString(
                            R.string.runtime_source_metadata_failed,
                            attached.exceptionOrNull()?.message ?: getString(R.string.runtime_unknown_error),
                        )}",
                    )
                }
                refresh(forceProjectInspection = project != null)
            }
        }.start()
    }

    private fun ensureExternalProviderReady(
        project: V04ProjectGateway.RuntimeProject,
        action: ExternalActionGate.Action,
        deferred: DeferredManualAction,
    ): Boolean {
        val projectId = project.summary.documentId
        val decision = externalActionGate.request(
            projectId = projectId,
            action = action,
            origin = ExternalActionGate.Origin.DEVELOPER_MODE,
        )
        if (decision is ExternalActionGate.Decision.Proceed) {
            externalGateDeferredActions.remove(projectId)
            return true
        }

        val request = (decision as? ExternalActionGate.Decision.Awaiting)?.request
        if (request != null) {
            externalGateDeferredActions[projectId] = request.generation to deferred
        } else {
            externalGateDeferredActions.remove(projectId)
        }

        val result = externalPreflight.current()
        when (result.readiness) {
            ExternalProviderReadiness.TERMUX_NOT_INSTALLED,
            ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED,
            -> AlertDialog.Builder(this)
                .setTitle(getString(R.string.runtime_termux_missing_title))
                .setMessage(
                    result.detail ?: getString(R.string.runtime_termux_missing_message),
                )
                .setNegativeButton(getString(R.string.common_cancel), null)
                .setPositiveButton(getString(R.string.home_open_termux)) { _, _ -> openTermux() }
                .show()

            ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED -> requestPermissions(
                arrayOf(TermuxContract.RUN_COMMAND_PERMISSION),
                REQUEST_RUN_COMMAND,
            )

            ExternalProviderReadiness.BRIDGE_CHECKING,
            ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED,
            -> toast(getString(R.string.normal_external_provider_checking))

            ExternalProviderReadiness.BRIDGE_UNRESPONSIVE -> AlertDialog.Builder(this)
                .setTitle(getString(R.string.runtime_termux_missing_title))
                .setMessage(getString(R.string.normal_external_provider_no_response))
                .setNegativeButton(getString(R.string.common_cancel), null)
                .setPositiveButton(getString(R.string.home_open_termux)) { _, _ -> openTermux() }
                .show()

            ExternalProviderReadiness.UNAVAILABLE -> errorDialog(
                getString(R.string.runtime_termux_missing_title),
                result.detail ?: getString(R.string.normal_external_provider_unavailable),
            )

            ExternalProviderReadiness.READY -> Unit
        }
        return false
    }

    private fun resumeExternalActionGate() {
        if (!::externalActionGate.isInitialized) return
        val ready = externalActionGate.claimReady(ExternalActionGate.Origin.DEVELOPER_MODE)
        ready.forEach { request ->
            val deferredEntry = externalGateDeferredActions.remove(request.projectId)
                ?: return@forEach
            if (deferredEntry.first != request.generation) return@forEach
            val deferred = deferredEntry.second
            dispatch(
                project = deferred.project,
                action = deferred.action,
                openBrowserAfterLogs = deferred.openBrowserAfterLogs,
                browserConfiguredUrl = deferred.browserConfiguredUrl,
                browserFramework = deferred.browserFramework,
                silentRecovery = deferred.silentRecovery,
                controlRequest = deferred.controlRequest,
                launchInvocation = deferred.launchInvocation,
                webLogDiscoveryAllowed = deferred.webLogDiscoveryAllowed,
                webHintPorts = deferred.webHintPorts,
            )
        }
    }

    private fun openTermux() {
        val launch = packageManager.getLaunchIntentForPackage(TermuxContract.PACKAGE_NAME)
        if (launch != null) {
            startActivity(launch)
        } else {
            toast(getString(R.string.home_no_launchable_termux))
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RUN_COMMAND) {
            externalPreflight.probe()
            refresh()
        }
    }

    private fun ensureRuntime(requireExternalProvider: Boolean = true): Boolean {
        if (gateway.rootUri() == null) {
            toast(getString(R.string.runtime_choose_root_first))
            return false
        }
        if (!requireExternalProvider) return true
        if (!runtime.runtimeSupported()) {
            errorDialog(
                getString(R.string.runtime_current_directory_unsupported),
                getString(R.string.runtime_current_directory_unsupported_message),
            )
            return false
        }
        if (!backend.isTermuxInstalled()) {
            errorDialog(
                getString(R.string.runtime_termux_missing_title),
                getString(R.string.runtime_termux_missing_message),
            )
            return false
        }
        if (!backend.hasRunCommandPermission()) {
            errorDialog(
                getString(R.string.runtime_permission_missing_title),
                getString(R.string.runtime_permission_missing_message),
            )
            return false
        }
        return true
    }

    private fun displayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun suggestName(raw: String): String {
        val value = raw.trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-', '.', '_')
            .take(60)
        return value.ifBlank { "imported-project" }
    }

    private fun openSource(url: String?) {
        if (url.isNullOrBlank()) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure {
                errorDialog(
                    getString(R.string.runtime_open_source_failed),
                    it.message ?: url,
                )
            }
    }

    private fun appVersionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
            .ifBlank { "?" }

    private fun safeUiAction(label: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Throwable) {
            val message = "${e.javaClass.simpleName}: ${e.message ?: getString(R.string.runtime_unknown_error)}"
            if (::output.isInitialized) output.text = "UI_ACTION_FAILED=$label\nERROR=$message"
            errorDialog(getString(R.string.runtime_ui_action_failed, label), message)
        }
    }

    private fun errorDialog(title: String, message: String) {
        if (isFinishing || isDestroyed) return
        runCatching {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.common_confirm), null)
                .show()
        }.onFailure {
            runCatching { toast("$title: $message") }
        }
    }

    private fun section(value: String) = text(value, 16f, true).apply {
        setTextColor(Color.WHITE)
        setPadding(0, dp(20), 0, dp(8))
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { safeUiAction(label, action) }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(6) }
    }

    private fun smallButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11.5f
        isAllCaps = false
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(6), dp(6), dp(6), dp(6))
        setOnClickListener { safeUiAction(label, action) }
    }

    private fun weight() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun text(value: String, size: Float, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        gravity = Gravity.START
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun hint(value: String) = text(value, 12.5f, false).apply {
        setTextColor(Color.rgb(160, 166, 178))
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PROJECT_DOCUMENT_ID = "project_document_id"
        private const val REQUEST_PY = 801
        private const val REQUEST_ZIP = 802
        private const val REQUEST_RUN_COMMAND = 803
        private val PROJECT_NAME = Regex("^[A-Za-z0-9._-]+$")
        private val PENDING_TASKS = mutableMapOf<Int, Pending>()
        private val RUNTIME_STATES = mutableMapOf<String, String>()
        private val RUNTIME_TYPED_STATES = mutableMapOf<String, RuntimeState>()
        private val RUNTIME_ENVIRONMENT_READY = mutableMapOf<String, Boolean>()
        private val ACTIVE_RUNTIME_STATES = setOf(
            RuntimeState.PREPARING,
            RuntimeState.STARTING,
            RuntimeState.RUNNING,
        )
        private const val REFRESH_DEBOUNCE_MS = 120L
        private const val EMBEDDED_POLL_INTERVAL_MS = 180L
        private const val EMBEDDED_OUTPUT_RENDER_INTERVAL_MS = 750L
        private const val EXTERNAL_OBSERVATION_INTERVAL_MS = 2_000L
        private const val EXTERNAL_WEB_LOG_PROBE_EVERY_STATUS =
            RuntimeWebDetectionCadence.FAST_EXTERNAL_WEB_LOG_STATUS_INTERVAL
        private const val EXTERNAL_WEB_LOG_PROBE_MAX = 3
        private var RUNTIME_STATES_LANGUAGE_TAG: String? = null
    }
}
