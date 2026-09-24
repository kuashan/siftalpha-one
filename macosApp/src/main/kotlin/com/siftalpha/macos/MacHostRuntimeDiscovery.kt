package com.siftalpha.macos

import java.io.File
import java.util.concurrent.TimeUnit

enum class MacHostToolKind(val id: String) {
    PYTHON("python"),
    NODE_JS("nodejs"),
    BUN("bun"),
    GIT("git"),
    SHELL("shell"),
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
 * Passive macOS Host Runtime Discovery（被动式苹果主机运行时发现）.
 *
 * Discovery must never trigger installation UI. Catalina exposes some Apple developer-tool shims
 * such as /usr/bin/python3 and /usr/bin/git even when Command Line Tools（命令行开发者工具） are not
 * installed. Executing those shims opens the system installer, so they are skipped unless
 * xcode-select proves developer tools are already configured.
 *
 * Python 2 is intentionally not accepted as the SiftAlpha host Python runtime. A candidate named
 * "python" is still checked because user-managed installations may point it at Python 3.
 */
class MacHostRuntimeDiscovery(
    private val environment: Map<String, String> = System.getenv(),
    private val userHome: File = File(System.getProperty("user.home")),
    private val developerToolsAvailable: () -> Boolean = { detectAppleDeveloperTools() },
) {
    fun discoverAll(): List<MacHostToolSnapshot> =
        MacHostToolKind.entries.map(::discover)

    fun discover(kind: MacHostToolKind): MacHostToolSnapshot {
        val candidates = candidatePaths(kind).distinct()
        val commandLineToolsAvailable by lazy(developerToolsAvailable)
        var firstFailure: String? = null

        for (candidate in candidates) {
            val file = File(candidate)
            if (!file.isFile || !file.canExecute()) continue

            if (
                MacHostDiscoveryPolicy.shouldSkipDeveloperToolStub(
                    kind = kind,
                    path = file.absolutePath,
                    developerToolsAvailable = commandLineToolsAvailable,
                )
            ) {
                if (firstFailure == null) {
                    firstFailure = "Apple Command Line Tools are not installed; skipped developer-tool shim"
                }
                continue
            }

            val probe = probeVersion(file.absolutePath, versionArguments(kind))
            if (
                probe.exitCode == 0 &&
                MacHostDiscoveryPolicy.acceptsVersion(kind, probe.output)
            ) {
                return MacHostToolSnapshot(
                    kind = kind,
                    availability = MacHostToolAvailability.AVAILABLE,
                    executablePath = file.absolutePath,
                    version = probe.output.lineSequence().first().trim().take(240),
                )
            }

            if (firstFailure == null) {
                firstFailure = when {
                    probe.exitCode == 0 && kind == MacHostToolKind.PYTHON ->
                        "Python 3 is required; legacy Python is not accepted"
                    else -> probe.detail ?: ("version probe failed for " + file.absolutePath)
                }
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
            MacHostToolKind.SHELL -> listOf("zsh", "bash", "sh")
        }

        val commonDirs = linkedSetOf<String>().apply {
            environment["PATH"]
                ?.split(File.pathSeparatorChar)
                ?.filter(String::isNotBlank)
                ?.forEach(::add)
            add("/usr/local/bin")
            add("/opt/homebrew/bin")
            add("/opt/local/bin")
            add(File(userHome, ".volta/bin").absolutePath)
            add(File(userHome, ".asdf/shims").absolutePath)
            add(File(userHome, ".bun/bin").absolutePath)
            add("/usr/bin")
            add("/bin")
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
        MacHostToolKind.SHELL,
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

    companion object {
        private fun detectAppleDeveloperTools(): Boolean =
            runCatching {
                val process = ProcessBuilder("/usr/bin/xcode-select", "-p")
                    .redirectErrorStream(true)
                    .start()
                val finished = process.waitFor(2, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    false
                } else {
                    process.exitValue() == 0
                }
            }.getOrDefault(false)
    }
}

/** Pure policy helpers kept testable without invoking macOS installation shims. */
internal object MacHostDiscoveryPolicy {
    fun shouldSkipDeveloperToolStub(
        kind: MacHostToolKind,
        path: String,
        developerToolsAvailable: Boolean,
    ): Boolean {
        if (developerToolsAvailable) return false
        return when (kind) {
            MacHostToolKind.PYTHON -> path == "/usr/bin/python3"
            MacHostToolKind.GIT -> path == "/usr/bin/git"
            else -> false
        }
    }

    fun acceptsVersion(
        kind: MacHostToolKind,
        output: String,
    ): Boolean {
        val firstLine = output.lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.isBlank()) return false
        return when (kind) {
            MacHostToolKind.PYTHON -> Regex("""^Python\s+3(?:\.|\s|$)""").containsMatchIn(firstLine)
            else -> true
        }
    }
}
