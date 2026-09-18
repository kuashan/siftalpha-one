package com.siftalpha.studio.siftalphax

/**
 * Turns an already parsed pylock document into one deterministic Android/CPython environment plan.
 *
 * This is the first composition layer which connects:
 *   lock contract -> runtime compatibility -> wheel selection -> dependency fingerprint.
 *
 * It still performs no network, filesystem mutation, extraction, installation, or Worker loading.
 */
enum class EmbeddedPythonEnvironmentPlanFailureCodeV1 {
    LOCK_REQUIRES_PYTHON_INCOMPATIBLE,
    LOCK_ENVIRONMENT_INCOMPATIBLE,
    COMPATIBILITY_UNSUPPORTED,
    PACKAGE_REQUIRES_PYTHON_INCOMPATIBLE,
    DUPLICATE_SELECTED_PACKAGE,
    WHEEL_UNSUPPORTED,
}

sealed interface EmbeddedPythonEnvironmentPlanResultV1 {
    data class Ready(
        val plan: EmbeddedPythonEnvironmentPlanV1,
    ) : EmbeddedPythonEnvironmentPlanResultV1

    data class Rejected(
        val code: EmbeddedPythonEnvironmentPlanFailureCodeV1,
        val detail: String,
    ) : EmbeddedPythonEnvironmentPlanResultV1
}

data class EmbeddedPythonEnvironmentSelectedPackageV1(
    val packageEntry: EmbeddedPythonPylockV1Package,
    val wheelSelection: EmbeddedPythonWheelSelectionResultV1.Selected,
)

data class EmbeddedPythonEnvironmentPlanV1(
    val runtime: EmbeddedPythonRuntimeCompatibilityContextV1,
    val androidApiLevel: Int,
    val androidWheelAbi: String,
    val selectedPackages: List<EmbeddedPythonEnvironmentSelectedPackageV1>,
    val dependencyFingerprint: EmbeddedPythonDependencyFingerprintV1,
)

object EmbeddedPythonEnvironmentPlannerV1 {
    private const val CANONICAL_ANDROID_ABI = "arm64-v8a"
    private const val DEFAULT_ANDROID_WHEEL_ABI = "arm64_v8a"

