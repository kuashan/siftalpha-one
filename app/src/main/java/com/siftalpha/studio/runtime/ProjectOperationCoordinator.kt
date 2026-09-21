package com.siftalpha.studio.runtime

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Result handling state exposed to the legacy Developer Workspace callback router. */
internal enum class ExternalResultDisposition {
    ACCEPTED,
    FENCED,
    UNMANAGED,
}

/**
 * A process-scoped scheduler for control-operation deadlines.
 *
 * The scheduler is deliberately independent of an Activity. The coordinator is an application
 * singleton, so leaving/recreating either presentation surface cannot remove this watchdog.
 */
internal interface RuntimeOperationWatchdog {
    fun schedule(
        record: RuntimeOperationRecord,
        delayMs: Long,
        onTimeout: () -> Unit,
    )

    fun cancel(projectId: String, generation: Long)
}

private data class WatchdogKey(
    val projectId: String,
    val generation: Long,
)

private class ScheduledRuntimeOperationWatchdog : RuntimeOperationWatchdog {
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "siftalpha-runtime-operation-watchdog").apply {
            isDaemon = true
        }
    }
    private val tasks = ConcurrentHashMap<WatchdogKey, ScheduledFuture<*>>()

    override fun schedule(
        record: RuntimeOperationRecord,
        delayMs: Long,
        onTimeout: () -> Unit,
    ) {
        val key = WatchdogKey(record.projectId, record.generation)
        cancel(record.projectId, record.generation)
        tasks[key] = executor.schedule(
            {
                tasks.remove(key)
                onTimeout()
            },
            delayMs.coerceAtLeast(1L),
            TimeUnit.MILLISECONDS,
        )
    }

    override fun cancel(projectId: String, generation: Long) {
        tasks.remove(WatchdogKey(projectId, generation))?.cancel(false)
    }
}

/**
 * The shared owner for one mutable Runtime operation per project.
 *
 * Both presentation surfaces may ask this coordinator to begin/bind/finish an operation. Neither
 * surface creates generations or writes RuntimeOperationRecord directly. STOP keeps its priority
 * and replaces the superseded operation with a new, project-scoped STOP record.
 */
