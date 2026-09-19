#!/usr/bin/env bash
set -euo pipefail

OUTPUT_DIR="${1:-}"
if [[ -z "$OUTPUT_DIR" ]]; then
  echo "usage: $0 <output-dir>" >&2
  exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

"$SCRIPT_DIR/prepare_internal_alpine_android.sh" "$WORK/runtime"

PROOT="$WORK/runtime/jniLibs/arm64-v8a/libproot.so"
LOADER="$WORK/runtime/jniLibs/arm64-v8a/libproot-loader.so"
ROOTFS="$WORK/runtime/assets/siftalphax/alpine/alpine-minirootfs.tar.gz"
PROVENANCE="$WORK/runtime/assets/siftalphax/alpine/PROVENANCE.txt"
STATUS="$WORK/runtime/prepare-status.txt"

for file in "$PROOT" "$LOADER" "$ROOTFS" "$PROVENANCE" "$STATUS"; do
  [[ -s "$file" ]] || { echo "missing Internal Alpine artifact: $file" >&2; exit 1; }
done

READELF="${ANDROID_NDK_HOME:-$ANDROID_SDK_ROOT/ndk/27.3.13750724}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
[[ -x "$READELF" ]] || { echo "missing llvm-readelf: $READELF" >&2; exit 1; }
"$READELF" -h "$PROOT" | grep -q "AArch64"
"$READELF" -h "$LOADER" | grep -q "AArch64"

grep -q '^ALPINE_VERSION=' "$STATUS"
grep -q '^ALPINE_SHA256=' "$STATUS"
grep -q '^PROOT_VERSION=' "$STATUS"
grep -q '^SIFTALPHA_INTERNAL_ALPINE_FORMAT=' "$PROVENANCE"

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cp "$PROOT" "$OUTPUT_DIR/libproot.so"
cp "$LOADER" "$OUTPUT_DIR/libproot-loader.so"
cp "$ROOTFS" "$OUTPUT_DIR/alpine-minirootfs.tar.gz"
cp "$PROVENANCE" "$OUTPUT_DIR/provenance.txt"
cp "$STATUS" "$OUTPUT_DIR/prepare-status.txt"

sha256sum   "$OUTPUT_DIR/libproot.so"   "$OUTPUT_DIR/libproot-loader.so"   "$OUTPUT_DIR/alpine-minirootfs.tar.gz"   | tee "$OUTPUT_DIR/artifact-sha256.txt"

echo "SIFTALPHA_INTERNAL_ALPINE_PROBE=PASS"
