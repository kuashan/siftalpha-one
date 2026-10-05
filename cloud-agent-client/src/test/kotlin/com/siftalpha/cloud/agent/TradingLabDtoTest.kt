package com.siftalpha.cloud.agent

import com.siftalpha.cloud.agent.api.M1Json
import com.siftalpha.cloud.agent.transport.CloudHttpRequest
import com.siftalpha.cloud.agent.transport.CloudHttpResponse
import com.siftalpha.cloud.agent.transport.CloudHttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingLabDtoTest {
    @Test
    fun `decodes web metadata runtime units and empty resources`() {
        val project = M1Json.decodeProject(
            """{"projectId":"freqtrade","environmentState":"NOT_READY","runtimeState":"UNKNOWN","displayName":"Freqtrade","group":"trading-lab","runtimeKind":"docker_compose","architecture":"arm64","web":{"scheme":"http","host":"10.77.0.1","port":18080,"path":"/"}}""",
        )
        val empty = M1Json.decodeResources("""{"projectId":"freqtrade","runtimeUnits":[]}""")

        assertEquals("trading-lab", project.group)
        assertEquals(18080, project.web?.port)
        assertTrue(empty.runtimeUnits.isEmpty())
    }

    @Test
    fun `maps one runtime unit and calls bounded resources path`() {
        val transport = OneResponseTransport(
            CloudHttpResponse(
                200,
                emptyMap(),
                """{"projectId":"freqtrade","runtimeUnits":[{"serviceName":"freqtrade","containerId":"abc","image":"freqtradeorg/freqtrade:2026.9","state":"running","health":"healthy","restartCount":0,"exitCode":0,"oomKilled":false,"startedAt":"2026-10-05T00:00:00Z","cpuPercent":1.25,"memoryUsage":"128MiB","memoryLimit":"2GiB"}]}""",
                1,
            ),
        )
        val client = SiftAlphaCloudAgentClient(
            serverId = "server-1",
            credentialStore = object : CloudCredentialStore {
                override fun get(serverId: String) = CloudCredential(serverId, "https://cloud.example", "token")
                override fun save(credential: CloudCredential) = Unit
                override fun delete(serverId: String) = Unit
            },
            transport = transport,
        )

        val resources = client.resources("freqtrade")

        assertEquals(1, resources.runtimeUnits.size)
        assertEquals(1.25, resources.runtimeUnits.single().cpuPercent!!, 0.001)
        assertEquals("https://cloud.example/v1/projects/freqtrade/resources", transport.request.url)
        assertTrue(transport.request.toString().contains("<redacted>"))
    }

    private class OneResponseTransport(private val response: CloudHttpResponse) : CloudHttpTransport {
        lateinit var request: CloudHttpRequest

        override fun execute(request: CloudHttpRequest, timeoutMillis: Long, maxResponseBytes: Int): CloudHttpResponse {
            this.request = request
            return response
        }
    }
}
