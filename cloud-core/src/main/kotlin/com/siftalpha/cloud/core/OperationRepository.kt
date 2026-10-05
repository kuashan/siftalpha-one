package com.siftalpha.cloud.core

interface OperationRepository {
    fun save(operation: CloudOperation)

    fun find(operationId: String): CloudOperation?
}

