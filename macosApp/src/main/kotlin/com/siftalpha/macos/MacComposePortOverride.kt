package com.siftalpha.macos

import com.siftalpha.studio.container.ComposeProjectPlan
import java.net.ServerSocket

data class MacComposePortOverrideResult(
    val remappedPorts: Map<Int, Int>,
    val yaml: String,
)

object MacComposePortOverride {
    fun resolve(
        plan: ComposeProjectPlan,
        isAvailable: (Int) -> Boolean = ::isAvailable,
    ): MacComposePortOverrideResult {
        val remapped = linkedMapOf<Int, Int>()
        val used = linkedSetOf<Int>()
        plan.services.flatMap { it.ports }.mapNotNull { it.published }.forEach { published ->
            if (!isAvailable(published)) {
                val replacement = generateSequence(published + 1) { it + 1 }
                    .takeWhile { it <= 65535 }
                    .firstOrNull { it !in used && isAvailable(it) }
                    ?: return MacComposePortOverrideResult(emptyMap(), "")
                remapped[published] = replacement
                used += replacement
            }
        }
        if (remapped.isEmpty()) return MacComposePortOverrideResult(emptyMap(), "")
        val yaml = buildString {
            append("services:\n")
            plan.services.filter { service -> service.ports.any { it.published in remapped } }.forEach { service ->
                append("  ").append(service.name).append(":\n    ports: !override\n")
                service.ports.forEach { binding ->
                    val published = binding.published?.let { remapped[it] ?: it }
                    val target = binding.target
                    if (published != null && target != null) {
                        append("      - \\\"$published:$target/${binding.protocol ?: "tcp"}\\\"\n")
                    } else {
                        append("      - \\\"${binding.raw}\\\"\n")
                    }
                }
            }
        }
        return MacComposePortOverrideResult(remapped, yaml)
    }

    private fun isAvailable(port: Int): Boolean = runCatching {
        ServerSocket(port).use { true }
    }.getOrDefault(false)
}
