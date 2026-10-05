package com.siftalpha.studio.cloud

import com.siftalpha.cloud.agent.CloudCredential
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Encodes the platform-neutral credential payload immediately before Android encryption.
 * The encoded bytes are never persisted without the AES/GCM envelope owned by the Android store.
 */
object CloudCredentialPayloadCodec {
    private const val MAGIC = 0x53414331
    private const val VERSION = 1
    private const val FIELD_COUNT = 3
    private const val MAX_FIELD_BYTES = 4 * 1024
    private const val MAX_PAYLOAD_BYTES = 16 * 1024

    fun encode(credential: CloudCredential): ByteArray {
        require(credential.serverId.isNotBlank()) { "serverId must not be blank" }
        require(credential.baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(credential.bearerToken.isNotBlank()) { "bearerToken must not be blank" }

        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeByte(VERSION)
            listOf(credential.serverId, credential.baseUrl, credential.bearerToken).forEach { value ->
                val bytes = value.toByteArray(Charsets.UTF_8)
                require(bytes.size <= MAX_FIELD_BYTES) { "credential field is too large" }
                data.writeInt(bytes.size)
                data.write(bytes)
            }
        }
        return output.toByteArray()
    }

    fun decode(payload: ByteArray): CloudCredential? = runCatching {
        require(payload.size <= MAX_PAYLOAD_BYTES)
        DataInputStream(ByteArrayInputStream(payload)).use { data ->
            require(data.readInt() == MAGIC)
            require(data.readUnsignedByte() == VERSION)
            val values = (0 until FIELD_COUNT).map { readField(data) }
            require(data.available() == 0)
            CloudCredential(
                serverId = values[0],
                baseUrl = values[1],
                bearerToken = values[2],
            )
        }
    }.getOrNull()

    private fun readField(data: DataInputStream): String {
        val size = data.readInt()
        require(size in 1..MAX_FIELD_BYTES)
        val bytes = ByteArray(size)
        data.readFully(bytes)
        val value = String(bytes, Charsets.UTF_8)
        require(value.toByteArray(Charsets.UTF_8).contentEquals(bytes))
        return value
    }
}

