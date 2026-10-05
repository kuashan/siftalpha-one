package com.siftalpha.cloud.agent

import com.siftalpha.cloud.core.CloudError
import com.siftalpha.cloud.core.CloudErrorCode
import com.siftalpha.cloud.core.CloudOperation
import com.siftalpha.cloud.core.CloudOperationStatus
import java.util.concurrent.CancellationException

data class CloudPollingConfig(
    val intervalMillis: Long,
    val maxDurationMillis: Long,
) {
    init {
        require(intervalMillis > 0) { "intervalMillis must be positive" }
        require(maxDurationMillis > 0) { "maxDurationMillis must be positive" }
    }
}

interface CloudClock {
    fun nowMillis(): Long
}

interface CloudDelay {
    fun sleep(millis: Long)
}

class CloudOperationPoller(
    private val operationLookup: (String) -> CloudOperation,
    private val clock: CloudClock,
    private val delay: CloudDelay,
) {
    fun poll(
        operationId: String,
        config: CloudPollingConfig,
        isCancelled: () -> Boolean = { false },
    ): CloudOperation {
        val startedAt = clock.nowMillis()
        val deadline = if (Long.MAX_VALUE - startedAt < config.maxDurationMillis) {
            Long.MAX_VALUE
        } else {
            startedAt + config.maxDurationMillis
        }

        while (true) {
            if (isCancelled()) throw CancellationException("Cloud operation polling was cancelled")
            if (clock.nowMillis() >= deadline) throw timeout()

            val operation = operationLookup(operationId)
            if (operation.state.isTerminal()) return operation

            if (isCancelled()) throw CancellationException("Cloud operation polling was cancelled")
            if (clock.nowMillis() >= deadline) throw timeout()
            delay.sleep(config.intervalMillis)
        }
    }

    private fun timeout(): CloudAgentException = CloudAgentException(
        CloudError(CloudErrorCode.TIMEOUT, "Cloud operation polling timed out"),
    )

    private fun CloudOperationStatus.isTerminal(): Boolean = when (this) {
        CloudOperationStatus.SUCCEEDED,
        CloudOperationStatus.FAILED,
        CloudOperationStatus.CANCELLED,
        -> true

        CloudOperationStatus.PENDING,
        CloudOperationStatus.RUNNING,
        -> false
    }
}

