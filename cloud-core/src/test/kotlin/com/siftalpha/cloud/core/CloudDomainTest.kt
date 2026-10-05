package com.siftalpha.cloud.core

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDomainTest {
    @Test
    fun `cloud state enums preserve the M2-1 vocabulary`() {
        assertEquals(
            listOf("UNKNOWN", "CONNECTING", "CONNECTED", "UNREACHABLE", "UNAUTHORIZED", "ERROR"),
            CloudConnectionState.entries.map { it.name },
        )
        assertEquals(listOf("READY", "NOT_READY", "UNKNOWN"), CloudEnvironmentState.entries.map { it.name })
        assertEquals(listOf("READY", "RUNNING", "STOPPED", "EXITED", "UNKNOWN"), CloudRuntimeState.entries.map { it.name })
        assertEquals(listOf("PREPARE", "START", "STOP"), CloudOperationAction.entries.map { it.name })
        assertEquals(listOf("PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"), CloudOperationStatus.entries.map { it.name })
    }

    @Test
    fun `domain timestamps use Instant and optional fields default safely`() {
        val updatedAt = Instant.parse("2026-09-28T00:00:00Z")
        val result = CloudResult(
            remoteProjectId = "remote-1",
            runtimeState = CloudRuntimeState.STOPPED,
            environmentState = CloudEnvironmentState.READY,
            updatedAt = updatedAt,
        )
        val operation = CloudOperation(
            operationId = "op-1",
            projectId = "remote-1",
            action = CloudOperationAction.START,
            state = CloudOperationStatus.PENDING,
        )
        val artifact = CloudArtifact(name = "output.txt", type = "file")
        val error = CloudError(code = CloudErrorCode.TIMEOUT, message = "timed out")

        assertEquals(updatedAt, result.updatedAt)
        assertNull(result.operationId)
        assertTrue(result.artifacts.isEmpty())
        assertNull(operation.startedAt)
        assertNull(operation.finishedAt)
        assertNull(operation.exitCode)
        assertNull(operation.failureReason)
        assertNull(artifact.url)
        assertNull(artifact.size)
        assertTrue(artifact.metadata.isEmpty())
        assertNull(error.httpStatus)
    }
}

