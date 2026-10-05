package com.siftalpha.cloud.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudCredentialStoreContractTest {
    @Test
    fun `credential store contract is keyed by server id and supports deletion`() {
        val store = InMemoryCredentialStore()
        val first = CloudCredential("server-1", "https://one.example", "token-one")
        val second = CloudCredential("server-2", "https://two.example", "token-two")

        store.save(first)
        store.save(second)

        assertEquals(first, store.get("server-1"))
        assertEquals(second, store.get("server-2"))

        store.delete("server-1")

        assertNull(store.get("server-1"))
        assertEquals(second, store.get("server-2"))
    }

    private class InMemoryCredentialStore : CloudCredentialStore {
        private val values = mutableMapOf<String, CloudCredential>()

        override fun get(serverId: String): CloudCredential? = values[serverId]

        override fun save(credential: CloudCredential) {
            values[credential.serverId] = credential
        }

        override fun delete(serverId: String) {
            values.remove(serverId)
        }
    }
}

