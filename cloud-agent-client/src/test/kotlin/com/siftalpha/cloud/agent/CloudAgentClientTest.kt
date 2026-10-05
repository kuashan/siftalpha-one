package com.siftalpha.cloud.agent

import com.siftalpha.cloud.agent.transport.CloudHttpRequest
import com.siftalpha.cloud.agent.transport.CloudHttpResponse
import com.siftalpha.cloud.agent.transport.CloudHttpTransport
import com.siftalpha.cloud.agent.transport.CloudTransportException
import com.siftalpha.cloud.agent.transport.CloudTransportFailure
import com.siftalpha.cloud.core.CloudErrorCode
import com.siftalpha.cloud.core.CloudOperationAction
import com.siftalpha.cloud.core.CloudOperationStatus
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAgentClientTest {
    @Test
    fun `health sends bearer token and maps response`() {
        val transport = QueueTransport(
            CloudHttpResponse(
                statusCode = 200,
                headers = emptyMap(),
                body = """{"status":"ok","agent":"0.1.0","docker":"ok","wireguardBinding":"10.77.0.1"}""",
                elapsedMillis = 4,
            ),
        )
        val client = client(transport)

        val health = client.health()

        assertEquals("ok", health.status)
        assertEquals("https://cloud.example/v1/health", transport.lastRequest?.url)
        assertEquals("Bearer test-token", transport.lastRequest?.headers?.get("Authorization"))
        assertEquals(1_048_576, transport.lastMaxResponseBytes)
    }

    @Test
    fun `projects status and logs map to domain models`() {
        val transport = QueueTransport(
            CloudHttpResponse(
                statusCode = 200,
                headers = emptyMap(),
                body = """{"projects":[{"projectId":"project-1","environmentState":"READY","runtimeState":"STOPPED","image":"image:tag"}]}""",
                elapsedMillis = 1,
            ),
            CloudHttpResponse(
                statusCode = 200,
                headers = emptyMap(),
                body = """{"projectId":"project-1","environmentState":"READY","runtimeState":"STOPPED","containerId":null,"image":"image:tag","exitCode":null,"oomKilled":null,"restartCount":null,"startedAt":null,"finishedAt":null}""",
                elapsedMillis = 1,
            ),
            CloudHttpResponse(
                statusCode = 200,
                headers = emptyMap(),
                body = """{"projectId":"project-1","lines":["a","b"],"bytes":3,"truncated":false}""",
                elapsedMillis = 1,
            ),
        )
        val client = client(transport)

        val projects = client.listProjects()
        val status = client.getStatus("project-1")
        val logs = client.logs("project-1", tail = 2)

        assertEquals("project-1", projects.single().remoteProjectId)
        assertEquals("server-1", projects.single().serverId)
        assertEquals("STOPPED", projects.single().runtimeState.name)
        assertEquals("project-1", status.remoteProjectId)
        assertEquals(listOf("a", "b"), logs.lines)
        assertTrue(transport.requests[2].url.endsWith("/v1/projects/project-1/logs?tail=2"))
    }

    @Test
    fun `mutation returns operation without polling`() {
        val transport = QueueTransport(
            CloudHttpResponse(
                statusCode = 202,
                headers = emptyMap(),
                body = """{"operationId":"op-1","projectId":"project-1","action":"START","state":"PENDING","startedAt":null,"finishedAt":null,"exitCode":null,"failureReason":null,"result":null}""",
                elapsedMillis = 1,
            ),
        )

        val operation = client(transport).start("project-1")

        assertEquals("op-1", operation.operationId)
        assertEquals(CloudOperationAction.START, operation.action)
        assertEquals(CloudOperationStatus.PENDING, operation.state)
        assertEquals("POST", transport.lastRequest?.method)
        assertEquals("https://cloud.example/v1/projects/project-1/start", transport.lastRequest?.url)
    }

    @Test
    fun `invalid tail is rejected before transport`() {
        val transport = QueueTransport()
        val thrown = runCatching { client(transport).logs("project-1", tail = 1001) }.exceptionOrNull()

        assertTrue(thrown is CloudAgentException)
        assertEquals(CloudErrorCode.INVALID_REQUEST, (thrown as CloudAgentException).error.code)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `connection and timeout remain typed transport errors`() {
        val unreachable = runCatching {
            client(QueueTransport(failure = CloudTransportException(CloudTransportFailure.UNREACHABLE, "offline"))).health()
        }.exceptionOrNull()
        val timeout = runCatching {
            client(QueueTransport(failure = CloudTransportException(CloudTransportFailure.TIMEOUT, "timed out"))).health()
        }.exceptionOrNull()

        assertEquals(CloudErrorCode.CLOUD_SERVER_UNREACHABLE, (unreachable as CloudAgentException).error.code)
        assertEquals(CloudErrorCode.TIMEOUT, (timeout as CloudAgentException).error.code)
        assertFalse(unreachable.message.orEmpty().contains("test-token"))
        assertFalse(timeout.message.orEmpty().contains("test-token"))
    }

    @Test
    fun `invalid JSON and oversized bodies become invalid response`() {
        val invalidJson = runCatching {
            client(
                QueueTransport(
                    CloudHttpResponse(200, emptyMap(), "{not-json", 1),
                ),
            ).health()
        }.exceptionOrNull()
        val oversized = runCatching {
            client(
                QueueTransport(
                    CloudHttpResponse(200, emptyMap(), "1234567890", 1),
                ),
                maxResponseBytes = 4,
            ).health()
        }.exceptionOrNull()

        assertEquals(CloudErrorCode.INVALID_RESPONSE, (invalidJson as CloudAgentException).error.code)
        assertEquals(CloudErrorCode.INVALID_RESPONSE, (oversized as CloudAgentException).error.code)
    }

    @Test
    fun `request string redacts authorization`() {
        val request = CloudHttpRequest(
            method = "GET",
            url = "https://cloud.example/v1/health",
            headers = mapOf("Authorization" to "Bearer test-token"),
        )

        assertTrue(request.toString().contains("<redacted>"))
        assertFalse(request.toString().contains("test-token"))
    }

    @Test
    fun `error responses map status and envelope without leaking authorization`() {
        val cases = listOf(
            401 to "UNAUTHORIZED",
            404 to "PROJECT_NOT_FOUND",
            409 to "OPERATION_CONFLICT",
            500 to "DOCKER_ERROR",
        )

        cases.forEach { (statusCode, code) ->
            val thrown = runCatching {
                client(
                    QueueTransport(
                        CloudHttpResponse(
                            statusCode = statusCode,
                            headers = emptyMap(),
                            body = """{"error":{"code":"$code","message":"safe message"}}""",
                            elapsedMillis = 1,
                        ),
                    ),
                ).getProject("project-1")
            }.exceptionOrNull() as CloudAgentException

            assertEquals(code, thrown.error.code.name)
            assertEquals(statusCode, thrown.error.httpStatus)
            assertFalse(thrown.toString().contains("test-token"))
        }
    }

    private fun client(
        transport: QueueTransport,
        maxResponseBytes: Int = 1_048_576,
    ): SiftAlphaCloudAgentClient = SiftAlphaCloudAgentClient(
        serverId = "server-1",
        credentialStore = InMemoryCredentialStore(),
        transport = transport,
        timeoutMillis = 100,
        maxResponseBytes = maxResponseBytes,
    )

    private class InMemoryCredentialStore : CloudCredentialStore {
        override fun get(serverId: String): CloudCredential? = CloudCredential(
            serverId = serverId,
            baseUrl = "https://cloud.example/",
            bearerToken = "test-token",
        )

        override fun save(credential: CloudCredential) = Unit

        override fun delete(serverId: String) = Unit
    }

    private class QueueTransport(
        vararg responses: CloudHttpResponse,
        private val failure: RuntimeException? = null,
    ) : CloudHttpTransport {
        private val responseQueue = ArrayDeque(responses.toList())
        val requests = mutableListOf<CloudHttpRequest>()
        var lastRequest: CloudHttpRequest? = null
        var lastMaxResponseBytes: Int? = null

        override fun execute(request: CloudHttpRequest, timeoutMillis: Long, maxResponseBytes: Int): CloudHttpResponse {
            requests += request
            lastRequest = request
            lastMaxResponseBytes = maxResponseBytes
            failure?.let { throw it }
            if (responseQueue.isEmpty()) {
                error("No fake response configured")
            }
            return responseQueue.removeFirst()
        }
    }
}

