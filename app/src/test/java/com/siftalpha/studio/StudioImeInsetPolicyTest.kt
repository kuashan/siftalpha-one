package com.siftalpha.studio

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioImeInsetPolicyTest {

    @Test
    fun `hidden ime preserves original bottom padding`() {
        assertEquals(
            14,
            StudioImeInsetPolicy.bottomPadding(
                baseBottom = 14,
                imeBottom = 320,
                imeVisible = false,
            ),
        )
    }

    @Test
    fun `visible ime adds safe bottom inset to original padding`() {
        assertEquals(
            334,
            StudioImeInsetPolicy.bottomPadding(
                baseBottom = 14,
                imeBottom = 320,
                imeVisible = true,
            ),
        )
    }

    @Test
    fun `invalid negative inset never removes original padding`() {
        assertEquals(
            14,
            StudioImeInsetPolicy.bottomPadding(
                baseBottom = 14,
                imeBottom = -100,
                imeVisible = true,
            ),
        )
    }
    @Test
    fun `system inset is added to original padding`() {
        assertEquals(
            58,
            StudioWindowInsetPolicy.systemPadding(
                base = 10,
                inset = 48,
            ),
        )
    }

    @Test
    fun `negative system inset never removes original padding`() {
        assertEquals(
            10,
            StudioWindowInsetPolicy.systemPadding(
                base = 10,
                inset = -20,
            ),
        )
    }

    @Test
    fun `hidden ime still preserves navigation bar inset`() {
        assertEquals(
            62,
            StudioWindowInsetPolicy.bottomPadding(
                baseBottom = 14,
                systemBottom = 48,
                imeBottom = 320,
                imeVisible = false,
            ),
        )
    }

    @Test
    fun `visible ime uses larger obstruction instead of double counting navigation bar`() {
        assertEquals(
            334,
            StudioWindowInsetPolicy.bottomPadding(
                baseBottom = 14,
                systemBottom = 48,
                imeBottom = 320,
                imeVisible = true,
            ),
        )
    }

}
