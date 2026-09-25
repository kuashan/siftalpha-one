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
import com.siftalpha.core.storage.DurableRuntimeOwnership
import com.siftalpha.core.storage.DurableRuntimeOwnershipStore
import java.io.File
import java.io.InputStream
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
        val process: Process?,
        val root: ProcessHandle,
        val handle: ProjectProcessHandle,
        val startedAtEpochMs: Long,
        val stdout: BoundedLogBuffer = BoundedLogBuffer(),
        val stderr: BoundedLogBuffer = BoundedLogBuffer(),
        @Volatile var stoppedByUser: Boolean = false,
    )

    private val records = ConcurrentHashMap<String, Record>()
    @Volatile
    private var ownershipStore: DurableRuntimeOwnershipStore? = null

    internal fun bindOwnershipStore(store: DurableRuntimeOwnershipStore) {
        ownershipStore = store
    }

    internal fun recover(scope: ProjectProcessScope): Boolean {
        synchronized(records) {
            val existing = records[scope.projectId]
            if (existing?.root?.isAlive == true) return true

            val identity = ownershipStore?.read(scope.projectId) ?: return false
            val pid = identity.platformHandle.removePrefix("macos-pid:").toLongOrNull()
                ?: run {
                    ownershipStore?.clear(scope.projectId)
                    return false
                }
            val root = ProcessHandle.of(pid).orElse(null)
            if (root == null || !root.isAlive || !matchesStartTime(root, identity.startedAtEpochMs)) {
                ownershipStore?.clear(scope.projectId)
                return false
            }
            records[scope.projectId] = Record(
                process = null,
                root = root,
                handle = ProjectProcessHandle(scope, identity.platformHandle),
                startedAtEpochMs = identity.startedAtEpochMs,
            )
            return true
        }
    }

    override fun start(request: ProjectProcessLaunchRequest): ProjectProcessHandle {
        val projectId = request.scope.projectId
        synchronized(records) {
            val existing = records[projectId]
            if (existing?.root?.isAlive == true) {
                error("project already owns a running process: " + projectId)
            }

            val command = listOf(request.executable) + request.arguments
            val builder = ProcessBuilder(command)
            request.workingDirectory?.let { builder.directory(File(it)) }
            builder.environment().putAll(request.environment)

            val process = builder.start()
            val root = process.toHandle()
            val startedAtEpochMs = root.info().startInstant().orElse(null)
                ?.toEpochMilli()
                ?: System.currentTimeMillis()
            val handle = ProjectProcessHandle(
                scope = request.scope,
                platformHandle = "macos-pid:" + process.pid(),
            )
            val record = Record(
                process = process,
                root = root,
                handle = handle,
                startedAtEpochMs = startedAtEpochMs,
            )
            records[projectId] = record
            val generation = request.environment["SIFTALPHA_OPERATION_GENERATION"]
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: 1L
            ownershipStore?.write(
                DurableRuntimeOwnership(
                    projectId = projectId,
                    generation = generation,
                    platformHandle = handle.platformHandle,
                    startedAtEpochMs = startedAtEpochMs,
                ),
            )
            pump(process.inputStream, record.stdout, "stdout", projectId)
            pump(process.errorStream, record.stderr, "stderr", projectId)
            return handle
        }
    }

    override fun status(scope: ProjectProcessScope): ProjectProcessStatus {
        val record = records[scope.projectId]
            ?: return ProjectProcessStatus(scope, ProjectProcessState.UNKNOWN)

        if (record.root.isAlive) {
            return ProjectProcessStatus(scope, ProjectProcessState.RUNNING)
        }

        ownershipStore?.clear(scope.projectId)
        val exitCode = record.process?.let { process ->
            runCatching { process.exitValue() }.getOrNull()
        }
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
        val root = record.root
        return buildSet {
            if (root.isAlive) add(root.pid())
            root.descendants().forEach { handle ->
                if (handle.isAlive) add(handle.pid())
            }
        }
    }

    internal fun forget(scope: ProjectProcessScope): Boolean {
        synchronized(records) {
            val record = records[scope.projectId] ?: return true
            if (record.root.isAlive) return false
            ownershipStore?.clear(scope.projectId)
            return records.remove(scope.projectId, record)
        }
    }

    override fun stopProject(scope: ProjectProcessScope): ProjectStopResult {
        val record = records[scope.projectId]
            ?: return ProjectStopResult(scope, ProjectStopOutcome.NOT_FOUND)

        if (!record.root.isAlive) {
            ownershipStore?.clear(scope.projectId)
            return ProjectStopResult(scope, ProjectStopOutcome.ALREADY_STOPPED)
        }

        record.stoppedByUser = true
        return runCatching {
            terminateOwnedTree(record.root)
            if (record.root.isAlive) {
                ProjectStopResult(
                    scope,
                    ProjectStopOutcome.FAILED,
                    "project process remained alive after termination",
                )
            } else {
                ownershipStore?.clear(scope.projectId)
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

    private fun terminateOwnedTree(root: ProcessHandle) {
        repeat(3) {
            val descendants = root.descendants().toList().asReversed()
            descendants.forEach { handle ->
                if (handle.isAlive) handle.destroy()
            }
            if (root.isAlive) root.destroy()
            if (awaitStopped(root, descendants, 700)) return

            descendants.forEach { handle ->
                if (handle.isAlive) handle.destroyForcibly()
            }
            if (root.isAlive) root.destroyForcibly()
            if (awaitStopped(root, descendants, 700)) return
        }
    }

    private fun awaitStopped(
        root: ProcessHandle,
        descendants: List<ProcessHandle>,
        timeoutMs: Long,
    ): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (!root.isAlive && descendants.none { it.isAlive }) return true
            Thread.sleep(25)
        }
        return !root.isAlive && descendants.none { it.isAlive }
    }

    private fun matchesStartTime(handle: ProcessHandle, expectedEpochMs: Long): Boolean {
        val actual = handle.info().startInstant().orElse(null)?.toEpochMilli() ?: return false
        return kotlin.math.abs(actual - expectedEpochMs) <= 2_000L
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
