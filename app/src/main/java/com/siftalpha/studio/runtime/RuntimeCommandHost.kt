package com.siftalpha.studio.runtime

import com.siftalpha.studio.project.V04ProjectGateway

/**
 * Host-specific shell facilities used by executable runtime adapters.
 *
 * Language adapters own language/package-manager behavior. The host owns how commands are wrapped
 * for the current Android execution environment. This separation allows a future built-in
 * SiftAlpha Runtime Host to replace Termux/PRoot without redesigning every language adapter.
 */
interface RuntimeCommandHost {
    fun runtimeSupported(): Boolean
    fun runtimeUnsupportedReason(): String
    fun sharedRoot(): String
    fun runtimeId(folderName: String): String
    fun sh(value: String): String
    fun wrapUbuntu(inner: String): String

    fun wrapUbuntuCancelable(runtimeId: String, operation: String, inner: String): String =
        wrapUbuntu(inner)

    fun wrapProjectActivity(
        runtimeId: String,
        operation: String,
        shellScript: String,
    ): String = shellScript

    fun stopProjectActivities(runtimeId: String): String = ""

    fun hostPreamble(): String
    fun hostProcessHelpers(): String
}

/**
 * Installs a tiny Termux-side proot-distro wrapper when Termux exposes a readable resolver file.
 *
 * Android does not guarantee a conventional host /etc/resolv.conf. Termux keeps its own resolver
 * configuration under $PREFIX/etc/resolv.conf; binding that exact file into the PRoot guest keeps
 * DNS aligned with the host without hard-coding public DNS servers. If the file is unavailable,
 * the bridge is not installed and proot-distro keeps its normal behaviour.
 */
internal object TermuxProotDnsBridge {
    fun setupShell(): String = """
        real_proot="${'$'}(command -v proot-distro)"
        termux_resolv=''
        if [ -n "${'$'}{PREFIX:-}" ] && [ -r "${'$'}PREFIX/etc/resolv.conf" ]; then
          termux_resolv="${'$'}PREFIX/etc/resolv.conf"
        fi
        if [ -n "${'$'}termux_resolv" ]; then
          proot_wrapper_dir="${'$'}runtime_dir/bin"
          proot_wrapper="${'$'}proot_wrapper_dir/proot-distro"
          mkdir -p "${'$'}proot_wrapper_dir"
          cat >"${'$'}proot_wrapper" <<'SIFTALPHA_PROOT_WRAPPER'
#!/data/data/com.termux/files/usr/bin/bash
if [ "${'$'}{1:-}" = 'login' ] && [ -n "${'$'}{SIFTALPHA_TERMUX_RESOLV:-}" ] && [ -r "${'$'}SIFTALPHA_TERMUX_RESOLV" ]; then
  shift
  exec "${'$'}SIFTALPHA_REAL_PROOT_DISTRO" login --bind "${'$'}SIFTALPHA_TERMUX_RESOLV:/etc/resolv.conf" "${'$'}@"
fi
exec "${'$'}SIFTALPHA_REAL_PROOT_DISTRO" "${'$'}@"
SIFTALPHA_PROOT_WRAPPER
          chmod 700 "${'$'}proot_wrapper"
          export SIFTALPHA_REAL_PROOT_DISTRO="${'$'}real_proot"
          export SIFTALPHA_TERMUX_RESOLV="${'$'}termux_resolv"
          export PATH="${'$'}proot_wrapper_dir:${'$'}PATH"
        fi
    """.trimIndent()
}

