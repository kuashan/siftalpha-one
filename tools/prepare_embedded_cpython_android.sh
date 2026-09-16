#!/usr/bin/env bash
set -euo pipefail

# Keep this preparation deterministic and deliberately narrow: one official
# CPython Android artifact, one ABI, and no arbitrary URL/version input.
readonly CPYTHON_VERSION="3.14.7"
readonly CPYTHON_URL="https://www.python.org/ftp/python/3.14.7/python-3.14.7-aarch64-linux-android.tar.gz"
readonly CPYTHON_SHA256="6d50cc3aa66e414a439594089bcdfb5f1264358155c70c1f00471c24cfb477fb"
readonly CPYTHON_ARCHIVE="python-3.14.7-aarch64-linux-android.tar.gz"
readonly CPYTHON_ABI="arm64-v8a"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
generated_root="$repo_root/app/build/generated/cpython"
output_arg="${1:-$generated_root/$CPYTHON_ABI}"
if [[ "$output_arg" = /* ]]; then
  output_dir="$output_arg"
else
  output_dir="$repo_root/$output_arg"
fi

case "$output_dir" in
  "$generated_root"/*) ;;
  *)
    echo "Refusing output outside app/build/generated/cpython: $output_dir" >&2
    exit 2
    ;;
esac

mkdir -p "$output_dir"
if [[ -f "$output_dir/prepare-status.txt" ]] && \
   grep -q "^SIFTALPHA_X_CPYTHON_VERSION=$CPYTHON_VERSION$" "$output_dir/prepare-status.txt" && \
   grep -q "^SIFTALPHA_X_CPYTHON_ABI=$CPYTHON_ABI$" "$output_dir/prepare-status.txt" && \
   grep -q "^SIFTALPHA_X_CPYTHON_SHA256=$CPYTHON_SHA256$" "$output_dir/prepare-status.txt" && \
   test -f "$output_dir/prefix/lib/libpython3.14.so" && \
   test -d "$output_dir/assets/siftalphax/python/lib/python3.14"; then
  echo "Reusing verified official CPython $CPYTHON_VERSION for $CPYTHON_ABI at $output_dir"
  exit 0
fi

work_dir="$(mktemp -d)"
cleanup() {
  rm -rf "$work_dir"
}
trap cleanup EXIT

archive="$work_dir/$CPYTHON_ARCHIVE"
extract_dir="$work_dir/extracted"

curl --fail --location --silent --show-error --retry 3 \
  "$CPYTHON_URL" \
  --output "$archive"
printf '%s  %s\n' "$CPYTHON_SHA256" "$archive" | sha256sum --check --status

mkdir -p "$extract_dir"
tar --extract --gzip --file "$archive" --directory "$extract_dir" --no-same-owner
prefix="$extract_dir/prefix"
test -d "$prefix/include/python3.14"
test -f "$prefix/lib/libpython3.14.so"
test -d "$prefix/lib/python3.14"

rm -rf "$output_dir"
mkdir -p \
  "$output_dir/prefix/include" \
  "$output_dir/prefix/lib" \
  "$output_dir/assets/siftalphax/python/lib" \
  "$output_dir/assets/siftalphax/cpython" \
  "$output_dir/jniLibs/$CPYTHON_ABI"

cp -a "$prefix/include/python3.14" "$output_dir/prefix/include/"
cp -L "$prefix/lib/libpython3.14.so" "$output_dir/prefix/lib/libpython3.14.so"
cp -a "$prefix/lib/python3.14" "$output_dir/assets/siftalphax/python/lib/"

# The PoC only needs the standard library; remove test/development payloads
# that are not part of the fixed scripts or the embedding contract.
rm -rf \
  "$output_dir/assets/siftalphax/python/lib/python3.14/test" \
  "$output_dir/assets/siftalphax/python/lib/python3.14/idlelib" \
  "$output_dir/assets/siftalphax/python/lib/python3.14/tkinter" \
  "$output_dir/assets/siftalphax/python/lib/python3.14/turtledemo" \
  "$output_dir/assets/siftalphax/python/lib/python3.14/venv" \
  "$output_dir/assets/siftalphax/python/lib/python3.14/ensurepip"

for library in "$prefix"/lib/*.so; do
  if [[ -f "$library" ]]; then
    cp -L "$library" "$output_dir/jniLibs/$CPYTHON_ABI/"
  fi
done

cp "$prefix/lib/python3.14/LICENSE.txt" \
  "$output_dir/assets/siftalphax/cpython/LICENSE.txt"
cp "$repo_root/third_party/cpython/PROVENANCE.md" \
  "$output_dir/assets/siftalphax/cpython/PROVENANCE.md"

printf '%s\n' \
  "SIFTALPHA_X_CPYTHON_PREPARED=YES" \
  "SIFTALPHA_X_CPYTHON_VERSION=$CPYTHON_VERSION" \
  "SIFTALPHA_X_CPYTHON_ABI=$CPYTHON_ABI" \
  "SIFTALPHA_X_CPYTHON_SHA256=$CPYTHON_SHA256" \
  "SIFTALPHA_X_CPYTHON_BINARY_IN_REPOSITORY=NO" \
  > "$output_dir/prepare-status.txt"

echo "Prepared official CPython $CPYTHON_VERSION for $CPYTHON_ABI at $output_dir"
