package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class PresentationTargetResolverTest {

    @Test
    fun webDashboardWinsWhenRichResultAlsoExists() {
        assertEquals(
            PresentationTarget.WEB,
            PresentationTargetResolver.resolve(webPresentationKnown = true, richResultAvailable = true),
        )
    }

    @Test
    fun verifiedWebIdentityWinsEvenWhileEndpointIsBeingRevalidated() {
        assertEquals(
            PresentationTarget.WEB,
            PresentationTargetResolver.resolve(
                webPresentationKnown = true,
                richResultAvailable = true,
            ),
        )
    }

    @Test
    fun richResultIsUsedWhenWebIsUnavailable() {
        assertEquals(
            PresentationTarget.RICH_RESULT,
            PresentationTargetResolver.resolve(webPresentationKnown = false, richResultAvailable = true),
        )
    }

    @Test
    fun noWebAndNoRichResultHasNoOpenTarget() {
        assertEquals(
            PresentationTarget.NONE,
            PresentationTargetResolver.resolve(webPresentationKnown = false, richResultAvailable = false),
        )
    }

    @Test
    fun rawLogPresenceDoesNotCreateAnOpenTarget() {
        assertEquals(
            PresentationTarget.NONE,
            PresentationTargetResolver.resolve(webPresentationKnown = false, richResultAvailable = false),
        )
    }
}
