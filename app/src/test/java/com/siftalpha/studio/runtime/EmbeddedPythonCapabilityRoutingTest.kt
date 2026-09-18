package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonCapabilityRoutingTest {

    @Test
    fun explicitPurePythonProjectRoutesToEmbeddedR() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("main.py", "helper.py"),
                declaredRun = "python main.py",
            ),
        )

        assertEquals(RuntimeControlPath.EMBEDDED_R, decision.path)
        assertEquals(RuntimeControlReason.EMBEDDED_R_ELIGIBLE, decision.reason)
    }

    @Test
    fun defaultRequestRemainsExternalProvider() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("main.py", "helper.py"),
            ),
            request = RuntimeControlRequest.EXTERNAL_PROVIDER,
        )

        assertEquals(RuntimeControlPath.EXTERNAL_PROVIDER, decision.path)
        assertEquals(RuntimeControlReason.EXPLICIT_EXTERNAL_PROVIDER, decision.reason)
    }

    @Test
    fun nonPythonProjectIsRejectedFromEmbeddedR() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("package.json", "index.js"),
                declaredType = "nodejs",
            ),
        )

        assertEquals(RuntimeControlPath.REJECTED, decision.path)
        assertEquals(RuntimeControlReason.RUNTIME_NOT_PYTHON, decision.reason)
    }

    @Test
    fun supplementalRuntimeIsRejected() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("main.py", "frontend/package.json", "frontend/index.js"),
            ),
        )

        assertEquals(RuntimeControlPath.REJECTED, decision.path)
        assertEquals(RuntimeControlReason.SUPPLEMENTAL_RUNTIME_UNSUPPORTED, decision.reason)
    }

    @Test
    fun dependencyMetadataDoesNotBlockEmbeddedRStart() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("main.py", "requirements.txt"),
                declaredRun = "python main.py",
                hasExternalDependencyRequirement = true,
            ),
        )

        assertEquals(RuntimeControlPath.EMBEDDED_R, decision.path)
        assertEquals(RuntimeControlReason.EMBEDDED_R_ELIGIBLE, decision.reason)
    }

    @Test
    fun preparationDoesNotRequireEntrypointOrProtectedConfiguration() {
        val decision = EmbeddedPythonCapabilityRouting.resolvePreparation(
            facts(
                paths = listOf("requirements.txt", "package/main.py"),
                resolvedEntrypoint = null,
                hasExternalDependencyRequirement = true,
                hasProtectedConfigurationRequirement = true,
            ),
        )

        assertEquals(RuntimeControlPath.EMBEDDED_R, decision.path)
        assertEquals(RuntimeControlReason.EMBEDDED_R_ELIGIBLE, decision.reason)
    }

    @Test
    fun protectedConfigurationRequirementFallsBackFromEmbeddedR() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("main.py"),
                hasProtectedConfigurationRequirement = true,
            ),
        )

        assertEquals(RuntimeControlPath.REJECTED, decision.path)
        assertEquals(RuntimeControlReason.PROTECTED_CONFIGURATION_REQUIRED, decision.reason)
    }

    @Test
    fun unresolvedEntrypointIsRejectedWithoutGuessing() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("first.py", "second.py"),
                resolvedEntrypoint = null,
            ),
        )

        assertEquals(RuntimeControlPath.REJECTED, decision.path)
        assertEquals(RuntimeControlReason.ENTRYPOINT_UNRESOLVED, decision.reason)
    }

    @Test
    fun arbitraryRunCommandDoesNotEnterFileBackedEmbeddedBoundary() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("main.py"),
                declaredRun = "python -m package",
            ),
        )

        assertEquals(RuntimeControlPath.REJECTED, decision.path)
        assertEquals(RuntimeControlReason.DECLARED_RUN_UNSUPPORTED, decision.reason)
    }

    @Test
    fun unavailableEmbeddedRuntimeIsRejected() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("main.py"),
                embeddedRuntimeAvailable = false,
            ),
        )

        assertEquals(RuntimeControlPath.REJECTED, decision.path)
        assertEquals(RuntimeControlReason.EMBEDDED_R_UNAVAILABLE, decision.reason)
    }

    private fun facts(
        paths: List<String>,
        declaredType: String? = "python",
        declaredRun: String? = null,
        resolvedEntrypoint: String? = "main.py",
        hasExternalDependencyRequirement: Boolean = false,
        hasProtectedConfigurationRequirement: Boolean = false,
        embeddedRuntimeAvailable: Boolean = true,
    ): EmbeddedPythonCapabilityFacts {
        val selection = ProjectRuntimeExecutionPlanner.select(
            relativePaths = paths,
            declaredType = declaredType,
        )
        assertTrue("test setup must resolve a single runtime", selection is ProjectRuntimeExecutionPlanner.Selection.Resolved)
        return EmbeddedPythonCapabilityFacts(
            selection = selection,
            relativePaths = paths,
            declaredRun = declaredRun,
            resolvedEntrypoint = resolvedEntrypoint,
            hasExternalDependencyRequirement = hasExternalDependencyRequirement,
            hasProtectedConfigurationRequirement = hasProtectedConfigurationRequirement,
            embeddedRuntimeAvailable = embeddedRuntimeAvailable,
        )
    }
}
