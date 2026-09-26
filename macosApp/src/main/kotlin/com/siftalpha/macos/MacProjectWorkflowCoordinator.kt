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
import com.siftalpha.core.storage.DurableProjectOperationRecord
import com.siftalpha.core.storage.DurableProjectOperationStore
import com.siftalpha.core.storage.DurableRuntimeStateStore
import com.siftalpha.core.storage.PlatformStateStorage
import com.siftalpha.core.storage.ProjectCleanupPolicy
import com.siftalpha.core.storage.ProjectSecretStorage
import com.siftalpha.studio.container.ComposeProjectPlan
import com.siftalpha.studio.container.ComposeProjectPlanStatus
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
    val composePlan: ComposeProjectPlan? = null,
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
    val containerProvider: String? = null,
    val composeProjectName: String? = null,
    val containerServices: List<MacComposeServiceStatus> = emptyList(),
)

internal enum class MacProjectRecoveryDecision {
    NONE,
    RECOVER_STALE,
    LIVE_OPERATION,
}

internal object MacProjectRecoveryPolicy {
    fun decide(
        persisted: DurableProjectOperationRecord?,
        live: ProjectOperationOwnership?,
    ): MacProjectRecoveryDecision {
        if (persisted == null || persisted.phase.terminal) {
            return MacProjectRecoveryDecision.NONE
        }
        if (
            live != null &&
            live.phase == ProjectOperationPhase.ACTIVE &&
            persisted.projectId == live.projectId &&
            persisted.action == live.action &&
            persisted.generation == live.generation
        ) {
            return MacProjectRecoveryDecision.LIVE_OPERATION
        }
        return MacProjectRecoveryDecision.RECOVER_STALE
    }
}

