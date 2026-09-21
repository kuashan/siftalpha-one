package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.V04ProjectGateway

/**
 * Normal Mode（普通模式） control boundary.
 *
 * This hub deliberately owns no Runtime implementation, UI state machine, observation loop,
 * Environment Plan or result presentation. It resolves the project's persisted Runtime selection
 * and delegates RUN / STOP to an injected executor that operates the already accepted Runtime
 * controller/backend.
 *
 * Developer Workspace（开发者工作区） and Normal Mode therefore control the same project Runtime
 * instead of one surface automating the other surface's buttons.
 */
class ProjectControlHub internal constructor(
    private val selectionReader: (String) -> ProjectRuntimeSelection,
    private val executor: Executor,
) {
    constructor(
        selectionStore: ProjectRuntimeSelectionStore,
        executor: Executor,
    ) : this(selectionStore::read, executor)

    data class RunRequest(
        val requiredConfiguration: Boolean = false,
        val launchInvocation: PythonLaunchInvocation? = null,
        val webLogDiscoveryAllowed: Boolean = false,
        val webHintPorts: List<Int> = emptyList(),
    )

    enum class Action {
        RUN,
        STOP,
    }

    enum class Failure {
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

    interface Executor {
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

    fun run(
        project: V04ProjectGateway.RuntimeProject,
        request: RunRequest = RunRequest(),
    ): Result {
        val selection = selectionReader(project.summary.documentId)
        return executor.run(project, selection, request)
    }

    fun stop(project: V04ProjectGateway.RuntimeProject): Result {
        val selection = selectionReader(project.summary.documentId)
        return executor.stop(project, selection)
    }
}

/**
 * Production RUN / STOP executor for ProjectControlHub（项目控制枢纽）.
 *
 * The implementation delegates directly to the existing ProjectRuntimeController（项目运行控制器）
 * and RuntimeBackend（运行后端）. It does not depend on V04Activity（开发者运行中心页面） and never
 * starts a hidden Activity or simulates a UI click.
 */
class ProjectRuntimeControlExecutor(
    private val runtime: ProjectRuntimeController,
    private val externalBackend: RuntimeBackend,
) : ProjectControlHub.Executor {

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
