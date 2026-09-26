package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeWebEndpointAuthorityPolicyTest {

    @Test
    fun htmlUiOutranksEarlierApiPort() {
        val ranked = RuntimeWebEndpointAuthorityPolicy.rank(
            ports = listOf(8000, 4321),
        ) { port ->
            when (port) {
                8000 -> RuntimeWebEndpointClass.HTTP_OTHER
                4321 -> RuntimeWebEndpointClass.HTML_UI
                else -> RuntimeWebEndpointClass.UNREACHABLE
            }
        }

        assertEquals(listOf(4321, 8000), ranked)
    }

    @Test
    fun sameClassPreservesExistingAuthorityOrder() {
        val ranked = RuntimeWebEndpointAuthorityPolicy.rank(
            ports = listOf(5173, 9234, 8000),
        ) { RuntimeWebEndpointClass.HTML_UI }

        assertEquals(listOf(5173, 9234, 8000), ranked)
    }

    @Test
    fun nonHtmlHttpRemainsFallbackBeforeUnreachablePorts() {
        val ranked = RuntimeWebEndpointAuthorityPolicy.rank(
            ports = listOf(8000, 9234, 5173),
        ) { port ->
            when (port) {
                8000 -> RuntimeWebEndpointClass.HTTP_OTHER
                9234 -> RuntimeWebEndpointClass.UNREACHABLE
                else -> RuntimeWebEndpointClass.HTTP_OTHER
            }
        }

        assertEquals(listOf(8000, 5173, 9234), ranked)
    }
}
