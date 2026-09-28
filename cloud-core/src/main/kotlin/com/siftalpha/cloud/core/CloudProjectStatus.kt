package com.siftalpha.cloud.core

import java.time.Instant

data class CloudProjectStatus(
    val remoteProjectId: String,
    val environmentState: CloudEnvironmentState,
    val runtimeState: CloudRuntimeState,
    val containerId: String? = null,
    val image: String? = null,
    val exitCode: Int? = null,
    val oomKilled: Boolean? = null,
    val restartCount: Int? = null,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
)
