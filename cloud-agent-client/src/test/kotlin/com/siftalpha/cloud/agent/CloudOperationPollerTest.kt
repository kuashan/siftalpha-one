package com.siftalpha.cloud.agent

import com.siftalpha.cloud.core.CloudOperation
import com.siftalpha.cloud.core.CloudOperationAction
import com.siftalpha.cloud.core.CloudOperationStatus
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudOperationPollerTest {
    @Test
    fun `poller returns every terminal operation state`() {
        listOf(
            CloudOperationStatus.SUCCEEDED,
            CloudOperationStatus.FAILED,
            CloudOperationStatus.CANCELLED,
        ).forEach { terminalState ->
            val clock = FakeClock()
            val operation = operation(terminalState)
            val poller = CloudOperationPoller(
                operationLookup = { operation },
                clock = clock,
                delay = FakeDelay(clock),
            )

            val result = poller.poll(
                operationId = operation.operationId,
                config = CloudPollingConfig(intervalMillis = 10, maxDurationMillis = 100),
            )

            assertEquals(terminalState, result.state)
        }
    }

    @Test
    fun `poller times out instead of looping forever`() {
        val clock = FakeClock()
        val poller = CloudOperationPoller(
            operationLookup = { operation(CloudOperationStatus.RUNNING) },
            clock = clock,
            delay = FakeDelay(clock),
        )

        val thrown = runCatching {
            poller.poll(
                operationId = "op-1",
                config = CloudPollingConfig(intervalMillis = 10, maxDurationMillis = 25),
            )
        }.exceptionOrNull()

        assertTrue(thrown is CloudAgentException)
        assertEquals(com.siftalpha.cloud.core.CloudErrorCode.TIMEOUT, (thrown as CloudAgentException).error.code)
    }

    @Test
    fun `poller cancellation throws CancellationException`() {
        val clock = FakeClock()
        var cancelled = false
        val poller = CloudOperationPoller(
            operationLookup = { operation(CloudOperationStatus.RUNNING) },
            clock = clock,
            delay = object : CloudDelay {
                override fun sleep(millis: Long) {
                    clock.now += millis
                    cancelled = true
                }
            },
        )

        val thrown = runCatching {
            poller.poll(
                operationId = "op-1",
                config = CloudPollingConfig(intervalMillis = 10, maxDurationMillis = 100),
                isCancelled = { cancelled },
            )
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun `polling configuration requires a positive interval and deadline`() {
        assertTrue(runCatching { CloudPollingConfig(intervalMillis = 0, maxDurationMillis = 10) }.isFailure)
        assertTrue(runCatching { CloudPollingConfig(intervalMillis = 10, maxDurationMillis = 0) }.isFailure)
    }

    private fun operation(state: CloudOperationStatus) = CloudOperation(
        operationId = "op-1",
        projectId = "project-1",
        action = CloudOperationAction.START,
        state = state,
    )

    private class FakeClock(var now: Long = 0) : CloudClock {
        override fun nowMillis(): Long = now
    }

    private class FakeDelay(private val clock: FakeClock) : CloudDelay {
        override fun sleep(millis: Long) {
            clock.now += millis
        }
    }
}
