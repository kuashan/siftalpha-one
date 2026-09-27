package com.siftalpha.studio.runtime

/**
 * SiftAlpha-managed CPython policy for the External Runtime.
 *
 * The Ubuntu system Python is bootstrap-only. Project environments use exact managed CPython
 * builds selected from this matrix, so distro upgrades cannot silently move projects to a newer
 * Python minor version.
 */
internal object ManagedPythonToolchainShell {
    const val POLICY_ID = "managed-python-v1"
    const val UV_VERSION = "0.12.18"
    const val UV_AARCH64_LINUX_GNU_SHA256 =
        "afb6291f3f0a6b4521fc67b947822506c41dde5b60d2189dd8f3695b2ac8c9e7"

    const val PYTHON_311 = "3.11.16"
    const val PYTHON_312 = "3.12.14"
    const val PYTHON_313 = "3.13.15"
    const val PYTHON_314 = "3.14.7"
    const val DEFAULT_PYTHON = PYTHON_312
    const val PIP_VERSION = "26.2.1"

    val supportedVersions: List<String> = listOf(
        PYTHON_312,
        PYTHON_311,
        PYTHON_313,
        PYTHON_314,
    )

    fun resolverShell(): String = """
        siftalpha_resolve_python_version() {
          required_python="${'$'}1"
          /usr/bin/python3.12 - "${'$'}required_python" <<'SIFTALPHA_PYTHON_VERSION_RESOLVER'
import sys
from pip._vendor.packaging.specifiers import SpecifierSet
from pip._vendor.packaging.version import Version

required = sys.argv[1].strip()
supported = [
    "${PYTHON_312}",
    "${PYTHON_311}",
    "${PYTHON_313}",
    "${PYTHON_314}",
]
if not required:
    print("${DEFAULT_PYTHON}")
    raise SystemExit(0)
try:
    spec = SpecifierSet(required)
except Exception:
    raise SystemExit(2)
for candidate in supported:
    if Version(candidate) in spec:
        print(candidate)
        raise SystemExit(0)
raise SystemExit(1)
SIFTALPHA_PYTHON_VERSION_RESOLVER
        }
    """.trimIndent()

