package com.siftalpha.studio.runtime

/** Provider-neutral actions owned by the project Runtime Center. */
enum class RuntimeOperationAction {
    PREPARE,
    START,
    STATUS,
    LOGS,
    STOP,
    CLEAN,
}

/** The lifecycle of one accepted, project-scoped operation. */
enum class RuntimeOperationPhase {
    ACCEPTED,
    ACTIVE,
    SUCCESS,
    FAILED,
    CANCELLED,
    TIMED_OUT,
}

enum class RuntimeOperationProvider {
    INTERNAL,
    EXTERNAL,
}

data class RuntimeOperationRecord(
    val projectId: String,
    val provider: RuntimeOperationProvider,
    val action: RuntimeOperationAction,
    val executionId: Int?,
    val generation: Long,
    val startedAtEpochMs: Long,
    /** Both providers have a bounded Android control-operation deadline. */
    val deadlineAtEpochMs: Long?,
    val userVisible: Boolean = true,
    val phase: RuntimeOperationPhase = RuntimeOperationPhase.ACCEPTED,
) {
    init {
        require(projectId.isNotBlank()) { "projectId must not be blank" }
        require(generation > 0L) { "generation must be positive" }
        if (deadlineAtEpochMs != null) {
            require(deadlineAtEpochMs >= startedAtEpochMs) {
                "deadline must not precede operation start"
            }
        }
    }

    val terminal: Boolean
        get() = phase in TERMINAL_PHASES

    fun withPhase(next: RuntimeOperationPhase): RuntimeOperationRecord = copy(phase = next)

    companion object {
        val TERMINAL_PHASES = setOf(
            RuntimeOperationPhase.SUCCESS,
            RuntimeOperationPhase.FAILED,
            RuntimeOperationPhase.CANCELLED,
            RuntimeOperationPhase.TIMED_OUT,
        )
    }
}

/**
 * Shared lifecycle rules used by both Internal and External execution adapters.
 *
 * This object deliberately contains no Android or Provider implementation detail. In
 * particular, RECOVERING is not an operation phase: it is reserved for reconciliation after
 * Activity/process restoration.
 */
object RuntimeOperationContract {
    const val PREPARE_TIMEOUT_MS = 30L * 60L * 1000L
    const val START_TIMEOUT_MS = 10L * 60L * 1000L
    const val STATUS_TIMEOUT_MS = 45L * 1000L
    const val LOGS_TIMEOUT_MS = 45L * 1000L
    const val STOP_TIMEOUT_MS = 45L * 1000L
    const val CLEAN_TIMEOUT_MS = 2L * 60L * 1000L

    const val EXTERNAL_START_TIMEOUT_MS = 30_000L
    const val EXTERNAL_STATUS_TIMEOUT_MS = 10_000L
    const val EXTERNAL_LOGS_TIMEOUT_MS = 10_000L
    const val EXTERNAL_STOP_TIMEOUT_MS = 15_000L

    fun timeoutMs(action: RuntimeOperationAction): Long = when (action) {
        RuntimeOperationAction.PREPARE -> PREPARE_TIMEOUT_MS
        RuntimeOperationAction.START -> START_TIMEOUT_MS
        RuntimeOperationAction.STATUS -> STATUS_TIMEOUT_MS
        RuntimeOperationAction.LOGS -> LOGS_TIMEOUT_MS
        RuntimeOperationAction.STOP -> STOP_TIMEOUT_MS
        RuntimeOperationAction.CLEAN -> CLEAN_TIMEOUT_MS
    }

    fun timeoutMs(
        provider: RuntimeOperationProvider,
        action: RuntimeOperationAction,
    ): Long = when (provider) {
        RuntimeOperationProvider.INTERNAL -> timeoutMs(action)
        RuntimeOperationProvider.EXTERNAL -> when (action) {
            RuntimeOperationAction.PREPARE -> PREPARE_TIMEOUT_MS
            RuntimeOperationAction.START -> EXTERNAL_START_TIMEOUT_MS
            RuntimeOperationAction.STATUS -> EXTERNAL_STATUS_TIMEOUT_MS
            RuntimeOperationAction.LOGS -> EXTERNAL_LOGS_TIMEOUT_MS
            RuntimeOperationAction.STOP -> EXTERNAL_STOP_TIMEOUT_MS
            RuntimeOperationAction.CLEAN -> CLEAN_TIMEOUT_MS
        }
    }

    data class TimeoutOutcome(
        val environmentReady: Boolean?,
        val runtimeState: RuntimeState,
        val failureReason: String,
    )

