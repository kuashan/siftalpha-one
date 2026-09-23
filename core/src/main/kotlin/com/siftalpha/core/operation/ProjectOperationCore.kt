package com.siftalpha.core.operation

/**
 * Platform-neutral project operations（平台无关项目操作）.
 *
 * These names describe product semantics only. A Platform Adapter（平台适配层） decides how the
 * operation is executed by Android（安卓）, macOS（苹果） or Windows（微软）.
 */
enum class ProjectOperationAction {
    PREPARE,
    START,
    STATUS,
    LOGS,
    STOP,
    CLEAN,
}

/** Lifecycle of one accepted project operation（单次项目操作生命周期）. */
enum class ProjectOperationPhase {
    ACCEPTED,
    ACTIVE,
    SUCCESS,
    FAILED,
    CANCELLED,
    TIMED_OUT,
    ;

    val terminal: Boolean
        get() = this in TERMINAL_PHASES

    companion object {
        val TERMINAL_PHASES: Set<ProjectOperationPhase> = setOf(
            SUCCESS,
            FAILED,
            CANCELLED,
            TIMED_OUT,
        )
    }
}

/**
 * Minimal operation ownership fact（操作归属事实）.
 *
 * Operations are always owned by one stable project identity. Platform process IDs, execution IDs
 * and provider names deliberately do not belong here.
 */
data class ProjectOperationOwnership(
    val projectId: String,
    val action: ProjectOperationAction,
    val phase: ProjectOperationPhase,
    val generation: Long,
) {
    init {
        require(projectId.isNotBlank()) { "projectId must not be blank" }
        require(generation > 0L) { "generation must be positive" }
    }

    val terminal: Boolean
        get() = phase.terminal
}

/**
 * Shared project-operation arbitration policy（共享项目操作仲裁策略）.
 *
 * Contract:
 * 1. Different projects never block one another.
 * 2. One project owns at most one active mutable operation.
 * 3. STOP（停止） may supersede another active operation for the same project.
 * 4. Duplicate STOP（停止） is rejected.
 * 5. A terminal record is historical evidence and is not itself a second active operation.
 */
object ProjectOperationPolicy {
    fun canBegin(
        current: ProjectOperationOwnership?,
        requestedProjectId: String,
        requestedAction: ProjectOperationAction,
    ): Boolean {
        require(requestedProjectId.isNotBlank()) { "requestedProjectId must not be blank" }

        if (current == null) return true
        if (current.projectId != requestedProjectId) return true
        if (current.terminal) return false

        return requestedAction == ProjectOperationAction.STOP &&
            current.action != ProjectOperationAction.STOP
    }

    fun canSupersede(
        current: ProjectOperationOwnership,
        requestedProjectId: String,
        requestedAction: ProjectOperationAction,
    ): Boolean =
        current.projectId == requestedProjectId &&
            !current.terminal &&
            requestedAction == ProjectOperationAction.STOP &&
            current.action != ProjectOperationAction.STOP

    fun sameProject(
        firstProjectId: String,
        secondProjectId: String,
    ): Boolean = firstProjectId == secondProjectId
}
