package com.siftalpha.core.storage

import com.siftalpha.core.lifecycle.RuntimeExecutionState
import com.siftalpha.core.operation.ProjectOperationAction
import com.siftalpha.core.operation.ProjectOperationPhase
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Durable runtime state shared by Android / macOS / Windows.
 *
 * Core owns what must survive process recreation. Platform adapters only implement
 * PlatformStateStorage and native process/container mechanisms.
 */
data class DurableRuntimeSnapshot(
    val environmentReady: Boolean?,
    val runtimeState: RuntimeExecutionState,
    val failureReason: String?,
)

class DurableRuntimeStateStore(
    private val storage: PlatformStateStorage,
) {
    fun read(projectId: String, providerId: String): DurableRuntimeSnapshot {
        requireProject(projectId)
        requireProvider(providerId)
        val prefix = prefix(projectId, providerId)
        return DurableRuntimeSnapshot(
            environmentReady = if (storage.contains(prefix + ENV_PRESENT)) {
                storage.readBoolean(prefix + ENV_READY)
            } else {
                null
            },
            runtimeState = storage.readText(prefix + RUNTIME_STATE)
                ?.let { runCatching { RuntimeExecutionState.valueOf(it) }.getOrNull() }
                ?: RuntimeExecutionState.UNKNOWN,
            failureReason = storage.readText(prefix + FAILURE),
        )
    }

    fun write(
        projectId: String,
        providerId: String,
        environmentReady: Boolean?,
        runtimeState: RuntimeExecutionState,
        failureReason: String?,
    ) {
        requireProject(projectId)
        requireProvider(providerId)
        val prefix = prefix(projectId, providerId)
        val writes = linkedMapOf<String, StoredStateValue>(
            prefix + RUNTIME_STATE to StoredStateValue.Text(runtimeState.name),
        )
        val removals = linkedSetOf<String>()

        if (environmentReady == null) {
            removals += prefix + ENV_PRESENT
            removals += prefix + ENV_READY
        } else {
            writes[prefix + ENV_PRESENT] = StoredStateValue.Bool(true)
            writes[prefix + ENV_READY] = StoredStateValue.Bool(environmentReady)
        }

        if (failureReason.isNullOrBlank()) {
            removals += prefix + FAILURE
        } else {
            writes[prefix + FAILURE] = StoredStateValue.Text(failureReason.trim().take(512))
        }
        storage.mutate(StateStorageMutation(writes = writes, removals = removals))
    }

    fun clear(projectId: String, providerId: String) {
        val prefix = prefix(projectId, providerId)
        storage.mutate(
            StateStorageMutation(
                removals = setOf(
                    prefix + ENV_PRESENT,
                    prefix + ENV_READY,
                    prefix + RUNTIME_STATE,
                    prefix + FAILURE,
                ),
            ),
        )
    }

    private fun prefix(projectId: String, providerId: String): String =
        "runtime:" + digest(projectId) + ":" + digest(providerId) + ":"

    private fun requireProject(value: String) = require(value.isNotBlank()) { "projectId must not be blank" }
    private fun requireProvider(value: String) = require(value.isNotBlank()) { "providerId must not be blank" }

    companion object {
        private const val ENV_PRESENT = "env_present"
        private const val ENV_READY = "env_ready"
        private const val RUNTIME_STATE = "state"
        private const val FAILURE = "failure"
    }
}

data class DurableProjectOperationRecord(
    val projectId: String,
    val providerId: String,
    val action: ProjectOperationAction,
    val phase: ProjectOperationPhase,
    val generation: Long,
    val startedAtEpochMs: Long,
    val deadlineAtEpochMs: Long? = null,
    val executionId: Int? = null,
    val userVisible: Boolean = true,
) {
    init {
        require(projectId.isNotBlank()) { "projectId must not be blank" }
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(generation > 0L) { "generation must be positive" }
        require(startedAtEpochMs > 0L) { "startedAtEpochMs must be positive" }
        require(deadlineAtEpochMs == null || deadlineAtEpochMs >= startedAtEpochMs) {
            "deadline must not precede operation start"
        }
    }
}

