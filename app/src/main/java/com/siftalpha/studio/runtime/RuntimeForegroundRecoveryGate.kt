package com.siftalpha.studio.runtime

/**
 * Android Activity（安卓页面） recovery gate.
 *
 * Persisted Runtime（持久化运行时） recovery is needed only once for a newly created Activity
 * instance. A normal background -> foreground transition must not be treated as process/activity
 * reconstruction, otherwise a healthy running project can be left permanently in RECOVERING（恢复中）.
 */
class RuntimeForegroundRecoveryGate {
    private var initialRecoveryConsumed = false

    fun consumeInitialRecovery(): Boolean {
        if (initialRecoveryConsumed) return false
        initialRecoveryConsumed = true
        return true
    }
}
