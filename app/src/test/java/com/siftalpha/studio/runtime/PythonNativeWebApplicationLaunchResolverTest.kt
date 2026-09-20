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
}
