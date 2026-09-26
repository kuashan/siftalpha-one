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
        // v206 implements the final approved four-stage opening motion without any stage titles,
        // numbers or explanatory captions: chaotic edge inflow -> S-shaped convergence ->
        // code/nebula S formation -> breathing reveal of the unchanged real app logo.
        // v207 changes the formation target from a generic S path to the actual logo geometry:
        // incoming code now settles directly onto the rounded-square shell, S ribbon and >_ terminal,
        // then the code-built logo continuously resolves into the unchanged real application logo.
        // v208 removes the noisy particle-cloud intermediate and makes the first settled form readable
        // as a true code-built logo: 216 code glyphs are laid out deterministically on the rounded-square
        // shell, seven-row S ribbon, and explicit >_ terminal before morphing into the real logo asset.
        // v209 follows the real-device correction: the intermediate state is ONLY a dense code-built S,
        // matching the approved reference image. No shell, no terminal, no particle cloud. Incoming code
        // converges into a 9-band S glyph lattice, holds clearly, then the unchanged real app logo resolves.
        // v210 replaces the path-band approximation with a true 2D S mask fill. Glyph targets are sampled
        // from a filled S area on a 30x36 grid, producing a broad, readable silhouette with a tight waist
        // before the unchanged real application logo resolves in place.
        // v211 fixes the remaining sparse-island failure: every accepted S-mask cell is now rendered as
        // a real code glyph directly on Canvas, so the intermediate reads as one continuous dense S instead
        // of several disconnected code clusters.
        // v212 removes the experimental custom code-opening sequence entirely and adds one lifecycle-aware
        // decorative-motion gate: Aurora, RunOrb and DotPulse leave infinite animation composition whenever
        // their Activity is no longer STARTED or Android system animations are disabled. Runtime work is unchanged.
        // v213 is the installable verification build for that motion-lifecycle change and restores the shared
        // Compose geometry Size import required by existing non-launch Normal Mode drawing code.
        // v214 completes the eight requested Normal Mode repairs: restore a static logo/text brand hold with no
        // code-convergence animation, simplify Home copy/colors/status/version placement, make descriptions editable,
        // compact Prepare to the current phase only, while preserving the v212 lifecycle-gated decorative motion.
        // v219 hardens External Provider lifecycle: bridge + real proot/Ubuntu capability proof,
        // action gating before PREPARING, pending PREPARE/RUN/REFRESH recovery, and shorter external
        // control-operation deadlines while preserving the long PREPARE hard cap.
        // v220 repairs Environment Plan identity so runtime-generated project files cannot invalidate
        // prepared environments, preserves r46 Python/runtime compatibility gates, and self-heals
        // interrupted External Python PREPARE rollback backups without touching Developer Mode source.
        // v221 moves External Python PREPARE's success boundary ahead of potentially slow rollback-
        // backup deletion and caps opportunistic backup cleanup so a committed READY environment
        // cannot leave the shared operation stuck in PREPARING for minutes.
        // r48d11-core1 begins platform-independent Core（核心） extraction without changing
        // Android Runtime（安卓运行时） behavior. RuntimeKind is now compiled from :core.
        // r48d11-core2 freezes the cross-platform Capability（能力） isolation contract.
        // Core may define optional capabilities but no platform is forced to implement all of them.
        // r48d11-m1.1 moves Runtime lifecycle parsing/decision policy into :core while
        // keeping Android UI/persistence compatibility facades unchanged.
        // r48d11-m1.2 separates provider-neutral project environment needs from
        // Android-specific backend selection and preparation execution.
        // r48d11-m1.2-r1 fixes Android foreground-resume recovery so a normal
        // background -> foreground transition cannot lock a healthy running project in RECOVERING.
        // r48d11-m1.3 moves project-operation arbitration and project-scoped
        // concurrency rules into the shared cross-platform Core.
        // r48d11-m1.4 introduces the cross-platform state-storage port and routes
        // shared lifecycle/operation persistence through the Android adapter.
        // r48d11-m1.5 introduces the cross-platform project-filesystem port and
        // routes Android SAF project tree and file CRUD through the adapter.
        // r48d11-m1.6 introduces the cross-platform project process-control
        // contract while preserving Android's accepted PID/PGID and Embedded R mechanisms.
        // r48d11-m1.6-r1 repairs Developer Mode External Provider return-to-app recovery:
        // recoverable Termux bridge/configuration states retain the original user intent and
        // onResume retries provider probing before resuming the deferred action exactly once.
        // v233 is Android-only: normal Python launch authority now outranks synthetic/learned
        // Web launchers; Web Discovery remains observational and macOS source is unchanged.
        versionCode = 233
        versionName = "0.8.0-alpha43-r48d11-android-launch-r1"
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
    implementation(project(":core"))

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
