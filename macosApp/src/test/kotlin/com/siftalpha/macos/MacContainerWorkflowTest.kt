package com.siftalpha.macos

import com.siftalpha.studio.platform.CapabilityAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacContainerWorkflowTest {
    private val healthyFacts = MacSystemFacts(
        osVersion = "15.0",
        osMajor = 15,
        architecture = "arm64",
        processorCount = 10,
        physicalMemoryBytes = 16L * 1024L * 1024L * 1024L,
        usableDiskBytes = 200L * 1024L * 1024L * 1024L,
    )

    @Test
    fun composeProjectIdentityIsStableAndProjectScoped() {
        val a1 = MacComposeProjectIdentity.forProject("macos:project-a")
        val a2 = MacComposeProjectIdentity.forProject("macos:project-a")
        val b = MacComposeProjectIdentity.forProject("macos:project-b")

        assertEquals(a1, a2)
        assertNotEquals(a1, b)
        assertTrue(a1.startsWith("siftalpha-"))
    }

    @Test
    fun selectorPrefersReadyDockerAndRequiresCompose() {
        val docker = MacContainerProviderSnapshot(
            kind = MacContainerProviderKind.DOCKER,
            availability = CapabilityAvailability.AVAILABLE,
            executablePath = "/usr/local/bin/docker",
            composeAvailable = true,
        )
        val podman = MacContainerProviderSnapshot(
            kind = MacContainerProviderKind.PODMAN,
            availability = CapabilityAvailability.AVAILABLE,
            executablePath = "/usr/local/bin/podman",
            composeAvailable = true,
        )

        val selected = MacComposeProviderSelector.select(listOf(podman, docker))
        assertNotNull(selected)
        assertEquals(MacContainerProviderKind.DOCKER, selected?.snapshot?.kind)

        assertNull(
            MacComposeProviderSelector.select(
                listOf(docker.copy(composeAvailable = false)),
            ),
        )
    }

    @Test
    fun composeFailureDiagnosticsPreserveOperationAndOutput() {
        val detail = MacComposeFailureDiagnostics.detail(
            operation = "pull",
            exitCode = 1,
            output = "web Pulling\nError response from daemon: registry unavailable\n",
        )

        assertTrue(detail.startsWith("docker compose pull failed exit=1"))
        assertTrue(detail.contains("web Pulling"))
        assertTrue(detail.contains("registry unavailable"))
    }

    @Test
    fun composeFailureDiagnosticsBoundVeryLargeOutput() {
        val detail = MacComposeFailureDiagnostics.detail(
            operation = "build",
            exitCode = 17,
            output = "prefix-" + "x".repeat(20_000) + "-useful-tail",
        )

        assertTrue(detail.startsWith("docker compose build failed exit=17"))
        assertTrue(detail.endsWith("useful-tail"))
        assertTrue(detail.length < 9_000)
    }

    @Test
    fun systemProxyParserCapturesMacOsHttpHttpsSocksAndExceptions() {
        val settings = MacSystemProxyDiscovery.parse(
            """
            <dictionary> {
              ExceptionsList : <array> {
                0 : 127.0.0.1
                1 : 192.168.0.0/16
                2 : localhost
                3 : *.local
                4 : <local>
              }
              HTTPEnable : 1
              HTTPPort : 7897
              HTTPProxy : 127.0.0.1
              HTTPSEnable : 1
              HTTPSPort : 7897
              HTTPSProxy : 127.0.0.1
              ProxyAutoConfigEnable : 0
              SOCKSEnable : 1
              SOCKSPort : 7897
              SOCKSProxy : 127.0.0.1
            }
            """.trimIndent(),
        )

        assertEquals("http://127.0.0.1:7897", settings.httpProxy)
        assertEquals("http://127.0.0.1:7897", settings.httpsProxy)
        assertEquals("socks5://127.0.0.1:7897", settings.socksProxy)
        assertTrue(settings.noProxy.orEmpty().contains("192.168.0.0/16"))
        assertTrue(settings.noProxy.orEmpty().contains("host.lima.internal"))
        assertTrue(!settings.noProxy.orEmpty().contains("<local>"))
    }

    @Test
    fun managedProxyPassesStaticSystemProxyThroughOfficialColimaEnvFlags() {
        val settings = MacSystemProxySettings(
            httpProxy = "http://127.0.0.1:7897",
            httpsProxy = "http://127.0.0.1:7897",
            socksProxy = "socks5://127.0.0.1:7897",
            noProxy = "localhost,127.0.0.1",
        )
        val args = MacManagedContainerProxy.colimaEnvironmentArguments(settings)

        assertTrue(args.contains("HTTP_PROXY=socks5h://127.0.0.1:7897"))
        assertTrue(args.contains("HTTPS_PROXY=socks5h://127.0.0.1:7897"))
        assertTrue(args.contains("NO_PROXY=localhost,127.0.0.1"))
        assertTrue(!args.any { it.startsWith("--dns") })
    }

    @Test
    fun managedProxyFallsBackToHttpWhenSystemHasNoSocksProxy() {
        val settings = MacSystemProxySettings(
            httpProxy = "http://127.0.0.1:8080",
            httpsProxy = "http://127.0.0.1:8080",
            socksProxy = null,
            noProxy = "localhost",
        )
        val args = MacManagedContainerProxy.colimaEnvironmentArguments(settings)

        assertTrue(args.contains("HTTP_PROXY=http://127.0.0.1:8080"))
        assertTrue(args.contains("HTTPS_PROXY=http://127.0.0.1:8080"))
        assertEquals("HTTP_SYSTEM_PROXY", MacManagedContainerProxy.strategy(settings))
    }

    @Test
    fun managedProxyPrefersSocks5hSoRegistryDnsStaysInsideProxyPath() {
        val settings = MacSystemProxySettings(
            httpProxy = "http://127.0.0.1:7897",
            httpsProxy = "http://127.0.0.1:7897",
            socksProxy = "socks5://127.0.0.1:7897",
            noProxy = "localhost",
        )
        val effective = MacManagedContainerProxy.effectiveDockerSettings(settings)

        assertEquals("socks5h://127.0.0.1:7897", effective.httpProxy)
        assertEquals("socks5h://127.0.0.1:7897", effective.httpsProxy)
        assertEquals("SOCKS5H_SYSTEM_PROXY", MacManagedContainerProxy.strategy(settings))
    }

    @Test
    fun managedProxyExplicitlyClearsPersistedHttpProxyWhenSystemProxyIsOff() {
        val args = MacManagedContainerProxy.colimaEnvironmentArguments(
            MacSystemProxySettings(null, null, null, null),
        )

        assertTrue(args.contains("HTTP_PROXY="))
        assertTrue(args.contains("HTTPS_PROXY="))
        assertTrue(args.contains("NO_PROXY="))
    }

    @Test
    fun managedProxyVerificationAcceptsColimaLoopbackToHostGatewayRewrite() {
        val settings = MacSystemProxySettings(
            httpProxy = "http://127.0.0.1:7897",
            httpsProxy = "http://127.0.0.1:7897",
            socksProxy = "socks5://127.0.0.1:7897",
            noProxy = "localhost",
        )

        assertTrue(
            MacManagedContainerProxy.dockerInfoMatches(
                settings,
                "socks5h://192.168.5.2:7897|socks5h://192.168.5.2:7897|localhost",
            ),
        )
        assertTrue(
            !MacManagedContainerProxy.dockerInfoMatches(
                settings,
                "||localhost",
            ),
        )
    }

    @Test
    fun managedDnsClearsPersistedExplicitResolversWithoutChangingOtherNetworkSettings() {
        val updated = MacManagedContainerDns.clearExplicitResolvers(
            """
            cpu: 2
            network:
              address: false
              dns:
                - 1.1.1.1
                - 8.8.8.8
              dnsHosts:
                host.docker.internal: host.lima.internal
              gatewayAddress: 192.168.5.2
            docker: {}
            """.trimIndent() + "\n",
        )

        assertTrue(updated.contains("network:\n  address: false\n  dns: []"))
        assertTrue(updated.contains("dnsHosts:\n    host.docker.internal: host.lima.internal"))
        assertTrue(updated.contains("gatewayAddress: 192.168.5.2"))
        assertTrue(updated.contains("docker: {}"))
        assertTrue(!updated.contains("- 1.1.1.1"))
        assertTrue(!updated.contains("- 8.8.8.8"))
    }

    @Test
    fun managedDnsAddsEmptyResolverListWhenNetworkBlockHasNone() {
        val updated = MacManagedContainerDns.clearExplicitResolvers(
            """
            cpu: 2
            network:
              address: false
              mode: shared
            docker: {}
            """.trimIndent(),
        )

        assertTrue(updated.contains("network:\n  dns: []\n  address: false"))
    }

    @Test
    fun managedDnsDetectsLoopbackAndRegistryTransportFailures() {
        assertTrue(
            MacManagedContainerDns.isRecoverableManagedNetworkFailure(
                "failed to resolve: dial tcp: lookup registry.example on [::1]:53: read: connection refused",
            ),
        )
        assertTrue(
            MacManagedContainerDns.isRecoverableManagedNetworkFailure(
                "failed to resolve reference image: dial tcp 203.0.113.10:443: i/o timeout",
            ),
        )
        assertTrue(
            MacManagedContainerDns.isRecoverableManagedNetworkFailure(
                "failed to resolve reference image: failed to do request: Head https://registry.example/v2/: EOF",
            ),
        )
        assertTrue(
            MacManagedContainerDns.isRecoverableManagedNetworkFailure(
                "failed to resolve reference image: proxyconnect tcp: EOF",
            ),
        )
        assertTrue(
            !MacManagedContainerDns.isRecoverableManagedNetworkFailure(
                "docker compose pull failed exit=1: manifest unknown",
            ),
        )
    }

    @Test
    fun managedDnsColimaArgumentsPreserveLimaHostResolver() {
        assertEquals(
            listOf("start", "--runtime", "docker"),
            MacManagedContainerDns.colimaStartArguments(),
        )
    }

    @Test
    fun managedDnsVmResolverRecoveryTargetsHostGatewayOnlyWhenNeeded() {
        assertTrue(
            !MacManagedContainerDns.requiresVmResolverRecovery(
                """
                # Generated by Colima
                nameserver 192.168.106.1
                """.trimIndent(),
            ),
        )
        assertTrue(
            MacManagedContainerDns.requiresVmResolverRecovery(
                """
                nameserver ::1
                nameserver 127.0.0.53
                """.trimIndent(),
            ),
        )
        assertTrue(
            MacManagedContainerDns.requiresVmResolverRecovery(
                """
                # Managed by SiftAlpha container DNS recovery
                nameserver 1.1.1.1
                """.trimIndent(),
            ),
        )

        val rendered = MacManagedContainerDns.renderHostResolverResolvConf("192.168.5.2")
        assertTrue(rendered.contains("nameserver 192.168.5.2"))
        assertTrue(!rendered.contains("1.1.1.1"))
        assertTrue(!rendered.contains("8.8.8.8"))
    }

    @Test
    fun managedDnsReadsManagedGatewayFromColimaConfig() {
        assertEquals(
            "192.168.77.2",
            MacManagedContainerDns.hostResolverGateway(
                """
                network:
                  gatewayAddress: 192.168.77.2
                """.trimIndent(),
            ),
        )
        assertEquals("192.168.5.2", MacManagedContainerDns.hostResolverGateway(""))
    }

    @Test
    fun checksumParserSelectsExactAssetFromMultiFileManifest() {
        val manifest = """
            bbdef91774885a0d05f7b048c4eb89ae2bcf3a0c252ae7ca7934e63df76d93c3 *lima-2.2.0-Darwin-arm64.tar.gz
            0d6f99c19f6e4bc3c92730c4c29d929e6927f0cb0a0ba1a84383367135a8ff31 *lima-2.2.0-Darwin-x86_64.tar.gz
        """.trimIndent()

        assertEquals(
            "0d6f99c19f6e4bc3c92730c4c29d929e6927f0cb0a0ba1a84383367135a8ff31",
            MacManagedContainerChecksum.expectedFor(
                manifest,
                "lima-2.2.0-Darwin-x86_64.tar.gz",
            ),
        )
        assertNull(
            MacManagedContainerChecksum.expectedFor(
                manifest,
                "lima-2.2.0-Darwin-riscv64.tar.gz",
            ),
        )
    }

    @Test
    fun buildxDarwinReleaseDigestsArePinnedForBothArchitectures() {
        assertEquals(
            "7003a7bae20e7741283db1e23dafdcb957776a8be85de3f459630b1dd4c19db0",
            MacManagedContainerToolchain.buildxReleaseSha256("x86_64"),
        )
        assertEquals(
            "c3cbbc820d578b0aa8158dd62ef1af25a0c8a75ef53331dbe4e219471e1dbe8c",
            MacManagedContainerToolchain.buildxReleaseSha256("arm64"),
        )
    }

    @Test
    fun checksumParserAcceptsSingleDigestSidecar() {
        val digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        assertEquals(
            digest,
            MacManagedContainerChecksum.expectedFor(digest + "\n", "single-asset"),
        )
    }

    @Test
    fun managedLimaStateUsesShortMacOsSafePath() {
        val home = java.io.File("/Users/" + "x".repeat(31))
        val limaHome = MacManagedContainerToolchain.limaHome(home)

        assertEquals(".siftalpha", limaHome.parentFile.name)
        assertEquals("l", limaHome.name)
        assertTrue(MacManagedContainerToolchain.projectedLimaSocketPathLength(home) < 104)
        assertTrue(!limaHome.absolutePath.contains("Library/Application Support"))
        assertTrue(!MacManagedContainerToolchain.usesFallbackStateRoot(home))
    }

    @Test
    fun managedLimaStateFallsBackToCompactHomePathWhenNeeded() {
        val home = java.io.File("/Users/" + "x".repeat(50))
        val limaHome = MacManagedContainerToolchain.limaHome(home)

        assertEquals(".sa", limaHome.parentFile.name)
        assertEquals("l", limaHome.name)
        assertTrue(MacManagedContainerToolchain.projectedLimaSocketPathLength(home) < 104)
        assertTrue(!MacManagedContainerToolchain.usesFallbackStateRoot(home))
    }

    @Test
    fun managedLimaStateMeasuresUtf8BytesForNonAsciiHomePaths() {
        val home = java.io.File("/Users/" + "用户".repeat(8))
        val limaHome = MacManagedContainerToolchain.limaHome(home)

        assertEquals(".sa", limaHome.parentFile.name)
        assertTrue(MacManagedContainerToolchain.projectedLimaSocketPathLength(home) < 104)
    }

    @Test
    fun managedLimaStateUsesGenericSystemFallbackWhenHomeCannotFitSocket() {
        val firstHome = java.io.File("/Users/" + "用户".repeat(10))
        val secondHome = java.io.File("/Users/" + "账户".repeat(10))
        val firstRoot = MacManagedContainerToolchain.managedStateRoot(firstHome)
        val secondRoot = MacManagedContainerToolchain.managedStateRoot(secondHome)

        assertEquals("/private/var/tmp", firstRoot.parentFile.absolutePath)
        assertTrue(firstRoot.name.startsWith("sax-"))
        assertNotEquals(firstRoot.name, secondRoot.name)
        assertTrue(MacManagedContainerToolchain.usesFallbackStateRoot(firstHome))
        assertTrue(MacManagedContainerToolchain.projectedLimaSocketPathLength(firstHome) < 104)
    }

    @Test
    fun managedEnvironmentSeparatesToolchainAndShortRuntimeState() {
        val home = java.io.File("/Users/tester")
        val toolchain = java.io.File(
            home,
            "Library/Application Support/SiftAlpha X/container-runtime/managed-test",
        )
        val env = MacManagedContainerToolchain.environment(
            root = toolchain,
            base = mapOf("PATH" to "/usr/bin"),
            userHome = home,
        )

        assertTrue(env.getValue("PATH").startsWith(java.io.File(toolchain, "bin").absolutePath))
        assertEquals(java.io.File(home, ".siftalpha/l").absolutePath, env["LIMA_HOME"])
        assertEquals(java.io.File(home, ".siftalpha/c").absolutePath, env["COLIMA_HOME"])
        assertEquals(java.io.File(toolchain, "cache/colima").absolutePath, env["COLIMA_CACHE_HOME"])
        assertEquals("sa", env["COLIMA_PROFILE"])
    }

    @Test
    fun advisorDistinguishesReadyInstalledButStoppedAndMissingProvider() {
        val ready = MacContainerProviderSnapshot(
            kind = MacContainerProviderKind.DOCKER,
            availability = CapabilityAvailability.AVAILABLE,
            executablePath = "/usr/local/bin/docker",
            composeAvailable = true,
        )
        assertEquals(
            MacContainerAdviceState.READY,
            MacContainerEnvironmentAdvisor.advise(healthyFacts, listOf(ready)).state,
        )

        val stopped = ready.copy(
            availability = CapabilityAvailability.UNKNOWN,
            composeAvailable = true,
        )
        assertEquals(
            MacContainerAdviceState.START_EXISTING_PROVIDER,
            MacContainerEnvironmentAdvisor.advise(healthyFacts, listOf(stopped)).state,
        )

        val constrained = healthyFacts.copy(
            processorCount = 2,
            physicalMemoryBytes = 6L * 1024L * 1024L * 1024L,
            usableDiskBytes = 15L * 1024L * 1024L * 1024L,
        )
        val missing = MacContainerEnvironmentAdvisor.advise(constrained, emptyList())
        assertEquals(MacContainerAdviceState.RESOURCE_WARNING, missing.state)
        assertTrue(missing.suggestedOptions.isNotEmpty())
        assertTrue(missing.warnings.size >= 2)
    }
}
