package com.siftalpha.macos

import com.siftalpha.studio.container.ComposeProjectPlan
import com.siftalpha.studio.platform.CapabilityAvailability
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class MacComposeServiceState {
    RUNNING,
    STOPPED,
    UNKNOWN,
}

data class MacComposeServiceStatus(
    val name: String,
    val state: MacComposeServiceState,
)

data class MacComposeRuntimeStatus(
    val projectName: String,
    val services: List<MacComposeServiceStatus>,
) {
    val anyRunning: Boolean get() = services.any { it.state == MacComposeServiceState.RUNNING }
    val allRunning: Boolean get() = services.isNotEmpty() && services.all { it.state == MacComposeServiceState.RUNNING }
}

data class MacContainerOperationResult(
    val success: Boolean,
    val cancelled: Boolean = false,
    val detail: String? = null,
    val output: String = "",
)

interface MacComposeContainerProvider {
    val snapshot: MacContainerProviderSnapshot

    fun prepare(
        project: MacImportedProject,
        plan: ComposeProjectPlan,
        cancelled: () -> Boolean,
        log: (String) -> Unit,
    ): MacContainerOperationResult

    fun start(
        project: MacImportedProject,
        plan: ComposeProjectPlan,
    ): MacContainerOperationResult

    fun status(
        project: MacImportedProject,
        plan: ComposeProjectPlan,
    ): MacComposeRuntimeStatus

    fun logs(
        project: MacImportedProject,
        plan: ComposeProjectPlan,
        maxBytes: Int,
    ): String

    fun stop(
        project: MacImportedProject,
        plan: ComposeProjectPlan,
    ): MacContainerOperationResult

    fun publishedPorts(
        project: MacImportedProject,
        plan: ComposeProjectPlan,
    ): List<Int>

    fun projectName(projectId: String): String = MacComposeProjectIdentity.forProject(projectId)
}

