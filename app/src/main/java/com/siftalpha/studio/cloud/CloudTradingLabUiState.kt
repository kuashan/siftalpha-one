package com.siftalpha.studio.cloud

import com.siftalpha.cloud.core.CloudError
import com.siftalpha.cloud.agent.api.CloudLogs
import com.siftalpha.cloud.core.CloudOperation
import com.siftalpha.cloud.core.CloudProject
import com.siftalpha.cloud.core.CloudProjectStatus
import com.siftalpha.cloud.core.CloudResources

data class CloudTradingLabUiState(
    val loading: Boolean = false,
    val project: CloudProject? = null,
    val status: CloudProjectStatus? = null,
    val resources: CloudResources? = null,
    val logs: CloudLogs? = null,
    val operation: CloudOperation? = null,
    val error: CloudError? = null,
) {
    val runtimeUnits get() = resources?.runtimeUnits ?: status?.runtimeUnits.orEmpty()
}
