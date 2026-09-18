#!/usr/bin/env bash
set -euo pipefail

OUTPUT_DIR="${1:-}"
if [[ -z "$OUTPUT_DIR" ]]; then
  echo "usage: $0 <output-dir>" >&2
  exit 2
fi

PROOT_VERSION="5.1.107.92"
PROOT_SHA256="29385d1ddb619a9c4449ab512bfd55032034b22f724ddf98fc95ff300ea32135"
TALLOC_VERSION="2.4.2"
TALLOC_SHA256="85ecf9e465e20f98f9950a52e9a411e14320bc555fa257d87697b7e7a9b1d8a6"
ALPINE_VERSION="3.21.8"
ANDROID_API="26"
NDK_VERSION="27.3.13750724"

: "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT is required}"
NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_SDK_ROOT/ndk/$NDK_VERSION}"
TOOLCHAIN="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
CC="$TOOLCHAIN/aarch64-linux-android${ANDROID_API}-clang"
AR="$TOOLCHAIN/llvm-ar"
STRIP="$TOOLCHAIN/llvm-strip"
OBJCOPY="$TOOLCHAIN/llvm-objcopy"
OBJDUMP="$TOOLCHAIN/llvm-objdump"
RANLIB="$TOOLCHAIN/llvm-ranlib"
READELF="$TOOLCHAIN/llvm-readelf"

for tool in "$CC" "$AR" "$STRIP" "$OBJCOPY" "$OBJDUMP" "$RANLIB" "$READELF"; do
  [[ -x "$tool" ]] || { echo "missing tool: $tool" >&2; exit 1; }
done

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUTPUT_DIR"

download_verified() {
  local url="$1" expected="$2" destination="$3"
  curl --fail --location --silent --show-error "$url" --output "$destination"
  printf '%s  %s\n' "$expected" "$destination" | sha256sum --check --status
}

PROOT_ZIP="$WORK/proot.zip"
download_verified   "https://github.com/termux/proot/archive/refs/tags/v${PROOT_VERSION}.zip"   "$PROOT_SHA256"   "$PROOT_ZIP"
unzip -q "$PROOT_ZIP" -d "$WORK"
PROOT_SRC="$WORK/proot-${PROOT_VERSION}"

# Android NDK r27 uses modern C99 diagnostics. PRoot 5.1.107.92's ashmem/memfd
# extension uses strcmp/memset without directly including string.h, which older
# toolchains accepted as an implicit declaration. Keep the upstream source
# immutable and apply this narrow, auditable build-time compatibility patch.
python3 - "$PROOT_SRC/src/extension/ashmem_memfd/ashmem_memfd.c" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
include = "#include <string.h>\n"
if include not in text:
    marker = "#include <stdlib.h>\n"
    if marker not in text:
        raise SystemExit("ashmem_memfd include anchor changed")
    text = text.replace(marker, marker + include, 1)
    path.write_text(text)
PY

TALLOC_TGZ="$WORK/talloc.tar.gz"
download_verified   "https://download.samba.org/pub/talloc/talloc-${TALLOC_VERSION}.tar.gz"   "$TALLOC_SHA256"   "$TALLOC_TGZ"
tar -xzf "$TALLOC_TGZ" -C "$WORK"
TALLOC_SRC="$WORK/talloc-${TALLOC_VERSION}"
TALLOC_COMPAT="$WORK/talloc-compat"
mkdir -p "$TALLOC_COMPAT"
cp "$TALLOC_SRC/talloc.c" "$TALLOC_COMPAT/"
cp "$TALLOC_SRC/talloc.h" "$TALLOC_COMPAT/"
cat >"$TALLOC_COMPAT/replace.h" <<'EOF'
#ifndef SIFTALPHA_TALLOC_REPLACE_H
#define SIFTALPHA_TALLOC_REPLACE_H
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <stdint.h>
#include <string.h>
#include <stdbool.h>
#include <errno.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/auxv.h>
#define TALLOC_BUILD_VERSION_MAJOR 2
#define TALLOC_BUILD_VERSION_MINOR 4
#define TALLOC_BUILD_VERSION_RELEASE 2
#define HAVE_SYS_AUXV_H 1
#define HAVE_INTPTR_T 1
#define HAVE_VA_COPY 1
#define HAVE_CONSTRUCTOR_ATTRIBUTE 1
#define VALGRIND_MAKE_MEM_UNDEFINED(p,n) do { (void)(p); (void)(n); } while (0)
#define VALGRIND_MAKE_MEM_DEFINED(p,n) do { (void)(p); (void)(n); } while (0)
#define VALGRIND_MAKE_MEM_NOACCESS(p,n) do { (void)(p); (void)(n); } while (0)
#ifndef ZERO_STRUCT
#define ZERO_STRUCT(x) memset((char *)&(x), 0, sizeof(x))
#endif
#ifndef discard_const
#define discard_const(ptr) ((void *)((uintptr_t)(ptr)))
#endif
#ifndef MIN
#define MIN(a,b) ((a) < (b) ? (a) : (b))
#endif
#ifndef MAX
#define MAX(a,b) ((a) > (b) ? (a) : (b))
#endif
#endif
EOF

