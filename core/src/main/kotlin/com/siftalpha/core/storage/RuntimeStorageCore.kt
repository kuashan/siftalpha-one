package com.siftalpha.core.storage

/** Platform-neutral categories shown by Runtime Storage Manager（运行空间管理器）. */
enum class RuntimeStorageKind {
    PROJECT_ENVIRONMENT,
    ORPHAN_PROJECT_DATA,
    SHARED_CACHE,
    MANAGED_TOOLCHAIN,
    PLATFORM_RUNTIME_STATE,
}

data class RuntimeStorageEntry(
    val id: String,
    val label: String,
    val kind: RuntimeStorageKind,
    val sizeBytes: Long,
    val projectId: String? = null,
    val active: Boolean = false,
    val reproducible: Boolean = false,
    val cleanable: Boolean = false,
    val detail: String? = null,
) {
    init {
        require(id.isNotBlank()) { "storage entry id must not be blank" }
        require(label.isNotBlank()) { "storage entry label must not be blank" }
        require(sizeBytes >= 0L) { "storage entry size must not be negative" }
        require(!cleanable || !active) { "active storage cannot be marked cleanable" }
    }
}

data class RuntimeStorageSnapshot(
    val entries: List<RuntimeStorageEntry>,
) {
    val totalBytes: Long
        get() = entries.sumOf { it.sizeBytes }

    val cleanableBytes: Long
        get() = entries.filter(RuntimeStorageCleanupPolicy::canClean).sumOf { it.sizeBytes }

    val orphanBytes: Long
        get() = entries
            .filter { it.kind == RuntimeStorageKind.ORPHAN_PROJECT_DATA }
            .sumOf { it.sizeBytes }
}

/**
 * Shared cleanup safety policy. Adapters identify files/resources; Core decides whether an entry is
 * eligible for user-triggered automatic cleanup.
 */
object RuntimeStorageCleanupPolicy {
    fun canClean(entry: RuntimeStorageEntry): Boolean =
        entry.cleanable && entry.reproducible && !entry.active

    fun safeEntries(snapshot: RuntimeStorageSnapshot): List<RuntimeStorageEntry> =
        snapshot.entries.filter(::canClean)
}
