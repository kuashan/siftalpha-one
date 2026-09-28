# SiftAlpha Cloud Manager M2.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有 Android 主流程和 Legacy Runtime 的前提下，建立可跨平台复用的 Cloud domain、M1 Agent client、操作轮询、平台无关凭据接口、Android Keystore integration 和完整测试基础。

**Architecture:** `:cloud-core` 是纯 Kotlin/JVM domain 与 policy 模块；`:cloud-agent-client` 是纯 Kotlin/JVM 的 M1 DTO、transport、client、error mapping、polling 和 `CloudCredentialStore` 接口；`:app` 只增加不接入主流程的 Android credential integration，实现 Keystore + AES/GCM。Cloud modules 不依赖旧 Runtime，Android integration 不反向接入首页或 Project Workspace。

**Tech Stack:** Kotlin 2.2.10；Gradle/AGP 现有版本；纯 Kotlin/JVM；`kotlinx-serialization-json` 1.9.0；Java `HttpURLConnection`；Android Keystore；AES/GCM；JUnit 4；fake transport；现有 Android unit/instrumentation test tooling。

**Spec:** `docs/superpowers/specs/2026-09-28-cloud-manager-m2-1-design.md`

## Global Constraints

- Source baseline remains `origin/codex/r48d11-external-runtime-baseline-python-resolver` at `b2650a957ca8ad7458ae8f911cd8fb5487d95a83`; if it changes, stop and output `SOURCE_BASELINE_CHANGED`.
- Work only on `codex/cloud-manager-m2`; do not modify or rewrite the r48d11 branch history.
- `:cloud-core` is pure Kotlin/JVM and must not depend on Android SDK, Activity, Compose, Termux, PRoot, Docker, or old Runtime code.
- `:cloud-agent-client` is platform-independent Kotlin/JVM and must depend only on `:cloud-core` plus standard/Kotlin serialization libraries; it must not contain Android Keystore code.
- `CloudCredentialStore` is a platform-agnostic interface in `:cloud-agent-client`; Android Keystore + AES/GCM exists only in `:app` Cloud Android integration.
- Cloud Manager must not call `ProjectRuntimeController`, `RuntimeBackend`, `TermuxBackend`, `RuntimeCommandHost`, `RuntimeWebPortDiscovery`, or `RuntimeAgentController`.
- Do not modify Internal Runtime, External Runtime, Termux, PRoot, Embedded CPython, Internal Alpine, Runtime Center, Developer Mode, Local Agent, Local Web Discovery, PID/PGID, ResultBus, or old Runtime lifecycle.
- Do not connect Cloud modules to Home, Project Card, Normal Workspace, Developer Workspace, Prepare UI, Run UI, Stop UI, Logs UI, Result Viewer, or existing lifecycle.
- Adapt only the existing M1 API; do not invent Cloud Import, Cloud Config Write, Cloud Secret Injection, Web Result, WebSocket, SSE, database, multi-server scheduling, or M2.2 behavior.
- Cloud endpoint is configuration data; no production hard-coded `10.77.0.1`, no public fallback, no WireGuard lifecycle/config/key management.
- Bearer Token, WireGuard private key, SSH key, and OCI API private key must not enter source, Git, BuildConfig, fixtures, logs, or reports.
- Existing Kotlin, AGP, Compose, dependencies, versionName, and product flow remain unchanged except the minimum module wiring required for compilation.
- Real Agent smoke, if possible, is read-only `health`, `projects`, and `project status`; never invoke Prepare/Start/Stop from the new Android code.
- Stop after M2.1; do not begin M2.2.

## Review Focus

- Baseline drift: every implementation phase must re-check the r48d11 ref before editing; the test is the explicit `fetch --prune` plus SHA comparison in Task 0.
- Platform leakage: `:cloud-agent-client` must compile without Android classes and without old Runtime references; the module dependency/source scan is pinned in Task 1 and Task 7.
- Protocol ambiguity: unknown or malformed M1 JSON must become `INVALID_RESPONSE`, not a guessed project state; DTO decode/mapping tests belong to Task 3.
- Network truth: connection failure and timeout must remain `CLOUD_SERVER_UNREACHABLE` / `TIMEOUT`, never `STOPPED` or `FAILED`; transport/error tests belong to Task 3.
- Secret persistence: stored values must be encrypted under a separate Cloud namespace and never logged or reused as project secrets; Android integration tests belong to Task 5.

