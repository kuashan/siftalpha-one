package com.siftalpha.studio

import android.content.Context

/**
 * Presentation-only preference. Developer Mode（开发者模式） never owns Runtime（运行时） state.
 */
class DeveloperModeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(FIELD_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(FIELD_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "siftalpha_developer_mode_v1"
        private const val FIELD_ENABLED = "enabled"
    }
}
