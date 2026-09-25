package com.siftalpha.macos

import com.siftalpha.core.lifecycle.ProjectLifecycleState
import com.siftalpha.core.storage.PlatformStateStorage
import com.siftalpha.studio.container.ComposeProjectPlan
import com.siftalpha.studio.container.ComposeProjectPlanner
import com.siftalpha.studio.runtime.RuntimeKind
import java.io.File
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

data class MacProductProject(
    val imported: MacImportedProject,
    val snapshot: MacProjectSnapshot,
    val plan: MacProjectEnvironmentPlan,
    val composePlan: ComposeProjectPlan? = null,
) {
    val projectId: String get() = imported.projectId
    val name: String get() = imported.root.name
    val runtime: RuntimeKind? get() = plan.needs?.primaryRuntime
    val isCompose: Boolean get() = composePlan?.isComposeProject == true
}

data class MacProductProjectView(
    val project: MacProductProject,
    val workflow: MacWorkflowStatus,
    val lastError: String?,
    val containerAdvice: MacContainerEnvironmentAdvice? = null,
    val containerInstallPlan: MacContainerInstallPlan? = null,
    val containerInstallProgress: MacContainerInstallProgress? = null,
) {
    val resultUrl: String? get() = workflow.webEndpoint?.url
}

data class MacDeveloperProjectView(
    val project: MacProductProject,
    val workflow: MacWorkflowStatus,
    val environment: MacPreparedEnvironment?,
    val entrypoint: String?,
    val runtimeVersion: String?,
    val ownedPids: Set<Long>,
    val stdout: String,
    val stderr: String,
    val combinedLogs: String,
    val logsTruncated: Boolean,
    val lastError: String?,
    val containerAdvice: MacContainerEnvironmentAdvice? = null,
    val containerInstallPlan: MacContainerInstallPlan? = null,
    val containerInstallProgress: MacContainerInstallProgress? = null,
    val containerProvider: String? = null,
    val composeProjectName: String? = null,
    val containerServices: List<MacComposeServiceStatus> = emptyList(),
) {
    val resultUrl: String? get() = workflow.webEndpoint?.url
    val webSource: String? get() = workflow.webEndpoint?.source?.name
    val endpointState: String
        get() = when {
            workflow.webEndpoint != null -> "AVAILABLE"
            workflow.processState.name == "RUNNING" -> "NOT_DISCOVERED"
            else -> "INACTIVE"
        }
}

