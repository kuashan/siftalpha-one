package com.siftalpha.macos

import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

data class MacManagedBunRuntime(
    val version: String,
    val root: File,
) {
    val executable: File
        get() = File(root, "bin/bun")

    val available: Boolean
        get() = executable.isFile && executable.canExecute()

    fun detectedVersion(): String? {
        if (!available) return null
        return runCatching {
            val process = ProcessBuilder(executable.absolutePath, "--version")
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            MacManagedBunArtifactPolicy.parseInstalledVersion(output)
                ?.takeIf { process.exitValue() == 0 }
        }.getOrNull()
    }
}

data class MacManagedBunProvisionResult(
    val success: Boolean,
    val runtime: MacManagedBunRuntime? = null,
    val detail: String? = null,
)

data class MacManagedBunArtifact(
    val assetName: String,
    val assetUrl: String,
    val shasumsUrl: String,
)

internal object MacManagedBunArtifactPolicy {
    private val exactVersion = Regex("""^[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?$""")
    private val packageManager = Regex(
        """["']packageManager["']\s*:\s*["']bun@([0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val installedVersion = Regex("""(?:^|\s|v)([0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?)(?:\s|$)""")

    fun packageManagerVersion(packageJsonText: String?): String? =
        packageJsonText
            ?.let { packageManager.find(it)?.groupValues?.getOrNull(1) }
            ?.takeIf(::isSupportedVersion)

    fun parseInstalledVersion(output: String?): String? =
        output
            ?.trim()
            ?.let { installedVersion.find(it)?.groupValues?.getOrNull(1) }
            ?.takeIf(::isSupportedVersion)

    fun isSupportedVersion(version: String): Boolean = exactVersion.matches(version)

    fun artifact(version: String, architecture: String): MacManagedBunArtifact? {
        if (!isSupportedVersion(version)) return null
        val asset = when (architecture.lowercase()) {
            "x86_64", "amd64", "x64" -> "bun-darwin-x64.zip"
            "aarch64", "arm64" -> "bun-darwin-aarch64.zip"
            else -> return null
        }
        val base = "https://github.com/oven-sh/bun/releases/download/bun-v$version/"
        return MacManagedBunArtifact(
            assetName = asset,
            assetUrl = base + asset,
            shasumsUrl = base + "SHASUMS256.txt",
        )
    }

    fun expectedSha256(shasumsText: String, assetName: String): String? {
        return shasumsText.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { line ->
                val match = Regex("""^([0-9a-fA-F]{64})\s+\*?(.+)$""").matchEntire(line)
                    ?: return@mapNotNull null
                val name = match.groupValues[2].trim()
                if (name == assetName) match.groupValues[1].lowercase() else null
            }
            .firstOrNull()
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

class MacManagedBunRuntimeProvider(
    private val root: File = File(
        MacProjectEnvironmentManager.defaultDataRoot(),
        "managed-runtimes/bun",
    ),
    private val architecture: String = System.getProperty("os.arch"),
) {
    fun locate(version: String): MacManagedBunRuntime? {
        if (!MacManagedBunArtifactPolicy.isSupportedVersion(version)) return null
        val runtime = MacManagedBunRuntime(version, File(root, version))
        return runtime.takeIf {
            it.available && it.detectedVersion() == version
        }
    }

    @Synchronized
    fun ensure(
        version: String,
        log: (String) -> Unit = {},
    ): MacManagedBunProvisionResult {
        locate(version)?.let { runtime ->
            log("MANAGED_BUN_CACHE=HIT version=$version")
            return MacManagedBunProvisionResult(true, runtime)
        }

        val artifact = MacManagedBunArtifactPolicy.artifact(version, architecture)
            ?: return MacManagedBunProvisionResult(
                false,
                detail = "unsupported Bun version or architecture: $version / $architecture",
            )

        root.mkdirs()
        val staging = runCatching {
            Files.createTempDirectory(root.toPath(), ".bun-install-").toFile()
        }.getOrElse { error ->
            return MacManagedBunProvisionResult(
                false,
                detail = "failed to create managed Bun staging directory: " +
                    (error.message ?: error.javaClass.simpleName),
            )
        }

        return try {
            val proxy = MacSystemProxyDiscovery.discover()
            val environment = MacManagedContainerProxy.applyToProcessEnvironment(
                System.getenv(),
                proxy,
            )
            log("MANAGED_BUN_PROVISION=STARTED version=$version")
            log("MANAGED_BUN_PROXY_POLICY=" + MacManagedContainerProxy.strategy(proxy))
            log("MANAGED_BUN_ASSET=" + artifact.assetName)

            val shasums = File(staging, "SHASUMS256.txt")
            val archive = File(staging, artifact.assetName)
            download(artifact.shasumsUrl, shasums, environment)
                ?.let { detail ->
                    return MacManagedBunProvisionResult(false, detail = detail)
                }
            download(artifact.assetUrl, archive, environment)
                ?.let { detail ->
                    return MacManagedBunProvisionResult(false, detail = detail)
                }

            val shasumsText = runCatching { shasums.readText(Charsets.UTF_8) }.getOrElse { error ->
                return MacManagedBunProvisionResult(
                    false,
                    detail = "failed to read Bun checksums: " +
                        (error.message ?: error.javaClass.simpleName),
                )
            }
            val expectedSha = MacManagedBunArtifactPolicy.expectedSha256(
                shasumsText,
                artifact.assetName,
            ) ?: return MacManagedBunProvisionResult(
                false,
                detail = "official Bun checksum is missing for " + artifact.assetName,
            )
            val actualSha = MacManagedBunArtifactPolicy.sha256(archive)
            if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                return MacManagedBunProvisionResult(
                    false,
                    detail = "Bun checksum mismatch for " + artifact.assetName,
                )
            }
            log("MANAGED_BUN_SHA256=PASS")

            val extracted = File(staging, "extracted").apply { mkdirs() }
            runCommand(
                listOf("/usr/bin/ditto", "-x", "-k", archive.absolutePath, extracted.absolutePath),
                environment,
                timeoutSeconds = 60,
            )?.let { detail ->
                return MacManagedBunProvisionResult(false, detail = detail)
            }

            val extractedRoot = extracted.canonicalFile
            val bun = extracted.walkTopDown()
                .maxDepth(3)
                .firstOrNull { candidate ->
                    candidate.isFile &&
                        candidate.name == "bun" &&
                        runCatching {
                            candidate.canonicalFile.toPath().startsWith(extractedRoot.toPath())
                        }.getOrDefault(false)
                }
                ?: return MacManagedBunProvisionResult(
                    false,
                    detail = "Bun archive did not contain an executable named bun",
                )

            val installStaging = File(root, ".$version-install")
            installStaging.deleteRecursively()
            val binDir = File(installStaging, "bin").apply { mkdirs() }
            val stagedExecutable = File(binDir, "bun")
            Files.copy(
                bun.toPath(),
                stagedExecutable.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            check(stagedExecutable.setExecutable(true, false)) {
                "failed to make managed Bun executable"
            }

            val stagedRuntime = MacManagedBunRuntime(version, installStaging)
            if (stagedRuntime.detectedVersion() != version) {
                installStaging.deleteRecursively()
                return MacManagedBunProvisionResult(
                    false,
                    detail = "managed Bun version verification failed; expected $version",
                )
            }

            val finalRoot = File(root, version)
            finalRoot.deleteRecursively()
            runCatching {
                Files.move(
                    installStaging.toPath(),
                    finalRoot.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.recoverCatching {
                Files.move(
                    installStaging.toPath(),
                    finalRoot.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse { error ->
                installStaging.deleteRecursively()
                return MacManagedBunProvisionResult(
                    false,
                    detail = "failed to activate managed Bun: " +
                        (error.message ?: error.javaClass.simpleName),
                )
            }

            val runtime = locate(version)
                ?: return MacManagedBunProvisionResult(
                    false,
                    detail = "managed Bun was installed but final verification failed",
                )
            log("MANAGED_BUN_VERSION=" + runtime.detectedVersion().orEmpty())
            log("MANAGED_BUN_PROVISION=PASS")
            MacManagedBunProvisionResult(true, runtime)
        } catch (error: Throwable) {
            MacManagedBunProvisionResult(
                false,
                detail = error.message ?: error.javaClass.simpleName,
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun download(
        url: String,
        target: File,
        environment: Map<String, String>,
    ): String? =
        runCommand(
            listOf(
                "/usr/bin/curl",
                "--fail",
                "--location",
                "--retry", "3",
                "--retry-delay", "2",
                "--connect-timeout", "20",
                "--max-time", "300",
                "--silent",
                "--show-error",
                "--output", target.absolutePath,
                url,
            ),
            environment,
            timeoutSeconds = 330,
        )

    private fun runCommand(
        command: List<String>,
        environment: Map<String, String>,
        timeoutSeconds: Long,
    ): String? =
        runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .apply { environment().putAll(environment) }
                .start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
                return "managed Bun command timed out: " + command.firstOrNull().orEmpty()
            }
            val output = process.inputStream.bufferedReader().use {
                it.readText().takeLast(16 * 1024)
            }.trim()
            if (process.exitValue() == 0) null
            else "managed Bun command failed exit=" + process.exitValue() +
                if (output.isBlank()) "" else ": " + output
        }.getOrElse { error ->
            error.message ?: error.javaClass.simpleName
        }
}
