import org.gradle.api.tasks.Exec

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val embeddedCpythonOutput = layout.buildDirectory
    .dir("generated/cpython/arm64-v8a")
    .get()
    .asFile
val embeddedCpythonPrefix = embeddedCpythonOutput.resolve("prefix")
val embeddedCpythonAssets = embeddedCpythonOutput.resolve("assets")
val embeddedCpythonJniLibs = embeddedCpythonOutput.resolve("jniLibs")
val prepareEmbeddedCpython = tasks.register<Exec>("prepareEmbeddedCpython") {
    commandLine(
        rootProject.file("tools/prepare_embedded_cpython_android.sh").absolutePath,
        embeddedCpythonOutput.absolutePath,
    )
}

val internalAlpineOutput = layout.buildDirectory
    .dir("generated/internal-alpine")
    .get()
    .asFile
val internalAlpineAssets = internalAlpineOutput.resolve("assets")
val internalAlpineJniLibs = internalAlpineOutput.resolve("jniLibs")
val prepareInternalAlpine = tasks.register<Exec>("prepareInternalAlpine") {
    commandLine(
        rootProject.file("tools/prepare_internal_alpine_android.sh").absolutePath,
        internalAlpineOutput.absolutePath,
    )
}

val trustedKeystorePayload = providers.gradleProperty("SIFTALPHA_DEBUG_KEYSTORE_B64")
    .orElse(providers.environmentVariable("SIFTALPHA_DEBUG_KEYSTORE_B64"))
    .orNull
val trustedStorePassword = providers.gradleProperty("SIFTALPHA_DEBUG_STORE_PASSWORD")
    .orElse(providers.environmentVariable("SIFTALPHA_DEBUG_STORE_PASSWORD"))
    .orNull
val trustedKeyAlias = providers.gradleProperty("SIFTALPHA_DEBUG_KEY_ALIAS")
    .orElse(providers.environmentVariable("SIFTALPHA_DEBUG_KEY_ALIAS"))
    .orNull
val trustedKeyPassword = providers.gradleProperty("SIFTALPHA_DEBUG_KEY_PASSWORD")
    .orElse(providers.environmentVariable("SIFTALPHA_DEBUG_KEY_PASSWORD"))
    .orNull
val trustedKeystoreFile = providers.gradleProperty("siftalphaDebugKeystoreFile")
    .orElse(providers.environmentVariable("SIFTALPHA_DEBUG_KEYSTORE_FILE"))
    .orNull
