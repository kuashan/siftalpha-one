package com.siftalpha.studio.runtime

import android.content.Context
import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

class RuntimeLifecycleStore internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    data class Snapshot(
        val externalEnvironmentReady: Boolean?,
        val embeddedEnvironmentReady: Boolean?,
        val runtimeState: RuntimeState,
        val failureReason: String?,
    ) {
        val environmentReady: Boolean?
            get() = externalEnvironmentReady

        fun environmentReadyFor(selection: ProjectRuntimeSelection): Boolean? = when (selection) {
            ProjectRuntimeSelection.TERMUX -> externalEnvironmentReady
            ProjectRuntimeSelection.EMBEDDED_R -> embeddedEnvironmentReady
        }
    }

    fun read(projectKey: String): Snapshot = Snapshot(
        externalEnvironmentReady = readEnvironment(
            projectKey,
            FIELD_ENV_PRESENT,
            FIELD_ENV_VALUE,
            allowLegacyFallback = true,
        ),
        embeddedEnvironmentReady = readEnvironment(
            projectKey,
            FIELD_EMBEDDED_ENV_PRESENT,
            FIELD_EMBEDDED_ENV_VALUE,
            allowLegacyFallback = false,
        ),
        runtimeState = readState(projectKey),
        failureReason = readFailureReason(projectKey),
    )

    fun write(
        projectKey: String,
        environmentReady: Boolean?,
        runtimeState: RuntimeState,
        failureReason: String?,
        runtimeSelection: ProjectRuntimeSelection = ProjectRuntimeSelection.TERMUX,
    ) {
        val (presentField, valueField) = when (runtimeSelection) {
            ProjectRuntimeSelection.TERMUX -> FIELD_ENV_PRESENT to FIELD_ENV_VALUE
            ProjectRuntimeSelection.EMBEDDED_R ->
                FIELD_EMBEDDED_ENV_PRESENT to FIELD_EMBEDDED_ENV_VALUE
        }
        val editor = prefs.edit().putString(key(projectKey, FIELD_STATE), runtimeState.name)
        if (environmentReady == null) {
            editor.remove(key(projectKey, presentField)).remove(key(projectKey, valueField))
        } else {
            editor.putBoolean(key(projectKey, presentField), true)
                .putBoolean(key(projectKey, valueField), environmentReady)
        }
        if (failureReason.isNullOrBlank()) {
            editor.remove(key(projectKey, FIELD_FAILURE))
        } else {
            editor.putString(key(projectKey, FIELD_FAILURE), failureReason.trim().take(MAX_FAILURE_LENGTH))
        }
        editor.apply()
    }

    fun clear(projectKey: String) {
        prefs.edit()
            .remove(key(projectKey, FIELD_STATE))
            .remove(key(projectKey, FIELD_ENV_PRESENT))
            .remove(key(projectKey, FIELD_ENV_VALUE))
            .remove(key(projectKey, FIELD_EMBEDDED_ENV_PRESENT))
            .remove(key(projectKey, FIELD_EMBEDDED_ENV_VALUE))
            .remove(key(projectKey, FIELD_FAILURE))
            .apply()
    }

    private fun readEnvironment(
        projectKey: String,
        presentField: String,
        valueField: String,
        allowLegacyFallback: Boolean,
    ): Boolean? {
        val present = readBoolean(
            key(projectKey, presentField),
            legacyKey(projectKey, presentField).takeIf { allowLegacyFallback },
            false,
        )
        if (!present) return null
        return readBoolean(
            key(projectKey, valueField),
            legacyKey(projectKey, valueField).takeIf { allowLegacyFallback },
            false,
        )
    }

    private fun readBoolean(currentKey: String, oldKey: String?, defaultValue: Boolean): Boolean {
        val currentRaw = rawValue(currentKey)
        val currentValue = currentRaw.toBooleanOrNull()
        if (currentValue != null) {
            migrateBoolean(currentKey, currentRaw, currentValue)
            return currentValue
        }
        if (oldKey != null) {
            val oldRaw = rawValue(oldKey)
            val oldValue = oldRaw.toBooleanOrNull()
            if (oldValue != null) {
                migrateBoolean(currentKey, oldRaw, oldValue)
                return oldValue
            }
        }
        return defaultValue
    }

    private fun readState(projectKey: String): RuntimeState =
        runtimeStateFrom(rawValue(key(projectKey, FIELD_STATE)))
            ?: runtimeStateFrom(rawValue(legacyKey(projectKey, FIELD_STATE)))
            ?: RuntimeState.UNKNOWN

    private fun readFailureReason(projectKey: String): String? {
        val current = rawValue(key(projectKey, FIELD_FAILURE))
        if (current is String) return current
        val old = rawValue(legacyKey(projectKey, FIELD_FAILURE)) as? String ?: return null
        if (old.toBooleanOrNull() != null || runtimeStateFrom(old) != null) return null
        return old
    }

    private fun runtimeStateFrom(value: Any?): RuntimeState? =
        (value as? String)?.let { runCatching { RuntimeState.valueOf(it) }.getOrNull() }

    private fun migrateBoolean(key: String, raw: Any?, value: Boolean) {
        if (raw is String) runCatching { prefs.edit().putBoolean(key, value).apply() }
    }

    private fun rawValue(key: String): Any? = runCatching { prefs.all[key] }.getOrNull()

    private fun Any?.toBooleanOrNull(): Boolean? = when (this) {
        is Boolean -> this
        is String -> when (trim().lowercase(Locale.ROOT)) {
            "true" -> true
            "false" -> false
            else -> null
        }
        else -> null
    }

    private fun key(projectKey: String, suffix: String): String =
        "project:" + digest(projectKey) + ":" + suffix

    @Suppress("UNUSED_PARAMETER")
    private fun legacyKey(projectKey: String, suffix: String): String =
        "project:${'$'}{digest(projectKey)}:${'$'}suffix"

    private fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS_NAME = "siftalpha_runtime_lifecycle_v1"
        private const val FIELD_STATE = "state"
        private const val FIELD_ENV_PRESENT = "env_present"
        private const val FIELD_ENV_VALUE = "env_value"
        private const val FIELD_EMBEDDED_ENV_PRESENT = "embedded_env_present"
        private const val FIELD_EMBEDDED_ENV_VALUE = "embedded_env_value"
        private const val FIELD_FAILURE = "failure"
        private const val MAX_FAILURE_LENGTH = 240
    }
}