---

### Task 0: Re-verify the frozen baseline and branch

**Files:**
- None.

**Interfaces:**
- Consumes: `origin/codex/r48d11-external-runtime-baseline-python-resolver`.
- Produces: a clean `codex/cloud-manager-m2` branch at the expected source SHA.

- [ ] **Step 1: Fetch and verify the source ref**

Run from the audit clone:

```bash
git fetch origin --prune
test "$(git rev-parse origin/codex/r48d11-external-runtime-baseline-python-resolver)" = "b2650a957ca8ad7458ae8f911cd8fb5487d95a83"
```

Expected: exit 0. If the comparison fails, stop and report `SOURCE_BASELINE_CHANGED`; do not rebase or merge.

- [ ] **Step 2: Verify branch and worktree state**

Run:

```bash
git branch --show-current
git rev-parse HEAD
git status --short
```

Expected: `codex/cloud-manager-m2`, HEAD initially equals the expected SHA, and no unrelated changes are present.

### Task 1: Add isolated Gradle modules and dependency boundaries

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `cloud-core/build.gradle.kts`
- Create: `cloud-agent-client/build.gradle.kts`
- Create: `cloud-core/src/main/kotlin/com/siftalpha/cloud/core/ModuleBoundary.kt` only if a minimal compile anchor is required by the selected Gradle setup
- Test: module compilation tasks, no Android main-flow test changes

**Interfaces:**
- Consumes: existing Kotlin/AGP plugin versions and existing `kotlinx-serialization-json:1.9.0` version.
- Produces: `:cloud-core`, `:cloud-agent-client`, and `:app` → `:cloud-agent-client` dependency wiring. `:cloud-agent-client` → `:cloud-core` is the only Cloud module dependency.

- [ ] **Step 1: Add the module includes and plugin aliases**

Add `include(":cloud-core", ":cloud-agent-client")` without removing `:app`. Add the existing Kotlin version as the JVM and serialization plugin version; do not upgrade Kotlin, AGP, Compose, or existing libraries.

- [ ] **Step 2: Add `:cloud-core` as a pure Kotlin/JVM module**

Configure `org.jetbrains.kotlin.jvm`, JUnit 4 test support, and no Android or Compose plugins/dependencies. Keep source and test packages under `com.siftalpha.cloud.core`.

- [ ] **Step 3: Add `:cloud-agent-client` as a pure Kotlin/JVM module**

Configure Kotlin JVM plus Kotlin serialization, depend on `project(":cloud-core")` and `kotlinx-serialization-json:1.9.0`, and add JUnit 4. Do not add Android, Activity, Compose, Termux, PRoot, or old Runtime dependencies.

- [ ] **Step 4: Add the app dependency without changing app entry points**

Add only `implementation(project(":cloud-agent-client"))` to `app/build.gradle.kts`. Do not import Cloud classes into `MainActivity`, `HomeScreen`, `NormalProjectWorkspaceActivity`, `ProjectWorkspaceActivity`, or any existing Runtime class.

- [ ] **Step 5: Verify the initial module boundary**

Run:

```bash
gradle --no-daemon --console=plain :cloud-core:test :cloud-agent-client:test :app:compileDebugKotlin
```

Expected: all requested tasks compile; source scans find no forbidden package imports in the new modules.

- [ ] **Step 6: Commit the module foundation**

```bash
git add settings.gradle.kts build.gradle.kts app/build.gradle.kts cloud-core cloud-agent-client
git commit -m "build: add cloud manager foundation modules"
```

### Task 2: Implement Cloud domain, errors, action policy, and repository contracts

