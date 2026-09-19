package com.siftalpha.studio.siftalphax

import android.content.Context
import android.system.Os
import com.siftalpha.studio.runtime.InternalRuntimeForegroundService
import com.siftalpha.studio.runtime.InterruptibleProjectTreeDelete
import com.siftalpha.studio.runtime.RuntimeOperationContract
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

internal object InternalAlpinePythonRuntimeBootstrap {
    private const val DEFAULT_MAX_ATTEMPTS = 4

    fun installCommand(maxAttempts: Int = DEFAULT_MAX_ATTEMPTS): String {
        require(maxAttempts >= 1) { "maxAttempts must be positive" }
        return """
            set -eu
            apk_attempt=1
            while :; do
              set +e
              apk add --no-cache ca-certificates python3 py3-pip py3-virtualenv
              apk_code=${'$'}?
              set -e
              if [ "${'$'}apk_code" -eq 0 ]; then
                break
              fi
              if [ "${'$'}apk_attempt" -ge "$maxAttempts" ]; then
                printf 'SIFTALPHA_INTERNAL_ALPINE_APK_FAILED attempt=%s/%s exit=%s\n' "${'$'}apk_attempt" "$maxAttempts" "${'$'}apk_code"
                exit "${'$'}apk_code"
              fi
              printf 'SIFTALPHA_INTERNAL_ALPINE_APK_RETRY attempt=%s/%s exit=%s\n' "${'$'}apk_attempt" "$maxAttempts" "${'$'}apk_code"
              sleep_seconds=${'$'}((apk_attempt * 2))
              sleep "${'$'}sleep_seconds"
              apk_attempt=${'$'}((apk_attempt + 1))
            done
            python3 --version
            virtualenv --version
        """.trimIndent()
    }
}

internal object InternalAlpineDependencyBootstrap {
    private const val PIP_COMMON =
        "--disable-pip-version-check --no-input --no-compile --timeout 30 --retries 4"

    fun installCommand(kind: InternalAlpineDependencySource.Kind): String = buildString {
        append("set -eu\n")
        append("printf 'SIFTALPHA_X_INTERNAL_PREPARE_STEP=CREATE_VENV\\n'\n")
        append("virtualenv /siftalpha-env/venv\n")
        append("printf 'SIFTALPHA_X_INTERNAL_PREPARE_STEP=UPGRADE_PIP\\n'\n")
        append("/siftalpha-env/venv/bin/python -m pip install ")
        append(PIP_COMMON)
        append(" --upgrade pip\n")
        when (kind) {
            InternalAlpineDependencySource.Kind.REQUIREMENTS_TXT -> {
                append("printf 'SIFTALPHA_X_INTERNAL_PREPARE_STEP=INSTALL_DEPENDENCIES\\n'\n")
                append("/siftalpha-env/venv/bin/python -m pip install ")
                append(PIP_COMMON)
                append(" -r /workspace/requirements.txt\n")
            }
            InternalAlpineDependencySource.Kind.PYPROJECT_TOML -> {
                append("printf 'SIFTALPHA_X_INTERNAL_PREPARE_STEP=INSTALL_DEPENDENCIES\\n'\n")
                append("/siftalpha-env/venv/bin/python -m pip install ")
                append(PIP_COMMON)
                append(" /workspace\n")
            }
            InternalAlpineDependencySource.Kind.NONE -> Unit
        }
        append("printf 'SIFTALPHA_X_INTERNAL_PREPARE_STEP=PIP_CHECK\\n'\n")
        append("/siftalpha-env/venv/bin/python -m pip check\n")
        append("printf 'SIFTALPHA_X_INTERNAL_PREPARE_STEP=VERIFY_PYTHON\\n'\n")
        append("/siftalpha-env/venv/bin/python -c 'import sys; print(sys.version)'\n")
    }
}

internal object InternalAlpinePrepareWatchdog {
    const val HEARTBEAT_INTERVAL_MS = 5_000L
    const val OUTPUT_IDLE_TIMEOUT_MS = 12L * 60L * 1_000L
    const val HARD_TIMEOUT_MS = 30L * 60L * 1_000L

