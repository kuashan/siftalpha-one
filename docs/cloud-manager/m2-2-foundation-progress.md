# Cloud Manager M2.2 Foundation Progress

This is the current branch status. It intentionally does not claim full CI or
any Oracle/Agent acceptance while the remote provenance gate is blocked.

```text
M2_2_FOUNDATION=IN_PROGRESS
AGENT_PROVENANCE=BLOCKED_NETWORK_ACCESS
SERVER_MUTATION=NO
TRADING_LAB_IMPLEMENTATION=NOT_STARTED
SECRETS_EXPOSED=NO

CLOUD_CORE_TESTS=PENDING_CI
CLOUD_AGENT_CLIENT_TESTS=PENDING_CI
ANDROID_UNIT_TESTS=PENDING_CI
ANDROID_COMPILE=PENDING_CI
ANDROID_APK_BUILD=PENDING_CI
ARCHITECTURE_GUARD=PENDING_CI
ANDROID_KEYSTORE_INSTRUMENTATION=PENDING_CI
```

The Foundation CI is split into independent responsibilities:

- Cloud JVM module tests for `:cloud-core` and `:cloud-agent-client`.
- Android JVM tests, including the existing Normal Mode and Developer/Runtime
  regression suite, Android compilation, and `assembleDebug`.
- An architecture guard that rejects Android, Legacy Runtime, Termux, PRoot,
  and Internal Alpine dependencies from the Cloud JVM modules.
- A separate emulator job for the Android Keystore instrumentation contract.

The Agent gate remains read-only and blocked until the original WireGuard path
to `10.77.0.1:9080` is restored. No server, Docker, Agent, WireGuard, or OCI
resource has been changed by this phase.
