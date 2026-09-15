package com.siftalpha.studio.runtime

/**
 * Identifies which runtime identity evidence a consumer is using.
 *
 * This is the resolution result, not the process scope ultimately used by Web discovery.
 * FULL_IDENTITY is the host/guest sidecar pair. The *_ONLY values describe a partial pair,
 * METADATA_MISMATCH describes a pair that cannot be safely merged, LEGACY_PID describes the
 * legacy PID files, and UNAVAILABLE means no identity evidence is available.
 */
enum class RuntimeIdentitySource {
    FULL_IDENTITY,
    HOST_ONLY,
    GUEST_ONLY,
    METADATA_MISMATCH,
    LEGACY_PID,
    UNAVAILABLE,
}

/** The process scope actually consumed by a discovery pass. */
enum class RuntimeIdentityUsage {
    FULL_IDENTITY,
    LEGACY_PID,
    UNAVAILABLE,
}

/**
 * Resolution details remain separate from [RuntimeIdentity] so a mismatch is observable without
 * changing the generic runtime identity data model or returning an unsafe merged identity.
 */
data class RuntimeIdentityResolution(
    val source: RuntimeIdentitySource,
    val identity: RuntimeIdentity? = null,
    val hostIdentity: RuntimeIdentity? = null,
    val guestIdentity: RuntimeIdentity? = null,
)

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
