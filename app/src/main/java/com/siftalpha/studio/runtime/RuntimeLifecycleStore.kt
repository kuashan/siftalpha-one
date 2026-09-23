package com.siftalpha.studio.runtime

import android.content.Context
import android.content.SharedPreferences
import com.siftalpha.core.storage.PlatformStateStorage
import com.siftalpha.core.storage.StateStorageMutation
import com.siftalpha.core.storage.StoredStateValue
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

class RuntimeLifecycleStore internal constructor(
    private val storage: PlatformStateStorage,
) {
    internal constructor(prefs: SharedPreferences) : this(
        AndroidSharedPreferencesStateStorage(prefs),
    )

    constructor(context: Context) : this(
        AndroidSharedPreferencesStateStorage(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        ),
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
        val writes = linkedMapOf<String, StoredStateValue>(
            key(projectKey, FIELD_STATE) to StoredStateValue.Text(runtimeState.name),
        )
        val removals = linkedSetOf<String>()

        if (environmentReady == null) {
            removals += key(projectKey, presentField)
            removals += key(projectKey, valueField)
        } else {
            writes[key(projectKey, presentField)] = StoredStateValue.Bool(true)
            writes[key(projectKey, valueField)] = StoredStateValue.Bool(environmentReady)
        }

        if (failureReason.isNullOrBlank()) {
            removals += key(projectKey, FIELD_FAILURE)
        } else {
            writes[key(projectKey, FIELD_FAILURE)] =
                StoredStateValue.Text(failureReason.trim().take(MAX_FAILURE_LENGTH))
        }

        storage.mutate(
            StateStorageMutation(
                writes = writes,
                removals = removals,
            ),
        )
    }

    fun clear(projectKey: String) {
        storage.mutate(
            StateStorageMutation(
                removals = setOf(
                    key(projectKey, FIELD_STATE),
                    key(projectKey, FIELD_ENV_PRESENT),
                    key(projectKey, FIELD_ENV_VALUE),
                    key(projectKey, FIELD_EMBEDDED_ENV_PRESENT),
                    key(projectKey, FIELD_EMBEDDED_ENV_VALUE),
                    key(projectKey, FIELD_FAILURE),
                ),
            ),
        )
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
        if (current is StoredStateValue.Text) return current.value
        val old = rawValue(legacyKey(projectKey, FIELD_FAILURE)) as? StoredStateValue.Text
            ?: return null
        if (old.toBooleanOrNull() != null || runtimeStateFrom(old) != null) return null
        return old.value
    }

    private fun runtimeStateFrom(value: StoredStateValue?): RuntimeState? =
        (value as? StoredStateValue.Text)?.value
            ?.let { runCatching { RuntimeState.valueOf(it) }.getOrNull() }

    private fun migrateBoolean(key: String, raw: StoredStateValue?, value: Boolean) {
        if (raw is StoredStateValue.Text) {
            runCatching {
                storage.mutate(
                    StateStorageMutation(
                        writes = mapOf(key to StoredStateValue.Bool(value)),
                    ),
                )
            }
        }
    }

    private fun rawValue(key: String): StoredStateValue? = runCatching {
        storage.read(key)
    }.getOrNull()

    private fun StoredStateValue?.toBooleanOrNull(): Boolean? = when (this) {
        is StoredStateValue.Bool -> value
        is StoredStateValue.Text -> when (value.trim().lowercase(Locale.ROOT)) {
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
        "project:${digest(projectKey)}:$suffix"

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
