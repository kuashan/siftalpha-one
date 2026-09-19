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
ALPINE_SHA256="f25a96d2846a4bc439093107c1b48a8b0c93dcb411e2cb9cfded6f790b2bc001"
ANDROID_API="26"
NDK_VERSION="27.3.13750724"
FORMAT_VERSION="1"

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

JNI_DIR="$OUTPUT_DIR/jniLibs/arm64-v8a"
ASSET_DIR="$OUTPUT_DIR/assets/siftalphax/alpine"
STATUS_FILE="$OUTPUT_DIR/prepare-status.txt"
ROOTFS_ASSET="$ASSET_DIR/alpine-minirootfs.tgzblob"
PROOT_OUT="$JNI_DIR/libproot.so"
LOADER_OUT="$JNI_DIR/libproot-loader.so"

expected_status="FORMAT_VERSION=$FORMAT_VERSION
PROOT_VERSION=$PROOT_VERSION
PROOT_SOURCE_SHA256=$PROOT_SHA256
TALLOC_VERSION=$TALLOC_VERSION
TALLOC_SOURCE_SHA256=$TALLOC_SHA256
ALPINE_VERSION=$ALPINE_VERSION
ALPINE_SHA256=$ALPINE_SHA256
ANDROID_API=$ANDROID_API
NDK_VERSION=$NDK_VERSION"

if [[ -f "$STATUS_FILE" && -s "$PROOT_OUT" && -s "$LOADER_OUT" && -s "$ROOTFS_ASSET" ]]; then
  if [[ "$(cat "$STATUS_FILE")" == "$expected_status" ]] &&
     printf '%s  %s\n' "$ALPINE_SHA256" "$ROOTFS_ASSET" | sha256sum --check --status; then
    echo "SIFTALPHA_INTERNAL_ALPINE_PREPARE=REUSED"
    exit 0
  fi
fi

for tool in "$CC" "$AR" "$STRIP" "$OBJCOPY" "$OBJDUMP" "$RANLIB" "$READELF"; do
  [[ -x "$tool" ]] || { echo "missing tool: $tool" >&2; exit 1; }
done

WORK="$(mktemp -d)"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT
rm -rf "$OUTPUT_DIR"
mkdir -p "$JNI_DIR" "$ASSET_DIR"

download_verified() {
  local url="$1" expected="$2" destination="$3"
  curl --fail --location --silent --show-error "$url" --output "$destination"
  printf '%s  %s\n' "$expected" "$destination" | sha256sum --check --status
}

PROOT_ZIP="$WORK/proot.zip"
download_verified   "https://github.com/termux/proot/archive/refs/tags/v${PROOT_VERSION}.zip"   "$PROOT_SHA256" "$PROOT_ZIP"
unzip -q "$PROOT_ZIP" -d "$WORK"
PROOT_SRC="$WORK/proot-${PROOT_VERSION}"

# PRoot 5.1.107.92 omits string.h in the Android ashmem/memfd extension.
# NDK r27 rejects the historical implicit strcmp/memset declarations.
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
    path.write_text(text.replace(marker, marker + include, 1))
PY

TALLOC_TGZ="$WORK/talloc.tar.gz"
download_verified   "https://download.samba.org/pub/talloc/talloc-${TALLOC_VERSION}.tar.gz"   "$TALLOC_SHA256" "$TALLOC_TGZ"
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

"$CC" -c "$TALLOC_COMPAT/talloc.c" -o "$WORK/talloc.o"   -I"$TALLOC_COMPAT" -fPIC -O2 -std=gnu99   -DHAVE_STDARG_H=1 -DHAVE_VA_COPY=1 -DHAVE_UNISTD_H=1 -DHAVE_INTPTR_T=1
"$AR" rcs "$WORK/libtalloc.a" "$WORK/talloc.o"
"$RANLIB" "$WORK/libtalloc.a"

CPPFLAGS="-D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -I. -DARG_MAX=131072 -I$TALLOC_COMPAT -DVERSION=\\\"${PROOT_VERSION}\\\""
CFLAGS="-O2 -Wall -Wextra -fPIE -DPROOT_UNBUNDLE_LOADER=\\\"/siftalpha-proot\\\""
LDFLAGS="-Wl,-z,noexecstack -pie -L$WORK -ltalloc"

make -C "$PROOT_SRC/src"   CC="$CC" STRIP="$STRIP" OBJCOPY="$OBJCOPY" OBJDUMP="$OBJDUMP"   CPPFLAGS="$CPPFLAGS" CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS"   PROOT_UNBUNDLE_LOADER="/siftalpha-proot" -j"$(nproc)"

"$STRIP" "$PROOT_SRC/src/proot"
"$STRIP" "$PROOT_SRC/src/loader/loader"
install -m 0755 "$PROOT_SRC/src/proot" "$PROOT_OUT"
install -m 0755 "$PROOT_SRC/src/loader/loader" "$LOADER_OUT"

"$READELF" -h "$PROOT_OUT" >"$WORK/proot.readelf"
"$READELF" -h "$LOADER_OUT" >"$WORK/loader.readelf"
grep -q "AArch64" "$WORK/proot.readelf"
grep -q "AArch64" "$WORK/loader.readelf"

ALPINE_SERIES="${ALPINE_VERSION%.*}"
ALPINE_NAME="alpine-minirootfs-${ALPINE_VERSION}-aarch64.tar.gz"
ALPINE_URL="https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_SERIES}/releases/aarch64/${ALPINE_NAME}"
download_verified "$ALPINE_URL" "$ALPINE_SHA256" "$ROOTFS_ASSET"

cat >"$ASSET_DIR/PROVENANCE.txt" <<EOF
SIFTALPHA_INTERNAL_ALPINE_FORMAT=$FORMAT_VERSION
PROOT_VERSION=$PROOT_VERSION
PROOT_SOURCE=https://github.com/termux/proot/archive/refs/tags/v$PROOT_VERSION.zip
PROOT_SOURCE_SHA256=$PROOT_SHA256
PROOT_LICENSE=GPL-2.0
TALLOC_VERSION=$TALLOC_VERSION
TALLOC_SOURCE=https://download.samba.org/pub/talloc/talloc-$TALLOC_VERSION.tar.gz
TALLOC_SOURCE_SHA256=$TALLOC_SHA256
ALPINE_VERSION=$ALPINE_VERSION
ALPINE_SOURCE=$ALPINE_URL
ALPINE_SHA256=$ALPINE_SHA256
ANDROID_API=$ANDROID_API
NDK_VERSION=$NDK_VERSION
EOF

printf '%s\n' "$expected_status" >"$STATUS_FILE"
sha256sum "$PROOT_OUT" "$LOADER_OUT" "$ROOTFS_ASSET" >"$OUTPUT_DIR/artifact-sha256.txt"
echo "SIFTALPHA_INTERNAL_ALPINE_PREPARE=PASS"
