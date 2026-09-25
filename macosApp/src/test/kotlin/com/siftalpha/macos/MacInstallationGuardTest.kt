package com.siftalpha.macos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacInstallationGuardTest {
    @Test
    fun developmentLaunchIsAllowed() {
        val assessment = MacInstallationGuard.assess("/usr/bin/java")

        assertEquals(MacInstallationState.DEVELOPMENT, assessment.state)
        assertTrue(assessment.allowed)
    }

    @Test
    fun applicationInstalledUnderGlobalApplicationsIsAllowed() {
        val assessment = MacInstallationGuard.assess(
            "/Applications/SiftAlpha X.app/Contents/MacOS/SiftAlpha X",
        )

        assertEquals(MacInstallationState.INSTALLED_IN_APPLICATIONS, assessment.state)
        assertTrue(assessment.allowed)
    }

    @Test
    fun appTranslocationIsBlockedBeforeProjectUiStarts() {
        val assessment = MacInstallationGuard.assess(
            "/private/var/folders/aa/bb/T/AppTranslocation/01234567-89AB-CDEF-0123-456789ABCDEF/d/" +
                "SiftAlpha X.app/Contents/MacOS/SiftAlpha X",
        )

        assertEquals(MacInstallationState.APP_TRANSLOCATION, assessment.state)
        assertFalse(assessment.allowed)
        assertTrue(MacInstallationGuard.userMessage(assessment).contains("App Translocation"))
    }

    @Test
    fun packagedAppOutsideApplicationsIsBlocked() {
        val assessment = MacInstallationGuard.assess(
            "/Users/example/Downloads/SiftAlpha X.app/Contents/MacOS/SiftAlpha X",
        )

        assertEquals(MacInstallationState.OUTSIDE_APPLICATIONS, assessment.state)
        assertFalse(assessment.allowed)
        assertTrue(MacInstallationGuard.userMessage(assessment).contains("/Applications/SiftAlpha X.app"))
    }

    @Test
    fun perUserApplicationsDoesNotCountAsFormalInstallLocation() {
        val assessment = MacInstallationGuard.assess(
            "/Users/example/Applications/SiftAlpha X.app/Contents/MacOS/SiftAlpha X",
        )

        assertEquals(MacInstallationState.OUTSIDE_APPLICATIONS, assessment.state)
        assertFalse(assessment.allowed)
    }
}
