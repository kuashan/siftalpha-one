package com.siftalpha.cloud.agent

import com.siftalpha.cloud.agent.api.M1ErrorEnvelopeDto
import com.siftalpha.cloud.agent.api.M1Json
import com.siftalpha.cloud.agent.api.M1OperationDto
import com.siftalpha.cloud.agent.api.M1ProjectDto
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M1JsonTest {
    @Test
    fun `health and project JSON use the audited M1 field names`() {
        val health = M1Json.decodeHealth(
            """
            {
              "status": "ok",
              "agent": "0.1.0",
              "docker": "ok",
              "wireguardBinding": "10.77.0.1",
              "unknownFutureField": true
            }
            """.trimIndent(),
        )
        val project = M1Json.decodeProject(
            """
            {
              "projectId": "daily-stock-analysis",
              "environmentState": "READY",
              "runtimeState": "STOPPED",
              "image": "siftalpha/daily-stock-analysis:m0-d3fee51"
            }
            """.trimIndent(),
        )

        assertEquals("ok", health.status)
        assertEquals("0.1.0", health.agent)
        assertEquals("10.77.0.1", health.wireguardBinding)
        assertEquals("daily-stock-analysis", project.projectId)
        assertEquals("READY", project.environmentState)
        assertEquals("STOPPED", project.runtimeState)
        assertEquals("siftalpha/daily-stock-analysis:m0-d3fee51", project.image)
    }

    @Test
    fun `operation JSON preserves nullable result and wire timestamps`() {
        val operation = M1Json.decodeOperation(
            """
            {
              "operationId": "op-1",
              "projectId": "daily-stock-analysis",
              "action": "START",
              "state": "SUCCEEDED",
              "startedAt": "2026-09-28T12:34:56.123456+00:00",
              "finishedAt": null,
              "exitCode": 0,
              "failureReason": null,
              "result": null
            }
            """.trimIndent(),
        )

        assertEquals("op-1", operation.operationId)
        assertEquals("START", operation.action)
        assertEquals("SUCCEEDED", operation.state)
        assertEquals("2026-09-28T12:34:56.123456+00:00", operation.startedAt)
        assertNull(operation.finishedAt)
        assertNull(operation.result)
    }

    @Test
    fun `status logs and error envelope decode their exact contract fields`() {
        val status = M1Json.decodeStatus(
            """
            {
              "projectId": "daily-stock-analysis",
              "environmentState": "READY",
              "runtimeState": "STOPPED",
              "containerId": null,
              "image": "image:tag",
              "exitCode": null,
              "oomKilled": null,
              "restartCount": null,
              "startedAt": null,
              "finishedAt": null
            }
            """.trimIndent(),
        )
        val logs = M1Json.decodeLogs(
            """
            {"projectId":"daily-stock-analysis","lines":["line-1"],"bytes":7,"truncated":false}
            """.trimIndent(),
        )
        val error = M1Json.decodeError(
            """{"error":{"code":"PROJECT_NOT_FOUND","message":"missing"}}""",
        )

        assertEquals("daily-stock-analysis", status.projectId)
        assertEquals("STOPPED", status.runtimeState)
        assertEquals(listOf("line-1"), logs.lines)
        assertEquals(7L, logs.bytes)
        assertTrue(!logs.truncated)
        assertEquals("PROJECT_NOT_FOUND", error.error.code)
        assertEquals("missing", error.error.message)
    }

    @Test
    fun `operation mapping converts non-null wire timestamps to Instant`() {
        val dto = M1OperationDto(
            operationId = "op-1",
            projectId = "project-1",
            action = "STOP",
            state = "FAILED",
            startedAt = "2026-09-28T12:34:56+00:00",
            finishedAt = "2026-09-28T12:35:00Z",
            exitCode = 1,
            failureReason = "docker failed",
        )
        val operation = CloudDtoMapper.toCloudOperation(dto)

        assertEquals(Instant.parse("2026-09-28T12:34:56Z"), operation.startedAt)
        assertEquals(Instant.parse("2026-09-28T12:35:00Z"), operation.finishedAt)
        assertEquals(1, operation.exitCode)
    }

    @Test
    fun `invalid project state cannot be silently mapped`() {
        val dto = M1ProjectDto(
            projectId = "project-1",
            environmentState = "MAYBE",
            runtimeState = "STOPPED",
        )

        val thrown = runCatching { CloudDtoMapper.toCloudProject(dto, serverId = "server-1") }.exceptionOrNull()

        assertTrue(thrown is CloudAgentException)
        assertEquals(com.siftalpha.cloud.core.CloudErrorCode.INVALID_RESPONSE, (thrown as CloudAgentException).error.code)
    }
}