**Files:**
- Create: `cloud-core/src/main/kotlin/com/siftalpha/cloud/core/CloudServer.kt`
- Create: `cloud-core/src/main/kotlin/com/siftalpha/cloud/core/CloudProject.kt`
- Create: `cloud-core/src/main/kotlin/com/siftalpha/cloud/core/CloudProjectStatus.kt`
- Create: `cloud-core/src/main/kotlin/com/siftalpha/cloud/core/CloudOperation.kt`
- Create: `cloud-core/src/main/kotlin/com/siftalpha/cloud/core/CloudResult.kt`
- Create: `cloud-core/src/main/kotlin/com/siftalpha/cloud/core/CloudState.kt`
- Create: `cloud-core/src/main/kotlin/com/siftalpha/cloud/core/CloudError.kt`
- Create: `cloud-core/src/main/kotlin/com/siftalpha/cloud/core/CloudProjectActionPolicy.kt`
- Create: `cloud-core/src/main/kotlin/com/siftalpha/cloud/core/OperationRepository.kt`
- Test: `cloud-core/src/test/kotlin/com/siftalpha/cloud/core/CloudDomainTest.kt`
- Test: `cloud-core/src/test/kotlin/com/siftalpha/cloud/core/CloudProjectActionPolicyTest.kt`

**Interfaces:**
- Consumes: module boundary from Task 1.
- Produces:
  - `data class CloudServer(serverId: String, displayName: String, baseUrl: String, connectionState: CloudConnectionState)`;
  - `data class CloudProject(localProjectId: String?, displayName: String, description: String?, serverId: String, remoteProjectId: String, source: String?, runtimeKind: String?, environmentState: CloudEnvironmentState, runtimeState: CloudRuntimeState, lastOperationId: String?, result: CloudResult?, createdAt: Long?, updatedAt: Long?)`;
  - `data class CloudProjectStatus(remoteProjectId: String, environmentState: CloudEnvironmentState, runtimeState: CloudRuntimeState, containerId: String?, image: String?, exitCode: Int?, oomKilled: Boolean?, restartCount: Int?, startedAt: Long?, finishedAt: Long?)`;
  - `data class CloudOperation(operationId: String, projectId: String, action: CloudOperationAction, state: CloudOperationStatus, startedAt: Long?, finishedAt: Long?, exitCode: Int?, failureReason: String?)`;
  - `data class CloudResult(remoteProjectId: String, operationId: String?, runtimeState: CloudRuntimeState, environmentState: CloudEnvironmentState, webEndpoint: String?, artifacts: List<CloudArtifact>, summary: String?, errorCode: CloudErrorCode?, errorMessage: String?, updatedAt: Long?)`;
  - `data class CloudArtifact(name: String, type: String, url: String?, size: Long?, metadata: Map<String, String>)`;
  - `enum class CloudErrorCode { CLOUD_SERVER_UNREACHABLE, UNAUTHORIZED, PROJECT_NOT_FOUND, OPERATION_CONFLICT, ENVIRONMENT_NOT_READY, ALREADY_RUNNING, ALREADY_STOPPED, DOCKER_ERROR, PREPARE_FAILED, RUNTIME_ERROR, INVALID_RESPONSE, TIMEOUT, UNKNOWN }`;
  - `data class CloudError(code: CloudErrorCode, message: String, httpStatus: Int? = null)`;
  - `data class CloudActionFacts(environmentState: CloudEnvironmentState, runtimeState: CloudRuntimeState, operation: CloudOperation?)`;
  - `enum class CloudProjectAction { PREPARE, START, STOP, RETRY, REFRESH, NONE }`;
  - `data class CloudActionDecision(action: CloudProjectAction, enabled: Boolean, reason: String?)`;
  - `object CloudProjectActionPolicy { fun decide(facts: CloudActionFacts): CloudActionDecision }`;
  - `interface OperationRepository { fun save(operation: CloudOperation); fun find(operationId: String): CloudOperation? }`.

- [ ] **Step 1: Write failing domain and policy tests**

Pin enum values, data-field defaults, and the policy matrix: `NOT_READY → PREPARE`, `READY + STOPPED → START`, `RUNNING → STOP`, pending/running operation disables destructive actions, and failed operation exposes retry/refresh according to Cloud facts.

- [ ] **Step 2: Run the focused core tests and verify failure**

Run:

```bash
gradle --no-daemon --console=plain :cloud-core:test --tests 'com.siftalpha.cloud.core.CloudDomainTest' --tests 'com.siftalpha.cloud.core.CloudProjectActionPolicyTest'
```

