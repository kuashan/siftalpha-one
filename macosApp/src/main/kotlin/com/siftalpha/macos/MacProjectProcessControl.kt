package com.siftalpha.macos

import com.siftalpha.core.process.ProjectProcessControl
import com.siftalpha.core.process.ProjectProcessHandle
import com.siftalpha.core.process.ProjectProcessLaunchRequest
import com.siftalpha.core.process.ProjectProcessLogs
import com.siftalpha.core.process.ProjectProcessScope
import com.siftalpha.core.process.ProjectProcessState
import com.siftalpha.core.process.ProjectProcessStatus
import com.siftalpha.core.process.ProjectStopOutcome
import com.siftalpha.core.process.ProjectStopResult
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * macOS implementation of ProjectProcessControl（项目进程控制）.
 *
 * Ownership is explicit and project-scoped. STOP never scans global processes; it only terminates
 * the root Process and descendant ProcessHandles created for the selected project record.
 */
class MacProjectProcessControl : ProjectProcessControl {
    private data class Record(
        val process: Process,
        val handle: ProjectProcessHandle,
        val stdout: BoundedLogBuffer = BoundedLogBuffer(),
        val stderr: BoundedLogBuffer = BoundedLogBuffer(),
        @Volatile var stoppedByUser: Boolean = false,
    )

    private val records = ConcurrentHashMap<String, Record>()

    override fun start(request: ProjectProcessLaunchRequest): ProjectProcessHandle {
        val projectId = request.scope.projectId
        synchronized(records) {
            val existing = records[projectId]
            if (existing?.process?.isAlive == true) {
                error("project already owns a running process: " + projectId)
            }

            val command = listOf(request.executable) + request.arguments
            val builder = ProcessBuilder(command)
            request.workingDirectory?.let { builder.directory(File(it)) }
            builder.environment().putAll(request.environment)

            val process = builder.start()
            val handle = ProjectProcessHandle(
                scope = request.scope,
                platformHandle = "macos:" + process.pid() + ":" + UUID.randomUUID(),
            )
            val record = Record(process = process, handle = handle)
            records[projectId] = record
            pump(process.inputStream, record.stdout, "stdout", projectId)
            pump(process.errorStream, record.stderr, "stderr", projectId)
            return handle
        }
    }

    override fun status(scope: ProjectProcessScope): ProjectProcessStatus {
        val record = records[scope.projectId]
            ?: return ProjectProcessStatus(scope, ProjectProcessState.UNKNOWN)

        if (record.process.isAlive) {
            return ProjectProcessStatus(scope, ProjectProcessState.RUNNING)
        }

        val exitCode = runCatching { record.process.exitValue() }.getOrNull()
        val state = when {
            record.stoppedByUser -> ProjectProcessState.STOPPED
            exitCode == 0 -> ProjectProcessState.EXITED_SUCCESS
            exitCode != null -> ProjectProcessState.EXITED_ERROR
            else -> ProjectProcessState.UNKNOWN
        }
        return ProjectProcessStatus(scope, state, exitCode)
    }

    override fun logs(
        scope: ProjectProcessScope,
        maxBytes: Int,
    ): ProjectProcessLogs {
        require(maxBytes > 0) { "maxBytes must be > 0" }
        val record = records[scope.projectId]
            ?: return ProjectProcessLogs(scope, stdout = "", stderr = "")

        val stdout = record.stdout.snapshot(maxBytes)
        val stderr = record.stderr.snapshot(maxBytes)
        return ProjectProcessLogs(
            scope = scope,
            stdout = stdout.text,
            stderr = stderr.text,
            truncated = stdout.truncated || stderr.truncated,
        )
    }

    internal fun ownedPids(scope: ProjectProcessScope): Set<Long> {
        val record = records[scope.projectId] ?: return emptySet()
        val root = record.process.toHandle()
        return buildSet {
            if (root.isAlive) add(root.pid())
            root.descendants().forEach { handle ->
                if (handle.isAlive) add(handle.pid())
            }
        }
    }

    override fun stopProject(scope: ProjectProcessScope): ProjectStopResult {
        val record = records[scope.projectId]
            ?: return ProjectStopResult(scope, ProjectStopOutcome.NOT_FOUND)

        if (!record.process.isAlive) {
            return ProjectStopResult(scope, ProjectStopOutcome.ALREADY_STOPPED)
        }

        record.stoppedByUser = true
        return runCatching {
            terminateOwnedTree(record.process)
            if (record.process.isAlive) {
                ProjectStopResult(
                    scope,
                    ProjectStopOutcome.FAILED,
                    "project process remained alive after termination",
                )
            } else {
                ProjectStopResult(scope, ProjectStopOutcome.STOPPED)
            }
        }.getOrElse { error ->
            ProjectStopResult(
                scope,
                ProjectStopOutcome.FAILED,
                error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun terminateOwnedTree(process: Process) {
        repeat(3) {
            val descendants = process.descendants().toList().asReversed()
            descendants.forEach { handle ->
                if (handle.isAlive) handle.destroy()
            }
            if (process.isAlive) process.destroy()
            if (awaitStopped(process, descendants, 700)) return

            descendants.forEach { handle ->
                if (handle.isAlive) handle.destroyForcibly()
            }
            if (process.isAlive) process.destroyForcibly()
            if (awaitStopped(process, descendants, 700)) return
        }
    }

    private fun awaitStopped(
        process: Process,
        descendants: List<ProcessHandle>,
        timeoutMs: Long,
    ): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (!process.isAlive && descendants.none { it.isAlive }) return true
            Thread.sleep(25)
        }
        return !process.isAlive && descendants.none { it.isAlive }
    }

    private fun pump(
        input: InputStream,
        target: BoundedLogBuffer,
        streamName: String,
        projectId: String,
    ) {
        thread(
            isDaemon = true,
            name = "siftalpha-macos-" + sanitize(projectId) + "-" + streamName,
        ) {
            input.use { source ->
                val buffer = ByteArray(4096)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count > 0) target.append(buffer, count)
                }
            }
        }
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

    private class BoundedLogBuffer(
        private val capacity: Int = 1_048_576,
    ) {
        private var bytes = ByteArray(0)
        private var dropped = false

        @Synchronized
        fun append(source: ByteArray, count: Int) {
            if (count <= 0) return
            val combined = ByteArray(bytes.size + count)
            bytes.copyInto(combined)
            source.copyInto(combined, destinationOffset = bytes.size, endIndex = count)
            if (combined.size > capacity) {
                bytes = combined.copyOfRange(combined.size - capacity, combined.size)
                dropped = true
            } else {
                bytes = combined
            }
        }

        @Synchronized
        fun snapshot(maxBytes: Int): Snapshot {
            val start = (bytes.size - maxBytes).coerceAtLeast(0)
            val selected = bytes.copyOfRange(start, bytes.size)
            return Snapshot(
                text = selected.toString(Charsets.UTF_8),
                truncated = dropped || start > 0,
            )
        }

        data class Snapshot(
            val text: String,
            val truncated: Boolean,
        )
    }
}
