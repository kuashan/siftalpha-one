package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeKindTest {
    @Test
    fun declaredRuntimeAliasesRemainStableAcrossPlatforms() {
        assertEquals(RuntimeKind.PYTHON, RuntimeKind.fromDeclaredType("py"))
        assertEquals(RuntimeKind.NODE_JS, RuntimeKind.fromDeclaredType("typescript"))
        assertEquals(RuntimeKind.JVM, RuntimeKind.fromDeclaredType("kotlin"))
        assertEquals(RuntimeKind.GO, RuntimeKind.fromDeclaredType("golang"))
        assertEquals(RuntimeKind.RUST, RuntimeKind.fromDeclaredType("rust"))
        assertNull(RuntimeKind.fromDeclaredType("unknown-runtime"))
    }

    @Test
    fun polyglotThresholdRemainsProviderNeutral() {
        val profile = ProjectRuntimeProfile(
            listOf(
                RuntimeCandidate(RuntimeKind.PYTHON, 100, listOf("pyproject.toml")),
                RuntimeCandidate(RuntimeKind.NODE_JS, 50, listOf("package.json")),
            ),
        )
        assertEquals(true, profile.isPolyglot)
        assertEquals(RuntimeKind.PYTHON, profile.primary?.kind)
    }
}
