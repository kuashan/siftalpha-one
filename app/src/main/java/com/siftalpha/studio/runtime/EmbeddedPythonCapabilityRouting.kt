package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.EmbeddedPythonEntrypointPolicy

/**
 * Conservative capability gate for the explicit Embedded R control path.
 *
 * Runtime detection answers which language a project resembles; this policy separately answers
 * whether the already accepted Embedded CPython boundary can own this particular START.
 */
enum class RuntimeControlRequest {
    /** Preserve the existing Termux/External Provider default control path. */
    EXTERNAL_PROVIDER,

    /** Explicit opt-in from the existing "Run with Embedded R" action. */
    EMBEDDED_R,
}

enum class RuntimeControlPath {
    EXTERNAL_PROVIDER,
    EMBEDDED_R,
    REJECTED,
}

enum class RuntimeControlReason {
    EXPLICIT_EXTERNAL_PROVIDER,
    EMBEDDED_R_ELIGIBLE,
    EMBEDDED_R_UNAVAILABLE,
    RUNTIME_SELECTION_UNRESOLVED,
    RUNTIME_NOT_PYTHON,
    SUPPLEMENTAL_RUNTIME_UNSUPPORTED,
    ENTRYPOINT_UNRESOLVED,
    ENTRYPOINT_NOT_IN_PROJECT,
    DECLARED_RUN_UNSUPPORTED,
    DEPENDENCY_ENVIRONMENT_REQUIRED,
    PROTECTED_CONFIGURATION_REQUIRED,
    ACTIVE_EMBEDDED_SESSION,
}

data class RuntimeControlDecision(
    val path: RuntimeControlPath,
    val reason: RuntimeControlReason,
)

data class EmbeddedPythonCapabilityFacts(
    val selection: ProjectRuntimeExecutionPlanner.Selection,
    val relativePaths: Collection<String>,
    val declaredRun: String?,
    val resolvedEntrypoint: String?,
    val hasExternalDependencyRequirement: Boolean,
    val hasProtectedConfigurationRequirement: Boolean,
    val embeddedRuntimeAvailable: Boolean,
)

object EmbeddedPythonCapabilityRouting {
    fun resolve(
        facts: EmbeddedPythonCapabilityFacts,
        request: RuntimeControlRequest = RuntimeControlRequest.EMBEDDED_R,
    ): RuntimeControlDecision {
        if (request == RuntimeControlRequest.EXTERNAL_PROVIDER) {
            return RuntimeControlDecision(
                path = RuntimeControlPath.EXTERNAL_PROVIDER,
                reason = RuntimeControlReason.EXPLICIT_EXTERNAL_PROVIDER,
            )
        }
        if (!facts.embeddedRuntimeAvailable) {
            return rejected(RuntimeControlReason.EMBEDDED_R_UNAVAILABLE)
        }

        val resolved = facts.selection as? ProjectRuntimeExecutionPlanner.Selection.Resolved
            ?: return rejected(RuntimeControlReason.RUNTIME_SELECTION_UNRESOLVED)
        if (resolved.primary != RuntimeKind.PYTHON) {
            return rejected(RuntimeControlReason.RUNTIME_NOT_PYTHON)
        }
        if (resolved.supplemental.isNotEmpty()) {
            return rejected(RuntimeControlReason.SUPPLEMENTAL_RUNTIME_UNSUPPORTED)
        }
        if (facts.hasProtectedConfigurationRequirement) {
            return rejected(RuntimeControlReason.PROTECTED_CONFIGURATION_REQUIRED)
        }
        val safeEntrypoint = facts.resolvedEntrypoint
            ?.let(EmbeddedPythonEntrypointPolicy::safeRelativePath)
            ?: return rejected(RuntimeControlReason.ENTRYPOINT_UNRESOLVED)
        val projectFiles = facts.relativePaths
            .asSequence()
            .mapNotNull(EmbeddedPythonEntrypointPolicy::safeRelativePath)
            .toSet()
        if (safeEntrypoint !in projectFiles) {
            return rejected(RuntimeControlReason.ENTRYPOINT_NOT_IN_PROJECT)
        }

        val declaredRun = facts.declaredRun?.trim().orEmpty()
        if (declaredRun.isNotBlank() && !isDirectPythonRun(declaredRun, safeEntrypoint)) {
            return rejected(RuntimeControlReason.DECLARED_RUN_UNSUPPORTED)
        }

        return RuntimeControlDecision(
            path = RuntimeControlPath.EMBEDDED_R,
            reason = RuntimeControlReason.EMBEDDED_R_ELIGIBLE,
        )
    }

    private fun isDirectPythonRun(command: String, entrypoint: String): Boolean {
        val tokens = command.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.size != 2) return false
        val interpreter = tokens[0].substringAfterLast('/').lowercase()
        if (interpreter != "python" && interpreter != "python3") return false
        val argument = tokens[1].trim().let { token ->
            if (token.length >= 2 &&
                ((token.first() == '\'' && token.last() == '\'') ||
                    (token.first() == '"' && token.last() == '"'))
            ) {
                token.substring(1, token.length - 1)
            } else {
                token
            }
        }
        return EmbeddedPythonEntrypointPolicy.safeRelativePath(argument) == entrypoint
    }

    private fun rejected(reason: RuntimeControlReason): RuntimeControlDecision =
        RuntimeControlDecision(RuntimeControlPath.REJECTED, reason)
}
