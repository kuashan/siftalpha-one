#!/usr/bin/env bash

set -euo pipefail

cloud_modules=(cloud-core cloud-agent-client)
for module in "${cloud_modules[@]}"; do
  test -d "$module"
  test -f "$module/build.gradle.kts"
  test -d "$module/src/main"
done

if rg -n -i \
  'com\.android|org\.jetbrains\.kotlin\.android|androidx|project\("(:app|:core)"\)|com\.siftalpha\.studio\.(runtime|siftalphax)|(^|[^a-z])termux([^a-z]|$)|(^|[^a-z])proot([^a-z]|$)|internal[-_ ]alpine|legacy[[:space:]_-]+runtime' \
  cloud-core cloud-agent-client; then
  echo "Cloud architecture guard failed: forbidden Android/Legacy Runtime dependency found" >&2
  exit 1
fi

grep -Fq 'id("org.jetbrains.kotlin.jvm")' cloud-core/build.gradle.kts
grep -Fq 'id("org.jetbrains.kotlin.jvm")' cloud-agent-client/build.gradle.kts
! grep -Fq 'id("com.android' cloud-core/build.gradle.kts
! grep -Fq 'id("com.android' cloud-agent-client/build.gradle.kts

if rg -n 'project\("' cloud-agent-client/build.gradle.kts | grep -vF 'project(":cloud-core")'; then
  echo "Cloud architecture guard failed: Agent client has a non-core project dependency" >&2
  exit 1
fi

if rg -n '^import[[:space:]]+android\.|^import[[:space:]]+androidx\.' cloud-core cloud-agent-client; then
  echo "Cloud architecture guard failed: Cloud JVM modules import Android APIs" >&2
  exit 1
fi

test -d app/src/test/java/com/siftalpha/studio/presentation
test -d app/src/test/java/com/siftalpha/studio/runtime
test -s app/src/test/java/com/siftalpha/studio/presentation/NormalProjectPrimaryActionPolicyTest.kt
test -s app/src/test/java/com/siftalpha/studio/runtime/RuntimeLifecycleStoreTest.kt

echo "Cloud architecture guard PASS"
echo "Cloud JVM modules are isolated from Android, Legacy Runtime, Termux, PRoot, and Internal Alpine dependencies"
echo "Existing Normal Mode and Developer/Runtime regression test sources are present"
