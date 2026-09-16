package com.siftalpha.studio.runtime

import android.content.Context
import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Persists the user's per-project Runtime choice without modifying the SAF source project.
 *
 * The SAF document ID is the stable project identity. Projects created before this preference
 * existed have no record and intentionally default to the External Provider / Termux path.
 */
enum class ProjectRuntimeSelection(
    val controlRequest: RuntimeControlRequest,
) {
    TERMUX(RuntimeControlRequest.EXTERNAL_PROVIDER),
    EMBEDDED_R(RuntimeControlRequest.EMBEDDED_R);

    companion object {
        fun fromStored(value: String?): ProjectRuntimeSelection =
            entries.firstOrNull { it.name == value } ?: TERMUX
    }
}

class ProjectRuntimeSelectionStore internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun read(projectDocumentId: String): ProjectRuntimeSelection {
        require(projectDocumentId.isNotBlank()) { "projectDocumentId must not be blank" }
        val raw = runCatching {
            prefs.getString(key(projectDocumentId), null)
        }.getOrNull()
        return ProjectRuntimeSelection.fromStored(raw)
    }

    fun write(
        projectDocumentId: String,
        selection: ProjectRuntimeSelection,
    ) {
        require(projectDocumentId.isNotBlank()) { "projectDocumentId must not be blank" }
        prefs.edit()
            .putString(key(projectDocumentId), selection.name)
            .apply()
    }

    fun clear(projectDocumentId: String) {
        require(projectDocumentId.isNotBlank()) { "projectDocumentId must not be blank" }
        prefs.edit().remove(key(projectDocumentId)).apply()
    }

    private fun key(projectDocumentId: String): String =
        "project:" + digest(projectDocumentId) + ":" + FIELD_RUNTIME_SELECTION

    private fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val PREFS_NAME = "siftalpha_project_runtime_selection_v1"
        private const val FIELD_RUNTIME_SELECTION = "runtime_selection"
    }
}
