package com.siftalpha.macos

import com.siftalpha.studio.platform.CapabilityAvailability
import java.io.File

data class MacSystemFacts(
    val osVersion: String,
    val osMajor: Int?,
    val architecture: String,
    val processorCount: Int,
    val physicalMemoryBytes: Long?,
    val usableDiskBytes: Long?,
)

enum class MacContainerAdviceState {
    READY,
    START_EXISTING_PROVIDER,
    COMPOSE_MISSING,
    INSTALL_PROVIDER,
    RESOURCE_WARNING,
}

data class MacContainerEnvironmentAdvice(
    val state: MacContainerAdviceState,
    val title: String,
    val detail: String,
    val suggestedOptions: List<String>,
    val warnings: List<String>,
)

object MacSystemFactsDiscovery {
    fun discover(
        properties: Map<String, String> = mapOf(
            "os.version" to System.getProperty("os.version").orEmpty(),
            "os.arch" to System.getProperty("os.arch").orEmpty(),
            "user.home" to System.getProperty("user.home").orEmpty(),
        ),
        processorCount: Int = Runtime.getRuntime().availableProcessors(),
        physicalMemoryBytes: Long? = physicalMemory(),
        usableDiskBytes: Long? = properties["user.home"]
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).usableSpace }
            ?.takeIf { it > 0L },
    ): MacSystemFacts {
        val osVersion = properties["os.version"].orEmpty()
        return MacSystemFacts(
            osVersion = osVersion,
            osMajor = osVersion.substringBefore('.').toIntOrNull(),
            architecture = properties["os.arch"].orEmpty().ifBlank { "unknown" },
            processorCount = processorCount.coerceAtLeast(1),
            physicalMemoryBytes = physicalMemoryBytes,
            usableDiskBytes = usableDiskBytes,
        )
    }

    private fun physicalMemory(): Long? =
        runCatching {
            val bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
            val method = bean.javaClass.methods.firstOrNull {
                it.name == "getTotalMemorySize" || it.name == "getTotalPhysicalMemorySize"
            } ?: return@runCatching null
            (method.invoke(bean) as? Number)?.toLong()
        }.getOrNull()?.takeIf { it > 0L }
}

object MacContainerEnvironmentAdvisor {
    private const val GIB = 1024L * 1024L * 1024L

    fun advise(
        facts: MacSystemFacts,
        providers: Collection<MacContainerProviderSnapshot>,
    ): MacContainerEnvironmentAdvice {
        val ready = providers.firstOrNull {
            it.availability == CapabilityAvailability.AVAILABLE && it.composeAvailable
        }
        if (ready != null) {
            return MacContainerEnvironmentAdvice(
                state = MacContainerAdviceState.READY,
                title = "容器环境已就绪",
                detail = providerLabel(ready.kind) + " 与 Compose 均可用，可以直接准备并运行 Compose 项目。",
                suggestedOptions = emptyList(),
                warnings = resourceWarnings(facts),
            )
        }

        val runtimeWithoutCompose = providers.firstOrNull {
            it.availability == CapabilityAvailability.AVAILABLE && !it.composeAvailable
        }
        if (runtimeWithoutCompose != null) {
            return MacContainerEnvironmentAdvice(
                state = MacContainerAdviceState.COMPOSE_MISSING,
                title = "容器运行环境可用，但缺少 Compose",
                detail = "已检测到 " + providerLabel(runtimeWithoutCompose.kind) +
                    "，但 Compose 命令不可用。请为当前 Provider 补齐兼容的 Compose 支持后刷新检测。",
                suggestedOptions = listOf("为现有 " + providerLabel(runtimeWithoutCompose.kind) + " 环境补齐 Compose"),
                warnings = resourceWarnings(facts),
            )
        }

        val installedButUnreachable = providers.firstOrNull {
            it.executablePath != null && it.availability == CapabilityAvailability.UNKNOWN
        }
        if (installedButUnreachable != null) {
            return MacContainerEnvironmentAdvice(
                state = MacContainerAdviceState.START_EXISTING_PROVIDER,
                title = "已找到容器工具，但运行环境尚未就绪",
                detail = "SiftAlpha 已找到 " + providerLabel(installedButUnreachable.kind) +
                    "，但当前无法连接其容器服务。请先启动或修复已有环境，再点击刷新。",
                suggestedOptions = listOf("优先恢复现有 " + providerLabel(installedButUnreachable.kind) + " 环境"),
                warnings = resourceWarnings(facts),
            )
        }

        val warnings = resourceWarnings(facts)
        val constrained = (facts.physicalMemoryBytes != null && facts.physicalMemoryBytes < 8L * GIB) ||
            facts.processorCount <= 2
        val options = if (constrained) {
            listOf(
                "轻量 Docker 兼容环境（例如 Colima；以其当前系统要求为准）",
                "Podman",
                "Docker Desktop（当前版本兼容且资源允许时）",
            )
        } else {
            listOf(
                "Docker Desktop（当前版本兼容时）",
                "轻量 Docker 兼容环境（例如 Colima）",
                "Podman",
            )
        }
        val state = if (warnings.isEmpty()) {
            MacContainerAdviceState.INSTALL_PROVIDER
        } else {
            MacContainerAdviceState.RESOURCE_WARNING
        }
        return MacContainerEnvironmentAdvice(
            state = state,
            title = "需要安装或启用容器环境",
            detail = "当前没有可用的 Docker/Podman + Compose。SiftAlpha 可以根据系统能力生成推荐环境方案；只会在用户主动点击“准备环境”或开发者环境修复时执行，也不会自动修改 CPU、内存或磁盘资源配置。",
            suggestedOptions = options,
            warnings = warnings,
        )
    }

    private fun resourceWarnings(facts: MacSystemFacts): List<String> = buildList {
        facts.physicalMemoryBytes?.let { bytes ->
            when {
                bytes < 4L * GIB -> add("物理内存低于 4 GiB，运行多服务容器项目可能明显受限。")
                bytes < 8L * GIB -> add("物理内存较紧张，建议优先选择轻量容器环境并减少并行服务。")
            }
        }
        if (facts.processorCount <= 2) {
            add("CPU 核心数较少，构建镜像和同时运行多个服务可能较慢。")
        }
        facts.usableDiskBytes?.let { bytes ->
            if (bytes < 20L * GIB) {
                add("可用磁盘空间低于 20 GiB，建议先释放空间再下载大型镜像。")
            }
        }
        val major = facts.osMajor
        if (major != null && major < 13) {
            add("当前系统低于 SiftAlpha X 的 macOS 13 完整支持基线。")
        }
    }

    private fun providerLabel(kind: MacContainerProviderKind): String = when (kind) {
        MacContainerProviderKind.DOCKER -> "Docker"
        MacContainerProviderKind.PODMAN -> "Podman"
    }
}
