package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression model for the Runtime Center pending/result ordering contract.
 *
 * A result may become available before the UI has registered the action associated with its
 * executionId. The UI must leave unmatched results cached, register the Pending action, then
 * immediately reconcile the cached result. This model intentionally stays Android-free so the
 * ordering contract is exercised by the normal JVM unit-test task.
 */
class RuntimeUiResultReconciliationTest {

    private class Gate<T> {
        private val pending = mutableMapOf<Int, T>()
        private val cached = mutableMapOf<Int, String>()
        private val cancelled = mutableSetOf<Int>()

        fun publish(id: Int, result: String): Pair<T, String>? {
            if (id in cancelled) return null
            val item = pending.remove(id)
            return if (item == null) {
                cached[id] = result
                null
            } else {
                item to result
            }
        }

        fun register(id: Int, item: T): Pair<T, String>? {
            pending[id] = item
            val result = cached.remove(id) ?: return null
            return pending.remove(id)?.let { it to result }
        }

        fun cancel(id: Int) {
            cancelled += id
            pending.remove(id)
            cached.remove(id)
        }
    }

    @Test
    fun `result arriving before pending registration is reconciled after registration`() {
        val gate = Gate<String>()

        assertNull(gate.publish(1013, "SIFTALPHA_ENV=READY"))
        val delivered = gate.register(1013, "PREPARE")

        assertEquals("PREPARE", delivered?.first)
        assertEquals("SIFTALPHA_ENV=READY", delivered?.second)
    }

    @Test
    fun `normal pending before result path still delivers exactly once`() {
        val gate = Gate<String>()

        assertNull(gate.register(1014, "STATUS"))
        val delivered = gate.publish(1014, "SIFTALPHA_STATUS=RUNNING")

        assertEquals("STATUS", delivered?.first)
        assertEquals("SIFTALPHA_STATUS=RUNNING", delivered?.second)
    }

    @Test
    fun `cancelled operation cannot be resurrected by a late result`() {
        val gate = Gate<String>()
        assertNull(gate.register(1015, "CLEAN"))
        gate.cancel(1015)

        assertNull(gate.publish(1015, "SIFTALPHA_ENV=CLEANED"))
        assertNull(gate.register(1015, "START"))
    }
}