Expected: FAIL because the domain types and policy are not yet implemented.

- [ ] **Step 3: Implement the domain types and pure policy**

Use epoch-millisecond nullable timestamps to keep the JVM library independent of Android time classes. Keep `CloudErrorCode` in core so client transport failures and domain result errors share one stable vocabulary. The policy must read only `CloudActionFacts`.

- [ ] **Step 4: Run the focused core tests**

Expected: PASS with zero failures and no forbidden dependency imports.

- [ ] **Step 5: Commit the Cloud domain foundation**

```bash
git add cloud-core
git commit -m "feat: add cloud domain foundation"
```

### Task 3: Implement M1 DTOs, transport, response validation, and Agent client

**Files:**
- Create: `cloud-agent-client/src/main/kotlin/com/siftalpha/cloud/agent/api/M1Dtos.kt`
- Create: `cloud-agent-client/src/main/kotlin/com/siftalpha/cloud/agent/api/M1Json.kt`
- Create: `cloud-agent-client/src/main/kotlin/com/siftalpha/cloud/agent/api/CloudApiModels.kt`
- Create: `cloud-agent-client/src/main/kotlin/com/siftalpha/cloud/agent/transport/CloudHttpTransport.kt`
- Create: `cloud-agent-client/src/main/kotlin/com/siftalpha/cloud/agent/transport/UrlConnectionCloudHttpTransport.kt`
- Create: `cloud-agent-client/src/main/kotlin/com/siftalpha/cloud/agent/CloudCredentialStore.kt`
- Create: `cloud-agent-client/src/main/kotlin/com/siftalpha/cloud/agent/CloudAgentClient.kt`
- Create: `cloud-agent-client/src/main/kotlin/com/siftalpha/cloud/agent/CloudAgentException.kt`
- Create: `cloud-agent-client/src/main/kotlin/com/siftalpha/cloud/agent/CloudDtoMapper.kt`
- Test: `cloud-agent-client/src/test/kotlin/com/siftalpha/cloud/agent/M1JsonTest.kt`
- Test: `cloud-agent-client/src/test/kotlin/com/siftalpha/cloud/agent/CloudAgentClientTest.kt`
- Test: `cloud-agent-client/src/test/kotlin/com/siftalpha/cloud/agent/CloudErrorMappingTest.kt`

**Interfaces:**
- Consumes: `CloudProject`, `CloudOperation`, `CloudErrorCode`, and `CloudResult` from Task 2.
- Produces:
  - `data class CloudCredential(serverId: String, baseUrl: String, bearerToken: String)`;
  - `interface CloudCredentialStore { fun get(serverId: String): CloudCredential?; fun save(credential: CloudCredential); fun delete(serverId: String) }`;
  - `data class CloudHealth(status: String, agent: String?, docker: String?, wireguardBinding: String?, version: String?)`;
  - `data class CloudLogs(remoteProjectId: String, lines: List<String>, bytes: Long, truncated: Boolean)`;
  - `data class CloudHttpRequest(method: String, url: String, headers: Map<String, String>, body: String? = null)`;
  - `data class CloudHttpResponse(statusCode: Int, headers: Map<String, String>, body: String, elapsedMillis: Long)`;
  - `interface CloudHttpTransport { fun execute(request: CloudHttpRequest, timeoutMillis: Long, maxResponseBytes: Int): CloudHttpResponse }`;
  - `class SiftAlphaCloudAgentClient(serverId: String, credentialStore: CloudCredentialStore, transport: CloudHttpTransport, timeoutMillis: Long = 10_000, maxResponseBytes: Int = 1_048_576)`;
  - methods `fun health(): CloudHealth`, `fun listProjects(): List<CloudProject>`, `fun getProject(projectId: String): CloudProject`, `fun getStatus(projectId: String): CloudProjectStatus`, `fun prepare(projectId: String): CloudOperation`, `fun start(projectId: String): CloudOperation`, `fun stop(projectId: String): CloudOperation`, `fun logs(projectId: String, tail: Int): CloudLogs`, and `fun getOperation(operationId: String): CloudOperation`;
  - `class CloudAgentException(val error: CloudError, cause: Throwable? = null)`.

