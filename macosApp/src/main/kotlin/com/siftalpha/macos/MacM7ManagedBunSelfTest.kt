package com.siftalpha.macos

import java.nio.file.Files

data class MacM7ManagedBunSelfTestResult(
    val passed: Boolean,
    val lines: List<String>,
)

object MacM7ManagedBunSelfTest {
    fun run(): MacM7ManagedBunSelfTestResult {
        val temp = Files.createTempDirectory("siftalpha-m7-managed-bun-").toFile()
        val lines = mutableListOf<String>()
        return try {
            val provider = MacManagedBunRuntimeProvider(
                root = temp.resolve("bun"),
                architecture = "x86_64",
            )
            val first = provider.ensure("1.3.14") { line -> lines += line }
            val second = provider.ensure("1.3.14") { line -> lines += line }
            val version = second.runtime?.detectedVersion()
            val passed =
                first.success &&
                    second.success &&
                    version == "1.3.14" &&
                    lines.any { it == "MANAGED_BUN_SHA256=PASS" } &&
                    lines.any { it == "MANAGED_BUN_PROVISION=PASS" } &&
                    lines.any { it == "MANAGED_BUN_CACHE=HIT version=1.3.14" }

            MacM7ManagedBunSelfTestResult(
                passed,
                listOf(
                    "first_install=" + first.success,
                    "second_cache=" + second.success,
                    "version=" + version.orEmpty(),
                    "sha256_pass=" + lines.any { it == "MANAGED_BUN_SHA256=PASS" },
                    "provision_pass=" + lines.any { it == "MANAGED_BUN_PROVISION=PASS" },
                    "cache_hit=" + lines.any { it == "MANAGED_BUN_CACHE=HIT version=1.3.14" },
                ) + lines.takeLast(12),
            )
        } finally {
            temp.deleteRecursively()
        }
    }
}
