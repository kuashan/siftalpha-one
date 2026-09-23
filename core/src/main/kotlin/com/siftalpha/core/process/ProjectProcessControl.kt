package com.siftalpha.core.process

/**
 * Stable project ownership for process control（进程控制的稳定项目归属）.
 *
 * Every platform must bind launched work to exactly one project. The native mechanism is platform
 * specific: Android（安卓） may use PID / PGID files, macOS（苹果） may use process groups, and
 * Windows（微软） may use Job Objects（作业对象）.
 */
data class ProjectProcessScope(
    val projectId: String,
) {
    init {
        require(projectId.isNotBlank()) { "projectId must not be blank" }
    }
}

/**
 * Platform-neutral host-process launch request（平台无关主机进程启动请求）.
 *
 * This is intentionally a host-process contract only. Embedded runtimes（内嵌运行时） may expose
 * the same product lifecycle through their Runtime Provider（运行提供者） without pretending to
 * be an OS process.
 */
data class ProjectProcessLaunchRequest(
    val scope: ProjectProcessScope,
    val executable: String,
    val arguments: List<String> = emptyList(),
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
) {
    init {
        require(executable.isNotBlank()) { "executable must not be blank" }
        require(workingDirectory == null || workingDirectory.isNotBlank()) {
            "workingDirectory must be null or non-blank"
        }
    }
}

/**
 * Opaque platform-owned process identity（平台持有的不透明进程身份）.
 *
 * Core（核心） never interprets the handle value as a PID, PGID or Windows handle.
 */
data class ProjectProcessHandle(
    val scope: ProjectProcessScope,
    val platformHandle: String,
) {
    init {
        require(platformHandle.isNotBlank()) { "platformHandle must not be blank" }
    }
}

enum class ProjectProcessState {
    UNKNOWN,
    STARTING,
    RUNNING,
    EXITED_SUCCESS,
    EXITED_ERROR,
    STOPPED,
}

data class ProjectProcessStatus(
    val scope: ProjectProcessScope,
    val state: ProjectProcessState,
    val exitCode: Int? = null,
)

data class ProjectProcessLogs(
    val scope: ProjectProcessScope,
    val stdout: String,
    val stderr: String = "",
    val truncated: Boolean = false,
)

enum class ProjectStopOutcome {
    STOPPED,
    ALREADY_STOPPED,
    NOT_FOUND,
    FAILED,
}

data class ProjectStopResult(
    val scope: ProjectProcessScope,
    val outcome: ProjectStopOutcome,
    val detail: String? = null,
)

/**
 * Platform process-control port（平台进程控制端口）.
 *
 * Product semantics are project-scoped: STOP（停止） means stop all process activity owned by the
 * selected project, not an arbitrary global process scan and not another project's work.
 */
interface ProjectProcessControl {
    fun start(request: ProjectProcessLaunchRequest): ProjectProcessHandle

    fun status(scope: ProjectProcessScope): ProjectProcessStatus

    fun logs(
        scope: ProjectProcessScope,
        maxBytes: Int,
    ): ProjectProcessLogs

    fun stopProject(scope: ProjectProcessScope): ProjectStopResult
}

/** Shared ownership rules（共享进程归属规则）. */
object ProjectProcessControlPolicy {
    fun sameProject(
        first: ProjectProcessScope,
        second: ProjectProcessScope,
    ): Boolean = first.projectId == second.projectId

    fun owns(
        scope: ProjectProcessScope,
        handle: ProjectProcessHandle,
    ): Boolean = sameProject(scope, handle.scope)

    fun requireOwned(
        scope: ProjectProcessScope,
        handle: ProjectProcessHandle,
    ) {
        require(owns(scope, handle)) {
            "process handle belongs to a different project"
        }
    }
}
