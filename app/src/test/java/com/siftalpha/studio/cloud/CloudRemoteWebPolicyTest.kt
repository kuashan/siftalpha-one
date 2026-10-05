package com.siftalpha.studio.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class CloudRemoteWebPolicyTest {
    @Test
    fun test_open_web_accepts_only_agent_private_http_https_endpoint() {
        assertNotNull(CloudTradingLabPolicy.validateRemoteWebUrl("http://10.77.0.1:18080/"))
        assertNotNull(CloudTradingLabPolicy.validateRemoteWebUrl("https://192.168.1.5:443/ui"))
        assertNull(CloudTradingLabPolicy.validateRemoteWebUrl("http://8.8.8.8:18080/"))
        assertNull(CloudTradingLabPolicy.validateRemoteWebUrl("file:///tmp/freqtrade"))
        assertNull(CloudTradingLabPolicy.validateRemoteWebUrl("javascript:alert(1)"))
        assertEquals("http://10.77.0.1:18080/", CloudTradingLabPolicy.remoteWebUrl(com.siftalpha.cloud.core.CloudWebEndpoint("http", "10.77.0.1", 18080)))
    }
}
