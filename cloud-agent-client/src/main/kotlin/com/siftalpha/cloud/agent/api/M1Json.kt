package com.siftalpha.cloud.agent.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object M1Json {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = false
    }

    fun decodeHealth(body: String): M1HealthDto = json.decodeFromString(body)

    fun decodeProjects(body: String): M1ProjectsResponseDto = json.decodeFromString(body)

    fun decodeProject(body: String): M1ProjectDto = json.decodeFromString(body)

    fun decodeStatus(body: String): M1StatusDto = json.decodeFromString(body)

    fun decodeLogs(body: String): M1LogsDto = json.decodeFromString(body)

    fun decodeOperation(body: String): M1OperationDto = json.decodeFromString(body)

    fun decodeError(body: String): M1ErrorEnvelopeDto = json.decodeFromString(body)
}