    fun toolchainShell(): String = """
        ${resolverShell()}

        siftalpha_ensure_uv() {
          toolchain_root='/root/siftalpha/toolchains'
          uv_root="${'$'}toolchain_root/uv-${UV_VERSION}"
          uv_bin="${'$'}uv_root/uv"
          if [ -x "${'$'}uv_bin" ] && \
             [ "${'$'}("${'$'}uv_bin" --version 2>/dev/null || true)" = "uv ${UV_VERSION}" ]; then
            return 0
          fi

          case "${'$'}(uname -m 2>/dev/null || true)" in
            aarch64|arm64) uv_target='aarch64-unknown-linux-gnu' ;;
            *)
              echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_ARCH_UNSUPPORTED'
              return 1
              ;;
          esac

          command -v curl >/dev/null 2>&1 || {
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_TOOLCHAIN_PREREQUISITE_MISSING'
            return 1
          }
          command -v tar >/dev/null 2>&1 || {
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_TOOLCHAIN_PREREQUISITE_MISSING'
            return 1
          }

          mkdir -p "${'$'}toolchain_root"
          uv_archive="${'$'}toolchain_root/uv-${UV_VERSION}-${'$'}uv_target.tar.gz"
          uv_tmp="${'$'}toolchain_root/.uv-${UV_VERSION}-${'$'}${'$'}"
          rm -rf -- "${'$'}uv_tmp"
          mkdir -p "${'$'}uv_tmp"

          if ! curl -fL --retry 3 --connect-timeout 20 \
              "https://github.com/astral-sh/uv/releases/download/${UV_VERSION}/uv-${'$'}uv_target.tar.gz" \
              -o "${'$'}uv_archive" >>"${'$'}log" 2>&1; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_TOOLCHAIN_DOWNLOAD_FAILED'
            rm -rf -- "${'$'}uv_tmp"
            return 1
          fi

          actual_uv_sha="${'$'}(sha256sum "${'$'}uv_archive" | awk '{print ${'$'}1}')"
          if [ "${'$'}actual_uv_sha" != "${UV_AARCH64_LINUX_GNU_SHA256}" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_TOOLCHAIN_CHECKSUM_FAILED'
            rm -f -- "${'$'}uv_archive"
            rm -rf -- "${'$'}uv_tmp"
            return 1
          fi

          if ! tar -xzf "${'$'}uv_archive" -C "${'$'}uv_tmp" --strip-components=1 >>"${'$'}log" 2>&1 || \
             [ ! -x "${'$'}uv_tmp/uv" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_TOOLCHAIN_EXTRACT_FAILED'
            rm -rf -- "${'$'}uv_tmp"
            return 1
          fi

          rm -rf -- "${'$'}uv_root"
          mv -- "${'$'}uv_tmp" "${'$'}uv_root"
          rm -f -- "${'$'}uv_archive"
          return 0
        }

        siftalpha_ensure_managed_python() {
          requested_version="${'$'}1"
          siftalpha_ensure_uv || return 1

          python_install_dir='/root/siftalpha/toolchains/python'
          uv_cache_dir='/root/siftalpha/cache/uv'
          mkdir -p "${'$'}python_install_dir" "${'$'}uv_cache_dir"
          export UV_PYTHON_INSTALL_DIR="${'$'}python_install_dir"
          export UV_CACHE_DIR="${'$'}uv_cache_dir"
          export UV_PYTHON_PREFERENCE='only-managed'

          if ! "${'$'}uv_bin" python install \
              --install-dir "${'$'}python_install_dir" \
              --no-bin --no-config "${'$'}requested_version" >>"${'$'}log" 2>&1; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_INSTALL_FAILED'
            return 1
          fi

          managed_python=''
          for candidate in \
              "${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}python_install_dir"/cpython-"${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}requested_version"-*/bin/python3 \
              "${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}python_install_dir"/cpython-"${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}requested_version"-*/bin/python3.* \
              "${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}python_install_dir"/cpython-"${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}requested_version"-*/bin/python; do
            [ -x "${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}candidate" ] || continue
            candidate_version="${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}("${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}candidate" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))' 2>/dev/null || true)"
            if [ "${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}candidate_version" = "${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}requested_version" ]; then
              managed_python="${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}candidate"
              break
            fi
          done
          [ -n "${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}managed_python" ] && [ -x "${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}managed_python" ] || {
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_DISCOVERY_FAILED'
            printf 'SIFTALPHA_MANAGED_PYTHON_DIR=%s\n' "${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}python_install_dir"
            find "${'

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
}python_install_dir" -maxdepth 3 \( -type f -o -type l \) -print 2>/dev/null | head -n 40 || true
            return 1
          }

          actual_version="${'$'}("${'$'}managed_python" -c 'import sys; print(".".join(str(v) for v in sys.version_info[:3]))')"
          actual_platform="${'$'}("${'$'}managed_python" -c 'import sysconfig; print(sysconfig.get_platform())')"
          actual_executable="${'$'}("${'$'}managed_python" -c 'import sys; print(sys.executable)')"

          if [ "${'$'}actual_version" != "${'$'}requested_version" ]; then
            echo 'SIFTALPHA_ERROR=MANAGED_PYTHON_VERSION_MISMATCH'
            return 1
          fi
          case "${'$'}actual_executable" in
            /data/data/com.termux/*)
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          case "${'$'}actual_platform" in
            *android*|'')
              echo 'SIFTALPHA_ERROR=EXTERNAL_PYTHON_IDENTITY_INVALID'
              return 1
              ;;
          esac
          return 0
        }
    """.trimIndent()
}
