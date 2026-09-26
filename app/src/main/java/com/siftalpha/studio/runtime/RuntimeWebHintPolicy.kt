package com.siftalpha.studio.runtime

/**
 * Ordered Web port hints. Hints never prove ownership or availability.
 *
 * Runtime discovery must still establish that a listener belongs to the current project before a
 * hinted port can become a Web candidate.
 */
object RuntimeWebHintPolicy {
    private val frameworkDefaults = mapOf(
        "vite" to 5173,
        "python-vite-web" to 5173,
        "next" to 3000,
        "fastapi" to 8000,
        "flask" to 5000,
        "streamlit" to 8501,
        "gradio" to 7860,
        "dash" to 8050,
    )

    private val commonPorts = listOf(
        5173,
        3000,
        8000,
        8080,
        5000,
        8501,
        7860,
        8050,
        8888,
    )

    fun ports(
        detectedPort: Int?,
        framework: String?,
        learnedPort: Int? = null,
        detectedSource: String? = null,
    ): List<Int> = buildList {
        val normalizedFramework = framework?.trim()?.lowercase()
        val frameworkPort = normalizedFramework?.let(frameworkDefaults::get)
        val explicitConfiguredPort = detectedSource
            ?.trim()
            ?.lowercase() == "config"

        // Current project facts own endpoint ordering. Explicit configuration remains strongest.
        // For browser-first hybrid projects (for example Python + Vite), the UI framework port must
        // be tried before a backend/API port inferred from source. A learned endpoint is only a weak
        // cross-run hint and can never outrank current source/configuration evidence.
        if (explicitConfiguredPort) {
            detectedPort?.takeIf { it in 1..65535 }?.let(::add)
        }
        if (normalizedFramework in browserUiFrameworks) {
            frameworkPort?.let(::add)
            if (!explicitConfiguredPort) {
                detectedPort?.takeIf { it in 1..65535 }?.let(::add)
            }
        } else {
            if (!explicitConfiguredPort) {
                detectedPort?.takeIf { it in 1..65535 }?.let(::add)
            }
            frameworkPort?.let(::add)
        }
        learnedPort?.takeIf { it in 1..65535 }?.let(::add)
        addAll(commonPorts)
    }.distinct().take(MAX_HINT_PORTS)

    private val browserUiFrameworks = setOf(
        "vite",
        "python-vite-web",
        "next",
    )

    internal const val MAX_HINT_PORTS = 10
}
