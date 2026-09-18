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
    fun autoPurePythonProjectRoutesToEmbeddedR() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("main.py", "helper.py"),
                declaredRun = "python main.py",
            ),
            request = RuntimeControlRequest.AUTO,
        )

        assertEquals(RuntimeControlPath.EMBEDDED_R, decision.path)
    }

    @Test
    fun explicitExternalProviderRequestStaysExternal() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(paths = listOf("main.py", "helper.py")),
            request = RuntimeControlRequest.EXTERNAL_PROVIDER,
        )

        assertEquals(RuntimeControlPath.EXTERNAL_PROVIDER, decision.path)
        assertEquals(RuntimeControlReason.EXPLICIT_EXTERNAL_PROVIDER, decision.reason)
    }

    @Test
    fun autoNonPythonProjectFallsBackToExternalProvider() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("package.json", "index.js"),
                declaredType = "nodejs",
            ),
            request = RuntimeControlRequest.AUTO,
        )

        assertEquals(RuntimeControlPath.EXTERNAL_PROVIDER, decision.path)
        assertEquals(RuntimeControlReason.RUNTIME_NOT_PYTHON, decision.reason)
    }

    @Test
    fun explicitNonPythonProjectIsRejectedFromEmbeddedR() {
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
    fun autoSupplementalRuntimeFallsBackToExternalProvider() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("main.py", "frontend/package.json", "frontend/index.js"),
            ),
            request = RuntimeControlRequest.AUTO,
        )

        assertEquals(RuntimeControlPath.EXTERNAL_PROVIDER, decision.path)
        assertEquals(RuntimeControlReason.SUPPLEMENTAL_RUNTIME_UNSUPPORTED, decision.reason)
    }

    @Test
    fun dependencyMetadataDoesNotShrinkLegacyEmbeddedRCompatibility() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("main.py", "requirements.txt"),
                hasExternalDependencyRequirement = true,
            ),
        )

        assertEquals(RuntimeControlPath.EMBEDDED_R, decision.path)
    }

    @Test
    fun autoDependencyMetadataCanStillUseEmbeddedR() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("main.py", "requirements.txt"),
                declaredRun = "python main.py",
                hasExternalDependencyRequirement = true,
            ),
            request = RuntimeControlRequest.AUTO,
        )

        assertEquals(RuntimeControlPath.EMBEDDED_R, decision.path)
    }

    @Test
    fun protectedConfigurationMetadataDoesNotShrinkLegacyEmbeddedRCompatibility() {
        val decision = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("main.py"),
                hasProtectedConfigurationRequirement = true,
            ),
        )

        assertEquals(RuntimeControlPath.EMBEDDED_R, decision.path)
    }

    @Test
    fun unresolvedEntrypointFallsBackAutomaticallyButExplicitEmbeddedRejects() {
        val auto = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("first.py", "second.py"),
                resolvedEntrypoint = null,
            ),
            request = RuntimeControlRequest.AUTO,
        )
        val explicit = EmbeddedPythonCapabilityRouting.resolve(
            facts(
                paths = listOf("first.py", "second.py"),
                resolvedEntrypoint = null,
            ),
        )

        assertEquals(RuntimeControlPath.EXTERNAL_PROVIDER, auto.path)
        assertEquals(RuntimeControlPath.REJECTED, explicit.path)
        assertEquals(RuntimeControlReason.ENTRYPOINT_UNRESOLVED, explicit.reason)
    }

    @Test
    fun nonDirectDeclaredRunUsesExternalProviderAutomaticallyButExplicitEmbeddedKeepsLegacyEntry() {
        val facts = facts(
            paths = listOf("main.py"),
            declaredRun = "python -m package",
        )
        val auto = EmbeddedPythonCapabilityRouting.resolve(
            facts,
            request = RuntimeControlRequest.AUTO,
        )
        val explicit = EmbeddedPythonCapabilityRouting.resolve(facts)

        assertEquals(RuntimeControlPath.EXTERNAL_PROVIDER, auto.path)
        assertEquals(RuntimeControlReason.DECLARED_RUN_UNSUPPORTED, auto.reason)
        assertEquals(RuntimeControlPath.EMBEDDED_R, explicit.path)
    }

    @Test
    fun unavailableEmbeddedRuntimeFallsBackAutomaticallyButExplicitEmbeddedRejects() {
        val facts = facts(
            paths = listOf("main.py"),
            embeddedRuntimeAvailable = false,
        )
        val auto = EmbeddedPythonCapabilityRouting.resolve(
            facts,
            request = RuntimeControlRequest.AUTO,
        )
        val explicit = EmbeddedPythonCapabilityRouting.resolve(facts)

        assertEquals(RuntimeControlPath.EXTERNAL_PROVIDER, auto.path)
        assertEquals(RuntimeControlPath.REJECTED, explicit.path)
        assertEquals(RuntimeControlReason.EMBEDDED_R_UNAVAILABLE, explicit.reason)
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
        assertTrue(
            "test setup must resolve a single runtime",
            selection is ProjectRuntimeExecutionPlanner.Selection.Resolved,
        )
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
