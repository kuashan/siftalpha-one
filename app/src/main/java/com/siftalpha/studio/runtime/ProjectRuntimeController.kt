package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.EmbeddedPythonProjectStager
import com.siftalpha.studio.project.V04ProjectGateway
import com.siftalpha.studio.siftalphax.EmbeddedPythonDependencyInputV1
import com.siftalpha.studio.siftalphax.EmbeddedPythonEnvironmentManager
import com.siftalpha.studio.siftalphax.EmbeddedPythonRequirementParserV1
import com.siftalpha.studio.siftalphax.EmbeddedPythonSession
import com.siftalpha.studio.siftalphax.EmbeddedPythonSnapshot
import com.siftalpha.studio.siftalphax.EmbeddedPythonStatePolicy
import com.siftalpha.studio.siftalphax.InternalAlpineDependencySource
import com.siftalpha.studio.siftalphax.InternalAlpineEnvironmentManager
import com.siftalpha.studio.siftalphax.InternalAlpineSession
import com.siftalpha.studio.siftalphax.InternalPythonBackend
import java.io.File

/**
 * Runtime-neutral project execution coordinator.
 *
 * v0.7 alpha5 removes the historical Python-primary assumption. Authoritative project evidence is
 * resolved into one executable primary Runtime; Python may still compose the accepted supplemental
 * Vite/Node build path, while a Node-primary project now owns its complete lifecycle directly.
 */
internal object InternalPythonPreparationFallbackPolicy {
    private val alpineCapabilityErrors = listOf(
        "no_compatible_wheel",
        "project_requires_python_incompatible",
        "unsupported_environment_marker",
        "dependency_conflict",
        "unsupported_dependency_manifest",
        "not supported by internal python preparation",
        "requirements options and nested requirement files are not supported",
        "direct url requirements are not supported",
        "unsupported requirement syntax",
        "unsupported version specifier",
        "dynamic project dependencies are not supported",
        "pyproject.toml without a [project] table",
    )

    fun backendFor(error: Throwable): InternalPythonBackend? {
        val text = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString("\n")
            .lowercase()
        return if (alpineCapabilityErrors.any { text.contains(it) }) {
            InternalPythonBackend.ALPINE
        } else {
            null
        }
    }
}