object MacComposeProjectIdentity {
    fun forProject(projectId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(projectId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "siftalpha-" + digest.take(20)
    }
}

object MacComposeProviderSelector {
    fun select(
        providers: Collection<MacContainerProviderSnapshot>,
    ): MacComposeContainerProvider? {
        val preferred = listOf(MacContainerProviderKind.DOCKER, MacContainerProviderKind.PODMAN)
        val snapshot = preferred
            .asSequence()
            .mapNotNull { kind -> providers.firstOrNull { it.kind == kind } }
            .firstOrNull {
                it.availability == CapabilityAvailability.AVAILABLE &&
                    it.composeAvailable &&
                    !it.executablePath.isNullOrBlank()
            }
            ?: return null
        return MacCliComposeContainerProvider(snapshot)
    }
}

class MacCliComposeContainerProvider(
    override val snapshot: MacContainerProviderSnapshot,
) : MacComposeContainerProvider {
    init {
        require(snapshot.availability == CapabilityAvailability.AVAILABLE)
        require(snapshot.composeAvailable)
        require(!snapshot.executablePath.isNullOrBlank())
    }

    private val executable = snapshot.executablePath!!

    override fun prepare(
        project: MacImportedProject,
        plan: ComposeProjectPlan,
        cancelled: () -> Boolean,
        log: (String) -> Unit,
    ): MacContainerOperationResult {
        val manifest = plan.manifestPath
            ?: return MacContainerOperationResult(false, detail = "Compose manifest is missing")
        if (plan.services.isEmpty()) {
            return MacContainerOperationResult(false, detail = "Compose plan has no services")
        }

        val projectName = projectName(project.projectId)
        log("CONTAINER_PROVIDER=" + snapshot.kind.id)
        log("COMPOSE_PROJECT_NAME=" + projectName)
        log("COMPOSE_MANIFEST=" + manifest)

        if (plan.services.any { !it.image.isNullOrBlank() }) {
            val pull = runCompose(
                project = project,
                manifest = manifest,
                projectName = projectName,
                arguments = listOf("pull"),
                timeoutSeconds = 20 * 60,
                cancelled = cancelled,
            )
            appendOutput("pull", pull.output, log)
            if (!pull.success) return pull
            log("COMPOSE_PULL:PASS")
        }

        if (plan.services.any { !it.buildContext.isNullOrBlank() }) {
            val build = runCompose(
                project = project,
                manifest = manifest,
                projectName = projectName,
                arguments = listOf("build"),
                timeoutSeconds = 30 * 60,
                cancelled = cancelled,
            )
            appendOutput("build", build.output, log)
            if (!build.success) return build
            log("COMPOSE_BUILD:PASS")
        }

        return MacContainerOperationResult(
            success = true,
            output = "prepare complete",
        )
    }

    override fun start(
        project: MacImportedProject,
        plan: ComposeProjectPlan,
    ): MacContainerOperationResult {
        val manifest = plan.manifestPath
            ?: return MacContainerOperationResult(false, detail = "Compose manifest is missing")
        return runCompose(
            project = project,
            manifest = manifest,
            projectName = projectName(project.projectId),
            arguments = listOf("up", "-d"),
            timeoutSeconds = 5 * 60,
            cancelled = { false },
        )
    }

    override fun status(
        project: MacImportedProject,
        plan: ComposeProjectPlan,
    ): MacComposeRuntimeStatus {
        val manifest = plan.manifestPath
        val name = projectName(project.projectId)
        if (manifest == null) {
            return MacComposeRuntimeStatus(
                projectName = name,
                services = plan.services.map { MacComposeServiceStatus(it.name, MacComposeServiceState.UNKNOWN) },
            )
        }
        val running = runCompose(
            project = project,
            manifest = manifest,
            projectName = name,
            arguments = listOf("ps", "--services", "--status", "running"),
            timeoutSeconds = 15,
            cancelled = { false },
        )
        if (!running.success) {
            return MacComposeRuntimeStatus(
                projectName = name,
                services = plan.services.map { MacComposeServiceStatus(it.name, MacComposeServiceState.UNKNOWN) },
            )
        }
        val runningNames = running.output
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
        return MacComposeRuntimeStatus(
            projectName = name,
            services = plan.services.map { service ->
                MacComposeServiceStatus(
                    name = service.name,
                    state = if (service.name in runningNames) {
                        MacComposeServiceState.RUNNING
                    } else {
                        MacComposeServiceState.STOPPED
                    },
                )
            },
        )
    }

    override fun logs(
        project: MacImportedProject,
        plan: ComposeProjectPlan,
        maxBytes: Int,
    ): String {
        val manifest = plan.manifestPath ?: return ""
        val result = runCompose(
            project = project,
            manifest = manifest,
            projectName = projectName(project.projectId),
            arguments = listOf("logs", "--no-color", "--tail", "500"),
            timeoutSeconds = 20,
            cancelled = { false },
            maxOutputBytes = maxBytes,
        )
        return result.output.takeLast(maxBytes)
    }

    override fun stop(
        project: MacImportedProject,
        plan: ComposeProjectPlan,
    ): MacContainerOperationResult {
        val manifest = plan.manifestPath
            ?: return MacContainerOperationResult(false, detail = "Compose manifest is missing")
        return runCompose(
            project = project,
            manifest = manifest,
            projectName = projectName(project.projectId),
            arguments = listOf("down", "--remove-orphans"),
            timeoutSeconds = 5 * 60,
            cancelled = { false },
        )
    }

    override fun publishedPorts(
        project: MacImportedProject,
        plan: ComposeProjectPlan,
    ): List<Int> {
        val manifest = plan.manifestPath ?: return emptyList()
        val name = projectName(project.projectId)
        val ports = linkedSetOf<Int>()

        plan.services.forEach { service ->
            service.ports
                .filter { it.protocol == null || it.protocol.equals("tcp", ignoreCase = true) }
                .mapNotNull { it.target }
                .distinct()
                .forEach { target ->
                    val result = runCompose(
                        project = project,
                        manifest = manifest,
                        projectName = name,
                        arguments = listOf("port", service.name, target.toString()),
                        timeoutSeconds = 10,
                        cancelled = { false },
                    )
                    if (result.success) {
                        result.output.lineSequence()
                            .map(String::trim)
                            .mapNotNull { line ->
                                Regex("""(?:^|:)(\d{1,5})$""")
                                    .find(line)
                                    ?.groupValues
                                    ?.getOrNull(1)
                                    ?.toIntOrNull()
                            }
                            .filter { it in 1..65535 }
                            .forEach(ports::add)
                    }
                }
        }

        if (ports.isEmpty()) {
            plan.services.asSequence()
                .flatMap { it.ports.asSequence() }
                .mapNotNull { it.published }
                .filter { it in 1..65535 }
                .forEach(ports::add)
        }
        return ports.toList().sorted()
    }

    private fun runCompose(
        project: MacImportedProject,
        manifest: String,
        projectName: String,
        arguments: List<String>,
        timeoutSeconds: Long,
        cancelled: () -> Boolean,
        maxOutputBytes: Int = 512 * 1024,
    ): MacContainerOperationResult {
        val outputFile = File.createTempFile("siftalpha-compose-", ".log")
        try {
            val command = buildList {
                add(executable)
                add("compose")
                add("-p")
                add(projectName)
                add("-f")
                add(manifest)
                addAll(arguments)
            }
            val processBuilder = ProcessBuilder(command)
                .directory(File(project.canonicalRootPath))
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
            processBuilder.environment().putAll(
                MacManagedContainerToolchain.environmentForExecutable(executable),
            )
            val process = processBuilder.start()

            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            while (true) {
                if (cancelled()) {
                    process.destroy()
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                    }
                    return MacContainerOperationResult(
                        success = false,
                        cancelled = true,
                        detail = "container operation cancelled",
                        output = readOutput(outputFile, maxOutputBytes),
                    )
                }
                if (process.waitFor(200, TimeUnit.MILLISECONDS)) {
                    val output = readOutput(outputFile, maxOutputBytes)
                    val code = process.exitValue()
                    return MacContainerOperationResult(
                        success = code == 0,
                        detail = if (code == 0) null else "container command exit=" + code,
                        output = output,
                    )
                }
                if (System.nanoTime() >= deadline) {
                    process.destroy()
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                    }
                    return MacContainerOperationResult(
                        success = false,
                        detail = "container command timed out",
                        output = readOutput(outputFile, maxOutputBytes),
                    )
                }
            }
        } catch (error: Throwable) {
            return MacContainerOperationResult(
                success = false,
                detail = error.message ?: error.javaClass.simpleName,
                output = readOutput(outputFile, maxOutputBytes),
            )
        } finally {
            outputFile.delete()
        }
    }

    private fun readOutput(file: File, maxBytes: Int): String {
        if (!file.isFile) return ""
        val bytes = file.readBytes()
        val slice = if (bytes.size <= maxBytes) bytes else bytes.copyOfRange(bytes.size - maxBytes, bytes.size)
        return slice.toString(Charsets.UTF_8)
    }

    private fun appendOutput(
        operation: String,
        output: String,
        log: (String) -> Unit,
    ) {
        output.lineSequence()
            .filter(String::isNotBlank)
            .forEach { line -> log("compose " + operation + ": " + line.take(1000)) }
    }
}
