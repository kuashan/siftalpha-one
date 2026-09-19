package com.siftalpha.studio.runtime

import android.content.Context
import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Minimal durable operation metadata. It is deliberately not a result/history database: it only
 * lets a recreated Activity distinguish a real unfinished operation from a recovery probe.
 */
class RuntimeOperationStore internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun read(projectId: String): RuntimeOperationRecord? {
        val prefix = prefix(projectId)
        val action = prefs.getString(prefix + ACTION, null)?.let {
            runCatching { RuntimeOperationAction.valueOf(it) }.getOrNull()
        } ?: return null
        val provider = prefs.getString(prefix + PROVIDER, null)?.let {
            runCatching { RuntimeOperationProvider.valueOf(it) }.getOrNull()
        } ?: return null
        val phase = prefs.getString(prefix + PHASE, null)?.let {
            runCatching { RuntimeOperationPhase.valueOf(it) }.getOrNull()
        } ?: return null
        val generation = prefs.getLong(prefix + GENERATION, 0L)
        val started = prefs.getLong(prefix + STARTED, 0L)
        val persistedDeadline = if (prefs.contains(prefix + DEADLINE)) {
            prefs.getLong(prefix + DEADLINE, 0L)
        } else {
            null
        }
        // r14 persisted External deadlines, but those deadlines were never part of the
        // backend-owned External contract. Ignore such legacy values during reconciliation.
        val deadline = if (provider == RuntimeOperationProvider.EXTERNAL) null else persistedDeadline
        if (generation <= 0L || started <= 0L) return null
        if (deadline != null && deadline < started) return null
        return RuntimeOperationRecord(
            projectId = prefs.getString(prefix + PROJECT, projectId) ?: projectId,
            provider = provider,
            action = action,
            executionId = if (prefs.contains(prefix + EXECUTION_ID)) {
                prefs.getInt(prefix + EXECUTION_ID, 0)
            } else {
                null
            },
            generation = generation,
            startedAtEpochMs = started,
            deadlineAtEpochMs = deadline,
            userVisible = prefs.getBoolean(prefix + USER_VISIBLE, true),
            phase = phase,
        )
    }

    fun lastGeneration(projectId: String): Long =
        prefs.getLong(prefix(projectId) + NEXT_GENERATION, 0L)

    fun write(record: RuntimeOperationRecord) {
        val prefix = prefix(record.projectId)
        prefs.edit()
            .putString(prefix + PROJECT, record.projectId)
            .putString(prefix + PROVIDER, record.provider.name)
            .putString(prefix + ACTION, record.action.name)
            .putString(prefix + PHASE, record.phase.name)
            .putLong(prefix + GENERATION, record.generation)
            .putLong(prefix + NEXT_GENERATION, record.generation)
            .putLong(prefix + STARTED, record.startedAtEpochMs)
            .putBoolean(prefix + USER_VISIBLE, record.userVisible)
            .apply {
                if (record.deadlineAtEpochMs == null) remove(prefix + DEADLINE)
                else putLong(prefix + DEADLINE, record.deadlineAtEpochMs!!)
                if (record.executionId == null) remove(prefix + EXECUTION_ID)
                else putInt(prefix + EXECUTION_ID, record.executionId)
            }
            .apply()
    }

    fun clear(projectId: String) {
        val prefix = prefix(projectId)
        prefs.edit()
            .remove(prefix + PROJECT)
            .remove(prefix + PROVIDER)
            .remove(prefix + ACTION)
            .remove(prefix + PHASE)
            .remove(prefix + GENERATION)
            .remove(prefix + STARTED)
            .remove(prefix + DEADLINE)
            .remove(prefix + USER_VISIBLE)
            .remove(prefix + EXECUTION_ID)
            .apply()
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