class ProjectRuntimeController(
    private val gateway: V04ProjectGateway,
    private val embeddedPythonSession: EmbeddedPythonSession? = null,
    private val embeddedPythonProjectStager: EmbeddedPythonProjectStager? = null,
    private val embeddedPythonEnvironmentManager: EmbeddedPythonEnvironmentManager? = null,
    private val internalAlpineEnvironmentManager: InternalAlpineEnvironmentManager? = null,
    private val internalAlpineSession: InternalAlpineSession? = null,
) {

    enum class Action {
        PREPARE,
        START,
        STOP,
        STATUS,
        LOGS,
        CLEAN,
        CLONE_GITHUB,
    }

    enum class InternalEnvironmentOutcome {
        CPYTHON_READY_REUSED,
        CPYTHON_READY_NO_EXTERNAL_DEPENDENCIES,
        CPYTHON_READY_DEPENDENCIES_INSTALLED,
        ALPINE_READY_REUSED,
        ALPINE_READY_ENVIRONMENT_INSTALLED,
    }

    data class InternalEnvironmentPreparationResult(
        val ready: Boolean,
        val outcome: InternalEnvironmentOutcome,
        val backend: InternalPythonBackend,
    )

    private data class InternalDependencyFiles(
        val requirementsText: String?,
        val pyprojectText: String?,
        val alpineSource: InternalAlpineDependencySource,
    )

    data class GitHubCloneSpec(
        val cloneUrl: String,
        val sourceUrl: String,
        val branch: String,
        val projectName: String,
    )

    private data class ExecutionContext(
        val spec: RuntimeProjectSpec,
        val selection: ProjectRuntimeExecutionPlanner.Selection,
    )

    private val host: RuntimeCommandHost = TermuxProotRuntimeHost(gateway)
    private val pythonAdapter: ExecutableRuntimeAdapter = PythonRuntimeAdapter(host)
    private val nodeExecutableAdapter: ExecutableRuntimeAdapter = ExecutableNodeJsRuntimeAdapter(host)
    private val supplementalNodeAdapter = NodeJsRuntimeAdapter(host)
    private val embeddedStagingRoots = mutableMapOf<String, File>()

    fun runtimeSupported(): Boolean = host.runtimeSupported()

    fun embeddedPythonCanStart(project: V04ProjectGateway.RuntimeProject): Boolean {
        val projectId = project.summary.documentId
        val files = internalDependencyFiles(projectId)
        val cpythonBinding = runCatching {
            val input = EmbeddedPythonRequirementParserV1.fromProjectFiles(
                files.requirementsText,
                files.pyprojectText,
            )
            embeddedPythonEnvironmentManager?.loadBinding(projectId, input)
        }.getOrNull()
        if (cpythonBinding != null) {
            val session = embeddedPythonSession ?: return false
            return runCatching { EmbeddedPythonStatePolicy.canStart(session.snapshot().state) }
                .getOrDefault(false)
        }
        val alpineBinding = internalAlpineEnvironmentManager?.loadBinding(projectId, files.alpineSource)
        return alpineBinding != null && internalAlpineSession?.canStart(projectId) == true
    }

    fun embeddedPythonSnapshotFor(projectDocumentId: String): EmbeddedPythonSnapshot? {
        val alpine = internalAlpineSession?.snapshot(projectDocumentId)
        val cpython = embeddedPythonSession
            ?.let { runCatching { it.snapshot() }.getOrNull() }
            ?.takeIf {
                it.sessionId.isNotBlank() && it.projectIdentity == projectDocumentId
            }
        val candidates = listOfNotNull(alpine, cpython)
        candidates
            .filter { EmbeddedPythonStatePolicy.isTerminal(it.state) }
            .forEach(::cleanupEmbeddedStaging)
        val selected = candidates
            .filter { EmbeddedPythonStatePolicy.canStop(it.state) }
            .maxByOrNull { it.startedAtEpochMs ?: Long.MIN_VALUE }
            ?: candidates.maxByOrNull { it.startedAtEpochMs ?: Long.MIN_VALUE }
        selected?.let(::cleanupEmbeddedStaging)
        return selected
    }

    /**
     * Resolves the M control action to the existing Embedded R or External Provider path.
     *
     * The default request remains External Provider so the experimental Embedded R path is explicit.
     * STOP is selected from the active R snapshot, never from Python detection or a guessed process ID.
     */
    fun resolveControlPath(
        project: V04ProjectGateway.RuntimeProject,
        action: Action,
        request: RuntimeControlRequest = RuntimeControlRequest.EXTERNAL_PROVIDER,
        requiredConfiguration: Boolean = false,
    ): RuntimeControlDecision {
        if (action == Action.STOP) {
            val snapshot = embeddedPythonSnapshotFor(project.summary.documentId)
            if (snapshot != null && EmbeddedPythonStatePolicy.canStop(snapshot.state)) {
                return RuntimeControlDecision(
                    path = RuntimeControlPath.EMBEDDED_R,
                    reason = RuntimeControlReason.ACTIVE_EMBEDDED_SESSION,
                )
            }
        }

        if (
            (action != Action.START && action != Action.PREPARE) ||
            request != RuntimeControlRequest.EMBEDDED_R
        ) {
            return RuntimeControlDecision(
                path = RuntimeControlPath.EXTERNAL_PROVIDER,
                reason = RuntimeControlReason.EXPLICIT_EXTERNAL_PROVIDER,
            )
        }

        val facts = gateway.runtimeFacts(project.summary.documentId)
        val selection = ProjectRuntimeExecutionPlanner.select(
            relativePaths = facts.relativePaths,
            declaredType = facts.declaredType,
        )
        val capabilityFacts = EmbeddedPythonCapabilityFacts(
            selection = selection,
            relativePaths = facts.relativePaths,
            declaredRun = facts.declaredRun,
            resolvedEntrypoint = gateway.resolveEmbeddedPythonEntrypoint(project.summary.documentId),
            hasExternalDependencyRequirement = facts.hasExternalDependencyRequirement,
            hasProtectedConfigurationRequirement = requiredConfiguration,
            embeddedRuntimeAvailable = embeddedPythonProjectStager != null && (
                (embeddedPythonSession != null && embeddedPythonEnvironmentManager != null) ||
                    (internalAlpineSession != null && internalAlpineEnvironmentManager != null)
                ),
        )
        return if (action == Action.PREPARE) {
            EmbeddedPythonCapabilityRouting.resolvePreparation(
                facts = capabilityFacts,
                request = request,
            )
        } else {
            EmbeddedPythonCapabilityRouting.resolve(
                facts = capabilityFacts,
                request = request,
            )
        }
    }

    fun embeddedPythonEnvironmentReady(
        project: V04ProjectGateway.RuntimeProject,
    ): Boolean {
        val projectId = project.summary.documentId
        val files = internalDependencyFiles(projectId)
        val cpythonReady = runCatching {
            val input = EmbeddedPythonRequirementParserV1.fromProjectFiles(
                files.requirementsText,
                files.pyprojectText,
            )
            embeddedPythonEnvironmentManager?.loadBinding(
                projectIdentity = projectId,
                dependencyInput = input,
            ) != null
        }.getOrDefault(false)
        if (cpythonReady) return true
        return internalAlpineEnvironmentManager?.loadBinding(
            projectIdentity = projectId,
            source = files.alpineSource,
        ) != null
    }

    fun cleanEmbeddedPythonEnvironment(
        project: V04ProjectGateway.RuntimeProject,
    ) {
        val projectId = project.summary.documentId
        val active = embeddedPythonSnapshotFor(projectId)
        check(active == null || !EmbeddedPythonStatePolicy.canStop(active.state)) {
            "EMBEDDED_R_RUNTIME_ACTIVE"
        }
        check(embeddedPythonEnvironmentManager != null || internalAlpineEnvironmentManager != null) {
            "EMBEDDED_R_ENVIRONMENT_MANAGER_UNAVAILABLE"
        }
        embeddedPythonEnvironmentManager?.cleanProjectEnvironment(projectId)
        internalAlpineEnvironmentManager?.cleanProjectEnvironment(projectId)
    }

    fun prepareEmbeddedPythonEnvironment(
        project: V04ProjectGateway.RuntimeProject,
        progress: ((String) -> Unit)? = null,
    ): InternalEnvironmentPreparationResult {
        val route = resolveControlPath(
            project = project,
            action = Action.PREPARE,
            request = RuntimeControlRequest.EMBEDDED_R,
        )
        check(route.path == RuntimeControlPath.EMBEDDED_R) {
            "Embedded R prepare route rejected: " + route.reason
        }
        val projectId = project.summary.documentId
        val files = internalDependencyFiles(projectId)
        val cpythonSession = embeddedPythonSession
        val cpythonManager = embeddedPythonEnvironmentManager
        var cpythonError: Throwable? = null

        if (cpythonSession != null && cpythonManager != null) {
            progress?.invoke(
                listOf(
                    "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                    "SIFTALPHA_X_PROJECT_ID=" + projectId,
                    "SIFTALPHA_X_ENVIRONMENT_STAGE=PREPARING",
                    "SIFTALPHA_X_INTERNAL_PREPARE_STAGE=CPYTHON",
                ).joinToString("\n"),
            )
            try {
                val input = EmbeddedPythonRequirementParserV1.fromProjectFiles(
                    files.requirementsText,
                    files.pyprojectText,
                )
                cpythonSession.prepareRuntime()
                val prepared = cpythonManager.prepare(
                    projectIdentity = projectId,
                    dependencyInput = input,
                )
                return InternalEnvironmentPreparationResult(
                    ready = prepared.ready,
                    outcome = when (prepared.outcome) {
                        EmbeddedPythonEnvironmentManager.Outcome.READY_REUSED ->
                            InternalEnvironmentOutcome.CPYTHON_READY_REUSED
                        EmbeddedPythonEnvironmentManager.Outcome.READY_NO_EXTERNAL_DEPENDENCIES ->
                            InternalEnvironmentOutcome.CPYTHON_READY_NO_EXTERNAL_DEPENDENCIES
                        EmbeddedPythonEnvironmentManager.Outcome.READY_DEPENDENCIES_INSTALLED ->
                            InternalEnvironmentOutcome.CPYTHON_READY_DEPENDENCIES_INSTALLED
                    },
                    backend = InternalPythonBackend.CPYTHON,
                )
            } catch (error: Throwable) {
                if (InternalPythonPreparationFallbackPolicy.backendFor(error) != InternalPythonBackend.ALPINE) throw error
                cpythonError = error
                progress?.invoke(
                    listOf(
                        "SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R",
                        "SIFTALPHA_X_PROJECT_ID=" + projectId,
                        "SIFTALPHA_X_ENVIRONMENT_STAGE=PREPARING",
                        "SIFTALPHA_X_INTERNAL_PREPARE_STAGE=ALPINE_FALLBACK",
                        "SIFTALPHA_X_INTERNAL_PREPARE_FALLBACK_REASON=" +
                            error.message.orEmpty().lineSequence().firstOrNull().orEmpty(),
                    ).joinToString("\n"),
                )
            }
        }

        val alpineManager = internalAlpineEnvironmentManager
            ?: throw cpythonError ?: error("Internal Alpine environment manager is unavailable")
        val stager = embeddedPythonProjectStager
            ?: throw cpythonError ?: error("Internal project staging is unavailable")
        val staged = stager.stageAll(projectId)
        return try {
            val prepared = alpineManager.prepare(
                projectIdentity = projectId,
                stagedProject = staged,
                source = files.alpineSource,
                progress = progress,
            )
            InternalEnvironmentPreparationResult(
                ready = prepared.ready,
                outcome = when (prepared.outcome) {
                    InternalAlpineEnvironmentManager.Outcome.READY_REUSED ->
                        InternalEnvironmentOutcome.ALPINE_READY_REUSED
                    InternalAlpineEnvironmentManager.Outcome.READY_ENVIRONMENT_INSTALLED ->
                        InternalEnvironmentOutcome.ALPINE_READY_ENVIRONMENT_INSTALLED
                },
                backend = InternalPythonBackend.ALPINE,
            )
        } finally {
            stager.cleanup(staged)
        }
    }

    /** M-only control entry for an explicitly selected Python project; Termux is not involved. */
    fun startEmbeddedPython(
        project: V04ProjectGateway.RuntimeProject,
        requiredConfiguration: Boolean = false,
    ): EmbeddedPythonSnapshot {
        val stager = checkNotNull(embeddedPythonProjectStager) { "Internal project staging is unavailable" }
        val route = resolveControlPath(
            project = project,
            action = Action.START,
            request = RuntimeControlRequest.EMBEDDED_R,
            requiredConfiguration = requiredConfiguration,
        )
        check(route.path == RuntimeControlPath.EMBEDDED_R) {
            "Embedded R route rejected: " + route.reason
        }
        val selection = project.runtimeSelection as? ProjectRuntimeExecutionPlanner.Selection.Resolved
        check(selection?.primary == RuntimeKind.PYTHON) {
            "Internal Python requires a resolved Python project"
        }
        val projectId = project.summary.documentId
        val files = internalDependencyFiles(projectId)
        val cpythonInput = runCatching {
            EmbeddedPythonRequirementParserV1.fromProjectFiles(
                files.requirementsText,
                files.pyprojectText,
            )
        }.getOrNull()
        val cpythonBinding = cpythonInput?.let { input ->
            embeddedPythonEnvironmentManager?.loadBinding(projectId, input)
        }
        val alpineBinding = internalAlpineEnvironmentManager?.loadBinding(projectId, files.alpineSource)
        val entrypoint = gateway.resolveEmbeddedPythonEntrypoint(projectId)
            ?: error("EMBEDDED_R_ENTRYPOINT_UNRESOLVED")
        val stagedRoot = stager.stage(projectId, entrypoint)

        return try {
            val snapshot = if (cpythonBinding != null) {
                val session = checkNotNull(embeddedPythonSession) { "Embedded CPython is unavailable" }
                val current = session.snapshot()
                check(EmbeddedPythonStatePolicy.canStart(current.state)) {
                    "Only one embedded Python session may be active; current state is " + current.state
                }
                cleanupEmbeddedStaging(current)
                session.prepareRuntime()
                session.start(
                    projectIdentity = projectId,
                    executionRoot = stagedRoot,
                    entrypoint = entrypoint,
                    workingDirectory = ".",
                    environmentSitePackages = cpythonBinding.sitePackages,
                    environmentKey = cpythonBinding.environmentKey,
                )
            } else {
                val binding = alpineBinding ?: error("EMBEDDED_R_ENVIRONMENT_NOT_READY")
                val session = checkNotNull(internalAlpineSession) { "Internal Alpine is unavailable" }
                check(session.canStart(projectId)) { "Internal Alpine project session is already active" }
                session.start(
                    projectIdentity = projectId,
                    executionRoot = stagedRoot,
                    entrypoint = entrypoint,
                    environmentRoot = binding.environmentRoot,
                )
            }
            synchronized(embeddedStagingRoots) {
                embeddedStagingRoots[snapshot.sessionId] = stagedRoot
            }
            cleanupEmbeddedStaging(snapshot)
            snapshot
        } catch (error: Throwable) {
            stager.cleanup(stagedRoot)
            throw error
        }
    }

    private fun internalDependencyFiles(projectDocumentId: String): InternalDependencyFiles {
        val requirements = gateway.readProjectRootText(projectDocumentId, "requirements.txt")
        val pyproject = gateway.readProjectRootText(projectDocumentId, "pyproject.toml")
        return InternalDependencyFiles(
            requirementsText = requirements,
            pyprojectText = pyproject,
            alpineSource = InternalAlpineDependencySource.fromProjectFiles(requirements, pyproject),
        )
    }

    private fun embeddedPythonDependencyInput(
        projectDocumentId: String,
    ): EmbeddedPythonDependencyInputV1 {
        val requirements = gateway.readProjectRootText(
            projectDocumentId,
            "requirements.txt",
        )
        val pyproject = gateway.readProjectRootText(
            projectDocumentId,
            "pyproject.toml",
        )
        val input = EmbeddedPythonRequirementParserV1.fromProjectFiles(
            requirementsText = requirements,
            pyprojectText = pyproject,
        )
        val facts = gateway.runtimeFacts(projectDocumentId)
        check(
            input.sourceKind != EmbeddedPythonDependencyInputV1.SourceKind.NONE ||
                !facts.hasExternalDependencyRequirement
        ) {
            "UNSUPPORTED_DEPENDENCY_MANIFEST: use root requirements.txt or [project].dependencies"
        }
        return input
    }

    /** A successful return only means the stop request was accepted; terminal state is polled. */
    fun requestEmbeddedPythonStop(projectDocumentId: String): Boolean {
        val snapshot = embeddedPythonSnapshotFor(projectDocumentId) ?: return false
        if (!EmbeddedPythonStatePolicy.canStop(snapshot.state)) return false
        return when (snapshot.engine) {
            InternalPythonBackend.CPYTHON -> embeddedPythonSession?.requestStop() == true
            InternalPythonBackend.ALPINE -> internalAlpineSession?.requestStop(projectDocumentId) == true
        }
    }

    private fun cleanupEmbeddedStaging(snapshot: EmbeddedPythonSnapshot) {
        if (!EmbeddedPythonStatePolicy.isTerminal(snapshot.state)) return
        val root = synchronized(embeddedStagingRoots) {
            embeddedStagingRoots[snapshot.sessionId]
        } ?: return
        val stager = embeddedPythonProjectStager ?: return
        runCatching { stager.cleanup(root) }
            .onSuccess {
                synchronized(embeddedStagingRoots) {
                    embeddedStagingRoots.remove(snapshot.sessionId)
                }
            }
    }

    fun runtimeUnsupportedReason(): String = host.runtimeUnsupportedReason()

    fun wrapCancelableExternalActivity(
        project: V04ProjectGateway.RuntimeProject,
        action: Action,
        command: RuntimeCommand,
    ): RuntimeCommand {
        val operation = when (action) {
            Action.PREPARE -> "prepare"
            Action.START -> "start"
            Action.STATUS -> "status"
            Action.LOGS -> "logs"
            Action.CLEAN -> "clean"
            Action.STOP -> "stop"
            Action.CLONE_GITHUB -> null
        } ?: return command
        return command.copy(
            shellScript = host.wrapProjectActivity(
                runtimeId = host.runtimeId(project.folderName),
                operation = operation,
                shellScript = command.shellScript,
                timeoutMs = RuntimeOperationContract.timeoutMs(
                    when (action) {
                        Action.PREPARE -> RuntimeOperationAction.PREPARE
                        Action.START -> RuntimeOperationAction.START
                        Action.STATUS -> RuntimeOperationAction.STATUS
                        Action.LOGS -> RuntimeOperationAction.LOGS
                        Action.STOP -> RuntimeOperationAction.STOP
                        Action.CLEAN -> RuntimeOperationAction.CLEAN
                        Action.CLONE_GITHUB -> error("clone has no runtime operation")
                    },
                ),
            ),
        )
    }

    /**
     * Resolve a one-shot Python launch at START time. Project metadata remains authoritative; this
     * action-time read is intentionally not used by card refresh.
     */
    fun resolvePythonLaunch(
        project: V04ProjectGateway.RuntimeProject,
    ): PythonCliLaunchResolver.Resolution {
        val projectId = project.summary.documentId
        val facts = gateway.runtimeFacts(projectId)
        val pyprojectToml = if (
            facts.declaredRun.isNullOrBlank() &&
            facts.relativePaths.any { it == "pyproject.toml" }
        ) {
            gateway.readProjectRootText(projectId, "pyproject.toml")
        } else {
            null
        }
        val strictFallback = if (facts.declaredRun.isNullOrBlank()) {
            gateway.resolveEmbeddedPythonEntrypoint(projectId)
        } else {
            null
        }
        val fallbackEntrypoint = PythonLaunchCompatibilityPolicy.fallbackEntrypoint(
            summaryEntry = project.summary.entry,
            relativePaths = facts.relativePaths,
            strictFallback = strictFallback,
        )
        val resolution = PythonCliLaunchResolver.resolve(
            declaredRun = facts.declaredRun,
            pyprojectToml = pyprojectToml,
            fallbackEntrypoint = fallbackEntrypoint,
        )
        return PythonLaunchCompatibilityPolicy.preserveLegacyRun(
            resolution = resolution,
            legacyRun = project.summary.run,
        )
    }

    fun prepare(project: V04ProjectGateway.RuntimeProject): RuntimeCommand {
        val context = executionContext(project)
        val resolved = context.selection as? ProjectRuntimeExecutionPlanner.Selection.Resolved
            ?: return selectionError(project, context.selection)
        return when (resolved.primary) {
            RuntimeKind.PYTHON -> {
                val python = PythonDependencyDiagnostics.wrapPrepareFailure(
                    base = pythonAdapter.prepare(context.spec),
                    project = context.spec,
                    host = host,
                )
                if (RuntimeSupplementalCompositionPolicy.requiresNode(resolved)) {
                    val node = supplementalNodeAdapter.prepareDetectedWebComponents(context.spec)
                    RuntimeEnvironmentComposer.prepare(
                        host = host,
                        projectName = context.spec.name,
                        primary = python,
                        node = node,
                    )
                } else {
                    python
                }
            }
            RuntimeKind.NODE_JS -> nodeExecutableAdapter.prepare(context.spec)
            else -> executableUnavailable(project, resolved.primary)
        }
    }

    /**
     * PREPARE progress is intentionally cheap and read-only. Do not recursively rescan the repository
     * every two seconds merely to tail the shared prepare log.
     */
    fun prepareProgress(project: V04ProjectGateway.RuntimeProject): RuntimeCommand =
        PrepareProgressProbe.command(basicSpec(project), host)

    fun start(project: V04ProjectGateway.RuntimeProject): RuntimeCommand =
        start(project, pythonLaunchInvocation = null)

    /**
     * Start with an optional action-time structured Python argv contract. A null invocation keeps
     * the existing declared-run / automatic-entry compatibility behavior.
     */
    fun start(
        project: V04ProjectGateway.RuntimeProject,
        pythonLaunchInvocation: PythonLaunchInvocation?,
        webLogDiscoveryAllowed: Boolean = false,
    ): RuntimeCommand {
        val context = executionContext(project, webLogDiscoveryAllowed)
        val resolved = context.selection as? ProjectRuntimeExecutionPlanner.Selection.Resolved
            ?: return selectionError(project, context.selection)
        if (pythonLaunchInvocation != null && resolved.primary != RuntimeKind.PYTHON) {
            return executableUnavailable(project, resolved.primary)
        }
        return when (resolved.primary) {
            RuntimeKind.PYTHON -> {
                val effectiveSpec = context.spec.copy(
                    pythonLaunchInvocation = pythonLaunchInvocation,
                )
                val python = PythonDependencyDiagnostics.wrapStartFailure(
                    base = pythonAdapter.start(effectiveSpec),
                    project = effectiveSpec,
                    host = host,
                )
                if (RuntimeSupplementalCompositionPolicy.requiresNode(resolved)) {
                    RuntimeEnvironmentComposer.start(
                        host = host,
                        projectName = effectiveSpec.name,
                        primary = python,
                        nodeStatus = supplementalNodeAdapter.statusDetectedWebComponents(effectiveSpec),
                    )
                } else {
                    python
                }
            }
            RuntimeKind.NODE_JS -> nodeExecutableAdapter.start(context.spec)
            else -> executableUnavailable(project, resolved.primary)
        }
    }

    /**
     * STOP is process ownership, not source detection. It must remain available after files were edited,
     * deleted or became ambiguous, so it never depends on a recursive project scan.
     */
    fun stop(project: V04ProjectGateway.RuntimeProject): RuntimeCommand {
        val spec = basicSpec(project)
        return ManagedProcessRuntime.stop(host, spec, host.runtimeId(spec.folderName))
    }

    fun status(
        project: V04ProjectGateway.RuntimeProject,
        webLogDiscoveryAllowed: Boolean = false,
    ): RuntimeCommand {
        val context = executionContext(project, webLogDiscoveryAllowed)
        val resolved = context.selection as? ProjectRuntimeExecutionPlanner.Selection.Resolved
            ?: return unresolvedStatus(context)
        return when (resolved.primary) {
            RuntimeKind.PYTHON -> {
                val python = pythonAdapter.status(context.spec)
                if (RuntimeSupplementalCompositionPolicy.requiresNode(resolved)) {
                    RuntimeEnvironmentComposer.status(
                        host = host,
                        projectName = context.spec.name,
                        primary = python,
                        node = supplementalNodeAdapter.statusDetectedWebComponents(context.spec),
                    )
                } else {
                    python
                }
            }
            RuntimeKind.NODE_JS -> nodeExecutableAdapter.status(context.spec)
            else -> executableUnavailable(project, resolved.primary)
        }
    }

    fun logs(
        project: V04ProjectGateway.RuntimeProject,
        webLogDiscoveryAllowed: Boolean = false,
    ): RuntimeCommand {
        val context = executionContext(project, webLogDiscoveryAllowed)
        val resolved = context.selection as? ProjectRuntimeExecutionPlanner.Selection.Resolved
        return when (resolved?.primary) {
            RuntimeKind.PYTHON -> PythonDependencyDiagnostics.appendRuntimeLogDiagnosis(
                base = pythonAdapter.logs(context.spec),
                project = context.spec,
                host = host,
            )
            RuntimeKind.NODE_JS -> nodeExecutableAdapter.logs(context.spec)
            else -> ManagedProcessRuntime.logs(host, context.spec, host.runtimeId(context.spec.folderName))
        }
    }

    fun clean(project: V04ProjectGateway.RuntimeProject): RuntimeCommand {
        val context = executionContext(project)
        val resolved = context.selection as? ProjectRuntimeExecutionPlanner.Selection.Resolved
            ?: return cleanUnresolved(project)
        return when (resolved.primary) {
            RuntimeKind.PYTHON -> {
                val python = pythonAdapter.clean(context.spec)
                if (RuntimeSupplementalCompositionPolicy.requiresNode(resolved)) {
                    RuntimeEnvironmentComposer.clean(
                        host = host,
                        projectName = context.spec.name,
                        primary = python,
                        node = supplementalNodeAdapter.cleanDetectedWebComponents(context.spec),
                    )
                } else {
                    python
                }
            }
            RuntimeKind.NODE_JS -> nodeExecutableAdapter.clean(context.spec)
            else -> executableUnavailable(project, resolved.primary)
        }
    }

    fun cloneGitHub(spec: GitHubCloneSpec): RuntimeCommand {
        val destination = "/root/projects/${spec.projectName}"
        val id = host.runtimeId(spec.projectName)
        val log = "/root/siftalpha/logs/clone-$id.log"
        val inner = """
            set -e
            mkdir -p /root/siftalpha/logs
            dest=${host.sh(destination)}
            log=${host.sh(log)}
            if [ -e "${'$'}dest" ]; then
              echo 'SIFTALPHA_ERROR=PROJECT_EXISTS'
              exit 61
            fi
            if ! command -v git >/dev/null 2>&1; then
              : >"${'$'}log"
              export DEBIAN_FRONTEND=noninteractive
              apt-get update >>"${'$'}log" 2>&1
              apt-get install -y git >>"${'$'}log" 2>&1
            fi
            : >"${'$'}log"
            if git clone --depth 1 --branch ${host.sh(spec.branch)} -- ${host.sh(spec.cloneUrl)} "${'$'}dest" >>"${'$'}log" 2>&1; then
              echo 'SIFTALPHA_CLONE=OK'
              tail -n 30 "${'$'}log" 2>/dev/null || true
            else
              code=${'$'}?
              rm -rf -- "${'$'}dest"
              echo 'SIFTALPHA_CLONE=FAILED'
              tail -n 40 "${'$'}log" 2>/dev/null || true
              exit "${'$'}code"
            fi
        """.trimIndent()
        return RuntimeCommand(
            shellScript = host.wrapUbuntu(inner),
            label = "GitHub 导入 · ${spec.projectName}",
            description = "从 GitHub 克隆项目到当前项目目录。",
        )
    }

    private fun executionContext(
        project: V04ProjectGateway.RuntimeProject,
        webLogDiscoveryAllowed: Boolean = false,
    ): ExecutionContext {
        val facts = gateway.runtimeFacts(project.summary.documentId)
        val selection = ProjectRuntimeExecutionPlanner.select(
            relativePaths = facts.relativePaths,
            declaredType = facts.declaredType,
        )
        val resolvedPrimary = (selection as? ProjectRuntimeExecutionPlanner.Selection.Resolved)?.primary
        val resolvedEntry = facts.declaredEntry ?: when (resolvedPrimary) {
            RuntimeKind.NODE_JS -> project.summary.entry.takeUnless { it.endsWith(".py", ignoreCase = true) }.orEmpty()
            else -> project.summary.entry
        }
        val resolvedRun = facts.declaredRun ?: when (resolvedPrimary) {
            RuntimeKind.NODE_JS -> project.summary.run.takeUnless {
                val value = it.trim()
                value.startsWith("python ") || value.startsWith("python3 ")
            }.orEmpty()
            else -> project.summary.run
        }
        return ExecutionContext(
            spec = RuntimeProjectSpec(
                name = project.summary.name,
                folderName = project.folderName,
                entry = resolvedEntry,
                run = resolvedRun,
                declaredType = facts.declaredType,
                declaredEntry = facts.declaredEntry,
                declaredRun = facts.declaredRun,
                relativePaths = facts.relativePaths,
                webLogDiscoveryAllowed = webLogDiscoveryAllowed,
            ),
            selection = selection,
        )
    }

    private fun basicSpec(project: V04ProjectGateway.RuntimeProject): RuntimeProjectSpec = RuntimeProjectSpec(
        name = project.summary.name,
        folderName = project.folderName,
        entry = project.summary.entry,
        run = project.summary.run,
    )

    private fun selectionError(
        project: V04ProjectGateway.RuntimeProject,
        selection: ProjectRuntimeExecutionPlanner.Selection,
    ): RuntimeCommand {
        val lines = when (selection) {
            is ProjectRuntimeExecutionPlanner.Selection.Ambiguous -> listOf(
                "SIFTALPHA_ERROR=RUNTIME_SELECTION_AMBIGUOUS",
                "SIFTALPHA_RUNTIME_CANDIDATES=${selection.candidates.joinToString(",") { it.id }}",
                "SIFTALPHA_ENV=NOT_READY",
            )
            is ProjectRuntimeExecutionPlanner.Selection.Unsupported -> listOf(
                "SIFTALPHA_ERROR=RUNTIME_UNSUPPORTED_OR_UNKNOWN",
                "SIFTALPHA_ENV=NOT_READY",
            )
            is ProjectRuntimeExecutionPlanner.Selection.Resolved -> error("resolved selection is not an error")
        }
        return simpleError(project.summary.name, lines, 81)
    }

    private fun executableUnavailable(
        project: V04ProjectGateway.RuntimeProject,
        kind: RuntimeKind,
    ): RuntimeCommand = simpleError(
        project.summary.name,
        listOf(
            "SIFTALPHA_ERROR=RUNTIME_EXECUTION_UNSUPPORTED",
            "SIFTALPHA_RUNTIME_KIND=${kind.id}",
            "SIFTALPHA_ENV=NOT_READY",
        ),
        81,
    )

    private fun unresolvedStatus(context: ExecutionContext): RuntimeCommand {
        val id = host.runtimeId(context.spec.folderName)
        val state = ManagedProcessRuntime.defaultGuestPaths(id).state
        val reason = when (context.selection) {
            is ProjectRuntimeExecutionPlanner.Selection.Ambiguous -> "RUNTIME_SELECTION_AMBIGUOUS"
            is ProjectRuntimeExecutionPlanner.Selection.Unsupported -> "RUNTIME_UNSUPPORTED_OR_UNKNOWN"
            is ProjectRuntimeExecutionPlanner.Selection.Resolved -> error("resolved status")
        }
        val guestStatus = """
            echo 'SIFTALPHA_ENV=NOT_READY'
            echo 'SIFTALPHA_ENV_REASON=$reason'
            if [ -f ${host.sh(state)} ]; then cat ${host.sh(state)}; fi
        """.trimIndent()
        return ManagedProcessRuntime.status(host, context.spec, id, guestStatus)
    }

    /**
     * CLEAN is a recovery operation. When selection is ambiguous, clean all known project-specific
     * Runtime state while preserving source and shared toolchains instead of trapping the user in an
     * uncleanable state.
     */
    private fun cleanUnresolved(project: V04ProjectGateway.RuntimeProject): RuntimeCommand {
        val spec = basicSpec(project)
        val id = host.runtimeId(spec.folderName)
        val paths = ManagedProcessRuntime.defaultGuestPaths(id)
        val guestClean = """
            rm -rf -- ${host.sh("/root/venvs/$id")} \
                       ${host.sh("/root/siftalpha/node-workspaces/$id")} \
                       ${host.sh("/root/siftalpha/node-exec-workspaces/$id")}
            rm -f -- ${host.sh("/root/siftalpha/env-ready-$id.txt")} \
                      ${host.sh("/root/siftalpha/node-ready-$id.txt")} \
                      ${host.sh("/root/siftalpha/node-exec-ready-$id.txt")} \
                      ${host.sh("/root/siftalpha/logs/prepare-$id.log")} \
                      ${host.sh(paths.runLog)} ${host.sh(paths.runner)} ${host.sh(paths.state)} ${host.sh(paths.secrets)}
            echo 'SIFTALPHA_RUNTIME_RECOVERY_CLEAN=1'
        """.trimIndent()
        return ManagedProcessRuntime.clean(host, spec, id, guestClean)
    }

    private fun simpleError(
        projectName: String,
        lines: List<String>,
        exitCode: Int,
    ): RuntimeCommand = RuntimeCommand(
        shellScript = buildString {
            appendLine("set +e")
            lines.forEach { appendLine("echo ${host.sh(it)}") }
            append("exit $exitCode")
        },
        label = "$projectName · Runtime",
        description = "$projectName · 无法安全确定可执行 Runtime。",
    )
}
