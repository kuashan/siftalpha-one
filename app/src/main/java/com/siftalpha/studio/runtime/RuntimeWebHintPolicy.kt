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
    ): List<Int> = buildList {
        detectedPort?.takeIf { it in 1..65535 }?.let(::add)
        framework
            ?.trim()
            ?.lowercase()
            ?.let(frameworkDefaults::get)
            ?.let(::add)
        addAll(commonPorts)
    }.distinct().take(MAX_HINT_PORTS)

    internal const val MAX_HINT_PORTS = 9
}
