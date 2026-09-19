package com.siftalpha.studio.siftalphax

import com.siftalpha.studio.runtime.RuntimeWebPortDiscovery
import java.io.File
import java.nio.file.Files

/**
 * Bounded listener observation for one Internal Alpine process tree.
 *
 * The TCP tables are only used as an inode-to-port index. A port is eligible only when its socket
 * inode was first observed through the current Alpine host PID or one of its descendants. This is
 * deliberately different from scanning Android's listeners or probing every port.
 */
internal data class InternalAlpineWebObservation(
    val projectPidCount: Int,
    val socketInodeCount: Int,
    val ports: List<Int>,
) {
    fun diagnosticLines(): List<String> = if (ports.isEmpty()) {
        listOf("SIFTALPHA_X_INTERNAL_WEB_DISCOVERY=NO_CANDIDATE")
    } else {
        listOf(
            "SIFTALPHA_X_INTERNAL_WEB_DISCOVERY=PASS",
            "SIFTALPHA_X_INTERNAL_WEB_PORT=" + ports.first(),
            "SIFTALPHA_X_INTERNAL_WEB_SOURCE=PROJECT_PID_SOCKET",
        )
    }

    companion object {
        fun empty(): InternalAlpineWebObservation = InternalAlpineWebObservation(
            projectPidCount = 0,
            socketInodeCount = 0,
            ports = emptyList(),
        )
    }
}

internal object InternalAlpineWebDiscovery {
    private const val LISTEN_STATE = "0A"
    private const val MAX_PROJECT_PIDS = 2_048
    private const val MAX_FDS_PER_PID = 4_096
    private const val MAX_NET_TABLE_ROWS = 32_768
    private val socketLink = Regex("^socket:\\[(\\d+)]$")

    fun observe(
        procRoot: File,
        rootPid: Int,
    ): InternalAlpineWebObservation {
        if (rootPid <= 0) return InternalAlpineWebObservation.empty()
        val projectPids = linkedSetOf(rootPid).apply {
            addAll(InternalAlpineProcessControl.descendantPids(procRoot, rootPid.toLong()))
        }.take(MAX_PROJECT_PIDS)
        return observePids(procRoot, projectPids)
    }

    internal fun observePids(
        procRoot: File,
        projectPids: Collection<Int>,
    ): InternalAlpineWebObservation {
        val scopedPids = projectPids
            .filter { it > 0 }
            .distinct()
            .take(MAX_PROJECT_PIDS)
        if (scopedPids.isEmpty()) return InternalAlpineWebObservation.empty()

        val socketInodes = socketInodesForPids(procRoot, scopedPids)
        if (socketInodes.isEmpty()) {
            return InternalAlpineWebObservation(
                projectPidCount = scopedPids.size,
                socketInodeCount = 0,
                ports = emptyList(),
            )
        }

        val tables = buildList {
            add(File(procRoot, "net/tcp"))
            add(File(procRoot, "net/tcp6"))
            // Some Android builds expose the network table through a process-scoped proc path
            // even when the global /proc/net path is restricted. These paths are still limited to
            // the already validated project PID set.
            scopedPids.forEach { pid ->
                add(File(procRoot, "$pid/net/tcp"))
                add(File(procRoot, "$pid/net/tcp6"))
            }
        }.distinctBy { it.absolutePath }

        val ports = tables
            .flatMap { table -> listeningPorts(table, socketInodes) }
            .distinct()

        return InternalAlpineWebObservation(
            projectPidCount = scopedPids.size,
            socketInodeCount = socketInodes.size,
            ports = RuntimeWebPortDiscovery.rankCandidates(ports),
        )
    }

    internal fun belongsToExecution(
        expectedSessionId: String,
        expectedGeneration: Long,
        currentSessionId: String,
        currentGeneration: Long,
    ): Boolean = expectedSessionId.isNotBlank() &&
        expectedSessionId == currentSessionId &&
        expectedGeneration > 0L &&
        expectedGeneration == currentGeneration

    private fun socketInodesForPids(
        procRoot: File,
        projectPids: Collection<Int>,
    ): Set<Long> = buildSet {
        projectPids.forEach { pid ->
            val fdDirectory = File(procRoot, "$pid/fd")
            val descriptors = runCatching { fdDirectory.listFiles().orEmpty() }
                .getOrDefault(emptyArray())
                .take(MAX_FDS_PER_PID)
            descriptors.forEach { descriptor ->
                val target = runCatching {
                    Files.readSymbolicLink(descriptor.toPath()).toString()
                }.getOrNull() ?: return@forEach
                val inode = socketLink.matchEntire(target)?.groupValues?.getOrNull(1)
                    ?.toLongOrNull()
                    ?: return@forEach
                add(inode)
            }
        }
    }

    private fun listeningPorts(
        table: File,
        socketInodes: Set<Long>,
    ): List<Int> {
        if (!table.exists()) return emptyList()
        return runCatching {
            table.bufferedReader().useLines { lines ->
                lines.drop(1)
                    .take(MAX_NET_TABLE_ROWS)
                    .mapNotNull { line -> parseListeningPort(line, socketInodes) }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun parseListeningPort(
        line: String,
        socketInodes: Set<Long>,
    ): Int? {
        val fields = line.trim().split(Regex("\\s+"))
        if (fields.size < 10 || fields[3].uppercase() != LISTEN_STATE) return null
        val inode = fields[9].toLongOrNull() ?: return null
        if (inode !in socketInodes) return null
        val portHex = fields[1].substringAfterLast(':')
        val port = portHex.toIntOrNull(16) ?: return null
        return port.takeIf { it in 1..65535 }
    }
}
