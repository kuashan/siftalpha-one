package com.siftalpha.studio.runtime

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeWebHttpReadinessProbeTest {

    @Test
    fun tcpListenerWithoutHttpResponseIsNotReady() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                executor.submit {
                    server.accept().use {
                        Thread.sleep(600)
                    }
                }
                assertFalse(
                    RuntimeWebHttpReadinessProbe.isReady(
                        "http://127.0.0.1:${server.localPort}",
                        connectTimeoutMs = 500,
                        readTimeoutMs = 150,
                    ),
                )
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun serverErrorStillProvesHttpReadiness() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                executor.submit {
                    server.accept().use { socket ->
                        val input = socket.getInputStream().bufferedReader()
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        socket.getOutputStream().use { output ->
                            output.write(
                                "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                    .toByteArray(),
                            )
                            output.flush()
                        }
                    }
                }
                assertTrue(
                    RuntimeWebHttpReadinessProbe.isReady(
                        "http://127.0.0.1:${server.localPort}",
                        connectTimeoutMs = 500,
                        readTimeoutMs = 500,
                    ),
                )
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun realHttpResponseIsReady() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                executor.submit {
                    server.accept().use { socket ->
                        val input = socket.getInputStream().bufferedReader()
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        socket.getOutputStream().use { output ->
                            output.write(
                                "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK"
                                    .toByteArray(),
                            )
                            output.flush()
                        }
                    }
                }
                assertTrue(
                    RuntimeWebHttpReadinessProbe.isReady(
                        "http://127.0.0.1:${server.localPort}",
                        connectTimeoutMs = 500,
                        readTimeoutMs = 500,
                    ),
                )
            } finally {
                executor.shutdownNow()
            }
        }
    }
}
