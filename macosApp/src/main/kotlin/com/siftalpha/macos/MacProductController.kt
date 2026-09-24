package com.siftalpha.macos

import com.siftalpha.core.lifecycle.ProjectLifecycleState
import com.siftalpha.studio.runtime.RuntimeKind
import java.io.File
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

data class MacProductProject(
    val imported: MacImportedProject,
    val snapshot: MacProjectSnapshot,
    val plan: MacProjectEnvironmentPlan,
) {
    val projectId: String get() = imported.projectId
    val name: String get() = imported.root.name
    val runtime: RuntimeKind? get() = plan.needs?.primaryRuntime
}

data class MacProductProjectView(
    val project: MacProductProject,
    val workflow: MacWorkflowStatus,
    val lastError: String?,
) {
    val resultUrl: String? get() = workflow.webEndpoint?.url
}

class MacProductController(
    private val discovery: List<MacHostToolSnapshot> = MacHostRuntimeDiscovery().discoverAll(),
    private val filesystem: MacProjectFilesystem = MacProjectFilesystem(),
    private val managedPython: MacManagedPythonRuntime? = MacManagedPythonRuntime.locate(),
    processControl: MacProjectProcessControl = MacProjectProcessControl(),
    dataRoot: File = MacProjectEnvironmentManager.defaultDataRoot(),
) {
    private val environmentManager = MacProjectEnvironmentManager(
        processControl = processControl,
        managedPython = managedPython,
        dataRoot = dataRoot,
    )
    private val coordinator = MacProjectWorkflowCoordinator(processControl, environmentManager)
    private val projects = LinkedHashMap<String, MacProductProject>()
    private val lastErrors = ConcurrentHashMap<String, String?>()

    @Synchronized
    fun importProject(directory: File): MacProductProject {
        val imported = filesystem.importDirectory(directory)
        val snapshot = MacProjectSnapshotBuilder(filesystem).build(imported)
        val plan = MacProjectWorkflowPlanner.plan(
            snapshot = snapshot,
            hostTools = discovery,
            managedPythonExecutable = managedPython
                ?.takeIf { it.available }
                ?.pythonExecutable
                ?.absolutePath,
        )
        val product = MacProductProject(imported, snapshot, plan)
        projects[product.projectId] = product
        coordinator.attach(MacWorkflowContext(imported, snapshot, plan))
        lastErrors.remove(product.projectId)
        return product
    }

    @Synchronized
    fun refreshProject(projectId: String): MacProductProject? {
        val existing = projects[projectId] ?: return null
        val snapshot = MacProjectSnapshotBuilder(filesystem).build(existing.imported)
        val plan = MacProjectWorkflowPlanner.plan(
            snapshot = snapshot,
            hostTools = discovery,
            managedPythonExecutable = managedPython
                ?.takeIf { it.available }
                ?.pythonExecutable
                ?.absolutePath,
        )
        val refreshed = existing.copy(snapshot = snapshot, plan = plan)
        projects[projectId] = refreshed
        coordinator.attach(MacWorkflowContext(refreshed.imported, snapshot, plan))
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
        )
    }

    fun prepare(projectId: String): MacPrepareResult {
        lastErrors.remove(projectId)
        val result = coordinator.prepare(projectId)
        if (!result.success) {
            lastErrors[projectId] = result.detail ?: if (result.cancelled) {
                "准备已停止"
            } else {
                "项目准备失败"
            }
        }
        return result
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
}