- [ ] **Step 1: Write failing JSON and client mapping tests**

Use the M1 field names from the server contract: health status/agent/docker/wireguardBinding/version, projectId/environment/runtime, status container fields, logs `lines`/`bytes`/`truncated`, operation fields, and the `{ "error": { "code", "message" } }` envelope. Assert DTOs never become domain objects without mapping.

- [ ] **Step 2: Run focused client tests and verify failure**

Run the three focused test classes. Expected: FAIL because DTOs, mapping, and client types are not yet implemented.

- [ ] **Step 3: Implement serializable M1 DTOs and strict mapping**

Keep server response fields optional only where M1 marks them optional; reject missing identity/state fields and malformed enums with `INVALID_RESPONSE`. Preserve unknown JSON fields through configured tolerant decoding, but do not invent endpoints or silently infer project state.

- [ ] **Step 4: Implement HTTP transport with bounded response handling**

Use `HttpURLConnection`, connect/read timeouts, bounded byte reads, URL joining that preserves the configured base URL, and no fallback URL. Do not log Authorization values. Convert connection exceptions to typed transport failures for client mapping.

- [ ] **Step 5: Implement `SiftAlphaCloudAgentClient`**

Send `Authorization: Bearer <token>` from the credential returned by `CloudCredentialStore`; map 200/202 success payloads, 401/404/409/5xx error envelopes, connection failure, timeout, malformed JSON, and invalid tail values (`0 <= tail <= 1000`). Mutation methods return operation references; they do not poll internally.

- [ ] **Step 6: Run client tests and verify all mappings**

Expected: PASS for JSON decode, DTO/domain mapping, 401, 404, 409, 500, connection failure, timeout, invalid JSON, bounded logs, and Authorization redaction.

- [ ] **Step 7: Commit the protocol foundation**

```bash
git add cloud-agent-client
git commit -m "feat: add cloud agent client protocol foundation"
```

### Task 4: Add operation polling tests and implementation

**Files:**
- Create: `cloud-agent-client/src/main/kotlin/com/siftalpha/cloud/agent/CloudOperationPoller.kt`
- Test: `cloud-agent-client/src/test/kotlin/com/siftalpha/cloud/agent/CloudOperationPollerTest.kt`
- Test: `cloud-agent-client/src/test/kotlin/com/siftalpha/cloud/agent/CloudCredentialStoreContractTest.kt`

**Interfaces:**
- Consumes: `CloudCredential`, `CloudCredentialStore`, `CloudOperation`, and `SiftAlphaCloudAgentClient`/operation lookup from Task 3.
- Produces: operation polling implementation; the platform-neutral `CloudCredentialStore` contract remains the Task 3 output consumed by the Android integration.
  - `data class CloudPollingConfig(intervalMillis: Long, maxDurationMillis: Long)`;
  - `interface CloudClock { fun nowMillis(): Long }`;
  - `interface CloudDelay { fun sleep(millis: Long) }`;
  - `class CloudOperationPoller(operationLookup: (String) -> CloudOperation, clock: CloudClock, delay: CloudDelay)`;
  - `fun poll(operationId: String, config: CloudPollingConfig, isCancelled: () -> Boolean = { false }): CloudOperation`.

- [ ] **Step 1: Write failing credential-contract and poller tests**

Assert an in-memory credential store can save/read/delete by serverId without any Android type. Assert poller stops on `SUCCEEDED`, `FAILED`, and `CANCELLED`, throws typed timeout after max duration, exits on cancellation, and never loops without a deadline.

- [ ] **Step 2: Run focused tests and verify failure**

Expected: FAIL because the interface and poller are not implemented.

- [ ] **Step 3: Implement bounded, cancellable polling**

Poll through the injected lookup function, inspect only terminal operation statuses, check cancellation and the max duration before every delay, and throw `CloudAgentException(TIMEOUT)` on deadline expiry. Do not persist only in memory; keep `OperationRepository` available for a later Android repository implementation.

- [ ] **Step 4: Run focused and full client tests**

Expected: PASS with deterministic fake clock/delay and no real server dependency.

