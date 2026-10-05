package com.siftalpha.cloud.agent

import com.siftalpha.cloud.core.CloudError

class CloudAgentException(
    val error: CloudError,
    cause: Throwable? = null,
) : RuntimeException(error.message, cause)

