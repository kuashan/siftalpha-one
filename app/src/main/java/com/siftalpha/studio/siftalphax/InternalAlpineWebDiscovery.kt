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
data class InternalAlpineWebObservation(
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
        preferredHints: Collection<Int> = emptyList(),
    ): InternalAlpineWebObservation {
        if (rootPid <= 0) return InternalAlpineWebObservation.empty()
        val projectPids = linkedSetOf(rootPid).apply {
            addAll(InternalAlpineProcessControl.descendantPids(procRoot, rootPid.toLong()))
        }.take(MAX_PROJECT_PIDS)
        ownedHintPort(procRoot, projectPids, preferredHints)?.let { port ->
            return InternalAlpineWebObservation(
                projectPidCount = projectPids.size,
                socketInodeCount = 1,
                ports = listOf(port),
            )
        }
        return observePids(procRoot, projectPids, preferredHints)
    }

    internal fun observePids(
        procRoot: File,
        projectPids: Collection<Int>,
        preferredHints: Collection<Int> = emptyList(),
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
            ports = RuntimeWebPortDiscovery.rankCandidates(ports, preferredHints),
        )
    }

    /**
     * Hint fast path: inspect only hinted LISTEN ports first, then prove that the matching socket
     * inode is owned by the current project PID tree. A reachable port from another project cannot
     * pass this check.
     */
    private fun ownedHintPort(
        procRoot: File,
        projectPids: Collection<Int>,
        preferredHints: Collection<Int>,
    ): Int? {
        val hints = preferredHints.filter { it in 1..65535 }.distinct()
        if (hints.isEmpty() || projectPids.isEmpty()) return null
        val representativePid = projectPids.firstOrNull { it > 0 } ?: return null
        val tables = listOf(
            File(procRoot, "net/tcp"),
            File(procRoot, "net/tcp6"),
            File(procRoot, "$representativePid/net/tcp"),
            File(procRoot, "$representativePid/net/tcp6"),
        ).distinctBy { it.absolutePath }

        val inodePorts = buildMap<Long, Int> {
            tables.forEach { table ->
                hintedListeningInodes(table, hints).forEach { (inode, port) ->
                    val existing = get(inode)
                    if (
                        existing == null ||
                        hints.indexOf(port) < hints.indexOf(existing)
                    ) {
                        put(inode, port)
                    }
                }
            }
        }
        if (inodePorts.isEmpty()) return null

        var bestIndex = Int.MAX_VALUE
        projectPids
            .asSequence()
            .filter { it > 0 }
            .distinct()
            .take(MAX_PROJECT_PIDS)
            .forEach { pid ->
                val fdDirectory = File(procRoot, "$pid/fd")
                val descriptors = runCatching { fdDirectory.listFiles().orEmpty() }
                    .getOrDefault(emptyArray())
                    .take(MAX_FDS_PER_PID)
                descriptors.forEach { descriptor ->
                    val target = runCatching {
                        Files.readSymbolicLink(descriptor.toPath()).toString()
                    }.getOrNull() ?: return@forEach
                    val inode = socketLink.matchEntire(target)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toLongOrNull()
                        ?: return@forEach
                    val port = inodePorts[inode] ?: return@forEach
                    val index = hints.indexOf(port)
                    if (index >= 0 && index < bestIndex) bestIndex = index
                    if (bestIndex == 0) return hints[0]
                }
            }
        return hints.getOrNull(bestIndex)
    }

    private fun hintedListeningInodes(
        table: File,
        hints: List<Int>,
    ): List<Pair<Long, Int>> {
        if (!table.exists()) return emptyList()
        val hintSet = hints.toSet()
        return runCatching {
            table.bufferedReader().useLines { lines ->
                lines.drop(1)
                    .take(MAX_NET_TABLE_ROWS)
                    .mapNotNull { line ->
                        val fields = line.trim().split(Regex("\\s+"))
                        if (fields.size < 10 || fields[3].uppercase() != LISTEN_STATE) {
                            return@mapNotNull null
                        }
                        val portHex = fields[1].substringAfterLast(':')
                        val port = portHex.toIntOrNull(16) ?: return@mapNotNull null
                        if (port !in hintSet) return@mapNotNull null
                        val inode = fields[9].toLongOrNull() ?: return@mapNotNull null
                        inode to port
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())
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
