package com.siftalpha.studio.runtime

import com.siftalpha.studio.siftalphax.EmbeddedPythonState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeOwnershipPolicyTest {
    @Test
    fun activeEmbeddedSessionOwnsObservation() {
        assertTrue(
            RuntimeOwnershipPolicy.ownsEmbeddedSnapshot(
                RuntimeOwnership.UNKNOWN,
                EmbeddedPythonState.STARTING,
            ),
        )
        assertTrue(
            RuntimeOwnershipPolicy.ownsEmbeddedSnapshot(
                RuntimeOwnership.UNKNOWN,
                EmbeddedPythonState.RUNNING,
            ),
        )
    }

    @Test
    fun embeddedTerminalSnapshotRemainsOwnedUntilExternalStartAccepted() {
        assertTrue(
            RuntimeOwnershipPolicy.ownsEmbeddedSnapshot(
                RuntimeOwnership.EMBEDDED_R,
                EmbeddedPythonState.SUCCEEDED,
            ),
        )
        assertTrue(
            RuntimeOwnershipPolicy.ownsEmbeddedSnapshot(
                RuntimeOwnership.EMBEDDED_R,
                EmbeddedPythonState.FAILED,
            ),
        )
        assertTrue(
            RuntimeOwnershipPolicy.ownsEmbeddedSnapshot(
                RuntimeOwnership.EMBEDDED_R,
                EmbeddedPythonState.STOPPED,
            ),
        )
        assertFalse(
            RuntimeOwnershipPolicy.ownsEmbeddedSnapshot(
                RuntimeOwnership.UNKNOWN,
                EmbeddedPythonState.SUCCEEDED,
            ),
        )
    }

    @Test
    fun acceptedExternalStartSupersedesRetainedEmbeddedSnapshot() {
        val owner = RuntimeOwnershipPolicy.afterAcceptedStart(
            RuntimeControlRequest.EXTERNAL_PROVIDER,
        )
        assertFalse(
            RuntimeOwnershipPolicy.ownsEmbeddedSnapshot(
                owner,
                EmbeddedPythonState.SUCCEEDED,
            ),
        )
        assertFalse(
            RuntimeOwnershipPolicy.ownsEmbeddedSnapshot(
                owner,
                EmbeddedPythonState.RUNNING,
            ),
        )
    }

    @Test
    fun acceptedEmbeddedStartRestoresEmbeddedOwnership() {
        val owner = RuntimeOwnershipPolicy.afterAcceptedStart(
            RuntimeControlRequest.EMBEDDED_R,
        )
        assertTrue(
            RuntimeOwnershipPolicy.ownsEmbeddedSnapshot(
                owner,
                EmbeddedPythonState.STOPPED,
            ),
        )
    }
}
