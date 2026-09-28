package com.siftalpha.cloud.agent

data class CloudCredential(
    val serverId: String,
    val baseUrl: String,
    val bearerToken: String,
)

interface CloudCredentialStore {
    fun get(serverId: String): CloudCredential?

    fun save(credential: CloudCredential)

    fun delete(serverId: String)
}
