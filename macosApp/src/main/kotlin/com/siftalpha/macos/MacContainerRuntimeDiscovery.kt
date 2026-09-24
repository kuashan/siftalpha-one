package com.siftalpha.macos

import com.siftalpha.studio.platform.CapabilityAvailability
import java.io.File
import java.util.concurrent.TimeUnit

enum class MacContainerProviderKind(val id: String) {
    DOCKER("docker"),
    PODMAN("podman"),
}

data class MacContainerProviderSnapshot(
    val kind: MacContainerProviderKind,
    val availability: CapabilityAvailability,
    val executablePath: String? = null,
    val version: String? = null,
    val composeAvailable: Boolean = false,
    val composeVersion: String? = null,
    val detail: String? = null,
)

class MacContainerRuntimeDiscovery(
    private val environment: Map<String, String> = System.getenv(),
    private val userHome: File = File(System.getProperty("user.home")),
) {
    fun discoverAll(): List<MacContainerProviderSnapshot> =
        MacContainerProviderKind.entries.map(::discover)

    fun discover(kind: MacContainerProviderKind): MacContainerProviderSnapshot {
        val executable = candidatePaths(kind)
            .distinct()
            .firstOrNull { File(it).let { file -> file.isFile && file.canExecute() } }
            ?: return MacContainerProviderSnapshot(
                kind = kind,
                availability = CapabilityAvailability.UNAVAILABLE,
                detail = "no executable candidate found",
            )

        val versionProbe = probe(executable, listOf("--version"))
        if (!versionProbe.success) {
            return MacContainerProviderSnapshot(
                kind = kind,
                availability = CapabilityAvailability.UNKNOWN,
                executablePath = executable,
                detail = versionProbe.detail ?: "container CLI version probe failed",
            )
        }

        val runtimeProbe = when (kind) {
            MacContainerProviderKind.DOCKER ->
                probe(executable, listOf("info", "--format", "{{.ServerVersion}}"))
            MacContainerProviderKind.PODMAN ->
                probe(executable, listOf("info", "--format", "{{.Version.Version}}"))
        }
        val composeProbe = probeCompose(executable)

        return MacContainerProviderSnapshot(
            kind = kind,
            availability = if (runtimeProbe.success) {
                CapabilityAvailability.AVAILABLE
            } else {
                CapabilityAvailability.UNKNOWN
            },
            executablePath = executable,
            version = versionProbe.output.lineSequence().firstOrNull()?.trim()?.take(240),
            composeAvailable = composeProbe.success,
            composeVersion = composeProbe.output.lineSequence().firstOrNull()?.trim()?.take(240),
            detail = if (runtimeProbe.success) {
                null
            } else {
                runtimeProbe.detail ?: runtimeProbe.output.take(240).ifBlank {
                    "container runtime is not reachable"
                }
            },
        )
    }

    private fun probeCompose(executable: String): ProbeResult {
        val short = probe(executable, listOf("compose", "version", "--short"))
        if (short.success) return short
        return probe(executable, listOf("compose", "version"))
    }

    private fun candidatePaths(kind: MacContainerProviderKind): List<String> {
        val name = kind.id
        val directories = linkedSetOf<String>().apply {
            environment["PATH"]
                ?.split(File.pathSeparatorChar)
                ?.filter(String::isNotBlank)
                ?.forEach(::add)
            add("/usr/local/bin")
            add("/opt/homebrew/bin")
            add("/opt/local/bin")
            add(File(userHome, ".local/bin").absolutePath)
            MacManagedContainerToolchain.managedBin(userHome)?.absolutePath?.let(::add)
            if (kind == MacContainerProviderKind.DOCKER) {
                add("/Applications/Docker.app/Contents/Resources/bin")
            } else {
                add("/opt/podman/bin")
            }
        }
        return directories.map { File(it, name).absolutePath }
    }

    private fun probe(executable: String, arguments: List<String>): ProbeResult =
        runCatching {
            val process = ProcessBuilder(listOf(executable) + arguments)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(4, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ProbeResult(false, "", "probe timed out")
            }
            val output = process.inputStream.bufferedReader().use { it.readText().take(4096) }.trim()
            ProbeResult(
                success = process.exitValue() == 0,
                output = output,
                detail = if (process.exitValue() == 0) null else "probe exit=" + process.exitValue(),
            )
        }.getOrElse { error ->
            ProbeResult(false, "", error.message ?: error.javaClass.simpleName)
        }

    private data class ProbeResult(
        val success: Boolean,
        val output: String,
        val detail: String?,
    )

    companion object {
        fun capabilityAvailability(
            providers: Collection<MacContainerProviderSnapshot>,
        ): CapabilityAvailability = when {
            providers.any { it.availability == CapabilityAvailability.AVAILABLE } ->
                CapabilityAvailability.AVAILABLE
            providers.any { it.availability == CapabilityAvailability.UNKNOWN } ->
                CapabilityAvailability.UNKNOWN
            else -> CapabilityAvailability.UNAVAILABLE
        }
    }
}
