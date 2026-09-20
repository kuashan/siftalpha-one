package com.siftalpha.studio.runtime

import com.siftalpha.studio.siftalphax.EmbeddedPythonDiagnosticText
import com.siftalpha.studio.siftalphax.EmbeddedPythonSnapshot
import com.siftalpha.studio.siftalphax.EmbeddedPythonState

/** Direct structured R-to-M mapping; Embedded R is never re-parsed as Termux marker output. */
object EmbeddedPythonRuntimeStateMapping {
    fun toRuntimeState(snapshot: EmbeddedPythonSnapshot): RuntimeState =
        toRuntimeState(snapshot.state)

    fun toRuntimeState(state: EmbeddedPythonState): RuntimeState = when (state) {
        EmbeddedPythonState.IDLE -> RuntimeState.UNKNOWN
        EmbeddedPythonState.STARTING -> RuntimeState.STARTING
        EmbeddedPythonState.RUNNING -> RuntimeState.RUNNING
        EmbeddedPythonState.SUCCEEDED -> RuntimeState.EXITED_SUCCESS
        EmbeddedPythonState.FAILED -> RuntimeState.EXITED_ERROR
        EmbeddedPythonState.STOPPED -> RuntimeState.STOPPED_BY_USER
    }

    fun outputText(snapshot: EmbeddedPythonSnapshot): String =
        EmbeddedPythonDiagnosticText.copyAll(snapshot)
}

object EmbeddedPythonObservationPolicy {
    enum class ManualAction {
        STATUS,
        LOGS,
    }

    fun shouldPresent(
        previous: EmbeddedPythonSnapshot?,
        current: EmbeddedPythonSnapshot,
        manualAction: ManualAction?,
    ): Boolean = manualAction != null || previous != current

    /**
     * stdout/stderr growth belongs to the existing output view. Rebuild the Runtime Center card
     * only when facts that affect controls, status, ownership or terminal presentation change.
     */
    fun requiresCardRefresh(
        previous: EmbeddedPythonSnapshot?,
        current: EmbeddedPythonSnapshot,
    ): Boolean {
        if (previous == null) return true
        return previous.engine != current.engine ||
            previous.sessionId != current.sessionId ||
            previous.projectIdentity != current.projectIdentity ||
            previous.generation != current.generation ||
            previous.state != current.state ||
            previous.runtimePhase != current.runtimePhase ||
            previous.stopPhase != current.stopPhase ||
            previous.stopResult != current.stopResult ||
            previous.finishedAtEpochMs != current.finishedAtEpochMs ||
            previous.exitCode != current.exitCode
    }

    fun shouldRenderOutput(
        lastRenderedAtEpochMs: Long?,
        nowEpochMs: Long,
        intervalMs: Long,
        manualAction: ManualAction?,
        structuralChanged: Boolean,
        active: Boolean,
    ): Boolean {
        require(intervalMs >= 0L)
        return manualAction != null ||
            structuralChanged ||
            !active ||
            lastRenderedAtEpochMs == null ||
            nowEpochMs - lastRenderedAtEpochMs >= intervalMs
    }

    fun outputText(
        snapshot: EmbeddedPythonSnapshot,
        manualAction: ManualAction? = null,
    ): String = buildString {
        append(EmbeddedPythonRuntimeStateMapping.outputText(snapshot))
        when (manualAction) {
            ManualAction.STATUS -> {
                append("\n\nSIFTALPHA_X_STATUS_CHECK=PASS")
                append("\nSIFTALPHA_X_STATUS_SOURCE=INTERNAL_SNAPSHOT")
            }
            ManualAction.LOGS -> {
                append("\n\nSIFTALPHA_X_LOG_REFRESH=PASS")
                append("\nSIFTALPHA_X_LOG_SOURCE=INTERNAL_SNAPSHOT")
            }
            null -> Unit
        }
    }
}

