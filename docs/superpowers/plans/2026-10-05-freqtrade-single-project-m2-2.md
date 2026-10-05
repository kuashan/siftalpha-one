# M2.2 Freqtrade Single Project Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Each task follows `superpowers:test-driven-development` and ends with an independently verified commit.

**Goal:** Add one version-pinned, dry-run Freqtrade CloudProject that can be discovered and operated through Prepare, Start, Status, Logs, Resources, private FreqUI open, and Stop without changing existing Cloud Manager or Runtime behavior.

**Architecture:** Extend the existing Python Agent registry/helper/API with optional project metadata, one ownership-checked `resources` action, and one versioned Freqtrade artifact bundle. Extend the platform-neutral Kotlin Cloud domain/client, then add a thin Android Trading Lab surface that consumes real Agent state and the existing operation poller/Web viewer. Deploy only after both repositories pass their CI gates.

**Tech Stack:** Python 3.12, FastAPI, Docker Compose, Docker CLI, official `freqtrade/freqtrade` release `2026.9`, Kotlin/JVM 17, Kotlin serialization, Android Compose/View activities, Gradle, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-05-freqtrade-single-project-m2-2-design.md`

## Global Constraints

- Android branch: `feature/cloud-manager-trading-lab-m2.2`.
- Agent branch: `feature/trading-lab-freqtrade-m2.2`, based on `baseline/runtime-helper-m0`.
- Freqtrade source: repository `freqtrade/freqtrade`, release `2026.9`, commit `1f394eaebc2f46a83d26971388628707802f8602`.
- Only one project is implemented: `projectId=freqtrade`, `mainService=freqtrade`, `group=trading-lab`, `runtimeKind=docker_compose`, `architecture=arm64`.
- Freqtrade is dry-run only: no real orders, real money, exchange private keys, or withdrawal permission.
- Freqtrade resource limits are CPU `1.0`, memory `2GiB`, and PIDs `256`; unlimited resources are forbidden.
- Freqtrade source, registry entry, Compose file, dry-run config, minimal strategy/config, Web metadata, and limits must be committed artifacts; Oracle must not receive hand-edited server-only configuration.
- `daily-stock-analysis` must parse and operate unchanged without requiring the new optional metadata.
- The helper gains only `resources`; generic shell, exec, Docker admin, arbitrary paths, arbitrary containers, and arbitrary commands remain forbidden.
- When no container exists, Status returns an initial state, Logs returns a bounded empty result, and Resources returns no running instance. Ownership validation is required once a container exists; an existing container with mismatched ownership labels or Compose identity is rejected.
- M1 API routes remain backward compatible. The only new runtime route is `GET /v1/projects/{id}/resources` plus optional Web/RuntimeUnit fields in existing responses.
- No WireGuard, SSH, OCI firewall, Android credential format, legacy Runtime, Termux, PRoot, or unrelated Docker workload changes.
- No Freqtrade credential synchronization, public fallback, WebSocket/SSE logs, strategy research, optimization, or second trading bot.

## Review Focus

- Missing container versus present-but-unowned container: the former is a valid initial/empty observation; the latter is a hard rejection for all runtime operations.
- Compose identity spoofing with a correct-looking container name: Docker Compose project/service labels must match the Registry-derived identity.
- Artifact drift between Git and Oracle: deployment must verify committed manifest hashes and the exact upstream source commit before restart.
- Malformed or public Web metadata: only Agent-returned private `http`/`https` endpoints may reach the Web viewer; `file:`, `javascript:`, `intent:`, arbitrary hosts, and public fallback must fail closed.
- Operation/network ambiguity: Prepare/Start/Stop UI state comes from operation polling and a follow-up Status request; unreachable or timeout errors must never become `STOPPED`.

---

### Task 0: Isolated workspaces and baseline verification

**Files:**
- Create: Agent linked worktree at `/Users/dlqs/Documents/Codex/2026-09-28/agent-m2-2-freqtrade` on `feature/trading-lab-freqtrade-m2.2`.
- Use: Android worktree `/Users/dlqs/Documents/Codex/2026-09-28/cloud-manager-trading-lab-m2-2` on `feature/cloud-manager-trading-lab-m2.2`.
- Create: Agent plan ledger under the plan-owned `.superpowers/sdd/` workspace when execution starts.

**Interfaces:**
- Consumes: Agent `baseline/runtime-helper-m0` at `0220516cc605e669bde9dc54631935f9239dd265`; Android approved Design Spec commit `38a4befcff396cf97f40db071c23d08c6f74b907`.
- Produces: Two clean isolated workspaces and immutable starting SHAs for all later tasks.

- [ ] **Step 1: Verify the Android worktree and branch.**

  Run: `git status --short --branch && git rev-parse HEAD` in the Android worktree.

  Expected: clean `feature/cloud-manager-trading-lab-m2.2` at the approved Design Spec baseline.

- [ ] **Step 2: Create the Agent linked worktree from the helper baseline.**

  Run: `git worktree add /Users/dlqs/Documents/Codex/2026-09-28/agent-m2-2-freqtrade -b feature/trading-lab-freqtrade-m2.2 baseline/runtime-helper-m0`.

  Expected: the new worktree starts at `0220516cc605e669bde9dc54631935f9239dd265`, with the existing helper source and provenance docs present.

- [ ] **Step 3: Run baseline checks before implementation.**

  Run: `python -m compileall -q src runtime-helper` in the Agent worktree; run `gradle --no-daemon --stacktrace --console=plain :cloud-core:test :cloud-agent-client:test` in the Android worktree.

  Expected: both baselines pass; any pre-existing failure is recorded before Task 1.

- [ ] **Step 4: Commit.**

  No product commit is created for this setup task; the branch/worktree SHAs are recorded in the plan ledger.

---

### Task 1: Versioned Freqtrade artifacts and backward-compatible Registry metadata

**Files:**
- Modify: `src/siftalpha_agent/registry.py` — add optional metadata models and preserve the current required-key validation for legacy entries.
- Create: `projects.d/freqtrade.json` — the sole new project entry with stable UUID, Freqtrade metadata, source/runtime/Compose paths, Web metadata, limits, and the exact upstream `sourceSha`.
- Create: `deploy/freqtrade/SOURCE_PIN.json` — official repository, release, commit, ARM64, and artifact schema identity.
- Create: `deploy/freqtrade/compose.yaml` — versioned Compose source with fixed service, private Web port binding, labels, and CPU/memory/PID limits.
- Create: `deploy/freqtrade/user_data/config.json` — dry-run-only Freqtrade configuration, private API binding, FreqUI authentication retained, and no exchange keys/secrets.
- Create: `deploy/freqtrade/user_data/strategies/SiftAlphaDryRunStrategy.py` — the smallest committed strategy required to boot the dry-run service.
- Create: `deploy/freqtrade/MANIFEST.sha256` — hashes for every deployment artifact above.
- Create: `tests/test_registry.py` and `tests/test_freqtrade_artifacts.py`.

**Interfaces:**
- Consumes: current `Project.from_dict(raw, filename)` and `Registry.load(directory)` behavior.
- Produces: optional `Project` fields `display_name`, `group`, `runtime_kind`, `project_uuid`, `main_service`, `architecture`, and `web`; stable `freqtrade` Registry identity; exact artifact manifest consumed by the deployment task and helper.

- [ ] **Step 1: Write failing Registry compatibility tests.**

  Add tests named `test_daily_stock_record_without_optional_metadata_remains_valid`, `test_freqtrade_record_requires_stable_identity_fields`, and `test_invalid_optional_web_scheme_is_rejected`. Assert that the existing daily-stock JSON still parses and that Freqtrade metadata is validated without changing legacy required keys.

- [ ] **Step 2: Write failing artifact tests.**

  Add `test_source_pin_is_exact`, `test_manifest_covers_all_deployment_artifacts`, `test_compose_contains_required_ownership_and_limits`, and `test_config_is_dry_run_without_exchange_credentials`. Assert the exact release/commit, one service, labels, private binding, CPU `1.0`, memory `2GiB`, PIDs `256`, `dry_run=true`, and absence of exchange key/secret/withdrawal permission fields.

- [ ] **Step 3: Run the focused tests to verify RED.**

  Run: `python -m unittest -v tests.test_registry tests.test_freqtrade_artifacts`.

  Expected: FAIL because optional metadata, the Freqtrade record, and committed artifacts do not yet exist.

- [ ] **Step 4: Implement the minimal Registry model and artifacts.**

  Keep the existing required field set intact. Parse optional fields with safe legacy defaults; reject invalid schemes, ports, UUIDs, architectures, limits, and non-Freqtrade source identities. Make the Compose file consume only fixed artifact paths and fixed values. FreqUI auth values, if required by the pinned Freqtrade release, are lab-only runtime credentials in the private versioned artifact, never Agent/App credentials and never exchange credentials.

- [ ] **Step 5: Run focused and full Agent tests.**

  Run: `python -m unittest -v tests.test_registry tests.test_freqtrade_artifacts`, then `python -m unittest discover -s tests -v`.

  Expected: PASS; legacy daily-stock parsing remains green and the manifest matches all committed artifacts.

- [ ] **Step 6: Commit.**

  ```bash
  git add src/siftalpha_agent/registry.py projects.d/freqtrade.json deploy/freqtrade tests/test_registry.py tests/test_freqtrade_artifacts.py
  git commit -m "feat: add versioned freqtrade project artifacts"
  ```

---

### Task 2: Helper ownership validation and bounded Resources action

**Files:**
- Modify: `runtime-helper/siftalpha-docker-helper` — add `resources`, Registry-derived ownership expectations, Compose identity checks, and missing-container semantics.
- Modify: `src/siftalpha_agent/runtime.py` — add `resources` to the Agent allowlist and invoke it with only validated project ID and no client container ID.
- Create: `tests/test_runtime_helper_boundary.py` and `tests/test_runtime_client.py`.

**Interfaces:**
- Consumes: Task 1 `Project` optional metadata and Freqtrade labels/Compose project identity.
- Produces: helper functions `validate_container_ownership(project, container)`, `owned_container(project)`, `resources(project)`, and `RuntimeClient.invoke("resources", project_id)` behavior; bounded payloads used by Task 3.

- [ ] **Step 1: Write failing helper boundary tests.**

  Add tests named `test_missing_container_status_is_initial`, `test_missing_container_logs_are_bounded_empty`, `test_missing_container_resources_have_no_running_instance`, `test_existing_wrong_project_label_is_rejected`, `test_existing_wrong_project_uuid_is_rejected`, `test_existing_wrong_compose_identity_is_rejected`, `test_daily_stock_legacy_ownership_remains_valid`, and `test_resources_uses_owned_container_only`.

- [ ] **Step 2: Write failing Agent runtime tests.**

  Add `test_resources_is_the_only_new_runtime_action`, `test_runtime_rejects_arbitrary_container_id`, and `test_logs_tail_remains_bounded_to_1000`.

- [ ] **Step 3: Run focused tests to verify RED.**

  Run: `python -m unittest -v tests.test_runtime_helper_boundary tests.test_runtime_client`.

  Expected: FAIL because `resources` and the ownership/Compose checks do not yet exist.

- [ ] **Step 4: Implement ownership validation.**

  Resolve the project exclusively from the Registry. If Docker inspect reports no container, return the existing initial/empty behavior without forcing labels. If a container exists, require the managed labels, the project ID/UUID where applicable, `com.docker.compose.project == project.project_id`, and `com.docker.compose.service == project.main_service`; otherwise raise the existing bounded runtime error. Preserve the legacy daily-stock expectation so its current managed container is not reclassified.

- [ ] **Step 5: Implement bounded resources.**

  Add only the fixed Docker `stats --no-stream` query for the ownership-validated container. Return one bounded RuntimeUnit for an owned running container, with CPU percent, memory usage/limit, state, health, restart count, exit code, OOM state, and timestamps. Return `runtimeUnits=[]`/no running instance when the container is absent or not running; never accept an App-supplied container ID.

- [ ] **Step 6: Extend the Agent allowlist.**

  Add `resources` to the existing action regex and invocation path. Keep `list`, `prepare`, `start`, `stop`, `status`, and `logs` unchanged except for the shared ownership validation. Keep logs at tail `0..1000` and never add follow/stream behavior.

- [ ] **Step 7: Run focused and full Agent tests.**

  Run: `python -m unittest -v tests.test_runtime_helper_boundary tests.test_runtime_client`, then `python -m unittest discover -s tests -v`.

  Expected: PASS, including all missing-container and present-but-unowned cases.

- [ ] **Step 8: Commit.**

  ```bash
  git add runtime-helper/siftalpha-docker-helper src/siftalpha_agent/runtime.py tests/test_runtime_helper_boundary.py tests/test_runtime_client.py
  git commit -m "feat: enforce runtime ownership and bounded resources"
  ```

---

### Task 3: Agent API metadata, RuntimeUnit DTOs, and operation-safe endpoints

**Files:**
- Modify: `src/siftalpha_agent/app.py` — expose optional project/Web/RuntimeUnit metadata and `GET /v1/projects/{id}/resources` while preserving every M1 route.
- Create: `tests/test_agent_api_contract.py`.

**Interfaces:**
- Consumes: Task 1 Registry metadata and Task 2 helper payload/error behavior.
- Produces: JSON contracts for project/status/resources, including `runtimeUnits`, `web`, and no-container empty semantics; unchanged M1 operation lifecycle.

- [ ] **Step 1: Write failing API contract tests.**

  Add tests named `test_projects_exposes_freqtrade_metadata`, `test_status_exposes_single_runtime_unit`, `test_resources_exposes_bounded_runtime_units`, `test_missing_container_status_logs_resources_are_nonfatal`, `test_unowned_existing_container_is_rejected`, and `test_m1_routes_remain_registered`.

- [ ] **Step 2: Run API tests to verify RED.**

  Run: `python -m unittest -v tests.test_agent_api_contract`.

  Expected: FAIL because the resources route and metadata payloads do not exist.

- [ ] **Step 3: Implement the minimal API extension.**

  Keep existing status/error codes and operation persistence. Add the resources route with the same bearer authentication and project lookup. Return Web metadata from the Registry, map missing containers to initial/empty runtime units, and propagate ownership mismatch as a bounded runtime error. Do not add any generic command or Docker admin route.

- [ ] **Step 4: Run Agent contract and full tests.**

  Run: `python -m unittest -v tests.test_agent_api_contract`, then `python -m unittest discover -s tests -v`.

  Expected: PASS with all M1 routes still available.

- [ ] **Step 5: Commit.**

  ```bash
  git add src/siftalpha_agent/app.py tests/test_agent_api_contract.py
  git commit -m "feat: expose freqtrade runtime resources"
  ```

---

### Task 4: Platform-neutral Cloud domain and Agent client support

**Files:**
- Create: `cloud-core/src/main/kotlin/com/siftalpha/cloud/core/CloudRuntimeUnit.kt` and `CloudWebEndpoint.kt`.
- Modify: `cloud-core/src/main/kotlin/com/siftalpha/cloud/core/CloudProject.kt` and `CloudProjectStatus.kt` — optional metadata and RuntimeUnit list.
- Modify: `cloud-agent-client/src/main/kotlin/com/siftalpha/cloud/agent/api/M1Dtos.kt`, `M1Json.kt`, `CloudApiModels.kt`, `CloudDtoMapper.kt`, and `CloudAgentClient.kt`.
- Create: `cloud-core/src/test/kotlin/com/siftalpha/cloud/core/CloudWebEndpointPolicyTest.kt` and `cloud-agent-client/src/test/kotlin/com/siftalpha/cloud/agent/TradingLabDtoTest.kt`.
- Modify: existing `CloudAgentClientTest.kt`, `M1JsonTest.kt`, and `CloudErrorMappingTest.kt` only for additive coverage.

**Interfaces:**
- Produces: `CloudWebEndpoint`, `CloudRuntimeUnit`, and `CloudResources` models; `SiftAlphaCloudAgentClient.resources(projectId)`; additive M1 DTO decoding; fail-closed Web endpoint validation.

- [ ] **Step 1: Write failing domain/client tests.**

  Cover valid private `http`/`https` endpoints, rejection of `file:`, `javascript:`, `intent:`, blank host, public fallback host, and malformed ports. Cover JSON decoding of Web metadata, absent RuntimeUnits, one RuntimeUnit resource values, missing-container empty resources, and the `/resources` request path. Assert Authorization remains redacted by existing request string behavior.

- [ ] **Step 2: Run focused Kotlin tests to verify RED.**

  Run: `gradle --no-daemon --stacktrace --console=plain :cloud-core:test --tests '*CloudWebEndpointPolicyTest' :cloud-agent-client:test --tests '*TradingLabDtoTest'`.

  Expected: FAIL because the models, decoder, mapper, and client method do not exist.

- [ ] **Step 3: Implement additive domain/client models.**

  Keep `CloudOperationAction` limited to Prepare/Start/Stop because Resources is read-only. Add nullable/empty-safe Web and RuntimeUnit fields without changing existing M1 constructor semantics. Map Agent enum strings and timestamps with the existing strict invalid-response behavior.

- [ ] **Step 4: Implement the bounded `/resources` client call.**

  Add `resources(projectId: String): CloudResources` using `GET /v1/projects/{id}/resources`; keep path-segment encoding, response-size limits, error mapping, credential loading, and timeout behavior identical to the existing client.

- [ ] **Step 5: Run focused and full Cloud tests.**

  Run the focused command from Step 2, then `gradle --no-daemon --stacktrace --console=plain :cloud-core:test :cloud-agent-client:test`.

  Expected: PASS with all existing M1 tests green.

- [ ] **Step 6: Commit.**

  ```bash
  git add cloud-core cloud-agent-client
  git commit -m "feat: add cloud runtime resources and web metadata"
  ```

---

### Task 5: Android Trading Lab lifecycle and single Freqtrade card

**Files:**
- Create: `app/src/main/java/com/siftalpha/studio/cloud/CloudTradingLabController.kt` — credential-backed client, bounded refresh, operation polling, and UI state.
- Create: `app/src/main/java/com/siftalpha/studio/cloud/CloudTradingLabScreen.kt` — one Freqtrade card and action callbacks.
- Create: `app/src/main/java/com/siftalpha/studio/cloud/CloudTradingLabUiState.kt` and `CloudTradingLabPolicy.kt` — pure state/action policy.
- Modify: `app/src/main/java/com/siftalpha/studio/MainActivity.kt`, `HomeScreen.kt`, and `SiftAlphaNormalSecondaryUi.kt` — add one Cloud/Trading Lab entry only.
- Modify: `app/src/main/java/com/siftalpha/studio/ResultWebActivity.kt` — add a strictly validated remote Web endpoint path while preserving existing local result behavior.
- Modify: `app/src/main/AndroidManifest.xml` and `app/src/main/res/values/strings.xml` for the new activity/labels.
- Create: `app/src/test/java/com/siftalpha/studio/cloud/CloudTradingLabPolicyTest.kt`, `CloudTradingLabControllerTest.kt`, and `CloudRemoteWebPolicyTest.kt`.

**Interfaces:**
- Consumes: Task 4 `SiftAlphaCloudAgentClient`, `CloudOperationPoller`, `CloudProjectActionPolicy`, `CloudWebEndpoint`, and `CloudRuntimeUnit`.
- Produces: `CloudTradingLabUiState` with one Freqtrade card, operation-safe callbacks `prepare()`, `start()`, `stop()`, `refresh()`, `loadLogs()`, `loadResources()`, and a validated `openWeb()` intent.

- [ ] **Step 1: Write failing policy/controller tests.**

  Add tests named `test_initial_project_shows_prepare_not_running`, `test_start_polls_then_refreshes_status`, `test_stop_polls_then_refreshes_status`, `test_operation_failure_does_not_fake_stopped`, `test_unreachable_server_is_explicit_error`, `test_logs_default_to_200_and_cap_at_1000`, `test_missing_resources_show_no_running_instance`, and `test_open_web_accepts_only_agent_private_http_https_endpoint`.

- [ ] **Step 2: Run Android tests to verify RED.**

  Run: `gradle --no-daemon --stacktrace --console=plain :app:testDebugUnitTest --tests '*CloudTradingLab*' --tests '*CloudRemoteWebPolicyTest'`.

  Expected: FAIL because the Trading Lab state/controller/policy and endpoint path do not exist.

- [ ] **Step 3: Implement the controller and pure policies.**

  Construct the existing credential store/client for the configured server. Discover the registered `freqtrade` project, render initial missing-container state, call actions only through the Agent, poll the returned operation ID, then fetch Status/Resources/Logs. Never infer `RUNNING` or `STOPPED` locally and never log or expose the bearer token.

- [ ] **Step 4: Implement the one-card UI and entry point.**

  Add only the Freqtrade card with state, CPU, memory, Prepare, Start, Stop, Logs, Open, and Refresh. Enable actions from the real operation/status facts. Do not add bot selection, strategy editing, terminal, Compose editor, or Docker controls.

- [ ] **Step 5: Reuse the Web viewer safely.**

  Keep the existing local `ResultWebActivity` path intact. The new remote endpoint extra must be accepted only after `CloudWebEndpointPolicy` validation and must keep navigation on the validated private host; all other schemes/hosts fail closed.

- [ ] **Step 6: Run focused and full Android JVM tests.**

  Run the focused command from Step 2, then `gradle --no-daemon --stacktrace --console=plain :core:test :app:testDebugUnitTest`.

  Expected: PASS with existing Normal Mode, Developer Mode, legacy Runtime, Termux, PRoot, and Web tests unchanged.

- [ ] **Step 7: Commit.**

  ```bash
  git add app/src/main app/src/test/java/com/siftalpha/studio/cloud app/src/main/java/com/siftalpha/studio/cloud
  git commit -m "feat: add single-project trading lab surface"
  ```

---

### Task 6: CI, architecture guards, and artifact integrity checks

**Files:**
- Modify: Android `.github/workflows/cloud-manager-m2-2.yml` — add named Trading Lab domain/ownership/Web validation steps while preserving Foundation and Keystore jobs.
- Modify: Android `tools/verify_cloud_manager_architecture.sh` only for additive Cloud-module checks.
- Create: Agent `.github/workflows/cloud-manager-agent-m2-2.yml` — run the Agent unit/contract suite and artifact-manifest checks.
- Create/modify: CI test helpers only where required to run the same tests locally and in CI.

**Interfaces:**
- Consumes: Tasks 1–5 committed tests and exact artifact manifest.
- Produces: independently visible `TRADING_LAB_DOMAIN`, `OWNERSHIP_BOUNDARY`, `WEB_ENDPOINT_VALIDATION`, Agent test, and APK evidence without weakening existing Cloud Foundation gates.

- [ ] **Step 1: Write failing CI guard tests/checks.**

  Add assertions that the Agent workflow runs all Agent tests, the App workflow keeps `architecture-guard`, `cloud-modules`, `android-foundation`, and `android-keystore-instrumentation`, and no Cloud module imports legacy Runtime/Termux/PRoot symbols.

- [ ] **Step 2: Run the guard checks to verify RED.**

  Run: `bash tools/verify_cloud_manager_architecture.sh` and the Agent artifact/test command before CI edits.

  Expected: the new Trading Lab evidence labels/checks are absent or incomplete.

- [ ] **Step 3: Implement additive CI jobs/steps.**

  Keep the verified emulator configuration and Cloud-only isolation unchanged. Add the Agent test workflow and App Trading Lab test coverage; upload test reports/APK artifacts. Do not run Oracle deployment from CI.

- [ ] **Step 4: Run all local CI-equivalent checks.**

  Run: `bash tools/verify_cloud_manager_architecture.sh`; `python -m unittest discover -s tests -v`; `gradle --no-daemon --stacktrace --console=plain :cloud-core:test :cloud-agent-client:test :core:test :app:testDebugUnitTest`; `gradle --no-daemon --stacktrace --console=plain :app:compileDebugKotlin :app:assembleDebug` with the existing CPython/Alpine preparation.

  Expected: all PASS and the APK exists; no legacy regression is introduced.

- [ ] **Step 5: Commit CI changes separately per repository.**

  ```bash
  git commit -m "ci: verify freqtrade trading lab foundation"
  ```

  Record exact Agent and Android SHAs before any server action. Push both branches only through existing GitHub authentication; never create PATs, deploy keys, or server credentials.

---

### Task 7: Controlled Oracle deployment from committed artifacts

**Files:**
- Use only committed Agent files from `feature/trading-lab-freqtrade-m2.2`.
- Use: `deploy/freqtrade/SOURCE_PIN.json`, Compose/config/strategy/manifest, `projects.d/freqtrade.json`, committed helper, and committed Agent source.
- No untracked or hand-edited server file is allowed.

**Interfaces:**
- Consumes: green Agent/App CI, exact artifact manifest, and pre-deployment source/helper hashes.
- Produces: Oracle Freqtrade project at the fixed paths, with one controlled Agent restart and a recorded rollback point.

- [ ] **Step 1: Record pre-deployment state read-only.**

  Record `PRE_DEPLOY_AGENT_SHA`, `PRE_DEPLOY_HELPER_SHA256`, service state/PID, `daily-stock-analysis` status, Docker workload inventory, and a private FreqUI port availability check. Do not read or print tokens.

- [ ] **Step 2: Validate the artifact bundle before transfer.**

  Verify the Git commit, `MANIFEST.sha256`, Freqtrade source pin, and all config/Compose/strategy hashes locally. Refuse deployment if any artifact is uncommitted or drifted.

- [ ] **Step 3: Install the exact source/artifacts without manual editing.**

  Clone or verify `freqtrade/freqtrade` at the exact commit under `/srv/siftalpha/projects/freqtrade/source`; copy the committed runtime artifact bundle to `/srv/siftalpha/runtime-data/freqtrade`; install the committed Registry entry; verify clean source tree, file hashes, ownership labels, private binding, and resource limits. Do not alter `/etc/siftalpha-agent/token`, WireGuard, SSH, OCI rules, or existing project files.

- [ ] **Step 4: Install committed Agent/helper and restart once.**

  Preserve root ownership/mode for the helper, deploy only the committed Agent/helper files, then perform one controlled `systemctl restart siftalpha-agent`. If deployment fails, restore the M0 Agent/helper baseline, restart once, record the failure, and stop.

- [ ] **Step 5: Run immediate regressions before Freqtrade actions.**

  Run authenticated `GET /v1/health`, `GET /v1/projects`, and daily-stock `GET /status`; confirm SSH, WireGuard binding, Docker, daily-stock, no new public management port, and no unrelated workload change.

  Expected: all existing regressions PASS before Prepare is attempted.

---

### Task 8: Real Freqtrade server acceptance, APK evidence, and device handoff

**Files:**
- Create only the final acceptance evidence/report in the approved documentation location after verification.
- No further product scope or architecture changes are allowed in this task.

**Interfaces:**
- Consumes: deployed Agent API, Android APK from Task 6, and the existing private WireGuard route.
- Produces: `FREQTRADE_REAL_SERVER_ACCEPTANCE`, APK SHA256, device verification state, and final phase status.

- [ ] **Step 1: Execute the exact server sequence.**

  Verify, in order: discover `freqtrade`; initial Status; Prepare returns operation ID; poll to `SUCCEEDED`; `environmentState=READY`; Start returns operation ID; poll to `SUCCEEDED`; `runtimeState=RUNNING`; RuntimeUnit CPU/RAM/limits; bounded Logs; private FreqUI endpoint; Stop returns operation ID; poll to `SUCCEEDED`; `runtimeState=STOPPED`.

- [ ] **Step 2: Verify non-target regression.**

  Confirm `daily-stock-analysis` and every existing Docker workload remain unchanged. If any regression appears, stop and restore the pre-deployment baseline according to Task 7.

- [ ] **Step 3: Verify APK evidence.**

  Run the final Foundation/Trading Lab CI and record the generated APK path and SHA256. Do not treat local build success as CI success.

- [ ] **Step 4: Perform Android real-device acceptance only when private routing exists.**

  On the user device, verify connection, Freqtrade discovery, Prepare, Start, Status, Logs, CPU/RAM, Open FreqUI, and Stop. If the route to `10.77.0.0/24` is absent, record `DEVICE_VERIFY_BLOCKED_WIREGUARD` and stop without changing WireGuard.

- [ ] **Step 5: Record final status and stop.**

  Use `M2_2_FREQTRADE_SINGLE_PROJECT=IMPLEMENTED_AND_CI_VERIFIED/DEVICE_VERIFY_PENDING` until real-device acceptance passes. Use `CLOSED` only after the full device sequence passes. Do not begin another bot, M2.3, strategy research, or infrastructure work.

---

## Plan Self-Review

1. **Spec coverage:** Tasks 1–3 cover Registry/artifact versioning, helper ownership/resources, missing-container semantics, M1 compatibility, Web metadata, and Agent API. Tasks 4–5 cover Cloud DTO/client, poller lifecycle, one-card UI, bounded logs/resources, and validated Web open. Tasks 6–8 cover CI, controlled deployment, server acceptance, APK, and device gating.
2. **Boundary coverage:** The plan adds exactly one helper action, one Freqtrade project, one RuntimeUnit, one resources route, and one Android card. It does not add generic command execution, Docker administration, WireGuard management, or future-bot abstractions.
3. **Artifact integrity:** Registry, Compose source, dry-run config, minimal strategy, Web metadata, resource limits, source pin, and hashes are committed before deployment; Oracle receives only the committed bundle.
4. **Missing-container rule:** Status/logs/resources explicitly have separate no-container tests; ownership is enforced only after a container is found, and any existing identity mismatch rejects the operation.
5. **Placeholder and ambiguity scan:** No task contains an unresolved placeholder. The private FreqUI port is selected once by read-only preflight and then persisted in the committed Registry/artifact bundle; a conflict requires a new reviewed artifact commit rather than a server edit.
6. **Stop conditions:** ARM64/build failure, deployment rollback, regression, missing WireGuard route, or failed real-device acceptance each has an explicit stop state. No issue found in this plan blocks the confirmed single-project closure.

```ini
IMPLEMENTATION_PLAN=READY
PLAN_SELF_REVIEW=PASS
IMPLEMENTATION_STARTED=NO
SERVER_MUTATION=NO
SECRETS_EXPOSED=NO
```
