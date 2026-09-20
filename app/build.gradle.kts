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
        // alpha43-r31 keeps the r24 product/Web baseline and r30 caches/Web wiring,
        // then moves Internal Alpine Process lifecycle ownership into the foreground service.
        versionCode = 157
        versionName = "0.8.0-alpha43-r31"
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