class MacProjectWorkflowCoordinator(
    private val processControl: MacProjectProcessControl = MacProjectProcessControl(),
    private val environmentManager: MacProjectEnvironmentManager = MacProjectEnvironmentManager(
        processControl = processControl,
        managedPython = MacManagedPythonRuntime.locate(),
    ),
    private val containerProviderSource: () -> MacComposeContainerProvider? = {
        MacComposeProviderSelector.select(MacContainerRuntimeDiscovery().discoverAll())
    },
    stateStorage: PlatformStateStorage? = null,
    private val projectSecretStorage: ProjectSecretStorage? = null,
) {
    private data class MutableProjectState(
        @Volatile var context: MacWorkflowContext? = null,
        @Volatile var operation: ProjectOperationOwnership? = null,
        @Volatile var cancelRequested: Boolean = false,
        @Volatile var composePrepared: Boolean = false,
        @Volatile var cachedComposeStatus: MacComposeRuntimeStatus? = null,
        @Volatile var cachedComposeStatusAtMs: Long = 0L,
        @Volatile var recoveryInProgress: Boolean = false,
        val generation: AtomicLong = AtomicLong(0),
        val history: StringBuilder = StringBuilder(),
    )

    private val states = ConcurrentHashMap<String, MutableProjectState>()
    private val webDiscovery = MacProjectWebDiscovery(processControl)
    private val runtimeStateStore = stateStorage?.let(::DurableRuntimeStateStore)
    private val operationStore = stateStorage?.let(::DurableProjectOperationStore)

    fun attach(context: MacWorkflowContext) {
        val projectId = context.project.projectId
        val state = state(projectId)
        state.context = context
        if (context.composePlan?.isComposeProject == true) {
            state.composePrepared = environmentManager.isComposePrepared(projectId)
        }

        operationStore?.let { store ->
            val persistedGeneration = store.lastGeneration(projectId)
            if (persistedGeneration > state.generation.get()) {
                state.generation.set(persistedGeneration)
            }
            val persisted = store.read(projectId)
            when (MacProjectRecoveryPolicy.decide(persisted, state.operation)) {
                MacProjectRecoveryDecision.RECOVER_STALE -> {
                    checkNotNull(persisted)
                    state.recoveryInProgress = true
                    append(
                        state,
                        "RECOVERY_PENDING=" +
                            persisted.action.name +
                            " generation=" +
                            persisted.generation,
                    )
                    store.clearCurrent(projectId)
                }
                MacProjectRecoveryDecision.LIVE_OPERATION,
                MacProjectRecoveryDecision.NONE -> Unit
            }
        }

        if (context.composePlan?.isComposeProject != true && processControl.recover(ProjectProcessScope(projectId))) {
            state.recoveryInProgress = true
            append(state, "PROCESS_OWNERSHIP_RECOVERED=1")
        }
    }

    fun detach(projectId: String): Boolean {
        val state = states[projectId] ?: return true
        val decision = ProjectCleanupPolicy.evaluate(
            processRunning = status(projectId).processState == ProjectProcessState.RUNNING,
            operationActive = state.operation != null,
        )
        if (!decision.allowed) return false
        if (!processControl.forget(ProjectProcessScope(projectId))) return false
        return states.remove(projectId, state)
    }

    fun clearEnvironment(projectId: String): Boolean {
        val state = states[projectId] ?: return false
        val context = state.context ?: return false
        val cleanupDecision = ProjectCleanupPolicy.evaluate(
            processRunning = status(projectId).processState == ProjectProcessState.RUNNING,
            operationActive = state.operation != null,
        )
        if (!cleanupDecision.allowed) {
            append(state, "CLEAN_BLOCKED=" + cleanupDecision.reason.orEmpty())
            return false
        }
        val generation = begin(projectId, ProjectOperationAction.CLEAN) ?: return false

        val composePlan = context.composePlan?.takeIf { it.isComposeProject }
        val success = if (composePlan != null) {
            val provider = containerProvider(projectId)
            if (provider == null) {
                append(state, "CLEAN_FAILED=container provider unavailable")
                false
            } else {
                val result = provider.clean(context.project, composePlan)
                appendProviderOutput(state, "compose clean", result.output)
                result.detail?.let { append(state, "CLEAN_DETAIL=" + it) }
                result.success
            }
        } else {
            runCatching { environmentManager.clearProjectEnvironment(projectId) }
                .onFailure { append(state, "CLEAN_DETAIL=" + (it.message ?: it.javaClass.simpleName)) }
                .getOrDefault(false)
        }

        if (success) {
            state.composePrepared = false
            environmentManager.clearComposePrepared(projectId)
            invalidateComposeStatus(state)
            processControl.forget(ProjectProcessScope(projectId))
            runtimeStateStore?.clear(projectId, providerId(state))
            synchronized(state.history) { state.history.setLength(0) }
        }
        finish(
            projectId,
            generation,
            if (success) ProjectOperationPhase.SUCCESS else ProjectOperationPhase.FAILED,
        )
        return success
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

        val composePlan = context.composePlan?.takeIf { it.isComposeProject }
        val result = if (composePlan != null) {
            prepareCompose(state, context, composePlan)
        } else {
            environmentManager.prepare(
                project = context.project,
                snapshot = context.snapshot,
                plan = context.plan,
                cancelled = { state.cancelRequested },
                log = { line -> append(state, line) },
            )
        }

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

    internal fun appendDiagnostic(projectId: String, line: String) {
        append(state(projectId), line)
    }

    private fun prepareCompose(
        state: MutableProjectState,
        context: MacWorkflowContext,
        plan: ComposeProjectPlan,
    ): MacPrepareResult {
        if (plan.status == ComposeProjectPlanStatus.INVALID_MANIFEST ||
            plan.status == ComposeProjectPlanStatus.NOT_COMPOSE
        ) {
            return MacPrepareResult(
                false,
                false,
                null,
                listOf("SIFTALPHA_M62_PREPARE=FAILED"),
                plan.issues.firstOrNull() ?: "Compose plan is invalid",
            )
        }
        val discoveredProvider = containerProvider(context.project.projectId)
        val managedRuntime = when {
            discoveredProvider?.snapshot?.executablePath?.let {
                MacManagedContainerToolchain.isManagedExecutable(it)
            } == true -> true
            discoveredProvider == null && MacManagedContainerToolchain.managedDockerExecutable() != null -> true
            else -> false
        }
        if (managedRuntime) {
            append(state, "RUNTIME_READY=MANAGED_CHECK")
            val ready = MacManagedContainerInstaller().ensureManagedRuntimeReady { line ->
                append(state, line)
            }
            if (!ready.success) {
                return MacPrepareResult(
                    false,
                    false,
                    null,
                    listOf("SIFTALPHA_M62_PREPARE=FAILED", "SIFTALPHA_M62_RUNTIME_READY=FAILED"),
                    ready.detail ?: "managed container runtime is not ready",
                )
            }
            append(state, "SIFTALPHA_M62_RUNTIME_READY=PASS")
        }
        val provider = if (managedRuntime) {
            containerProvider(context.project.projectId)
        } else {
            discoveredProvider
        }
            ?: return MacPrepareResult(
                false,
                false,
                null,
                listOf("SIFTALPHA_M62_PREPARE=FAILED"),
                "container runtime or Compose is unavailable; install/start a compatible provider and refresh",
            )

        val operation = provider.prepare(
            project = context.project,
            plan = plan,
            cancelled = { state.cancelRequested },
            log = { line -> append(state, line) },
        )
        if (operation.success) {
            state.composePrepared = true
            environmentManager.markComposePrepared(context.project.projectId)
            invalidateComposeStatus(state)
        }
        return MacPrepareResult(
            success = operation.success,
            cancelled = operation.cancelled,
            environment = null,
            lines = listOf(
                "SIFTALPHA_M62_PREPARE=" + if (operation.success) "PASS" else "FAILED",
                "SIFTALPHA_M62_PROVIDER=" + provider.snapshot.kind.id,
                "SIFTALPHA_M62_COMPOSE_PROJECT=" + provider.projectName(context.project.projectId),
            ),
            detail = operation.detail,
        )
    }

    fun start(projectId: String): Boolean {
        val state = state(projectId)
        val context = state.context ?: return false
        val composePlan = context.composePlan?.takeIf { it.isComposeProject }
        if (composePlan != null) {
            return startCompose(projectId, state, context, composePlan)
        }

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
                    environment = buildMap {
                        put("PYTHONUNBUFFERED", "1")
                        put("SIFTALPHA_PROJECT_ID", projectId)
                        put("SIFTALPHA_OPERATION_GENERATION", generation.toString())
                        putAll(projectEnvironment(projectId))
                    },
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
                    environment = buildMap {
                        put("SIFTALPHA_PROJECT_ID", projectId)
                        put("SIFTALPHA_OPERATION_GENERATION", generation.toString())
                        putAll(projectEnvironment(projectId))
                    },
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

    private fun startCompose(
        projectId: String,
        state: MutableProjectState,
        context: MacWorkflowContext,
        plan: ComposeProjectPlan,
    ): Boolean {
        if (!state.composePrepared) return false
        val provider = containerProvider(projectId) ?: return false
        val generation = begin(projectId, ProjectOperationAction.START) ?: return false
        val result = provider.start(context.project, plan)
        appendProviderOutput(state, "compose up", result.output)
        invalidateComposeStatus(state)
        append(state, "COMPOSE_START=" + if (result.success) "PASS" else "FAILED")
        result.detail?.let { append(state, "COMPOSE_START_DETAIL=" + it) }
        finish(
            projectId,
            generation,
            if (result.success) ProjectOperationPhase.SUCCESS else ProjectOperationPhase.FAILED,
        )
        return result.success
    }

    fun stop(projectId: String): Boolean {
        val state = state(projectId)
        val context = state.context
        val composePlan = context?.composePlan?.takeIf { it.isComposeProject }
        if (context != null && composePlan != null) {
            return stopCompose(projectId, state, context, composePlan)
        }

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

    private fun stopCompose(
        projectId: String,
        state: MutableProjectState,
        context: MacWorkflowContext,
        plan: ComposeProjectPlan,
    ): Boolean {
        val generation = begin(projectId, ProjectOperationAction.STOP) ?: return false
        state.cancelRequested = true
        val provider = containerProvider(projectId)
        if (provider == null) {
            append(state, "COMPOSE_STOP=FAILED provider unavailable")
            finish(projectId, generation, ProjectOperationPhase.FAILED)
            return false
        }
        val result = provider.stop(context.project, plan)
        appendProviderOutput(state, "compose down", result.output)
        invalidateComposeStatus(state)
        append(state, "COMPOSE_STOP=" + if (result.success) "PASS" else "FAILED")
        result.detail?.let { append(state, "COMPOSE_STOP_DETAIL=" + it) }
        finish(
            projectId,
            generation,
            if (result.success) ProjectOperationPhase.SUCCESS else ProjectOperationPhase.FAILED,
        )
        return result.success
    }

    fun restart(projectId: String): Boolean {
        if (!stop(projectId)) return false
        state(projectId).cancelRequested = false
        return start(projectId)
    }

    fun logs(projectId: String, maxBytes: Int = 512 * 1024): String {
        val state = state(projectId)
        val context = state.context
        val history = synchronized(state.history) { state.history.toString() }
        val composePlan = context?.composePlan?.takeIf { it.isComposeProject }
        if (context != null && composePlan != null) {
            val containerLogs = containerProvider(projectId)
                ?.logs(context.project, composePlan, maxBytes)
                .orEmpty()
            return buildString {
                append(history)
                if (containerLogs.isNotBlank()) {
                    append("\n--- compose logs ---\n")
                    append(containerLogs)
                }
            }.takeLast(maxBytes)
        }

        val processLogs = processControl.logs(ProjectProcessScope(projectId), maxBytes)
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
        return redactProjectText(projectId, combined)
    }

    fun webEndpoint(projectId: String): MacProjectWebEndpoint? {
        val state = state(projectId)
        val context = state.context ?: return null
        val composePlan = context.composePlan?.takeIf { it.isComposeProject }
        if (composePlan != null) {
            val provider = containerProvider(projectId) ?: return null
            return webDiscovery.discoverFromPorts(
                ports = provider.publishedPorts(context.project, composePlan),
                combinedOutput = logs(projectId),
            )
        }
        return webDiscovery.discover(ProjectProcessScope(projectId), logs(projectId))
    }

    fun diagnostics(projectId: String, maxBytes: Int = 512 * 1024): MacWorkflowDiagnostics {
        val state = state(projectId)
        val context = state.context
        val composePlan = context?.composePlan?.takeIf { it.isComposeProject }
        if (context != null && composePlan != null) {
            val provider = containerProvider(projectId)
            val runtime = provider?.let { composeStatus(state, context, composePlan, it, force = true) }
            val combined = logs(projectId, maxBytes)
            return MacWorkflowDiagnostics(
                status = status(projectId),
                environment = null,
                entrypoint = entrypoint(projectId),
                ownedPids = emptySet(),
                stdout = combined,
                stderr = "",
                combinedLogs = combined,
                logsTruncated = combined.length >= maxBytes,
                containerProvider = provider?.snapshot?.kind?.id,
                composeProjectName = provider?.projectName(projectId)
                    ?: MacComposeProjectIdentity.forProject(projectId),
                containerServices = runtime?.services.orEmpty(),
            )
        }

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
            stdout = redactProjectText(projectId, processLogs.stdout),
            stderr = redactProjectText(projectId, processLogs.stderr),
            combinedLogs = redactProjectText(projectId, combined),
            logsTruncated = processLogs.truncated,
        )
    }

    fun entrypoint(projectId: String): String? {
        val context = state(projectId).context ?: return null
        context.composePlan?.takeIf { it.isComposeProject }?.let { plan ->
            return plan.manifestPath?.let { "compose:" + it }
        }
        return when (context.plan.needs?.primaryRuntime) {
            RuntimeKind.PYTHON -> EmbeddedPythonEntrypointPolicy.resolve(
                declaredEntry = null,
                filePaths = context.snapshot.relativePaths,
            )
            RuntimeKind.NODE_JS -> {
                val packageText = context.snapshot.packageJsonText.orEmpty()
                val hasStart = Regex("""["']start["']\s*:""").containsMatchIn(packageText)
                val contract = NodeStartContractPolicy.resolve(null, hasStart)
                if (contract is NodeStartContractPolicy.Result.Resolved) contract.command else null
            }
            else -> null
        }
    }

    fun status(projectId: String): MacWorkflowStatus {
        val state = state(projectId)
        val context = state.context
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

        val composePlan = context?.composePlan?.takeIf { it.isComposeProject }
        if (context != null && composePlan != null) {
            val provider = containerProvider(projectId)
            val composeStatus = provider?.let { composeStatus(state, context, composePlan, it) }
            val processState = when {
                composeStatus == null -> ProjectProcessState.UNKNOWN
                composeStatus.anyRunning -> ProjectProcessState.RUNNING
                composeStatus.services.any { it.state == MacComposeServiceState.UNKNOWN } ->
                    ProjectProcessState.UNKNOWN
                else -> ProjectProcessState.STOPPED
            }
            val runtimeState = when (processState) {
                ProjectProcessState.RUNNING -> RuntimeExecutionState.RUNNING
                ProjectProcessState.STOPPED -> RuntimeExecutionState.STOPPED_BY_USER
                ProjectProcessState.STARTING -> RuntimeExecutionState.STARTING
                ProjectProcessState.EXITED_SUCCESS -> RuntimeExecutionState.EXITED_SUCCESS
                ProjectProcessState.EXITED_ERROR -> RuntimeExecutionState.EXITED_ERROR
                ProjectProcessState.UNKNOWN -> RuntimeExecutionState.UNKNOWN
            }
            val endpoint = if (processState == ProjectProcessState.RUNNING) {
                webEndpoint(projectId)
            } else {
                null
            }
            runtimeStateStore?.write(
                projectId = projectId,
                providerId = "container",
                environmentReady = state.composePrepared,
                runtimeState = runtimeState,
                failureReason = null,
            )
            val recovering = state.recoveryInProgress
            val resolved = MacWorkflowStatus(
                projectId = projectId,
                lifecycle = RuntimeLifecyclePolicy.resolve(
                    environmentReady = state.composePrepared,
                    runtimeState = runtimeState,
                    operation = lifecycleOp,
                    processActive = processState == ProjectProcessState.RUNNING,
                    recoveryInProgress = recovering,
                ),
                environmentReady = state.composePrepared,
                processState = processState,
                operation = operation,
                webEndpoint = endpoint,
            )
            if (recovering) state.recoveryInProgress = false
            return resolved
        }

        val envReady = environmentManager.currentEnvironment(projectId) != null
        val processStatus = processControl.status(ProjectProcessScope(projectId))
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
        runtimeStateStore?.write(
            projectId = projectId,
            providerId = "host",
            environmentReady = envReady,
            runtimeState = runtimeState,
            failureReason = null,
        )
        val recovering = state.recoveryInProgress
        val resolved = MacWorkflowStatus(
            projectId = projectId,
            lifecycle = RuntimeLifecyclePolicy.resolve(
                environmentReady = envReady,
                runtimeState = runtimeState,
                operation = lifecycleOp,
                processActive = processStatus.state == ProjectProcessState.RUNNING,
                recoveryInProgress = recovering,
            ),
            environmentReady = envReady,
            processState = processStatus.state,
            operation = operation,
            webEndpoint = endpoint,
        )
        if (recovering) state.recoveryInProgress = false
        return resolved
    }

    private fun composeStatus(
        state: MutableProjectState,
        context: MacWorkflowContext,
        plan: ComposeProjectPlan,
        provider: MacComposeContainerProvider,
        force: Boolean = false,
    ): MacComposeRuntimeStatus {
        val now = System.currentTimeMillis()
        val cached = state.cachedComposeStatus
        if (!force && cached != null && now - state.cachedComposeStatusAtMs < 800L) {
            return cached
        }
        val latest = provider.status(context.project, plan)
        state.cachedComposeStatus = latest
        state.cachedComposeStatusAtMs = now
        return latest
    }

    private fun invalidateComposeStatus(state: MutableProjectState) {
        state.cachedComposeStatus = null
        state.cachedComposeStatusAtMs = 0L
    }

    private fun appendProviderOutput(
        state: MutableProjectState,
        label: String,
        output: String,
    ) {
        output.lineSequence()
            .filter(String::isNotBlank)
            .forEach { append(state, label + ": " + it.take(1000)) }
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
            val durableGeneration = operationStore?.lastGeneration(projectId) ?: 0L
            val generation = maxOf(state.generation.get(), durableGeneration) + 1L
            state.generation.set(generation)
            state.recoveryInProgress = false
            state.operation = ProjectOperationOwnership(
                projectId = projectId,
                action = action,
                phase = ProjectOperationPhase.ACTIVE,
                generation = generation,
            )
            operationStore?.write(
                DurableProjectOperationRecord(
                    projectId = projectId,
                    providerId = providerId(state),
                    action = action,
                    phase = ProjectOperationPhase.ACTIVE,
                    generation = generation,
                    startedAtEpochMs = System.currentTimeMillis(),
                ),
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
            operationStore?.read(projectId)?.let { persisted ->
                if (persisted.generation == generation) {
                    operationStore.write(persisted.copy(phase = phase))
                }
            }
            operationStore?.clearCurrent(projectId)
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

    private fun providerId(state: MutableProjectState): String =
        if (state.context?.composePlan?.isComposeProject == true) "container" else "host"

    private fun projectEnvironment(projectId: String): Map<String, String> {
        val store = projectSecretStorage ?: return emptyMap()
        return store.configuredKeys(projectId)
            .sorted()
            .mapNotNull { name -> store.read(projectId, name)?.let { value -> name to value } }
            .toMap(linkedMapOf())
    }

    private fun redactProjectText(projectId: String, text: String): String {
        if (text.isBlank()) return text
        return projectEnvironment(projectId).values
            .filter(String::isNotBlank)
            .distinct()
            .sortedByDescending(String::length)
            .fold(text) { safe, secret -> safe.replace(secret, "[REDACTED]") }
    }

    private fun containerProvider(projectId: String): MacComposeContainerProvider? =
        containerProviderSource()?.also { provider ->
            provider.configureProjectEnvironment(projectId, projectEnvironment(projectId))
        }

    private fun state(projectId: String): MutableProjectState =
        states.computeIfAbsent(projectId) { MutableProjectState() }
}
