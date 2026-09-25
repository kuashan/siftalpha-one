package com.siftalpha.macos

import com.siftalpha.core.storage.PlatformStateStorage
import com.siftalpha.core.storage.ProjectConfigurationPolicy
import com.siftalpha.core.storage.ProjectSecretStorage
import com.siftalpha.core.storage.StateStorageMutation
import com.siftalpha.core.storage.StoredStateValue
import com.siftalpha.core.storage.readText
import com.siftalpha.studio.platform.CapabilityAvailability
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * macOS Keychain adapter（macOS 钥匙串适配器）.
 *
 * Secret values are stored as generic-password items in the user's login Keychain. SiftAlpha keeps
 * only the configured variable names in PlatformStateStorage; secret values are never written to
 * SiftAlpha's files or logs.
 */
class MacKeychainProjectSecretStore(
    private val stateStorage: PlatformStateStorage,
    private val securityExecutable: File = File("/usr/bin/security"),
) : ProjectSecretStorage {

    override fun configuredKeys(projectId: String): Set<String> =
        stateStorage.readText(indexKey(projectId))
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toCollection(linkedSetOf())

    override fun read(projectId: String, name: String): String? {
        val normalized = normalizeName(name)
        if (!securityExecutable.isFile || !securityExecutable.canExecute()) return null
        val result = runSecurity(
            listOf(
                "find-generic-password",
                "-s", service(projectId),
                "-a", normalized,
                "-w",
            ),
        )
        return result.output.trimEnd('\r', '\n').takeIf { result.exitCode == 0 }
    }

    override fun write(projectId: String, name: String, value: String) {
        require(projectId.isNotBlank()) { "projectId must not be blank" }
        val normalized = normalizeName(name)
        require(value.isNotBlank()) { "$normalized value must not be blank" }
        check(securityExecutable.isFile && securityExecutable.canExecute()) {
            "macOS security tool is unavailable"
        }

        // Putting -w last asks security(1) to read the password interactively. We feed stdin so the
        // secret does not become a command-line argument.
        val result = runSecurity(
            listOf(
                "add-generic-password",
                "-U",
                "-s", service(projectId),
                "-a", normalized,
                "-w",
            ),
            stdin = value + "\n",
        )
        check(result.exitCode == 0) {
            "Keychain write failed (exit=" + result.exitCode + ")"
        }
        writeIndex(projectId, configuredKeys(projectId) + normalized)
    }

    override fun remove(projectId: String, name: String) {
        val normalized = normalizeName(name)
        if (securityExecutable.isFile && securityExecutable.canExecute()) {
            runSecurity(
                listOf(
                    "delete-generic-password",
                    "-s", service(projectId),
                    "-a", normalized,
                ),
            )
        }
        writeIndex(projectId, configuredKeys(projectId) - normalized)
    }

    fun environment(projectId: String): Map<String, String> =
        configuredKeys(projectId)
            .sorted()
            .mapNotNull { name -> read(projectId, name)?.let { name to it } }
            .toMap(linkedMapOf())

    fun redact(projectId: String, text: String): String {
        if (text.isBlank()) return text
        return environment(projectId).values
            .filter(String::isNotBlank)
            .distinct()
            .sortedByDescending(String::length)
            .fold(text) { safe, secret -> safe.replace(secret, "[REDACTED]") }
    }

    private fun writeIndex(projectId: String, names: Collection<String>) {
        val normalized = ProjectConfigurationPolicy.normalizedEnvironmentNames(names)
        if (normalized.isEmpty()) {
            stateStorage.mutate(StateStorageMutation(removals = setOf(indexKey(projectId))))
        } else {
            stateStorage.mutate(
                StateStorageMutation(
                    writes = mapOf(
                        indexKey(projectId) to StoredStateValue.Text(normalized.joinToString("\n")),
                    ),
                ),
            )
        }
    }

    private fun normalizeName(name: String): String =
        ProjectConfigurationPolicy.normalizedEnvironmentNames(listOf(name)).single()

    private fun service(projectId: String): String {
        require(projectId.isNotBlank()) { "projectId must not be blank" }
        return "com.siftalpha.project." + digest(projectId).take(32)
    }

    private fun indexKey(projectId: String): String =
        "macos.keychain.keys:" + digest(projectId)

    private fun runSecurity(args: List<String>, stdin: String? = null): CommandResult {
        val process = ProcessBuilder(listOf(securityExecutable.absolutePath) + args)
            .redirectErrorStream(true)
            .start()
        if (stdin != null) {
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(stdin)
                writer.flush()
            }
        } else {
            process.outputStream.close()
        }
        val completed = process.waitFor(15, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return CommandResult(-1, "")
        }
        val output = process.inputStream.bufferedReader().use { it.readText().take(64 * 1024) }
        return CommandResult(process.exitValue(), output)
    }

    private data class CommandResult(
        val exitCode: Int,
        val output: String,
    )

    companion object {
        fun capabilityAvailability(
            securityExecutable: File = File("/usr/bin/security"),
        ): CapabilityAvailability =
            if (securityExecutable.isFile && securityExecutable.canExecute()) {
                CapabilityAvailability.AVAILABLE
            } else {
                CapabilityAvailability.UNAVAILABLE
            }

        private fun digest(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
