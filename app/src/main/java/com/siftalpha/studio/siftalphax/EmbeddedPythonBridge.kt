package com.siftalpha.studio.siftalphax

/**
 * JNI boundary for the isolated SiftAlpha X experiment.
 *
 * This class intentionally has no dependency on RuntimeCommandHost, TermuxBackend, or the
 * production RuntimeAdapter hierarchy. The native library is packaged only for arm64-v8a.
 */
internal object EmbeddedPythonBridge {
    init {
        System.loadLibrary("python3.14")
        System.loadLibrary("siftalpha_x_native")
    }

    @JvmStatic
    external fun nativeStart(
        home: String,
        projectIdentity: String,
        executionRoot: String,
        environmentSitePackages: String,
        entrypoint: String,
        workingDirectory: String,
        sessionId: String,
        generation: Long,
    ): Boolean

    @JvmStatic
    external fun nativeRequestStop(): Boolean

    @JvmStatic
    external fun nativeSnapshot(): String
}
