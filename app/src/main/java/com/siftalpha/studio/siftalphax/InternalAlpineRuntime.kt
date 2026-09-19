package com.siftalpha.studio.siftalphax

import android.content.Context
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class InternalPythonBackend {
    CPYTHON,
    ALPINE,
}

data class InternalAlpineDependencySource(
    val kind: Kind,
    val sourceFingerprint: String,
) {
    enum class Kind { REQUIREMENTS_TXT, PYPROJECT_TOML, NONE }

    companion object {
        fun fromProjectFiles(requirementsText: String?, pyprojectText: String?): InternalAlpineDependencySource {
            val kind: Kind
            val source: String
            when {
                requirementsText != null -> {
                    kind = Kind.REQUIREMENTS_TXT
                    source = "requirements.txt\n" + requirementsText.replace("\r\n", "\n").replace("\r", "\n")
                }
                pyprojectText != null -> {
                    kind = Kind.PYPROJECT_TOML
                    source = "pyproject.toml\n" + pyprojectText.replace("\r\n", "\n").replace("\r", "\n")
                }
                else -> {
                    kind = Kind.NONE
                    source = "none\n"
                }
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(source.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            return InternalAlpineDependencySource(kind, "sha256:$digest")
        }
    }
}

internal object InternalAlpineProcessControl {
    private const val MAX_DESCENDANTS = 2048

    fun throwIfCancelled() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("Internal Alpine operation cancelled")
        }
    }

    fun terminate(process: Process, gracefulMillis: Long = 1500L) {
        val procRoot = File("/proc")
        val rootPid = runCatching { process.pid() }
            .getOrNull()
            ?.takeIf { it in 1..Int.MAX_VALUE.toLong() }
        val descendants = rootPid?.let { descendantPids(procRoot, it) }.orEmpty()

        descendants.forEach { signal(it, OsConstants.SIGTERM) }
        runCatching { process.destroy() }
        runCatching { process.waitFor(gracefulMillis, TimeUnit.MILLISECONDS) }

        val stillAlive = process.isAlive || descendants.any { pidAlive(procRoot, it) }
        if (!stillAlive) return

        val refreshed = rootPid?.let { descendantPids(procRoot, it) }.orEmpty()
        (descendants + refreshed).distinct().forEach { signal(it, OsConstants.SIGKILL) }
        if (process.isAlive) {
            runCatching { process.destroyForcibly() }
            runCatching { process.waitFor(gracefulMillis, TimeUnit.MILLISECONDS) }
        }
    }

    internal fun descendantPids(procRoot: File, rootPid: Long): List<Int> {
        if (rootPid !in 1..Int.MAX_VALUE.toLong()) return emptyList()
        val root = rootPid.toInt()
        val visited = linkedSetOf<Int>()
        val result = mutableListOf<Int>()

        fun visit(parent: Int) {
            if (visited.size >= MAX_DESCENDANTS || !visited.add(parent)) return
            val childrenFile = File(procRoot, "$parent/task/$parent/children")
            val children = runCatching { childrenFile.readText() }
                .getOrDefault("")
                .trim()
                .split(Regex("\\s+"))
                .mapNotNull { it.toIntOrNull() }
                .filter { it > 0 }
            children.forEach { child ->
                visit(child)
                if (child != root && child !in result) result += child
            }
        }

        visit(root)
        return result
    }

    private fun pidAlive(procRoot: File, pid: Int): Boolean =
        pid > 0 && File(procRoot, pid.toString()).exists()

    private fun signal(pid: Int, signal: Int) {
        if (pid <= 0) return
        runCatching { Os.kill(pid, signal) }
    }
}

class InternalAlpineEnvironmentManager(context: Context) {
    private val appContext = context.applicationContext

    enum class Outcome { READY_REUSED, READY_ENVIRONMENT_INSTALLED }

