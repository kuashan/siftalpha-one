package com.siftalpha.studio.runtime

import android.content.Context
import android.content.SharedPreferences

/**
 * Shared External Provider（外部执行环境） readiness model.
 *
 * This class contains no Activity/UI behavior. Permission dialogs, opening Termux and user-facing
 * messages stay in the presentation layer. Both Normal Mode（普通模式） and Developer Workspace
 * （开发者工作区） consume the same readiness facts from here.
 */
enum class ExternalProviderReadinessStatus {
    TERMUX_NOT_INSTALLED,
    RUN_COMMAND_PERMISSION_REQUIRED,
    BRIDGE_PROBE_REQUIRED,
    TERMUX_CONFIGURATION_REQUIRED,
    BRIDGE_UNAVAILABLE,
    READY,
}

data class ExternalProviderReadiness(
    val status: ExternalProviderReadinessStatus,
    val termuxInstalled: Boolean,
    val runCommandPermissionGranted: Boolean,
    val bridgeVerified: Boolean,
    val allowExternalApps: Boolean?,
    val checkedAtEpochMs: Long? = null,
) {
    val ready: Boolean
        get() = status == ExternalProviderReadinessStatus.READY
}

object ExternalProviderPreflightPolicy {
    const val DEFAULT_FRESHNESS_MS = 30_000L

    fun local(
        termuxInstalled: Boolean,
        runCommandPermissionGranted: Boolean,
    ): ExternalProviderReadiness = when {
        !termuxInstalled -> ExternalProviderReadiness(
            status = ExternalProviderReadinessStatus.TERMUX_NOT_INSTALLED,
            termuxInstalled = false,
            runCommandPermissionGranted = false,
            bridgeVerified = false,
            allowExternalApps = null,
        )
        !runCommandPermissionGranted -> ExternalProviderReadiness(
            status = ExternalProviderReadinessStatus.RUN_COMMAND_PERMISSION_REQUIRED,
            termuxInstalled = true,
            runCommandPermissionGranted = false,
            bridgeVerified = false,
            allowExternalApps = null,
        )
        else -> ExternalProviderReadiness(
            status = ExternalProviderReadinessStatus.BRIDGE_PROBE_REQUIRED,
            termuxInstalled = true,
            runCommandPermissionGranted = true,
            bridgeVerified = false,
            allowExternalApps = null,
        )
    }

    fun fromProbe(
        local: ExternalProviderReadiness,
        result: RuntimeResult,
        checkedAtEpochMs: Long,
    ): ExternalProviderReadiness {
        if (local.status != ExternalProviderReadinessStatus.BRIDGE_PROBE_REQUIRED) {
            return local
        }

        val output = result.stdout + "\n" + result.stderr
        val bridgeMarker = "SIFTALPHA_TERMUX_BRIDGE_OK" in output
        val allowExternalApps = when {
            "SIFTALPHA_TERMUX_ALLOW_EXTERNAL_APPS=YES" in output -> true
            "SIFTALPHA_TERMUX_ALLOW_EXTERNAL_APPS=NO" in output -> false
            else -> null
        }
        val transportSucceeded =
            result.exitCode == 0 &&
                result.internalErrorMessage.isBlank() &&
                bridgeMarker

        return when {
            !transportSucceeded -> ExternalProviderReadiness(
                status = ExternalProviderReadinessStatus.BRIDGE_UNAVAILABLE,
                termuxInstalled = true,
                runCommandPermissionGranted = true,
                bridgeVerified = false,
                allowExternalApps = allowExternalApps,
                checkedAtEpochMs = checkedAtEpochMs,
            )
            allowExternalApps == false -> ExternalProviderReadiness(
                status = ExternalProviderReadinessStatus.TERMUX_CONFIGURATION_REQUIRED,
                termuxInstalled = true,
                runCommandPermissionGranted = true,
                bridgeVerified = true,
                allowExternalApps = false,
                checkedAtEpochMs = checkedAtEpochMs,
            )
            else -> ExternalProviderReadiness(
                status = ExternalProviderReadinessStatus.READY,
                termuxInstalled = true,
                runCommandPermissionGranted = true,
                bridgeVerified = true,
                allowExternalApps = allowExternalApps,
                checkedAtEpochMs = checkedAtEpochMs,
            )
        }
    }

    fun isFresh(
        snapshot: ExternalProviderReadiness,
        nowEpochMs: Long,
        freshnessMs: Long = DEFAULT_FRESHNESS_MS,
    ): Boolean {
        if (!snapshot.ready) return false
        val checkedAt = snapshot.checkedAtEpochMs ?: return false
        return nowEpochMs >= checkedAt && nowEpochMs - checkedAt <= freshnessMs
    }
}