    fun violation(elapsedMillis: Long, outputIdleMillis: Long): String? = when {
        elapsedMillis >= HARD_TIMEOUT_MS -> "HARD_TIMEOUT"
        outputIdleMillis >= OUTPUT_IDLE_TIMEOUT_MS -> "OUTPUT_IDLE_TIMEOUT"
        else -> null
    }
}

internal object InternalAlpineProcessControl {
    private const val MAX_DESCENDANTS = 2048

    fun throwIfCancelled() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("Internal Alpine operation cancelled")
        }
    }

    fun terminate(
        managed: InternalAlpineManagedProcess,
        gracefulMillis: Long = 1500L,
    ) {
        val process = managed.process
        val procRoot = File("/proc")
        val rootPid = managed.hostPid()?.toLong()
        val descendants = rootPid?.let { descendantPids(procRoot, it) }.orEmpty()

        descendants.forEach { signal(it, OsConstants.SIGTERM) }
        rootPid?.toInt()?.let { signal(it, OsConstants.SIGTERM) }
        runCatching { process.destroy() }
        runCatching { process.waitFor(gracefulMillis, TimeUnit.MILLISECONDS) }

        val rootAlive = rootPid?.toInt()?.let { pidAlive(procRoot, it) } == true
        val childAlive = descendants.any { pidAlive(procRoot, it) }
        if (process.isAlive || rootAlive || childAlive) {
            val refreshed = rootPid?.let { descendantPids(procRoot, it) }.orEmpty()
            (descendants + refreshed).distinct().forEach {
                signal(it, OsConstants.SIGKILL)
            }
            rootPid?.toInt()?.let { signal(it, OsConstants.SIGKILL) }
            if (process.isAlive) {
                runCatching { process.destroyForcibly() }
                runCatching {
                    process.waitFor(gracefulMillis, TimeUnit.MILLISECONDS)
                }
            }
        }
        managed.cleanup()
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
        progress: ((String) -> Unit)? = null,
    ): PreparationResult {
        require(projectIdentity.isNotBlank())
        InternalAlpineProcessControl.throwIfCancelled()
        progress?.invoke(progressText(projectIdentity, "ALPINE_RUNTIME", ""))
        val layout = InternalAlpineFiles.prepare(appContext)
        InternalAlpineProcessControl.throwIfCancelled()
        ensurePythonRuntime(layout, projectIdentity, progress)
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
            val command = InternalAlpineDependencyBootstrap.installCommand(source.kind)
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
                projectIdentity = projectIdentity,
                stage = "ALPINE_PROJECT_DEPENDENCIES",
                progress = progress,
            )
            val environmentKey = environmentKey(projectIdentity, source.sourceFingerprint)
            File(temp, READY_MARKER).writeText(
                "BACKEND=ALPINE\nSOURCE_FINGERPRINT=" + source.sourceFingerprint +
                    "\nENVIRONMENT_KEY=" + environmentKey + "\n",
            )
            if (environmentRoot.exists()) {
                InterruptibleProjectTreeDelete.delete(
                    environmentRoot = environmentRoot,
                    allowedParent = checkNotNull(environmentRoot.parentFile),
                    deadlineNanos = System.nanoTime() +
                        RuntimeOperationContract.PREPARE_TIMEOUT_MS * 1_000_000L,
                )
            }
            check(temp.renameTo(environmentRoot)) { "Unable to activate Internal Alpine environment" }
            return PreparationResult(true, Outcome.READY_ENVIRONMENT_INSTALLED, environmentRoot, environmentKey)
        } catch (error: Throwable) {
            temp.deleteRecursively()
            throw error
        }
    }

    @Synchronized
    fun cleanProjectEnvironment(projectIdentity: String) {
        require(projectIdentity.isNotBlank())
        InternalAlpineProcessControl.throwIfCancelled()
        val root = InternalAlpineFiles.projectEnvironmentRoot(appContext, projectIdentity)
        if (root.exists()) {
            InterruptibleProjectTreeDelete.delete(
                environmentRoot = root,
                allowedParent = checkNotNull(root.parentFile),
                deadlineNanos = System.nanoTime() +
                    RuntimeOperationContract.CLEAN_TIMEOUT_MS * 1_000_000L,
            )
        }
        InternalAlpineProcessControl.throwIfCancelled()
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

    private fun ensurePythonRuntime(
        layout: InternalAlpineLayout,
        projectIdentity: String,
        progress: ((String) -> Unit)?,
    ) {
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
                InternalAlpinePythonRuntimeBootstrap.installCommand(),
            ),
            logFile = log,
            failurePrefix = "INTERNAL_ALPINE_PYTHON_RUNTIME_PREPARE_FAILED",
            projectIdentity = projectIdentity,
            stage = "ALPINE_PYTHON_RUNTIME",
            progress = progress,
        )
        InternalAlpineProcessControl.throwIfCancelled()
        marker.writeText("READY=1\n")
    }

    private fun runCommand(
        builder: InternalAlpineCommand,
        logFile: File,
        failurePrefix: String,
        projectIdentity: String,
        stage: String,
        progress: ((String) -> Unit)?,
    ) {
        logFile.parentFile?.mkdirs()
        builder.redirectErrorStream(true).redirectOutput(logFile)
        progress?.invoke(progressText(projectIdentity, stage, "", 0L, 0L, true))
        val managed = builder.start()
        val process = managed.process
        val startedAt = System.currentTimeMillis()
        var lastObservedTail = ""
        var lastOutputChangedAt = startedAt
        var lastPublishedAt = 0L

        fun publishProgress(force: Boolean = false) {
            val callback = progress ?: return
            val now = System.currentTimeMillis()
            val tail = readTail(logFile, PROGRESS_TAIL_CHARS)
            if (tail != lastObservedTail) {
                lastObservedTail = tail
                lastOutputChangedAt = now
            }
            val dueHeartbeat = now - lastPublishedAt >= InternalAlpinePrepareWatchdog.HEARTBEAT_INTERVAL_MS
            if (!force && !dueHeartbeat) return
            lastPublishedAt = now
            callback(
                progressText(
                    projectIdentity = projectIdentity,
                    stage = stage,
                    tail = tail,
                    elapsedMillis = now - startedAt,
                    outputIdleMillis = now - lastOutputChangedAt,
                    processAlive = process.isAlive,
                ),
            )
        }

        try {
            while (true) {
                if (process.waitFor(250, TimeUnit.MILLISECONDS)) break
                publishProgress()
                val now = System.currentTimeMillis()
                val violation = InternalAlpinePrepareWatchdog.violation(
                    elapsedMillis = now - startedAt,
                    outputIdleMillis = now - lastOutputChangedAt,
                )
                if (violation != null) {
                    InternalAlpineProcessControl.terminate(managed)
                    error(
                        failurePrefix +
                            ": " + violation +
                            " elapsed_sec=" + ((now - startedAt) / 1_000L) +
                            " output_idle_sec=" + ((now - lastOutputChangedAt) / 1_000L) +
                            "\n" + readTail(logFile, 12_000),
                    )
                }
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException("Internal Alpine operation cancelled")
                }
            }
            publishProgress(force = true)
        } catch (cancelled: InterruptedException) {
            InternalAlpineProcessControl.terminate(managed)
            Thread.currentThread().interrupt()
            throw IllegalStateException("INTERNAL_ALPINE_OPERATION_CANCELLED", cancelled)
        }
        managed.cleanup()
        if (process.exitValue() != 0) {
            error(failurePrefix + ": exit=" + process.exitValue() + "\n" + readTail(logFile, 12_000))
        }
    }

    private fun progressText(
        projectIdentity: String,
        stage: String,
        tail: String,
        elapsedMillis: Long = 0L,
        outputIdleMillis: Long = 0L,
        processAlive: Boolean = true,
    ): String = buildString {
        appendLine("SIFTALPHA_X_RUNTIME_PROVIDER=EMBEDDED_R")
        appendLine("SIFTALPHA_X_PROJECT_ID=" + projectIdentity)
        appendLine("SIFTALPHA_X_ENVIRONMENT_STAGE=PREPARING")
        appendLine("SIFTALPHA_X_INTERNAL_PREPARE_STAGE=" + stage)
        appendLine("SIFTALPHA_X_INTERNAL_PREPARE_PROCESS_ALIVE=" + processAlive)
        appendLine("SIFTALPHA_X_INTERNAL_PREPARE_ELAPSED_SEC=" + (elapsedMillis / 1_000L))
        append("SIFTALPHA_X_INTERNAL_PREPARE_OUTPUT_IDLE_SEC=" + (outputIdleMillis / 1_000L))
        if (tail.isNotBlank()) {
            appendLine()
            appendLine()
            append(tail.trimEnd())
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
        private const val PROGRESS_INTERVAL_MS = 750L
        private const val PROGRESS_TAIL_CHARS = 16_000

        internal fun readTail(file: File, maxChars: Int): String {
            if (!file.isFile) return ""
            val text = runCatching { file.readText() }.getOrDefault("")
            return if (text.length <= maxChars) text else text.takeLast(maxChars)
        }
    }
}