    data class PreparationResult(
        val ready: Boolean,
        val outcome: Outcome,
        val environmentRoot: File,
        val environmentKey: String,
    )

    data class LoadBinding(val environmentRoot: File, val environmentKey: String)

    @Synchronized
    fun prepare(
        projectIdentity: String,
        stagedProject: File,
        source: InternalAlpineDependencySource,
    ): PreparationResult {
        require(projectIdentity.isNotBlank())
        InternalAlpineProcessControl.throwIfCancelled()
        val layout = InternalAlpineFiles.prepare(appContext)
        InternalAlpineProcessControl.throwIfCancelled()
        ensurePythonRuntime(layout)
        InternalAlpineProcessControl.throwIfCancelled()
        loadBinding(projectIdentity, source)?.let {
            return PreparationResult(true, Outcome.READY_REUSED, it.environmentRoot, it.environmentKey)
        }

        val environmentRoot = InternalAlpineFiles.projectEnvironmentRoot(appContext, projectIdentity)
        val parent = environmentRoot.parentFile ?: error("Internal Alpine environment has no parent")
        val temp = File(parent, environmentRoot.name + ".install-" + UUID.randomUUID())
        check(temp.mkdirs())
        val log = File(temp, "prepare.log")
        try {
            val command = buildString {
                append("set -eu\n")
                append("virtualenv /siftalpha-env/venv\n")
                append("/siftalpha-env/venv/bin/python -m pip install --disable-pip-version-check --no-input --upgrade pip\n")
                when (source.kind) {
                    InternalAlpineDependencySource.Kind.REQUIREMENTS_TXT ->
                        append("/siftalpha-env/venv/bin/python -m pip install --disable-pip-version-check --no-input -r /workspace/requirements.txt\n")
                    InternalAlpineDependencySource.Kind.PYPROJECT_TOML ->
                        append("/siftalpha-env/venv/bin/python -m pip install --disable-pip-version-check --no-input /workspace\n")
                    InternalAlpineDependencySource.Kind.NONE -> Unit
                }
                append("/siftalpha-env/venv/bin/python -m pip check\n")
                append("/siftalpha-env/venv/bin/python -c 'import sys; print(sys.version)'\n")
            }
            runCommand(
                builder = InternalAlpineFiles.buildCommand(
                    appContext,
                    layout,
                    command,
                    binds = listOf(stagedProject to "/workspace", temp to "/siftalpha-env"),
                    workingDirectory = "/workspace",
                ),
                logFile = log,
                failurePrefix = "INTERNAL_ALPINE_DEPENDENCY_PREPARE_FAILED",
            )
            val environmentKey = environmentKey(projectIdentity, source.sourceFingerprint)
            File(temp, READY_MARKER).writeText(
                "BACKEND=ALPINE\nSOURCE_FINGERPRINT=" + source.sourceFingerprint +
                    "\nENVIRONMENT_KEY=" + environmentKey + "\n",
            )
            if (environmentRoot.exists()) check(environmentRoot.deleteRecursively())
            check(temp.renameTo(environmentRoot)) { "Unable to activate Internal Alpine environment" }
            return PreparationResult(true, Outcome.READY_ENVIRONMENT_INSTALLED, environmentRoot, environmentKey)
        } catch (error: Throwable) {
            temp.deleteRecursively()
            throw error
        }
    }