internal object TermuxProjectActivityContract {
    fun wrap(
        runtimeId: String,
        operation: String,
        quotedShellScript: String,
        hostPreamble: String,
        hostProcessHelpers: String,
    ): String {
        require(runtimeId.matches(Regex("[A-Za-z0-9._-]+"))) { "invalid runtime id" }
        require(operation.matches(Regex("[A-Za-z0-9._-]+"))) { "invalid operation id" }
        return """
            ${hostPreamble}
            ${hostProcessHelpers}
            activity_pid_file="${'runtime_dir/$runtimeId.activity.@D@operation.pid"
            activity_pgid_file="@D@runtime_dir/$runtimeId.activity.@D@operation.pgid"
            old_pid="@D@(cat "@D@activity_pid_file" 2>/dev/null || true)"
            old_pgid="@D@(cat "@D@activity_pgid_file" 2>/dev/null || true)"
            if siftalpha_pid_alive "@D@old_pid" || { [ -n "@D@old_pgid" ] && siftalpha_group_alive "@D@old_pgid"; }; then
              echo 'SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE'
              exit 80
            fi
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}runtime_dir/$runtimeId.activity.${'operation.pid"
            activity_pgid_file="@D@runtime_dir/$runtimeId.activity.@D@operation.pgid"
            old_pid="@D@(cat "@D@activity_pid_file" 2>/dev/null || true)"
            old_pgid="@D@(cat "@D@activity_pgid_file" 2>/dev/null || true)"
            if siftalpha_pid_alive "@D@old_pid" || { [ -n "@D@old_pgid" ] && siftalpha_group_alive "@D@old_pgid"; }; then
              echo 'SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE'
              exit 80
            fi
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}operation.pid"
            activity_pgid_file="${'runtime_dir/$runtimeId.activity.@D@operation.pgid"
            old_pid="@D@(cat "@D@activity_pid_file" 2>/dev/null || true)"
            old_pgid="@D@(cat "@D@activity_pgid_file" 2>/dev/null || true)"
            if siftalpha_pid_alive "@D@old_pid" || { [ -n "@D@old_pgid" ] && siftalpha_group_alive "@D@old_pgid"; }; then
              echo 'SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE'
              exit 80
            fi
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}runtime_dir/$runtimeId.activity.${'operation.pgid"
            old_pid="@D@(cat "@D@activity_pid_file" 2>/dev/null || true)"
            old_pgid="@D@(cat "@D@activity_pgid_file" 2>/dev/null || true)"
            if siftalpha_pid_alive "@D@old_pid" || { [ -n "@D@old_pgid" ] && siftalpha_group_alive "@D@old_pgid"; }; then
              echo 'SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE'
              exit 80
            fi
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}operation.pgid"
            old_pid="${'(cat "@D@activity_pid_file" 2>/dev/null || true)"
            old_pgid="@D@(cat "@D@activity_pgid_file" 2>/dev/null || true)"
            if siftalpha_pid_alive "@D@old_pid" || { [ -n "@D@old_pgid" ] && siftalpha_group_alive "@D@old_pgid"; }; then
              echo 'SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE'
              exit 80
            fi
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}(cat "${'activity_pid_file" 2>/dev/null || true)"
            old_pgid="@D@(cat "@D@activity_pgid_file" 2>/dev/null || true)"
            if siftalpha_pid_alive "@D@old_pid" || { [ -n "@D@old_pgid" ] && siftalpha_group_alive "@D@old_pgid"; }; then
              echo 'SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE'
              exit 80
            fi
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}activity_pid_file" 2>/dev/null || true)"
            old_pgid="${'(cat "@D@activity_pgid_file" 2>/dev/null || true)"
            if siftalpha_pid_alive "@D@old_pid" || { [ -n "@D@old_pgid" ] && siftalpha_group_alive "@D@old_pgid"; }; then
              echo 'SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE'
              exit 80
            fi
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}(cat "${'activity_pgid_file" 2>/dev/null || true)"
            if siftalpha_pid_alive "@D@old_pid" || { [ -n "@D@old_pgid" ] && siftalpha_group_alive "@D@old_pgid"; }; then
              echo 'SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE'
              exit 80
            fi
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}activity_pgid_file" 2>/dev/null || true)"
            if siftalpha_pid_alive "${'old_pid" || { [ -n "@D@old_pgid" ] && siftalpha_group_alive "@D@old_pgid"; }; then
              echo 'SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE'
              exit 80
            fi
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}old_pid" || { [ -n "${'old_pgid" ] && siftalpha_group_alive "@D@old_pgid"; }; then
              echo 'SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE'
              exit 80
            fi
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}old_pgid" ] && siftalpha_group_alive "${'old_pgid"; }; then
              echo 'SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE'
              exit 80
            fi
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}old_pgid"; }; then
              echo 'SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE'
              exit 80
            fi
            rm -f -- "${'activity_pid_file" "@D@activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}activity_pid_file" "${'activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid bash -lc ${quotedShellScript} &
              activity_pid=${'!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}!
              printf '%s\n' "${'activity_pid" >"@D@activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}activity_pid" >"${'activity_pid_file"
              printf '%s\n' "@D@activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}activity_pid_file"
              printf '%s\n' "${'activity_pid" >"@D@activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}activity_pid" >"${'activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=@D@!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}activity_pgid_file"
            else
              bash -lc ${quotedShellScript} &
              activity_pid=${'!
              printf '%s\n' "@D@activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}!
              printf '%s\n' "${'activity_pid" >"@D@activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}activity_pid" >"${'activity_pid_file"
            fi
            wait "@D@activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}activity_pid_file"
            fi
            wait "${'activity_pid"
            activity_code=@D@?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}activity_pid"
            activity_code=${'?
            rm -f -- "@D@activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}?
            rm -f -- "${'activity_pid_file" "@D@activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}activity_pid_file" "${'activity_pgid_file"
            exit "@D@activity_code"
        """.trimIndent()
    }
}

}activity_pgid_file"
            exit "${'activity_code"
        """.trimIndent()
    }
}

}activity_code"
        """.trimIndent()
    }
}

