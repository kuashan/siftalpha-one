package com.siftalpha.cloud.core

import java.time.Instant

data class CloudProject(
    val displayName: String,
    val serverId: String,
    val remoteProjectId: String,
    val environmentState: CloudEnvironmentState,
    val runtimeState: CloudRuntimeState,
    val localProjectId: String? = null,
    val description: String? = null,
    val source: String? = null,
    val runtimeKind: String? = null,
    val lastOperationId: String? = null,
    val result: CloudResult? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)
