package com.siftalpha.studio.runtime

import android.content.Context
import android.content.SharedPreferences
import com.siftalpha.core.storage.PlatformStateStorage
import com.siftalpha.core.storage.StateStorageMutation
import com.siftalpha.core.storage.StoredStateValue
import com.siftalpha.core.storage.readBoolean
import com.siftalpha.core.storage.readInt
import com.siftalpha.core.storage.readLong
import com.siftalpha.core.storage.readText
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Minimal durable operation metadata. It is deliberately not a result/history database: it only
 * lets a recreated Activity distinguish a real unfinished operation from a recovery probe.
 */
interface RuntimeOperationRecordStore {
    fun read(projectId: String): RuntimeOperationRecord?
    fun lastGeneration(projectId: String): Long
    fun write(record: RuntimeOperationRecord)
    fun clear(projectId: String)
}

class RuntimeOperationStore internal constructor(
    private val storage: PlatformStateStorage,
) : RuntimeOperationRecordStore {
    internal constructor(prefs: SharedPreferences) : this(
        AndroidSharedPreferencesStateStorage(prefs),
    )

    constructor(context: Context) : this(
        AndroidSharedPreferencesStateStorage(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        ),
    )

    override fun read(projectId: String): RuntimeOperationRecord? {
        val prefix = prefix(projectId)
        val action = storage.readText(prefix + ACTION)?.let {
            runCatching { RuntimeOperationAction.valueOf(it) }.getOrNull()
        } ?: return null
        val provider = storage.readText(prefix + PROVIDER)?.let {
            runCatching { RuntimeOperationProvider.valueOf(it) }.getOrNull()
        } ?: return null
        val phase = storage.readText(prefix + PHASE)?.let {
            runCatching { RuntimeOperationPhase.valueOf(it) }.getOrNull()
        } ?: return null
        val generation = storage.readLong(prefix + GENERATION) ?: 0L
        val started = storage.readLong(prefix + STARTED) ?: 0L
        val persistedDeadline = if (storage.contains(prefix + DEADLINE)) {
            storage.readLong(prefix + DEADLINE) ?: 0L
        } else {
            null
        }
        // External deadlines are Android control-operation deadlines. They do not claim that the
        // provider process stopped, but they must survive Activity recreation so the shared
        // coordinator can release the project operation lock and require recovery.
        val deadline = persistedDeadline
        if (generation <= 0L || started <= 0L) return null
        if (deadline != null && deadline < started) return null
        return RuntimeOperationRecord(
            projectId = storage.readText(prefix + PROJECT) ?: projectId,
            provider = provider,
            action = action,
            executionId = if (storage.contains(prefix + EXECUTION_ID)) {
                storage.readInt(prefix + EXECUTION_ID) ?: 0
            } else {
                null
            },
            generation = generation,
            startedAtEpochMs = started,
            deadlineAtEpochMs = deadline,
            userVisible = storage.readBoolean(prefix + USER_VISIBLE) ?: true,
            phase = phase,
        )
    }

    override fun lastGeneration(projectId: String): Long =
        storage.readLong(prefix(projectId) + NEXT_GENERATION) ?: 0L

    override fun write(record: RuntimeOperationRecord) {
        val prefix = prefix(record.projectId)
        val writes = linkedMapOf<String, StoredStateValue>(
            prefix + PROJECT to StoredStateValue.Text(record.projectId),
            prefix + PROVIDER to StoredStateValue.Text(record.provider.name),
            prefix + ACTION to StoredStateValue.Text(record.action.name),
            prefix + PHASE to StoredStateValue.Text(record.phase.name),
            prefix + GENERATION to StoredStateValue.LongNumber(record.generation),
            prefix + NEXT_GENERATION to StoredStateValue.LongNumber(record.generation),
            prefix + STARTED to StoredStateValue.LongNumber(record.startedAtEpochMs),
            prefix + USER_VISIBLE to StoredStateValue.Bool(record.userVisible),
        )
        val removals = linkedSetOf<String>()

        if (record.deadlineAtEpochMs == null) {
            removals += prefix + DEADLINE
        } else {
            writes[prefix + DEADLINE] = StoredStateValue.LongNumber(record.deadlineAtEpochMs)
        }

        if (record.executionId == null) {
            removals += prefix + EXECUTION_ID
        } else {
            writes[prefix + EXECUTION_ID] = StoredStateValue.IntNumber(record.executionId)
        }

        storage.mutate(
            StateStorageMutation(
                writes = writes,
                removals = removals,
            ),
        )
    }

    override fun clear(projectId: String) {
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
                    prefix + USER_VISIBLE,
                    prefix + EXECUTION_ID,
                ),
            ),
        )
    }

    private fun prefix(projectId: String): String = PREFIX + digest(projectId) + ":"

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val PREFS_NAME = "siftalpha_runtime_operations_v1"
        private const val PREFIX = "operation:"
        private const val PROJECT = "project"
        private const val PROVIDER = "provider"
        private const val ACTION = "action"
        private const val PHASE = "phase"
        private const val GENERATION = "generation"
        private const val NEXT_GENERATION = "next_generation"
        private const val STARTED = "started"
        private const val DEADLINE = "deadline"
        private const val USER_VISIBLE = "user_visible"
        private const val EXECUTION_ID = "execution_id"
    }
}
