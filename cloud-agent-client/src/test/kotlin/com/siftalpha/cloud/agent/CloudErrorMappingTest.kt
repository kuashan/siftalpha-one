package com.siftalpha.cloud.agent

import com.siftalpha.cloud.core.CloudErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudErrorMappingTest {
    @Test
    fun `known M1 error codes map to the shared cloud vocabulary`() {
        val codes = listOf(
            "UNAUTHORIZED" to CloudErrorCode.UNAUTHORIZED,
            "PROJECT_NOT_FOUND" to CloudErrorCode.PROJECT_NOT_FOUND,
            "OPERATION_CONFLICT" to CloudErrorCode.OPERATION_CONFLICT,
            "INVALID_REQUEST" to CloudErrorCode.INVALID_REQUEST,
            "DOCKER_ERROR" to CloudErrorCode.DOCKER_ERROR,
        )

        codes.forEach { (wireCode, expected) ->
            assertEquals(expected, CloudDtoMapper.toCloudError(wireCode, "message", 400).code)
        }
    }

    @Test
    fun `unknown error code uses unknown without guessing a project state`() {
        val error = CloudDtoMapper.toCloudError("NEW_SERVER_CODE", "message", 499)

        assertEquals(CloudErrorCode.UNKNOWN, error.code)
        assertEquals(499, error.httpStatus)
    }
}

