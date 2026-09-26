package com.siftalpha.studio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PythonNativeWebApplicationLaunchResolverTest {

    private val pyproject = """
        [project]
        name = "easy-tdx"

        [project.scripts]
        easy-tdx = "easy_tdx.cli:main"

        [project.optional-dependencies]
        web = ["fastapi>=0.110", "uvicorn[standard]>=0.29"]
    """.trimIndent()

    private val paths = listOf(
        "pyproject.toml",
        "web-ui/package.json",
        "web-ui/vite.config.ts",
        "src/easy_tdx/cli/cmd_web.py",
        "run_all_strategies.py",
    )

    private val sources = linkedMapOf(
        "src/easy_tdx/cli/cmd_web.py" to """
            import click

            @click.command("serve")
            @click.option("--host", default="0.0.0.0")
            @click.option("--open-browser/--no-open-browser", default=True)
            def serve(host: str, open_browser: bool):
                pass
        """.trimIndent(),
        "run_all_strategies.py" to """
            import click

            @click.argument("market")
            @click.argument("code")
            def main(market: str, code: str):
                pass
        """.trimIndent(),
    )

    @Test
    fun `vite backed Python Web app resolves project owned serve command`() {
        val result = PythonNativeWebApplicationLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = pyproject,
            relativePaths = paths,
            pythonSources = sources,
            webProjectEnabled = true,
        )

        assertEquals(
            PythonNativeWebLaunchCandidate(
                executableName = "easy-tdx",
                arguments = listOf("serve", "--host", "127.0.0.1", "--no-open-browser"),
                evidencePath = "src/easy_tdx/cli/cmd_web.py",
            ),
            result,
        )
    }

    @Test
    fun `strict project owned Web launch outranks generic FastAPI evidence`() {
        val result = PythonNativeWebApplicationLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = pyproject,
            relativePaths = paths + "src/easy_tdx/web/api.py",
            pythonSources = sources + (
                "src/easy_tdx/web/api.py" to """
                    from fastapi import FastAPI
                    app = FastAPI()
                """.trimIndent()
            ),
            webProjectEnabled = true,
        )

        assertEquals(
            PythonNativeWebLaunchCandidate(
                executableName = "easy-tdx",
                arguments = listOf("serve", "--host", "127.0.0.1", "--no-open-browser"),
                evidencePath = "src/easy_tdx/cli/cmd_web.py",
            ),
            result,
        )
    }


    @Test
    fun `explicit project run remains authoritative`() {
        val result = PythonNativeWebApplicationLaunchResolver.resolve(
            declaredRun = "python run_all_strategies.py SH 600519",
            pyprojectToml = pyproject,
            relativePaths = paths,
            pythonSources = sources,
            webProjectEnabled = true,
        )

        assertNull(result)
    }

    @Test
    fun `missing Web extra fails closed`() {
        val result = PythonNativeWebApplicationLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = """
                [project]
                name = "demo"
                [project.scripts]
                demo = "demo.cli:main"
            """.trimIndent(),
            relativePaths = paths,
            pythonSources = sources,
            webProjectEnabled = true,
        )

        assertNull(result)
    }

    @Test
    fun `missing Vite component fails closed`() {
        val result = PythonNativeWebApplicationLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = pyproject,
            relativePaths = listOf("pyproject.toml", "src/easy_tdx/cli/cmd_web.py"),
            pythonSources = sources,
            webProjectEnabled = true,
        )

        assertNull(result)
    }

    @Test
    fun `serve command without browser suppression fails closed`() {
        val result = PythonNativeWebApplicationLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = pyproject,
            relativePaths = paths,
            pythonSources = mapOf(
                "src/easy_tdx/cli/cmd_web.py" to """
                    import click
                    @click.command("serve")
                    @click.option("--host")
                    def serve(host: str):
                        pass
                """.trimIndent(),
            ),
            webProjectEnabled = true,
        )

        assertNull(result)
    }

    @Test
    fun `multiple scripts use unique project name match`() {
        val result = PythonNativeWebApplicationLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = """
                [project]
                name = "demo-app"

                [project.scripts]
                admin = "demo.admin:main"
                demo_app = "demo.cli:main"

                [project.optional-dependencies]
                web = ["fastapi", "uvicorn"]
            """.trimIndent(),
            relativePaths = listOf("ui/package.json", "ui/vite.config.js"),
            pythonSources = mapOf(
                "demo/web.py" to """
                    import click
                    @click.command("serve")
                    @click.option("--no-browser", is_flag=True)
                    def serve(no_browser: bool):
                        pass
                """.trimIndent(),
            ),
            webProjectEnabled = true,
        )

        assertEquals("demo_app", result?.executableName)
        assertEquals(listOf("serve", "--no-browser"), result?.arguments)
    }

    @Test
    fun `ordinary CLI with unrelated required arguments does not become Web launch`() {
        val result = PythonNativeWebApplicationLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = pyproject,
            relativePaths = paths,
            pythonSources = mapOf(
                "run_all_strategies.py" to """
                    import click
                    @click.argument("market")
                    @click.argument("code")
                    def main(market: str, code: str):
                        pass
                """.trimIndent(),
            ),
            webProjectEnabled = true,
        )

        assertNull(result)
    }
    @Test
    fun streamlitCommonSignatureResolvesDirectLaunch() {
        val result = PythonNativeWebApplicationLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = null,
            requirementsText = "streamlit>=1.40",
            relativePaths = listOf("streamlit_app.py"),
            pythonSources = mapOf(
                "streamlit_app.py" to "import streamlit as st\nst.title('hello')",
            ),
            webProjectEnabled = true,
        )

        assertEquals("streamlit", result?.executableName)
        assertEquals("streamlit_app.py", result?.evidencePath)
        assertEquals(
            listOf(
                "run",
                "streamlit_app.py",
                "--server.address",
                "127.0.0.1",
                "--server.headless",
                "true",
            ),
            result?.arguments,
        )
    }

    @Test
    fun djangoCommonSignatureResolvesManageRunserver() {
        val result = PythonNativeWebApplicationLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = null,
            requirementsText = "Django>=5",
            relativePaths = listOf(
                "manage.py",
                "demo/settings.py",
                "demo/urls.py",
            ),
            pythonSources = mapOf("manage.py" to "def main(): pass"),
            webProjectEnabled = true,
        )

        assertEquals("python", result?.executableName)
        assertEquals(listOf("manage.py", "runserver", "127.0.0.1:8000"), result?.arguments)
    }

    @Test
    fun `generic FastAPI does not override project owned console script`() {
        val result = PythonNativeWebApplicationLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = """
                [project]
                name = "cli-owned"
                dependencies = ["fastapi", "uvicorn"]

                [project.scripts]
                cli-owned = "cli_owned.cli:main"
            """.trimIndent(),
            requirementsText = null,
            relativePaths = listOf("src/cli_owned/api.py"),
            pythonSources = mapOf(
                "src/cli_owned/api.py" to """
                    from fastapi import FastAPI
                    app = FastAPI()
                """.trimIndent(),
            ),
            webProjectEnabled = true,
        )

        assertNull(result)
    }

    @Test
    fun `ambiguous FastAPI apps fail closed`() {
        val result = PythonNativeWebApplicationLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = """
                [project]
                dependencies = ["fastapi", "uvicorn"]
            """.trimIndent(),
            requirementsText = null,
            relativePaths = listOf("src/demo/a.py", "src/demo/b.py"),
            pythonSources = mapOf(
                "src/demo/a.py" to "from fastapi import FastAPI\napp = FastAPI()",
                "src/demo/b.py" to "from fastapi import FastAPI\napp = FastAPI()",
            ),
            webProjectEnabled = true,
        )

        assertNull(result)
    }

    @Test
    fun `serve source outside console script package cannot prove project web contract`() {
        val result = PythonNativeWebApplicationLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = pyproject,
            relativePaths = paths + "tools/cmd_web.py",
            pythonSources = mapOf(
                "tools/cmd_web.py" to """
                    import click
                    @click.command("serve")
                    @click.option("--no-open-browser", is_flag=True)
                    def serve(no_open_browser: bool):
                        pass
                """.trimIndent(),
            ),
            webProjectEnabled = true,
        )

        assertNull(result)
    }

    @Test
    fun fastApiCommonSignatureResolvesInstalledModuleTarget() {
        val result = PythonNativeWebApplicationLaunchResolver.resolve(
            declaredRun = null,
            pyprojectToml = """
                [project]
                dependencies = ["fastapi", "uvicorn"]
            """.trimIndent(),
            requirementsText = null,
            relativePaths = listOf("src/demo/server.py"),
            pythonSources = mapOf(
                "src/demo/server.py" to """
                    from fastapi import FastAPI
                    app = FastAPI()
                """.trimIndent(),
            ),
            webProjectEnabled = true,
        )

        assertEquals("uvicorn", result?.executableName)
        assertEquals(listOf("demo.server:app", "--host", "127.0.0.1"), result?.arguments)
    }

}
