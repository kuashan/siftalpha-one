package com.siftalpha.macos

import java.io.File

data class MacManagedPythonRuntime(
    val root: File,
    val source: Source,
) {
    enum class Source {
        SYSTEM_PROPERTY,
        ENVIRONMENT,
        APP_BUNDLE,
    }

    val pythonExecutable: File
        get() = File(root, "bin/python3")

    val available: Boolean
        get() = pythonExecutable.isFile && pythonExecutable.canExecute()

    fun version(): String? {
        if (!available) return null
        return runCatching {
            val process = ProcessBuilder(pythonExecutable.absolutePath, "--version")
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            process.inputStream.bufferedReader().use { it.readText().trim() }
                .takeIf { process.exitValue() == 0 && it.startsWith("Python 3.") }
        }.getOrNull()
    }

    companion object {
        fun locate(): MacManagedPythonRuntime? {
            val candidates = buildList {
                System.getProperty("siftalpha.managed.python.root")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(MacManagedPythonRuntime(File(it), Source.SYSTEM_PROPERTY)) }
                System.getenv("SIFTALPHA_MANAGED_PYTHON_ROOT")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(MacManagedPythonRuntime(File(it), Source.ENVIRONMENT)) }

                System.getProperty("jpackage.app-path")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { appPath ->
                        val launcher = File(appPath).absoluteFile
                        val contents = launcher.parentFile?.parentFile
                        if (contents?.name == "Contents") {
                            add(
                                MacManagedPythonRuntime(
                                    File(contents, "app/managed-python/python"),
                                    Source.APP_BUNDLE,
                                ),
                            )
                        }
                    }

                var cursor: File? = File(System.getProperty("java.home")).absoluteFile
                while (cursor != null) {
                    if (cursor.name == "Contents" && cursor.parentFile?.name?.endsWith(".app") == true) {
                        add(
                            MacManagedPythonRuntime(
                                File(cursor, "app/managed-python/python"),
                                Source.APP_BUNDLE,
                            ),
                        )
                        break
                    }
                    cursor = cursor.parentFile
                }
            }

            return candidates.firstOrNull { runtime ->
                runCatching { runtime.root.canonicalFile }.isSuccess && runtime.available
            }
        }
    }
}