class DurableProjectOperationStore(
    private val storage: PlatformStateStorage,
    private val keyPrefix: String = "project-operation:",
) {
    init {
        require(keyPrefix.isNotBlank()) { "keyPrefix must not be blank" }
    }
    fun read(projectId: String): DurableProjectOperationRecord? {
        val prefix = prefix(projectId)
        val action = storage.readText(prefix + ACTION)
            ?.let { runCatching { ProjectOperationAction.valueOf(it) }.getOrNull() }
            ?: return null
        val phase = storage.readText(prefix + PHASE)
            ?.let { runCatching { ProjectOperationPhase.valueOf(it) }.getOrNull() }
            ?: return null
        val providerId = storage.readText(prefix + PROVIDER)?.takeIf(String::isNotBlank) ?: return null
        val generation = storage.readLong(prefix + GENERATION) ?: return null
        val startedAt = storage.readLong(prefix + STARTED) ?: return null
        if (generation <= 0L || startedAt <= 0L) return null
        val deadline = storage.readLong(prefix + DEADLINE)
        if (deadline != null && deadline < startedAt) return null
        return DurableProjectOperationRecord(
            projectId = storage.readText(prefix + PROJECT) ?: projectId,
            providerId = providerId,
            action = action,
            phase = phase,
            generation = generation,
            startedAtEpochMs = startedAt,
            deadlineAtEpochMs = deadline,
            executionId = storage.readInt(prefix + EXECUTION_ID),
            userVisible = storage.readBoolean(prefix + USER_VISIBLE) ?: true,
        )
    }

    fun lastGeneration(projectId: String): Long =
        storage.readLong(prefix(projectId) + NEXT_GENERATION) ?: 0L

    fun write(record: DurableProjectOperationRecord) {
        val prefix = prefix(record.projectId)
        val writes = linkedMapOf<String, StoredStateValue>(
            prefix + PROJECT to StoredStateValue.Text(record.projectId),
            prefix + PROVIDER to StoredStateValue.Text(record.providerId),
            prefix + ACTION to StoredStateValue.Text(record.action.name),
            prefix + PHASE to StoredStateValue.Text(record.phase.name),
            prefix + GENERATION to StoredStateValue.LongNumber(record.generation),
            prefix + NEXT_GENERATION to StoredStateValue.LongNumber(record.generation),
            prefix + STARTED to StoredStateValue.LongNumber(record.startedAtEpochMs),
            prefix + USER_VISIBLE to StoredStateValue.Bool(record.userVisible),
        )
        val removals = linkedSetOf<String>()
        record.deadlineAtEpochMs?.let {
            writes[prefix + DEADLINE] = StoredStateValue.LongNumber(it)
        } ?: run { removals += prefix + DEADLINE }
        record.executionId?.let {
            writes[prefix + EXECUTION_ID] = StoredStateValue.IntNumber(it)
        } ?: run { removals += prefix + EXECUTION_ID }
        storage.mutate(StateStorageMutation(writes = writes, removals = removals))
    }

    /** Clears only the active/terminal record. NEXT_GENERATION is intentionally retained as a fence. */
    fun clearCurrent(projectId: String) {
        val prefix = prefix(projectId)
        storage.mutate(
            StateStorageMutation(
                removals = setOf(
                    prefix + PROJECT,
                    prefix + PROVIDER,
                    prefix + ACTION,
                    prefix + PHASE,
                    prefix + GENERATION,
                    prefix + STARTED,
                    prefix + DEADLINE,
                    prefix + EXECUTION_ID,
                    prefix + USER_VISIBLE,
                ),
            ),
        )
    }

    fun clearAll(projectId: String) {
        val prefix = prefix(projectId)
        clearCurrent(projectId)
        storage.mutate(StateStorageMutation(removals = setOf(prefix + NEXT_GENERATION)))
    }

    private fun prefix(projectId: String): String {
        require(projectId.isNotBlank()) { "projectId must not be blank" }
        return keyPrefix + digest(projectId) + ":"
    }

    companion object {
        private const val PROJECT = "project"
        private const val PROVIDER = "provider"
        private const val ACTION = "action"
        private const val PHASE = "phase"
        private const val GENERATION = "generation"
        private const val NEXT_GENERATION = "next_generation"
        private const val STARTED = "started"
        private const val DEADLINE = "deadline"
        private const val EXECUTION_ID = "execution_id"
        private const val USER_VISIBLE = "user_visible"
    }
}

enum class PendingRunReason {
    CONFIGURATION,
    CLI_ARGUMENTS,
}

data class PendingRunIntent(
    val projectId: String,
    val reason: PendingRunReason,
    val updatedAtEpochMs: Long,
)