    fun loadBinding(
        projectIdentity: String,
        source: InternalAlpineDependencySource,
    ): LoadBinding? {
        val root = InternalAlpineFiles.projectEnvironmentRoot(appContext, projectIdentity)
        val marker = File(root, READY_MARKER)
        if (!marker.isFile) return null
        val values = marker.readLines().mapNotNull {
            val index = it.indexOf('=')
            if (index <= 0) null else it.substring(0, index) to it.substring(index + 1)
        }.toMap()
        if (values["BACKEND"] != "ALPINE") return null
        if (values["SOURCE_FINGERPRINT"] != source.sourceFingerprint) return null
        val key = values["ENVIRONMENT_KEY"]?.takeIf { it.isNotBlank() } ?: return null
        val python = File(root, "venv/bin/python")
        if (!Files.exists(python.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
        return LoadBinding(root, key)
    }

    private fun ensurePythonRuntime(layout: InternalAlpineLayout) {
        InternalAlpineProcessControl.throwIfCancelled()
        val marker = File(layout.rootfs, PYTHON_READY_MARKER)
        if (
            marker.isFile &&
            Files.exists(File(layout.rootfs, "usr/bin/python3").toPath(), LinkOption.NOFOLLOW_LINKS) &&
            Files.exists(File(layout.rootfs, "usr/bin/virtualenv").toPath(), LinkOption.NOFOLLOW_LINKS)
        ) return

        val log = File(layout.rootfs.parentFile, "python-runtime-prepare.log")
        runCommand(
            builder = InternalAlpineFiles.buildCommand(
                appContext,
                layout,
                "set -eu; apk add --no-cache ca-certificates python3 py3-pip py3-virtualenv; python3 --version; virtualenv --version",
            ),
            logFile = log,
            failurePrefix = "INTERNAL_ALPINE_PYTHON_RUNTIME_PREPARE_FAILED",
        )
        InternalAlpineProcessControl.throwIfCancelled()
        marker.writeText("READY=1\n")
    }

    private fun runCommand(builder: ProcessBuilder, logFile: File, failurePrefix: String) {
        logFile.parentFile?.mkdirs()
        builder.redirectErrorStream(true)
        builder.redirectOutput(logFile)
        val process = builder.start()
        try {
            while (true) {
                if (process.waitFor(250, TimeUnit.MILLISECONDS)) break
                if (Thread.currentThread().isInterrupted) throw InterruptedException("Internal Alpine operation cancelled")
            }
        } catch (cancelled: InterruptedException) {
            InternalAlpineProcessControl.terminate(process)
            Thread.currentThread().interrupt()
            throw IllegalStateException("INTERNAL_ALPINE_OPERATION_CANCELLED", cancelled)
        }
        if (process.exitValue() != 0) {
            error(failurePrefix + ": exit=" + process.exitValue() + "\n" + readTail(logFile, 12_000))
        }
    }

    private fun environmentKey(projectIdentity: String, fingerprint: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("alpine\n" + projectIdentity + "\n" + fingerprint + "\n").toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "alpine:$digest"
    }

    companion object {
        private const val READY_MARKER = "siftalpha-alpine-environment-ready.txt"
        private const val PYTHON_READY_MARKER = ".siftalpha-python-runtime-ready"

        internal fun readTail(file: File, maxChars: Int): String {
            if (!file.isFile) return ""
            val text = runCatching { file.readText() }.getOrDefault("")
            return if (text.length <= maxChars) text else text.takeLast(maxChars)
        }
    }
}

class InternalAlpineSession(context: Context) {
    private val appContext = context.applicationContext
    private val nextGeneration = AtomicLong(0L)
    private val records = ConcurrentHashMap<String, Record>()
    private val monitor = Executors.newCachedThreadPool()

    private class Record(
        val sessionId: String,
        val projectIdentity: String,
        val executionRoot: File,
        val entrypoint: String,
        val generation: Long,
        val stdout: File,
        val stderr: File,
        val startedAt: Long,
        @Volatile var state: EmbeddedPythonState,
        @Volatile var process: Process?,
        @Volatile var finishedAt: Long? = null,
        @Volatile var exitCode: Int? = null,
        @Volatile var stopRequested: Boolean = false,
    )

    fun canStart(projectIdentity: String): Boolean {
        val state = records[projectIdentity]?.state ?: return true
        return EmbeddedPythonStatePolicy.isTerminal(state)
    }

    fun snapshot(projectIdentity: String): EmbeddedPythonSnapshot? =
        records[projectIdentity]?.let(::snapshotOf)

    fun start(
        projectIdentity: String,
        executionRoot: File,
        entrypoint: String,
        environmentRoot: File,
    ): EmbeddedPythonSnapshot {
        check(canStart(projectIdentity)) { "Internal Alpine project session is already active" }
        val layout = InternalAlpineFiles.prepare(appContext)
        val sessionId = "siftalpha-alpine-" + UUID.randomUUID()
        val sessionRoot = InternalAlpineFiles.sessionRoot(appContext, sessionId)
        val stdout = File(sessionRoot, "stdout.log")
        val stderr = File(sessionRoot, "stderr.log")
        val generation = nextGeneration.incrementAndGet()
        val safeEntrypoint = entrypoint.replace("'", "'\"'\"'")
        val shell = "exec /siftalpha-env/venv/bin/python '/workspace/" + safeEntrypoint + "'"
        val builder = InternalAlpineFiles.buildCommand(
            appContext,
            layout,
            shell,
            binds = listOf(executionRoot to "/workspace", environmentRoot to "/siftalpha-env"),
            workingDirectory = "/workspace",
        )
        builder.redirectOutput(stdout)
        builder.redirectError(stderr)
        val process = builder.start()
        val record = Record(
            sessionId = sessionId,
            projectIdentity = projectIdentity,
            executionRoot = executionRoot,
            entrypoint = entrypoint,
            generation = generation,
            stdout = stdout,
            stderr = stderr,
            startedAt = System.currentTimeMillis(),
            state = EmbeddedPythonState.RUNNING,
            process = process,
        )
        records[projectIdentity] = record
        monitor.execute {
            val code = runCatching { process.waitFor() }.getOrElse { -1 }
            record.exitCode = code
            record.finishedAt = System.currentTimeMillis()
            record.state = if (record.stopRequested) {
                EmbeddedPythonState.STOPPED
            } else if (code == 0) {
                EmbeddedPythonState.SUCCEEDED
            } else {
                EmbeddedPythonState.FAILED
            }
            record.process = null
        }
        return snapshotOf(record)
    }

    fun requestStop(projectIdentity: String): Boolean {
        val record = records[projectIdentity] ?: return false
        if (!EmbeddedPythonStatePolicy.canStop(record.state)) return false
        record.stopRequested = true
        val process = record.process ?: return false
        InternalAlpineProcessControl.terminate(process)
        return true
    }

    private fun snapshotOf(record: Record): EmbeddedPythonSnapshot =
        EmbeddedPythonSnapshot(
            engine = InternalPythonBackend.ALPINE,
            sessionId = record.sessionId,
            projectIdentity = record.projectIdentity,
            executionRoot = record.executionRoot.absolutePath,
            entrypoint = File(record.executionRoot, record.entrypoint).absolutePath,
            workingDirectory = record.executionRoot.absolutePath,
            generation = record.generation,
            state = record.state,
            runtimePhase = if (EmbeddedPythonStatePolicy.isTerminal(record.state)) {
                EmbeddedPythonRuntimePhase.TERMINAL
            } else {
                EmbeddedPythonRuntimePhase.RUNNING
            },
            stopPhase = if (record.stopRequested) EmbeddedPythonStopPhase.STOP_REQUEST_RETURNED else EmbeddedPythonStopPhase.IDLE,
            stopResult = if (record.stopRequested) EmbeddedPythonStopResult.REQUEST_ACCEPTED else EmbeddedPythonStopResult.NONE,
            startedAtEpochMs = record.startedAt,
            finishedAtEpochMs = record.finishedAt,
            exitCode = record.exitCode,
            stdout = InternalAlpineEnvironmentManager.readTail(record.stdout, 512 * 1024),
            stderr = InternalAlpineEnvironmentManager.readTail(record.stderr, 512 * 1024),
        )
}
