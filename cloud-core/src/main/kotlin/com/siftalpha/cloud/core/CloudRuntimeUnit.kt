package com.siftalpha.cloud.core

import java.time.Instant

data class CloudRuntimeUnit(
    val serviceName: String,
    val containerId: String?,
    val image: String?,
    val state: String,
    val health: String? = null,
    val restartCount: Int? = null,
    val exitCode: Int? = null,
    val oomKilled: Boolean? = null,
    val startedAt: Instant? = null,
    val cpuPercent: Double? = null,
    val memoryUsage: String? = null,
    val memoryLimit: String? = null,
)

data class CloudResources(
    val remoteProjectId: String,
    val runtimeUnits: List<CloudRuntimeUnit> = emptyList(),
)
