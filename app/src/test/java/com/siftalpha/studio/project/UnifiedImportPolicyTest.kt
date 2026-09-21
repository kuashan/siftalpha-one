package com.siftalpha.studio.project

import org.junit.Assert.assertEquals
import org.junit.Test

class UnifiedImportPolicyTest {

    @Test
    fun classifiesSupportedUserImportFiles() {
        assertEquals(UnifiedImportKind.PYTHON_FILE, UnifiedImportPolicy.classify("main.py"))
        assertEquals(UnifiedImportKind.ZIP_PROJECT, UnifiedImportPolicy.classify("project.ZIP"))
        assertEquals(UnifiedImportKind.UNSUPPORTED, UnifiedImportPolicy.classify("README.md"))
    }

    @Test
    fun suggestsSafeProjectName() {
        assertEquals("my-project", UnifiedImportPolicy.suggestedProjectName("my project.zip"))
        assertEquals("alpha", UnifiedImportPolicy.suggestedProjectName("alpha.py"))
    }
}
