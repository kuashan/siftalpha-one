package com.siftalpha.macos

import com.siftalpha.studio.platform.CapabilityAvailability
import com.siftalpha.studio.platform.PlatformCapability
import com.siftalpha.studio.platform.PlatformCapabilitySnapshot
import com.siftalpha.studio.platform.StandardPlatformCapabilities
import javax.swing.SwingUtilities
import javax.swing.UIManager

object MacPlatformCapabilities {
    val publishedCapabilities: List<PlatformCapability> = listOf(
        StandardPlatformCapabilities.HOST_PROCESS_EXECUTION,
        StandardPlatformCapabilities.SECURE_SECRET_STORAGE,
        StandardPlatformCapabilities.CONTAINER_RUNTIME,
    )

    fun snapshot(
        containerRuntimeAvailability: CapabilityAvailability = CapabilityAvailability.UNKNOWN,
    ): PlatformCapabilitySnapshot =
        PlatformCapabilitySnapshot(
            publishedCapabilities.associateWith { capability ->
                when (capability) {
                    StandardPlatformCapabilities.HOST_PROCESS_EXECUTION ->
                        CapabilityAvailability.AVAILABLE
                    StandardPlatformCapabilities.CONTAINER_RUNTIME ->
                        containerRuntimeAvailability
                    else -> CapabilityAvailability.UNKNOWN
                }
            },
        )
}

private fun capabilityLines(snapshot: PlatformCapabilitySnapshot): List<String> =
    MacPlatformCapabilities.publishedCapabilities.map { capability ->
        capability.id + " = " + snapshot.availabilityOf(capability)
    }

private fun printProbe(
    snapshot: PlatformCapabilitySnapshot,
    discovery: List<MacHostToolSnapshot>,
    containerProviders: List<MacContainerProviderSnapshot>,
) {
    println("SIFTALPHA_MACOS_HOST=READY")
    println("SIFTALPHA_CORE_LOAD=PASS")
    capabilityLines(snapshot).forEach { line ->
        println("SIFTALPHA_CAPABILITY=" + line)
    }
    discovery.forEach { tool ->
        println(
            "SIFTALPHA_HOST_TOOL=" +
                listOf(
                    tool.kind.id,
                    tool.availability.name,
                    tool.executablePath.orEmpty(),
                    tool.version.orEmpty(),
                ).joinToString("|"),
        )
    }
    containerProviders.forEach { provider ->
        println(
            "SIFTALPHA_CONTAINER_PROVIDER=" +
                listOf(
                    provider.kind.id,
                    provider.availability.name,
                    provider.executablePath.orEmpty(),
                    provider.version.orEmpty(),
                    provider.composeAvailable.toString(),
                    provider.composeVersion.orEmpty(),
                ).joinToString("|"),
        )
    }
    val managed = MacManagedPythonRuntime.locate()
    println(
        "SIFTALPHA_MANAGED_PYTHON=" +
            if (managed?.available == true) {
                "AVAILABLE|" + managed.pythonExecutable.absolutePath + "|" + managed.version().orEmpty()
            } else {
                "UNAVAILABLE"
            },
    )
}

fun main(args: Array<String>) {
    System.setProperty("apple.awt.application.name", "SiftAlpha X")

    val containerProviders = MacContainerRuntimeDiscovery().discoverAll()
    val capabilitySnapshot = MacPlatformCapabilities.snapshot(
        MacContainerRuntimeDiscovery.capabilityAvailability(containerProviders),
    )
    val discovery = MacHostRuntimeDiscovery().discoverAll()
    if ("--probe" in args) {
        printProbe(capabilitySnapshot, discovery, containerProviders)
        return
    }
    if ("--m3-process-probe" in args) {
        val result = MacM3SelfTest.run()
        println("SIFTALPHA_M3_PROCESS_PROBE=" + if (result.passed) "PASS" else "FAIL")
        result.lines.forEach { line -> println("SIFTALPHA_M3_PROCESS=" + line) }
        if (!result.passed) error("M3 process probe failed")
        return
    }
    if ("--m4-plan-probe" in args) {
        val result = MacM4PlanSelfTest.run()
        println("SIFTALPHA_M4_PLAN_PROBE=" + if (result.passed) "PASS" else "FAIL")
        result.lines.forEach { line -> println("SIFTALPHA_M4_PLAN=" + line) }
        if (!result.passed) error("M4 plan probe failed")
        return
    }
    if ("--m4-workflow-probe" in args) {
        val result = MacM42SelfTest.run()
        println("SIFTALPHA_M42_WORKFLOW_PROBE=" + if (result.passed) "PASS" else "FAIL")
        result.lines.forEach { line -> println("SIFTALPHA_M42=" + line) }
        if (!result.passed) error("M4.2 workflow probe failed")
        return
    }
    if ("--m5-normal-ui-probe" in args) {
        val result = MacM51NormalModeSelfTest.run()
        println("SIFTALPHA_M51_NORMAL_PROBE=" + if (result.passed) "PASS" else "FAIL")
        result.lines.forEach { line -> println("SIFTALPHA_M51=" + line) }
        if (!result.passed) error("M5.1 Normal Mode probe failed")
        return
    }
    if ("--m5-shared-state-probe" in args) {
        val result = MacM52SharedStateSelfTest.run()
        println("SIFTALPHA_M52_SHARED_STATE_PROBE=" + if (result.passed) "PASS" else "FAIL")
        result.lines.forEach { line -> println("SIFTALPHA_M52=" + line) }
        if (!result.passed) error("M5.2 shared state parity probe failed")
        return
    }
    if ("--m6-container-plan-probe" in args) {
        val result = MacM61PlanSelfTest.run()
        println("SIFTALPHA_M61_CONTAINER_PLAN_PROBE=" + if (result.passed) "PASS" else "FAIL")
        result.lines.forEach { line -> println("SIFTALPHA_M61=" + line) }
        if (!result.passed) error("M6.1 container capability / Compose plan probe failed")
        return
    }
    if ("--m6-compose-workflow-probe" in args) {
        val result = MacM62SelfTest.run()
        println("SIFTALPHA_M62_COMPOSE_WORKFLOW_PROBE=" + if (result.passed) "PASS" else "FAIL")
        result.lines.forEach { line -> println("SIFTALPHA_M62=" + line) }
        if (!result.passed) error("M6.2 Compose workflow / isolation probe failed")
        return
    }

    SwingUtilities.invokeLater {
        runCatching {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        }
        val controller = MacProductController(
            discovery = discovery,
            containerProviderSnapshotSource = { MacContainerRuntimeDiscovery().discoverAll() },
        )
        MacNormalModeWindow(controller).show()
    }
}
