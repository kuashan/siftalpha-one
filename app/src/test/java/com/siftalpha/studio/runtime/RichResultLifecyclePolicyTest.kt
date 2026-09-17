package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RichResultLifecyclePolicyTest {

    private val first = RichResultDocument(
        type = RichResultType.LINK_LIST,
        items = listOf(RichResultItem("First", "https://example.com/first")),
    )
    private val second = RichResultDocument(
        type = RichResultType.LINK_LIST,
        items = listOf(RichResultItem("Second", "https://example.com/second")),
    )

    @Test
    fun acceptedStartClearsPreviousResult() {
        assertNull(RichResultLifecyclePolicy.onAcceptedStart())
    }

    @Test
    fun emptyStatusOrLogsObservationPreservesCurrentResult() {
        assertEquals(first, RichResultLifecyclePolicy.merge(first, detected = null))
    }

    @Test
    fun laterValidObservationReplacesCurrentResult() {
        assertEquals(second, RichResultLifecyclePolicy.merge(first, detected = second))
    }
}
