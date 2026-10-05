package com.siftalpha.cloud.core

import java.time.Instant

data class CloudOperation(
    val operationId: String,
    val projectId: String,
    val action: CloudOperationAction,
    val state: CloudOperationStatus,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val exitCode: Int? = null,
    val failureReason: String? = null,
)

