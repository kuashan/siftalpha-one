package com.siftalpha.cloud.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudWebEndpointPolicyTest {
    @Test
    fun `accepts private http and https endpoints`() {
        assertEquals("http", CloudWebEndpoint("http", "10.77.0.1", 18080, "/").scheme)
        assertEquals("https", CloudWebEndpoint("https", "192.168.1.10", 443, "/ui").scheme)
    }

    @Test
    fun `rejects unsafe schemes blank host public fallback and malformed ports`() {
        listOf<() -> Unit>(
            { CloudWebEndpoint("file", "10.77.0.1", 18080, "/").validate() },
            { CloudWebEndpoint("javascript", "10.77.0.1", 18080, "/").validate() },
            { CloudWebEndpoint("intent", "10.77.0.1", 18080, "/").validate() },
            { CloudWebEndpoint("http", "", 18080, "/").validate() },
            { CloudWebEndpoint("http", "8.8.8.8", 18080, "/").validate() },
            { CloudWebEndpoint("http", "10.77.0.1", 0, "/").validate() },
            { CloudWebEndpoint("http", "10.77.0.1", 65536, "/").validate() },
        ).forEach { candidate ->
            val thrown = runCatching { candidate() }.exceptionOrNull()
            assertTrue("expected invalid endpoint", thrown != null)
        }
    }
}
