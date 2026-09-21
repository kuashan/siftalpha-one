package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.V04ProjectGateway

/**
 * Normal Mode（普通模式） control boundary.
 *
 * This hub deliberately owns no Runtime implementation, UI state machine, observation loop,
 * Environment Plan or result presentation. It resolves the project's persisted Runtime selection
 * and delegates PREPARE / RUN / STOP to an injected executor that operates the already accepted Runtime
 * controller/backend.
 *
 * Developer Workspace（开发者工作区） and Normal Mode therefore control the same project Runtime
 * instead of one surface automating the other surface's buttons.
 */
class ProjectControlHub internal constructor(
    private val selectionReader: (String) -> ProjectRuntimeSelection,
    private val executor: Executor,
    private val stateBridge: StateBridge = StateBridge.NONE,
) {
    constructor(
        selectionStore: ProjectRuntimeSelectionStore,
        executor: Executor,
        stateBridge: StateBridge = StateBridge.NONE,
    ) : this(selectionStore::read, executor, stateBridge)

    data class RunRequest(
        val requiredConfiguration: Boolean = false,
        val launchInvocation: PythonLaunchInvocation? = null,
        val webLogDiscoveryAllowed: Boolean = false,
        val webHintPorts: List<Int> = emptyList(),
    )

    enum class Action {
        PREPARE,
        RUN,
        STOP,
    }

    enum class Failure {
        ENVIRONMENT_PLAN_BLOCKED,
        CONFIGURATION_REQUIRED,
        ROUTE_REJECTED,
        RUNTIME_BUSY,
        BACKEND_UNAVAILABLE,
        NO_ACTIVE_RUNTIME,
        EXECUTION_FAILED,
    }

    sealed interface Result {
        val action: Action

        data class Dispatched(
            override val action: Action,
            val provider: RuntimeOperationProvider,
            val executionId: Int? = null,
            val observedState: RuntimeState = RuntimeState.UNKNOWN,
        ) : Result

        data class Completed(
            override val action: Action,
            val provider: RuntimeOperationProvider,
            val environmentReady: Boolean,
            val observedState: RuntimeState = RuntimeState.UNKNOWN,
            val detail: String? = null,
        ) : Result

        data class NoOp(
            override val action: Action,
            val observedState: RuntimeState,
            val detail: String,
        ) : Result

        data class Rejected(
            override val action: Action,
            val failure: Failure,
            val detail: String,
        ) : Result
    }

    interface StateBridge {
        fun onActionStarted(
            project: V04ProjectGateway.RuntimeProject,
            selection: ProjectRuntimeSelection,
            action: Action,
        ) = Unit

        fun onActionResult(
            project: V04ProjectGateway.RuntimeProject,
            selection: ProjectRuntimeSelection,
            result: Result,
        ) = Unit

        companion object {
            val NONE = object : StateBridge {}
        }
    }

    interface Executor {
        fun prepare(
            project: V04ProjectGateway.RuntimeProject,
            selection: ProjectRuntimeSelection,
        ): Result

        fun run(
            project: V04ProjectGateway.RuntimeProject,
            selection: ProjectRuntimeSelection,
            request: RunRequest,
        ): Result

        fun stop(
            project: V04ProjectGateway.RuntimeProject,
            selection: ProjectRuntimeSelection,
        ): Result
    }

    fun prepare(project: V04ProjectGateway.RuntimeProject): Result {
        val selection = selectionReader(project.summary.documentId)
        stateBridge.onActionStarted(project, selection, Action.PREPARE)
        val result = executor.prepare(project, selection)
        stateBridge.onActionResult(project, selection, result)
        return result
    }

    fun run(
        project: V04ProjectGateway.RuntimeProject,
        request: RunRequest = RunRequest(),
    ): Result {
        val selection = selectionReader(project.summary.documentId)
        stateBridge.onActionStarted(project, selection, Action.RUN)
        val result = executor.run(project, selection, request)
        stateBridge.onActionResult(project, selection, result)
        return result
    }

    fun stop(project: V04ProjectGateway.RuntimeProject): Result {
        val selection = selectionReader(project.summary.documentId)
        stateBridge.onActionStarted(project, selection, Action.STOP)
        val result = executor.stop(project, selection)
        stateBridge.onActionResult(project, selection, result)
        return result
    }
}

