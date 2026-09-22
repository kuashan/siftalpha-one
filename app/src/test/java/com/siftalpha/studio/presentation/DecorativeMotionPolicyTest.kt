package com.siftalpha.studio.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecorativeMotionPolicyTest {
    @Test
    fun enabled_onlyWhenForegroundAndSystemAnimationsEnabled() {
        assertTrue(
            DecorativeMotionPolicy.enabled(
                foregroundVisible = true,
                systemAnimationsEnabled = true,
            ),
        )
        assertFalse(
            DecorativeMotionPolicy.enabled(
                foregroundVisible = false,
                systemAnimationsEnabled = true,
            ),
        )
        assertFalse(
            DecorativeMotionPolicy.enabled(
                foregroundVisible = true,
                systemAnimationsEnabled = false,
            ),
        )
        assertFalse(
            DecorativeMotionPolicy.enabled(
                foregroundVisible = false,
                systemAnimationsEnabled = false,
            ),
        )
    }
}
