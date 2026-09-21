package com.siftalpha.studio.project

import com.siftalpha.studio.runtime.ProjectRuntimeController

/**
 * Shared GitHub import contract used by both Normal Mode and Developer Workspace.
 *
 * UI, progress text, and Runtime operation ownership remain surface-specific. URL normalization,
 * project-name rules, and post-clone source attachment live here so the two surfaces cannot drift.
 */
object SharedGitHubImportService {

    enum class ParseError {
        ENTER_ADDRESS,
        ONLY_GITHUB_SUPPORTED,
        ADDRESS_RULE,
        PROJECT_NAME_RULE,
    }

    class ParseException(
        val reason: ParseError,
        message: String,
    ) : IllegalArgumentException(message)

    private val projectNamePattern = Regex("^[A-Za-z0-9._-]+$")

    fun parse(
        raw: String,
        rawBranch: String,
        rawName: String,
        errorMessage: (ParseError) -> String,
    ): ProjectRuntimeController.GitHubCloneSpec {
        val input = raw.trim()
        if (input.isBlank()) {
            throw ParseException(ParseError.ENTER_ADDRESS, errorMessage(ParseError.ENTER_ADDRESS))
        }

        val repoPath: String
        val cloneUrl: String
        val sourceUrl: String
        when {
            input.startsWith("https://github.com/") -> {
                val clean = input.substringBefore('?').substringBefore('#').trimEnd('/')
                repoPath = clean.removePrefix("https://github.com/").removeSuffix(".git")
                cloneUrl = if (clean.endsWith(".git")) clean else "$clean.git"
                sourceUrl = "https://github.com/$repoPath"
            }

            input.startsWith("git@github.com:") -> {
                repoPath = input.removePrefix("git@github.com:")
                    .removeSuffix(".git")
                    .trim('/')
                cloneUrl = input
                sourceUrl = "https://github.com/$repoPath"
            }

            else -> throw ParseException(
                ParseError.ONLY_GITHUB_SUPPORTED,
                errorMessage(ParseError.ONLY_GITHUB_SUPPORTED),
            )
        }

        val parts = repoPath.trim('/').split('/').filter { it.isNotBlank() }
        if (parts.size != 2) {
            throw ParseException(ParseError.ADDRESS_RULE, errorMessage(ParseError.ADDRESS_RULE))
        }

        val branch = rawBranch.trim().ifBlank { "main" }
        val projectName = rawName.trim().ifBlank { suggestProjectName(parts.last()) }
        if (!projectNamePattern.matches(projectName)) {
            throw ParseException(
                ParseError.PROJECT_NAME_RULE,
                errorMessage(ParseError.PROJECT_NAME_RULE),
            )
        }

        return ProjectRuntimeController.GitHubCloneSpec(
            cloneUrl = cloneUrl,
            sourceUrl = sourceUrl,
            branch = branch,
            projectName = projectName,
        )
    }

    fun attachMetadata(
        gateway: V04ProjectGateway,
        spec: ProjectRuntimeController.GitHubCloneSpec,
        attempts: Int = 8,
        retryDelayMs: Long = 250L,
    ): V04ProjectGateway.RuntimeProject {
        require(attempts >= 1)
        var last: Throwable? = null
        repeat(attempts) { attempt ->
            try {
                return gateway.attachGitHubSource(
                    spec.projectName,
                    spec.sourceUrl,
                    spec.branch,
                )
            } catch (error: Throwable) {
                last = error
                if (attempt < attempts - 1 && retryDelayMs > 0L) {
                    Thread.sleep(retryDelayMs)
                }
            }
        }
        throw last ?: IllegalStateException("GITHUB_SOURCE_ATTACH_FAILED")
    }

    fun suggestProjectName(raw: String): String {
        val value = raw.trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-', '.', '_')
            .take(60)
        return value.ifBlank { "imported-project" }
    }
}