"$CC" -c "$TALLOC_COMPAT/talloc.c"   -o "$WORK/talloc.o"   -I"$TALLOC_COMPAT"   -fPIC -O2 -std=gnu99   -DHAVE_STDARG_H=1 -DHAVE_VA_COPY=1 -DHAVE_UNISTD_H=1 -DHAVE_INTPTR_T=1
"$AR" rcs "$WORK/libtalloc.a" "$WORK/talloc.o"
"$RANLIB" "$WORK/libtalloc.a"

CPPFLAGS="-D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -I. -DARG_MAX=131072 -I$TALLOC_COMPAT -DVERSION=\\\"${PROOT_VERSION}\\\""
CFLAGS="-O2 -Wall -Wextra -fPIE -DPROOT_UNBUNDLE_LOADER=\\\"/siftalpha-proot\\\""
LDFLAGS="-Wl,-z,noexecstack -pie -L$WORK -ltalloc"

make -C "$PROOT_SRC/src"   CC="$CC"   STRIP="$STRIP"   OBJCOPY="$OBJCOPY"   OBJDUMP="$OBJDUMP"   CPPFLAGS="$CPPFLAGS"   CFLAGS="$CFLAGS"   LDFLAGS="$LDFLAGS"   PROOT_UNBUNDLE_LOADER="/siftalpha-proot"   -j"$(nproc)"

"$STRIP" "$PROOT_SRC/src/proot"
"$STRIP" "$PROOT_SRC/src/loader/loader"

install -m 0755 "$PROOT_SRC/src/proot" "$OUTPUT_DIR/libproot.so"
install -m 0755 "$PROOT_SRC/src/loader/loader" "$OUTPUT_DIR/libproot-loader.so"

"$READELF" -h "$OUTPUT_DIR/libproot.so" >"$WORK/libproot.readelf.txt"
"$READELF" -h "$OUTPUT_DIR/libproot-loader.so" >"$WORK/libproot-loader.readelf.txt"
grep -q "AArch64" "$WORK/libproot.readelf.txt"
grep -q "AArch64" "$WORK/libproot-loader.readelf.txt"

ALPINE_NAME="alpine-minirootfs-${ALPINE_VERSION}-aarch64.tar.gz"
ALPINE_BASE="https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64"
curl --fail --location --silent --show-error "$ALPINE_BASE/$ALPINE_NAME.sha256"   --output "$WORK/alpine.sha256"
curl --fail --location --silent --show-error "$ALPINE_BASE/$ALPINE_NAME"   --output "$OUTPUT_DIR/$ALPINE_NAME"
(
  cd "$OUTPUT_DIR"
  sha256sum --check "$WORK/alpine.sha256"
)
ALPINE_SHA256="$(sha256sum "$OUTPUT_DIR/$ALPINE_NAME" | awk '{print $1}')"

cat >"$OUTPUT_DIR/provenance.txt" <<EOF
SIFTALPHA_INTERNAL_ALPINE_PROBE=1
PROOT_VERSION=$PROOT_VERSION
PROOT_SOURCE=https://github.com/termux/proot/archive/refs/tags/v$PROOT_VERSION.zip
PROOT_SOURCE_SHA256=$PROOT_SHA256
PROOT_LICENSE=GPL-2.0
TALLOC_VERSION=$TALLOC_VERSION
TALLOC_SOURCE=https://download.samba.org/pub/talloc/talloc-$TALLOC_VERSION.tar.gz
TALLOC_SOURCE_SHA256=$TALLOC_SHA256
ALPINE_VERSION=$ALPINE_VERSION
ALPINE_SOURCE=$ALPINE_BASE/$ALPINE_NAME
ALPINE_SHA256=$ALPINE_SHA256
ANDROID_API=$ANDROID_API
NDK_VERSION=$NDK_VERSION
EOF

sha256sum   "$OUTPUT_DIR/libproot.so"   "$OUTPUT_DIR/libproot-loader.so"   "$OUTPUT_DIR/$ALPINE_NAME"   | tee "$OUTPUT_DIR/artifact-sha256.txt"

echo "SIFTALPHA_INTERNAL_ALPINE_PROBE=PASS"
