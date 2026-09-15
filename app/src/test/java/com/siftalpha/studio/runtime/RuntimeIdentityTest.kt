package com.siftalpha.studio.runtime

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeIdentityTest {

    @Test
    fun `identity source distinguishes complete partial legacy and unavailable scopes`() {
        assertEquals(
            RuntimeIdentitySource.FULL_IDENTITY,
            RuntimeIdentityStore.sourceFor(
                hostIdentityAvailable = true,
                guestIdentityAvailable = true,
                legacyPidAvailable = false,
            ),
        )
        assertEquals(
            RuntimeIdentitySource.HOST_ONLY,
            RuntimeIdentityStore.sourceFor(
                hostIdentityAvailable = true,
                guestIdentityAvailable = false,
                legacyPidAvailable = true,
            ),
        )
        assertEquals(
            RuntimeIdentitySource.GUEST_ONLY,
            RuntimeIdentityStore.sourceFor(
                hostIdentityAvailable = false,
                guestIdentityAvailable = true,
                legacyPidAvailable = true,
            ),
        )
        assertEquals(
            RuntimeIdentitySource.LEGACY_PID,
            RuntimeIdentityStore.sourceFor(
                hostIdentityAvailable = false,
                guestIdentityAvailable = false,
                legacyPidAvailable = true,
            ),
        )
        assertEquals(
            RuntimeIdentitySource.UNAVAILABLE,
            RuntimeIdentityStore.sourceFor(
                hostIdentityAvailable = false,
                guestIdentityAvailable = false,
                legacyPidAvailable = false,
            ),
        )
    }

    @Test
    fun `generic identity round trips host and guest process fields independently`() {
        val root = Files.createTempDirectory("siftalpha-identity").toFile()
        try {
            val store = RuntimeIdentityStore(root)
            val identity = RuntimeIdentity(
                runtimeId = "runtime-id",
                runtimeToken = "token-1",
                startTime = 1_700_000_000L,
                hostSessionPid = 101L,
                hostSessionPgid = 101L,
                guestRootPid = 7L,
                guestRootPgid = 7L,
            )

            assertTrue(store.validate(identity, expectedRuntimeId = "runtime-id", requireHost = true, requireGuest = true))
            assertTrue(store.write(identity))

            val restored = store.read("runtime-id")
            assertNotNull(restored)
            assertEquals(101L, restored!!.hostSessionPid)
            assertEquals(101L, restored.hostSessionPgid)
            assertEquals(7L, restored.guestRootPid)
            assertEquals(7L, restored.guestRootPgid)
            assertFalse(RuntimeIdentityStore.encode(identity).contains("pythonPid"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `host and guest sidecars merge without confusing their PIDs`() {
        val root = Files.createTempDirectory("siftalpha-identity-split").toFile()
        try {
            val store = RuntimeIdentityStore(root)
            val metadata = RuntimeIdentity(
                runtimeId = "runtime-id",
                runtimeToken = "token-2",
                startTime = 42L,
            )
            assertTrue(store.writeHost(metadata.copy(hostSessionPid = 202L, hostSessionPgid = 202L)))
            assertTrue(store.writeGuest(metadata.copy(guestRootPid = 9L, guestRootPgid = 9L)))

            val restored = store.read("runtime-id")
            assertNotNull(restored)
            assertEquals(202L, restored!!.hostSessionPid)
            assertEquals(202L, restored.hostSessionPgid)
            assertEquals(9L, restored.guestRootPid)
            assertEquals(9L, restored.guestRootPgid)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `missing and malformed identity data fail closed`() {
        val root = Files.createTempDirectory("siftalpha-identity-invalid").toFile()
        try {
            val store = RuntimeIdentityStore(root)
            assertNull(store.read("missing"))
            assertFalse(
                store.validate(
                    RuntimeIdentity("runtime-id", "", 1L),
                ),
            )

            val runtimeDirectory = root.resolve("runtime-id")
            runtimeDirectory.mkdirs()
            runtimeDirectory.resolve(RuntimeIdentityStore.IDENTITY_FILE_NAME).writeText(
                """
                ${RuntimeIdentityStore.SCHEMA_KEY}=1
                ${RuntimeIdentityStore.RUNTIME_ID_KEY}=runtime-id
                ${RuntimeIdentityStore.RUNTIME_TOKEN_KEY}=token
                ${RuntimeIdentityStore.START_TIME_KEY}=not-a-time
                ${RuntimeIdentityStore.HOST_SESSION_PID_KEY}=not-a-pid
                """.trimIndent(),
            )
            assertNull(store.read("runtime-id"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `token and start time mismatch expires an otherwise valid identity`() {
        val root = Files.createTempDirectory("siftalpha-identity-expiry").toFile()
        try {
            val store = RuntimeIdentityStore(root)
            val identity = RuntimeIdentity("runtime-id", "token-3", 77L, guestRootPid = 10L)

            assertFalse(store.isExpired(identity, expectedRuntimeToken = "token-3", expectedStartTime = 77L))
            assertTrue(store.isExpired(identity, expectedRuntimeToken = "different-token"))
            assertTrue(store.isExpired(identity, expectedStartTime = 78L))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `guest writer reports the runner root identity and publication failure`() {
        val script = RuntimeIdentityStore.guestIdentityWriterShell()

        assertTrue(script.contains("runtime_identity_guest_pid=${'$'}${'$'}"))
        assertTrue(script.contains("SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT=RUNNER"))
        assertTrue(script.contains("SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT_PID=%s"))
        assertTrue(script.contains("SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT=NOT_PUBLISHED"))
    }
}
