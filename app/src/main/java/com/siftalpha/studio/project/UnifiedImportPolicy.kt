package com.siftalpha.studio.project

enum class UnifiedImportKind {
    PYTHON_FILE,
    ZIP_PROJECT,
    UNSUPPORTED,
}

/**
 * Product-facing import classification only.
 *
 * Actual import remains owned by V04ProjectGateway so Normal Mode does not create a second importer.
 */
object UnifiedImportPolicy {
    fun classify(fileName: String): UnifiedImportKind = when {
        fileName.trim().lowercase().endsWith(".py") -> UnifiedImportKind.PYTHON_FILE
        fileName.trim().lowercase().endsWith(".zip") -> UnifiedImportKind.ZIP_PROJECT
        else -> UnifiedImportKind.UNSUPPORTED
    }

    fun suggestedProjectName(fileName: String): String {
        val base = fileName
            .trim()
            .substringBeforeLast('.', missingDelimiterValue = fileName.trim())
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-', '.', '_')
            .take(60)
        return base.ifBlank { "imported-project" }
    }
}
