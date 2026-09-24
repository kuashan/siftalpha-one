package com.siftalpha.macos

import com.siftalpha.core.process.ProjectProcessScope
import com.siftalpha.studio.runtime.RuntimeWebEndpointProbe
import com.siftalpha.studio.runtime.RuntimeWebUrl
import java.io.File
import java.util.concurrent.TimeUnit

data class MacProjectWebEndpoint(
    val url: String,
    val source: Source,
) {
    enum class Source {
        LOG_OUTPUT,
        PROJECT_PROCESS_PORT,
    }
}

class MacProjectWebDiscovery(
    private val processControl: MacProjectProcessControl,
) {
    fun discover(scope: ProjectProcessScope, combinedOutput: String): MacProjectWebEndpoint? {
        RuntimeWebUrl.extractLocalHttpUrl(combinedOutput)?.let { candidate ->
            if (RuntimeWebEndpointProbe.isListening(candidate, timeoutMs = 500)) {
                return MacProjectWebEndpoint(candidate, MacProjectWebEndpoint.Source.LOG_OUTPUT)
            }
        }

        val pids = processControl.ownedPids(scope)
        if (pids.isEmpty()) return null
        val lsof = listOf("/usr/sbin/lsof", "/usr/bin/lsof")
            .map(::File)
            .firstOrNull { it.isFile && it.canExecute() }
            ?: return null

        val ports = linkedSetOf<Int>()
        pids.forEach { pid ->
            val output = runCatching {
                val process = ProcessBuilder(
                    lsof.absolutePath,
                    "-Pan",
                    "-p",
                    pid.toString(),
                    "-iTCP",
                    "-sTCP:LISTEN",
                ).redirectErrorStream(true).start()
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return@runCatching ""
                }
                process.inputStream.bufferedReader().use { it.readText().take(128 * 1024) }
            }.getOrDefault("")

            Regex("""TCP\s+\S+:(\d+)\s+\(LISTEN\)""")
                .findAll(output)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .filter { it in 1..65535 }
                .forEach(ports::add)
        }

        ports.sorted().forEach { port ->
            val candidate = "http://127.0.0.1:" + port
            if (RuntimeWebEndpointProbe.isListening(candidate, timeoutMs = 500)) {
                return MacProjectWebEndpoint(
                    candidate,
                    MacProjectWebEndpoint.Source.PROJECT_PROCESS_PORT,
                )
            }
        }
        return null
    }
}
