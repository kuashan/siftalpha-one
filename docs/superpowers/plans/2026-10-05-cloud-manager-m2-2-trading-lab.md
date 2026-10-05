# Cloud Manager M2.2 — Trading Lab Implementation Plan

**Date:** 2026-10-05  
**Status:** READY_FOR_CODEX_AFTER_AGENT_PROVENANCE_GATE  
**Repository:** `kuashan/siftalpha-one`  
**Planning branch:** `codex/trading-lab-architecture-audit`

## Baseline

Current SiftAlpha X baseline audited before this plan:

`feature/cross-platform-core@fbd1e74259e4cc83a7d530df121015abbc216e52`

Old Cloud Manager branch:

`codex/cloud-manager-m2@336763abc2cfb88d038e2f48231acf29f3c2d09f`

The branches are diverged. Do not continue implementation on the old Cloud Manager branch and do not merge it wholesale.

Read first:

`docs/TRADING_LAB_ARCHITECTURE_AUDIT_2026-10-05.md`

That audit freezes the architecture decision:

- keep SiftAlpha Agent as runtime authority;
- use Docker Compose project as primary runtime abstraction;
- adopt stable SiftAlpha ownership labels;
- allowlist project paths/actions in Agent registry;
- do not depend on Dockge/Coolify/CasaOS at runtime;
- do not expose general Docker/SSH/shell/terminal administration in M2.2.

## Phase A — Realignment Gate

Before modifying code:

1. `git fetch --all --prune`
2. Re-read remote HEADs for:
   - `feature/cross-platform-core`
   - `codex/cloud-manager-m2`
3. If either advanced, audit new commits first.
4. Create implementation branch from the **current** cross-platform HEAD:

`feature/cloud-manager-trading-lab-m2.2`

Record:

```text
REALIGNMENT_BASE_HEAD=
OLD_CLOUD_MANAGER_HEAD=
ARCH_AUDIT_HEAD=
```

No code from the old Cloud branch may be copied until its compatibility with the current baseline is checked.

## Phase B — Restore M2.1 Foundation Only

Selectively restore/reimplement the verified M2.1 modules:

- `:cloud-core`
- `:cloud-agent-client`
- Android Cloud credential storage
- Cloud-specific tests/architecture guards

Restore the behavior, not blindly the historical diffs.

Must retain:

- CloudServer
- CloudProject
- CloudProjectStatus
- CloudResult
- CloudOperation
- CloudProjectActionPolicy
- SiftAlphaCloudAgentClient
- bounded responses
- timeouts
- JSON validation
- HTTP/error mapping
- Android Keystore + AES/GCM
- operation polling

Must not regress:

- Normal Mode
- Developer Mode
- Internal Alpine
- External Runtime
- Python/Node launch
- Prepare/Run/Stop
- Web discovery
- Result viewer
- current Design System

Gate:

```text
PHASE_B_CLOUD_FOUNDATION=PASS
CURRENT_RUNTIME_REGRESSION=PASS
```

## Phase C — Agent Provenance Gate

This phase is mandatory before server mutations.

Determine the actual Oracle deployment:

- Agent version
- source location
- Git repository, if any
- deployment path
- service/container identity
- current API contract
- current WireGuard/private endpoint

Read-only smoke only:

```text
GET /v1/health
GET /v1/projects
GET /v1/projects/{id}/status
```

Required:

```text
AGENT_PROVENANCE=VERIFIED
AGENT_API_CONTRACT=VERIFIED
AGENT_REMOTE_SMOKE=PASS
```

If the Agent source cannot be traced to versioned source:

```text
AGENT_PROVENANCE=BLOCKED
```

Stop. Do not rewrite or replace the server Agent implicitly.

## Phase D — Trading Lab Domain

Extend Cloud domain without breaking M1 compatibility.

Add first-class multi-container representation:

```text
CloudProject
  -> RuntimeUnit[]
```

RuntimeUnit minimum fields:

- serviceName
- containerId
- image
- state
- health
- restartCount
- exitCode
- oomKilled
- startedAt
- cpuPercent
- memoryUsage
- memoryLimit

Project metadata:

- displayName
- group
- runtimeKind
- description
- mainService
- web endpoint metadata
- supported architectures

Trading Lab group:

`group=trading-lab`

Runtime kinds initially:

- DOCKER_CONTAINER
- DOCKER_COMPOSE

Keep old top-level container fields only as temporary compatibility fields where required.

## Phase E — Agent Registry and Ownership Boundary

The Agent must own an allowlisted registry.

The App must never send:

- arbitrary Docker command
- arbitrary shell command
- arbitrary filesystem path
- arbitrary compose YAML
- arbitrary container id to mutate

Registry records bind project IDs to fixed server-side resources.

Every managed service/container must carry:

```text
com.siftalpha.managed=true
com.siftalpha.group=trading-lab
com.siftalpha.project.id=<stable-id>
com.siftalpha.project.uuid=<stable-uuid>
```

Before a lifecycle mutation, Agent verifies:

1. requested project is registered;
2. compose path is the registered path;
3. discovered containers belong to the registered project via ownership labels / Compose project identity.

Never stop a container based only on name matching.

## Phase F — Compose Runtime Adapter

For M2.2, use a thin bounded wrapper around Docker Compose V2.

Read paths conceptually:

