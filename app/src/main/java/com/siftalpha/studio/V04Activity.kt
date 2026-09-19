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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.siftalpha.studio.project.EmbeddedPythonProjectStager
import com.siftalpha.studio.project.ProjectConfigurationInspector
import com.siftalpha.studio.project.ProjectSecretPolicyInspector
import com.siftalpha.studio.project.V04ProjectGateway
import com.siftalpha.studio.project.WebProjectInspector
import com.siftalpha.studio.presentation.ProjectActionPolicy
import com.siftalpha.studio.presentation.ProjectUiSnapshot
import com.siftalpha.studio.runtime.EmbeddedPythonRuntimeStateMapping
import com.siftalpha.studio.runtime.EmbeddedPythonObservationPolicy
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
import com.siftalpha.studio.runtime.RuntimeKind
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
import com.siftalpha.studio.runtime.RuntimeWebDiscoveryScopePolicy
import com.siftalpha.studio.runtime.RuntimeWebStateStore
import com.siftalpha.studio.runtime.RuntimeWebUiStatus
import com.siftalpha.studio.runtime.RuntimeWebUrl
import com.siftalpha.studio.runtime.RichResultDocument
import com.siftalpha.studio.runtime.RichResultLifecyclePolicy
import com.siftalpha.studio.runtime.RichResultParser
import com.siftalpha.studio.runtime.TermuxBackend
import com.siftalpha.studio.siftalphax.EmbeddedPythonEnvironmentManager
import com.siftalpha.studio.siftalphax.EmbeddedPythonSession
import com.siftalpha.studio.siftalphax.InternalAlpineEnvironmentManager
import com.siftalpha.studio.siftalphax.InternalAlpineSession
import com.siftalpha.studio.siftalphax.EmbeddedPythonSnapshot
import com.siftalpha.studio.siftalphax.EmbeddedPythonState
import com.siftalpha.studio.runtime.TermuxResultBus
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
        val configuredLocalUrl: String?,
        var finalLogsRequested: Boolean = false,
        var finalLogsCompleted: Boolean = false,
        var webLogProbeCount: Int = 0,
        var statusesSinceWebLogProbe: Int = 0,
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
    private lateinit var webAvailability: RuntimeWebAvailabilityTracker
    private lateinit var projectOutputs: ProjectOutputPanelController
    private lateinit var prepareLiveProgress: PrepareLiveProgressController
    private lateinit var lifecycleStore: RuntimeLifecycleStore
    private lateinit var projectRuntimeSelectionStore: ProjectRuntimeSelectionStore
    private val recoveryProjects = mutableSetOf<String>()
    private val failureReasons = mutableMapOf<String, String>()
    /** Activity-lifetime Rich Result cache; raw output remains owned by ProjectOutputPanelController. */
    private val richResults = mutableMapOf<String, RichResultDocument>()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshExecutor = Executors.newSingleThreadExecutor()
    private var refreshScheduled = false
    private var refreshInFlight = false
    private var refreshGeneration = 0L
    private var activityStarted = false
    private val externalObservations = mutableMapOf<String, ExternalObservation>()
    private val externalObservationGenerations = mutableMapOf<String, Long>()
    private val externalObservationRunnables = mutableMapOf<String, Runnable>()
    private val deferredManualActions = mutableMapOf<String, DeferredManualAction>()
    private val embeddedStartExecutor = Executors.newSingleThreadExecutor()
    private val embeddedStartInFlight = mutableSetOf<String>()
    private val projectActivities = ProjectActivityRegistry()
    private val externalActivityTokens = mutableMapOf<Int, ProjectActivityRegistry.Token>()
    private val cancelledExternalExecutions = mutableSetOf<Int>()
    private val embeddedLastSnapshots = mutableMapOf<String, EmbeddedPythonSnapshot>()
    private val embeddedRuntimeOwnership = mutableMapOf<String, RuntimeOwnership>()
    private var embeddedPollProject: V04ProjectGateway.RuntimeProject? = null
    private val embeddedPollRunnable = object : Runnable {
        override fun run() {
            val project = embeddedPollProject ?: return
            if (!activityStarted || isFinishing || isDestroyed) return
            val stateKey = project.summary.documentId
            if (embeddedRuntimeOwnership[stateKey] == RuntimeOwnership.EXTERNAL_PROVIDER) {
                embeddedPollProject = null
                return
            }
            val snapshot = runCatching {
                runtime.embeddedPythonSnapshotFor(project.summary.documentId)
            }.getOrNull() ?: return
            val changed = syncEmbeddedSnapshot(project, snapshot)
            if (changed) refresh()
            if (isEmbeddedActive(snapshot)) {
                refreshHandler.postDelayed(this, EMBEDDED_POLL_INTERVAL_MS)
            } else {
                embeddedPollProject = null
            }
        }
    }
    private lateinit var rootState: TextView
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
            internalAlpineSession = InternalAlpineSession(this),
        )
        secretStore = ProjectSecretStore(this)
        lifecycleStore = RuntimeLifecycleStore(this)
        secretPolicyInspector = ProjectSecretPolicyInspector(this)
        configurationInspector = ProjectConfigurationInspector(this)
        webInspector = WebProjectInspector(this)
        webStateStore = RuntimeWebStateStore(this)
        webAvailability = RuntimeWebAvailabilityTracker {
            if (!isFinishing && !isDestroyed && ::projectList.isInitialized) {
                refresh()
            }
        }
        projectOutputs = ProjectOutputPanelController(this)
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
        if (::prepareLiveProgress.isInitialized) prepareLiveProgress.resume()
        pending.keys.toList().forEach { id ->
            TermuxResultBus.consume(id)?.let(resultListener)
        }
        recoverPersistedRuntimeStates()
        resumeExternalObservations()
    }

    override fun onResume() {
        super.onResume()
        if (::projectList.isInitialized) refresh()
        embeddedPollProject?.let(::scheduleEmbeddedPolling)
        resumeExternalObservations()
    }

    override fun onStop() {
        activityStarted = false
        refreshHandler.removeCallbacks(embeddedPollRunnable)
        // Observation state is retained, but all delayed UI work is paused with the Activity.
        // The runtime itself remains owned by Termux and continues running.
        refreshHandler.removeCallbacksAndMessages(null)
        refreshScheduled = false
        if (::webAvailability.isInitialized) webAvailability.pause()
        if (::prepareLiveProgress.isInitialized) prepareLiveProgress.pause()
        TermuxResultBus.removeListener(resultListener)
        super.onStop()
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacksAndMessages(null)
        embeddedPollProject = null
        externalObservations.keys.toList().forEach(::invalidateExternalObservation)
        externalObservationRunnables.clear()
        deferredManualActions.clear()
        embeddedStartExecutor.shutdownNow()
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
            root.addView(button(getString(R.string.runtime_center_refresh)) { refresh() }.apply {
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

    private fun refresh() {
        if (!::projectList.isInitialized) return

        // Coalesce onStart/onResume/result/probe callbacks and keep the currently rendered
        // workspace visible while SAF/configuration reads happen off the main thread.
        refreshGeneration += 1
        if (refreshScheduled || refreshInFlight) return
        refreshScheduled = true
        val selectedId = selectedProjectDocumentId
        refreshHandler.postDelayed({
            refreshScheduled = false
            val requestGeneration = refreshGeneration
            refreshInFlight = true
            refreshExecutor.execute {
                val result = runCatching {
                    loadRefreshResult(selectedId)
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

    private fun loadRefreshResult(selectedId: String?): ProjectRefreshResult {
        val rootSelected = gateway.rootUri() != null
        if (!rootSelected) {
            return ProjectRefreshResult(
                rootSelected = false,
                runtimeSupported = false,
            )
        }

        val runtimeSupported = runCatching { runtime.runtimeSupported() }.getOrDefault(false)
        return try {
            val projects = gateway.projects().let { allProjects ->
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
                    ),
                    webProfile = runCatching {
                        webInspector.inspect(project.summary.documentId)
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
        val candidateWebUrls = listOfNotNull(webSnapshot.candidateUrl, configuredWebUrl).distinct()
        val endpointReachable = if (::webAvailability.isInitialized) {
            webAvailability.endpointReachable(stateKey, typedState, candidateWebUrls)
        } else {
            null
        }
        val reachableWebUrl = if (::webAvailability.isInitialized) {
            webAvailability.reachableUrl(stateKey, typedState, candidateWebUrls)
        } else {
            null
        }
        val reachableWebFramework = when (reachableWebUrl) {
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
            reachableUrl = reachableWebUrl,
            framework = reachableWebFramework,
        )
        val webUiStatus = web.status
        val richResult = richResults[stateKey]
        val presentationTarget = PresentationTargetResolver.resolve(
            webAvailable = reachableWebUrl != null,
            richResultAvailable = richResult != null,
        )
        val pendingItem = pending.values.firstOrNull { item ->
            (item.documentId == summary.documentId ||
                (item.documentId == null && item.folderName == project.folderName)) &&
                ObservationPresentationPolicy.isUserVisiblePending(item.automaticObservation)
        }
        val deferredManualAction = deferredManualActions[stateKey]
        val internalCleanPending = ProjectRuntimeController.Action.CLEAN.takeIf {
            ProjectActivityRegistry.Kind.CLEAN in projectActivities.activeKinds(stateKey)
        }
        val visiblePendingAction = pendingItem?.action ?: deferredManualAction?.action ?: internalCleanPending
        val pendingExecutionId = pendingItem?.let { visibleItem ->
            pending.entries.firstOrNull { it.value === visibleItem }?.key
        }
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
            states[stateKey] != null && snapshot.lifecycleState == RuntimeLifecycleState.DETECTING ->
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
                webUrl = reachableWebUrl,
                webFramework = reachableWebFramework,
                richResult = richResult,
            )
        }.apply {
            isEnabled = when (presentationTarget) {
                PresentationTarget.WEB -> policy.isEnabled(ProjectActionPolicy.Action.OPEN_BROWSER)
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
        val selection = projectRuntimeSelectionStore.read(stateKey)
        if (
            !activityStarted &&
            selection == ProjectRuntimeSelection.TERMUX &&
            cached.runtimeState in ACTIVE_RUNTIME_STATES
        ) {
            recoveryProjects += stateKey
        } else if (selection == ProjectRuntimeSelection.EMBEDDED_R) {
            recoveryProjects.remove(stateKey)
        }
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

    private fun confirmPrepare(project: V04ProjectGateway.RuntimeProject) {
        val controlRequest = selectedRuntimeControlRequest(project)
        if (controlRequest == RuntimeControlRequest.EXTERNAL_PROVIDER && !ensureRuntime()) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.runtime_prepare_title, project.summary.name))
            .setMessage(getString(R.string.runtime_prepare_message))
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.runtime_prepare_start)) { _, _ ->
                dispatch(
                    project = project,
                    action = ProjectRuntimeController.Action.PREPARE,
                    controlRequest = controlRequest,
                )
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

    private fun startProject(
        project: V04ProjectGateway.RuntimeProject,
        webProfile: WebProjectInspector.Profile? = null,
        controlRequest: RuntimeControlRequest? = null,
        launchInvocation: PythonLaunchInvocation? = null,
    ) {
        val profile = webProfile ?: runCatching {
            webInspector.inspect(project.summary.documentId)
        }.getOrNull()
        dispatch(
            project = project,
            action = ProjectRuntimeController.Action.START,
            browserConfiguredUrl = profile?.configuredLocalUrl(),
            browserFramework = profile?.framework,
            controlRequest = controlRequest ?: selectedRuntimeControlRequest(project),
            launchInvocation = launchInvocation,
            webLogDiscoveryAllowed = profile?.enabled == true,
        )
    }

    private fun confirmRun(project: V04ProjectGateway.RuntimeProject) {
        val controlRequest = selectedRuntimeControlRequest(project)
        if (!ensureRuntime(requireExternalProvider = controlRequest == RuntimeControlRequest.EXTERNAL_PROVIDER)) {
            return
        }
        val webProfile = runCatching { webInspector.inspect(project.summary.documentId) }.getOrNull()
        val resolvedSelection =
            project.runtimeSelection as? ProjectRuntimeExecutionPlanner.Selection.Resolved
        val isPythonCliCandidate =
            controlRequest == RuntimeControlRequest.EXTERNAL_PROVIDER &&
                webProfile != null &&
                !webProfile.enabled &&
                resolvedSelection?.primary == RuntimeKind.PYTHON

        if (isPythonCliCandidate) {
            val cliWebProfile = checkNotNull(webProfile)
            val resolution = runCatching {
                runtime.resolvePythonLaunch(project)
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
                        )
                    } else {
                        showPythonConsoleScriptSelection(project, cliWebProfile, controlRequest, resolution.names)
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
    ) {
        val input = EditText(this).apply {
            hint = getString(R.string.runtime_cli_arguments_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.runtime_cli_arguments))
            .setMessage(getString(R.string.runtime_cli_entry_label, entryLabel))
            .setView(input)
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.runtime_button_run)) { _, _ ->
                when (val parsed = RuntimeArgumentParser.parse(input.text?.toString().orEmpty())) {
                    is RuntimeArgumentParser.Result.Success -> {
                        val invocation = runCatching {
                            invocationFactory(parsed.arguments)
                        }.getOrElse {
                            errorDialog(
                                getString(R.string.runtime_cli_unsupported),
                                it.message ?: getString(R.string.runtime_cli_unsupported),
                            )
                            return@setPositiveButton
                        }
                        startProject(project, webProfile, controlRequest, invocation)
                    }
                    is RuntimeArgumentParser.Result.Invalid ->
                        errorDialog(
                            getString(R.string.runtime_cli_arguments_invalid),
                            getString(R.string.runtime_cli_arguments_invalid),
                        )
                }
            }
            .show()
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
        embeddedStartInFlight += stateKey
        richResults.remove(stateKey)
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
                )
            }
            runOnUiThread {
                embeddedStartInFlight.remove(stateKey)
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

        val cancelledInternal = projectActivities.cancelProject(stateKey)
        projectPending.forEach { (executionId, _) ->
            externalActivityTokens.remove(executionId)
        }
        if (cancelledInternal > 0) {
            embeddedStartInFlight.remove(stateKey)
        }

        val embeddedSnapshot = runCatching {
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
        val accepted = runCatching {
            runtime.requestEmbeddedPythonStop(project.summary.documentId)
        }.getOrDefault(false)
        if (!accepted) {
            toast(getString(R.string.runtime_stop_failed))
            refreshEmbeddedProject(project)
            return
        }
        states[project.summary.documentId] = getString(R.string.runtime_action_stopping)
        projectOutputs.write(
            project.folderName,
            listOf(
                "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                "SIFTALPHA_X_PROJECT_ID=" + project.summary.documentId,
                "SIFTALPHA_X_STOP_REQUEST=ACCEPTED",
            ).joinToString("\n"),
            expand = true,
        )
        refresh()
        scheduleEmbeddedPolling(project)
    }

    private fun refreshEmbeddedProject(
        project: V04ProjectGateway.RuntimeProject,
        manualAction: EmbeddedPythonObservationPolicy.ManualAction? = null,
    ) {
        val stateKey = project.summary.documentId
        val checkedEnvironmentReady = if (
            manualAction == EmbeddedPythonObservationPolicy.ManualAction.STATUS
        ) {
            runCatching { runtime.embeddedPythonEnvironmentReady(project) }.getOrNull()
        } else {
            null
        }
        if (manualAction == EmbeddedPythonObservationPolicy.ManualAction.STATUS) {
            setEnvironmentReady(
                stateKey,
                ProjectRuntimeSelection.EMBEDDED_R,
                checkedEnvironmentReady,
            )
        }
        val snapshot = runCatching {
            runtime.embeddedPythonSnapshotFor(stateKey)
        }.getOrNull()
        if (snapshot == null) {
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
            refresh()
            return
        }
        if (!ownsEmbeddedObservation(stateKey, snapshot)) return
        if (embeddedRuntimeOwnership[stateKey] != RuntimeOwnership.EXTERNAL_PROVIDER) {
            embeddedRuntimeOwnership[stateKey] = RuntimeOwnershipPolicy.afterAcceptedStart(
                RuntimeControlRequest.EMBEDDED_R,
            )
        }
        val changed = syncEmbeddedSnapshot(project, snapshot, manualAction)
        if (manualAction == EmbeddedPythonObservationPolicy.ManualAction.STATUS) {
            persistRuntimeState(stateKey, ProjectRuntimeSelection.EMBEDDED_R)
        }
        if (changed) refresh()
        if (isEmbeddedActive(snapshot)) scheduleEmbeddedPolling(project)
    }

    private fun syncEmbeddedSnapshot(
        project: V04ProjectGateway.RuntimeProject,
        snapshot: EmbeddedPythonSnapshot,
        manualAction: EmbeddedPythonObservationPolicy.ManualAction? = null,
    ): Boolean {
        val stateKey = project.summary.documentId
        val previous = embeddedLastSnapshots[stateKey]
        if (!EmbeddedPythonObservationPolicy.shouldPresent(previous, snapshot, manualAction)) {
            return false
        }
        embeddedLastSnapshots[stateKey] = snapshot
        updateEmbeddedRichResult(project, snapshot)

        if (::webInspector.isInitialized && ::webStateStore.isInitialized) {
            val safeStdout = if (::secretStore.isInitialized) {
                secretStore.redactRuntimeText(project.folderName, snapshot.stdout)
            } else {
                snapshot.stdout
            }
            val webCapabilityEnabled = runCatching {
                webInspector.inspect(stateKey).enabled
            }.getOrDefault(false)
            RuntimeWebDiscoveryScopePolicy.candidateFromOutput(
                output = safeStdout,
                webCapabilityEnabled = webCapabilityEnabled,
            )?.let { candidate ->
                val existingWeb = webStateStore.snapshot(stateKey)
                webStateStore.rememberCandidateUrl(
                    projectKey = stateKey,
                    url = candidate.url,
                    framework = existingWeb.framework,
                    source = candidate.source,
                )
            }
        }

        val mappedState = EmbeddedPythonRuntimeStateMapping.toRuntimeState(snapshot)
        typedStates[stateKey] = mappedState
        states[stateKey] = mappedState.uiLabel(this)
        if (mappedState == RuntimeState.EXITED_ERROR && snapshot.stderr.isNotBlank()) {
            failureReasons[stateKey] = snapshot.stderr.trim().lineSequence().lastOrNull().orEmpty()
        } else {
            failureReasons.remove(stateKey)
        }
        if (::projectOutputs.isInitialized) {
            projectOutputs.write(
                project.folderName,
                EmbeddedPythonObservationPolicy.outputText(snapshot, manualAction),
                expand = true,
                forceFollowTail = isEmbeddedActive(snapshot),
            )
        }
        return true
    }

    private fun scheduleEmbeddedPolling(project: V04ProjectGateway.RuntimeProject) {
        embeddedPollProject = project
        if (!activityStarted) return
        refreshHandler.removeCallbacks(embeddedPollRunnable)
        refreshHandler.post(embeddedPollRunnable)
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
        if (embeddedPollProject?.summary?.documentId == projectDocumentId) {
            embeddedPollProject = null
            refreshHandler.removeCallbacks(embeddedPollRunnable)
        }
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
        if (!canDispatch(project, action, route.path)) return false
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
                    startEmbeddedProject(project, requiredConfiguration)
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
                        )
                    } else {
                        runtime.start(
                            project = project,
                            pythonLaunchInvocation = launchInvocation,
                            webLogDiscoveryAllowed = webLogDiscoveryAllowed,
                        )
                    }
                ProjectRuntimeController.Action.STOP -> runtime.stop(project)
                ProjectRuntimeController.Action.STATUS -> runtime.status(
                    project = project,
                    webLogDiscoveryAllowed = webLogDiscoveryAllowed,
                )
                ProjectRuntimeController.Action.LOGS -> runtime.logs(
                    project = project,
                    webLogDiscoveryAllowed = webLogDiscoveryAllowed,
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
            markExternalRuntimeOwner(stateKey)
            beginExternalObservation(
                project = project,
                webLogDiscoveryAllowed = webLogDiscoveryAllowed,
                configuredLocalUrl = browserConfiguredUrl,
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
                ProjectRuntimeController.Action.LOGS ->
                    states[stateKey] ?: getString(R.string.runtime_state_not_checked)
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
                if (!success) {
                    if (!item.automaticObservation) runtimeError(result)
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
        configuredLocalUrl: String? = null,
    ) {
        val stateKey = project.summary.documentId
        externalObservationRunnables[stateKey]?.let(refreshHandler::removeCallbacks)
        val generation = (externalObservationGenerations[stateKey] ?: 0L) + 1L
        externalObservationGenerations[stateKey] = generation
        externalObservations[stateKey] = ExternalObservation(
            project = project,
            generation = generation,
            webLogDiscoveryAllowed = webLogDiscoveryAllowed,
            configuredLocalUrl = configuredLocalUrl ?: runCatching {
                webInspector.inspect(stateKey).configuredLocalUrl()
            }.getOrNull(),
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
        delayMs: Long = EXTERNAL_OBSERVATION_INTERVAL_MS,
    ) {
        val stateKey = project.summary.documentId
        val observation = externalObservations[stateKey] ?: return
        if (!activityStarted || isFinishing || isDestroyed) return
        val runnable = externalObservationRunnables.getOrPut(stateKey) {
            Runnable { runExternalObservation(stateKey) }
        }
        refreshHandler.removeCallbacks(runnable)
        refreshHandler.postDelayed(runnable, delayMs.coerceAtLeast(0L))
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
        val candidateUrls = listOfNotNull(
            snapshot.candidateUrl,
            observation.configuredLocalUrl,
        ).distinct()
        val endpointVerified = ::webAvailability.isInitialized &&
            webAvailability.reachableUrl(
                projectKey = stateKey,
                runtimeState = typedStates[stateKey] ?: RuntimeState.UNKNOWN,
                candidateUrls = candidateUrls,
            ) != null
        return RuntimeWebObservationProbePolicy.shouldProbe(
            RuntimeWebObservationProbePolicy.Input(
                webLogDiscoveryAllowed = observation.webLogDiscoveryAllowed,
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
    ) {
        when (PresentationTargetResolver.resolve(webUrl != null, richResult != null)) {
            PresentationTarget.WEB -> openBrowserForProject(
                projectKey = projectKey,
                folderName = folderName,
                runtimeState = runtimeState,
                url = webUrl,
                framework = webFramework,
            )
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
    ) {
        val safeOutput = if (::secretStore.isInitialized) {
            secretStore.redactRuntimeText(project.folderName, snapshot.stdout)
        } else {
            snapshot.stdout
        }
        val changed = mergeRichResult(project.summary.documentId, safeOutput)
        if (changed && ::projectOutputs.isInitialized) {
            projectOutputs.collapse(project.folderName)
        }
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
                    refresh()
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
    ): ProjectRuntimeController.GitHubCloneSpec {
        val input = raw.trim()
        require(input.isNotBlank()) { getString(R.string.runtime_github_enter_address) }
        val repoPath: String
        val cloneUrl: String
        val sourceUrl: String
        when {
            input.startsWith("https://github.com/") -> {
                val clean = input.substringBefore('?').substringBefore('#').trimEnd('/')
                repoPath = clean.removePrefix("https://github.com/").removeSuffix(".git")
                cloneUrl = if (clean.endsWith(".git")) clean else "$clean.git"
                sourceUrl = "https://github.com/$repoPath"
            }
            input.startsWith("git@github.com:") -> {
                repoPath = input.removePrefix("git@github.com:").removeSuffix(".git").trim('/')
                cloneUrl = input
                sourceUrl = "https://github.com/$repoPath"
            }
            else -> error(getString(R.string.runtime_github_only_supported))
        }
        val parts = repoPath.trim('/').split('/').filter { it.isNotBlank() }
        require(parts.size == 2) { getString(R.string.runtime_github_address_rule) }
        val branch = rawBranch.trim().ifBlank { "main" }
        val projectName = rawName.trim().ifBlank { suggestName(parts.last()) }
        require(PROJECT_NAME.matches(projectName)) { getString(R.string.runtime_project_name_rule) }
        return ProjectRuntimeController.GitHubCloneSpec(cloneUrl, sourceUrl, branch, projectName)
    }

    private fun attachCloneMetadata(spec: ProjectRuntimeController.GitHubCloneSpec) {
        output.append("\n\n${getString(R.string.runtime_registering_source)}")
        Thread {
            var project: V04ProjectGateway.RuntimeProject? = null
            var last: Throwable? = null
            for (attempt in 0 until 8) {
                try {
                    project = gateway.attachGitHubSource(spec.projectName, spec.sourceUrl, spec.branch)
                    break
                } catch (e: Throwable) {
                    last = e
                    if (attempt < 7) Thread.sleep(250)
                }
            }
            runOnUiThread {
                if (project != null) {
                    val stateKey = project!!.summary.documentId
                    states[stateKey] = getString(R.string.runtime_state_not_checked)
                    typedStates[stateKey] = RuntimeState.UNKNOWN
                    clearEnvironmentStates(stateKey)
                    output.append("\n${getString(R.string.runtime_import_completed, spec.sourceUrl)}")
                    toast(getString(R.string.runtime_project_imported, spec.projectName))
                } else {
                    output.append(
                        "\n${getString(
                            R.string.runtime_source_metadata_failed,
                            last?.message ?: getString(R.string.runtime_unknown_error),
                        )}",
                    )
                }
                refresh()
            }
        }.start()
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
        private const val EXTERNAL_OBSERVATION_INTERVAL_MS = 2_000L
        private const val EXTERNAL_WEB_LOG_PROBE_EVERY_STATUS = 3
        private const val EXTERNAL_WEB_LOG_PROBE_MAX = 3
        private var RUNTIME_STATES_LANGUAGE_TAG: String? = null
    }
}
