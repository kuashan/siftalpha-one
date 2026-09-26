package com.siftalpha.macos

import com.siftalpha.core.process.ProjectProcessScope
import com.siftalpha.studio.runtime.RuntimeWebEndpointProbe
import com.siftalpha.studio.runtime.RuntimeWebUrl
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class MacProjectWebEndpoint(
    val url: String,
    val source: Source,
) {
    enum class Source {
        LOG_OUTPUT,
        PROJECT_PROCESS_PORT,
        CONTAINER_PORT,
    }
}

internal object MacHttpWebSurfaceProbe {
    fun isHtml(url: String, timeoutMs: Int = 800): Boolean = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..399) return@runCatching false
            val contentType = connection.contentType.orEmpty().lowercase()
            if ("text/html" in contentType || "application/xhtml+xml" in contentType) {
                return@runCatching true
            }
            val stream = connection.inputStream ?: return@runCatching false
            val bytes = stream.use { input ->
                val buffer = ByteArray(32 * 1024)
                val count = input.read(buffer)
                if (count <= 0) ByteArray(0) else buffer.copyOf(count)
            }
            val prefix = bytes.toString(Charsets.UTF_8).lowercase()
            "<!doctype html" in prefix || "<html" in prefix || "<title" in prefix
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)
}

class MacProjectWebDiscovery(
    private val processControl: MacProjectProcessControl,
    private val listeningProbe: (String) -> Boolean = {
        RuntimeWebEndpointProbe.isListening(it, timeoutMs = 500)
    },
    private val webSurfaceProbe: (String) -> Boolean = MacHttpWebSurfaceProbe::isHtml,
) {
    fun discoverFromPorts(
        ports: Collection<Int>,
        combinedOutput: String,
    ): MacProjectWebEndpoint? {
        RuntimeWebUrl.extractLocalHttpUrl(combinedOutput)?.let { candidate ->
            if (listeningProbe(candidate)) {
                return MacProjectWebEndpoint(candidate, MacProjectWebEndpoint.Source.LOG_OUTPUT)
            }
        }
        ports.asSequence()
            .filter { it in 1..65535 }
            .distinct()
            .sorted()
            .forEach { port ->
                val candidate = "http://127.0.0.1:" + port
                if (listeningProbe(candidate) && webSurfaceProbe(candidate)) {
                    return MacProjectWebEndpoint(candidate, MacProjectWebEndpoint.Source.CONTAINER_PORT)
                }
            }
        return null
    }

    fun discover(scope: ProjectProcessScope, combinedOutput: String): MacProjectWebEndpoint? {
        RuntimeWebUrl.extractLocalHttpUrl(combinedOutput)?.let { candidate ->
            if (listeningProbe(candidate)) {
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
            if (listeningProbe(candidate) && webSurfaceProbe(candidate)) {
                return MacProjectWebEndpoint(candidate, MacProjectWebEndpoint.Source.PROJECT_PROCESS_PORT)
            }
        }
        return null
    }
}