```text
docker compose -p <registered-project> -f <registered-file> ps --format json
docker compose -p <registered-project> -f <registered-file> logs --no-color --tail <N>
docker stats --no-stream --format json <owned-container-ids>
```

Lifecycle:

```text
docker compose -p <registered-project> -f <registered-file> up -d --remove-orphans
docker compose -p <registered-project> -f <registered-file> stop
```

Command arguments must be built from registry values and validated identifiers.

There must be no generic remote command API.

## Phase G — API Extension

Preserve existing M1 endpoints.

Add only the minimum required information for Trading Lab.

Preferred direction:

- extend project/status response with runtimeUnits and Web metadata where backward-compatible;
- otherwise add a versioned read endpoint.

Resource snapshot can be:

`GET /v1/projects/{projectId}/resources`

or a compatible extension to status.

Do not add WebSocket/SSE logs in M2.2.

Logs remain bounded snapshots:

`GET /v1/projects/{projectId}/logs?tail=N`

N must remain bounded.

## Phase H — Cloud Manager UI

Wire the restored Cloud modules into current SiftAlpha X UI.

Add a Cloud / Trading Lab entry without replacing local project flows.

Minimum project card:

```text
Freqtrade
● Running

CPU       12%
Memory    640 MB / 1.5 GB

[打开] [停止] [日志]
```

Actions:

- Refresh
- Prepare
- Start
- Stop
- Logs
- Open Web

UI state must come from server facts.

Never set RUNNING optimistically after Start.

Correct flow:

```text
POST start
-> operationId
-> poll operation
-> SUCCEEDED
-> refresh status
-> RUNNING
```

Network failure is not STOPPED.

## Phase I — Web Endpoint

Agent registry is authoritative for the project's Web UI entrypoint.

Allow only:

- http
- https
- private network hosts/addresses approved by Cloud configuration

Reject:

- file:
- javascript:
- arbitrary Android intents
- public fallback

Prefer existing SiftAlpha Web/Result viewer when compatible.

## Phase J — First Real Workload: Freqtrade Only

Do not install multiple trading products in parallel.

First acceptance target:

`freqtrade`

Use Dry-run / non-real-money configuration for the first lifecycle test.

Real Oracle acceptance must prove:

1. App discovers Freqtrade.
2. STOPPED/RUNNING state is correct.
3. Start request creates operation.
4. Operation becomes SUCCEEDED.
5. Status becomes RUNNING.
6. Logs are readable.
7. RuntimeUnit list is correct.
8. CPU/memory snapshot is visible.
9. Health state is meaningful.
10. Open launches the private FreqUI endpoint.
11. Stop succeeds.
12. Status returns STOPPED.
13. unrelated server projects remain unchanged.

Required:

`FREQTRADE_TRADING_LAB_ACCEPTANCE=PASS`

## Phase K — Security Tests

Mandatory regression cases:

- unknown project cannot be mutated;
- project cannot address another project's container;
- project name spoofing cannot bypass labels;
- path traversal rejected;
- invalid runtime unit ID rejected;
- arbitrary shell input has no API path;
- API token never logged;
- Binance/third-party secrets never returned;
- Cloud network unavailable does not fall back to public host;
- Docker/Compose timeout maps to bounded failure;
- log response remains bounded.

## Phase L — CI / Closeout

Each meaningful phase receives its own commit.

CI must include:

- cloud-core tests
- cloud-agent-client tests
- Android cloud tests
- current Android regression suite
- architecture guard
- build/APK
- Trading Lab domain tests
- ownership-label tests
- endpoint validation tests

Completion report must include:

```text
START_HEAD=
FINAL_HEAD=
BRANCH=

CLOUD_MANAGER_FOUNDATION=
AGENT_PROVENANCE=
AGENT_API_CONTRACT=
AGENT_REMOTE_SMOKE=

TRADING_LAB_DOMAIN=
OWNERSHIP_LABEL_BOUNDARY=
COMPOSE_RUNTIME=
CLOUD_UI=
WEB_ENDPOINT=
RESOURCE_STATUS=

FREQTRADE_REAL_SERVER_ACCEPTANCE=

CI_RUN=
CI_RESULT=
APK_ARTIFACT=
APK_SHA256=

SECRETS_EXPOSED=NO
PUBLIC_TRADING_PORTS_REQUIRED=NO

DEVICE_VERIFY_PENDING=
FINAL_STATUS=
```

Do not mark M2.2 CLOSED until all are true:

- GitHub Actions PASS
- Oracle real-server acceptance PASS
- Android real-device acceptance PASS

Before real-device acceptance:

`IMPLEMENTED_AND_CI_VERIFIED / DEVICE_VERIFY_PENDING`

After real-device PASS:

`SIFTALPHA_CLOUD_MANAGER_M2_2=CLOSED`

Stop there. Do not automatically begin M2.3.

## Explicitly Out of Scope for M2.2

- Dockge/Coolify/CasaOS as required runtime dependency
- arbitrary remote shell
- container terminal
- Compose editor
- Docker socket proxy
- App Store installer
- project uninstall/delete
- image/volume/network administration
- Cloud Secret Injection
- Binance secret editing
- public Trading Lab ports
- Tailscale migration
- Kubernetes
- Prometheus/Grafana
- live unbounded logs
- multi-server scheduler
- OctoBot/Hummingbot/Jesse/SSSS deployment during first Freqtrade acceptance
