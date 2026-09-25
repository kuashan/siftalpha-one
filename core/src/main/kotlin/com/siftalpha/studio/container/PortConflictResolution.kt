package com.siftalpha.studio.container

enum class PortBindingOwnership { FREE, CURRENT_PROJECT, OTHER_SIFTALPHA_PROJECT, EXTERNAL_PROCESS, UNKNOWN }

enum class PortRemapCapability { SUPPORTED, UNSUPPORTED }

data class RequestedPortBinding(
    val publishedPort: Int,
    val targetPort: Int,
    val protocol: String = "tcp",
)

sealed interface PortResolutionPlan {
    data object KeepRequestedPort : PortResolutionPlan
    data class RemapAutomatically(val requestedPort: Int, val ownership: PortBindingOwnership) : PortResolutionPlan
    data class BlockSafely(val requestedPort: Int, val ownership: PortBindingOwnership) : PortResolutionPlan
}

object PortConflictResolutionPolicy {
    fun resolve(
        binding: RequestedPortBinding,
        ownership: PortBindingOwnership,
        remapCapability: PortRemapCapability,
    ): PortResolutionPlan = when (ownership) {
        PortBindingOwnership.FREE, PortBindingOwnership.CURRENT_PROJECT -> PortResolutionPlan.KeepRequestedPort
        else -> if (remapCapability == PortRemapCapability.SUPPORTED) {
            PortResolutionPlan.RemapAutomatically(binding.publishedPort, ownership)
        } else {
            PortResolutionPlan.BlockSafely(binding.publishedPort, ownership)
        }
    }
}
