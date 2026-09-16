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
