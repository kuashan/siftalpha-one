package com.siftalpha.studio.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbeddedPythonEntrypointPolicyTest {
    @Test
    fun explicitEntryPointWinsAndMayBeNested() {
        assertEquals(
            "src/main.py",
            EmbeddedPythonEntrypointPolicy.resolve(
                declaredEntry = "src/main.py",
                filePaths = listOf("src/main.py", "src/helper.py"),
            ),
        )
    }

    @Test
    fun deterministicConventionalEntryPointIsResolvedByM() {
        assertEquals(
            "main.py",
            EmbeddedPythonEntrypointPolicy.resolve(
                declaredEntry = null,
                filePaths = listOf("helper.py", "main.py", "app.py"),
            ),
        )
    }

    @Test
    fun unresolvedWhenRootPythonEntryIsAmbiguous() {
        assertNull(
            EmbeddedPythonEntrypointPolicy.resolve(
                declaredEntry = null,
                filePaths = listOf("first.py", "second.py", "pkg/helper.py"),
            ),
        )
    }

    @Test
    fun invalidOrMissingDeclaredEntryPointDoesNotFallBack() {
        assertNull(
            EmbeddedPythonEntrypointPolicy.resolve(
                declaredEntry = "../main.py",
                filePaths = listOf("main.py"),
            ),
        )
        assertNull(
            EmbeddedPythonEntrypointPolicy.resolve(
                declaredEntry = "missing.py",
                filePaths = listOf("main.py"),
            ),
        )
    }
}
