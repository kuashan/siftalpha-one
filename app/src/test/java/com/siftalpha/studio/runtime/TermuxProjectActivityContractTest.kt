package com.siftalpha.studio.runtime

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxProjectActivityContractTest {
    @Test
    fun lightweightPrefixDoesNotCreateNestedExecutionLayer() {
        val script = TermuxProjectActivityContract.wrap(
            runtimeId = "project-a",
            operation = "status",
            shellScript = "printf 'RAW_PAYLOAD_OK\\n'",
        )

        assertFalse(script.contains("bash -lc"))
        assertFalse(script.contains("bash -c"))
        assertFalse(script.contains("sh -c"))
        assertFalse(script.contains("eval "))
        assertFalse(script.contains("hostPreamble"))
        assertFalse(script.contains("hostProcessHelpers"))
        assertFalse(script.contains("proot-distro"))
        assertFalse(script.contains("SIFTALPHA_REAL_PROOT_DISTRO"))
        assertFalse(script.contains("ROOT="))
        assertFalse(script.contains("activity_pid=\$!"))
        assertFalse(script.contains("wait"))
        assertTrue(script.contains("_siftalpha_activity_pid_alive()"))
        assertTrue(script.contains("_siftalpha_activity_group_alive()"))
        assertTrue(script.contains("_siftalpha_activity_pid_file"))
        assertTrue(script.contains("_siftalpha_activity_pgid_file"))
        assertTrue(script.contains("trap _siftalpha_activity_on_exit EXIT"))
        assertTrue(script.contains("trap '_siftalpha_activity_cleanup; exit 130' INT"))
        assertTrue(script.contains("trap '_siftalpha_activity_cleanup; exit 143' TERM"))
        assertTrue(script.contains("trap '_siftalpha_activity_cleanup; exit 129' HUP"))
        val pgidWrite = "printf '%s\\n' \"${'$'}_siftalpha_activity_pid\" >\"${'$'}_siftalpha_activity_pgid_file\""
        assertFalse(script.contains(pgidWrite))
    }

    @Test
    fun rawPayloadSeesOwnershipAndPreservesOutputAndExitCode() {
        val home = Files.createTempDirectory("siftalpha-activity-foreground").toFile()
        try {
            val script = TermuxProjectActivityContract.wrap(
                runtimeId = "project-a",
                operation = "clean",
                shellScript = """
                    if [ -s "${'$'}HOME/.siftalpha/runtime/project-a.activity.clean.pid" ]; then
                      printf 'OWNERSHIP_VISIBLE=YES\\n'
                    else
                      printf 'OWNERSHIP_VISIBLE=NO\\n'
                    fi
                    printf 'RAW_PAYLOAD_OK\\n'
                    printf 'RAW_PAYLOAD_STDERR\\n' >&2
                    exit 37
                """.trimIndent(),
            )

            val result = execute(script, home)

            assertEquals(37, result.exitCode)
            assertTrue(result.stdout.contains("SIFTALPHA_EXTERNAL_ACTIVITY_OPERATION=clean"))
            assertTrue(result.stdout.contains("SIFTALPHA_EXTERNAL_ACTIVITY_OWNER_PID="))
            assertTrue(result.stdout.contains("OWNERSHIP_VISIBLE=YES"))
            assertTrue(result.stdout.contains("RAW_PAYLOAD_OK"))
            assertTrue(result.stderr.contains("RAW_PAYLOAD_STDERR"))
            assertFalse(File(home, ".siftalpha/runtime/project-a.activity.clean.pid").exists())
            assertFalse(File(home, ".siftalpha/runtime/project-a.activity.clean.pgid").exists())
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun wrapperDoesNotInitializeTheRuntimeHostForThePayload() {
        val script = TermuxProjectActivityContract.wrap(
            runtimeId = "project-a",
            operation = "prepare",
            shellScript = "printf 'SIFTALPHA_PAYLOAD_HOST_INIT=1\\n'",
        )

        assertTrue(script.contains("SIFTALPHA_PAYLOAD_HOST_INIT=1"))
        assertFalse(script.contains("ROOT="))
        assertFalse(script.contains("proot-distro"))
        assertFalse(script.contains("SIFTALPHA_TERMUX_RESOLV"))
        assertFalse(script.contains("proot_wrapper_dir"))
    }

    @Test
    fun stalePgidFileIsRemovedWithoutCreatingANewPgidFile() {
        val home = Files.createTempDirectory("siftalpha-activity-stale-pgid").toFile()
        try {
            val runtime = File(home, ".siftalpha/runtime")
            assertTrue(runtime.mkdirs())
            Files.write(
                File(runtime, "project-a.activity.status.pgid").toPath(),
                "99999999\n".toByteArray(StandardCharsets.UTF_8),
            )

            val result = execute(
                TermuxProjectActivityContract.wrap(
                    runtimeId = "project-a",
                    operation = "status",
                    shellScript = "printf 'STALE_PGID_RECOVERED=YES\\n'",
                ),
                home,
            )

            assertEquals(0, result.exitCode)
            assertTrue(result.stdout.contains("STALE_PGID_RECOVERED=YES"))
            assertFalse(File(runtime, "project-a.activity.status.pgid").exists())
            assertFalse(File(runtime, "project-a.activity.status.pid").exists())
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun differentProjectsCanOwnTheSameOperationConcurrently() {
        val home = Files.createTempDirectory("siftalpha-activity-multi-project").toFile()
        try {
            val readyA = File(home, "project-a.ready")
            val releaseA = File(home, "project-a.release")
            val scriptA = TermuxProjectActivityContract.wrap(
                runtimeId = "project-a",
                operation = "status",
                shellScript = """
                    touch '${readyA.absolutePath}'
                    while [ ! -f '${releaseA.absolutePath}' ]; do
                      sleep 0.05
                    done
                    printf 'PROJECT_A_DONE=YES\\n'
                """.trimIndent(),
            )
            val processA = ProcessBuilder("bash", "-lc", scriptA).apply {
                environment()["HOME"] = home.absolutePath
            }.start()

            val deadline = System.nanoTime() + 5_000_000_000L
            while (!readyA.isFile && processA.isAlive && System.nanoTime() < deadline) {
                Thread.sleep(20)
            }
            assertTrue("project A must own its status activity before B starts", readyA.isFile)
            assertTrue("project A must still be active", processA.isAlive)

            val resultB = execute(
                TermuxProjectActivityContract.wrap(
                    runtimeId = "project-b",
                    operation = "status",
                    shellScript = "printf 'PROJECT_B_OK=YES\\n'",
                ),
                home,
            )

            assertEquals(0, resultB.exitCode)
            assertTrue(resultB.stdout.contains("PROJECT_B_OK=YES"))
            assertFalse(resultB.stdout.contains("SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE"))
            assertTrue(
                "project A ownership file must remain while project B completes",
                File(home, ".siftalpha/runtime/project-a.activity.status.pid").isFile,
            )
            assertFalse(
                "project B ownership file must be cleaned independently",
                File(home, ".siftalpha/runtime/project-b.activity.status.pid").exists(),
            )

            releaseA.writeText("release")
            assertEquals(0, processA.waitFor())
            assertFalse(File(home, ".siftalpha/runtime/project-a.activity.status.pid").exists())
        } finally {
            runCatching { File(home, "project-a.release").writeText("release") }
            home.deleteRecursively()
        }
    }

    @Test
    fun liveOwnershipRejectsNewOperationBeforePayload() {
        val home = Files.createTempDirectory("siftalpha-activity-live").toFile()
        try {
            val runtime = File(home, ".siftalpha/runtime")
            assertTrue(runtime.mkdirs())
            Files.write(
                File(runtime, "project-a.activity.clean.pid").toPath(),
                ProcessHandle.current().pid().toString().toByteArray(StandardCharsets.UTF_8),
            )

            val result = execute(
                TermuxProjectActivityContract.wrap(
                    runtimeId = "project-a",
                    operation = "clean",
                    shellScript = "printf 'SHOULD_NOT_RUN\\n'",
                ),
                home,
            )

            assertEquals(80, result.exitCode)
            assertTrue(result.stdout.contains("SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE"))
            assertFalse(result.stdout.contains("SHOULD_NOT_RUN"))
        } finally {
            home.deleteRecursively()
        }
    }

    private fun execute(script: String, home: File): ScriptResult {
        val process = ProcessBuilder("bash", "-lc", script).apply {
            environment()["HOME"] = home.absolutePath
        }.start()
        val stdout = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
        val stderr = process.errorStream.readBytes().toString(StandardCharsets.UTF_8)
        return ScriptResult(
            exitCode = process.waitFor(),
            stdout = stdout,
            stderr = stderr,
        )
    }

    private data class ScriptResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}
