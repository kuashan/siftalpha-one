package com.siftalpha.studio.project

/**
 * M-side policy for resolving one explicit Python entrypoint before a project reaches R.
 * The policy is deterministic; Embedded R never scans or guesses a target.
 */
object EmbeddedPythonEntrypointPolicy {
    private val conventionalEntrypoints = listOf("main.py", "app.py", "run.py", "manage.py")

    fun resolve(declaredEntry: String?, filePaths: Collection<String>): String? {
        val files = filePaths
            .mapNotNull(::safeRelativePath)
            .toSet()

        if (declaredEntry != null) {
            val explicit = safeRelativePath(declaredEntry) ?: return null
            return explicit.takeIf { it in files }
        }

        val orderedFiles = files.sorted()
        conventionalEntrypoints.forEach { wanted ->
            orderedFiles.firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { return it }
        }

        val rootPythonFiles = orderedFiles.filter {
            '/' !in it && it.endsWith(".py", ignoreCase = true)
        }
        return rootPythonFiles.singleOrNull()
    }

    internal fun safeRelativePath(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank() || value.startsWith("/") || value.contains('\\') || value.contains('\u0000')) {
            return null
        }
        val parts = value.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }
}
