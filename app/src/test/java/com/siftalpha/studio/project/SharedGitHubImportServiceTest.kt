package com.siftalpha.studio.project

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedGitHubImportServiceTest {

    @Test
    fun parsesHttpsRepositoryAndDefaultsBranchAndName() {
        val spec = SharedGitHubImportService.parse(
            raw = "https://github.com/example/demo",
            rawBranch = "",
            rawName = "",
            errorMessage = { it.name },
        )

        assertEquals("https://github.com/example/demo.git", spec.cloneUrl)
        assertEquals("https://github.com/example/demo", spec.sourceUrl)
        assertEquals("main", spec.branch)
        assertEquals("demo", spec.projectName)
    }

    @Test
    fun preservesSshCloneAndNormalizesSourceUrl() {
        val spec = SharedGitHubImportService.parse(
            raw = "git@github.com:example/demo.git",
            rawBranch = "dev",
            rawName = "my-demo",
            errorMessage = { it.name },
        )

        assertEquals("git@github.com:example/demo.git", spec.cloneUrl)
        assertEquals("https://github.com/example/demo", spec.sourceUrl)
        assertEquals("dev", spec.branch)
        assertEquals("my-demo", spec.projectName)
    }

    @Test
    fun rejectsNonGithubAddress() {
        try {
            SharedGitHubImportService.parse(
                raw = "https://example.com/demo",
                rawBranch = "",
                rawName = "",
                errorMessage = { it.name },
            )
            throw AssertionError("Expected ParseException")
        } catch (error: SharedGitHubImportService.ParseException) {
            assertEquals(
                SharedGitHubImportService.ParseError.ONLY_GITHUB_SUPPORTED,
                error.reason,
            )
        }
    }

    @Test
    fun suggestProjectNameMatchesExistingImportRules() {
        assertEquals(
            "my-project",
            SharedGitHubImportService.suggestProjectName(" my project "),
        )
        assertEquals(
            "imported-project",
            SharedGitHubImportService.suggestProjectName("///"),
        )
    }
}
