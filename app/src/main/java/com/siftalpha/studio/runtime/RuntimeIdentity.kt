package com.siftalpha.studio.runtime

/**
 * Identifies which runtime identity evidence a consumer is using.
 *
 * FULL_IDENTITY is the host/guest sidecar pair. The *_ONLY values describe an incomplete pair
 * before a legacy PID fallback is selected. LEGACY_PID means the old pid/pgid files are the
 * available runtime scope, while UNAVAILABLE means neither identity contract is available.
 */
enum class RuntimeIdentitySource {
    FULL_IDENTITY,
    HOST_ONLY,
    GUEST_ONLY,
    LEGACY_PID,
    UNAVAILABLE,
}

/**
 * Generic identity for one managed runtime session.
 *
 * The host PID/PGID identify the Termux-side session used by the existing lifecycle commands.
 * The guest PID/PGID identify the root process inside the PRoot environment and are the only
 * identities that guest-scoped Web discovery should use. No language-specific process fields are
 * part of this model.
 */
data class RuntimeIdentity(
    val runtimeId: String,
    val runtimeToken: String,
    val startTime: Long,
    val schemaVersion: Int = RuntimeIdentityStore.CURRENT_SCHEMA_VERSION,
    val hostSessionPid: Long? = null,
    val hostSessionPgid: Long? = null,
    val guestRootPid: Long? = null,
    val guestRootPgid: Long? = null,
)