- [ ] **Step 5: Commit the poller**

```bash
git add cloud-agent-client
git commit -m "feat: add cloud operation polling"
```

### Task 5: Implement Android Cloud credential integration without main-flow wiring

**Files:**
- Create: `app/src/main/java/com/siftalpha/studio/cloud/AndroidCloudCredentialStore.kt`
- Create: `app/src/main/java/com/siftalpha/studio/cloud/CloudCredentialPayloadCodec.kt`
- Create: `app/src/test/java/com/siftalpha/studio/cloud/CloudCredentialEncodingTest.kt`
- Create: `app/src/androidTest/java/com/siftalpha/studio/cloud/AndroidCloudCredentialStoreTest.kt` if instrumentation is available in the existing project setup
- Modify: `app/build.gradle.kts` only for the project dependency already specified in Task 1 and any minimum test dependency required by the existing Android test setup

**Interfaces:**
- Consumes: `CloudCredential`, `CloudCredentialStore` from Task 3.
- Produces: `class AndroidCloudCredentialStore(context: Context) : CloudCredentialStore`.

- [ ] **Step 1: Write failing encoding and integration tests**

Pin the pure payload codec round-trip in the JVM test, plus the separate Cloud preference namespace, ciphertext-only value storage, delete behavior, wrong-key/corrupt-ciphertext behavior, and absence of plaintext Token in persisted values in instrumentation. The instrumentation test must use a real Android Keystore provider when an emulator/device is available.

- [ ] **Step 2: Run the JVM-focused credential test and verify failure**

Expected: FAIL until the encoding/storage implementation is added; no test may print a token.

- [ ] **Step 3: Implement the payload codec and `AndroidCloudCredentialStore`**

Implement `CloudCredentialPayloadCodec` as a small platform-neutral payload encoder/decoder used only by this integration. Use a Cloud-specific SharedPreferences file and key namespace. Generate/load an AES key in Android Keystore, encrypt the encoded credential payload with AES/GCM and a fresh IV, store only ciphertext plus non-sensitive server metadata, and return `null` for unreadable/corrupt entries without exposing plaintext. Do not call `ProjectSecretStore` and do not alter it.

- [ ] **Step 4: Run the JVM credential test and compile the Android integration**

Expected: PASS for encoding/security invariants and `:app:compileDebugKotlin` succeeds.

- [ ] **Step 5: Run the instrumentation credential test if an Android target is available**

Run:

```bash
gradle --no-daemon --console=plain :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.siftalpha.studio.cloud.AndroidCloudCredentialStoreTest
```

Expected: PASS against a real Android Keystore. If no target is available, record the remote/instrumentation verification as blocked; do not call it PASS or substitute a fake test for real Keystore evidence.

- [ ] **Step 6: Commit the Android integration**

```bash
git add app/build.gradle.kts app/src/main/java/com/siftalpha/studio/cloud app/src/test/java/com/siftalpha/studio/cloud app/src/androidTest/java/com/siftalpha/studio/cloud
git commit -m "feat: add android cloud credential integration"
```

### Task 6: Add protocol-gap and architecture documentation

**Files:**
- Create: `docs/M2_PROTOCOL_GAPS.md`
- Modify: `docs/PROJECT_CONTEXT.md`
- Modify: `docs/README.md` only if needed to link the new protocol-gap document

**Interfaces:**
- Consumes: completed modules and the M1 API contract from the approved spec.
- Produces: documentation recording M2.0R closed, M2.1 current phase, Cloud Manager independence, Agent-as-source-of-truth, WireGuard ownership boundary, Legacy Runtime freeze, and intentionally absent M1 capabilities.

- [ ] **Step 1: Document protocol gaps without inventing APIs**

Record that M1 does not provide Cloud Import, Cloud Config Write, Cloud Secret Injection, Cloud Web Result, WebSocket/SSE streaming, or multi-server scheduling; mark them as future scope, not implemented behavior.

- [ ] **Step 2: Update project context**

Add a concise M2.1 entry without rewriting historical Runtime architecture or changing the current Android product baseline.

- [ ] **Step 3: Verify documentation and forbidden-scope scans**

