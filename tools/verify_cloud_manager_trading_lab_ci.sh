#!/usr/bin/env bash

set -euo pipefail

workflow=.github/workflows/cloud-manager-m2-2.yml
test -s "$workflow"

for marker in \
  'architecture-guard:' \
  'cloud-modules:' \
  'android-foundation:' \
  'android-keystore-instrumentation:' \
  'name: Verify Trading Lab domain' \
  'name: Verify ownership boundary' \
  'name: Verify Web endpoint validation' \
  'TRADING_LAB_DOMAIN=PASS' \
  'OWNERSHIP_BOUNDARY=PASS' \
  'WEB_ENDPOINT_VALIDATION=PASS' \
  "--tests 'com.siftalpha.studio.cloud.CloudTradingLab*'" \
  "--tests 'com.siftalpha.studio.cloud.CloudRemoteWebPolicyTest'"; do
  if ! grep -Fq -- "$marker" "$workflow"; then
    echo "Trading Lab CI guard failed: missing marker $marker" >&2
    exit 1
  fi
done

grep -Fq 'android:name=".cloud.CloudTradingLabActivity"' app/src/main/AndroidManifest.xml
grep -Fq 'android:exported="false"' app/src/main/AndroidManifest.xml
test -s app/src/main/java/com/siftalpha/studio/cloud/CloudTradingLabController.kt
test -s app/src/main/java/com/siftalpha/studio/cloud/CloudTradingLabScreen.kt
test -s app/src/main/java/com/siftalpha/studio/cloud/CloudTradingLabPolicy.kt
test -s app/src/test/java/com/siftalpha/studio/cloud/CloudTradingLabPolicyTest.kt
test -s app/src/test/java/com/siftalpha/studio/cloud/CloudTradingLabControllerTest.kt
test -s app/src/test/java/com/siftalpha/studio/cloud/CloudRemoteWebPolicyTest.kt

echo "TRADING_LAB_CI_GUARD=PASS"
