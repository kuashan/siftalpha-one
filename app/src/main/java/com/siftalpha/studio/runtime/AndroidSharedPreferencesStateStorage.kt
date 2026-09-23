package com.siftalpha.studio.runtime

import android.content.SharedPreferences
import com.siftalpha.core.storage.PlatformStateStorage
import com.siftalpha.core.storage.StateStorageMutation
import com.siftalpha.core.storage.StoredStateValue

/**
 * Android（安卓） implementation of the shared PlatformStateStorage（平台状态存储） port.
 */
internal class AndroidSharedPreferencesStateStorage(
    private val prefs: SharedPreferences,
) : PlatformStateStorage {

    override fun read(key: String): StoredStateValue? = when (val value = prefs.all[key]) {
        is String -> StoredStateValue.Text(value)
        is Boolean -> StoredStateValue.Bool(value)
        is Long -> StoredStateValue.LongNumber(value)
        is Int -> StoredStateValue.IntNumber(value)
        else -> null
    }

    override fun mutate(mutation: StateStorageMutation) {
        val editor = prefs.edit()
        mutation.removals.forEach(editor::remove)
        mutation.writes.forEach { (key, value) ->
            when (value) {
                is StoredStateValue.Text -> editor.putString(key, value.value)
                is StoredStateValue.Bool -> editor.putBoolean(key, value.value)
                is StoredStateValue.LongNumber -> editor.putLong(key, value.value)
                is StoredStateValue.IntNumber -> editor.putInt(key, value.value)
            }
        }
        editor.apply()
    }
}