class InternalAlpineSession private constructor(context: Context) {
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
        @Volatile var process: InternalAlpineManagedProcess?,
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

    /**
     * Returns listener facts only for the still-active execution identified by the snapshot.
     * A delayed observation from an older session/generation is rejected before procfs access.
     */
    fun webObservation(
        projectIdentity: String,
        expectedSessionId: String,
        expectedGeneration: Long,
        preferredHints: Collection<Int> = emptyList(),
    ): InternalAlpineWebObservation? {
        val record = records[projectIdentity] ?: return null
        if (!InternalAlpineWebDiscovery.belongsToExecution(
                expectedSessionId = expectedSessionId,
                expectedGeneration = expectedGeneration,
                currentSessionId = record.sessionId,
                currentGeneration = record.generation,
            )
        ) {
            return null
        }
        if (!EmbeddedPythonStatePolicy.canStop(record.state)) return null
        val managed = record.process ?: return InternalAlpineWebObservation.empty()
        val hostPid = managed.hostPid() ?: return InternalAlpineWebObservation.empty()
        return InternalAlpineWebDiscovery.observe(
            procRoot = File("/proc"),
            rootPid = hostPid,
            preferredHints = preferredHints,
        )
    }

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
        val command = InternalAlpineFiles.buildCommand(
            appContext,
            layout,
            shell,
            binds = listOf(executionRoot to "/workspace", environmentRoot to "/siftalpha-env"),
            workingDirectory = "/workspace",
        )
        command.redirectOutput(stdout).redirectError(stderr)
        val managed = command.start()
        val process = managed.process
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
            process = managed,
        )
        records[projectIdentity] = record
        try {
            InternalRuntimeForegroundService.acquire(appContext, sessionId)
        } catch (error: Throwable) {
            records.remove(projectIdentity, record)
            InternalAlpineProcessControl.terminate(managed)
            throw IllegalStateException("INTERNAL_RUNTIME_FOREGROUND_SERVICE_FAILED", error)
        }
        monitor.execute {
            try {
                val code = runCatching { process.waitFor() }.getOrElse { -1 }
                managed.cleanup()
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
            } finally {
                InternalRuntimeForegroundService.release(appContext, sessionId)
            }
        }
        return snapshotOf(record)
    }

    fun requestStop(projectIdentity: String): Boolean {
        val record = records[projectIdentity] ?: return false
        if (!EmbeddedPythonStatePolicy.canStop(record.state)) return false
        record.stopRequested = true
        val managed = record.process ?: return false
        InternalAlpineProcessControl.terminate(managed)
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

    companion object {
        @Volatile
        private var sharedInstance: InternalAlpineSession? = null

        /** Keep Internal Alpine session records and generations alive for the app process. */
        fun shared(context: Context): InternalAlpineSession =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: InternalAlpineSession(context).also { sharedInstance = it }
            }
    }
}