/**
 * App-wide persisted bridge evidence. It is intentionally not project state: Termux RUN_COMMAND
 * readiness is an External Provider property shared by all projects.
 */
class ExternalProviderReadinessStore internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun read(): ExternalProviderReadiness? {
        val status = prefs.getString(FIELD_STATUS, null)
            ?.let { runCatching { ExternalProviderReadinessStatus.valueOf(it) }.getOrNull() }
            ?: return null
        return ExternalProviderReadiness(
            status = status,
            termuxInstalled = prefs.getBoolean(FIELD_INSTALLED, false),
            runCommandPermissionGranted = prefs.getBoolean(FIELD_PERMISSION, false),
            bridgeVerified = prefs.getBoolean(FIELD_BRIDGE, false),
            allowExternalApps = if (prefs.contains(FIELD_ALLOW_EXTERNAL_APPS)) {
                prefs.getBoolean(FIELD_ALLOW_EXTERNAL_APPS, false)
            } else {
                null
            },
            checkedAtEpochMs = if (prefs.contains(FIELD_CHECKED_AT)) {
                prefs.getLong(FIELD_CHECKED_AT, 0L)
            } else {
                null
            },
        )
    }

    fun write(snapshot: ExternalProviderReadiness) {
        prefs.edit()
            .putString(FIELD_STATUS, snapshot.status.name)
            .putBoolean(FIELD_INSTALLED, snapshot.termuxInstalled)
            .putBoolean(FIELD_PERMISSION, snapshot.runCommandPermissionGranted)
            .putBoolean(FIELD_BRIDGE, snapshot.bridgeVerified)
            .apply {
                if (snapshot.allowExternalApps == null) remove(FIELD_ALLOW_EXTERNAL_APPS)
                else putBoolean(FIELD_ALLOW_EXTERNAL_APPS, snapshot.allowExternalApps)
                if (snapshot.checkedAtEpochMs == null) remove(FIELD_CHECKED_AT)
                else putLong(FIELD_CHECKED_AT, snapshot.checkedAtEpochMs)
            }
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "siftalpha_external_provider_readiness_v1"
        private const val FIELD_STATUS = "status"
        private const val FIELD_INSTALLED = "installed"
        private const val FIELD_PERMISSION = "permission"
        private const val FIELD_BRIDGE = "bridge"
        private const val FIELD_ALLOW_EXTERNAL_APPS = "allow_external_apps"
        private const val FIELD_CHECKED_AT = "checked_at"
    }
}

class ExternalProviderPreflight(
    private val backend: TermuxBackend,
    private val store: ExternalProviderReadinessStore,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) {
    constructor(
        context: Context,
        backend: TermuxBackend = TermuxBackend(context),
    ) : this(
        backend = backend,
        store = ExternalProviderReadinessStore(context),
    )

    fun inspect(): ExternalProviderReadiness {
        val local = ExternalProviderPreflightPolicy.local(
            termuxInstalled = runCatching { backend.isTermuxInstalled() }.getOrDefault(false),
            runCommandPermissionGranted = runCatching { backend.hasRunCommandPermission() }
                .getOrDefault(false),
        )
        if (local.status != ExternalProviderReadinessStatus.BRIDGE_PROBE_REQUIRED) {
            store.write(local)
            return local
        }

        val cached = store.read()
        return if (
            cached != null &&
            cached.termuxInstalled &&
            cached.runCommandPermissionGranted &&
            ExternalProviderPreflightPolicy.isFresh(cached, nowEpochMs())
        ) {
            cached
        } else {
            local
        }
    }

    fun probeCommand(): RuntimeCommand = TermuxBackend.CONNECTION_TEST

    fun recordProbe(result: RuntimeResult): ExternalProviderReadiness {
        val local = ExternalProviderPreflightPolicy.local(
            termuxInstalled = runCatching { backend.isTermuxInstalled() }.getOrDefault(false),
            runCommandPermissionGranted = runCatching { backend.hasRunCommandPermission() }
                .getOrDefault(false),
        )
        val snapshot = ExternalProviderPreflightPolicy.fromProbe(
            local = local,
            result = result,
            checkedAtEpochMs = nowEpochMs(),
        )
        store.write(snapshot)
        return snapshot
    }

    fun invalidateBridgeEvidence() {
        val local = ExternalProviderPreflightPolicy.local(
            termuxInstalled = runCatching { backend.isTermuxInstalled() }.getOrDefault(false),
            runCommandPermissionGranted = runCatching { backend.hasRunCommandPermission() }
                .getOrDefault(false),
        )
        store.write(local)
    }
}
