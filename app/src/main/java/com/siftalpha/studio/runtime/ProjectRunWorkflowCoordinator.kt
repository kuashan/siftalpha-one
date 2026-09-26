package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.PythonCliArgumentKind
import com.siftalpha.studio.project.PythonCliRequirement
import com.siftalpha.studio.project.V04ProjectGateway
import com.siftalpha.studio.project.WebProjectInspector

/**
 * Shared M-layer RUN workflow.
 *
 * It does not render UI and it does not own a second Runtime state machine. It resolves the
 * action-time launch contract, records high-confidence runtime configuration discoveries, and
 * remembers a pending RUN intent while configuration / CLI input is being recovered.
 */
class ProjectRunWorkflowCoordinator(
    private val runtime: ProjectRuntimeController,
    private val webInspector: WebProjectInspector,
    private val learnedWebLaunchStore: RuntimeWebLearnedLaunchStore,
    private val configurationDiscoveryStore: RuntimeConfigurationDiscoveryStore,
    private val recoveryStore: ProjectRunRecoveryStore,
) {

    data class ConfigurationFacts(
        val missingRequiredNames: List<String>,
        val cliRequirements: List<PythonCliRequirement>,
    )

    sealed interface Preparation {
        data class Ready(
            val request: ProjectControlHub.RunRequest,
        ) : Preparation

        data class NeedsConfiguration(
            val missingNames: List<String>,
        ) : Preparation

        data class NeedsLaunchInput(
            val plan: LaunchInputPlan,
        ) : Preparation

        data class Rejected(
            val reason: RejectReason,
        ) : Preparation
    }

    enum class RejectReason {
        PYTHON_LAUNCH_MISSING,
        PYTHON_LAUNCH_INVALID,
    }

    enum class TargetKind {
        CONSOLE_SCRIPT,
        PYTHON_FILE,
    }

    data class LaunchTarget(
        val id: String,
        val label: String,
        val kind: TargetKind,
        val entrypoint: String? = null,
    )

    data class LaunchInputPlan(
        val targets: List<LaunchTarget>,
        val requiredArguments: List<PythonCliRequirement>,
        val webLogDiscoveryAllowed: Boolean,
        val webHintPorts: List<Int>,
    )

    sealed interface InputResolution {
        data class Ready(val request: ProjectControlHub.RunRequest) : InputResolution
        data class Invalid(val token: String? = null) : InputResolution
    }

    data class RuntimeFinding(
        val finding: RuntimeConfigurationDiagnostic.Result,
        val recoveryReason: ProjectRunRecoveryStore.Reason?,
        val changed: Boolean,
    ) {
        val actionable: Boolean
            get() = recoveryReason != null
    }

    fun prepare(
        project: V04ProjectGateway.RuntimeProject,
        configuration: ConfigurationFacts,
        webHintPorts: List<Int>,
    ): Preparation {
        val projectId = project.summary.documentId
        if (configuration.missingRequiredNames.isNotEmpty()) {
            recoveryStore.mark(projectId, ProjectRunRecoveryStore.Reason.CONFIGURATION)
            return Preparation.NeedsConfiguration(configuration.missingRequiredNames)
        }

        val webProfile = runCatching { webInspector.inspect(projectId) }
            .getOrElse { WebProjectInspector.Profile(false, null, "none", null, null) }
        val resolvedSelection =
            project.runtimeSelection as? ProjectRuntimeExecutionPlanner.Selection.Resolved

        if (resolvedSelection?.primary != RuntimeKind.PYTHON) {
            return Preparation.Ready(
                ProjectControlHub.RunRequest(
                    webLogDiscoveryAllowed = webProfile.enabled,
                    webHintPorts = webHintPorts,
                ),
            )
        }

        // A bounded presentation-oriented Web profile must not be the sole gate for launch
        // resolution. Unless Web was explicitly disabled by project config, let the authoritative
        // runtime source snapshot prove (or reject) the Python Web launch contract.
        val webLaunchAllowed = webProfile.enabled || webProfile.source != "config"
        val nativeWebResolution = if (webLaunchAllowed) {
            runCatching {
                runtime.resolvePythonNativeWebLaunchResolution(
                    project = project,
                    webProjectEnabled = true,
                )
            }.getOrNull()
        } else {
            null
        }
        val learnedNativeWebLaunch = nativeWebResolution?.let { resolution ->
            learnedWebLaunchStore.readVerified(
                projectKey = projectId,
                sourceFingerprint = resolution.sourceFingerprint,
            )
        }
        val nativeWebLaunch = learnedNativeWebLaunch ?: nativeWebResolution?.candidate
        if (nativeWebLaunch != null && nativeWebResolution != null) {
            if (learnedNativeWebLaunch == null) {
                learnedWebLaunchStore.rememberDiscovered(
                    projectKey = projectId,
                    candidate = nativeWebLaunch,
                    sourceFingerprint = nativeWebResolution.sourceFingerprint,
                )
            }
            return Preparation.Ready(
                ProjectControlHub.RunRequest(
                    launchInvocation = nativeWebLaunch.toInvocation(),
                    webLogDiscoveryAllowed = true,
                    webHintPorts = webHintPorts,
                ),
            )
        }

        val effectiveWebEnabled = webProfile.enabled || nativeWebResolution != null
        val requiredCli = configuration.cliRequirements.filter { it.required }
        val shouldResolveCli = !effectiveWebEnabled || requiredCli.isNotEmpty()
        if (!shouldResolveCli) {
            return Preparation.Ready(
                ProjectControlHub.RunRequest(
                    webLogDiscoveryAllowed = effectiveWebEnabled,
                    webHintPorts = webHintPorts,
                ),
            )
        }

        return when (
            val resolution = runCatching {
                runtime.resolvePythonLaunch(
                    project = project,
                    cliRequirements = requiredCli,
                )
            }.getOrElse {
                return Preparation.Rejected(RejectReason.PYTHON_LAUNCH_INVALID)
            }
        ) {
            is PythonCliLaunchResolver.Resolution.DeclaredRun ->
                Preparation.Ready(
                    ProjectControlHub.RunRequest(
                        webLogDiscoveryAllowed = webEnabled,
                        webHintPorts = webHintPorts,
                    ),
                )

            is PythonCliLaunchResolver.Resolution.ConsoleScripts -> {
                val targets = resolution.names.map { name ->
                    LaunchTarget(
                        id = name,
                        label = name,
                        kind = TargetKind.CONSOLE_SCRIPT,
                    )
                }
                if (targets.size == 1 && requiredCli.isEmpty()) {
                    Preparation.Ready(
                        ProjectControlHub.RunRequest(
                            launchInvocation = PythonLaunchInvocation.consoleScript(targets.single().id),
                            webLogDiscoveryAllowed = webEnabled,
                            webHintPorts = webHintPorts,
                        ),
                    )
                } else {
                    recoveryStore.mark(projectId, ProjectRunRecoveryStore.Reason.CLI_ARGUMENTS)
                    Preparation.NeedsLaunchInput(
                        LaunchInputPlan(
                            targets = targets,
                            requiredArguments = requiredCli,
                            webLogDiscoveryAllowed = webEnabled,
                            webHintPorts = webHintPorts,
                        ),
                    )
                }
            }

            is PythonCliLaunchResolver.Resolution.PythonFile -> {
                val target = LaunchTarget(
                    id = resolution.entrypoint,
                    label = resolution.entrypoint,
                    kind = TargetKind.PYTHON_FILE,
                    entrypoint = resolution.entrypoint,
                )
                if (requiredCli.isEmpty()) {
                    Preparation.Ready(
                        ProjectControlHub.RunRequest(
                            launchInvocation = PythonLaunchInvocation.pythonFile(resolution.entrypoint),
                            webLogDiscoveryAllowed = webEnabled,
                            webHintPorts = webHintPorts,
                        ),
                    )
                } else {
                    recoveryStore.mark(projectId, ProjectRunRecoveryStore.Reason.CLI_ARGUMENTS)
                    Preparation.NeedsLaunchInput(
                        LaunchInputPlan(
                            targets = listOf(target),
                            requiredArguments = requiredCli,
                            webLogDiscoveryAllowed = webEnabled,
                            webHintPorts = webHintPorts,
                        ),
                    )
                }
            }

            PythonCliLaunchResolver.Resolution.Missing ->
                Preparation.Rejected(RejectReason.PYTHON_LAUNCH_MISSING)

            is PythonCliLaunchResolver.Resolution.Invalid ->
                Preparation.Rejected(RejectReason.PYTHON_LAUNCH_INVALID)
        }
    }

    fun resolveInput(
        plan: LaunchInputPlan,
        selectedTargetId: String?,
        values: Map<String, String>,
        additionalArguments: String,
    ): InputResolution {
        val target = when {
            plan.targets.size == 1 -> plan.targets.single()
            else -> plan.targets.firstOrNull { it.id == selectedTargetId }
        } ?: return InputResolution.Invalid()

        val structuredArguments = mutableListOf<String>()
        plan.requiredArguments.forEach { requirement ->
            val value = values[requirement.token].orEmpty()
            if (value.isBlank()) {
                return InputResolution.Invalid(requirement.token)
            }
            when (requirement.kind) {
                PythonCliArgumentKind.POSITIONAL -> structuredArguments += value
                PythonCliArgumentKind.OPTION -> {
                    structuredArguments += requirement.token
                    structuredArguments += value
                }
            }
        }

        val extra = when (val parsed = RuntimeArgumentParser.parse(additionalArguments)) {
            is RuntimeArgumentParser.Result.Success -> parsed.arguments
            is RuntimeArgumentParser.Result.Invalid -> return InputResolution.Invalid()
        }
        val arguments = structuredArguments + extra
        val invocation = runCatching {
            when (target.kind) {
                TargetKind.CONSOLE_SCRIPT ->
                    PythonLaunchInvocation.consoleScript(target.id, arguments)
                TargetKind.PYTHON_FILE ->
                    PythonLaunchInvocation.pythonFile(
                        target.entrypoint ?: target.id,
                        arguments,
                    )
            }
        }.getOrElse {
            return InputResolution.Invalid()
        }

        return InputResolution.Ready(
            ProjectControlHub.RunRequest(
                launchInvocation = invocation,
                webLogDiscoveryAllowed = plan.webLogDiscoveryAllowed,
                webHintPorts = plan.webHintPorts,
            ),
        )
    }

    fun observeRuntimeOutput(
        project: V04ProjectGateway.RuntimeProject,
        output: String,
    ): RuntimeFinding {
        val record = configurationDiscoveryStore.record(project.folderName, output)
        val finding = record.finding
        val reason = when {
            finding.missingEnvironmentNames.isNotEmpty() || finding.unnamedCredentialRequired ->
                ProjectRunRecoveryStore.Reason.CONFIGURATION
            finding.missingCliArguments.isNotEmpty() ->
                ProjectRunRecoveryStore.Reason.CLI_ARGUMENTS
            else -> null
        }
        if (reason != null) {
            recoveryStore.mark(project.summary.documentId, reason)
        }
        return RuntimeFinding(
            finding = finding,
            recoveryReason = reason,
            changed = record.changed,
        )
    }

    fun pendingRecovery(projectId: String): ProjectRunRecoveryStore.Pending? =
        recoveryStore.read(projectId)

    fun completeRunDispatch(projectId: String) {
        recoveryStore.clear(projectId)
    }

    fun clearRecovery(projectId: String) {
        recoveryStore.clear(projectId)
    }
}
