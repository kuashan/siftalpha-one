package com.siftalpha.studio.runtime

import android.content.Context
import android.content.SharedPreferences
import com.siftalpha.core.operation.ProjectOperationAction
import com.siftalpha.core.operation.ProjectOperationPhase
import com.siftalpha.core.storage.DurableProjectOperationRecord
import com.siftalpha.core.storage.DurableProjectOperationStore
import com.siftalpha.core.storage.PlatformStateStorage

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
    private val delegate = DurableProjectOperationStore(
        storage = storage,
        keyPrefix = "operation:",
    )

    internal constructor(prefs: SharedPreferences) : this(
        AndroidSharedPreferencesStateStorage(prefs),
    )

    constructor(context: Context) : this(
        AndroidSharedPreferencesStateStorage(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        ),
    )

    override fun read(projectId: String): RuntimeOperationRecord? =
        delegate.read(projectId)?.let { record ->
            RuntimeOperationRecord(
                projectId = record.projectId,
                provider = runCatching { RuntimeOperationProvider.valueOf(record.providerId) }.getOrNull()
                    ?: return null,
                action = RuntimeOperationAction.valueOf(record.action.name),
                executionId = record.executionId,
                generation = record.generation,
                startedAtEpochMs = record.startedAtEpochMs,
                deadlineAtEpochMs = record.deadlineAtEpochMs,
                userVisible = record.userVisible,
                phase = RuntimeOperationPhase.valueOf(record.phase.name),
            )
        }

    override fun lastGeneration(projectId: String): Long =
        delegate.lastGeneration(projectId)

    override fun write(record: RuntimeOperationRecord) {
        delegate.write(
            DurableProjectOperationRecord(
                projectId = record.projectId,
                providerId = record.provider.name,
                action = ProjectOperationAction.valueOf(record.action.name),
                phase = ProjectOperationPhase.valueOf(record.phase.name),
                generation = record.generation,
                startedAtEpochMs = record.startedAtEpochMs,
                deadlineAtEpochMs = record.deadlineAtEpochMs,
                executionId = record.executionId,
                userVisible = record.userVisible,
            ),
        )
    }

    override fun clear(projectId: String) {
        delegate.clearCurrent(projectId)
    }

    companion object {
        private const val PREFS_NAME = "siftalpha_runtime_operations_v1"
    }
}
