# kotlinx.serialization JSON provenance

This project uses the upstream JSON tree parser as a runtime dependency for
the platform-neutral Embedded CPython snapshot parser. No kotlinx.serialization
source code is copied into this repository.

- Artifact: `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0`
- Upstream: https://github.com/Kotlin/kotlinx.serialization
- Release: https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.9.0
- API used: `Json.parseToJsonElement` and the `JsonElement` tree accessors
- License: Apache License 2.0
- License source: https://github.com/Kotlin/kotlinx.serialization/blob/v1.9.0/LICENSE.txt
- Direct use: the Android and local-JVM source sets use the published runtime
  artifact; the parser does not use generated serializers or reflection.
- Transitive runtime: `org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0`,
  under the same Apache License 2.0.

The dependency version is paired with the repository's Kotlin 2.2.x toolchain.
Any future upgrade requires rechecking the upstream release, API compatibility,
transitive dependencies, and license notices.
