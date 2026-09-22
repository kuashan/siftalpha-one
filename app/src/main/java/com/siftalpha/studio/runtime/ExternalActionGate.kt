package com.siftalpha.studio.runtime

import android.content.Context

/**
 * Shared External Action Gate（共享外部操作门禁）.
 *
 * It owns only the user's deferred external intent identity:
 * project + action + generation + origin. Provider probing remains owned by
 * ExternalProviderProbeCoordinator, while Runtime execution remains owned by
 * ProjectOperationCoordinator.
 */
class ExternalActionGate internal constructor(
    private val currentReadiness: () -> ExternalProviderPreflightResult,
    private val ensureReadiness: () -> ExternalProviderPreflightResult,
) {
    enum class Origin {
        NORMAL_MODE,
        DEVELOPER_MODE,
    }

    enum class Action {
        PREPARE,
        RUN,
        REFRESH,
    }

    data class Request(
        val projectId: String,
        val action: Action,
        val generation: Long,
        val origin: Origin,
    )

    sealed interface Decision {
        data object Proceed : Decision

        data class Awaiting(
            val request: Request,
            val readiness: ExternalProviderReadiness,
        ) : Decision

        data class Rejected(
            val readiness: ExternalProviderReadiness,
        ) : Decision
    }

    private val lock = Any()
    private val pendingByProject = linkedMapOf<String, Request>()
    private val generations = linkedMapOf<String, Long>()

    fun request(
        projectId: String,
        action: Action,
        origin: Origin,
    ): Decision {
        require(projectId.isNotBlank()) { "projectId must not be blank" }

        val before = currentReadiness()
        if (before.ready) return Decision.Proceed

        val request = synchronized(lock) {
            val existing = pendingByProject[projectId]
            if (existing != null && existing.action == action && existing.origin == origin) {
                existing
            } else {
                val generation = (generations[projectId] ?: 0L) + 1L
                generations[projectId] = generation
                Request(
                    projectId = projectId,
                    action = action,
                    generation = generation,
                    origin = origin,
                ).also { pendingByProject[projectId] = it }
            }
        }

        val after = ensureReadiness()
        if (after.ready) {
            val claimed = claimReady(
                projectId = projectId,
                origin = origin,
                generation = request.generation,
            )
            return if (claimed != null) Decision.Proceed
            else Decision.Awaiting(request, after.readiness)
        }

        if (after.readiness.isTerminalGateFailure()) {
            cancelIfGeneration(projectId, request.generation)
            return Decision.Rejected(after.readiness)
        }

        return Decision.Awaiting(request, after.readiness)
    }

    fun pending(projectId: String): Request? = synchronized(lock) {
        pendingByProject[projectId]
    }

    fun claimReady(
        projectId: String,
        origin: Origin,
        generation: Long? = null,
    ): Request? {
        if (!currentReadiness().ready) return null
        return synchronized(lock) {
            val request = pendingByProject[projectId] ?: return@synchronized null
            if (request.origin != origin) return@synchronized null
            if (generation != null && request.generation != generation) return@synchronized null
            pendingByProject.remove(projectId)
            request
        }
    }

    fun claimReady(origin: Origin): List<Request> {
        if (!currentReadiness().ready) return emptyList()
        return synchronized(lock) {
            val requests = pendingByProject.values.filter { it.origin == origin }
            requests.forEach { pendingByProject.remove(it.projectId) }
            requests
        }
    }

    /**
     * STOP（停止） invalidates only this project's deferred request. Increasing the
     * generation ensures any stale continuation token can never match a later request.
     */
    fun cancel(projectId: String) {
        synchronized(lock) {
            pendingByProject.remove(projectId)
            generations[projectId] = (generations[projectId] ?: 0L) + 1L
        }
    }

    internal fun handlePreflightResult(result: ExternalProviderPreflightResult) {
        if (!result.readiness.isTerminalGateFailure()) return
        synchronized(lock) {
            pendingByProject.clear()
        }
    }

    private fun cancelIfGeneration(projectId: String, generation: Long) {
        synchronized(lock) {
            val current = pendingByProject[projectId]
            if (current?.generation == generation) {
                pendingByProject.remove(projectId)
            }
        }
    }

    private fun ExternalProviderReadiness.isTerminalGateFailure(): Boolean = when (this) {
        ExternalProviderReadiness.TERMUX_NOT_INSTALLED,
        ExternalProviderReadiness.EXTERNAL_APPS_CONFIGURATION_REQUIRED,
        ExternalProviderReadiness.BRIDGE_UNRESPONSIVE,
        ExternalProviderReadiness.UNAVAILABLE,
        -> true

        ExternalProviderReadiness.RUN_COMMAND_PERMISSION_REQUIRED,
        ExternalProviderReadiness.BRIDGE_CHECK_REQUIRED,
        ExternalProviderReadiness.BRIDGE_CHECKING,
        ExternalProviderReadiness.READY,
        -> false
    }

    companion object {
        @Volatile
        private var sharedInstance: ExternalActionGate? = null

        fun shared(context: Context): ExternalActionGate =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: run {
                    val preflight = ExternalProviderProbeCoordinator.shared(context.applicationContext)
                    ExternalActionGate(
                        currentReadiness = preflight::current,
                        ensureReadiness = preflight::ensureReady,
                    ).also { gate ->
                        preflight.addListener(gate::handlePreflightResult)
                    }
                }.also { sharedInstance = it }
            }
    }
}
