package com.siftalpha.studio.runtime

/**
 * High-confidence, read-only configuration diagnosis for completed Runtime output.
 *
 * This intentionally does not guess provider/package names. A variable is surfaced only when the
 * application output itself names an environment variable and states that it is missing/required.
 * Generic messages such as "API key is required" are reported without inventing a variable name.
 */
object RuntimeConfigurationDiagnostic {

    data class Result(
        val missingEnvironmentNames: List<String>,
        val unnamedCredentialRequired: Boolean,
        val missingCliArguments: List<String> = emptyList(),
    ) {
        val hasActionableFinding: Boolean
            get() = missingEnvironmentNames.isNotEmpty() ||
                unnamedCredentialRequired ||
                missingCliArguments.isNotEmpty()
    }

    private data class NamedMatch(
        val position: Int,
        val name: String,
    )

    private val ENV_NAME = Regex("^[A-Z_][A-Z0-9_]*$")
    private val CLI_ARGUMENT = Regex("^(?:--?)?[A-Za-z0-9_][A-Za-z0-9_.-]*$")
    private val argparseRequired = Regex(
        "(?im)(?:^|\\n)[^\\n]*?the following arguments are required:\\s*([^\\n\\r]+)",
    )
    private val clickMissingArgument = Regex(
        "(?im)(?:^|\\n)(?:Error:\\s*)?Missing argument ['\"]([^'\"]+)['\"]\\.?\\s*$",
    )
    private val clickMissingOption = Regex(
        "(?im)(?:^|\\n)(?:Error:\\s*)?Missing option ['\"]([^'\"]+)['\"]\\.?\\s*$",
    )
    private val clickUsage = Regex(
        "(?im)^Usage:\\s+[^\\n]*?\\[OPTIONS\\]\\s+([^\\n\\r]+)$",
    )

    private val namedPatterns = listOf(
        Regex(
            "(?i)(?:missing|required|unset|not\\s+set)[^\\n]{0,80}(?:environment\\s+variable|env(?:ironment)?\\s+var(?:iable)?)\\s*[:=]?\\s*[`'\"]?([A-Z_][A-Z0-9_]*)",
        ),
        Regex(
            "(?i)(?:environment\\s+variable|env(?:ironment)?\\s+var(?:iable)?)\\s*[`'\"]?([A-Z_][A-Z0-9_]*)[`'\"]?[^\\n]{0,80}(?:missing|required|unset|not\\s+set)",
        ),
        Regex(
            "(?i)\\b([A-Z_][A-Z0-9_]*(?:API_KEY|API_KEYS|TOKEN|TOKENS|SECRET|SECRETS|PASSWORD|CREDENTIALS?))\\b[^\\n]{0,60}(?:is\\s+)?(?:missing|required|unset|not\\s+set)",
        ),
        Regex(
            "(?i)(?:missing|required)\\s+(?:configuration|credential)\\s*[:=]?\\s*[`'\"]?([A-Z_][A-Z0-9_]*)",
        ),
        // Python's os.environ["NAME"] commonly surfaces as KeyError: "NAME". Restrict this
        // fallback to credential-shaped names so ordinary dictionary KeyError messages are not
        // turned into configuration prompts.
        Regex(
            "(?i)KeyError\\s*:\\s*[`'\"]?([A-Z_][A-Z0-9_]*(?:API_KEY|API_SECRET|TOKEN|SECRET|PASSWORD|CREDENTIALS?))[`'\"]?",
        ),
    )

    private val genericCredentialPatterns = listOf(
        Regex("(?i)\\bAPI\\s+key\\b[^\\n]{0,50}(?:missing|required|not\\s+set|unset)"),
        Regex("(?i)(?:missing|required)[^\\n]{0,40}\\bAPI\\s+key\\b"),
        Regex("(?i)\\bcredential(?:s)?\\b[^\\n]{0,50}(?:missing|required|not\\s+configured)"),
    )

    fun inspect(text: String): Result {
        val matches = mutableListOf<NamedMatch>()
        namedPatterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                val candidate = match.groupValues.getOrNull(1).orEmpty().uppercase()
                if (ENV_NAME.matches(candidate) && candidate !in IGNORED_GENERIC_WORDS) {
                    val group = match.groups[1]
                    matches += NamedMatch(
                        position = group?.range?.first ?: match.range.first,
                        name = candidate,
                    )
                }
            }
        }

        val names = linkedSetOf<String>()
        matches.sortedBy { it.position }.forEach { names += it.name }

        val cliArguments = linkedSetOf<String>()
        argparseRequired.findAll(text).forEach { match ->
            match.groupValues.getOrNull(1)
                .orEmpty()
                .split(',')
                .asSequence()
                .map { it.trim().trim('\'', '"') }
                .filter { it.isNotBlank() && CLI_ARGUMENT.matches(it) }
                .forEach { cliArguments += it }
        }
        clickUsage.findAll(text).forEach { match ->
            match.groupValues.getOrNull(1)
                .orEmpty()
                .trim()
                .split(Regex("\\s+"))
                .asSequence()
                .map { it.trim().trimEnd(',', '.', ':') }
                .filter { token ->
                    token.isNotBlank() &&
                        !token.startsWith("[") &&
                        token.matches(Regex("^[A-Z][A-Z0-9_.-]*$")) &&
                        token !in CLICK_USAGE_META_WORDS
                }
                .forEach { cliArguments += it }
        }
        clickMissingArgument.findAll(text).forEach { match ->
            match.groupValues.getOrNull(1)
                .orEmpty()
                .trim()
                .takeIf { it.isNotBlank() && CLI_ARGUMENT.matches(it) }
                ?.let { cliArguments += it }
        }
        clickMissingOption.findAll(text).forEach { match ->
            match.groupValues.getOrNull(1)
                .orEmpty()
                .trim()
                .takeIf { it.isNotBlank() && CLI_ARGUMENT.matches(it) }
                ?.let { cliArguments += it }
        }

        val unnamed = names.isEmpty() &&
            cliArguments.isEmpty() &&
            genericCredentialPatterns.any { it.containsMatchIn(text) }
        return Result(
            missingEnvironmentNames = names.take(MAX_NAMES),
            unnamedCredentialRequired = unnamed,
            missingCliArguments = cliArguments.take(MAX_CLI_ARGUMENTS),
        )
    }

    private const val MAX_NAMES = 10
    private const val MAX_CLI_ARGUMENTS = 16
    private val CLICK_USAGE_META_WORDS = setOf(
        "COMMAND",
        "ARGS",
        "OPTIONS",
    )
    private val IGNORED_GENERIC_WORDS = setOf(
        "API_KEY",
        "API_KEYS",
        "TOKEN",
        "SECRET",
        "PASSWORD",
        "CREDENTIAL",
        "CREDENTIALS",
    )
}