/**
 * Production PREPARE / RUN / STOP executor for ProjectControlHub（项目控制枢纽）.
 *
 * The implementation delegates directly to the existing ProjectRuntimeController（项目运行控制器）
 * and RuntimeBackend（运行后端）. It does not depend on V04Activity（开发者运行中心页面） and never
 * starts a hidden Activity or simulates a UI click.
 */
class ProjectRuntimeControlExecutor(
    private val runtime: ProjectRuntimeController,
    private val externalBackend: RuntimeBackend,
) : ProjectControlHub.Executor {

    override fun prepare(
        project: V04ProjectGateway.RuntimeProject,
        selection: ProjectRuntimeSelection,
    ): ProjectControlHub.Result {
        val resolution = runCatching {
            runtime.detectEnvironment(
                project = project,
                resolveCompatibility = selection == ProjectRuntimeSelection.EMBEDDED_R,
            )
        }.getOrElse { error ->
            return executionFailure(ProjectControlHub.Action.PREPARE, error)
        }
        val plan = resolution.plan
        if (!plan.readyToPrepare) {
            return rejected(
                action = ProjectControlHub.Action.PREPARE,
                failure = ProjectControlHub.Failure.ENVIRONMENT_PLAN_BLOCKED,
                detail = plan.diagnosticLines().joinToString("\n"),
            )
        }

        val route = runCatching {
            runtime.resolveControlPath(
                project = project,
                action = ProjectRuntimeController.Action.PREPARE,
                request = selection.controlRequest,
            )
        }.getOrElse { error ->
            return executionFailure(ProjectControlHub.Action.PREPARE, error)
        }
        if (route.path == RuntimeControlPath.REJECTED) {
            return rejected(
                action = ProjectControlHub.Action.PREPARE,
                failure = ProjectControlHub.Failure.ROUTE_REJECTED,
                detail = route.reason.name,
            )
        }

        return when (route.path) {
            RuntimeControlPath.EMBEDDED_R -> runCatching {
                val prepared = runtime.prepareEmbeddedPythonEnvironment(project)
                val verified = prepared.ready && runtime.embeddedPythonEnvironmentReady(project)
                if (!verified) {
                    rejected(
                        action = ProjectControlHub.Action.PREPARE,
                        failure = ProjectControlHub.Failure.EXECUTION_FAILED,
                        detail = "Internal R environment verification failed",
                    )
                } else {
                    ProjectControlHub.Result.Completed(
                        action = ProjectControlHub.Action.PREPARE,
                        provider = RuntimeOperationProvider.INTERNAL,
                        environmentReady = true,
                        observedState = RuntimeState.UNKNOWN,
                        detail = prepared.backend.name + ":" + prepared.outcome.name,
                    )
                }
            }.getOrElse { error ->
                executionFailure(ProjectControlHub.Action.PREPARE, error)
            }

            RuntimeControlPath.EXTERNAL_PROVIDER -> {
                if (!externalBackend.isAvailable()) {
                    return rejected(
                        action = ProjectControlHub.Action.PREPARE,
                        failure = ProjectControlHub.Failure.BACKEND_UNAVAILABLE,
                        detail = runtime.runtimeUnsupportedReason(),
                    )
                }
                if (!plan.supports(EnvironmentBackend.EXTERNAL_PROVIDER)) {
                    return rejected(
                        action = ProjectControlHub.Action.PREPARE,
                        failure = ProjectControlHub.Failure.ENVIRONMENT_PLAN_BLOCKED,
                        detail = "Environment Plan does not allow External Provider preparation",
                    )
                }
                runCatching {
                    val command = runtime.prepare(project)
                    val managed = runtime.wrapCancelableExternalActivity(
                        project = project,
                        action = ProjectRuntimeController.Action.PREPARE,
                        command = command,
                    )
                    ProjectControlHub.Result.Dispatched(
                        action = ProjectControlHub.Action.PREPARE,
                        provider = RuntimeOperationProvider.EXTERNAL,
                        executionId = externalBackend.execute(managed),
                        observedState = RuntimeState.PREPARING,
                    )
                }.getOrElse { error ->
                    executionFailure(ProjectControlHub.Action.PREPARE, error)
                }
            }

            RuntimeControlPath.REJECTED -> error("handled above")
        }
    }

    override fun run(
        project: V04ProjectGateway.RuntimeProject,
        selection: ProjectRuntimeSelection,
        request: ProjectControlHub.RunRequest,
    ): ProjectControlHub.Result {
        if (request.requiredConfiguration) {
            return rejected(
                action = ProjectControlHub.Action.RUN,
                failure = ProjectControlHub.Failure.CONFIGURATION_REQUIRED,
                detail = "Required project configuration is incomplete",
            )
        }

        val route = runCatching {
            runtime.resolveControlPath(
                project = project,
                action = ProjectRuntimeController.Action.START,
                request = selection.controlRequest,
                requiredConfiguration = false,
            )
        }.getOrElse { error ->
            return executionFailure(ProjectControlHub.Action.RUN, error)
        }

        if (route.path == RuntimeControlPath.REJECTED) {
            return rejected(
                action = ProjectControlHub.Action.RUN,
                failure = ProjectControlHub.Failure.ROUTE_REJECTED,
                detail = route.reason.name,
            )
        }

        return when (route.path) {
            RuntimeControlPath.EMBEDDED_R -> runCatching {
                if (!runtime.embeddedPythonCanStart(project)) {
                    return rejected(
                        action = ProjectControlHub.Action.RUN,
                        failure = ProjectControlHub.Failure.RUNTIME_BUSY,
                        detail = "Internal R cannot accept another START",
                    )
                }
                val snapshot = runtime.startEmbeddedPython(
                    project = project,
                    requiredConfiguration = false,
                    pythonLaunchInvocation = request.launchInvocation,
                )
                ProjectControlHub.Result.Dispatched(
                    action = ProjectControlHub.Action.RUN,
                    provider = RuntimeOperationProvider.INTERNAL,
                    observedState = EmbeddedPythonRuntimeStateMapping.toRuntimeState(snapshot),
                )
            }.getOrElse { error ->
                executionFailure(ProjectControlHub.Action.RUN, error)
            }

            RuntimeControlPath.EXTERNAL_PROVIDER -> {
                if (!externalBackend.isAvailable()) {
                    return rejected(
                        action = ProjectControlHub.Action.RUN,
                        failure = ProjectControlHub.Failure.BACKEND_UNAVAILABLE,
                        detail = runtime.runtimeUnsupportedReason(),
                    )
                }
                runCatching {
                    val command = runtime.start(
                        project = project,
                        pythonLaunchInvocation = request.launchInvocation,
                        webLogDiscoveryAllowed = request.webLogDiscoveryAllowed,
                        webHintPorts = request.webHintPorts,
                    )
                    val managed = runtime.wrapCancelableExternalActivity(
                        project = project,
                        action = ProjectRuntimeController.Action.START,
                        command = command,
                    )
                    ProjectControlHub.Result.Dispatched(
                        action = ProjectControlHub.Action.RUN,
                        provider = RuntimeOperationProvider.EXTERNAL,
                        executionId = externalBackend.execute(managed),
                        observedState = RuntimeState.STARTING,
                    )
                }.getOrElse { error ->
                    executionFailure(ProjectControlHub.Action.RUN, error)
                }
            }

            RuntimeControlPath.REJECTED -> error("handled above")
        }
    }

    override fun stop(
        project: V04ProjectGateway.RuntimeProject,
        selection: ProjectRuntimeSelection,
    ): ProjectControlHub.Result {
        val route = runCatching {
            runtime.resolveControlPath(
                project = project,
                action = ProjectRuntimeController.Action.STOP,
                request = selection.controlRequest,
            )
        }.getOrElse { error ->
            return executionFailure(ProjectControlHub.Action.STOP, error)
        }

        if (route.path == RuntimeControlPath.EMBEDDED_R) {
            return runCatching {
                if (!runtime.requestEmbeddedPythonStop(project.summary.documentId)) {
                    rejected(
                        action = ProjectControlHub.Action.STOP,
                        failure = ProjectControlHub.Failure.NO_ACTIVE_RUNTIME,
                        detail = "Internal R has no stoppable project session",
                    )
                } else {
                    ProjectControlHub.Result.Dispatched(
                        action = ProjectControlHub.Action.STOP,
                        provider = RuntimeOperationProvider.INTERNAL,
                        observedState = RuntimeState.RUNNING,
                    )
                }
            }.getOrElse { error ->
                executionFailure(ProjectControlHub.Action.STOP, error)
            }
        }

        // An explicitly selected Internal R project with no active Internal session is already
        // stopped. Do not fall through and send an unrelated External Provider STOP.
        if (selection == ProjectRuntimeSelection.EMBEDDED_R) {
            return ProjectControlHub.Result.NoOp(
                action = ProjectControlHub.Action.STOP,
                observedState = RuntimeState.STOPPED_BY_USER,
                detail = "Internal R project has no active session",
            )
        }

        if (route.path == RuntimeControlPath.REJECTED) {
            return rejected(
                action = ProjectControlHub.Action.STOP,
                failure = ProjectControlHub.Failure.ROUTE_REJECTED,
                detail = route.reason.name,
            )
        }

        if (!externalBackend.isAvailable()) {
            return rejected(
                action = ProjectControlHub.Action.STOP,
                failure = ProjectControlHub.Failure.BACKEND_UNAVAILABLE,
                detail = runtime.runtimeUnsupportedReason(),
            )
        }

        return runCatching {
            val command = runtime.stop(project)
            ProjectControlHub.Result.Dispatched(
                action = ProjectControlHub.Action.STOP,
                provider = RuntimeOperationProvider.EXTERNAL,
                executionId = externalBackend.execute(command),
                observedState = RuntimeState.RUNNING,
            )
        }.getOrElse { error ->
            executionFailure(ProjectControlHub.Action.STOP, error)
        }
    }

    private fun rejected(
        action: ProjectControlHub.Action,
        failure: ProjectControlHub.Failure,
        detail: String,
    ): ProjectControlHub.Result.Rejected =
        ProjectControlHub.Result.Rejected(
            action = action,
            failure = failure,
            detail = detail,
        )

    private fun executionFailure(
        action: ProjectControlHub.Action,
        error: Throwable,
    ): ProjectControlHub.Result.Rejected =
        rejected(
            action = action,
            failure = ProjectControlHub.Failure.EXECUTION_FAILED,
            detail = error.message ?: error.javaClass.simpleName,
        )
}