    /**
     * Elapsed control time is a recovery fact, not proof that a provider process stopped. PREPARE
     * preserves an already proven environment; STOP and all other uncertain controls become UNKNOWN.
     */
    fun timeoutOutcome(
        action: RuntimeOperationAction,
        currentEnvironmentReady: Boolean?,
        previousEnvironmentReady: Boolean? = currentEnvironmentReady,
    ): TimeoutOutcome {
        val environmentReady = if (action == RuntimeOperationAction.PREPARE) {
            previousEnvironmentReady
        } else {
            currentEnvironmentReady
        }
        val state = if (
            action == RuntimeOperationAction.PREPARE && environmentReady != true
        ) {
            RuntimeState.ENVIRONMENT_ERROR
        } else {
            RuntimeState.UNKNOWN
        }
        return TimeoutOutcome(
            environmentReady = environmentReady,
            runtimeState = state,
            failureReason = "RUNTIME_OPERATION_TIMED_OUT:${action.name}",
        )
    }

    fun deadlineAtEpochMs(
        provider: RuntimeOperationProvider,
        action: RuntimeOperationAction,
        startedAtEpochMs: Long,
    ): Long? = when (provider) {
        RuntimeOperationProvider.INTERNAL,
        RuntimeOperationProvider.EXTERNAL,
        -> startedAtEpochMs + timeoutMs(provider, action)
    }

    fun lifecycleState(action: RuntimeOperationAction): RuntimeLifecycleState = when (action) {
        RuntimeOperationAction.PREPARE -> RuntimeLifecycleState.PREPARING
        RuntimeOperationAction.START -> RuntimeLifecycleState.STARTING
        RuntimeOperationAction.STATUS,
        RuntimeOperationAction.LOGS,
        -> RuntimeLifecycleState.CHECKING
        RuntimeOperationAction.STOP -> RuntimeLifecycleState.STOPPING
        RuntimeOperationAction.CLEAN -> RuntimeLifecycleState.CLEANING
    }

    fun canBegin(current: RuntimeOperationRecord?, requested: RuntimeOperationAction): Boolean {
        if (current == null) return true
        if (current.terminal) return false
        // STOP has priority over another operation, but duplicate STOP requests are rejected.
        return requested == RuntimeOperationAction.STOP &&
            current.action != RuntimeOperationAction.STOP
    }

    fun providerFor(selection: ProjectRuntimeSelection): RuntimeOperationProvider = when (selection) {
        ProjectRuntimeSelection.EMBEDDED_R -> RuntimeOperationProvider.INTERNAL
        ProjectRuntimeSelection.TERMUX -> RuntimeOperationProvider.EXTERNAL
    }

    fun actionName(action: RuntimeOperationAction): String = action.name
}

/** Small deterministic owner for one current operation per project. */
class RuntimeOperationTracker(
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()
    private val current = linkedMapOf<String, RuntimeOperationRecord>()
    private val nextGeneration = linkedMapOf<String, Long>()

    fun begin(
        projectId: String,
        provider: RuntimeOperationProvider,
        action: RuntimeOperationAction,
        executionId: Int? = null,
        userVisible: Boolean = true,
        now: Long = nowEpochMs(),
    ): RuntimeOperationRecord? = synchronized(lock) {
        val existing = current[projectId]
        if (!RuntimeOperationContract.canBegin(existing, action)) return@synchronized null
        val generation = (nextGeneration[projectId] ?: 0L) + 1L
        nextGeneration[projectId] = generation
        RuntimeOperationRecord(
            projectId = projectId,
            provider = provider,
            action = action,
            executionId = executionId,
            generation = generation,
            startedAtEpochMs = now,
            deadlineAtEpochMs = RuntimeOperationContract.deadlineAtEpochMs(
                provider = provider,
                action = action,
                startedAtEpochMs = now,
            ),
            userVisible = userVisible,
            phase = RuntimeOperationPhase.ACTIVE,
        ).also { current[projectId] = it }
    }

    fun current(projectId: String): RuntimeOperationRecord? = synchronized(lock) {
        current[projectId]
    }

    fun seed(projectId: String, generation: Long) = synchronized(lock) {
        if (generation > (nextGeneration[projectId] ?: 0L)) {
            nextGeneration[projectId] = generation
        }
    }

    fun finish(
        projectId: String,
        generation: Long,
        phase: RuntimeOperationPhase,
    ): Boolean = synchronized(lock) {
        val record = current[projectId] ?: return@synchronized false
        if (record.generation != generation || record.terminal) return@synchronized false
        current[projectId] = record.withPhase(phase)
        true
    }

    fun clearTerminal(projectId: String, generation: Long): Boolean = synchronized(lock) {
        val record = current[projectId] ?: return@synchronized false
        if (record.generation != generation || !record.terminal) return@synchronized false
        current.remove(projectId)
        true
    }

    fun markExpired(now: Long = nowEpochMs()): List<RuntimeOperationRecord> = synchronized(lock) {
        current.values
            .filter { record ->
                !record.terminal && record.deadlineAtEpochMs?.let { it <= now } == true
            }
            .onEach { current[it.projectId] = it.withPhase(RuntimeOperationPhase.TIMED_OUT) }
    }
}
