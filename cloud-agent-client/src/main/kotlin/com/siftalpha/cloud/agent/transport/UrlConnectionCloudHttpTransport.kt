package com.siftalpha.cloud.agent.transport

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets

class UrlConnectionCloudHttpTransport : CloudHttpTransport {
    override fun execute(
        request: CloudHttpRequest,
        timeoutMillis: Long,
        maxResponseBytes: Int,
    ): CloudHttpResponse {
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        require(maxResponseBytes > 0) { "maxResponseBytes must be positive" }

        val startedAt = System.nanoTime()
        val connection = try {
            (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                readTimeout = timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                useCaches = false
                doInput = true
                request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
                if (request.body != null) {
                    doOutput = true
                    if (request.getHeader("Content-Type") == null) {
                        setRequestProperty("Content-Type", "application/json")
                    }
                }
            }
        } catch (error: IllegalArgumentException) {
            throw CloudTransportException(CloudTransportFailure.UNREACHABLE, "Cloud URL is invalid", error)
        }

        return try {
            request.body?.let { body ->
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(StandardCharsets.UTF_8))
                }
            }
            val statusCode = connection.responseCode
            val input = if (statusCode >= 400) connection.errorStream else connection.inputStream
            val body = input?.use { readBounded(it, maxResponseBytes) } ?: ""
            val headers = connection.headerFields.entries
                .filter { it.key != null }
                .associate { entry ->
                    entry.key!! to (entry.value ?: emptyList()).joinToString(",")
                }
            CloudHttpResponse(
                statusCode = statusCode,
                headers = headers,
                body = body,
                elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000,
            )
        } catch (error: CloudTransportException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw CloudTransportException(CloudTransportFailure.TIMEOUT, "Cloud request timed out", error)
        } catch (error: ConnectException) {
            throw CloudTransportException(CloudTransportFailure.UNREACHABLE, "Cloud server is unreachable", error)
        } catch (error: UnknownHostException) {
            throw CloudTransportException(CloudTransportFailure.UNREACHABLE, "Cloud server is unreachable", error)
        } catch (error: NoRouteToHostException) {
            throw CloudTransportException(CloudTransportFailure.UNREACHABLE, "Cloud server is unreachable", error)
        } catch (error: IOException) {
            throw CloudTransportException(CloudTransportFailure.UNREACHABLE, "Cloud server is unreachable", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream, maxResponseBytes: Int): String {
        val output = ByteArrayOutputStream(minOf(maxResponseBytes, 8_192))
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxResponseBytes) {
                throw CloudTransportException(
                    CloudTransportFailure.RESPONSE_TOO_LARGE,
                    "Cloud response exceeded the configured limit",
                )
            }
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private fun CloudHttpRequest.getHeader(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}
