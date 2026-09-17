package com.siftalpha.studio.runtime

enum class PresentationTarget {
    WEB,
    RICH_RESULT,
    NONE,
}

object PresentationTargetResolver {
    fun resolve(
        webAvailable: Boolean,
        richResultAvailable: Boolean,
    ): PresentationTarget = when {
        webAvailable -> PresentationTarget.WEB
        richResultAvailable -> PresentationTarget.RICH_RESULT
        else -> PresentationTarget.NONE
    }
}
