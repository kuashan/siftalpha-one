# Embedded CPython provenance

This project does not commit a CPython binary. The Android build downloads and
verifies the fixed official CPython Android release below, then extracts only
the generated build inputs and app-private runtime assets.

- CPython version: `3.14.7`
- Upstream release: https://www.python.org/downloads/release/python-3147/
- Android artifact: `python-3.14.7-aarch64-linux-android.tar.gz`
- Artifact URL: https://www.python.org/ftp/python/3.14.7/python-3.14.7-aarch64-linux-android.tar.gz
- Artifact SHA-256: `6d50cc3aa66e414a439594089bcdfb5f1264358155c70c1f00471c24cfb477fb`
- Embedded `libpython3.14.so` SHA-256: `ec0b46cd65f228fb2a750303227f83e9e8eed83bbb95dd1b746bff61199907b`
- Target ABI: `arm64-v8a` (`aarch64-linux-android`)
- CPython license: Python Software Foundation License 2.0
- License source: https://github.com/python/cpython/blob/v3.14.7/LICENSE
- Build method: download the fixed official Android release at build time, verify SHA-256, extract the official `prefix`, link the official `libpython3.14.so`, and package the standard library as generated app assets.
- Binary provenance: official Python.org CPython Android release; no binary is stored in this repository.
- Upstream notices: the official `prefix/lib/python3.14/LICENSE.txt` is copied into the generated APK assets together with this record. Bundled third-party components retain their upstream notices.

This is an engineering provenance record, not legal advice. Any future change
to the CPython version, artifact, ABI, or bundled native component requires a
new review of its upstream licenses and checksums.
