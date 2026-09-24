package com.siftalpha.macos

import com.siftalpha.core.lifecycle.ProjectLifecycleOperation
import com.siftalpha.core.lifecycle.ProjectLifecycleState
import com.siftalpha.core.lifecycle.RuntimeExecutionState
import com.siftalpha.core.lifecycle.RuntimeLifecyclePolicy
import com.siftalpha.core.operation.ProjectOperationAction
import com.siftalpha.core.operation.ProjectOperationOwnership
import com.siftalpha.core.operation.ProjectOperationPhase
import com.siftalpha.core.operation.ProjectOperationPolicy
import com.siftalpha.core.process.ProjectProcessLaunchRequest
import com.siftalpha.core.process.ProjectProcessScope
import com.siftalpha.core.process.ProjectProcessState
import com.siftalpha.core.process.ProjectStopOutcome
import com.siftalpha.studio.project.EmbeddedPythonEntrypointPolicy
import com.siftalpha.studio.runtime.NodeStartContractPolicy
import com.siftalpha.studio.runtime.RuntimeKind
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class MacWorkflowContext(
    val project: MacImportedProject,
    val snapshot: MacProjectSnapshot,
    val plan: MacProjectEnvironmentPlan,
)

data class MacWorkflowStatus(
    val projectId: String,
    val lifecycle: ProjectLifecycleState,
    val environmentReady: Boolean,
    val processState: ProjectProcessState,
    val operation: ProjectOperationOwnership?,
    val webEndpoint: MacProjectWebEndpoint?,
)

data class MacWorkflowDiagnostics(
    val status: MacWorkflowStatus,
    val environment: MacPreparedEnvironment?,
    val entrypoint: String?,
    val ownedPids: Set<Long>,
    val stdout: String,
    val stderr: String,
    val combinedLogs: String,
    val logsTruncated: Boolean,
)

