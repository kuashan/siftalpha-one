package com.siftalpha.studio.runtime

/**
 * Activity-lifetime lifecycle policy for the latest detected Rich Result.
 *
 * A newly accepted START begins a new result generation. STATUS/LOGS observations that do not
 * contain a new document must not erase the latest valid result; a later observation may replace it.
 */
object RichResultLifecyclePolicy {
    fun onAcceptedStart(): RichResultDocument? = null

    fun merge(
        existing: RichResultDocument?,
        detected: RichResultDocument?,
    ): RichResultDocument? = detected ?: existing
}
