package com.siftalpha.studio.runtime

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.atomic.AtomicInteger

class TermuxBackend(private val context: Context) : RuntimeBackend, ExternalProviderBridge {

    override fun isAvailable(): Boolean = isTermuxInstalled()

    override fun isTermuxInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TermuxContract.PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun hasRunCommandPermission(): Boolean {
        return context.checkSelfPermission(TermuxContract.RUN_COMMAND_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun execute(command: RuntimeCommand): Int {
        check(isTermuxInstalled()) { "未检测到 Termux" }

        // Sensitive data is resolved only immediately before dispatch. It never enters
        // RuntimeCommand.shellScript / label / description.
        val secretPayload = command.secretNamespace
            ?.let { ProjectSecretStore(context).runtimePayload(it) }
        val stdinPayload = RuntimeStdinPolicy.resolvePayload(command, secretPayload)
        val effectiveShellScript = RuntimeStdinPolicy.effectiveShellScript(command, stdinPayload)

        val executionId = NEXT_ID.incrementAndGet()
        val callbackIntent = Intent(context, TermuxResultService::class.java).apply {
            putExtra(TermuxResultService.EXTRA_EXECUTION_ID, executionId)
        }

        val pendingFlags = PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        val pendingIntent = PendingIntent.getService(
            context,
            executionId,
            callbackIntent,
            pendingFlags,
        )

        val intent = Intent().apply {
            setClassName(TermuxContract.PACKAGE_NAME, TermuxContract.RUN_COMMAND_SERVICE)
            action = TermuxContract.ACTION_RUN_COMMAND
            putExtra(TermuxContract.EXTRA_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
            putExtra(TermuxContract.EXTRA_ARGUMENTS, arrayOf("-lc", effectiveShellScript))
            if (RuntimeStdinPolicy.shouldAttachStdin(command, stdinPayload)) {
                putExtra(TermuxContract.EXTRA_STDIN, stdinPayload)
            }
            putExtra(TermuxContract.EXTRA_WORKDIR, "/data/data/com.termux/files/home")
            putExtra(TermuxContract.EXTRA_BACKGROUND, command.background)
            putExtra(TermuxContract.EXTRA_COMMAND_LABEL, command.label)
            putExtra(TermuxContract.EXTRA_COMMAND_DESCRIPTION, command.description)
            putExtra(TermuxContract.EXTRA_PENDING_INTENT, pendingIntent)
        }

        context.startService(intent)
        return executionId
    }

    companion object {
        private val NEXT_ID = AtomicInteger(1000)

        val FIRST_RUN_SETUP_COMMAND = """
            mkdir -p ~/.termux
            touch ~/.termux/termux.properties
            sed -i '/allow-external-apps/d' ~/.termux/termux.properties
            printf '\nallow-external-apps = true\n' >> ~/.termux/termux.properties
            termux-reload-settings
        """.trimIndent()

        const val STORAGE_ACCESS_SETUP_COMMAND = "termux-setup-storage"
        const val PROOT_DISTRO_INSTALL_COMMAND = "pkg install -y proot-distro"
        const val UBUNTU_INSTALL_COMMAND = "proot-distro install ubuntu:24.04"
        const val UBUNTU_PYTHON_INSTALL_COMMAND =
            "proot-distro login ubuntu -- bash -lc 'apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates python3 python3-venv python3-pip && update-ca-certificates'"

        val CONNECTION_TEST = RuntimeCommand(
            shellScript = "printf 'SIFTALPHA_TERMUX_BRIDGE_OK\\n'; printf 'TERMUX_PREFIX=%s\\n' \"${'$'}PREFIX\"; uname -m",
            label = "SiftAlpha Studio 连接测试",
            description = "验证 SiftAlpha Studio 是否可以通过官方 RUN_COMMAND 接口调用 Termux。",
        )

        val RUNTIME_CAPABILITY_TEST = RuntimeCommand(
            shellScript = """
                set -eu
                command -v bash >/dev/null 2>&1 || {
                  echo 'SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=BASH' >&2
                  exit 31
                }
                if [ ! -d /storage/emulated/0 ] || ! ls /storage/emulated/0 >/dev/null 2>&1; then
                  echo 'SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=SHARED_STORAGE_ACCESS' >&2
                  exit 34
                fi
                echo 'SIFTALPHA_EXTERNAL_STORAGE_ACCESS=OK'
                command -v proot-distro >/dev/null 2>&1 || {
                  echo 'SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=PROOT_DISTRO' >&2
                  exit 32
                }

                set +e
                ubuntu_probe="$(proot-distro login ubuntu -- bash -lc '
                  set -eu
                  printf "SIFTALPHA_EXTERNAL_RUNTIME_OK\\n"
                  uname -m >/dev/null
                  if [ ! -x /usr/bin/python3 ]; then
                    echo "SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=UBUNTU_PYTHON"
                    exit 35
                  fi
                  python_executable="$(/usr/bin/python3 -c "import sys; print(sys.executable)")"
                  python_platform="$(/usr/bin/python3 -c "import sysconfig; print(sysconfig.get_platform())")"
                  case "${'$'}python_executable" in
                    /data/data/com.termux/*)
                      echo "SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=UBUNTU_PYTHON"
                      exit 35
                      ;;
                  esac
                  case "${'$'}python_platform" in
                    *android*)
                      echo "SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=UBUNTU_PYTHON"
                      exit 35
                      ;;
                  esac
                  /usr/bin/python3 -m venv --help >/dev/null 2>&1 || {
                    echo "SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=UBUNTU_PYTHON"
                    exit 35
                  }
                  /usr/bin/python3 -m pip --version >/dev/null 2>&1 || {
                    echo "SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=UBUNTU_PYTHON"
                    exit 35
                  }
                  printf "SIFTALPHA_EXTERNAL_UBUNTU_PYTHON=%s\\n" "${'$'}python_executable"
                  printf "SIFTALPHA_EXTERNAL_UBUNTU_PLATFORM=%s\\n" "${'$'}python_platform"
                ' 2>&1)"
                ubuntu_code=$?
                set -e
                printf '%s\\n' "${'$'}ubuntu_probe"
                if [ "${'$'}ubuntu_code" -ne 0 ]; then
                  if printf '%s\\n' "${'$'}ubuntu_probe" | grep -q '^SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=UBUNTU_PYTHON$'; then
                    echo 'SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=UBUNTU_PYTHON' >&2
                    exit 35
                  fi
                  echo 'SIFTALPHA_EXTERNAL_CAPABILITY_MISSING=UBUNTU' >&2
                  exit 33
                fi
            """.trimIndent(),
            label = "SiftAlpha Studio 外部运行能力检测",
            description = "验证 Termux、共享存储、PRoot Ubuntu 与 Ubuntu 自有 Python/venv/pip。",
        )

        val ENVIRONMENT_PROBE = RuntimeCommand(
            shellScript = """
                echo '=== SiftAlpha Runtime Probe ==='
                printf 'TERMUX=OK\\n'
                printf 'ARCH='; uname -m

                if [ -d /storage/emulated/0 ]; then
                  echo 'SHARED_STORAGE=OK'
                else
                  echo 'SHARED_STORAGE=MISSING'
                fi

                if command -v proot-distro >/dev/null 2>&1; then
                  echo 'PROOT_DISTRO=OK'
                else
                  echo 'PROOT_DISTRO=MISSING'
                  exit 20
                fi

                echo '--- Ubuntu ---'
                if proot-distro login ubuntu -- bash -lc '
                  echo UBUNTU=OK
                  printf "PYTHON="; /usr/bin/python3 --version 2>&1 || true
                  printf "PYTHON_EXECUTABLE="; /usr/bin/python3 -c "import sys; print(sys.executable)" 2>&1 || true
                  printf "PYTHON_PLATFORM="; /usr/bin/python3 -c "import sysconfig; print(sysconfig.get_platform())" 2>&1 || true
                  printf "PIP="; /usr/bin/python3 -m pip --version 2>&1 || true
                  printf "GIT="; git --version 2>&1 || true
                  printf "VENV="; /usr/bin/python3 -m venv --help >/dev/null 2>&1 && echo OK || echo MISSING
                  printf "TMUX="; command -v tmux >/dev/null 2>&1 && tmux -V || echo MISSING
                  printf "LIBC="; (ldd --version 2>&1 | head -n 1) || echo MISSING
                '; then
                  echo 'UBUNTU_LOGIN=OK'
                else
                  code=${'$'}?
                  printf 'UBUNTU_LOGIN=FAILED:%s\\n' "${'$'}code"
                  exit "${'$'}code"
                fi
            """.trimIndent(),
            label = "SiftAlpha Studio 运行环境检测",
            description = "检测 Termux、共享存储、proot-distro、Ubuntu、Python、pip、venv、Git、tmux 与 libc。",
        )
    }
}