class PendingRunIntentStore(
    private val storage: PlatformStateStorage,
) {
    fun read(projectId: String): PendingRunIntent? {
        val prefix = prefix(projectId)
        val reason = storage.readText(prefix + REASON)
            ?.let { runCatching { PendingRunReason.valueOf(it) }.getOrNull() }
            ?: return null
        val updated = storage.readLong(prefix + UPDATED) ?: return null
        return PendingRunIntent(projectId, reason, updated)
    }

    fun mark(projectId: String, reason: PendingRunReason, nowEpochMs: Long = System.currentTimeMillis()) {
        val prefix = prefix(projectId)
        storage.mutate(
            StateStorageMutation(
                writes = mapOf(
                    prefix + REASON to StoredStateValue.Text(reason.name),
                    prefix + UPDATED to StoredStateValue.LongNumber(nowEpochMs),
                ),
            ),
        )
    }

    fun clear(projectId: String) {
        val prefix = prefix(projectId)
        storage.mutate(StateStorageMutation(removals = setOf(prefix + REASON, prefix + UPDATED)))
    }

    private fun prefix(projectId: String): String =
        "pending-run:" + digest(projectId) + ":"

    companion object {
        private const val REASON = "reason"
        private const val UPDATED = "updated"
    }
}

data class DurableRuntimeOwnership(
    val projectId: String,
    val generation: Long,
    val platformHandle: String,
    val startedAtEpochMs: Long,
) {
    init {
        require(projectId.isNotBlank()) { "projectId must not be blank" }
        require(generation > 0L) { "generation must be positive" }
        require(platformHandle.isNotBlank()) { "platformHandle must not be blank" }
        require(startedAtEpochMs > 0L) { "startedAtEpochMs must be positive" }
    }
}

class DurableRuntimeOwnershipStore(
    private val storage: PlatformStateStorage,
) {
    fun read(projectId: String): DurableRuntimeOwnership? {
        val prefix = prefix(projectId)
        val generation = storage.readLong(prefix + GENERATION) ?: return null
        val handle = storage.readText(prefix + HANDLE)?.takeIf(String::isNotBlank) ?: return null
        val started = storage.readLong(prefix + STARTED) ?: return null
        if (generation <= 0L || started <= 0L) return null
        return DurableRuntimeOwnership(
            projectId = storage.readText(prefix + PROJECT) ?: projectId,
            generation = generation,
            platformHandle = handle,
            startedAtEpochMs = started,
        )
    }

    fun write(value: DurableRuntimeOwnership) {
        val prefix = prefix(value.projectId)
        storage.mutate(
            StateStorageMutation(
                writes = mapOf(
                    prefix + PROJECT to StoredStateValue.Text(value.projectId),
                    prefix + GENERATION to StoredStateValue.LongNumber(value.generation),
                    prefix + HANDLE to StoredStateValue.Text(value.platformHandle),
                    prefix + STARTED to StoredStateValue.LongNumber(value.startedAtEpochMs),
                ),
            ),
        )
    }

    fun clear(projectId: String) {
        val prefix = prefix(projectId)
        storage.mutate(
            StateStorageMutation(
                removals = setOf(
                    prefix + PROJECT,
                    prefix + GENERATION,
                    prefix + HANDLE,
                    prefix + STARTED,
                ),
            ),
        )
    }

    private fun prefix(projectId: String): String =
        "runtime-owner:" + digest(projectId) + ":"

    companion object {
        private const val PROJECT = "project"
        private const val GENERATION = "generation"
        private const val HANDLE = "handle"
        private const val STARTED = "started"
    }
}

data class ProjectCleanupDecision(
    val allowed: Boolean,
    val reason: String? = null,
)

object ProjectCleanupPolicy {
    fun evaluate(processRunning: Boolean, operationActive: Boolean): ProjectCleanupDecision = when {
        processRunning -> ProjectCleanupDecision(false, "PROJECT_RUNNING")
        operationActive -> ProjectCleanupDecision(false, "PROJECT_OPERATION_ACTIVE")
        else -> ProjectCleanupDecision(true)
    }
}

object ProjectConfigurationPolicy {
    private val envName = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun normalizedEnvironmentNames(names: Collection<String>): List<String> =
        names.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .onEach { require(envName.matches(it)) { "invalid environment variable name: $it" } }
            .distinct()
            .sorted()
            .toList()

    fun missingEnvironmentNames(required: Collection<String>, configured: Set<String>): List<String> =
        normalizedEnvironmentNames(required).filterNot(configured::contains)
}

private fun digest(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }


/**
 * Platform secure-secret port. Core may reason about configured names and missing requirements,
 * while secret bytes remain owned by the platform secure store.
 */
interface ProjectSecretStorage {
    fun configuredKeys(projectId: String): Set<String>
    fun read(projectId: String, name: String): String?
    fun write(projectId: String, name: String, value: String)
    fun remove(projectId: String, name: String)
}
