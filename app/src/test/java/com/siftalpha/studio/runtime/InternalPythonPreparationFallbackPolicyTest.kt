package com.siftalpha.studio.runtime

import com.siftalpha.studio.siftalphax.InternalPythonBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InternalPythonPreparationFallbackPolicyTest {
    @Test
    fun noCompatibleWheelRoutesToInternalAlpine() {
        val error = IllegalStateException(
            "NO_COMPATIBLE_WHEEL: crc32c has no compatible CPython 3.14 Android or pure-Python wheel",
        )

        assertEquals(
            InternalPythonBackend.ALPINE,
            InternalPythonPreparationFallbackPolicy.backendFor(error),
        )
    }

    @Test
    fun nestedCapabilityFailureStillRoutesToInternalAlpine() {
        val error = IllegalStateException(
            "embedded preparation failed",
            IllegalArgumentException("UNSUPPORTED_ENVIRONMENT_MARKER: platform_system"),
        )

        assertEquals(
            InternalPythonBackend.ALPINE,
            InternalPythonPreparationFallbackPolicy.backendFor(error),
        )
    }

    @Test
    fun unrelatedFailureIsNotSilentlyRerouted() {
        val error = IllegalStateException("project storage is unreadable")

        assertNull(InternalPythonPreparationFallbackPolicy.backendFor(error))
    }
}
