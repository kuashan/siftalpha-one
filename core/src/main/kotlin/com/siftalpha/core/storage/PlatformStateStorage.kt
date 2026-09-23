package com.siftalpha.core.storage

/**
 * Platform-neutral app-state persistence port（平台无关应用状态持久化端口）.
 *
 * Core（核心） and shared domain stores can describe what state must be persisted without knowing
 * whether Android（安卓） uses SharedPreferences（偏好存储）, macOS（苹果） uses plist / files, or
 * Windows（微软） uses its own app-data implementation.
 */
interface PlatformStateStorage {
    fun read(key: String): StoredStateValue?

    fun mutate(mutation: StateStorageMutation)

    fun contains(key: String): Boolean = read(key) != null
}

/** Small primitive value set sufficient for durable SiftAlpha control state. */
sealed interface StoredStateValue {
    data class Text(val value: String) : StoredStateValue
    data class Bool(val value: Boolean) : StoredStateValue
    data class LongNumber(val value: Long) : StoredStateValue
    data class IntNumber(val value: Int) : StoredStateValue
}

/**
 * One logical storage mutation（一次逻辑存储变更）.
 *
 * Platform adapters should apply one mutation as one local commit/apply operation when possible.
 */
data class StateStorageMutation(
    val writes: Map<String, StoredStateValue> = emptyMap(),
    val removals: Set<String> = emptySet(),
) {
    init {
        require(writes.keys.intersect(removals).isEmpty()) {
            "the same storage key cannot be written and removed in one mutation"
        }
        require(writes.keys.none(String::isBlank)) { "storage write key must not be blank" }
        require(removals.none(String::isBlank)) { "storage removal key must not be blank" }
    }
}

fun PlatformStateStorage.readText(key: String): String? =
    (read(key) as? StoredStateValue.Text)?.value

fun PlatformStateStorage.readBoolean(key: String): Boolean? =
    (read(key) as? StoredStateValue.Bool)?.value

fun PlatformStateStorage.readLong(key: String): Long? =
    (read(key) as? StoredStateValue.LongNumber)?.value

fun PlatformStateStorage.readInt(key: String): Int? =
    (read(key) as? StoredStateValue.IntNumber)?.value
