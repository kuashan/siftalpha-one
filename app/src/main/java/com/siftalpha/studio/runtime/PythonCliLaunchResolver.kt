package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.EmbeddedPythonEntrypointPolicy
import org.tomlj.Toml
import org.tomlj.TomlTable

/**
 * Resolves the action-time Python launch contract owned by M.
 *
 * The resolver reads only already-materialized root metadata. It never executes a command and it
 * never turns a user argument string into shell syntax.
 */
object PythonCliLaunchResolver {
    private val safeCommandName = Regex("^[A-Za-z0-9._-]+$")

    internal fun isSafeCommandName(value: String): Boolean =
        value.isNotBlank() && value != "." && value != ".." && safeCommandName.matches(value)

    sealed class Resolution {
        data class DeclaredRun(val command: String) : Resolution()
        data class ConsoleScripts(val names: List<String>) : Resolution()
        data class PythonFile(val entrypoint: String) : Resolution()
        object Missing : Resolution()
        data class Invalid(val reason: InvalidReason) : Resolution()
    }

    enum class InvalidReason {
        TOML_PARSE_FAILED,
        SCRIPT_ENTRY_UNSUPPORTED,
        SCRIPT_NAME_UNSUPPORTED,
    }

    fun resolve(
        declaredRun: String?,
        pyprojectToml: String?,
        fallbackEntrypoint: String?,
    ): Resolution {
        declaredRun?.trim()?.takeIf { it.isNotBlank() }?.let {
            return Resolution.DeclaredRun(it)
        }

        if (pyprojectToml != null) {
            val parsed = runCatching { Toml.parse(pyprojectToml) }.getOrNull()
                ?: return Resolution.Invalid(InvalidReason.TOML_PARSE_FAILED)
            if (parsed.hasErrors()) {
                return Resolution.Invalid(InvalidReason.TOML_PARSE_FAILED)
            }

            when (val standard = readScriptTable(parsed, "project.scripts")) {
                is ScriptTableResult.Invalid -> return Resolution.Invalid(standard.reason)
                is ScriptTableResult.Present -> {
                    if (standard.names.isNotEmpty()) {
                        return Resolution.ConsoleScripts(standard.names)
                    }
                }
                ScriptTableResult.Absent -> Unit
            }

            when (val poetry = readScriptTable(parsed, "tool.poetry.scripts")) {
                is ScriptTableResult.Invalid -> return Resolution.Invalid(poetry.reason)
                is ScriptTableResult.Present -> {
                    if (poetry.names.isNotEmpty()) {
                        return Resolution.ConsoleScripts(poetry.names)
                    }
                }
                ScriptTableResult.Absent -> Unit
            }
        }

        val safeEntrypoint = fallbackEntrypoint
            ?.let(EmbeddedPythonEntrypointPolicy::safeRelativePath)
        return if (safeEntrypoint != null) {
            Resolution.PythonFile(safeEntrypoint)
        } else {
            Resolution.Missing
        }
    }

    private fun readScriptTable(root: TomlTable, path: String): ScriptTableResult {
        var table: TomlTable = root
        for (segment in path.split('.')) {
            val value = try {
                table.get(segment)
            } catch (_: Throwable) {
                return ScriptTableResult.Invalid(InvalidReason.SCRIPT_ENTRY_UNSUPPORTED)
            } ?: return ScriptTableResult.Absent
            if (value !is TomlTable) {
                return ScriptTableResult.Invalid(InvalidReason.SCRIPT_ENTRY_UNSUPPORTED)
            }
            table = value
        }

        val names = table.keySet().toList().sorted()
        if (names.isEmpty()) return ScriptTableResult.Present(emptyList())

        for (name in names) {
            if (!isSafeCommandName(name)) {
                return ScriptTableResult.Invalid(InvalidReason.SCRIPT_NAME_UNSUPPORTED)
            }
            val value = try {
                table.get(name)
            } catch (_: Throwable) {
                return ScriptTableResult.Invalid(InvalidReason.SCRIPT_ENTRY_UNSUPPORTED)
            }
            if (value !is String || value.isBlank()) {
                // Poetry's { reference = ..., type = "file" } form and other structured forms are
                // intentionally outside this first console-script contract.
                return ScriptTableResult.Invalid(InvalidReason.SCRIPT_ENTRY_UNSUPPORTED)
            }
        }
        return ScriptTableResult.Present(names)
    }

