package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRuntimePlanningCoreTest {
    @Test
    fun pythonRootWithNestedNodeFrontendStaysPythonPrimary() {
        val result = ProjectRuntimeExecutionPlanner.select(
            listOf(
                "requirements.txt",
                "main.py",
                "web/package.json",
                "web/package-lock.json",
                "web/vite.config.ts",
            ),
        ) as ProjectRuntimeExecutionPlanner.Selection.Resolved

        assertEquals(RuntimeKind.PYTHON, result.primary)
        assertTrue(RuntimeKind.NODE_JS in result.supplemental)
    }

    @Test
    fun conflictingRootRuntimeEvidenceIsAmbiguous() {
        val result = ProjectRuntimeExecutionPlanner.select(
            listOf("requirements.txt", "main.py", "package.json", "package-lock.json"),
        )
        assertTrue(result is ProjectRuntimeExecutionPlanner.Selection.Ambiguous)
    }

    @Test
    fun unknownProjectIsUnsupported() {
        assertTrue(
            ProjectRuntimeExecutionPlanner.select(listOf("README.md", "assets/logo.svg")) is
                ProjectRuntimeExecutionPlanner.Selection.Unsupported,
        )
    }
}
