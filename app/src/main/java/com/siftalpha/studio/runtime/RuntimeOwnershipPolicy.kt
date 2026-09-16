package com.siftalpha.studio.runtime

import com.siftalpha.studio.siftalphax.EmbeddedPythonState

/**
 * Separates retained Embedded R terminal history from the project's current runtime owner.
 *
 * A terminal Embedded R snapshot remains useful for recent-result display until an accepted
 * External Provider start establishes a newer owner for the project.
 */
enum class RuntimeOwnership {
    UNKNOWN,
    EMBEDDED_R,
    EXTERNAL_PROVIDER,
}

object RuntimeOwnershipPolicy {
    fun ownsEmbeddedSnapshot(
        ownership: RuntimeOwnership,
        state: EmbeddedPythonState,
    ): Boolean {
        if (ownership == RuntimeOwnership.EXTERNAL_PROVIDER) return false
        return when (state) {
            EmbeddedPythonState.STARTING,
            EmbeddedPythonState.RUNNING -> true
            EmbeddedPythonState.SUCCEEDED,
            EmbeddedPythonState.FAILED,
            EmbeddedPythonState.STOPPED -> ownership == RuntimeOwnership.EMBEDDED_R
            EmbeddedPythonState.IDLE -> false
        }
    }

    fun afterAcceptedStart(request: RuntimeControlRequest): RuntimeOwnership = when (request) {
        RuntimeControlRequest.EMBEDDED_R -> RuntimeOwnership.EMBEDDED_R
        RuntimeControlRequest.EXTERNAL_PROVIDER -> RuntimeOwnership.EXTERNAL_PROVIDER
    }
}
