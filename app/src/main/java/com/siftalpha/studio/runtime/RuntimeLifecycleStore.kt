package com.siftalpha.studio.runtime

import android.content.Context
import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Small, non-secret persistence layer for lifecycle recovery.
 *
 * It stores only project identity-derived keys, the last typed state, environment readiness, and
 * a bounded redacted failure reason. Persisted active states are hints only and are reconciled by
 * a real STATUS command when the Activity returns to the foreground.
 */
class RuntimeLifecycleStore internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    data class Snapshot(
        val environmentReady: Boolean?,
        val runtimeState: RuntimeState,
        val failureReason: String?,
    )

    fun read(projectKey: String): Snapshot {
        val environmentReady = if (
            readBoolean(
                key(projectKey, FIELD_ENV_PRESENT),
                legacyKey(projectKey, FIELD_ENV_PRESENT),
                defaultValue = false,
            )
        ) {
            readBoolean(
                key(projectKey, FIELD_ENV_VALUE),
                legacyKey(projectKey, FIELD_ENV_VALUE),
                defaultValue = false,
            )
        } else {
            null
        }

        return Snapshot(
            environmentReady = environmentReady,
            runtimeState = readState(projectKey),
            failureReason = readFailureReason(projectKey),
        )
    }

    fun write(
        projectKey: String,
        environmentReady: Boolean?,
        runtimeState: RuntimeState,
        failureReason: String?,
    ) {
        val editor = prefs.edit()
            .putString(key(projectKey, FIELD_STATE), runtimeState.name)
        if (environmentReady == null) {
            editor.remove(key(projectKey, FIELD_ENV_PRESENT))
                .remove(key(projectKey, FIELD_ENV_VALUE))
        } else {
            editor.putBoolean(key(projectKey, FIELD_ENV_PRESENT), true)
                .putBoolean(key(projectKey, FIELD_ENV_VALUE), environmentReady)
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
            .remove(key(projectKey, FIELD_FAILURE))
            .apply()
    }

    private fun readBoolean(
        currentKey: String,
        oldKey: String,
        defaultValue: Boolean,
    ): Boolean {
        val currentRaw = rawValue(currentKey)
        val currentValue = currentRaw.toBooleanOrNull()
        if (currentValue != null) {
            migrateBoolean(currentKey, currentRaw, currentValue)
            return currentValue
        }

        val oldRaw = rawValue(oldKey)
        val oldValue = oldRaw.toBooleanOrNull()
        if (oldValue != null) {
            // The old key may have been shared by several fields. Copy only the boolean fact that
            // can be established safely; all other lifecycle facts use their own safe fallback.
            migrateBoolean(currentKey, oldRaw, oldValue)
            return oldValue
        }

        return defaultValue
    }

    private fun readState(projectKey: String): RuntimeState {
        val current = runtimeStateFrom(rawValue(key(projectKey, FIELD_STATE)))
        if (current != null) return current

        return runtimeStateFrom(rawValue(legacyKey(projectKey, FIELD_STATE)))
            ?: RuntimeState.UNKNOWN
    }

    private fun readFailureReason(projectKey: String): String? {
        val current = rawValue(key(projectKey, FIELD_FAILURE))
        if (current is String) return current

        val old = rawValue(legacyKey(projectKey, FIELD_FAILURE)) as? String ?: return null
        if (old.toBooleanOrNull() != null || runtimeStateFrom(old) != null) return null
        return old
    }

    private fun runtimeStateFrom(value: Any?): RuntimeState? =
        (value as? String)?.let { token ->
            runCatching { RuntimeState.valueOf(token) }.getOrNull()
        }

    private fun migrateBoolean(key: String, raw: Any?, value: Boolean) {
        if (raw is String) {
            runCatching {
                prefs.edit().putBoolean(key, value).apply()
            }
        }
    }

    private fun rawValue(key: String): Any? =
        runCatching { prefs.all[key] }.getOrNull()

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
        "project:${digest(projectKey)}:$suffix"

    /**
     * Exact key shape emitted by the pre-migration implementation.
     *
     * It is intentionally retained only as a read fallback so an old malformed preference cannot
     * crash the Activity or overwrite the new field-specific keys.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun legacyKey(projectKey: String, suffix: String): String =
        "project:${'$'}{digest(projectKey)}:${'$'}suffix"

    private fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val PREFS_NAME = "siftalpha_runtime_lifecycle_v1"
        private const val FIELD_STATE = "state"
        private const val FIELD_ENV_PRESENT = "env_present"
        private const val FIELD_ENV_VALUE = "env_value"
        private const val FIELD_FAILURE = "failure"
        private const val MAX_FAILURE_LENGTH = 240
    }
}
