package com.siftalpha.studio.cloud

import com.siftalpha.cloud.agent.CloudCredential
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CloudCredentialEncodingTest {
    @Test
    fun roundTripPreservesCredentialFields() {
        val credential = CloudCredential(
            serverId = "oracle-test-server",
            baseUrl = "https://198.51.100.10:8443",
            bearerToken = UUID.randomUUID().toString(),
        )

        val encoded = CloudCredentialPayloadCodec.encode(credential)

        assertEquals(credential, CloudCredentialPayloadCodec.decode(encoded))
        assertFalse(encoded.decodeToString().contains(credential.bearerToken))
    }

    @Test
    fun malformedOrTrailingPayloadIsRejected() {
        val credential = CloudCredential(
            serverId = "oracle-test-server",
            baseUrl = "https://198.51.100.10:8443",
            bearerToken = UUID.randomUUID().toString(),
        )
        val encoded = CloudCredentialPayloadCodec.encode(credential)

        assertNull(CloudCredentialPayloadCodec.decode(encoded + byteArrayOf(0x01)))
        assertNull(CloudCredentialPayloadCodec.decode(encoded.copyOf(encoded.size - 1)))
    }
}
