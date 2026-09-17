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

        val script = adapter.start(project).shellScript

        assertTrue(script.contains("launch_executable='/root/venvs/runtime-id/bin/sherlock'"))
        assertTrue(script.contains("launch_args+=( '--help' )"))
        assertTrue(script.contains("launch_args+=( ';' )"))
        assertTrue(script.contains("launch_args+=( 'john smith' )"))
        assertTrue(script.contains("launch_args+=( '${'$'}HOME' )"))
        assertTrue(script.contains("\"${'$'}launch_executable\" \"${'$'}{launch_args[@]}\""))
        assertTrue(script.contains("SIFTALPHA_LAUNCH_ARGUMENT_COUNT=%s"))
        assertFalse(script.contains("bash -c \"sherlock"))
        assertFalse(script.contains("configured_run='python main.py --help"))
    }

    @Test
    fun `python file structured launch passes entrypoint as argv zero payload`() {
        val host = FakeHost()
        val adapter = PythonRuntimeAdapter(host)
        val project = baseProject(
            PythonLaunchInvocation.pythonFile(
                entrypoint = "main.py",
                arguments = listOf("--name", "john smith"),
            ),
        )

        val script = adapter.start(project).shellScript

        assertTrue(script.contains("launch_executable='/root/venvs/runtime-id/bin/python'"))
        assertTrue(script.contains("launch_args+=( 'main.py' )"))
        assertTrue(script.contains("launch_args+=( '--name' )"))
        assertTrue(script.contains("launch_args+=( 'john smith' )"))
        assertTrue(script.contains("\"${'$'}launch_executable\" \"${'$'}{launch_args[@]}\""))
        assertFalse(script.contains("bash -c \"python main.py --name"))
    }
}