class TermuxProotRuntimeHost(
    private val gateway: V04ProjectGateway,
) : RuntimeCommandHost {

    override fun runtimeSupported(): Boolean = gateway.runtimeSharedRootRelativePath() != null

    override fun runtimeUnsupportedReason(): String =
        "当前运行功能要求项目根目录位于 Android 内部共享存储。" +
            "建议使用 AcodeProjects 或内部存储中的其他目录。"

    override fun sharedRoot(): String {
        val relative = gateway.runtimeSharedRootRelativePath() ?: error(runtimeUnsupportedReason())
        return if (relative.isBlank()) "/storage/emulated/0" else "/storage/emulated/0/$relative"
    }

    override fun runtimeId(folderName: String): String {
        val readable = folderName.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .take(30)
            .ifBlank { "project" }
        return "$readable-${Integer.toHexString(folderName.hashCode())}"
    }

    override fun sh(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    override fun wrapUbuntu(inner: String): String = """
        ${hostPreamble()}
        proot-distro login --bind "${'$'}ROOT:/root/projects" ubuntu -- bash -lc ${sh(inner)}
    """.trimIndent()

    override fun wrapUbuntuCancelable(
        runtimeId: String,
        operation: String,
        inner: String,
    ): String {
        require(runtimeId.matches(Regex("[A-Za-z0-9._-]+"))) { "invalid runtime id" }
        require(operation.matches(Regex("[A-Za-z0-9._-]+"))) { "invalid operation id" }
        return """
            ${hostPreamble()}
            ${hostProcessHelpers()}
            activity_pid_file="${'$'}runtime_dir/$runtimeId.$operation.pid"
            activity_pgid_file="${'$'}runtime_dir/$runtimeId.$operation.pgid"
            old_pid="${'$'}(cat "${'$'}activity_pid_file" 2>/dev/null || true)"
            old_pgid="${'$'}(cat "${'$'}activity_pgid_file" 2>/dev/null || true)"
            if siftalpha_pid_alive "${'$'}old_pid" || { [ -n "${'$'}old_pgid" ] && siftalpha_group_alive "${'$'}old_pgid"; }; then
              echo 'SIFTALPHA_ERROR=PROJECT_OPERATION_ALREADY_ACTIVE'
              exit 80
            fi
            rm -f -- "${'$'}activity_pid_file" "${'$'}activity_pgid_file"
            set +e
            if command -v setsid >/dev/null 2>&1; then
              setsid proot-distro login --bind "${'$'}ROOT:/root/projects" ubuntu -- bash -lc ${sh(inner)} &
              activity_pid=${'$'}!
              printf '%s\n' "${'$'}activity_pid" >"${'$'}activity_pgid_file"
            else
              proot-distro login --bind "${'$'}ROOT:/root/projects" ubuntu -- bash -lc ${sh(inner)} &
              activity_pid=${'$'}!
            fi
            printf '%s\n' "${'$'}activity_pid" >"${'$'}activity_pid_file"
            wait "${'$'}activity_pid"
            activity_code=${'$'}?
            rm -f -- "${'$'}activity_pid_file" "${'$'}activity_pgid_file"
            exit "${'$'}activity_code"
        """.trimIndent()
    }

    override fun wrapProjectActivity(
        runtimeId: String,
        operation: String,
        shellScript: String,
    ): String = TermuxProjectActivityContract.wrap(
        runtimeId = runtimeId,
        operation = operation,
        quotedShellScript = sh(shellScript),
        hostPreamble = hostPreamble(),
        hostProcessHelpers = hostProcessHelpers(),
    )

    override fun stopProjectActivities(runtimeId: String): String {
        require(runtimeId.matches(Regex("[A-Za-z0-9._-]+"))) { "invalid runtime id" }
        return """
            siftalpha_activity_stop_failed=0
            for activity_name in prepare start status logs clean; do
              activity_pid_file="${'$'}runtime_dir/$runtimeId.activity.${'$'}activity_name.pid"
              activity_pgid_file="${'$'}runtime_dir/$runtimeId.activity.${'$'}activity_name.pgid"
              activity_pid="${'$'}(cat "${'$'}activity_pid_file" 2>/dev/null || true)"
              activity_pgid="${'$'}(cat "${'$'}activity_pgid_file" 2>/dev/null || true)"
              if [ -n "${'$'}activity_pid" ] || [ -n "${'$'}activity_pgid" ]; then
                if ! siftalpha_stop_tree "${'$'}activity_pid" "${'$'}activity_pgid"; then
                  siftalpha_activity_stop_failed=1
                  printf 'SIFTALPHA_ACTIVITY_STOP_FAILED=%s\n' "${'$'}activity_name"
                else
                  printf 'SIFTALPHA_ACTIVITY_STOPPED=%s\n' "${'$'}activity_name"
                fi
              fi
              rm -f -- "${'$'}activity_pid_file" "${'$'}activity_pgid_file"
            done
        """.trimIndent()
    }

    override fun hostPreamble(): String = """
        set -e
        ROOT=${sh(sharedRoot())}
        if [ ! -d "${'$'}ROOT" ]; then
          echo 'SIFTALPHA_ERROR=SHARED_STORAGE_UNAVAILABLE'
          exit 70
        fi
        if ! command -v proot-distro >/dev/null 2>&1; then
          echo 'SIFTALPHA_ERROR=PROOT_DISTRO_MISSING'
          exit 71
        fi
        runtime_dir="${'$'}HOME/.siftalpha/runtime"
        mkdir -p "${'$'}runtime_dir"
        ${TermuxProotDnsBridge.setupShell()}
    """.trimIndent()

    override fun hostProcessHelpers(): String = """
        siftalpha_pid_alive() {
          [ -n "${'$'}1" ] && kill -0 "${'$'}1" 2>/dev/null
        }

        siftalpha_group_alive() {
          [ -n "${'$'}1" ] && kill -0 -- "-${'$'}1" 2>/dev/null
        }

        siftalpha_descendants() {
          parent="${'$'}1"
          children_file="/proc/${'$'}parent/task/${'$'}parent/children"
          [ -r "${'$'}children_file" ] || return 0
          for child in ${'$'}(cat "${'$'}children_file" 2>/dev/null || true); do
            siftalpha_descendants "${'$'}child"
            printf '%s\n' "${'$'}child"
          done
        }

        siftalpha_leaf_descendants() {
          parent="${'$'}1"
          children_file="/proc/${'$'}parent/task/${'$'}parent/children"
          [ -r "${'$'}children_file" ] || return 0
          children="${'$'}(cat "${'$'}children_file" 2>/dev/null || true)"
          for child in ${'$'}children; do
            grand_file="/proc/${'$'}child/task/${'$'}child/children"
            grandchildren=''
            if [ -r "${'$'}grand_file" ]; then
              grandchildren="${'$'}(cat "${'$'}grand_file" 2>/dev/null || true)"
            fi
            if [ -n "${'$'}grandchildren" ]; then
              siftalpha_leaf_descendants "${'$'}child"
            else
              printf '%s\n' "${'$'}child"
            fi
          done
        }

        siftalpha_tree_alive() {
          root_pid="${'$'}1"
          pgid="${'$'}2"
          descendants="${'$'}3"
          if siftalpha_pid_alive "${'$'}root_pid"; then
            return 0
          fi
          if [ -n "${'$'}pgid" ] && siftalpha_group_alive "${'$'}pgid"; then
            return 0
          fi
          for child in ${'$'}descendants; do
            if siftalpha_pid_alive "${'$'}child"; then
              return 0
            fi
          done
          return 1
        }

        siftalpha_stop_tree() {
          root_pid="${'$'}1"
          pgid="${'$'}2"
          descendants="${'$'}(siftalpha_descendants "${'$'}root_pid" | tr '\n' ' ')"
          leaves="${'$'}(siftalpha_leaf_descendants "${'$'}root_pid" | tr '\n' ' ')"

          # Phase 1: ask only the deepest workload processes to terminate cleanly.
          # This lets the guest runner flush logs/state before the PRoot host is torn down.
          if [ -n "${'$'}leaves" ]; then
            for leaf in ${'$'}leaves; do
              kill -TERM "${'$'}leaf" 2>/dev/null || true
            done

            grace=0
            while [ "${'$'}grace" -lt 4 ]; do
              if ! siftalpha_tree_alive "${'$'}root_pid" "${'$'}pgid" "${'$'}descendants"; then
                return 0
              fi
              sleep 1
              grace=${'$'}((grace + 1))
            done
          fi

          # Phase 2: graceful shutdown did not complete; terminate the entire runtime tree.
          if [ -n "${'$'}pgid" ] && siftalpha_group_alive "${'$'}pgid"; then
            kill -TERM -- "-${'$'}pgid" 2>/dev/null || true
          fi
          for child in ${'$'}descendants; do
            kill -TERM "${'$'}child" 2>/dev/null || true
          done
          if siftalpha_pid_alive "${'$'}root_pid"; then
            kill -TERM "${'$'}root_pid" 2>/dev/null || true
          fi

          sleep 1
          if ! siftalpha_tree_alive "${'$'}root_pid" "${'$'}pgid" "${'$'}descendants"; then
            return 0
          fi

          # Phase 3: last-resort hard kill, followed by verification.
          if [ -n "${'$'}pgid" ] && siftalpha_group_alive "${'$'}pgid"; then
            kill -KILL -- "-${'$'}pgid" 2>/dev/null || true
          fi
          for child in ${'$'}descendants; do
            if siftalpha_pid_alive "${'$'}child"; then
              kill -KILL "${'$'}child" 2>/dev/null || true
            fi
          done
          if siftalpha_pid_alive "${'$'}root_pid"; then
            kill -KILL "${'$'}root_pid" 2>/dev/null || true
          fi

          sleep 1
          ! siftalpha_tree_alive "${'$'}root_pid" "${'$'}pgid" "${'$'}descendants"
        }
    """.trimIndent()
}
