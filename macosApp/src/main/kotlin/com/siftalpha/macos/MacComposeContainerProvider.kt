package com.siftalpha.macos

import com.siftalpha.studio.container.ComposeProjectPlan
import com.siftalpha.studio.platform.CapabilityAvailability
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
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

internal object MacComposeFailureDiagnostics {
    private const val MAX_DETAIL_CHARS = 8 * 1024

    fun detail(
        operation: String,
        exitCode: Int,
        output: String,
    ): String {
        val cleaned = output
            .lineSequence()
            .map(String::trimEnd)
            .filter(String::isNotBlank)
            .joinToString("\n")
            .takeLast(MAX_DETAIL_CHARS)
        return buildString {
            append("docker compose ")
            append(operation)
            append(" failed exit=")
            append(exitCode)
            if (cleaned.isNotBlank()) {
                append("\n")
                append(cleaned)
            }
        }
    }
}

internal object MacComposePreparePolicy {
    fun operations(plan: ComposeProjectPlan): List<List<String>> = buildList {
        val hasPullOnlyService = plan.services.any { service ->
            !service.image.isNullOrBlank() && service.buildContext.isNullOrBlank()
        }
        if (hasPullOnlyService) add(listOf("pull", "--ignore-buildable"))
        if (plan.services.any { !it.buildContext.isNullOrBlank() }) add(listOf("build"))
    }
}

enum class MacComposeResourceFailure {
    MEMORY_EXHAUSTED,
    OTHER,
}

object MacComposeResourceFailureClassifier {
    fun classify(text: String?): MacComposeResourceFailure {
        val normalized = text.orEmpty().lowercase()
        val explicitMemoryEvidence = normalized.contains("cannot allocate memory") ||
            normalized.contains("resourceexhausted") ||
            normalized.contains("out of memory") ||
            normalized.contains("oom")
        val killedWithBuildContext =
            (normalized.contains("sigkill") || normalized.contains("killed")) &&
                (normalized.contains("build") || normalized.contains("memory") || normalized.contains("oom"))
        return if (explicitMemoryEvidence || killedWithBuildContext) {
            MacComposeResourceFailure.MEMORY_EXHAUSTED
        } else {
            MacComposeResourceFailure.OTHER
        }
    }
}

interface MacComposeContainerProvider {
    val snapshot: MacContainerProviderSnapshot

    fun configureProjectEnvironment(
        projectId: String,
        environment: Map<String, String>,
    ) = Unit

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

