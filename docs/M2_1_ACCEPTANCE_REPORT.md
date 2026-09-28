# SiftAlpha Cloud Manager M2.1 Acceptance Report

**Report status:** CLOSED — stopped before M2.2  
**Date:** 2026-09-29  
**Repository:** `kuashan/siftalpha-one`  
**Branch:** `codex/cloud-manager-m2`  
**Implementation verification HEAD:** `9bbcca4` (`ci: extend Android emulator boot timeout`)

## Approval and source of truth

- `M2_1_PLAN=APPROVED_FOR_IMPLEMENTATION` after the actual W0 gate Run [`36414509699`](https://github.com/kuashan/siftalpha-one/actions/runs/36414509699) completed successfully.
- The user-provided Run `36414509799` was a GitHub 404/typo; it was not used as evidence.
- Frozen source baseline: `origin/codex/r48d11-external-runtime-baseline-python-resolver` at `b2650a957ca8ad7458ae8f911cd8fb5487d95a83`.
- GitHub is the Single Source of Truth. The final branch was pushed over SSH and verified against `origin/codex/cloud-manager-m2`.

## Implemented scope

### `:cloud-core`

Pure Kotlin/JVM Cloud domain and policy: server/project/status/operation/result models, error vocabulary, action policy, and operation repository contract. Domain timestamps use `java.time.Instant`.

### `:cloud-agent-client`

Pure Kotlin/JVM M1 DTOs, tolerant JSON decoding with strict required-field mapping, bounded `HttpURLConnection` transport, HTTP/error classification, `SiftAlphaCloudAgentClient`, and bounded/cancellable operation polling. It exposes only the platform-neutral `CloudCredentialStore` interface and does not contain Android Keystore code.

### `:app` Cloud Android integration

`AndroidCloudCredentialStore` and `CloudCredentialPayloadCodec` use an Android Keystore AES/GCM key and a dedicated Cloud credential preference namespace. The client module remains reusable by future Android/macOS/Windows integrations.

## Boundary verification

- No old Internal/External Runtime, Termux, PRoot, Embedded CPython, Internal Alpine, Runtime Center, PID/PGID, local Web Discovery, or Runtime lifecycle source was changed.
- No Normal/Developer main-flow, Home, Project Card, Workspace, Prepare/Run/Stop/Logs UI, Result Viewer, or Cloud UI wiring was added.
- No server, Agent, or WireGuard configuration/lifecycle change was made.
- No Cloud endpoint beyond the M1 contract was invented.
- Cloud modules contain no forbidden Runtime/Termux/PRoot references; the GitHub architecture guard passed.

## Formal GitHub Actions evidence

| Evidence | Run / job | Result |
|---|---|---|
| W0 approval gate | [`36414509699`](https://github.com/kuashan/siftalpha-one/actions/runs/36414509699) | SUCCESS |
| Latest W0 Cloud Build for `9bbcca4` | [`36448915725`](https://github.com/kuashan/siftalpha-one/actions/runs/36448915725) | SUCCESS |
| Android Keystore instrumentation | [`36448915775`](https://github.com/kuashan/siftalpha-one/actions/runs/36448915775), job `109018290849` | SUCCESS |

The latest W0 run covered Cloud module unit tests, architecture guard, existing Android unit tests, Kotlin compilation, and debug APK assembly. The Android run covered the real Android Keystore instrumentation test. The Android runner emitted emulator-console permission notices during its long run, but the GitHub job completed successfully and the instrumentation step was not classified as failed.

## Real Agent smoke

`M2_1_REMOTE_SMOKE=PASS` using the existing WireGuard path and configured Agent endpoint. Only read-only requests were sent:

- `GET /v1/health` → HTTP 200, `status=ok`.
- `GET /v1/projects` → HTTP 200, one project (`daily-stock-analysis`).
- `GET /v1/projects/daily-stock-analysis/status` → HTTP 200, runtime state `STOPPED`.

No Prepare, Start, Stop, configuration write, secret injection, or lifecycle operation was sent.

## Security and provenance

- No Bearer token, SSH key, WireGuard private key, OCI API private key, or other secret was printed, committed, stored in source, or included in this report.
- `SECRETS_EXPOSED=NO`.
- `LOCAL_GRADLE_BUILD_USED=NO`; local checks were limited to read-only Git/diff/source-scan validation.

## Final status

```text
M2_1_PLAN=APPROVED_FOR_IMPLEMENTATION
M2_1_CODE=CLOSED
M2_1_REMOTE_SMOKE=PASS
ANDROID_KEYSTORE_TEST=PASS
M2_2=NOT_STARTED
LOCAL_GRADLE_BUILD_USED=NO
SECRETS_EXPOSED=NO
FINAL=SIFTALPHA_CLOUD_MANAGER_M2_1=CLOSED
```