    fun plan(
        document: EmbeddedPythonPylockV1Document,
        androidApiLevel: Int,
        runtime: EmbeddedPythonRuntimeCompatibilityContextV1 =
            EmbeddedPythonRuntimeCompatibilityContextV1(),
        androidWheelAbi: String = DEFAULT_ANDROID_WHEEL_ABI,
    ): EmbeddedPythonEnvironmentPlanResultV1 {
        require(androidApiLevel > 0) { "androidApiLevel must be positive" }

        when (
            val rootCompatibility = EmbeddedPythonRuntimeCompatibilityV1.requiresPython(
                document.requiresPython,
                runtime,
            )
        ) {
            is EmbeddedPythonCompatibilityResultV1 -> {
                when (rootCompatibility.state) {
                    EmbeddedPythonCompatibilityStateV1.MATCH -> Unit
                    EmbeddedPythonCompatibilityStateV1.NO_MATCH -> {
                        return rejected(
                            EmbeddedPythonEnvironmentPlanFailureCodeV1
                                .LOCK_REQUIRES_PYTHON_INCOMPATIBLE,
                            rootCompatibility.detail
                                ?: "lock requires-python does not match current runtime",
                        )
                    }
                    EmbeddedPythonCompatibilityStateV1.UNSUPPORTED -> {
                        return rejected(
                            EmbeddedPythonEnvironmentPlanFailureCodeV1.COMPATIBILITY_UNSUPPORTED,
                            rootCompatibility.detail
                                ?: "lock requires-python cannot be evaluated",
                        )
                    }
                }
            }
        }

        if (document.environments.isNotEmpty()) {
            val environmentResults = document.environments.map { marker ->
                marker to EmbeddedPythonRuntimeCompatibilityV1.marker(marker, runtime)
            }
            if (environmentResults.none { it.second.matches }) {
                val unsupported = environmentResults.firstOrNull {
                    it.second.state == EmbeddedPythonCompatibilityStateV1.UNSUPPORTED
                }
                if (unsupported != null) {
                    return rejected(
                        EmbeddedPythonEnvironmentPlanFailureCodeV1.COMPATIBILITY_UNSUPPORTED,
                        "environment marker cannot be evaluated: " + unsupported.first +
                            ": " + unsupported.second.detail.orEmpty(),
                    )
                }
                return rejected(
                    EmbeddedPythonEnvironmentPlanFailureCodeV1.LOCK_ENVIRONMENT_INCOMPATIBLE,
                    "no lock environment marker matches the current runtime",
                )
            }
        }

        val selected = mutableListOf<EmbeddedPythonEnvironmentSelectedPackageV1>()
        for (pkg in document.packages) {
            val marker = pkg.marker
            if (marker != null) {
                val markerResult = EmbeddedPythonRuntimeCompatibilityV1.marker(marker, runtime)
                when (markerResult.state) {
                    EmbeddedPythonCompatibilityStateV1.MATCH -> Unit
                    EmbeddedPythonCompatibilityStateV1.NO_MATCH -> continue
                    EmbeddedPythonCompatibilityStateV1.UNSUPPORTED -> {
                        return rejected(
                            EmbeddedPythonEnvironmentPlanFailureCodeV1.COMPATIBILITY_UNSUPPORTED,
                            "package marker cannot be evaluated for " + pkg.normalizedName +
                                ": " + markerResult.detail.orEmpty(),
                        )
                    }
                }
            }

            val packageRequiresPython = pkg.requiresPython
            if (packageRequiresPython != null) {
                val packageCompatibility = EmbeddedPythonRuntimeCompatibilityV1.requiresPython(
                    packageRequiresPython,
                    runtime,
                )
                when (packageCompatibility.state) {
                    EmbeddedPythonCompatibilityStateV1.MATCH -> Unit
                    EmbeddedPythonCompatibilityStateV1.NO_MATCH -> {
                        return rejected(
                            EmbeddedPythonEnvironmentPlanFailureCodeV1
                                .PACKAGE_REQUIRES_PYTHON_INCOMPATIBLE,
                            "package " + pkg.normalizedName + "==" + pkg.version +
                                " is incompatible with " + runtime.pythonFullVersion,
                        )
                    }
                    EmbeddedPythonCompatibilityStateV1.UNSUPPORTED -> {
                        return rejected(
                            EmbeddedPythonEnvironmentPlanFailureCodeV1.COMPATIBILITY_UNSUPPORTED,
                            "package requires-python cannot be evaluated for " +
                                pkg.normalizedName + ": " +
                                packageCompatibility.detail.orEmpty(),
                        )
                    }
                }
            }

            val wheelResult = EmbeddedPythonWheelSelectionV1.selectForAndroidRuntime(
                pkg = pkg,
                androidApiLevel = androidApiLevel,
                androidAbi = androidWheelAbi,
            )
            val wheelSelection = wheelResult as? EmbeddedPythonWheelSelectionResultV1.Selected
                ?: return rejected(
                    EmbeddedPythonEnvironmentPlanFailureCodeV1.WHEEL_UNSUPPORTED,
                    (wheelResult as EmbeddedPythonWheelSelectionResultV1.Rejected).detail,
                )
            selected += EmbeddedPythonEnvironmentSelectedPackageV1(
                packageEntry = pkg,
                wheelSelection = wheelSelection,
            )
        }

        val ordered = selected.sortedWith(
            compareBy(
                { it.packageEntry.normalizedName },
                { it.packageEntry.version },
                { it.wheelSelection.wheel.filename },
            ),
        )
        val duplicate = ordered.zipWithNext().firstOrNull { (left, right) ->
            left.packageEntry.normalizedName == right.packageEntry.normalizedName
        }
        if (duplicate != null) {
            return rejected(
                EmbeddedPythonEnvironmentPlanFailureCodeV1.DUPLICATE_SELECTED_PACKAGE,
                "multiple active package entries for " +
                    duplicate.first.packageEntry.normalizedName,
            )
        }

        val fingerprintPackages = ordered.map { selectedPackage ->
            val pkg = selectedPackage.packageEntry
            val wheel = selectedPackage.wheelSelection.wheel
            val tags = selectedPackage.wheelSelection.tags
            EmbeddedPythonSelectedPackageV1(
                normalizedName = pkg.normalizedName,
                version = pkg.version,
                marker = pkg.marker,
                requiresPython = pkg.requiresPython,
                wheelFilename = wheel.filename,
                artifactSha256 = wheel.sha256,
                pythonTags = tags.pythonTags,
                abiTags = tags.abiTags,
                platformTags = tags.platformTags,
                artifactOrigin = wheel.url
                    ?: "project:" + requireNotNull(wheel.projectRelativePath),
            )
        }

        val fingerprint = EmbeddedPythonEnvironmentIdentityV1.dependencyFingerprint(
            runtime = EmbeddedPythonDependencyRuntimeContextV1(
                pythonFullVersion = runtime.pythonFullVersion,
                pythonImplementation = runtime.implementationName,
                pythonAbi = "cp314",
                androidAbi = CANONICAL_ANDROID_ABI,
                androidApiPolicy = "device-api:" + androidApiLevel,
            ),
            selectedPackages = fingerprintPackages,
        )

        return EmbeddedPythonEnvironmentPlanResultV1.Ready(
            EmbeddedPythonEnvironmentPlanV1(
                runtime = runtime,
                androidApiLevel = androidApiLevel,
                androidWheelAbi = androidWheelAbi,
                selectedPackages = ordered,
                dependencyFingerprint = fingerprint,
            ),
        )
    }

    private fun rejected(
        code: EmbeddedPythonEnvironmentPlanFailureCodeV1,
        detail: String,
    ): EmbeddedPythonEnvironmentPlanResultV1.Rejected =
        EmbeddedPythonEnvironmentPlanResultV1.Rejected(code = code, detail = detail)
}
