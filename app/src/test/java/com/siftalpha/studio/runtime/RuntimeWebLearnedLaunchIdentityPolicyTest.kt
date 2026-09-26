package com.siftalpha.studio.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeWebLearnedLaunchIdentityPolicyTest {

    @Test
    fun identicalSourceFingerprintCanReuseVerifiedLaunch() {
        assertTrue(
            RuntimeWebLearnedLaunchIdentityPolicy.reusable(
                storedFingerprint = "sha256:abc",
                currentFingerprint = "sha256:abc",
            ),
        )
    }

    @Test
    fun changedSourceFingerprintInvalidatesVerifiedLaunch() {
        assertFalse(
            RuntimeWebLearnedLaunchIdentityPolicy.reusable(
                storedFingerprint = "sha256:abc",
                currentFingerprint = "sha256:def",
            ),
        )
    }

    @Test
    fun blankFingerprintNeverReusesLaunch() {
        assertFalse(RuntimeWebLearnedLaunchIdentityPolicy.reusable("", "sha256:abc"))
        assertFalse(RuntimeWebLearnedLaunchIdentityPolicy.reusable("sha256:abc", ""))
    }
}
