package com.siftalpha.studio.presentation

internal object DecorativeMotionPolicy {
    fun enabled(
        foregroundVisible: Boolean,
        systemAnimationsEnabled: Boolean,
    ): Boolean =
        foregroundVisible && systemAnimationsEnabled
}
