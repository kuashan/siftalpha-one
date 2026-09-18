package com.siftalpha.studio.siftalphax

import com.siftalpha.studio.R

/**
 * Reviewable file-backed project fixtures shipped with the experimental APK.
 *
 * The fixture metadata is the only scenario input sent across the M/R boundary; Python source is
 * read from the staged project root by native R.
 */
enum class EmbeddedPythonProjectFixture(
    val assetPath: String,
    val projectIdentity: String,
    val entrypoint: String = "main.py",
    val workingDirectory: String = ".",
) {
    PROJECT_A(
        assetPath = "siftalphax/project-fixtures/project-a",
        projectIdentity = "fixture-project-a",
    ),
    PROJECT_B(
        assetPath = "siftalphax/project-fixtures/project-b",
        projectIdentity = "fixture-project-b",
    ),
    PROJECT_C(
        assetPath = "siftalphax/project-fixtures/project-c",
        projectIdentity = "fixture-project-c",
    ),
    PROJECT_D_ISOLATION(
        assetPath = "siftalphax/project-fixtures/project-d-isolation",
        projectIdentity = "fixture-project-d-isolation",
    ),
    PROJECT_E_SYSTEM_EXIT_ZERO(
        assetPath = "siftalphax/project-fixtures/project-e-system-exit-zero",
        projectIdentity = "fixture-project-e-system-exit-zero",
    ),
    PROJECT_F_SYSTEM_EXIT_NONZERO(
        assetPath = "siftalphax/project-fixtures/project-f-system-exit-nonzero",
        projectIdentity = "fixture-project-f-system-exit-nonzero",
    ),
    PROJECT_G_WORKER_SMOKE(
        assetPath = "siftalphax/project-fixtures/project-g-worker-smoke",
        projectIdentity = "alpha44-worker-cpython-smoke",
    ),
    PROJECT_MISSING_ENTRYPOINT(
        assetPath = "siftalphax/project-fixtures/project-missing-entrypoint",
        projectIdentity = "fixture-project-missing-entrypoint",
        entrypoint = "missing.py",
    ),
}

enum class EmbeddedPythonScenario(
    val labelResource: Int,
    val fixture: EmbeddedPythonProjectFixture,
) {
    NORMAL(
        R.string.siftalpha_x_test_a,
        EmbeddedPythonProjectFixture.PROJECT_A,
    ),
    STDOUT_STDERR_FAILURE(
        R.string.siftalpha_x_test_b,
        EmbeddedPythonProjectFixture.PROJECT_B,
    ),
    LONG_RUNNING(
        R.string.siftalpha_x_test_c,
        EmbeddedPythonProjectFixture.PROJECT_C,
    ),
}
