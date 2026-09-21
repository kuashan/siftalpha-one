package com.siftalpha.studio.runtime

import android.content.Context
import java.util.concurrent.CopyOnWriteArraySet

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
) {
    data class ExternalCompletion(
        val projectId: String,
        val action: RuntimeOperationAction,
        val generation: Long,
        val result: RuntimeResult,
    )

    private data class PreviousLifecycle(
        val projectId: String,
        val selection: ProjectRuntimeSelection,
        val snapshot: RuntimeLifecycleStore.Snapshot,
    )

    private val lock = Any()
    private val previousLifecycles = linkedMapOf<String, PreviousLifecycle>()
    private val externalExecutionGenerations = linkedMapOf<Int, Pair<String, Long>>()
    private val completionListeners = CopyOnWriteArraySet<(ExternalCompletion) -> Unit>()
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

    fun current(projectId: String, includeHidden: Boolean = false): RuntimeOperationRecord? {
        val record = synchronized(lock) {
            tracker.current(projectId) ?: operationStore.read(projectId)
        }
        if (listenForExternalResults && record != null && !record.terminal) {
            restoreExternalBinding(record)
            record.executionId?.let { reconcileFastExternalResult(it) }
        }
        val refreshed = synchronized(lock) {
            tracker.current(projectId) ?: operationStore.read(projectId)
        }
        return refreshed?.takeUnless { it.terminal || (!includeHidden && !it.userVisible) }
    }

    fun persisted(projectId: String): RuntimeOperationRecord? = operationStore.read(projectId)

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
        val record = synchronized(lock) {
            val now = nowEpochMs()
            val persisted = operationStore.read(projectId)
            if (persisted != null && !persisted.terminal) {
                val expired = persisted.deadlineAtEpochMs?.let { it <= now } == true
                if (expired) {
                    removeOperationBindings(persisted)
                    operationStore.clear(projectId)
                } else if (action != RuntimeOperationAction.STOP || persisted.action == RuntimeOperationAction.STOP) {
                    return@synchronized null
                } else {
                    // STOP has priority. The provider command itself remains project-scoped; only
                    // the stale control record is superseded here.
                    removeOperationBindings(persisted)
                    operationStore.clear(projectId)
                }
            } else if (persisted != null) {
                operationStore.clear(projectId)
            }

            tracker.seed(projectId, operationStore.lastGeneration(projectId))
            val local = tracker.current(projectId)
            if (local != null && !local.terminal) {
                val expired = local.deadlineAtEpochMs?.let { it <= now } == true
                if (expired) {
                    removeOperationBindings(local)
                    tracker.finish(projectId, local.generation, RuntimeOperationPhase.TIMED_OUT)
                    tracker.clearTerminal(projectId, local.generation)
                } else if (action != RuntimeOperationAction.STOP || local.action == RuntimeOperationAction.STOP) {
                    return@synchronized null
                } else {
                    removeOperationBindings(local)
                    tracker.finish(projectId, local.generation, RuntimeOperationPhase.CANCELLED)
                    tracker.clearTerminal(projectId, local.generation)
                }
            } else if (local != null) {
                tracker.clearTerminal(projectId, local.generation)
            }

            tracker.begin(
                projectId = projectId,
                provider = provider,
                action = action,
                executionId = executionId,
                userVisible = userVisible,
                now = now,
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
                    }
                }
            }
        } ?: return null

        markLifecycleStarted(record, runtimeSelection)
        executionId?.let { id -> reconcileFastExternalResult(id) }
        return record
    }

    fun bindExternalExecution(
        projectId: String,
        generation: Long,
        executionId: Int,
    ): RuntimeOperationRecord? {
        synchronized(lock) {
            val current = operationStore.read(projectId)
                ?: tracker.current(projectId)
                ?: return null
            if (
                current.projectId != projectId ||
                current.provider != RuntimeOperationProvider.EXTERNAL ||
                current.generation != generation ||
                current.terminal
            ) {
                return null
            }
            val bound = current.copy(executionId = executionId)
            operationStore.write(bound)
            externalExecutionGenerations[executionId] = projectId to generation
            reconcileFastExternalResult(executionId)
            return bound
        }
    }

    fun finish(
        projectId: String,
        generation: Long? = null,
        phase: RuntimeOperationPhase,
        executionId: Int? = null,
    ): Boolean {
        synchronized(lock) {
            val record = tracker.current(projectId)?.takeUnless { it.terminal }
                ?: operationStore.read(projectId)?.takeUnless { it.terminal }
                ?: return false
            if (generation != null && record.generation != generation) return false
            if (executionId != null && record.executionId != executionId) return false
            tracker.seed(projectId, operationStore.lastGeneration(projectId))
            tracker.current(projectId)?.let { local ->
                if (!local.terminal) tracker.finish(projectId, local.generation, phase)
            }
            removeOperationBindings(record)
            operationStore.clear(projectId)
            return true
        }
    }

    fun cancel(projectId: String): Boolean = finish(
        projectId = projectId,
        phase = RuntimeOperationPhase.CANCELLED,
    )

    fun clearPersisted(projectId: String) {
        synchronized(lock) {
            operationStore.clear(projectId)
            tracker.current(projectId)?.let { current ->
                if (current.terminal) tracker.clearTerminal(projectId, current.generation)
            }
            externalExecutionGenerations.keys
                .filter { externalExecutionGenerations[it]?.first == projectId }
                .forEach { externalExecutionGenerations.remove(it) }
            previousLifecycles.keys
                .filter { previousLifecycles[it]?.projectId == projectId }
                .forEach { previousLifecycles.remove(it) }
        }
    }

    private fun handleExternalResult(result: RuntimeResult) {
        val binding = synchronized(lock) {
            externalExecutionGenerations.remove(result.executionId)
        } ?: return
        val projectId = binding.first
        val generation = binding.second
        val record = operationStore.read(projectId)?.takeIf {
            it.provider == RuntimeOperationProvider.EXTERNAL &&
                it.generation == generation &&
                it.executionId == result.executionId
        } ?: return
        val previous = synchronized(lock) {
            previousLifecycles[operationKey(record)]?.snapshot
        }

        val phase = if (result.exitCode == 0 && result.internalErrorMessage.isBlank()) {
            RuntimeOperationPhase.SUCCESS
        } else {
            RuntimeOperationPhase.FAILED
        }
        finish(projectId, generation, phase, result.executionId)
        updateLifecycleAfterExternalResult(record, result, previous)
        val completion = ExternalCompletion(projectId, record.action, generation, result)
        completionListeners.forEach { listener -> runCatching { listener(completion) } }
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
        }
    }

    private fun removeOperationBindings(record: RuntimeOperationRecord) {
        record.executionId?.let { externalExecutionGenerations.remove(it) }
        externalExecutionGenerations.keys
            .filter {
                val binding = externalExecutionGenerations[it]
                binding?.first == record.projectId && binding.second == record.generation
            }
            .forEach { externalExecutionGenerations.remove(it) }
        previousLifecycles.remove(operationKey(record))
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
            RuntimeOperationAction.STOP -> RuntimeState.STOPPING
            RuntimeOperationAction.STATUS,
            RuntimeOperationAction.LOGS,
            -> RuntimeState.CHECKING
            RuntimeOperationAction.CLEAN -> RuntimeState.CLEANING
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
                    current.runtimeState
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
