package com.siftalpha.macos

import com.siftalpha.studio.platform.CapabilityAvailability
import java.io.File
import java.util.concurrent.TimeUnit

data class MacSystemFacts(
    val osVersion: String,
    val osMajor: Int?,
    val architecture: String,
    val processorCount: Int,
    val physicalMemoryBytes: Long?,
    val usableDiskBytes: Long?,
)

data class MacManagedVmResourceFacts(
    val cpuCount: Int?,
    val memoryBytes: Long?,
)

enum class MacManagedResourceDecision {
    CURRENT_OK,
    REPAIR_REQUIRED,
    HOST_INSUFFICIENT,
    UNKNOWN,
}

data class MacManagedVmResourceRecommendation(
    val currentCpuCount: Int?,
    val currentMemoryBytes: Long?,
    val recommendedCpuCount: Int?,
    val recommendedMemoryBytes: Long?,
    val maximumSafeMemoryBytes: Long?,
    val decision: MacManagedResourceDecision,
)

object MacManagedResourcePolicy {
    const val GIB = 1024L * 1024L * 1024L

    fun recommend(
        host: MacSystemFacts,
        current: MacManagedVmResourceFacts,
    ): MacManagedVmResourceRecommendation {
        val hostMemory = host.physicalMemoryBytes?.takeIf { it > 0L }
        val currentMemory = current.memoryBytes?.takeIf { it > 0L }
        val currentCpu = current.cpuCount?.takeIf { it > 0 }
        if (hostMemory == null || currentMemory == null || currentCpu == null) {
            return MacManagedVmResourceRecommendation(
                currentCpuCount = current.cpuCount,
                currentMemoryBytes = current.memoryBytes,
                recommendedCpuCount = null,
                recommendedMemoryBytes = null,
                maximumSafeMemoryBytes = null,
                decision = MacManagedResourceDecision.UNKNOWN,
            )
        }

        // Build Boost keeps a bounded host reserve while giving managed Compose builds
        // enough headroom to avoid repeated OOM/retry cycles on 8 GiB-class Macs.
        val reservedForHost = maxOf(3L * GIB, hostMemory / 4L)
        val maximumSafeMemory = (hostMemory - reservedForHost).coerceAtLeast(0L)
        val hostInsufficient = hostMemory < 8L * GIB || maximumSafeMemory < 5L * GIB
        if (hostInsufficient) {
            return MacManagedVmResourceRecommendation(
                currentCpuCount = currentCpu,
                currentMemoryBytes = currentMemory,
                recommendedCpuCount = null,
                recommendedMemoryBytes = null,
                maximumSafeMemoryBytes = maximumSafeMemory,
                decision = MacManagedResourceDecision.HOST_INSUFFICIENT,
            )
        }

        val maximumSafeCpu = maxOf(1, host.processorCount - 1)
        val recommendedCpu = minOf(
            maximumSafeCpu,
            maxOf(1, (host.processorCount * 3 + 3) / 4),
        )
        val recommendedMemory = minOf(
            maximumSafeMemory,
            maxOf(5L * GIB, hostMemory / 2L),
        )
        val decision = if (currentMemory >= recommendedMemory && currentCpu >= recommendedCpu) {
            MacManagedResourceDecision.CURRENT_OK
        } else {
            MacManagedResourceDecision.REPAIR_REQUIRED
        }
        return MacManagedVmResourceRecommendation(
            currentCpuCount = currentCpu,
            currentMemoryBytes = currentMemory,
            recommendedCpuCount = recommendedCpu,
            recommendedMemoryBytes = recommendedMemory,
            maximumSafeMemoryBytes = maximumSafeMemory,
            decision = decision,
        )
    }
}

object MacManagedVmResourceParser {
    private val cpuPattern = Regex("\\\"(?:cpu|cpus)\\\"\\s*:\\s*(\\d+)")
    private val memoryPattern = Regex("\\\"memory\\\"\\s*:\\s*(\\d+)")

    fun parse(text: String): MacManagedVmResourceFacts? {
        val cpu = cpuPattern.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val memoryBytes = memoryPattern.find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (cpu == null && memoryBytes == null) return null
        return MacManagedVmResourceFacts(cpuCount = cpu, memoryBytes = memoryBytes)
    }
}

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

internal data class MacSysctlMemoryRead(
    val stdout: String,
    val exitCode: Int,
    val timedOut: Boolean,
)

object MacSystemFactsDiscovery {
    private const val SYSCTL_TIMEOUT_MILLIS = 2_000L

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

    internal fun physicalMemory(
        jvmMemory: () -> Long? = ::jvmPhysicalMemory,
        sysctlMemory: () -> MacSysctlMemoryRead? = ::readSysctlMemory,
    ): Long? {
        val jvmBytes = runCatching { jvmMemory() }
            .getOrNull()
            ?.takeIf { it > 0L }
        if (jvmBytes != null) return jvmBytes

        val sysctl = runCatching { sysctlMemory() }.getOrNull() ?: return null
        if (sysctl.timedOut || sysctl.exitCode != 0) return null
        return sysctl.stdout.trim().toLongOrNull()?.takeIf { it > 0L }
    }

    private fun jvmPhysicalMemory(): Long? =
        runCatching {
            val bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
            val method = bean.javaClass.methods.firstOrNull {
                it.name == "getTotalMemorySize" || it.name == "getTotalPhysicalMemorySize"
            } ?: return@runCatching null
            (method.invoke(bean) as? Number)?.toLong()
        }.getOrNull()?.takeIf { it > 0L }

    private fun readSysctlMemory(): MacSysctlMemoryRead? = runCatching {
        val process = ProcessBuilder("/usr/sbin/sysctl", "-n", "hw.memsize")
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(SYSCTL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            process.destroy()
            if (!process.waitFor(200L, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
            }
            return@runCatching MacSysctlMemoryRead(
                stdout = "",
                exitCode = -1,
                timedOut = true,
            )
        }

        val stdout = process.inputStream.bufferedReader().use { it.readText().take(32 * 1024) }
        MacSysctlMemoryRead(
            stdout = stdout,
            exitCode = process.exitValue(),
            timedOut = false,
        )
    }.getOrNull()
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
            detail = "当前没有可用的 Docker/Podman + Compose。SiftAlpha 可以根据系统能力生成推荐环境方案；初次安装不会自动修改 CPU、内存或磁盘资源配置，只有托管容器构建明确遇到内存不足时，才会进行一次有上限的资源恢复。",
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
