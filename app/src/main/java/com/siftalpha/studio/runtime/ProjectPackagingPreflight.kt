package com.siftalpha.studio.runtime

import org.tomlj.Toml

/**
 * Bounded, non-executing validation of project-owned packaging inputs before dependency work starts.
 *
 * Generated build outputs, such as Hatch force-include targets produced by a Vite build, are not
 * preflighted here because supplemental build steps may legitimately create them before Python
 * packaging.
 */
object ProjectPackagingPreflight {

    enum class FailureKind {
        PYPROJECT_TOML_INVALID,
        PROJECT_PACKAGING_INPUT_MISSING,
        PROJECT_PACKAGING_REFERENCE_UNSAFE,
    }

    data class Failure(
        val kind: FailureKind,
        val source: String,
        val reference: String? = null,
        val detail: String,
    )

    data class Result(
        val failures: List<Failure>,
    ) {
        val ready: Boolean
            get() = failures.isEmpty()
    }

    fun inspect(
        relativePaths: Collection<String>,
        pyprojectToml: String?,
        requirementsText: String?,
    ): Result {
        val existing = relativePaths
            .asSequence()
            .map(::normalizeProjectPath)
            .filter { it.isNotBlank() }
            .toSet()
        val failures = mutableListOf<Failure>()

        pyprojectToml?.let { text ->
            val parsed = Toml.parse(text)
            if (parsed.hasErrors()) {
                failures += Failure(
                    kind = FailureKind.PYPROJECT_TOML_INVALID,
                    source = "pyproject.toml",
                    detail = "pyproject.toml could not be parsed as TOML",
                )
            } else {
                val project = parsed.getTable("project")
                project?.getString("readme")?.let { path ->
                    validateLocalReference(
                        raw = path,
                        source = "pyproject.toml:[project].readme",
                        existing = existing,
                        failures = failures,
                    )
                }
                project?.getTable("readme")?.getString("file")?.let { path ->
                    validateLocalReference(
                        raw = path,
                        source = "pyproject.toml:[project].readme.file",
                        existing = existing,
                        failures = failures,
                    )
                }
                project?.getTable("license")?.getString("file")?.let { path ->
                    validateLocalReference(
                        raw = path,
                        source = "pyproject.toml:[project].license.file",
                        existing = existing,
                        failures = failures,
                    )
                }
            }
        }

        requirementsText
            ?.lineSequence()
            ?.take(MAX_REQUIREMENT_LINES)
            ?.forEachIndexed { lineIndex, rawLine ->
                requirementIncludeReference(rawLine)?.let { reference ->
                    if (isNetworkReference(reference)) return@let
                    validateLocalReference(
                        raw = reference,
                        source = "requirements.txt:" + (lineIndex + 1),
                        existing = existing,
                        failures = failures,
                    )
                }
            }

        return Result(failures.distinct())
    }

    fun diagnosticLines(result: Result): List<String> {
        if (result.ready) return emptyList()
        val first = result.failures.first()
        val lines = mutableListOf(
            "SIFTALPHA_ERROR=PROJECT_PACKAGING_PREFLIGHT_FAILED",
            "SIFTALPHA_ENV=NOT_READY",
            "SIFTALPHA_DIAG_STAGE=PREPARE",
            "SIFTALPHA_DIAG=" + first.kind.name,
            "SIFTALPHA_DIAG_DETAIL=" + diagnosticDetail(first),
            "SIFTALPHA_PACKAGING_FAILURE_COUNT=" + result.failures.size,
        )
        result.failures.take(MAX_DIAGNOSTIC_FAILURES).forEachIndexed { index, failure ->
            lines += "SIFTALPHA_PACKAGING_FAILURE_" + (index + 1) + "=" + diagnosticDetail(failure)
        }
        return lines
    }

    private fun diagnosticDetail(failure: Failure): String = buildString {
        append(failure.source)
        failure.reference?.let {
            append(" -> ")
            append(it)
        }
        append(": ")
        append(failure.detail)
    }.replace('\n', ' ').replace('\r', ' ')

    private fun validateLocalReference(
        raw: String,
        source: String,
        existing: Set<String>,
        failures: MutableList<Failure>,
    ) {
        val normalized = normalizeReference(raw)
        if (normalized == null) {
            failures += Failure(
                kind = FailureKind.PROJECT_PACKAGING_REFERENCE_UNSAFE,
                source = source,
                reference = raw.trim().takeIf { it.isNotEmpty() },
                detail = "reference must stay inside the imported project snapshot",
            )
            return
        }
        if (normalized !in existing) {
            failures += Failure(
                kind = FailureKind.PROJECT_PACKAGING_INPUT_MISSING,
                source = source,
                reference = normalized,
                detail = "declared local packaging input does not exist in the imported project snapshot",
            )
        }
    }

    private fun requirementIncludeReference(rawLine: String): String? {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return null
        val match = REQUIREMENT_INCLUDE.matchEntire(line) ?: return null
        val value = match.groupValues[1]
            .substringBefore(" #")
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
        return value.takeIf { it.isNotBlank() }
    }

    private fun isNetworkReference(value: String): Boolean {
        val lower = value.trim().lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun normalizeReference(raw: String): String? {
        val value = raw.trim().replace('\\', '/')
        if (value.isBlank() || '\u0000' in value) return null
        if (value.startsWith("/") || value.startsWith("~")) return null
        if (WINDOWS_ABSOLUTE.matches(value)) return null
        if (URI_SCHEME.containsMatchIn(value)) return null

        val parts = value.split('/')
        if (parts.any { it == ".." }) return null
        return parts
            .filter { it.isNotBlank() && it != "." }
            .joinToString("/")
            .takeIf { it.isNotBlank() }
    }

    private fun normalizeProjectPath(raw: String): String =
        raw.trim().replace('\\', '/').trim('/')

    private const val MAX_REQUIREMENT_LINES = 2048
    private const val MAX_DIAGNOSTIC_FAILURES = 16
    private val REQUIREMENT_INCLUDE = Regex(
        """^(?:-r|--requirement(?:\s+|=)|-c|--constraint(?:\s+|=))\s*(.+)$""",
    )
    private val WINDOWS_ABSOLUTE = Regex("""^[A-Za-z]:/.*$""")
    private val URI_SCHEME = Regex("""^[A-Za-z][A-Za-z0-9+.-]*:""")
}
