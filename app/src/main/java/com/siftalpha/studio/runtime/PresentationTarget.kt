package com.siftalpha.studio.runtime

enum class PresentationTarget {
    WEB,
    RICH_RESULT,
    NONE,
}

object PresentationTargetResolver {
    fun resolve(
        webPresentationKnown: Boolean,
        richResultAvailable: Boolean,
    ): PresentationTarget = when {
        webPresentationKnown -> PresentationTarget.WEB
        richResultAvailable -> PresentationTarget.RICH_RESULT
        else -> PresentationTarget.NONE
    }
}
