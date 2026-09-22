package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonRuntimeAdapterTest {

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
        override fun wrapUbuntuCancelable(runtimeId: String, operation: String, inner: String): String =
            "HOST_CANCELABLE_BEGIN:$runtimeId:$operation\n$inner\nHOST_CANCELABLE_END"
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

    private class ScopedHost : RuntimeCommandHost {
        override fun runtimeSupported(): Boolean = true
        override fun runtimeUnsupportedReason(): String = "unsupported"
        override fun sharedRoot(): String = "/storage/emulated/0/AcodeProjects"
        override fun runtimeId(folderName: String): String = "id-" + folderName
        override fun sh(value: String): String =
            "'" + value.replace("'", "'\"'\"'") + "'"
        override fun wrapUbuntu(inner: String): String = inner
        override fun hostPreamble(): String =
            "set -e\nROOT='/storage/emulated/0/AcodeProjects'\nruntime_dir=\"\$HOME/.siftalpha/runtime\"\nmkdir -p \"\$runtime_dir\""
        override fun hostProcessHelpers(): String =
            "siftalpha_pid_alive() { return 1; }\nsiftalpha_group_alive() { return 1; }\nsiftalpha_stop_tree() { return 0; }"
    }
    private val host = FakeHost()
    private val adapter = PythonRuntimeAdapter(host)
    private val project = RuntimeProjectSpec(
        name = "Sample",
        folderName = "sample-project",
        entry = "main.py",
        run = "python main.py",
        webLogDiscoveryAllowed = true,
    )

    @Test
    fun `python adapter is registered as executable runtime`() {
        val registry = RuntimeAdapterCatalog.forHost(FakeHost())
        val executable = registry.executableAdapter(RuntimeKind.PYTHON)

        assertNotNull(executable)
        assertEquals(RuntimeKind.PYTHON, executable!!.kind)
        assertTrue(executable.supports(RuntimeAction.PREPARE))
        assertTrue(executable.supports(RuntimeAction.START))
    }

    @Test
    fun `prepare preserves venv pip dependency source and durable readiness contract`() {
        val command = adapter.prepare(project)
        val script = command.shellScript

        assertEquals("Sample · 准备环境", command.label)
        assertTrue(script.contains("HOST_WRAP_BEGIN"))
        assertFalse(script.contains("HOST_CANCELABLE_BEGIN:runtime-id:prepare"))
        assertTrue(script.contains("python3 -m venv"))
        assertFalse(script.contains("venv.prepare-"))
        assertTrue(script.contains("venv.backup-"))
        assertTrue(script.contains("ready.backup-"))
        assertTrue(script.contains("rollback_prepare"))
        assertTrue(script.contains("ENVIRONMENT_ACTIVATION_FAILED"))
        assertTrue(script.contains("ENVIRONMENT_ROLLBACK_FAILED"))
        assertTrue(script.contains("PYTHON_ENVIRONMENT_PREFIX_MISMATCH"))
        assertTrue(script.contains("python3 -m pip --version"))
        assertTrue(script.contains("apt-get install -y python3-venv python3-pip"))
        assertTrue(script.contains("DEPENDENCY_SOURCE=requirements.txt"))
        assertTrue(script.contains("DEPENDENCY_SOURCE=pyproject.toml"))
        assertTrue(script.contains("SIFTALPHA_PYPROJECT_EXTRAS=none"))
        assertFalse(script.contains("optional-dependencies"))
        assertFalse(script.contains("vite.config.ts"))
        assertFalse(script.contains("SIFTALPHA_PYPROJECT_WEB_EXTRA"))
        assertTrue(script.contains("install_target="))
        assertTrue(script.contains("/root/siftalpha/env-ready-runtime-id.txt"))
        assertTrue(script.contains("sha256sum"))
        assertTrue(script.contains("PYTHON_VERSION=%s"))
        assertTrue(script.contains("REQUIRES_PYTHON=%s"))
        assertTrue(script.contains("INSTALL_EXTRAS=%s"))
        assertTrue(script.contains("SIFTALPHA_ENV_STALE_BACKUP_CLEANED=1"))
        assertTrue(script.contains("SIFTALPHA_ENV_INTERRUPTED_PREPARE_RECOVERED=1"))
        assertTrue(script.contains("SIFTALPHA_ENV=READY"))

        val finalVenvCreation = script.indexOf("python3 -m venv \"${'$'}venv\"")
        val pythonValidation = script.indexOf(
            "\"\$venv/bin/python\" -m pip install",
            finalVenvCreation,
        )
        val prefixValidation = script.indexOf("prepared_python_executable", pythonValidation)
        val readySignal = script.indexOf("echo 'SIFTALPHA_ENV=READY'")
        assertTrue("replacement venv must be created at the stable final path", finalVenvCreation >= 0)
        assertTrue("dependency work must use the stable final venv", pythonValidation > finalVenvCreation)
        assertTrue("stable-prefix validation must occur before READY", prefixValidation > pythonValidation)
        assertTrue("READY must only be emitted after final Python validation", readySignal > prefixValidation)
    }


    @Test
    fun `planned web extra is executed without install-time project rediscovery`() {
        val script = adapter.prepare(
            project.copy(
                environmentPlanId = "sha256:web-plan",
                pythonInstallExtras = listOf("web"),
            ),
        ).shellScript

        assertTrue(script.contains("planned_extras='web'"))
        assertTrue(script.contains("SIFTALPHA_PYPROJECT_EXTRAS=%s"))
        assertTrue(script.contains("install_target=\"${'$'}project[${'$'}planned_extras]\""))
        assertFalse(script.contains("tomllib"))
        assertFalse(script.contains("optional-dependencies"))
        assertFalse(script.contains("vite.config"))
        assertFalse(script.contains("find \"${'$'}project\" -maxdepth"))
    }

    @Test
    fun `prepare transaction never relocates a built virtual environment`() {
        val script = adapter.prepare(project).shellScript

        val backupMove = script.indexOf("mv -- \"${'$'}venv\" \"${'$'}backup\"")
        val createFinal = script.indexOf("python3 -m venv \"${'$'}venv\"")
        val installFinal = script.indexOf("\"${'$'}venv/bin/python\" -m pip install")
        val readyWrite = script.indexOf("REQUIRES_PYTHON=%s")
        val disableRollback = script.indexOf("trap - EXIT", readyWrite)

        assertTrue("old environment must be retained before replacement", backupMove >= 0)
        assertTrue("new environment must be created only after old environment is backed up", createFinal > backupMove)
        assertTrue("pip must install into the final stable prefix", installFinal > createFinal)
        assertFalse("a completed venv must never be renamed from a temporary prefix", script.contains("mv -- \"${'$'}candidate\" \"${'$'}venv\""))
        assertTrue("rollback stays armed until the new READY marker is written", disableRollback > readyWrite)
    }

    @Test
    fun `prepare rejects unavailable declared Python version before dependency install`() {
        val script = adapter.prepare(project.copy(pythonRequiresVersion = "<3")).shellScript

        assertTrue(script.contains("SpecifierSet"))
        assertTrue(script.contains("SIFTALPHA_ERROR=PYTHON_RUNTIME_UNAVAILABLE"))
        assertTrue(script.contains("SIFTALPHA_PYTHON_REQUIRES"))
    }

    @Test
    fun `environment plan metadata is rebound only after material compatibility checks`() {
        val planned = project.copy(environmentPlanId = "sha256:plan-a")
        val prepare = adapter.prepare(planned).shellScript
        val status = adapter.status(planned).shellScript

        assertTrue(prepare.contains("required_plan='sha256:plan-a'"))
        assertTrue(prepare.contains("INSTALL_EXTRAS=%s"))
        assertTrue(prepare.contains("PLAN_ID=%s"))
        assertTrue(status.contains("saved_plan="))
        assertTrue(status.contains("saved_extras="))
        assertTrue(status.contains("PYTHON_INSTALL_EXTRAS_UNKNOWN"))
        assertTrue(status.contains("PYTHON_INSTALL_EXTRAS_CHANGED"))
        assertTrue(status.contains("READY_PLAN_MIGRATED"))
        assertFalse(status.contains("env_reason='ENVIRONMENT_PLAN_CHANGED'"))
        assertTrue(
            "plan rebinding must happen only after the existing Python requirement check",
            status.indexOf("READY_PLAN_MIGRATED") > status.indexOf("PYTHON_REQUIREMENT_CHANGED"),
        )
        assertTrue(
            "install extras compatibility must be proven before plan metadata rebinding",
            status.lastIndexOf("READY_PLAN_MIGRATED") >
                status.indexOf("PYTHON_INSTALL_EXTRAS_CHANGED"),
        )
    }

    @Test
    fun `legacy external readiness marker migrates only with venv version evidence`() {
        val script = adapter.status(
            project.copy(pythonRequiresVersion = ">=3.10"),
        ).shellScript

        assertTrue(script.contains("pyvenv.cfg"))
        assertTrue(script.contains("legacy_created_python"))
        assertTrue(script.contains("pip._vendor.packaging.specifiers"))
        assertTrue(script.contains("migrated_ready"))
        assertTrue(script.contains("READY_MIGRATED"))
        assertTrue(script.contains("PYTHON_RUNTIME_VERSION_UNKNOWN"))
    }

    @Test
    fun `start preserves generic secret injection readiness guard auto entry and host lifecycle`() {
        host.quotedInputs.clear()
        val command = adapter.start(project)
        val script = command.shellScript
        val rawQuotedInputs = host.quotedInputs.joinToString("\n---\n")

        assertEquals("sample-project", command.secretNamespace)
        assertTrue(rawQuotedInputs.contains("SIFTALPHA_SECRETS_FORMAT"))
        assertTrue(rawQuotedInputs.contains("SIFTALPHA_BINANCE_API_KEY"))
        assertTrue(rawQuotedInputs.contains("SIFTALPHA_ENV_COUNT"))
        assertTrue(rawQuotedInputs.contains("_NAME_B64"))
        assertTrue(rawQuotedInputs.contains("_VALUE_B64"))
        assertTrue(rawQuotedInputs.contains("SIFTALPHA_ERROR=ENV_NOT_READY"))
        assertTrue(rawQuotedInputs.contains("DEPENDENCY_MANIFEST_CHANGED"))
        assertTrue(rawQuotedInputs.contains("PYTHON_RUNTIME_VERSION_CHANGED"))
        assertTrue(rawQuotedInputs.contains("PYTHON_REQUIREMENT_CHANGED"))
        assertTrue(rawQuotedInputs.contains("SIFTALPHA_ENTRY_AUTO"))
        assertTrue(rawQuotedInputs.contains("PYTHONUNBUFFERED=1"))
        assertTrue(rawQuotedInputs.contains("VIRTUAL_ENV"))
        assertTrue(rawQuotedInputs.contains("venv='/root/venvs/runtime-id'"))
        assertTrue(rawQuotedInputs.contains("${'$'}venv/bin/python"))
        assertTrue(rawQuotedInputs.contains(RuntimeIdentityStore.SCHEMA_KEY))
        assertTrue(rawQuotedInputs.contains(RuntimeIdentityStore.GUEST_ROOT_PID_KEY))
        assertTrue(rawQuotedInputs.contains(RuntimeIdentityStore.GUEST_ROOT_PGID_KEY))
        assertTrue(rawQuotedInputs.contains("SIFTALPHA_RUNTIME_IDENTITY_GUEST_ROOT=RUNNER"))
        assertTrue(script.contains("identity_dir=\"${'$'}runtime_dir/runtime-id\""))
        assertTrue(script.contains("${RuntimeIdentityStore.GUEST_RUNTIME_ROOT}/runtime-id"))
        assertTrue(script.contains("pid_file=\"${'$'}runtime_dir/runtime-id.pid\""))
        assertTrue(script.contains("pgid_file=\"${'$'}runtime_dir/runtime-id.pgid\""))
        assertTrue(script.contains("SIFTALPHA_RUNTIME_SESSION=SETSID"))
        assertTrue(script.contains("SIFTALPHA_RUNTIME_BACKEND=TERMUX_PROOT_PID"))
        assertTrue(script.contains("SIFTALPHA_STATUS=RUNNING"))
    }

    @Test
    fun `guest root identity is published before the long lived runtime command`() {
        host.quotedInputs.clear()
        adapter.start(project)
        val runner = host.quotedInputs.joinToString("\n---\n")

        val publish = runner.indexOf("siftalpha_runtime_identity_write_guest")
        val runningState = runner.indexOf("printf 'STATE=RUNNING")
        val runtimeCommand = runner.indexOf("bash -c \"${'$'}configured_run\"")

        assertTrue("guest root must be published", publish >= 0)
        assertTrue("runner must publish identity before declaring RUNNING", runningState > publish)
        assertTrue("runtime command must run after the root identity is published", runtimeCommand > publish)
    }

    @Test
    fun `stop status logs and clean preserve accepted runtime markers`() {
        val stop = adapter.stop(project).shellScript
        val status = adapter.status(project).shellScript
        val logs = adapter.logs(project).shellScript
        val clean = adapter.clean(project).shellScript

        assertTrue(stop.contains("siftalpha_stop_tree"))
        assertTrue(stop.contains("SIFTALPHA_STATUS=STOPPED_BY_USER"))
        assertTrue(stop.contains("prepare_pid_file=\"${'$'}runtime_dir/runtime-id.prepare.pid\""))
        assertTrue(stop.contains("prepare_pgid_file=\"${'$'}runtime_dir/runtime-id.prepare.pgid\""))
        assertTrue(stop.contains("SIFTALPHA_PREPARE_STOPPED=1"))
        assertTrue(stop.contains("SIFTALPHA_ENV_INTERRUPTED_PREPARE_RECOVERED=1"))
        assertTrue(stop.contains("SIFTALPHA_ERROR=ENVIRONMENT_ROLLBACK_FAILED"))
        assertTrue(stop.contains("SIFTALPHA_ERROR=STOP_INCOMPLETE"))
        assertTrue("STOP must keep using the legacy host PID files", stop.contains("pid_file=\"${'$'}runtime_dir/runtime-id.pid\""))
        assertTrue("STOP must keep using the legacy host PGID files", stop.contains("pgid_file=\"${'$'}runtime_dir/runtime-id.pgid\""))
        assertFalse("PREPARE must not become identity-aware", adapter.prepare(project).shellScript.contains(RuntimeIdentityStore.SCHEMA_KEY))
        assertFalse("STOP must not become identity-aware", stop.contains(RuntimeIdentityStore.SCHEMA_KEY))

        assertTrue(status.contains("SIFTALPHA_ENV=READY"))
        assertTrue(status.contains("SIFTALPHA_ENV=NOT_READY"))
        assertTrue(status.contains("SIFTALPHA_ENV_REASON"))
        assertTrue(status.contains("/root/siftalpha/env-ready-runtime-id.txt"))
        assertTrue(status.contains("SIFTALPHA_STATUS=RUNNING"))
        assertTrue(status.contains("SIFTALPHA_ERROR=RUNTIME_LAUNCH_FAILED"))

        assertTrue(logs.contains("=== SiftAlpha Project Log ==="))
        assertTrue(logs.contains("SIFTALPHA_LOG=EMPTY"))
        assertTrue(logs.contains("SIFTALPHA_WEB_PORT"))
        assertTrue(logs.contains("runtime_identity_id"))
        assertTrue(logs.contains(RuntimeIdentityStore.GUEST_RUNTIME_ROOT))

        assertTrue(clean.contains("/root/venvs/runtime-id"))
        assertTrue(clean.contains(".backup-*"))
        assertTrue(clean.contains("/root/siftalpha/env-ready-runtime-id.txt"))
        assertTrue(clean.contains("SIFTALPHA_STATUS=CLEAN_BLOCKED_RUNNING_PROCESS"))
        assertTrue(clean.contains("SIFTALPHA_ENV=CLEANED"))
    }

    @Test
    fun `project stop scripts remain strictly runtime id scoped`() {
        val scoped = PythonRuntimeAdapter(ScopedHost())
        val projectA = project.copy(name = "A", folderName = "project-a")
        val projectB = project.copy(name = "B", folderName = "project-b")

        val stopA = scoped.stop(projectA).shellScript
        val stopB = scoped.stop(projectB).shellScript

        assertTrue(stopA.contains("id-project-a.pid"))
        assertTrue(stopA.contains("id-project-a.pgid"))
        assertTrue(stopA.contains("id-project-a.prepare.pid"))
        assertFalse("A STOP must never reference B runtime ownership", stopA.contains("id-project-b"))

        assertTrue(stopB.contains("id-project-b.pid"))
        assertTrue(stopB.contains("id-project-b.pgid"))
        assertTrue(stopB.contains("id-project-b.prepare.pid"))
        assertFalse("B STOP must never reference A runtime ownership", stopB.contains("id-project-a"))
    }
    @Test
    fun `logs pass runtime identity context before web discovery`() {
        val logs = adapter.logs(project).shellScript
        val identityId = logs.indexOf("runtime_identity_id='runtime-id'")
        val identityDir = logs.indexOf("runtime_identity_dir=\"\$runtime_dir/runtime-id\"")
        val discovery = logs.indexOf("siftalpha-web identity")

        assertTrue("logs must define runtime identity id", identityId >= 0)
        assertTrue("logs must define runtime identity directory", identityDir >= 0)
        assertTrue("runtime identity id must precede Web Discovery", discovery > identityId)
        assertTrue("runtime identity directory must precede Web Discovery", discovery > identityDir)
    }

    @Test
    fun `python logs wire the current runtime log into bounded Web fallback`() {
        host.quotedInputs.clear()
        adapter.logs(project)
        val inner = host.quotedInputs.single { value ->
            value.contains("siftalpha_web_procfs_success=\"${'$'}{1:-0}\"") &&
                value.contains("=== SiftAlpha Project Log ===") &&
                value.contains("tail -n 160 \"${'$'}log\"")
        }
        val logTail = inner.indexOf("tail -n 160 \"${'$'}log\"")
        val fallbackGuard = inner.indexOf("if [ \"${'$'}siftalpha_web_procfs_success\" != '1' ]; then")
        val fallback = inner.indexOf("siftalpha_log_web_candidate='", fallbackGuard)

        assertTrue("log path must be scoped to this runtime", inner.contains("log='/root/siftalpha/logs/run-runtime-id.log'"))
        assertTrue("log fallback must run after the bounded log read", fallbackGuard > logTail)
        assertTrue("log fallback must be guarded by procfs success", fallback > fallbackGuard)
        assertTrue(inner.contains("SIFTALPHA_WEB_DISCOVERY_SOURCE=RUNTIME_LOG"))
        assertTrue(inner.contains("SIFTALPHA_WEB_URL=%s"))
        assertTrue(inner.contains("127\\.0\\.0\\.1"))
        assertTrue(inner.contains("localhost"))
        assertTrue(inner.contains("0\\.0\\.0\\.0"))
        assertTrue(inner.contains("\\[::\\]"))
        assertFalse("bare PORT output must not be treated as Web evidence", inner.contains("PORT:"))
        assertFalse("external hosts must not be accepted by the shell candidate source", inner.contains("example.com"))
    }

    @Test
    fun `python logs keep procfs discovery authoritative and preserve the endpoint gate`() {
        host.quotedInputs.clear()
        val script = adapter.logs(project).shellScript
        val inner = host.quotedInputs.single { value ->
            value.contains("siftalpha_web_procfs_success=\"${'$'}{1:-0}\"") &&
                value.contains("=== SiftAlpha Project Log ===") &&
                value.contains("tail -n 160 \"${'$'}log\"")
        }

        val procfsCaptureStart = script.indexOf("siftalpha_web_procfs_output=\"${'$'}(")
        val procfsCaptureEnd = script.indexOf("\n            )\"", procfsCaptureStart)
        val procfsOutputPrint = script.indexOf(
            "printf '%s\\n' \"${'$'}siftalpha_web_procfs_output\"",
            procfsCaptureEnd,
        )
        val procfsGuardStart = script.indexOf(
            "if printf '%s\\n' \"${'$'}siftalpha_web_procfs_output\" | grep -q '^SIFTALPHA_WEB_AUTODISCOVERY=PASS '; then",
            procfsOutputPrint,
        )
        val procfsGuardEnd = script.indexOf(
            "\n            fi\n            proot-distro login --bind",
            procfsGuardStart,
        )
        val guestLogs = script.indexOf(
            "proot-distro login --bind \"${'$'}ROOT:/root/projects\" ubuntu -- bash -lc",
            procfsGuardEnd,
        )

        assertTrue("Python logs must retain project-scoped procfs discovery", procfsCaptureStart >= 0)
        assertTrue("procfs capture must have a bounded end", procfsCaptureEnd > procfsCaptureStart)
        assertTrue("existing procfs output must remain visible", procfsOutputPrint > procfsCaptureEnd)
        assertTrue("outer procfs success guard must be present", procfsGuardStart > procfsOutputPrint)
        assertTrue("outer procfs success guard must have a bounded end", procfsGuardEnd > procfsGuardStart)
        val procfsGuard = script.substring(procfsGuardStart, procfsGuardEnd)
        assertTrue("procfs success branch must set success", "siftalpha_web_procfs_success=1" in procfsGuard)
        assertTrue("procfs failure branch must clear success", "siftalpha_web_procfs_success=0" in procfsGuard)
        assertTrue("runtime log fallback must be invoked after procfs discovery", guestLogs > procfsGuardEnd)
        assertTrue(
            "guest logs must receive the procfs success flag",
            "siftalpha-web-logs \"${'$'}siftalpha_web_procfs_success\"" in script.substring(guestLogs),
        )

        val logTail = inner.indexOf("tail -n 160 \"${'$'}log\"")
        val fallbackGuardStart = inner.indexOf("if [ \"${'$'}siftalpha_web_procfs_success\" != '1' ]; then")
        val fallbackGuardEnd = inner.lastIndexOf("\nfi")
        val fallback = inner.indexOf("siftalpha_log_web_candidate='", fallbackGuardStart)

        assertTrue("log fallback guard must exist", fallbackGuardStart >= 0)
        assertTrue("log fallback guard must have a bounded end", fallbackGuardEnd > fallbackGuardStart)
        assertTrue("log fallback must run after the bounded log read", fallbackGuardStart > logTail)
        assertTrue("RuntimeWebLogDiscoveryShell must remain inside the fallback guard", fallback in (fallbackGuardStart until fallbackGuardEnd))
        assertTrue(script.contains("siftalpha_web_http_probe"))
        assertTrue(script.contains("SIFTALPHA_WEB_AUTODISCOVERY=PASS"))
        assertFalse(script.contains("seq 1 65535"))
        assertFalse(script.contains("/proc/[0-9]*"))
        assertFalse(script.contains("global ss"))
        assertFalse(script.contains("netstat"))
        assertFalse(script.contains("lsof"))
        assertTrue(RuntimeWebEndpointProbe.targets("http://127.0.0.1:8766").isNotEmpty())
        assertTrue(RuntimeWebEndpointProbe.targets("http://example.com:8766").isEmpty())
    }


    @Test
    fun `non Web project skips weak log discovery but keeps strong socket discovery`() {
        val shell = adapter.logs(project.copy(webLogDiscoveryAllowed = false)).shellScript

        assertTrue(shell.contains("SIFTALPHA_WEB_LOG_DISCOVERY=SKIPPED_NOT_WEB_PROJECT"))
        assertTrue(shell.contains("SIFTALPHA_WEB_AUTODISCOVERY=PASS"))
        assertFalse(shell.contains("SIFTALPHA_WEB_DISCOVERY_SOURCE=RUNTIME_LOG"))
    }

    @Test
    fun `structured python file launch publishes result source entrypoint`() {
        host.quotedInputs.clear()
        adapter.start(
            project.copy(
                pythonLaunchInvocation = PythonLaunchInvocation.pythonFile(
                    entrypoint = "run_all_strategies.py",
                    arguments = listOf("SH", "600519"),
                ),
            ),
        )
        val runner = host.quotedInputs.joinToString("\n---\n")

        assertTrue(runner.contains("launch_entrypoint='run_all_strategies.py'"))
        assertTrue(runner.contains("SIFTALPHA_LAUNCH_ENTRYPOINT=%s"))
        assertTrue(runner.contains("SIFTALPHA_LAUNCH_ARGUMENT_COUNT=%s"))
    }

    @Test
    fun `custom run command remains delegated instead of forced to python entry`() {
        host.quotedInputs.clear()
        val custom = project.copy(run = "python -m package.worker --mode live")
        adapter.start(custom)
        val rawQuotedInputs = host.quotedInputs.joinToString("\n---\n")

        assertTrue(rawQuotedInputs.contains("configured_run='python -m package.worker --mode live'"))
        assertTrue(rawQuotedInputs.contains("auto_entry_mode=0"))
        assertTrue(rawQuotedInputs.contains("printf 'COMMAND=%s"))
        assertTrue(rawQuotedInputs.contains("bash -c \"${'$'}configured_run\""))
    }
}