Run `git diff --check` and scan new/changed code for imports or calls to `ProjectRuntimeController`, `RuntimeBackend`, `TermuxBackend`, `RuntimeCommandHost`, `RuntimeWebPortDiscovery`, `RuntimeAgentController`, and for secret-like literals. Expected: no forbidden Cloud-to-Legacy edges and no real token.

- [ ] **Step 4: Commit documentation**

```bash
git add docs/M2_PROTOCOL_GAPS.md docs/PROJECT_CONTEXT.md docs/README.md
git commit -m "docs: record cloud manager m2.1 boundaries"
```

### Task 7: Full verification, read-only smoke test, and acceptance report

**Files:**
- Create: `docs/M2_1_ACCEPTANCE_REPORT.md`
- No production code changes in this task.

**Interfaces:**
- Consumes: all module APIs and tests from Tasks 1–6.
- Produces: evidence-backed M2.1 report and a clean new remote branch; pushing this new branch is within the approved M2.1 scope and must not modify r48d11 history.

- [ ] **Step 1: Re-fetch and re-check baseline before final verification**

Run `git fetch origin --prune` and compare the target ref to `b2650a957ca8ad7458ae8f911cd8fb5487d95a83`. If changed, stop and report `SOURCE_BASELINE_CHANGED`; do not rebase or merge.

- [ ] **Step 2: Run Cloud module tests**

```bash
gradle --no-daemon --stacktrace --console=plain :cloud-core:test :cloud-agent-client:test
```

Expected: exit 0 and zero failures.

- [ ] **Step 3: Run existing Android unit tests**

```bash
gradle --no-daemon --stacktrace --console=plain :app:testDebugUnitTest
```

Expected: exit 0 and no regression in existing tests.

- [ ] **Step 4: Run Android compile/build verification**

```bash
gradle --no-daemon --stacktrace --console=plain :app:compileDebugKotlin :app:assembleDebug
```

Expected: exit 0. This is compile/build verification only; do not distribute the APK for user testing.

- [ ] **Step 5: Run the read-only real Agent smoke test when WireGuard is available**

Use the existing local token file without printing, logging, or embedding its contents. Send only `GET /v1/health`, `GET /v1/projects`, and `GET /v1/projects/{id}/status` through the configured WireGuard address. Never send Prepare, Start, or Stop. If WireGuard is unavailable, output `REAL_AGENT_SMOKE=BLOCKED` and do not fabricate PASS.

- [ ] **Step 6: Verify legacy isolation and secret hygiene**

Run source scans, `git status --short`, and `git diff origin/codex/r48d11-external-runtime-baseline-python-resolver...HEAD --` review. Expected: only Cloud modules, app credential integration, Gradle wiring, approved docs, and tests changed; no Runtime/Termux/PRoot/main-flow files changed; no token/key literals or logged Authorization header.

- [ ] **Step 7: Write the complete acceptance report**

Populate `docs/M2_1_ACCEPTANCE_REPORT.md` with the required fields: baseline, work branch/head, modules, domain, legacy isolation, Agent client methods, error mapping, poller, credential security, tests, Android compile, existing regression, real Agent smoke, CI status, and final `M2_1_CODE` / `M2_1_REMOTE_SMOKE`.

Only mark `M2_1_CODE=CLOSED` after all code, tests, compile, isolation, and secret checks are evidenced. Mark the remote smoke separately as `PASS` or `BLOCKED`; never merge the two statuses.

- [ ] **Step 8: Commit the acceptance report**

```bash
git add docs/M2_1_ACCEPTANCE_REPORT.md
git commit -m "docs: record cloud manager m2.1 acceptance"
```

- [ ] **Step 9: Push only the new Cloud Manager branch and verify CI**

```bash
git push --set-upstream origin codex/cloud-manager-m2
```

After the push, inspect the new branch's workflow result. If the repository's GitHub Actions credentials are available, wait for the relevant Cloud build to finish and record its exact run and result. Do not push, merge, or alter any existing baseline branch.

- [ ] **Step 10: Final branch cleanliness check and stop**

Run:

```bash
git status --short
git log --oneline --decorate -8
```

Expected: no uncommitted changes and no work started on M2.2. Stop after the report.
