# Cloud Manager M2.1 Protocol Gaps

**M2.0R:** CLOSED

**M2.1:** CLOSED after GitHub-first implementation and verification; M2.2 has not started.

Cloud Manager remains an independent architecture:

`Single UI → Cloud Manager → Cloud Agent Client → WireGuard path → SiftAlpha Agent → Docker Runtime`

The Agent is the server-side source of runtime truth. WireGuard is an existing network path and is not started, stopped, configured, or credentialed by SiftAlpha. Cloud Manager does not depend on the old Runtime Provider.

## Current M1 contract record

The auditable endpoint and JSON record is [`docs/cloud-agent/m1-api-contract.md`](cloud-agent/m1-api-contract.md). It is derived from the available M1 implementation snapshot and the approved M1 specification. The server source itself is not part of the Android repository and its available workspace snapshot has no Git remote in this checkout; this provenance limitation is recorded in the contract and must be reconciled before DTO implementation.

## Intentionally absent from M1

These are not Cloud Manager M2.1 APIs and must not be invented:

- Cloud Import API;
- Cloud Config Write API;
- Cloud Secret Injection API;
- Cloud Web Result API;
- WebSocket/SSE log streaming;
- multi-server scheduling.

## M2.1 implementation result

The implementation is isolated in `:cloud-core`, `:cloud-agent-client`, and the `:app` Cloud Android integration. `CloudCredentialStore` remains platform-neutral in `:cloud-agent-client`; Android Keystore + AES/GCM is implemented only in `:app`. No existing UI or Runtime lifecycle is wired to these modules.

## Verification boundary

Formal Cloud module tests, Android regression tests, Android compile, APK/W0 build, architecture guard, and Android Keystore instrumentation are GitHub Actions evidence only. Local Gradle or Android SDK execution is not a formal PASS. A real Agent smoke remains an external read-only check for health, projects, and status only; it is separate from build/compile/test evidence.
