package com.siftalpha.studio.platform

/**
 * Stable identifier for one optional platform capability（平台能力）.
 *
 * Core（核心） may name a capability, but must never assume every platform implements it.
 * Platform adapters（平台适配层） publish their own availability.
 */
data class PlatformCapability(
    val id: String,
) {
    init {
        require(id.isNotBlank()) { "capability id must not be blank" }
    }
}

enum class CapabilityAvailability {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN,
}

/**
 * Immutable platform capability snapshot（平台能力快照） consumed by Core（核心） policy.
 *
 * Missing entries are UNKNOWN（未知）, never implicitly AVAILABLE（可用）.
 */
class PlatformCapabilitySnapshot(
    availability: Map<PlatformCapability, CapabilityAvailability> = emptyMap(),
) {
    private val values = availability.toMap()

    fun availabilityOf(capability: PlatformCapability): CapabilityAvailability =
        values[capability] ?: CapabilityAvailability.UNKNOWN

    fun supports(capability: PlatformCapability): Boolean =
        availabilityOf(capability) == CapabilityAvailability.AVAILABLE
}

/**
 * Common capability identifiers only. These identifiers do not require every platform to
 * implement the corresponding capability.
 */
object StandardPlatformCapabilities {
    val HOST_PROCESS_EXECUTION = PlatformCapability("host_process_execution")
    val SECURE_SECRET_STORAGE = PlatformCapability("secure_secret_storage")
    val CONTAINER_RUNTIME = PlatformCapability("container_runtime")
    val LINUX_COMPATIBILITY_LAYER = PlatformCapability("linux_compatibility_layer")
    val VIRTUAL_MACHINE_RUNTIME = PlatformCapability("virtual_machine_runtime")
}
