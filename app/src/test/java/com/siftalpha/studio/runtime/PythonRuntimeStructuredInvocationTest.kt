package com.siftalpha.studio.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonRuntimeStructuredInvocationTest {

    private class FakeHost : RuntimeCommandHost {
        val quotedInputs = mutableListOf<String>()

        override fun runtimeSupported(): Boolean = true
        override fun runtimeUnsupportedReason(): String = "unsupported"
        override fun sharedRoot(): String = "/storage/emulated/0/AcodeProjects"
        override fun runtimeId(folderName: String): String = "runtime-id"
        override fun sh(value: String): String {
            quotedInputs += value
            return "'" + value.replace("'", "'\"'\"'") + "'"
        }
        override fun wrapUbuntu(inner: String): String = "HOST_WRAP_BEGIN\n$inner\nHOST_WRAP_END"
        override fun hostPreamble(): String = """
            set -e
            ROOT='/storage/emulated/0/AcodeProjects'
            runtime_dir="${'$'}HOME/.siftalpha/runtime"
            mkdir -p "${'$'}runtime_dir"
        """.trimIndent()
        override fun hostProcessHelpers(): String = """
            siftalpha_pid_alive() { return 1; }
            siftalpha_group_alive() { return 1; }
            siftalpha_stop_tree() { return 0; }
        """.trimIndent()
    }

    private fun baseProject(invocation: PythonLaunchInvocation): RuntimeProjectSpec = RuntimeProjectSpec(
        name = "Sample",
        folderName = "sample-project",
        entry = "main.py",
        run = "python main.py",
        pythonLaunchInvocation = invocation,
    )

    private fun structuredRunner(host: FakeHost): String =
        host.quotedInputs.single { value ->
            value.contains("launch_args=()") &&
                value.contains("SIFTALPHA_LAUNCH_ARGUMENT_COUNT") &&
                value.contains("launch_mode=1")
        }

    @Test
    fun `console script uses quoted argv array instead of user controlled bash c`() {
        val host = FakeHost()
        val adapter = PythonRuntimeAdapter(host)
        val project = baseProject(
            PythonLaunchInvocation.consoleScript(
                name = "sherlock",
                arguments = listOf("--help", ";", "echo", "hacked", "john smith", "${'$'}HOME"),
            ),
        )

        adapter.start(project)
        val runner = structuredRunner(host)

        assertTrue(host.quotedInputs.contains("/root/venvs/runtime-id/bin/sherlock"))
        assertTrue(host.quotedInputs.contains("--help"))
        assertTrue(host.quotedInputs.contains(";"))
        assertTrue(host.quotedInputs.contains("john smith"))
        assertTrue(host.quotedInputs.contains("${'$'}HOME"))
        assertTrue(runner.contains("launch_executable='/root/venvs/runtime-id/bin/sherlock'"))
        assertTrue(runner.contains("launch_args+=( '--help' )"))
        assertTrue(runner.contains("launch_args+=( ';' )"))
        assertTrue(runner.contains("launch_args+=( 'john smith' )"))
        assertTrue(runner.contains("launch_args+=( '${'$'}HOME' )"))
        assertTrue(runner.contains("\"${'$'}launch_executable\" \"${'$'}{launch_args[@]}\""))
        assertFalse(runner.contains("bash -c \"sherlock"))
        assertFalse(runner.contains("configured_run='python main.py --help"))
    }

    @Test
    fun `python file structured launch passes entrypoint as first argv element`() {
        val host = FakeHost()
        val adapter = PythonRuntimeAdapter(host)
        val project = baseProject(
            PythonLaunchInvocation.pythonFile(
                entrypoint = "main.py",
                arguments = listOf("--name", "john smith"),
            ),
        )

        adapter.start(project)
        val runner = structuredRunner(host)

        assertTrue(host.quotedInputs.contains("/root/venvs/runtime-id/bin/python"))
        assertTrue(host.quotedInputs.contains("main.py"))
        assertTrue(host.quotedInputs.contains("--name"))
        assertTrue(host.quotedInputs.contains("john smith"))
        assertTrue(runner.contains("launch_executable='/root/venvs/runtime-id/bin/python'"))
        assertTrue(runner.contains("launch_args+=( 'main.py' )"))
        assertTrue(runner.contains("launch_args+=( '--name' )"))
        assertTrue(runner.contains("launch_args+=( 'john smith' )"))
        assertTrue(runner.contains("\"${'$'}launch_executable\" \"${'$'}{launch_args[@]}\""))
        assertFalse(runner.contains("bash -c \"python main.py --name"))
    }
}
