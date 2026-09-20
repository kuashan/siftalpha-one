package com.siftalpha.studio.runtime

import android.content.Context
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object ResultWebHost {

    private val lock = Any()
    private var server: ServerSocket? = null
    private var appContext: Context? = null
    private val clients = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "SiftAlphaResultWebClient").apply { isDaemon = true }
    }

    fun ensureStarted(context: Context): Int = synchronized(lock) {
        val existing = server
        if (existing != null && !existing.isClosed) return@synchronized existing.localPort

        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(
            InetSocketAddress(
                InetAddress.getByName(LOOPBACK_BIND_HOST),
                0,
            ),
            BACKLOG,
        )
        appContext = context.applicationContext
        server = socket
        Thread(
            { acceptLoop(socket) },
            "SiftAlphaResultWebHost",
        ).apply {
            isDaemon = true
            start()
        }
        socket.localPort
    }

    fun urlFor(context: Context, resultId: String): String {
        require(SAFE_ID.matches(resultId)) { "invalid result id" }
        val port = ensureStarted(context)
        return "http://" + LOOPBACK_URL_HOST + ":" + port + "/result/" + resultId + "/"
    }

    fun indexUrl(context: Context): String =
        "http://" + LOOPBACK_URL_HOST + ":" + ensureStarted(context) + "/"

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            clients.execute {
                runCatching { handle(client) }
                runCatching { client.close() }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))
        val requestLine = reader.readLine()?.trim().orEmpty()
        if (requestLine.isBlank()) return

        var headerCount = 0
        while (headerCount < MAX_HEADERS) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            headerCount += 1
        }

        val parts = requestLine.split(' ')
        if (parts.size < 2) {
            respond(socket, 400, "Bad Request", "text/plain; charset=utf-8", "Bad Request")
            return
        }
        val method = parts[0]
        val rawPath = parts[1].substringBefore('?')
        if (method != "GET" && method != "HEAD") {
            respond(socket, 405, "Method Not Allowed", "text/plain; charset=utf-8", "Method Not Allowed", headOnly = method == "HEAD")
            return
        }

        val context = appContext
        if (context == null) {
            respond(socket, 503, "Service Unavailable", "text/plain; charset=utf-8", "Result host unavailable", headOnly = method == "HEAD")
            return
        }

        when {
            rawPath == "/" -> {
                val html = renderIndex(ResultWebStore(context).listRecent())
                respond(socket, 200, "OK", "text/html; charset=utf-8", html, headOnly = method == "HEAD")
            }
            rawPath == "/health" -> {
                respond(socket, 200, "OK", "text/plain; charset=utf-8", "OK", headOnly = method == "HEAD")
            }
            rawPath.startsWith("/result/") -> {
                val id = rawPath.removePrefix("/result/").trim('/')
                if (!SAFE_ID.matches(id)) {
                    respond(socket, 404, "Not Found", "text/plain; charset=utf-8", "Not Found", headOnly = method == "HEAD")
                    return
                }
                val html = ResultWebStore(context).readHtml(id)
                if (html == null) {
                    respond(socket, 404, "Not Found", "text/plain; charset=utf-8", "Result not found", headOnly = method == "HEAD")
                } else {
                    respond(socket, 200, "OK", "text/html; charset=utf-8", html, headOnly = method == "HEAD")
                }
            }
            else -> respond(socket, 404, "Not Found", "text/plain; charset=utf-8", "Not Found", headOnly = method == "HEAD")
        }
    }

    private fun respond(
        socket: Socket,
        code: Int,
        reason: String,
        contentType: String,
        body: String,
        headOnly: Boolean = false,
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1))
        writer.write("HTTP/1.1 " + code + " " + reason + "\r\n")
        writer.write("Content-Type: " + contentType + "\r\n")
        writer.write("Content-Length: " + bytes.size + "\r\n")
        writer.write("Cache-Control: no-store\r\n")
        writer.write("X-Content-Type-Options: nosniff\r\n")
        writer.write("X-Frame-Options: DENY\r\n")
        writer.write("Referrer-Policy: no-referrer\r\n")
        writer.write("Cross-Origin-Resource-Policy: same-origin\r\n")
        writer.write("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'\r\n")
        writer.write("Connection: close\r\n")
        writer.write("\r\n")
        writer.flush()
        if (!headOnly && bytes.isNotEmpty()) {
            socket.getOutputStream().write(bytes)
            socket.getOutputStream().flush()
        }
    }

    private fun renderIndex(results: List<ResultWebStore.ResultRef>): String {
        val cards = if (results.isEmpty()) {
            "<p class=\"empty\">No local results yet.</p>"
        } else {
            results.joinToString("") { result ->
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(result.createdAtEpochMs))
                "<a class=\"card\" href=\"/result/" + result.id + "/\">" +
                    "<strong>" + escape(result.projectName) + "</strong>" +
                    "<span>" + escape(result.summary) + "</span>" +
                    "<small>" + escape(time) + "</small></a>"
            }
        }
        return """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>SiftAlpha Results</title>
<style>
:root{color-scheme:dark;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Noto Sans SC",sans-serif;background:#0f1217;color:#eef3f8}
body{margin:0}.page{max-width:900px;margin:auto;padding:20px 14px 40px}h1{font-size:25px}.sub{color:#9aabba}.grid{display:grid;gap:10px}
.card{display:flex;flex-direction:column;gap:5px;padding:14px;background:#181d25;border:1px solid #303844;border-radius:12px;text-decoration:none;color:#eef3f8}
.card:hover{border-color:#557089}.card span{color:#b8c7d7}.card small{color:#7f8d9d}.empty{color:#9aabba}
</style></head><body><main class="page"><h1>SiftAlpha Results</h1><p class="sub">Local-only result pages served from 127.0.0.1.</p><div class="grid">__CARDS__</div></main></body></html>"""
            .replace("__CARDS__", cards)
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    private const val LOOPBACK_BIND_HOST = "127.0.0.1"
    private const val LOOPBACK_URL_HOST = "localhost"
    private const val BACKLOG = 16
    private const val SOCKET_TIMEOUT_MS = 4_000
    private const val MAX_HEADERS = 64
    private val SAFE_ID = Regex("^[a-z0-9-]{8,80}$")
}
