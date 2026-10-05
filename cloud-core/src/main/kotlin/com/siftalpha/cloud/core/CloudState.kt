package com.siftalpha.cloud.core

enum class CloudConnectionState {
    UNKNOWN,
    CONNECTING,
    CONNECTED,
    UNREACHABLE,
    UNAUTHORIZED,
    ERROR,
}

enum class CloudEnvironmentState {
    READY,
    NOT_READY,
    UNKNOWN,
}

enum class CloudRuntimeState {
    READY,
    RUNNING,
    STOPPED,
    EXITED,
    UNKNOWN,
}

enum class CloudOperationAction {
    PREPARE,
    START,
    STOP,
}

enum class CloudOperationStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

