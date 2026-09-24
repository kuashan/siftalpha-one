package com.siftalpha.macos

import com.siftalpha.core.process.ProjectProcessLaunchRequest
import com.siftalpha.core.process.ProjectProcessScope
import com.siftalpha.core.process.ProjectProcessState
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong
import org.tomlj.Toml
import org.tomlj.TomlTable

data class MacPreparedEnvironment(
    val projectId: String,
    val generation: Long,
    val root: File,
    val pythonExecutable: File?,
    val nodeExecutable: File?,
)

data class MacPrepareResult(
    val success: Boolean,
    val cancelled: Boolean,
    val environment: MacPreparedEnvironment?,
    val lines: List<String>,
    val detail: String? = null,
)

class MacProjectEnvironmentManager(
    private val processControl: MacProjectProcessControl,
    private val managedPython: MacManagedPythonRuntime?,
    dataRoot: File = defaultDataRoot(),
) {
    private val root = dataRoot
    private val generations = AtomicLong(System.currentTimeMillis())

    fun managedPythonExecutable(): File? =
        managedPython?.pythonExecutable?.takeIf { managedPython.available }

    fun currentEnvironment(projectId: String): MacPreparedEnvironment? {
        val projectRoot = projectRoot(projectId)
        val pointer = File(projectRoot, "current-environment.txt")
        if (!pointer.isFile) return null
        val name = pointer.readText().trim()
        if (name.isBlank() || '/' in name || '\\' in name) return null
        val envRoot = File(projectRoot, "environments/" + name).canonicalFile
        if (!envRoot.isDirectory || !envRoot.toPath().startsWith(projectRoot.canonicalFile.toPath())) {
            return null
        }
        val generation = name.removePrefix("env-").toLongOrNull() ?: return null
        val python = File(envRoot, "bin/python3").takeIf { it.isFile && it.canExecute() }
        val node = File(envRoot, "bin/node").takeIf { it.isFile && it.canExecute() }
        return MacPreparedEnvironment(projectId, generation, envRoot, python, node)
    }

    fun prepare(
        project: MacImportedProject,
        snapshot: MacProjectSnapshot,
        plan: MacProjectEnvironmentPlan,
        cancelled: () -> Boolean,
        log: (String) -> Unit,
    ): MacPrepareResult {
        val primary = plan.needs?.primaryRuntime
            ?: return failure("environment plan has no primary runtime")
        if (plan.status == MacProjectPlanStatus.BLOCKED) {
            return failure("environment plan is blocked: " + plan.issues.joinToString("; "))
        }

        return when (primary.id) {
            "python" -> preparePython(project, snapshot, cancelled, log)
            "nodejs" -> prepareNode(project, snapshot, plan, cancelled, log)
            else -> failure("M4.2 prepare does not support runtime: " + primary.id)
        }
    }

    private fun preparePython(
        project: MacImportedProject,
        snapshot: MacProjectSnapshot,
        cancelled: () -> Boolean,
        log: (String) -> Unit,
    ): MacPrepareResult {
        val basePython = managedPythonExecutable()
            ?: return failure("managed Python runtime is unavailable")

        val generation = generations.incrementAndGet()
        val projectRoot = projectRoot(project.projectId)
        val environments = File(projectRoot, "environments").apply { mkdirs() }
        val temp = File(environments, "env-" + generation + ".tmp")
        val final = File(environments, "env-" + generation)
        temp.deleteRecursively()
        final.deleteRecursively()

        fun abort(detail: String, cancelledFlag: Boolean = false): MacPrepareResult {
            temp.deleteRecursively()
            return MacPrepareResult(
                success = false,
                cancelled = cancelledFlag,
                environment = null,
                lines = listOf("SIFTALPHA_M4_PREPARE=FAILED", "DETAIL=" + detail),
                detail = detail,
            )
        }

        if (cancelled()) return abort("prepare cancelled before runtime acquisition", true)

        log("VALIDATE_PLAN:PASS")
        log("ACQUIRE_RUNTIME:" + basePython.absolutePath)

        val venv = runCommand(
            projectId = project.projectId,
            executable = basePython,
            arguments = listOf("-m", "venv", "--copies", temp.absolutePath),
            workingDirectory = project.canonicalRootPath,
            cancelled = cancelled,
            log = log,
        )
        if (!venv.success) return abort(venv.detail, venv.cancelled)
        log("CREATE_ENVIRONMENT:PASS")

        val envPython = File(temp, "bin/python3")
        if (!envPython.isFile || !envPython.canExecute()) {
            return abort("venv did not create bin/python3")
        }

        val dependencies = dependencyArguments(snapshot)
        if (dependencies.isNotEmpty()) {
            val installArgs = buildList {
                add("-m")
                add("pip")
                add("install")
                add("--disable-pip-version-check")
                add("--no-input")
                add("--only-binary=:all:")
                addAll(dependencies)
            }
            val install = runCommand(
                projectId = project.projectId,
                executable = envPython,
                arguments = installArgs,
                workingDirectory = project.canonicalRootPath,
                cancelled = cancelled,
                log = log,
            )
            if (!install.success) return abort(install.detail, install.cancelled)
        }
        log("PYTHON_INSTALL:PASS dependencies=" + dependencies.size)

        val version = runCommand(
            projectId = project.projectId,
            executable = envPython,
            arguments = listOf("--version"),
            workingDirectory = project.canonicalRootPath,
            cancelled = cancelled,
            log = log,
        )
        if (!version.success) return abort(version.detail, version.cancelled)

        val pipCheck = runCommand(
            projectId = project.projectId,
            executable = envPython,
            arguments = listOf("-m", "pip", "--disable-pip-version-check", "check"),
            workingDirectory = project.canonicalRootPath,
            cancelled = cancelled,
            log = log,
        )
        if (!pipCheck.success) return abort(pipCheck.detail, pipCheck.cancelled)
        log("VERIFY_ENVIRONMENT:PASS")

        if (cancelled()) return abort("prepare cancelled before commit", true)

        commitEnvironment(temp, final, projectRoot)
        log("COMMIT_ENVIRONMENT:PASS")

        val environment = MacPreparedEnvironment(
            projectId = project.projectId,
            generation = generation,
            root = final,
            pythonExecutable = File(final, "bin/python3"),
            nodeExecutable = null,
        )
        return MacPrepareResult(
            success = true,
            cancelled = false,
            environment = environment,
            lines = listOf(
                "SIFTALPHA_M4_PREPARE=PASS",
                "SIFTALPHA_M4_ENV_GENERATION=" + generation,
                "SIFTALPHA_M4_ENV_PYTHON=" + environment.pythonExecutable?.absolutePath,
            ),
        )
    }

    private fun prepareNode(
        project: MacImportedProject,
        snapshot: MacProjectSnapshot,
        plan: MacProjectEnvironmentPlan,
        cancelled: () -> Boolean,
        log: (String) -> Unit,
    ): MacPrepareResult {
        val nodePath = plan.runtimeExecutables.entries
            .firstOrNull { it.key.id == "nodejs" }
            ?.value
            ?: return failure("host Node.js runtime is unavailable")
        val node = File(nodePath)
        val npm = File(node.parentFile, "npm")
        if (!npm.isFile || !npm.canExecute()) {
            return failure("npm is unavailable next to host Node.js runtime")
        }

        val hasLock = snapshot.relativePaths.any {
            '/' !in it && it.equals("package-lock.json", ignoreCase = true)
        }
        val args = if (hasLock) {
            listOf("ci", "--no-audit", "--no-fund")
        } else {
            listOf("install", "--no-audit", "--no-fund", "--package-lock=false")
        }

        val install = runCommand(
            projectId = project.projectId,
            executable = npm,
            arguments = args,
            workingDirectory = project.canonicalRootPath,
            cancelled = cancelled,
            log = log,
        )
        if (!install.success) {
            return MacPrepareResult(false, install.cancelled, null, emptyList(), install.detail)
        }

        val generation = generations.incrementAndGet()
        val projectRoot = projectRoot(project.projectId)
        val environments = File(projectRoot, "environments").apply { mkdirs() }
        val final = File(environments, "env-" + generation).apply { mkdirs() }
        val bin = File(final, "bin").apply { mkdirs() }
        val nodeLink = File(bin, "node")
        if (!nodeLink.exists()) {
            runCatching { Files.createSymbolicLink(nodeLink.toPath(), node.toPath()) }
                .getOrElse { node.copyTo(nodeLink, overwrite = true) }
        }
        commitPointer(projectRoot, final.name)
        log("NODE_INSTALL:PASS")
        log("COMMIT_ENVIRONMENT:PASS")

        return MacPrepareResult(
            success = true,
            cancelled = false,
            environment = MacPreparedEnvironment(
                project.projectId,
                generation,
                final,
                pythonExecutable = null,
                nodeExecutable = node,
            ),
            lines = listOf(
                "SIFTALPHA_M4_PREPARE=PASS",
                "SIFTALPHA_M4_ENV_GENERATION=" + generation,
                "SIFTALPHA_M4_ENV_NODE=" + node.absolutePath,
            ),
        )
    }

    private data class CommandResult(
        val success: Boolean,
        val cancelled: Boolean,
        val detail: String,
    )

    private fun runCommand(
        projectId: String,
        executable: File,
        arguments: List<String>,
        workingDirectory: String,
        cancelled: () -> Boolean,
        log: (String) -> Unit,
    ): CommandResult {
        val scope = ProjectProcessScope(projectId)
        try {
            processControl.start(
                ProjectProcessLaunchRequest(
                    scope = scope,
                    executable = executable.absolutePath,
                    arguments = arguments,
                    workingDirectory = workingDirectory,
                    environment = mapOf(
                        "PIP_DISABLE_PIP_VERSION_CHECK" to "1",
                        "PYTHONUNBUFFERED" to "1",
                    ),
                ),
            )
            while (true) {
                if (cancelled()) {
                    processControl.stopProject(scope)
                    val logs = processControl.logs(scope, 256 * 1024)
                    appendCommandLogs(logs.stdout, logs.stderr, log)
                    return CommandResult(false, true, "operation cancelled")
                }
                when (processControl.status(scope).state) {
                    ProjectProcessState.RUNNING,
                    ProjectProcessState.STARTING,
                    -> Thread.sleep(40)
                    ProjectProcessState.EXITED_SUCCESS -> {
                        val logs = processControl.logs(scope, 256 * 1024)
                        appendCommandLogs(logs.stdout, logs.stderr, log)
                        return CommandResult(true, false, "exit=0")
                    }
                    ProjectProcessState.STOPPED -> {
                        val logs = processControl.logs(scope, 256 * 1024)
                        appendCommandLogs(logs.stdout, logs.stderr, log)
                        return CommandResult(false, true, "process stopped")
                    }
                    ProjectProcessState.EXITED_ERROR,
                    ProjectProcessState.UNKNOWN,
                    -> {
                        val status = processControl.status(scope)
                        val logs = processControl.logs(scope, 256 * 1024)
                        appendCommandLogs(logs.stdout, logs.stderr, log)
                        return CommandResult(
                            false,
                            false,
                            "command failed state=" + status.state + " exit=" + status.exitCode,
                        )
                    }
                }
            }
        } catch (error: Throwable) {
            return CommandResult(false, false, error.message ?: error.javaClass.simpleName)
        }
    }

    private fun appendCommandLogs(stdout: String, stderr: String, log: (String) -> Unit) {
        stdout.lineSequence().filter { it.isNotBlank() }.forEach { log("stdout: " + it) }
        stderr.lineSequence().filter { it.isNotBlank() }.forEach { log("stderr: " + it) }
    }

    private fun dependencyArguments(snapshot: MacProjectSnapshot): List<String> {
        val requirements = snapshot.requirementsText
            ?.lineSequence()
            ?.map { line -> line.substringBefore('#').trim() }
            ?.filter { it.isNotBlank() }
            ?.toList()
            .orEmpty()

        val dependencies = if (requirements.isNotEmpty()) {
            requirements
        } else {
            val text = snapshot.pyprojectText ?: return emptyList()
            val parsed = Toml.parse(text)
            require(!parsed.hasErrors()) { "pyproject.toml is invalid" }
            val project = parsed.get("project") as? TomlTable ?: return emptyList()
            val array = project.getArray("dependencies") ?: return emptyList()
            (0 until array.size()).map { index ->
                array.getString(index)
            }
        }

        dependencies.forEach(::requireSafeDependency)
        return dependencies
    }

    private fun requireSafeDependency(value: String) {
        val normalized = value.trim()
        require(normalized.isNotBlank()) { "blank dependency is not allowed" }
        require(!normalized.startsWith("-")) { "requirements options are not allowed: " + normalized }
        require(!normalized.startsWith(".") && !normalized.startsWith("/")) {
            "local path dependencies are not allowed: " + normalized
        }
        require(!normalized.contains(" @ ")) { "direct URL dependencies are not allowed: " + normalized }
        require(
            !normalized.startsWith("git+", ignoreCase = true) &&
                !normalized.startsWith("http:", ignoreCase = true) &&
                !normalized.startsWith("https:", ignoreCase = true),
        ) { "remote direct dependencies are not allowed: " + normalized }
    }

    private fun commitEnvironment(temp: File, final: File, projectRoot: File) {
        try {
            Files.move(temp.toPath(), final.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Throwable) {
            Files.move(temp.toPath(), final.toPath())
        }
        commitPointer(projectRoot, final.name)
    }

    private fun commitPointer(projectRoot: File, name: String) {
        val pointer = File(projectRoot, "current-environment.txt")
        val tempPointer = File(projectRoot, "current-environment.txt.tmp")
        tempPointer.parentFile.mkdirs()
        tempPointer.writeText(name + "\n")
        try {
            Files.move(
                tempPointer.toPath(),
                pointer.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: Throwable) {
            Files.move(
                tempPointer.toPath(),
                pointer.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun projectRoot(projectId: String): File {
        val safe = projectId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96)
        return File(root, "projects/" + safe).apply { mkdirs() }
    }

    private fun failure(detail: String): MacPrepareResult =
        MacPrepareResult(
            success = false,
            cancelled = false,
            environment = null,
            lines = listOf("SIFTALPHA_M4_PREPARE=FAILED", "DETAIL=" + detail),
            detail = detail,
        )

    companion object {
        fun defaultDataRoot(): File =
            System.getProperty("siftalpha.data.root")
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?: File(System.getProperty("user.home"), "Library/Application Support/SiftAlpha X")
    }
}