val trustedSigningEnabled = listOf(
    trustedStorePassword,
    trustedKeyAlias,
    trustedKeyPassword,
    trustedKeystoreFile,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.siftalpha.studio"
    compileSdk = 36
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.siftalpha.studio"
        minSdk = 26
        targetSdk = 36
        // alpha43-r24 closes Web continuity gaps: bounded Internal discovery retries, stable
        // foreground Web presentation, CPython foreground-runtime leases, and learned endpoints.
        // Rich Result, External execution semantics and Worker freeze remain unchanged.
        // alpha43-r38 preserves the frozen r34 baseline plus r35-r37 CLI work, then adds
        // an adaptive app-owned localhost Result Web Host for successful one-shot program output.
        // alpha43-r39 adds Android 15+ system-bar safe insets to legacy View surfaces.
        // alpha43-r40 fixes polyglot prepare ordering: frontend build assets precede Python packaging.
        // alpha43-r41 installs declared Web extras for detected Vite-backed Python Web projects.
        // alpha43-r42 adds high-confidence project-owned Python Web application launch discovery.
        // alpha43-r43 extends that launch/preparation contract into Internal Alpine.
        // alpha43-r44 scales bounded Internal R source staging for modern multi-file projects.
        // alpha43-r44-storage1 unifies Internal R storage inspection/cleanup with the existing
        // External Provider storage manager without making Termux a prerequisite for Internal R.
        // alpha43-r45a1 freezes the External Provider multi-project concurrency contract before
        // the shared per-project observation model is applied to Embedded R.
        // alpha43-r45b1 replaces the single Embedded R poll pointer with project-scoped polling.
        // alpha43-r45b2 keeps verified Web presentation stable across Activity lifecycle resume
        // while a fresh Endpoint Probe revalidates the same project-owned localhost endpoint.
        // alpha43-r46 binds project environments to their Python Runtime identity and makes
        // External Python re-prepare replace the prior venv only after a clean prepare succeeds.
        // alpha43-r46.2 adds fast common-Web recognition, PREPARE-time background rediscovery,
        // and verified launch-contract learning before the existing deep Web discovery fallback.
        // alpha43-r46.3 fixes generic External Python venv relocation so pip-generated console
        // scripts keep a stable interpreter prefix while failed prepares still roll back cleanly.
        // alpha43-r48a6 realigns Normal Mode and Developer Workspace on the Shared Core and adds
        // the shared External Provider preflight/bridge readiness contract.
        // R48-D2 adds the explicitly authorized shared background-reliability reminder
        // and indeterminate Runtime activity indicator without changing Runtime deadlines.
        // R48-D3 implements the approved SiftAlpha X Normal Mode visual system.
        // v196 adds the mandatory R48-D2 parity repair: no baseline capability may be removed
        // or merged by presentation work; secondary Normal Mode routes receive the same design system.
        // v197 repairs the running-state presentation and launch transition from real-device feedback:
        // result readiness follows the existing Open fact, running uses breathing motion, the launch
        // brand transition dwells 3-5 seconds with the original logo, and Stop adopts the blue system tone.
        // v198 links execution completion to the existing Open-ready fact, closes the running ring when
        // a result is available, adds restrained inner data-flow lines, and evolves the launch brand
        // transition into an S-path code/data convergence that resolves into the unchanged original logo.
        // v199 rebuilds the Normal Mode launch page in code to match the approved full-screen concept:
        // dense curved code/data streams from top-right and bottom-left converge into the unchanged
        // real app logo, with SiftAlpha X wordmark, Chinese tagline, and restrained bottom accent.
        // v200 changes only code/data flow motion: independent upper/lower clocks, staggered token phases,
        // soft entry/exit envelopes and subtle curve drift remove the rigid synchronized movement while
        // preserving the approved launch composition, timing, logo, text and R48-D2 functionality.
        // v201 refines that motion into a galaxy-style convergence: wide particle clouds at both edges
        // taper into a single soft filament immediately before the real logo, while all long rigid rails
        // are removed to eliminate the visible cut-line effect.
        // v202 increases the galaxy density and replaces sparse streaks with 24 curved rotating strands
        // per side plus denser code/data particles. Upper-right flow now lands on the visible upper S end
        // and lower-left flow lands on the visible lower S end instead of converging on the logo center.
        // v204 intentionally restores the v202 launch composition and changes only convergence density:
        // 40 curved strands per side, 120 code tokens, and 144 luminous particles per side. No phase
        // headers, extra launch copy, layout changes, logo changes, or R48-D2 functional changes.
        // v205 preserves that v204 page exactly and changes only the convergence choreography:
        // upper-right data spirals around the logo into the lower-left S end; lower-left data spirals
        // around the logo into the upper-right S end; the incoming code then fills a fragment-built S
        // which continuously resolves into the unchanged real application logo.
        versionCode = 205
        versionName = "0.8.0-alpha43-r48d3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCPYTHON_PREFIX_DIR=${embeddedCpythonPrefix.absolutePath}",
                    "-DCPYTHON_VERSION=3.14",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(embeddedCpythonAssets)
            assets.srcDir(internalAlpineAssets)
            jniLibs.srcDir(embeddedCpythonJniLibs)
            jniLibs.srcDir(internalAlpineJniLibs)
        }
    }

    androidResources {
        noCompress += "tgzblob"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildFeatures {
        compose = true
    }

    if (trustedSigningEnabled) {
        signingConfigs.create("siftalphaTrustedDebug") {
            storeFile = file(trustedKeystoreFile!!)
            storePassword = trustedStorePassword!!
            keyAlias = trustedKeyAlias!!
            keyPassword = trustedKeyPassword!!
        }
    }

    buildTypes {
        getByName("debug") {
            if (trustedSigningEnabled) {
                signingConfig = signingConfigs.getByName("siftalphaTrustedDebug")
            }
        }
        getByName("release") {
            if (trustedSigningEnabled) {
                signingConfig = signingConfigs.getByName("siftalphaTrustedDebug")
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.03.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.tomlj:tomlj:1.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    dependsOn("testDebugUnitTest")
    dependsOn(prepareEmbeddedCpython)
    dependsOn(prepareInternalAlpine)
}

tasks.configureEach {
    if (
        name == "preDebugBuild" ||
        name == "externalNativeBuildDebug" ||
        name.contains("CMake", ignoreCase = true) ||
        name.contains("mergeDebugAssets", ignoreCase = true)
    ) {
        dependsOn(prepareEmbeddedCpython)
        dependsOn(prepareInternalAlpine)
    }
}
