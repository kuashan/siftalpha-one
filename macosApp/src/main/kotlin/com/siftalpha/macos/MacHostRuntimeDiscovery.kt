package com.siftalpha.macos

import java.io.File
import java.util.concurrent.TimeUnit

enum class MacHostToolKind(val id: String) {
    PYTHON("python"),
    NODE_JS("nodejs"),
    BUN("bun"),
    GIT("git"),
}

enum class MacHostToolAvailability {
    AVAILABLE,
    UNAVAILABLE,
}

data class MacHostToolSnapshot(
    val kind: MacHostToolKind,
    val availability: MacHostToolAvailability,
    val executablePath: String? = null,
    val version: String? = null,
    val detail: String? = null,
)

/**
 * macOS Host Runtime Discovery（苹果主机运行时发现）.
 *
 * Finder-launched applications often inherit a smaller PATH than an interactive shell, so discovery
 * combines PATH with common Homebrew / MacPorts / Volta / asdf / Bun locations. Availability is
 * proven by a bounded version probe; executable-file existence alone is not treated as AVAILABLE.
 */
class MacHostRuntimeDiscovery(
    private val environment: Map<String, String> = System.getenv(),
    private val userHome: File = File(System.getProperty("user.home")),
) {
    fun discoverAll(): List<MacHostToolSnapshot> =
        MacHostToolKind.entries.map(::discover)

    fun discover(kind: MacHostToolKind): MacHostToolSnapshot {
        val candidates = candidatePaths(kind).distinct()
        var firstFailure: String? = null
        for (candidate in candidates) {
            val file = File(candidate)
            if (!file.isFile || !file.canExecute()) continue
            val probe = probeVersion(file.absolutePath, versionArguments(kind))
            if (probe.exitCode == 0 && probe.output.isNotBlank()) {
                return MacHostToolSnapshot(
                    kind = kind,
                    availability = MacHostToolAvailability.AVAILABLE,
                    executablePath = file.absolutePath,
                    version = probe.output.lineSequence().first().trim().take(240),
                )
            }
            if (firstFailure == null) {
                firstFailure = probe.detail ?: ("version probe failed for " + file.absolutePath)
            }
        }
        return MacHostToolSnapshot(
            kind = kind,
            availability = MacHostToolAvailability.UNAVAILABLE,
            detail = firstFailure ?: "no executable candidate found",
        )
    }

    private fun candidatePaths(kind: MacHostToolKind): List<String> {
        val names = when (kind) {
            MacHostToolKind.PYTHON -> listOf("python3", "python")
            MacHostToolKind.NODE_JS -> listOf("node")
            MacHostToolKind.BUN -> listOf("bun")
            MacHostToolKind.GIT -> listOf("git")
        }

        val commonDirs = linkedSetOf<String>().apply {
            environment["PATH"]
                ?.split(File.pathSeparatorChar)
                ?.filter(String::isNotBlank)
                ?.forEach(::add)
            add("/usr/bin")
            add("/bin")
            add("/usr/local/bin")
            add("/opt/homebrew/bin")
            add("/opt/local/bin")
            add(File(userHome, ".volta/bin").absolutePath)
            add(File(userHome, ".asdf/shims").absolutePath)
            add(File(userHome, ".bun/bin").absolutePath)
        }

        val paths = commonDirs.flatMap { dir ->
            names.map { name -> File(dir, name).absolutePath }
        }.toMutableList()

        if (kind == MacHostToolKind.NODE_JS) {
            val nvmVersions = File(userHome, ".nvm/versions/node")
            nvmVersions.listFiles()
                ?.sortedByDescending { it.name }
                ?.forEach { versionDir ->
                    paths += File(versionDir, "bin/node").absolutePath
                }
        }
        return paths
    }

    private fun versionArguments(kind: MacHostToolKind): List<String> = when (kind) {
        MacHostToolKind.PYTHON,
        MacHostToolKind.NODE_JS,
        MacHostToolKind.BUN,
        MacHostToolKind.GIT,
        -> listOf("--version")
    }

    private fun probeVersion(
        executable: String,
        arguments: List<String>,
    ): ProbeResult {
        return runCatching {
            val process = ProcessBuilder(listOf(executable) + arguments)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ProbeResult(
                    exitCode = null,
                    output = "",
                    detail = "version probe timed out",
                )
            }
            ProbeResult(
                exitCode = process.exitValue(),
                output = process.inputStream.bufferedReader().use { it.readText().take(4096) }.trim(),
                detail = if (process.exitValue() == 0) {
                    null
                } else {
                    "version probe exit=" + process.exitValue()
                },
            )
        }.getOrElse { error ->
            ProbeResult(
                exitCode = null,
                output = "",
                detail = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private data class ProbeResult(
        val exitCode: Int?,
        val output: String,
        val detail: String?,
    )
}
