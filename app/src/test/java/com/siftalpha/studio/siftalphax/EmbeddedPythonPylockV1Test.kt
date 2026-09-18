package com.siftalpha.studio.siftalphax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPythonPylockV1Test {
    @Test
    fun acceptsResolvedPurePythonWheelLock() {
        val result = EmbeddedPythonPylockV1Parser.parse(
            """
            lock-version = "1.0"
            requires-python = ">=3.14,<3.15"
            environments = ["sys_platform == 'android'"]
            extras = []
            dependency-groups = []
            default-groups = []
            created-by = "siftalpha-locker"

            [[packages]]
            name = "Example_Package"
            version = "1.2.3"
            requires-python = ">=3.10"

              [[packages.wheels]]
              name = "example_package-1.2.3-py3-none-any.whl"
              url = "https://example.test/wheels/example_package-1.2.3-py3-none-any.whl"
              size = 1234
              hashes = { sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" }
            """.trimIndent(),
        )

        val accepted = result as EmbeddedPythonPylockV1ParseResult.Accepted
        assertEquals("1.0", accepted.document.lockVersion)
        assertEquals(">=3.14,<3.15", accepted.document.requiresPython)
        assertEquals(listOf("sys_platform == 'android'"), accepted.document.environments)
        assertEquals("siftalpha-locker", accepted.document.createdBy)
        assertEquals(1, accepted.document.packages.size)

        val pkg = accepted.document.packages.single()
        assertEquals("Example_Package", pkg.name)
        assertEquals("example-package", pkg.normalizedName)
        assertEquals("1.2.3", pkg.version)
        assertEquals(1, pkg.wheels.size)

        val wheel = pkg.wheels.single()
        assertEquals("example_package-1.2.3-py3-none-any.whl", wheel.filename)
        assertEquals(1234L, wheel.size)
        assertEquals(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            wheel.sha256,
        )
    }

    @Test
    fun derivesWheelFilenameFromProjectRelativePath() {
        val result = EmbeddedPythonPylockV1Parser.parse(
            validLock(
                wheelOrigin = "path = \"vendor/demo-1.0.0-py3-none-any.whl\"",
                includeWheelName = false,
            ),
        )

        val accepted = result as EmbeddedPythonPylockV1ParseResult.Accepted
        val wheel = accepted.document.packages.single().wheels.single()
        assertEquals("demo-1.0.0-py3-none-any.whl", wheel.filename)
        assertEquals("vendor/demo-1.0.0-py3-none-any.whl", wheel.projectRelativePath)
    }

    @Test
    fun rejectsTomlSyntaxFailure() {
        val result = EmbeddedPythonPylockV1Parser.parse("lock-version = [")
        val rejected = result as EmbeddedPythonPylockV1ParseResult.Rejected

        assertEquals(EmbeddedPythonPylockV1FailureCode.TOML_PARSE_ERROR, rejected.code)
    }

    @Test
    fun rejectsUnsupportedLockVersionAndMissingRequiresPython() {
        assertRejected(
            validLock(lockVersion = "2.0"),
            EmbeddedPythonPylockV1FailureCode.LOCK_INVALID,
            "lock-version",
        )
        assertRejected(
            validLock().replace("requires-python = \">=3.14,<3.15\"\n", ""),
            EmbeddedPythonPylockV1FailureCode.LOCK_INVALID,
            "requires-python",
        )
    }

    @Test
    fun rejectsNonEmptyExtrasAndSourceDistributions() {
        assertRejected(
            validLock().replace("extras = []", "extras = [\"gpu\"]"),
            EmbeddedPythonPylockV1FailureCode.PACKAGE_UNSUPPORTED,
            "extras",
        )

        val sdistLock = validLock().replace(
            "  [[packages.wheels]]\n",
            "  [packages.sdist]\n" +
                "  name = \"demo-1.0.0.tar.gz\"\n" +
                "  url = \"https://example.test/demo-1.0.0.tar.gz\"\n" +
                "  size = 100\n" +
                "  hashes = { sha256 = \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\" }\n\n" +
                "  [[packages.wheels]]\n",
        )
        assertRejected(
            sdistLock,
            EmbeddedPythonPylockV1FailureCode.PACKAGE_UNSUPPORTED,
            "sdist",
        )
    }

    @Test
    fun rejectsUnsafeArtifactOrigins() {
        assertRejected(
            validLock(
                wheelOrigin = "url = \"http://example.test/demo-1.0.0-py3-none-any.whl\"",
            ),
            EmbeddedPythonPylockV1FailureCode.LOCK_INVALID,
            "HTTPS",
        )
        assertRejected(
            validLock(
                wheelOrigin = "path = \"../demo-1.0.0-py3-none-any.whl\"",
            ),
            EmbeddedPythonPylockV1FailureCode.LOCK_INVALID,
            "project-relative",
        )
    }

    @Test
    fun rejectsMissingSizeOrSha256() {
        assertRejected(
            validLock().replace("  size = 1234\n", ""),
            EmbeddedPythonPylockV1FailureCode.LOCK_INVALID,
            "size",
        )
        assertRejected(
            validLock().replace(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "ABC",
            ),
            EmbeddedPythonPylockV1FailureCode.LOCK_INVALID,
            "sha256",
        )
    }

    @Test
    fun toolDataDoesNotAffectInstallationContract() {
        val result = EmbeddedPythonPylockV1Parser.parse(
            validLock() +
                "\n[tool.siftalpha]\n" +
                "diagnostic-only = \"yes\"\n",
        )

        assertTrue(result is EmbeddedPythonPylockV1ParseResult.Accepted)
    }

    private fun validLock(
        lockVersion: String = "1.0",
        wheelOrigin: String =
            "url = \"https://example.test/demo-1.0.0-py3-none-any.whl\"",
        includeWheelName: Boolean = true,
    ): String {
        val wheelNameLine = if (includeWheelName) {
            "  name = \"demo-1.0.0-py3-none-any.whl\"\n"
        } else {
            ""
        }
        return (
            "lock-version = \"" + lockVersion + "\"\n" +
                "requires-python = \">=3.14,<3.15\"\n" +
                "extras = []\n" +
                "dependency-groups = []\n" +
                "default-groups = []\n" +
                "created-by = \"siftalpha-locker\"\n\n" +
                "[[packages]]\n" +
                "name = \"demo\"\n" +
                "version = \"1.0.0\"\n\n" +
                "  [[packages.wheels]]\n" +
                wheelNameLine +
                "  " + wheelOrigin + "\n" +
                "  size = 1234\n" +
                "  hashes = { sha256 = \"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\" }\n"
            )
    }

    private fun assertRejected(
        lock: String,
        code: EmbeddedPythonPylockV1FailureCode,
        detailContains: String,
    ) {
        val result = EmbeddedPythonPylockV1Parser.parse(lock)
        val rejected = result as EmbeddedPythonPylockV1ParseResult.Rejected
        assertEquals(code, rejected.code)
        assertTrue(
            "Expected detail to contain '" + detailContains + "', got '" + rejected.detail + "'",
            rejected.detail.contains(detailContains),
        )
    }
}
