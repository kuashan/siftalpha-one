package com.siftalpha.studio.runtime

enum class PresentationTarget {
    WEB,
    RESULT_WEB,
    RICH_RESULT,
    NONE,
}

object PresentationTargetResolver {
    fun resolve(
        webPresentationKnown: Boolean,
        richResultAvailable: Boolean,
        resultWebAvailable: Boolean = false,
    ): PresentationTarget = when {
        webPresentationKnown -> PresentationTarget.WEB
        resultWebAvailable -> PresentationTarget.RESULT_WEB
        richResultAvailable -> PresentationTarget.RICH_RESULT
        else -> PresentationTarget.NONE
    }
}
