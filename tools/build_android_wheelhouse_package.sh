#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 4 ]]; then
  echo "usage: $0 <package> <version> <sdist-sha256> <output-dir>" >&2
  exit 2
fi

package="$1"
version="$2"
expected_sha256="$3"
output_dir="$4"

case "$package" in
  crc32c|cffi|cryptography) ;;
  *)
    echo "unsupported wheelhouse package: $package" >&2
    exit 2
    ;;
esac

case "$version" in
  *[!A-Za-z0-9._-]*|'')
    echo "invalid version: $version" >&2
    exit 2
    ;;
esac

case "$expected_sha256" in
  [0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]*)
    [[ "${#expected_sha256}" -eq 64 ]] || exit 2
    ;;
  *)
    echo "invalid SHA-256" >&2
    exit 2
    ;;
esac

work_dir="$(mktemp -d)"
cleanup() {
  rm -rf "$work_dir"
}
trap cleanup EXIT

download_dir="$work_dir/download"
mkdir -p "$download_dir" "$output_dir"

python -m pip download   --disable-pip-version-check   --no-deps   --no-binary=:all:   --dest "$download_dir"   "$package==$version"

mapfile -t archives < <(find "$download_dir" -maxdepth 1 -type f -name '*.tar.gz' -print)
if [[ "${#archives[@]}" -ne 1 ]]; then
  echo "expected one source distribution, found ${#archives[@]}" >&2
  exit 1
fi
archive="${archives[0]}"
printf '%s  %s\n' "$expected_sha256" "$archive" | sha256sum --check --status

source_dir="$work_dir/source"
mkdir -p "$source_dir"
tar --extract --gzip --file "$archive" --directory "$source_dir" --strip-components=1 --no-same-owner

rm -f "$output_dir"/*.whl

python -m cibuildwheel   --platform android   --output-dir "$output_dir"   "$source_dir"

mapfile -t wheels < <(find "$output_dir" -maxdepth 1 -type f -name '*.whl' -print)
if [[ "${#wheels[@]}" -ne 1 ]]; then
  echo "expected one Android wheel, found ${#wheels[@]}" >&2
  exit 1
fi

wheel_name="$(basename "${wheels[0]}")"
case "$wheel_name" in
  *android_*arm64_v8a.whl) ;;
  *)
    echo "unexpected Android wheel filename: $wheel_name" >&2
    exit 1
    ;;
esac

echo "Built $wheel_name"
