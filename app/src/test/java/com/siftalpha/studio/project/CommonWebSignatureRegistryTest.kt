package com.siftalpha.studio.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommonWebSignatureRegistryTest {

    @Test
    fun streamlitRequiresDependencyAndEntryEvidence() {
        val match = CommonWebSignatureRegistry.detectPython(
            requirements = "streamlit>=1.40\npandas\n",
            pyproject = null,
            relativePaths = listOf("streamlit_app.py"),
            pythonSources = mapOf(
                "streamlit_app.py" to "import streamlit as st\nst.title('demo')",
            ),
        )

        assertEquals("streamlit", match?.framework)
        assertEquals(8501, match?.defaultPort)
        assertEquals(CommonWebSignatureRegistry.Confidence.HIGH, match?.confidence)
    }

    @Test
    fun dependencyNameAloneDoesNotTakeFastPath() {
        val match = CommonWebSignatureRegistry.detectPython(
            requirements = "fastapi\n",
            pyproject = null,
            relativePaths = listOf("worker.py"),
            pythonSources = mapOf("worker.py" to "print('not a server')"),
        )

        assertNull(match)
    }

    @Test
    fun djangoUsesConventionalProjectStructure() {
        val match = CommonWebSignatureRegistry.detectPython(
            requirements = "Django>=5\n",
            pyproject = null,
            relativePaths = listOf(
                "manage.py",
                "demo/settings.py",
                "demo/urls.py",
            ),
        )

        assertEquals("django", match?.framework)
        assertEquals(8000, match?.defaultPort)
    }

    @Test
    fun hybridPythonViteWinsOverBackendFrameworkSignature() {
        val pyproject = """
            [project]
            name = "demo"
            dependencies = ["fastapi", "uvicorn"]

            [project.scripts]
            demo = "demo.cli:main"

            [project.optional-dependencies]
            web = ["fastapi", "uvicorn"]
        """.trimIndent()

        val match = CommonWebSignatureRegistry.detectPython(
            requirements = null,
            pyproject = pyproject,
            relativePaths = listOf(
                "pyproject.toml",
                "ui/package.json",
                "ui/vite.config.ts",
                "src/demo/cli/cmd_web.py",
            ),
            pythonSources = mapOf(
                "src/demo/cli/cmd_web.py" to """
                    import click
                    from fastapi import FastAPI
                    app = FastAPI()

                    @click.command("serve")
                    def serve():
                        pass
                """.trimIndent(),
            ),
        )

        assertEquals("python-vite-web", match?.framework)
    }

    @Test
    fun nextFastPathRequiresPackageScriptEvidence() {
        val match = CommonWebSignatureRegistry.detectNode(
            dependencies = setOf("next", "react"),
            packageStartCommand = "next start",
            relativePaths = listOf("package.json"),
        )

        assertEquals("next", match?.framework)
        assertEquals(3000, match?.defaultPort)
    }

    @Test
    fun expressRequiresSourceListenEvidence() {
        val match = CommonWebSignatureRegistry.detectNode(
            dependencies = setOf("express"),
            packageStartCommand = "node server.js",
            relativePaths = listOf("package.json", "server.js"),
            nodeSources = listOf(
                """
                    const express = require('express')
                    express().listen(4000)
                """.trimIndent(),
            ),
        )

        assertEquals("express", match?.framework)
    }
}
