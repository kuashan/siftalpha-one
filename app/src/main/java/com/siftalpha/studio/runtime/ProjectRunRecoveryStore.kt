package com.siftalpha.studio.runtime

import android.content.Context

/**
 * Shared pending-run intent. It remembers only that the user intended to RUN and which recovery
 * gate interrupted that intent. It never stores secrets, CLI values or Runtime state.
 */
class ProjectRunRecoveryStore(context: Context) {

    enum class Reason {
        CONFIGURATION,
        CLI_ARGUMENTS,
    }

    data class Pending(
        val reason: Reason,
        val updatedAtEpochMs: Long,
    )

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun read(projectId: String): Pending? {
        val reason = prefs.getString(reasonKey(projectId), null)
            ?.let { runCatching { Reason.valueOf(it) }.getOrNull() }
            ?: return null
        return Pending(
            reason = reason,
            updatedAtEpochMs = prefs.getLong(updatedAtKey(projectId), 0L),
        )
    }

    fun mark(projectId: String, reason: Reason) {
        prefs.edit()
            .putString(reasonKey(projectId), reason.name)
            .putLong(updatedAtKey(projectId), System.currentTimeMillis())
            .apply()
    }

    fun clear(projectId: String) {
        prefs.edit()
            .remove(reasonKey(projectId))
            .remove(updatedAtKey(projectId))
            .apply()
    }

    private fun reasonKey(projectId: String): String =
        "reason:" + projectId.length + ":" + projectId

    private fun updatedAtKey(projectId: String): String =
        "updated:" + projectId.length + ":" + projectId

    companion object {
        private const val PREFS_NAME = "siftalpha_project_run_recovery_v1"
    }
}