class MacProjectWorkflowCoordinator(
    private val processControl: MacProjectProcessControl = MacProjectProcessControl(),
    private val environmentManager: MacProjectEnvironmentManager = MacProjectEnvironmentManager(
        processControl = processControl,
        managedPython = MacManagedPythonRuntime.locate(),
    ),
) {
    private data class MutableProjectState(
        @Volatile var context: MacWorkflowContext? = null,
        @Volatile var operation: ProjectOperationOwnership? = null,
        @Volatile var cancelRequested: Boolean = false,
        val generation: AtomicLong = AtomicLong(0),
        val history: StringBuilder = StringBuilder(),
    )

    private val states = ConcurrentHashMap<String, MutableProjectState>()
    private val webDiscovery = MacProjectWebDiscovery(processControl)

    fun attach(context: MacWorkflowContext) {
        state(context.project.projectId).context = context
    }

    fun prepare(projectId: String): MacPrepareResult {
        val state = state(projectId)
        val context = state.context ?: return MacPrepareResult(
            false, false, null, emptyList(), "project is not attached",
        )
        val generation = begin(projectId, ProjectOperationAction.PREPARE)
            ?: return MacPrepareResult(false, false, null, emptyList(), "project operation is busy")

        state.cancelRequested = false
        append(state, "PREPARE generation=" + generation)
        val result = environmentManager.prepare(
            project = context.project,
            snapshot = context.snapshot,
            plan = context.plan,
            cancelled = { state.cancelRequested },
            log = { line -> append(state, line) },
        )
        finish(
            projectId,
            generation,
            if (result.success) ProjectOperationPhase.SUCCESS
            else if (result.cancelled) ProjectOperationPhase.CANCELLED
            else ProjectOperationPhase.FAILED,
        )
        result.lines.forEach { append(state, it) }
        result.detail?.let { append(state, "PREPARE_DETAIL=" + it) }
        return result
    }

    fun start(projectId: String): Boolean {
        val state = state(projectId)
        val context = state.context ?: return false
        val environment = environmentManager.currentEnvironment(projectId) ?: return false
        val generation = begin(projectId, ProjectOperationAction.START) ?: return false

        val request = when (context.plan.needs?.primaryRuntime) {
            RuntimeKind.PYTHON -> {
                val python = environment.pythonExecutable ?: run {
                    finish(projectId, generation, ProjectOperationPhase.FAILED)
                    return false
                }
                val entry = EmbeddedPythonEntrypointPolicy.resolve(
                    declaredEntry = null,
                    filePaths = context.snapshot.relativePaths,
                ) ?: run {
                    append(state, "START_FAILED=no safe Python entrypoint")
                    finish(projectId, generation, ProjectOperationPhase.FAILED)
                    return false
                }
                ProjectProcessLaunchRequest(
                    scope = ProjectProcessScope(projectId),
                    executable = python.absolutePath,
                    arguments = listOf("-u", entry),
                    workingDirectory = context.project.canonicalRootPath,
                    environment = mapOf(
                        "PYTHONUNBUFFERED" to "1",
                        "SIFTALPHA_PROJECT_ID" to projectId,
                    ),
                )
            }

            RuntimeKind.NODE_JS -> {
                val nodePath = context.plan.runtimeExecutables[RuntimeKind.NODE_JS] ?: run {
                    finish(projectId, generation, ProjectOperationPhase.FAILED)
                    return false
                }
                val npm = File(File(nodePath).parentFile, "npm")
                val packageText = context.snapshot.packageJsonText.orEmpty()
                val hasStart = Regex("""["']start["']\s*:""").containsMatchIn(packageText)
                val contract = NodeStartContractPolicy.resolve(null, hasStart)
                if (contract !is NodeStartContractPolicy.Result.Resolved ||
                    contract.command != "npm start" ||
                    !npm.isFile
                ) {
                    append(state, "START_FAILED=no supported Node start contract")
                    finish(projectId, generation, ProjectOperationPhase.FAILED)
                    return false
                }
                ProjectProcessLaunchRequest(
                    scope = ProjectProcessScope(projectId),
                    executable = npm.absolutePath,
                    arguments = listOf("start"),
                    workingDirectory = context.project.canonicalRootPath,
                    environment = mapOf("SIFTALPHA_PROJECT_ID" to projectId),
                )
            }

            else -> {
                finish(projectId, generation, ProjectOperationPhase.FAILED)
                return false
            }
        }

        return runCatching {
            processControl.start(request)
            append(state, "START:PASS")
            finish(projectId, generation, ProjectOperationPhase.SUCCESS)
            true
        }.getOrElse { error ->
            append(state, "START_FAILED=" + (error.message ?: error.javaClass.simpleName))
            finish(projectId, generation, ProjectOperationPhase.FAILED)
            false
        }
    }

    fun stop(projectId: String): Boolean {
        val state = state(projectId)
        val generation = begin(projectId, ProjectOperationAction.STOP) ?: return false
        state.cancelRequested = true
        val result = processControl.stopProject(ProjectProcessScope(projectId))
        val okay = result.outcome == ProjectStopOutcome.STOPPED ||
            result.outcome == ProjectStopOutcome.ALREADY_STOPPED ||
            result.outcome == ProjectStopOutcome.NOT_FOUND
        append(state, "STOP=" + result.outcome)
        finish(
            projectId,
            generation,
            if (okay) ProjectOperationPhase.SUCCESS else ProjectOperationPhase.FAILED,
        )
        return okay
    }

    fun restart(projectId: String): Boolean {
        stop(projectId)
        state(projectId).cancelRequested = false
        return start(projectId)
    }

    fun logs(projectId: String, maxBytes: Int = 512 * 1024): String {
        val state = state(projectId)
        val processLogs = processControl.logs(ProjectProcessScope(projectId), maxBytes)
        val history = synchronized(state.history) { state.history.toString() }
        return buildString {
            append(history)
            if (processLogs.stdout.isNotBlank()) {
                append("\n--- stdout ---\n")
                append(processLogs.stdout)
            }
            if (processLogs.stderr.isNotBlank()) {
                append("\n--- stderr ---\n")
                append(processLogs.stderr)
            }
        }.takeLast(maxBytes)
    }

    fun webEndpoint(projectId: String): MacProjectWebEndpoint? =
        webDiscovery.discover(ProjectProcessScope(projectId), logs(projectId))

    fun diagnostics(projectId: String, maxBytes: Int = 512 * 1024): MacWorkflowDiagnostics {
        val state = state(projectId)
        val scope = ProjectProcessScope(projectId)
        val processLogs = processControl.logs(scope, maxBytes)
        val history = synchronized(state.history) { state.history.toString() }
        val combined = buildString {
            append(history)
            if (processLogs.stdout.isNotBlank()) {
                append("\n--- stdout ---\n")
                append(processLogs.stdout)
            }
            if (processLogs.stderr.isNotBlank()) {
                append("\n--- stderr ---\n")
                append(processLogs.stderr)
            }
        }.takeLast(maxBytes)
        return MacWorkflowDiagnostics(
            status = status(projectId),
            environment = environmentManager.currentEnvironment(projectId),
            entrypoint = entrypoint(projectId),
            ownedPids = processControl.ownedPids(scope),
            stdout = processLogs.stdout,
            stderr = processLogs.stderr,
            combinedLogs = combined,
            logsTruncated = processLogs.truncated,
        )
    }

    fun entrypoint(projectId: String): String? {
        val context = state(projectId).context ?: return null
        return when (context.plan.needs?.primaryRuntime) {
            RuntimeKind.PYTHON -> EmbeddedPythonEntrypointPolicy.resolve(
                declaredEntry = null,
                filePaths = context.snapshot.relativePaths,
            )
            RuntimeKind.NODE_JS -> {
                val packageText = context.snapshot.packageJsonText.orEmpty()
                val hasStart = Regex("""["']start["']\\s*:""").containsMatchIn(packageText)
                val contract = NodeStartContractPolicy.resolve(null, hasStart)
                if (contract is NodeStartContractPolicy.Result.Resolved) contract.command else null
            }
            else -> null
        }
    }

    fun status(projectId: String): MacWorkflowStatus {
        val state = state(projectId)
        val envReady = environmentManager.currentEnvironment(projectId) != null
        val processStatus = processControl.status(ProjectProcessScope(projectId))
        val operation = state.operation
        val lifecycleOp = when (operation?.action) {
            ProjectOperationAction.PREPARE -> ProjectLifecycleOperation.PREPARE
            ProjectOperationAction.START -> ProjectLifecycleOperation.START
            ProjectOperationAction.STOP -> ProjectLifecycleOperation.STOP
            ProjectOperationAction.STATUS -> ProjectLifecycleOperation.STATUS
            ProjectOperationAction.LOGS -> ProjectLifecycleOperation.LOGS
            ProjectOperationAction.CLEAN -> ProjectLifecycleOperation.CLEAN
            null -> ProjectLifecycleOperation.NONE
        }
        val runtimeState = when (processStatus.state) {
            ProjectProcessState.STARTING -> RuntimeExecutionState.STARTING
            ProjectProcessState.RUNNING -> RuntimeExecutionState.RUNNING
            ProjectProcessState.EXITED_SUCCESS -> RuntimeExecutionState.EXITED_SUCCESS
            ProjectProcessState.EXITED_ERROR -> RuntimeExecutionState.EXITED_ERROR
            ProjectProcessState.STOPPED -> RuntimeExecutionState.STOPPED_BY_USER
            ProjectProcessState.UNKNOWN -> RuntimeExecutionState.UNKNOWN
        }
        val endpoint = if (processStatus.state == ProjectProcessState.RUNNING) {
            webEndpoint(projectId)
        } else {
            null
        }
        return MacWorkflowStatus(
            projectId = projectId,
            lifecycle = RuntimeLifecyclePolicy.resolve(
                environmentReady = envReady,
                runtimeState = runtimeState,
                operation = lifecycleOp,
                processActive = processStatus.state == ProjectProcessState.RUNNING,
            ),
            environmentReady = envReady,
            processState = processStatus.state,
            operation = operation,
            webEndpoint = endpoint,
        )
    }

    private fun begin(projectId: String, action: ProjectOperationAction): Long? {
        val state = state(projectId)
        synchronized(state) {
            val current = state.operation
            if (current != null && !ProjectOperationPolicy.canBegin(current, projectId, action)) {
                return null
            }
            if (current != null &&
                ProjectOperationPolicy.canSupersede(current, projectId, action)
            ) {
                state.cancelRequested = true
            }
            val generation = state.generation.incrementAndGet()
            state.operation = ProjectOperationOwnership(
                projectId = projectId,
                action = action,
                phase = ProjectOperationPhase.ACTIVE,
                generation = generation,
            )
            return generation
        }
    }

    private fun finish(
        projectId: String,
        generation: Long,
        phase: ProjectOperationPhase,
    ) {
        val state = state(projectId)
        synchronized(state) {
            val current = state.operation
            if (current?.generation != generation) return
            state.operation = null
            append(state, current.action.name + ":" + phase.name)
        }
    }

    private fun append(state: MutableProjectState, line: String) {
        synchronized(state.history) {
            state.history.append(line).append('\n')
            if (state.history.length > 1_000_000) {
                state.history.delete(0, state.history.length - 800_000)
            }
        }
    }

    private fun state(projectId: String): MutableProjectState =
        states.computeIfAbsent(projectId) { MutableProjectState() }
}
