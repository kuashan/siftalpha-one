package com.siftalpha.cloud.core

enum class CloudErrorCode {
    CLOUD_SERVER_UNREACHABLE,
    UNAUTHORIZED,
    PROJECT_NOT_FOUND,
    OPERATION_CONFLICT,
    ENVIRONMENT_NOT_READY,
    ALREADY_RUNNING,
    ALREADY_STOPPED,
    DOCKER_ERROR,
    PREPARE_FAILED,
    RUNTIME_ERROR,
    INVALID_REQUEST,
    INVALID_RESPONSE,
    TIMEOUT,
    UNKNOWN,
}

data class CloudError(
    val code: CloudErrorCode,
    val message: String,
    val httpStatus: Int? = null,
)