class ProjectOperationCoordinator internal constructor(
    private val operationStore: RuntimeOperationRecordStore,
    private val lifecycleStore: RuntimeLifecycleStore? = null,
    private val tracker: RuntimeOperationTracker = RuntimeOperationTracker(),
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val listenForExternalResults: Boolean = true,
    private val watchdog: RuntimeOperationWatchdog = ScheduledRuntimeOperationWatchdog(),
) {
    data class ExternalCompletion(
        val projectId: String,
        val action: RuntimeOperationAction,
        val generation: Long,
        val result: RuntimeResult,
    )

    data class ExternalTimeout(
        val projectId: String,
        val action: RuntimeOperationAction,
        val generation: Long,
        val executionId: Int?,
        val failureReason: String,
    )

    private data class PreviousLifecycle(
        val projectId: String,
        val selection: ProjectRuntimeSelection,
        val snapshot: RuntimeLifecycleStore.Snapshot,
    )

    private val lock = Any()
    private val previousLifecycles = linkedMapOf<String, PreviousLifecycle>()
    private val externalExecutionGenerations = linkedMapOf<Int, Pair<String, Long>>()
    /** Retained briefly so the legacy callback router can distinguish accepted results from late ones. */
    private val managedExternalResults = linkedMapOf<Int, ExternalResultDisposition>()
    private val completionListeners = CopyOnWriteArraySet<(ExternalCompletion) -> Unit>()
    private val timeoutListeners = CopyOnWriteArraySet<(ExternalTimeout) -> Unit>()
    private val resultListener: (RuntimeResult) -> Unit = { result ->
        handleExternalResult(result)
    }

    init {
        if (listenForExternalResults) TermuxResultBus.addListener(resultListener)
    }

    fun addCompletionListener(listener: (ExternalCompletion) -> Unit) {
        completionListeners += listener
    }

    fun removeCompletionListener(listener: (ExternalCompletion) -> Unit) {
        completionListeners -= listener
    }

    fun addTimeoutListener(listener: (ExternalTimeout) -> Unit) {
        timeoutListeners += listener
    }

    fun removeTimeoutListener(listener: (ExternalTimeout) -> Unit) {
        timeoutListeners -= listener
    }

    /**
     * Used by the mature V04 result router after it finds its pending item. A fenced result must
     * be consumed and discarded; an unmanaged result remains on the old observation path.
     */
    internal fun consumeExternalResultDisposition(executionId: Int): ExternalResultDisposition =
        synchronized(lock) {
            managedExternalResults.remove(executionId) ?: ExternalResultDisposition.UNMANAGED
        }

    fun current(projectId: String, includeHidden: Boolean = false): RuntimeOperationRecord? {
        reconcileDueOperation(projectId)
        val record = synchronized(lock) {
            operationStore.read(projectId) ?: tracker.current(projectId)
        }
        if (listenForExternalResults && record != null && !record.terminal) {
            restoreExternalBinding(record)
            record.executionId?.let { reconcileFastExternalResult(it) }
        }
        val refreshed = synchronized(lock) {
            operationStore.read(projectId) ?: tracker.current(projectId)
        }
        return refreshed?.takeUnless { it.terminal || (!includeHidden && !it.userVisible) }
    }

    fun persisted(projectId: String): RuntimeOperationRecord? {
        reconcileDueOperation(projectId)
        val record = operationStore.read(projectId)
        if (listenForExternalResults && record != null && !record.terminal) {
            restoreExternalBinding(record)
            record.executionId?.let { reconcileFastExternalResult(it) }
        }
        return operationStore.read(projectId)
    }

    fun lastGeneration(projectId: String): Long = operationStore.lastGeneration(projectId)

    fun begin(
        projectId: String,
        provider: RuntimeOperationProvider,
        action: RuntimeOperationAction,
        executionId: Int? = null,
        userVisible: Boolean = true,
        runtimeSelection: ProjectRuntimeSelection = selectionFor(provider),
    ): RuntimeOperationRecord? {
        require(projectId.isNotBlank()) { "projectId must not be blank" }
        reconcileDueOperation(projectId)

        val timedOut = mutableListOf<ExternalTimeout>()
        val record = synchronized(lock) {
            var persisted = operationStore.read(projectId)
            if (persisted != null && !persisted.terminal) {
                val expired = persisted.deadlineAtEpochMs?.let { it <= nowEpochMs() } == true
                if (expired && persisted.provider == RuntimeOperationProvider.EXTERNAL) {
                    expireExternalOperationLocked(persisted)?.let(timedOut::add)
                    persisted = operationStore.read(projectId)
                }
            }
            if (persisted != null && !persisted.terminal) {
                if (action != RuntimeOperationAction.STOP || persisted.action == RuntimeOperationAction.STOP) {
                    return@synchronized null
                }
                // STOP has priority. The provider command itself remains project-scoped; only
                // the stale control record is superseded here.
                removeOperationBindings(persisted, fenceResult = true)
                operationStore.clear(projectId)
            } else if (persisted != null) {
                operationStore.clear(projectId)
            }

            tracker.seed(projectId, operationStore.lastGeneration(projectId))
            var local = tracker.current(projectId)
            if (local != null && !local.terminal) {
                val expired = local.deadlineAtEpochMs?.let { it <= nowEpochMs() } == true
                if (expired && local.provider == RuntimeOperationProvider.EXTERNAL) {
                    expireExternalOperationLocked(local)?.let(timedOut::add)
                    local = tracker.current(projectId)
                }
            }
            if (local != null && !local.terminal) {
                if (action != RuntimeOperationAction.STOP || local.action == RuntimeOperationAction.STOP) {
                    return@synchronized null
                }
                removeOperationBindings(local, fenceResult = true)
                tracker.finish(projectId, local.generation, RuntimeOperationPhase.CANCELLED)
                tracker.clearTerminal(projectId, local.generation)
            } else if (local != null) {
                tracker.clearTerminal(projectId, local.generation)
            }

            tracker.begin(
                projectId = projectId,
                provider = provider,
                action = action,
                executionId = executionId,
                userVisible = userVisible,
                now = nowEpochMs(),
            )?.also { accepted ->
                operationStore.write(accepted)
                if (provider == RuntimeOperationProvider.EXTERNAL) {
                    previousLifecycles[operationKey(accepted)] = PreviousLifecycle(
                        projectId = projectId,
                        selection = runtimeSelection,
                        snapshot = lifecycleStore?.read(projectId)
                            ?: emptyLifecycleSnapshot(),
                    )
                    executionId?.let { id ->
                        externalExecutionGenerations[id] = projectId to accepted.generation
                        rememberManagedResult(id, ExternalResultDisposition.ACCEPTED)
                    }
                }
            }
        }
        timedOut.forEach(::notifyTimeout)
        record ?: return null

        markLifecycleStarted(record, runtimeSelection)
        registerWatchdog(record)
        executionId?.let { id -> reconcileFastExternalResult(id) }
        return record
    }

    fun bindExternalExecution(
        projectId: String,
        generation: Long,
        executionId: Int,
    ): RuntimeOperationRecord? {
        val bound = synchronized(lock) {
            val current = operationStore.read(projectId)
                ?: tracker.current(projectId)
                ?: return@synchronized null
            if (
                current.projectId != projectId ||
                current.provider != RuntimeOperationProvider.EXTERNAL ||
                current.generation != generation ||
                current.terminal
            ) {
                return@synchronized null
            }
            current.executionId?.takeIf { it != executionId }?.let { oldId ->
                externalExecutionGenerations.remove(oldId)
                rememberManagedResult(oldId, ExternalResultDisposition.FENCED)
            }
            val updated = current.copy(executionId = executionId)
            operationStore.write(updated)
            externalExecutionGenerations[executionId] = projectId to generation
            rememberManagedResult(executionId, ExternalResultDisposition.ACCEPTED)
            registerWatchdogLocked(updated)
            updated
        }
        bound?.let { it.executionId?.let(::reconcileFastExternalResult) }
        return bound
    }

    fun finish(
        projectId: String,
        generation: Long? = null,
        phase: RuntimeOperationPhase,
        executionId: Int? = null,
    ): Boolean {
        return synchronized(lock) {
            val record = operationStore.read(projectId)?.takeUnless { it.terminal }
                ?: tracker.current(projectId)?.takeUnless { it.terminal }
                ?: return@synchronized false
            if (generation != null && record.generation != generation) return@synchronized false
            if (executionId != null && record.executionId != executionId) return@synchronized false
            finishLocked(record, phase, fenceResult = true)
            true
        }
    }

    fun cancel(projectId: String): Boolean = finish(
        projectId = projectId,
        phase = RuntimeOperationPhase.CANCELLED,
    )

    fun clearPersisted(projectId: String) {
        synchronized(lock) {
            operationStore.read(projectId)?.let { removeOperationBindings(it, fenceResult = true) }
            operationStore.clear(projectId)
            tracker.current(projectId)?.let { current ->
                if (current.terminal) tracker.clearTerminal(projectId, current.generation)
            }
            previousLifecycles.keys
                .filter { previousLifecycles[it]?.projectId == projectId }
                .forEach { previousLifecycles.remove(it) }
        }
    }

    private fun reconcileDueOperation(projectId: String) {
        val due = synchronized(lock) {
            listOfNotNull(
                tracker.current(projectId),
                operationStore.read(projectId),
            ).distinctBy { it.projectId to it.generation }
                .filter {
                    it.provider == RuntimeOperationProvider.EXTERNAL &&
                        !it.terminal &&
                        it.deadlineAtEpochMs?.let { deadline -> deadline <= nowEpochMs() } == true
                }
        }
        due.forEach { candidate ->
            val timeout = synchronized(lock) { expireExternalOperationLocked(candidate) }
            timeout?.let(::notifyTimeout)
        }
    }

    private fun registerWatchdog(record: RuntimeOperationRecord) {
        synchronized(lock) {
            registerWatchdogLocked(record)
        }
    }

    private fun registerWatchdogLocked(record: RuntimeOperationRecord) {
        if (record.provider != RuntimeOperationProvider.EXTERNAL) return
        val deadline = record.deadlineAtEpochMs ?: return
        val current = operationStore.read(record.projectId) ?: tracker.current(record.projectId)
        if (
            current == null ||
            current.generation != record.generation ||
            current.phase != RuntimeOperationPhase.ACTIVE
        ) {
            return
        }
        watchdog.schedule(
            record = record,
            delayMs = (deadline - nowEpochMs()).coerceAtLeast(1L),
            onTimeout = { onWatchdog(record) },
        )
    }

    private fun onWatchdog(record: RuntimeOperationRecord) {
        val timeout = synchronized(lock) { expireExternalOperationLocked(record) }
        if (timeout != null) {
            notifyTimeout(timeout)
            return
        }
        // A scheduler can wake a few milliseconds early. Re-register only the same generation;
        // a STOP or a newer operation can never inherit the old deadline.
        synchronized(lock) {
            val current = operationStore.read(record.projectId) ?: tracker.current(record.projectId)
            if (
                current != null &&
                current.provider == RuntimeOperationProvider.EXTERNAL &&
                current.generation == record.generation &&
                !current.terminal
            ) {
                registerWatchdogLocked(current)
            }
        }
    }

    private fun expireExternalOperationLocked(
        candidate: RuntimeOperationRecord,
    ): ExternalTimeout? {
        val current = operationStore.read(candidate.projectId)?.takeUnless { it.terminal }
            ?: tracker.current(candidate.projectId)?.takeUnless { it.terminal }
            ?: return null
        if (
            current.provider != RuntimeOperationProvider.EXTERNAL ||
            current.generation != candidate.generation ||
            current.deadlineAtEpochMs?.let { it <= nowEpochMs() } != true
        ) {
            return null
        }

        val previous = previousLifecycles[operationKey(current)]?.snapshot
        finishLocked(current, RuntimeOperationPhase.TIMED_OUT, fenceResult = true)
        updateLifecycleAfterExternalTimeout(current, previous)
        return ExternalTimeout(
            projectId = current.projectId,
            action = current.action,
            generation = current.generation,
            executionId = current.executionId,
            failureReason = "RUNTIME_OPERATION_TIMED_OUT:${current.action.name}",
        )
    }

    private fun notifyTimeout(timeout: ExternalTimeout) {
        timeoutListeners.forEach { listener -> runCatching { listener(timeout) } }
    }

    private fun handleExternalResult(result: RuntimeResult) {
        var completion: ExternalCompletion? = null
        synchronized(lock) {
            val binding = externalExecutionGenerations.remove(result.executionId)
            if (binding == null) return@synchronized
            val projectId = binding.first
            val generation = binding.second
            val record = operationStore.read(projectId)?.takeIf {
                it.provider == RuntimeOperationProvider.EXTERNAL &&
                    it.generation == generation &&
                    it.executionId == result.executionId &&
                    !it.terminal
            }
            if (record == null) {
                rememberManagedResult(result.executionId, ExternalResultDisposition.FENCED)
                return@synchronized
            }
            val previous = previousLifecycles[operationKey(record)]?.snapshot
            val phase = if (result.exitCode == 0 && result.internalErrorMessage.isBlank()) {
                RuntimeOperationPhase.SUCCESS
            } else {
                RuntimeOperationPhase.FAILED
            }
            // Lifecycle update happens while the generation fence is held. A new STOP/START
            // cannot interleave and have its state overwritten by this old callback.
            finishLocked(record, phase, fenceResult = false)
            updateLifecycleAfterExternalResult(record, result, previous)
            rememberManagedResult(result.executionId, ExternalResultDisposition.ACCEPTED)
            completion = ExternalCompletion(projectId, record.action, generation, result)
        }
        completion?.let { accepted ->
            completionListeners.forEach { listener -> runCatching { listener(accepted) } }
        }
    }

    private fun reconcileFastExternalResult(executionId: Int) {
        TermuxResultBus.peek(executionId)?.let(resultListener)
    }

    private fun restoreExternalBinding(record: RuntimeOperationRecord) {
        if (record.provider != RuntimeOperationProvider.EXTERNAL) return
        synchronized(lock) {
            previousLifecycles.putIfAbsent(
                operationKey(record),
                PreviousLifecycle(
                    projectId = record.projectId,
                    selection = selectionFor(record.provider),
                    snapshot = lifecycleStore?.read(record.projectId) ?: emptyLifecycleSnapshot(),
                ),
            )
            record.executionId?.let { executionId ->
                externalExecutionGenerations.putIfAbsent(
                    executionId,
                    record.projectId to record.generation,
                )
            }
            registerWatchdogLocked(record)
        }
    }

    private fun finishLocked(
        record: RuntimeOperationRecord,
        phase: RuntimeOperationPhase,
        fenceResult: Boolean,
    ) {
        tracker.seed(record.projectId, operationStore.lastGeneration(record.projectId))
        tracker.current(record.projectId)?.let { local ->
            if (!local.terminal && local.generation == record.generation) {
                tracker.finish(record.projectId, local.generation, phase)
            }
        }
        removeOperationBindings(record, fenceResult)
        operationStore.clear(record.projectId)
    }

    private fun removeOperationBindings(
        record: RuntimeOperationRecord,
        fenceResult: Boolean,
    ) {
        watchdog.cancel(record.projectId, record.generation)
        record.executionId?.let { executionId ->
            externalExecutionGenerations.remove(executionId)
            if (record.provider == RuntimeOperationProvider.EXTERNAL && fenceResult) {
                rememberManagedResult(executionId, ExternalResultDisposition.FENCED)
            }
        }
        externalExecutionGenerations.keys
            .filter {
                val binding = externalExecutionGenerations[it]
                binding?.first == record.projectId && binding.second == record.generation
            }
            .forEach { executionId ->
                externalExecutionGenerations.remove(executionId)
                if (fenceResult) rememberManagedResult(executionId, ExternalResultDisposition.FENCED)
            }
        previousLifecycles.remove(operationKey(record))
    }

    private fun rememberManagedResult(
        executionId: Int,
        disposition: ExternalResultDisposition,
    ) {
        managedExternalResults[executionId] = disposition
        while (managedExternalResults.size > MAX_MANAGED_RESULTS) {
            managedExternalResults.entries.firstOrNull()?.key?.let(managedExternalResults::remove)
        }
    }

    private fun markLifecycleStarted(
        record: RuntimeOperationRecord,
        selection: ProjectRuntimeSelection,
    ) {
        val store = lifecycleStore ?: return
        val current = store.read(record.projectId)
        val state = when (record.action) {
            RuntimeOperationAction.PREPARE -> RuntimeState.PREPARING
            RuntimeOperationAction.START -> RuntimeState.STARTING
            RuntimeOperationAction.STOP,
            RuntimeOperationAction.STATUS,
            RuntimeOperationAction.LOGS,
            RuntimeOperationAction.CLEAN,
            -> RuntimeState.UNKNOWN
        }
        store.write(
            projectKey = record.projectId,
            environmentReady = current.environmentReadyFor(selection),
            runtimeState = state,
            failureReason = null,
            runtimeSelection = selection,
        )
    }

    private fun updateLifecycleAfterExternalResult(
        record: RuntimeOperationRecord,
        result: RuntimeResult,
        previous: RuntimeLifecycleStore.Snapshot?,
    ) {
        val store = lifecycleStore ?: return
        val selection = selectionFor(record.provider)
        val current = store.read(record.projectId)
        val success = result.exitCode == 0 && result.internalErrorMessage.isBlank()
        val error = result.internalErrorMessage
            .ifBlank { result.stderr }
            .ifBlank { "External operation failed (exitCode=${result.exitCode})" }
            .trim()
            .take(240)
        val environmentReady = current.environmentReadyFor(selection)
        val state: RuntimeState
        val ready: Boolean?
        val failure: String?
        when (record.action) {
            RuntimeOperationAction.PREPARE -> {
                val prepared = success && "SIFTALPHA_ENV=READY" in result.stdout
                val previousReady = previous?.environmentReadyFor(selection) ?: environmentReady
                ready = if (prepared) true else previousReady
                state = if (prepared || previousReady == true) {
                    RuntimeState.UNKNOWN
                } else {
                    RuntimeState.ENVIRONMENT_ERROR
                }
                failure = if (prepared) null else error
            }
            RuntimeOperationAction.START -> {
                ready = environmentReady
                state = if (success) {
                    RuntimeState.fromOutput(result.stdout).takeIf { it != RuntimeState.UNKNOWN }
                        ?: RuntimeState.STARTING
                } else {
                    RuntimeState.EXITED_ERROR
                }
                failure = if (success) null else error
            }
            RuntimeOperationAction.STOP -> {
                ready = environmentReady
                state = if (success) {
                    RuntimeState.fromOutput(result.stdout).takeIf { it != RuntimeState.UNKNOWN }
                        ?: RuntimeState.STOPPED_BY_USER
                } else {
                    RuntimeState.UNKNOWN
                }
                failure = if (success) null else error
            }
            RuntimeOperationAction.STATUS,
            RuntimeOperationAction.LOGS,
            RuntimeOperationAction.CLEAN,
            -> {
                ready = when {
                    "SIFTALPHA_ENV=READY" in result.stdout -> true
                    "SIFTALPHA_ENV=NOT_READY" in result.stdout ||
                        "SIFTALPHA_ENV=CLEANED" in result.stdout -> false
                    else -> environmentReady
                }
                state = RuntimeState.fromOutput(result.stdout).takeIf { it != RuntimeState.UNKNOWN }
                    ?: current.runtimeState
                failure = if (success) null else error
            }
        }
        store.write(
            projectKey = record.projectId,
            environmentReady = ready,
            runtimeState = state,
            failureReason = failure,
            runtimeSelection = selection,
        )
    }

    private fun updateLifecycleAfterExternalTimeout(
        record: RuntimeOperationRecord,
        previous: RuntimeLifecycleStore.Snapshot?,
    ) {
        val store = lifecycleStore ?: return
        val selection = selectionFor(record.provider)
        val current = store.read(record.projectId)
        val currentReady = current.environmentReadyFor(selection)
        val previousReady = previous?.environmentReadyFor(selection) ?: currentReady
        val outcome = RuntimeOperationContract.timeoutOutcome(
            action = record.action,
            currentEnvironmentReady = currentReady,
            previousEnvironmentReady = previousReady,
        )
        store.write(
            projectKey = record.projectId,
            environmentReady = outcome.environmentReady,
            runtimeState = outcome.runtimeState,
            failureReason = outcome.failureReason,
            runtimeSelection = selection,
        )
    }

    private fun operationKey(record: RuntimeOperationRecord): String =
        record.projectId + ":" + record.generation

    private fun emptyLifecycleSnapshot(): RuntimeLifecycleStore.Snapshot =
        RuntimeLifecycleStore.Snapshot(
            externalEnvironmentReady = null,
            embeddedEnvironmentReady = null,
            runtimeState = RuntimeState.UNKNOWN,
            failureReason = null,
        )

    private fun selectionFor(provider: RuntimeOperationProvider): ProjectRuntimeSelection = when (provider) {
        RuntimeOperationProvider.INTERNAL -> ProjectRuntimeSelection.EMBEDDED_R
        RuntimeOperationProvider.EXTERNAL -> ProjectRuntimeSelection.TERMUX
    }

    companion object {
        private const val MAX_MANAGED_RESULTS = 128

        @Volatile
        private var sharedInstance: ProjectOperationCoordinator? = null

        fun shared(context: Context): ProjectOperationCoordinator =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: ProjectOperationCoordinator(
                    operationStore = RuntimeOperationStore(context.applicationContext),
                    lifecycleStore = RuntimeLifecycleStore(context.applicationContext),
                ).also { sharedInstance = it }
            }
    }
}