class MacProductController(
    private val discovery: List<MacHostToolSnapshot> = MacHostRuntimeDiscovery().discoverAll(),
    private val filesystem: MacProjectFilesystem = MacProjectFilesystem(),
    private val managedPython: MacManagedPythonRuntime? = MacManagedPythonRuntime.locate(),
    private val containerProviderSnapshotSource: () -> List<MacContainerProviderSnapshot> = {
        MacContainerRuntimeDiscovery().discoverAll()
    },
    private val composeProviderFactory:
        (Collection<MacContainerProviderSnapshot>) -> MacComposeContainerProvider? =
        MacComposeProviderSelector::select,
    private val systemFacts: MacSystemFacts = MacSystemFactsDiscovery.discover(),
    private val containerInstaller: MacContainerEnvironmentInstaller =
        MacManagedContainerInstaller(systemFacts),
    processControl: MacProjectProcessControl = MacProjectProcessControl(),
    dataRoot: File = MacProjectEnvironmentManager.defaultDataRoot(),
    stateStorage: PlatformStateStorage = MacFileStateStorage(File(dataRoot, "state/platform-state.properties")),
) {
    @Volatile
    private var latestContainerProviders: List<MacContainerProviderSnapshot> =
        containerProviderSnapshotSource()

    private val environmentManager = MacProjectEnvironmentManager(
        processControl = processControl,
        managedPython = managedPython,
        dataRoot = dataRoot,
    )
    private val coordinator = MacProjectWorkflowCoordinator(
        processControl = processControl,
        environmentManager = environmentManager,
        containerProviderSource = {
            composeProviderFactory(latestContainerProviders)
        },
    )
    private val projects = LinkedHashMap<String, MacProductProject>()
    private val projectCatalog = MacProjectCatalog(stateStorage)
    private val lastErrors = ConcurrentHashMap<String, String?>()
    private val containerInstallProgress = ConcurrentHashMap<String, MacContainerInstallProgress>()
    private val containerInstallLogs = ConcurrentHashMap<String, StringBuilder>()
    private val managedPythonVersion: String? by lazy { managedPython?.version() }

    init {
        restoreProjects()
    }

    @Synchronized
    fun importProject(directory: File): MacProductProject {
        refreshContainerProviders()
        val product = attachProject(directory)
        projectCatalog.add(product.imported.canonicalRootPath)
        lastErrors.remove(product.projectId)
        return product
    }

    @Synchronized
    fun removeProject(projectId: String): Boolean {
        val product = projects[projectId] ?: return false
        if (!coordinator.detach(projectId)) {
            lastErrors[projectId] = "项目正在运行或有操作进行中，请先停止后再移除。"
            return false
        }
        projects.remove(projectId)
        filesystem.forgetProject(projectId)
        projectCatalog.remove(product.imported.canonicalRootPath)
        lastErrors.remove(projectId)
        containerInstallProgress.remove(projectId)
        containerInstallLogs.remove(projectId)
        return true
    }

    fun clearProjectEnvironment(projectId: String): Boolean {
        val exists = synchronized(this) { projects.containsKey(projectId) }
        if (!exists) return false
        lastErrors.remove(projectId)
        val success = coordinator.clearEnvironment(projectId)
        if (!success) {
            lastErrors[projectId] = "无法清理项目环境。请先停止当前项目并等待正在执行的操作结束。"
        } else {
            containerInstallProgress.remove(projectId)
            containerInstallLogs.remove(projectId)
        }
        return success
    }

    @Synchronized
    private fun restoreProjects() {
        projectCatalog.paths().forEach { path ->
            val directory = File(path)
            if (!directory.isDirectory || !directory.canRead()) return@forEach
            runCatching { attachProject(directory) }
        }
    }

    private fun attachProject(directory: File): MacProductProject {
        val imported = filesystem.importDirectory(directory)
        val snapshot = MacProjectSnapshotBuilder(filesystem).build(imported)
        val plan = languagePlan(snapshot)
        val composePlan = composePlan(snapshot)
        val product = MacProductProject(imported, snapshot, plan, composePlan)
        projects[product.projectId] = product
        coordinator.attach(MacWorkflowContext(imported, snapshot, plan, composePlan))
        return product
    }

    @Synchronized
    fun refreshProject(projectId: String): MacProductProject? {
        val existing = projects[projectId] ?: return null
        refreshContainerProviders()
        val snapshot = MacProjectSnapshotBuilder(filesystem).build(existing.imported)
        val plan = languagePlan(snapshot)
        val composePlan = composePlan(snapshot)
        val refreshed = existing.copy(snapshot = snapshot, plan = plan, composePlan = composePlan)
        projects[projectId] = refreshed
        coordinator.attach(MacWorkflowContext(refreshed.imported, snapshot, plan, composePlan))
        return refreshed
    }

    @Synchronized
    fun projects(): List<MacProductProject> = projects.values.toList()

    @Synchronized
    fun project(projectId: String): MacProductProject? = projects[projectId]

    fun view(projectId: String): MacProductProjectView? {
        val product = synchronized(this) { projects[projectId] } ?: return null
        return MacProductProjectView(
            project = product,
            workflow = coordinator.status(projectId),
            lastError = lastErrors[projectId],
            containerAdvice = adviceFor(product),
            containerInstallPlan = installPlanFor(product),
            containerInstallProgress = containerInstallProgress[projectId],
        )
    }

    fun developerView(projectId: String, maxLogBytes: Int = 512 * 1024): MacDeveloperProjectView? {
        val product = synchronized(this) { projects[projectId] } ?: return null
        val diagnostics = coordinator.diagnostics(projectId, maxLogBytes)
        val runtimeVersion = when {
            product.isCompose -> latestContainerProviders
                .firstOrNull {
                    it.kind.id == diagnostics.containerProvider &&
                        it.availability.name == "AVAILABLE"
                }
                ?.let { it.version ?: it.composeVersion }
            product.runtime?.id == "python" -> managedPythonVersion
                ?: discovery.firstOrNull { it.kind == MacHostToolKind.PYTHON }?.version
            product.runtime?.id == "nodejs" ->
                discovery.firstOrNull { it.kind == MacHostToolKind.NODE_JS }?.version
            else -> null
        }
        return MacDeveloperProjectView(
            project = product,
            workflow = diagnostics.status,
            environment = diagnostics.environment,
            entrypoint = diagnostics.entrypoint,
            runtimeVersion = runtimeVersion,
            ownedPids = diagnostics.ownedPids,
            stdout = diagnostics.stdout,
            stderr = diagnostics.stderr,
            combinedLogs = diagnostics.combinedLogs,
            logsTruncated = diagnostics.logsTruncated,
            lastError = lastErrors[projectId],
            containerAdvice = adviceFor(product),
            containerInstallPlan = installPlanFor(product),
            containerInstallProgress = containerInstallProgress[projectId],
            containerProvider = diagnostics.containerProvider,
            composeProjectName = diagnostics.composeProjectName,
            containerServices = diagnostics.containerServices,
        )
    }

    fun installRecommendedContainerAndPrepare(projectId: String): Boolean {
        val product = synchronized(this) { projects[projectId] } ?: return false
        if (!product.isCompose) return false
        if (containerInstallProgress[projectId]?.active == true) return false

        refreshContainerProviders()
        val plan = installPlanFor(product) ?: run {
            lastErrors[projectId] = "当前没有可自动执行的容器安装方案，请刷新或检查已有容器环境。"
            return false
        }

        lastErrors.remove(projectId)
        containerInstallLogs[projectId] = StringBuilder()

        fun updateProgress(phase: MacContainerInstallPhase, message: String) {
            val lines = synchronized(containerInstallLogs.getValue(projectId)) {
                containerInstallLogs.getValue(projectId)
                    .toString()
                    .lineSequence()
                    .filter(String::isNotBlank)
                    .toList()
                    .takeLast(80)
            }
            containerInstallProgress[projectId] = MacContainerInstallProgress(
                phase = phase,
                message = message,
                logLines = lines,
            )
        }

        val installResult = containerInstaller.install(
            plan = plan,
            progress = ::updateProgress,
            log = { line ->
                val buffer = containerInstallLogs.getValue(projectId)
                synchronized(buffer) {
                    buffer.append(line).append('\n')
                    if (buffer.length > 200_000) {
                        buffer.delete(0, buffer.length - 160_000)
                    }
                }
            },
        )
        if (!installResult.success) {
            val detail = installResult.detail ?: "容器环境安装失败"
            lastErrors[projectId] = detail
            updateProgress(MacContainerInstallPhase.FAILED, detail)
            return false
        }

        val refreshed = refreshProject(projectId)
        if (refreshed == null || adviceFor(refreshed)?.state != MacContainerAdviceState.READY) {
            val detail = "安装步骤完成，但 SiftAlpha 重新检测后仍未发现可用的 Docker/Podman + Compose。"
            lastErrors[projectId] = detail
            updateProgress(MacContainerInstallPhase.FAILED, detail)
            return false
        }

        updateProgress(MacContainerInstallPhase.PREPARING_PROJECT, "容器环境已就绪，正在继续准备项目…")
        val prepareResult = coordinator.prepare(projectId)
        if (!prepareResult.success) {
            val detail = prepareResult.detail ?: "容器环境已安装，但项目准备失败。"
            lastErrors[projectId] = detail
            updateProgress(MacContainerInstallPhase.FAILED, detail)
            return false
        }

        updateProgress(MacContainerInstallPhase.COMPLETE, "容器环境和项目准备已完成。")
        return true
    }

    fun prepare(projectId: String): MacPrepareResult {
        lastErrors.remove(projectId)

        val product = synchronized(this) { projects[projectId] }
        if (product?.isCompose == true && !coordinator.status(projectId).environmentReady) {
            refreshContainerProviders()
            val refreshed = refreshProject(projectId) ?: product
            val advice = adviceFor(refreshed)
            val installPlan = installPlanFor(refreshed)
            if (advice != null &&
                advice.state != MacContainerAdviceState.READY &&
                installPlan != null
            ) {
                val provisioned = installRecommendedContainerAndPrepare(projectId)
                return if (provisioned) {
                    MacPrepareResult(
                        success = true,
                        cancelled = false,
                        environment = null,
                        lines = listOf("SIFTALPHA_ENVIRONMENT_PROVISION=PASS"),
                        detail = null,
                    )
                } else {
                    MacPrepareResult(
                        success = false,
                        cancelled = false,
                        environment = null,
                        lines = listOf("SIFTALPHA_ENVIRONMENT_PROVISION=FAILED"),
                        detail = lastErrors[projectId] ?: "运行环境准备失败",
                    )
                }
            }
        }

        val result = coordinator.prepare(projectId)
        if (
            product?.isCompose == true &&
            !result.success &&
            !result.cancelled &&
            MacManagedContainerDns.isRecoverableManagedNetworkFailure(result.detail) &&
            managedDockerProviderAvailable()
        ) {
            val repaired = repairManagedContainerNetwork(projectId)
            if (repaired) {
                refreshContainerProviders()
                val retry = coordinator.prepare(projectId)
                if (retry.success) {
                    containerInstallProgress[projectId] = MacContainerInstallProgress(
                        phase = MacContainerInstallPhase.COMPLETE,
                        message = "托管容器网络已自动修复，项目准备已完成。",
                        logLines = installLogTail(projectId),
                    )
                    lastErrors.remove(projectId)
                    return retry
                }
                val retryDetail = retry.detail ?: "网络修复后项目准备仍然失败。"
                lastErrors[projectId] = retryDetail
                containerInstallProgress[projectId] = MacContainerInstallProgress(
                    phase = MacContainerInstallPhase.FAILED,
                    message = retryDetail,
                    logLines = installLogTail(projectId),
                )
                return retry
            }
        }

        if (!result.success) {
            lastErrors[projectId] = result.detail ?: if (result.cancelled) {
                "准备已停止"
            } else {
                "项目准备失败"
            }
        }
        return result
    }

    private fun managedDockerProviderAvailable(): Boolean =
        latestContainerProviders.any { provider ->
            provider.kind == MacContainerProviderKind.DOCKER &&
                provider.availability.name == "AVAILABLE" &&
                MacManagedContainerToolchain.isManagedExecutable(provider.executablePath)
        }

    private fun repairManagedContainerNetwork(projectId: String): Boolean {
        containerInstallLogs[projectId] = StringBuilder()
        fun updateProgress(phase: MacContainerInstallPhase, message: String) {
            containerInstallProgress[projectId] = MacContainerInstallProgress(
                phase = phase,
                message = message,
                logLines = installLogTail(projectId),
            )
        }
        val result = containerInstaller.repairManagedNetwork(
            progress = ::updateProgress,
            log = { appendInstallLog(projectId, it) },
        )
        if (!result.success) {
            val detail = result.detail ?: "托管容器网络自动修复失败。"
            lastErrors[projectId] = detail
            updateProgress(MacContainerInstallPhase.FAILED, detail)
        }
        return result.success
    }

    private fun appendInstallLog(projectId: String, line: String) {
        val buffer = containerInstallLogs.computeIfAbsent(projectId) { StringBuilder() }
        synchronized(buffer) {
            buffer.append(line).append('\n')
            if (buffer.length > 200_000) {
                buffer.delete(0, buffer.length - 160_000)
            }
        }
    }

    private fun installLogTail(projectId: String): List<String> {
        val buffer = containerInstallLogs[projectId] ?: return emptyList()
        return synchronized(buffer) {
            buffer.toString()
                .lineSequence()
                .filter(String::isNotBlank)
                .toList()
                .takeLast(80)
        }
    }

    fun start(projectId: String): Boolean {
        lastErrors.remove(projectId)
        val started = coordinator.start(projectId)
        if (!started) lastErrors[projectId] = "项目启动失败"
        return started
    }

    fun stop(projectId: String): Boolean {
        val stopped = coordinator.stop(projectId)
        if (!stopped) lastErrors[projectId] = "项目停止失败"
        return stopped
    }

    fun restart(projectId: String): Boolean {
        lastErrors.remove(projectId)
        val restarted = coordinator.restart(projectId)
        if (!restarted) lastErrors[projectId] = "项目重新运行失败"
        return restarted
    }

    fun logs(projectId: String): String = coordinator.logs(projectId)

    fun clearError(projectId: String) {
        lastErrors.remove(projectId)
    }

    fun waitForResult(projectId: String, attempts: Int = 80, delayMs: Long = 100): String? {
        repeat(attempts) {
            view(projectId)?.resultUrl?.let { return it }
            val lifecycle = view(projectId)?.workflow?.lifecycle
            if (lifecycle == ProjectLifecycleState.RUN_FAILED ||
                lifecycle == ProjectLifecycleState.STOPPED
            ) {
                return null
            }
            Thread.sleep(delayMs)
        }
        return view(projectId)?.resultUrl
    }

    private fun languagePlan(snapshot: MacProjectSnapshot): MacProjectEnvironmentPlan =
        MacProjectWorkflowPlanner.plan(
            snapshot = snapshot,
            hostTools = discovery,
            managedPythonExecutable = managedPython
                ?.takeIf { it.available }
                ?.pythonExecutable
                ?.absolutePath,
        )

    private fun composePlan(snapshot: MacProjectSnapshot): ComposeProjectPlan =
        ComposeProjectPlanner.plan(
            relativePaths = snapshot.relativePaths,
            manifestText = snapshot.composeText,
            containerAvailability = MacContainerRuntimeDiscovery.capabilityAvailability(
                latestContainerProviders,
            ),
        )

    private fun adviceFor(project: MacProductProject): MacContainerEnvironmentAdvice? =
        if (project.isCompose) {
            MacContainerEnvironmentAdvisor.advise(systemFacts, latestContainerProviders)
        } else {
            null
        }

    private fun installPlanFor(project: MacProductProject): MacContainerInstallPlan? =
        if (
            project.isCompose &&
            containerInstallProgress[project.projectId]?.active != true
        ) {
            MacContainerInstallPlanner.plan(systemFacts, latestContainerProviders)
        } else {
            null
        }

    fun containerInstallLog(projectId: String): String {
        val buffer = containerInstallLogs[projectId] ?: return ""
        return synchronized(buffer) { buffer.toString() }
    }

    private fun refreshContainerProviders() {
        latestContainerProviders = containerProviderSnapshotSource()
    }
}
