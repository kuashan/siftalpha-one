package com.siftalpha.studio.runtime

/**
 * Platform-independent Runtime（运行时） classification shared by Android（安卓）,
 * macOS（苹果桌面系统） and Windows（微软桌面系统） hosts.
 *
 * This file intentionally imports no Android API and owns no platform process/file/UI behavior.
 */
enum class RuntimeKind(val id: String) {
    PYTHON("python"),
    NODE_JS("nodejs"),
    JVM("jvm"),
    GO("go"),
    RUST("rust"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromDeclaredType(raw: String?): RuntimeKind? = when (raw?.trim()?.lowercase()) {
            "python", "py" -> PYTHON
            "node", "nodejs", "javascript", "typescript", "js", "ts" -> NODE_JS
            "java", "kotlin", "jvm" -> JVM
            "go", "golang" -> GO
            "rust" -> RUST
            else -> null
        }
    }
}

data class RuntimeCandidate(
    val kind: RuntimeKind,
    val score: Int,
    val evidence: List<String>,
)

data class ProjectRuntimeProfile(
    val candidates: List<RuntimeCandidate>,
) {
    val primary: RuntimeCandidate?
        get() = candidates.firstOrNull()

    val isPolyglot: Boolean
        get() = candidates.count { it.score >= POLYGLOT_SCORE } > 1

    fun candidate(kind: RuntimeKind): RuntimeCandidate? = candidates.firstOrNull { it.kind == kind }

    companion object {
        const val POLYGLOT_SCORE = 50
    }
}