    private sealed class ScriptTableResult {
        object Absent : ScriptTableResult()
        data class Present(val names: List<String>) : ScriptTableResult()
        data class Invalid(val reason: InvalidReason) : ScriptTableResult()
    }
}

/**
 * One safe argv-based invocation selected for a single Termux START action.
 */
data class PythonLaunchInvocation(
    val kind: PythonLaunchKind,
    val executableName: String,
    val entrypoint: String? = null,
    val arguments: List<String> = emptyList(),
) {
    init {
        require(arguments.size <= RuntimeArgumentParser.MAX_ARGUMENT_COUNT) {
            "Too many runtime arguments"
        }
        require(arguments.all { it.length <= RuntimeArgumentParser.MAX_ARGUMENT_LENGTH }) {
            "Runtime argument is too long"
        }
        when (kind) {
            PythonLaunchKind.CONSOLE_SCRIPT -> {
                require(PythonCliLaunchResolver.isSafeCommandName(executableName)) {
                    "Unsupported console script name"
                }
                require(entrypoint == null) { "Console script cannot have a Python file entrypoint" }
            }
            PythonLaunchKind.PYTHON_FILE -> {
                require(executableName == "python") { "Python file invocation must use python" }
                require(
                    entrypoint != null &&
                        EmbeddedPythonEntrypointPolicy.safeRelativePath(entrypoint) == entrypoint,
                ) { "Unsupported Python entrypoint" }
            }
        }
    }

    companion object {
        fun consoleScript(name: String, arguments: List<String> = emptyList()): PythonLaunchInvocation =
            PythonLaunchInvocation(
                kind = PythonLaunchKind.CONSOLE_SCRIPT,
                executableName = name,
                arguments = arguments,
            )

        fun pythonFile(entrypoint: String, arguments: List<String> = emptyList()): PythonLaunchInvocation =
            PythonLaunchInvocation(
                kind = PythonLaunchKind.PYTHON_FILE,
                executableName = "python",
                entrypoint = entrypoint,
                arguments = arguments,
            )
    }
}

enum class PythonLaunchKind(val id: String) {
    CONSOLE_SCRIPT("CONSOLE_SCRIPT"),
    PYTHON_FILE("PYTHON_FILE"),
}

/**
 * Parses a user argument line without invoking a shell.
 */
object RuntimeArgumentParser {
    const val MAX_ARGUMENT_COUNT = 64
    const val MAX_ARGUMENT_LENGTH = 4096
    const val MAX_INPUT_LENGTH = 16 * 1024

    sealed class Result {
        data class Success(val arguments: List<String>) : Result()
        data class Invalid(val reason: Reason) : Result()
    }

    enum class Reason {
        INPUT_TOO_LONG,
        TOO_MANY_ARGUMENTS,
        ARGUMENT_TOO_LONG,
        UNCLOSED_QUOTE,
        INCOMPLETE_ESCAPE,
    }

    fun parse(input: String): Result {
        if (input.length > MAX_INPUT_LENGTH) {
            return Result.Invalid(Reason.INPUT_TOO_LONG)
        }

        val arguments = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false
        var tokenStarted = false

        fun finishToken(): Result.Invalid? {
            if (!tokenStarted) return null
            if (current.length > MAX_ARGUMENT_LENGTH) {
                return Result.Invalid(Reason.ARGUMENT_TOO_LONG)
            }
            arguments += current.toString()
            if (arguments.size > MAX_ARGUMENT_COUNT) {
                return Result.Invalid(Reason.TOO_MANY_ARGUMENTS)
            }
            current.setLength(0)
            tokenStarted = false
            return null
        }

        for (char in input) {
            if (escaping) {
                current.append(char)
                escaping = false
                tokenStarted = true
                continue
            }
            if (quote != null) {
                if (char == quote) {
                    quote = null
                } else if (char == '\\') {
                    escaping = true
                } else {
                    current.append(char)
                }
                tokenStarted = true
                continue
            }

            when {
                char == '\\' -> {
                    escaping = true
                    tokenStarted = true
                }
                char == '\'' || char == '"' -> {
                    quote = char
                    tokenStarted = true
                }
                char.isWhitespace() -> {
                    finishToken()?.let { return it }
                }
                else -> {
                    current.append(char)
                    tokenStarted = true
                }
            }
        }

        if (quote != null) return Result.Invalid(Reason.UNCLOSED_QUOTE)
        if (escaping) return Result.Invalid(Reason.INCOMPLETE_ESCAPE)
        finishToken()?.let { return it }
        return Result.Success(arguments.toList())
    }
}