/**
 * Writes only shared lifecycle facts needed for cross-surface recovery.
 *
 * START records an active state so the existing Developer Workspace（开发者工作区） recovery path
 * knows to reconcile the same Runtime（运行时） when it is opened later. STOP keeps an active
 * lifecycle until the provider proves a terminal state; this avoids falsely claiming that an
 * asynchronous stop has completed.
 */
class SharedRuntimeLifecycleBridge(
    private val store: RuntimeLifecycleStore,
) : ProjectControlHub.StateBridge {
    private val previousRunState = linkedMapOf<String, RuntimeLifecycleStore.Snapshot>()
    private val previousPrepareState = linkedMapOf<String, RuntimeLifecycleStore.Snapshot>()

    override fun onActionStarted(
        project: V04ProjectGateway.RuntimeProject,
        selection: ProjectRuntimeSelection,
        action: ProjectControlHub.Action,
    ) {
        val projectId = project.summary.documentId
        val current = store.read(projectId)
        when (action) {
            ProjectControlHub.Action.PREPARE -> {
                synchronized(previousPrepareState) {
                    previousPrepareState[projectId] = current
                }
                store.write(
                    projectKey = projectId,
                    environmentReady = current.environmentReadyFor(selection),
                    runtimeState = RuntimeState.PREPARING,
                    failureReason = null,
                    runtimeSelection = selection,
                )
            }
            ProjectControlHub.Action.RUN -> {
                synchronized(previousRunState) {
                    previousRunState[projectId] = current
                }
                store.write(
                    projectKey = projectId,
                    environmentReady = current.environmentReadyFor(selection),
                    runtimeState = RuntimeState.STARTING,
                    failureReason = null,
                    runtimeSelection = selection,
                )
            }
            ProjectControlHub.Action.STOP -> Unit
        }
    }

    override fun onActionResult(
        project: V04ProjectGateway.RuntimeProject,
        selection: ProjectRuntimeSelection,
        result: ProjectControlHub.Result,
    ) {
        val projectId = project.summary.documentId
        val current = store.read(projectId)
        when (result) {
            is ProjectControlHub.Result.Completed -> when (result.action) {
                ProjectControlHub.Action.PREPARE -> {
                    synchronized(previousPrepareState) {
                        previousPrepareState.remove(projectId)
                    }
                    store.write(
                        projectKey = projectId,
                        environmentReady = result.environmentReady,
                        runtimeState = if (result.environmentReady) {
                            result.observedState
                        } else {
                            RuntimeState.ENVIRONMENT_ERROR
                        },
                        failureReason = if (result.environmentReady) null else result.detail,
                        runtimeSelection = selection,
                    )
                }
                ProjectControlHub.Action.RUN,
                ProjectControlHub.Action.STOP,
                -> Unit
            }
            is ProjectControlHub.Result.Dispatched -> when (result.action) {
                ProjectControlHub.Action.PREPARE -> Unit
                ProjectControlHub.Action.RUN -> {
                    synchronized(previousRunState) {
                        previousRunState.remove(projectId)
                    }
                    store.write(
                    projectKey = projectId,
                    environmentReady = current.environmentReadyFor(selection),
                    runtimeState = if (result.observedState == RuntimeState.UNKNOWN) {
                        RuntimeState.STARTING
                    } else {
                        result.observedState
                    },
                    failureReason = null,
                    runtimeSelection = selection,
                )
                }
                ProjectControlHub.Action.STOP -> {
                    // STOP is asynchronous for both provider families. Keep the last active fact
                    // until a provider observation proves STOPPED / EXITED.
                    val state = current.runtimeState.takeIf {
                        it == RuntimeState.STARTING || it == RuntimeState.RUNNING
                    } ?: RuntimeState.RUNNING
                    store.write(
                        projectKey = projectId,
                        environmentReady = current.environmentReadyFor(selection),
                        runtimeState = state,
                        failureReason = null,
                        runtimeSelection = selection,
                    )
                }
            }
            is ProjectControlHub.Result.NoOp -> if (result.action == ProjectControlHub.Action.STOP) {
                store.write(
                    projectKey = projectId,
                    environmentReady = current.environmentReadyFor(selection),
                    runtimeState = result.observedState,
                    failureReason = null,
                    runtimeSelection = selection,
                )
            }
            is ProjectControlHub.Result.Rejected -> {
                val previous = when (result.action) {
                    ProjectControlHub.Action.PREPARE -> synchronized(previousPrepareState) {
                        previousPrepareState.remove(projectId)
                    }
                    ProjectControlHub.Action.RUN -> synchronized(previousRunState) {
                        previousRunState.remove(projectId)
                    }
                    ProjectControlHub.Action.STOP -> null
                }
                if (previous != null) {
                    store.write(
                        projectKey = projectId,
                        environmentReady = previous.environmentReadyFor(selection),
                        runtimeState = previous.runtimeState,
                        failureReason = previous.failureReason,
                        runtimeSelection = selection,
                    )
                }
            }
        }
    }

    fun completeExternalPrepare(
        project: V04ProjectGateway.RuntimeProject,
        selection: ProjectRuntimeSelection,
        prepared: Boolean,
        failureReason: String? = null,
    ) {
        val projectId = project.summary.documentId
        val previous = synchronized(previousPrepareState) {
            previousPrepareState.remove(projectId)
        }
        val current = store.read(projectId)
        val previousReady = previous?.environmentReadyFor(selection)
            ?: current.environmentReadyFor(selection)
        store.write(
            projectKey = projectId,
            environmentReady = if (prepared) true else previousReady,
            runtimeState = when {
                prepared -> RuntimeState.UNKNOWN
                previousReady == true -> RuntimeState.UNKNOWN
                else -> RuntimeState.ENVIRONMENT_ERROR
            },
            failureReason = if (prepared) null else failureReason,
            runtimeSelection = selection,
        )
    }

    fun cancelPrepare(
        project: V04ProjectGateway.RuntimeProject,
        selection: ProjectRuntimeSelection,
    ) {
        val projectId = project.summary.documentId
        val previous = synchronized(previousPrepareState) {
            previousPrepareState.remove(projectId)
        }
        val current = store.read(projectId)
        val ready = previous?.environmentReadyFor(selection)
            ?: current.environmentReadyFor(selection)
        store.write(
            projectKey = projectId,
            environmentReady = ready,
            runtimeState = RuntimeState.STOPPED_BY_USER,
            failureReason = null,
            runtimeSelection = selection,
        )
    }
}