    fun clean(
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

internal object MacComposeProcessEnvironment {
    fun build(
        executable: String,
        projectEnvironment: Map<String, String>,
        userHome: File = File(System.getProperty("user.home")),
        base: Map<String, String> = System.getenv(),
        systemProxy: MacSystemProxySettings? = null,
    ): Map<String, String> {
        val managed = MacManagedContainerToolchain.isManagedExecutable(executable, userHome)
        val runtimeEnvironment = MacManagedContainerToolchain.environmentForExecutable(
            executable = executable,
            userHome = userHome,
            base = base,
        )
        val merged = buildMap {
            putAll(runtimeEnvironment)
            putAll(projectEnvironment)
        }
        if (!managed) return merged

        return MacManagedContainerProxy.applyToProcessEnvironment(
            merged,
            systemProxy ?: MacSystemProxyDiscovery.discover(),
        )
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
    private val projectEnvironment = ConcurrentHashMap<String, Map<String, String>>()

    override fun configureProjectEnvironment(
        projectId: String,
        environment: Map<String, String>,
    ) {
        if (environment.isEmpty()) {
            projectEnvironment.remove(projectId)
        } else {
            projectEnvironment[projectId] = environment.toMap()
        }
    }

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

        MacComposePreparePolicy.operations(plan).forEach { arguments ->
            val operation = arguments.first()
            val result = runCompose(
                project = project,
                manifest = manifest,
                projectName = projectName,
                arguments = arguments,
                timeoutSeconds = if (operation == "build") 30 * 60 else 20 * 60,
                cancelled = cancelled,
                onLine = { line -> log("compose " + operation + ": " + line.take(1000)) },
            )
            if (!result.success) return result
            log("COMPOSE_" + operation.uppercase() + ":PASS")
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
        val first = runCompose(
            project,
            manifest,
            projectName(project.projectId),
            listOf("up", "-d"),
            5 * 60,
            { false },
        )
        if (first.success || !first.output.contains("port is already allocated", ignoreCase = true)) return first
        val override = MacComposePortOverride.resolve(plan, ::isPortAvailable)
        if (override.yaml.isBlank()) {
            return first.copy(
                detail = first.detail + "\nNo safe replacement port is available; the project was left unchanged.",
            )
        }
        val overrideFile = File.createTempFile("siftalpha-compose-port-", ".yaml")
            .apply { writeText(override.yaml) }
        return try {
            runCompose(
                project,
                manifest,
                projectName(project.projectId),
                listOf("up", "-d"),
                5 * 60,
                { false },
                additionalManifest = overrideFile,
            ).let { remapped ->
                if (remapped.success) {
                    remapped.copy(
                        detail = "Port conflict resolved automatically: " +
                            override.remappedPorts.entries.joinToString(", ") { (requested, actual) ->
                                "$requested->$actual"
                            },
                    )
                } else {
                    remapped
                }
            }
        } finally {
            overrideFile.delete()
        }
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

    override fun clean(
        project: MacImportedProject,
        plan: ComposeProjectPlan,
    ): MacContainerOperationResult {
        val manifest = plan.manifestPath
            ?: return MacContainerOperationResult(false, detail = "Compose manifest is missing")
        return runCompose(
            project = project,
            manifest = manifest,
            projectName = projectName(project.projectId),
            arguments = listOf("down", "--remove-orphans", "--volumes"),
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
        additionalManifest: File? = null,
        onLine: (String) -> Unit = {},
    ): MacContainerOperationResult {
        val command = buildList {
            add(executable)
            add("compose")
            add("-p")
            add(projectName)
            add("-f")
            add(manifest)
            additionalManifest?.let { file ->
                add("-f")
                add(file.absolutePath)
            }
            addAll(arguments)
        }
        val output = StringBuilder()
        val outputLock = Any()
        var reader: Thread? = null
        fun snapshot(): String = synchronized(outputLock) { output.toString() }
        fun stopProcess(process: Process) {
            process.destroy()
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        }
        fun awaitReader() {
            reader?.join(2_000)
        }
        try {
            val process = ProcessBuilder(command)
                .directory(File(project.canonicalRootPath))
                .redirectErrorStream(true)
                .apply {
                    environment().putAll(
                        MacComposeProcessEnvironment.build(
                            executable = executable,
                            projectEnvironment = projectEnvironment[project.projectId].orEmpty(),
                        ),
                    )
                }
                .start()

            reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { rawLine ->
                            val safeLine = redact(project.projectId, rawLine)
                            synchronized(outputLock) {
                                output.append(safeLine).append('\n')
                                trimOutput(output, maxOutputBytes)
                            }
                            runCatching { onLine(safeLine) }
                        }
                    }
                }
            }.apply {
                isDaemon = true
                name = "siftalpha-compose-output"
                start()
            }

            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            while (true) {
                if (cancelled()) {
                    stopProcess(process)
                    awaitReader()
                    return MacContainerOperationResult(
                        success = false,
                        cancelled = true,
                        detail = "container operation cancelled",
                        output = redact(project.projectId, snapshot()),
                    )
                }
                if (process.waitFor(200, TimeUnit.MILLISECONDS)) {
                    awaitReader()
                    val safeOutput = redact(project.projectId, snapshot())
                    val code = process.exitValue()
                    return MacContainerOperationResult(
                        success = code == 0,
                        detail = if (code == 0) {
                            null
                        } else {
                            MacComposeFailureDiagnostics.detail(
                                operation = arguments.joinToString(" "),
                                exitCode = code,
                                output = safeOutput,
                            )
                        },
                        output = safeOutput,
                    )
                }
                if (System.nanoTime() >= deadline) {
                    stopProcess(process)
                    awaitReader()
                    val safeOutput = redact(project.projectId, snapshot())
                    return MacContainerOperationResult(
                        success = false,
                        detail = buildString {
                            append("docker compose ")
                            append(arguments.joinToString(" "))
                            append(" timed out")
                            if (safeOutput.isNotBlank()) {
                                append("\n")
                                append(safeOutput.takeLast(8 * 1024))
                            }
                        },
                        output = safeOutput,
                    )
                }
            }
        } catch (error: Throwable) {
            awaitReader()
            return MacContainerOperationResult(
                success = false,
                detail = error.message ?: error.javaClass.simpleName,
                output = redact(project.projectId, snapshot()),
            )
        }
    }

    private fun redact(projectId: String, text: String): String =
        projectEnvironment[projectId].orEmpty().values
            .filter(String::isNotBlank)
            .distinct()
            .sortedByDescending(String::length)
            .fold(text) { safe, secret -> safe.replace(secret, "[REDACTED]") }

    private fun isPortAvailable(port: Int): Boolean = runCatching {
        java.net.ServerSocket(port).use { true }
    }.getOrDefault(false)

    private fun trimOutput(output: StringBuilder, maxBytes: Int) {
        while (output.toString().toByteArray(Charsets.UTF_8).size > maxBytes) {
            val newline = output.indexOf("\n")
            if (newline < 0) {
                output.delete(0, output.length)
                return
            }
            output.delete(0, newline + 1)
        }
    }
}
