# M2.2 Freqtrade Single Project Design

Status: `DESIGN_SPEC=READY`

## Goal

Extend SiftAlpha Cloud Manager so one Android client can operate exactly one
Oracle-hosted Freqtrade dry-run project through the existing Cloud Agent:

```text
discover → prepare → start → status → logs → resources → open FreqUI → stop
```

The implementation ends after this single-project capability is verified. It
does not create a general trading-bot platform.

## Fixed scope

This phase includes only:

- one CloudProject with `projectId=freqtrade`;
- one Docker Compose runtime unit named `freqtrade`;
- Freqtrade official release `2026.9` at commit
  `1f394eaebc2f46a83d26971388628707802f8602`;
- ARM64 deployment on the existing Oracle server;
- dry-run operation with no real orders, real money, exchange private keys, or
  withdrawal permission;
- private-only FreqUI access through the existing Agent binding;
- Prepare, Start, Stop, Status, Logs, bounded Resources, and verified Web open;
- the existing Android Cloud credential store, operation poller, and Web
  viewer/activity.

Hummingbot, OctoBot, Jesse, SSSS, multiple projects or servers, generic shell
or container execution, Docker administration, secret injection, public
Trading Lab endpoints, WireGuard management, and M2.3 work are out of scope.

## Branches and provenance

Android changes remain on:

```text
feature/cloud-manager-trading-lab-m2.2
```

Agent changes start from the versioned Agent plus runtime-helper baseline on:

```text
feature/trading-lab-freqtrade-m2.2
```

The existing M1 Agent API and `daily-stock-analysis` behavior are compatibility
baselines. No implementation may replace the existing runtime model or alter
WireGuard, SSH, OCI networking, or unrelated Docker workloads.

## Agent and registry design

The Registry parser keeps the existing required fields and adds only optional
metadata needed by Freqtrade. Existing `daily-stock-analysis.json` remains
valid without migration. The Freqtrade entry supplies:

```text
projectId=freqtrade
displayName=Freqtrade
group=trading-lab
runtimeKind=docker_compose
projectUuid=fixed UUID persisted in the Registry and never regenerated
mainService=freqtrade
architecture=arm64
web.scheme=http
web.port=fixed private port selected by deployment preflight and persisted in the Registry
web.path=/
```

The project source and runtime locations are fixed:

```text
/srv/siftalpha/projects/freqtrade/source
/srv/siftalpha/runtime-data/freqtrade
```

The source identity is the official `freqtrade/freqtrade` repository and the
exact pinned commit above. The source tree must be clean and its HEAD must
equal the Registry `sourceSha` before Prepare is allowed. The Compose path
remains the Registry-controlled runtime `compose.yaml`.

The Freqtrade Compose configuration uses ARM64-compatible images/build inputs,
dry-run mode, no exchange private credentials, and explicit limits of CPU 1.0,
RAM 2GiB, and PIDs 256. FreqUI binds only to the Agent's private address
`10.77.0.1` on the fixed registered port. The port is selected only after a
read-only availability check and is never supplied by the App.

## Runtime-helper ownership boundary

The existing root-owned helper remains the only privileged runtime entrypoint.
It gains exactly one allowlisted action: `resources`. Existing actions remain
`list`, `prepare`, `start`, `stop`, `status`, and `logs`.

Before Start, Stop, Status, Logs, or Resources, the helper resolves the
project from the fixed Registry and verifies all of the following:

- the project ID is registered;
- the Compose path is the Registry-fixed path;
- the inspected container belongs to the expected Compose project;
- the ownership labels match the project.

Freqtrade containers carry these labels in addition to the existing managed
runtime labels:

```text
com.siftalpha.managed=true
com.siftalpha.group=trading-lab
com.siftalpha.project.id=freqtrade
com.siftalpha.project.uuid=the persisted Registry projectUuid
com.siftalpha.runtime.provider=cloud-docker
com.siftalpha.runtime.schema=1
```

Container name alone is never sufficient for ownership. Client input cannot
select an arbitrary path, container ID, Docker command, image, volume,
network, or Compose project.

`resources` uses a bounded, non-streaming Docker statistics query for the
Registry- and ownership-selected container. It returns only the RuntimeUnit
resource facts needed by the client; it does not expose Docker administration
or container exec.

The existing helper source is preserved as the provenance baseline. Production
deployment is a later controlled gate and is not part of this design document.

## Agent API and DTO design

All M1 endpoints remain unchanged:

```text
GET  /v1/health
GET  /v1/projects
GET  /v1/projects/{id}
POST /v1/projects/{id}/prepare
POST /v1/projects/{id}/start
POST /v1/projects/{id}/stop
GET  /v1/projects/{id}/status
GET  /v1/projects/{id}/logs
GET  /v1/operations/{operationId}
```

The only new runtime endpoint is:

```text
GET /v1/projects/{id}/resources
```

Project and status responses may include the registered Web metadata and a
single-element `runtimeUnits` array. The RuntimeUnit shape is limited to the
current Freqtrade needs:

```text
serviceName
containerId
image
state
health
restartCount
exitCode
oomKilled
startedAt
cpuPercent
memoryUsage
memoryLimit
```

Resources and logs remain bounded. Logs default to tail 200 and accept at most
1000 lines. No WebSocket/SSE log stream is introduced.

## Android design

The App reuses `AndroidCloudCredentialStore` for the server name, private
Agent base URL, and bearer token. Tokens remain Keystore-backed, masked, never
logged, and never returned by a UI or API model.

The Cloud client adds only the DTOs and calls required for registered Web
metadata, RuntimeUnit status, and `/resources`. The existing bounded operation
poller drives Prepare, Start, and Stop:

```text
POST action → operationId → poll operation → terminal success → GET status
```

The Trading Lab surface contains one Freqtrade card with:

```text
state, CPU, memory,
Prepare, Start, Stop, Logs, Open, Refresh
```

Every displayed state and enabled action comes from the latest Agent response
and operation lifecycle. Network failure is represented as an explicit Cloud
error; it must not be converted into `STOPPED`.

`Open` accepts only Agent-returned and locally validated `http` or `https`
metadata. The host is Agent-provided private metadata, not App input. The
existing `ResultWebActivity`/Web viewer is reused; no arbitrary intent,
`file:`, `javascript:`, `intent:`, public fallback, or new browser subsystem is
introduced.

## Verification gates

Implementation follows this order:

1. Agent unit/contract tests for legacy registry compatibility, Freqtrade
   validation, ownership, bounded resources/logs, Web metadata validation, and
   operation lifecycle.
2. Android Cloud and Trading Lab unit tests, architecture guard, existing
   Foundation CI, Keystore CI, and APK build.
3. Only after those tests pass, a controlled Oracle deployment of committed
   Agent/helper/project files with recorded pre-deployment hashes.
4. Read-only regression checks followed by the real Freqtrade server sequence:
   discovery, initial status, Prepare, READY, Start, RUNNING, RuntimeUnit
   resources, logs, private FreqUI, Stop, and STOPPED.
5. Confirm `daily-stock-analysis` and other existing Docker workloads are
   unchanged.
6. Android device verification over the pre-existing private route. If the
   device lacks the WireGuard route, record `DEVICE_VERIFY_BLOCKED_WIREGUARD`
   and stop; do not modify WireGuard.

Until the server and App checks pass, the phase cannot be marked closed. A
successful build without real device verification remains
`DEVICE_VERIFY_PENDING`.

## Explicit non-goals and backlog

The following are recorded as backlog only and are not designed or implemented
in this phase: additional trading bots, multiple RuntimeUnits, credential
synchronization, exchange secrets, public access, WebSocket logs, metrics
systems, multi-server scheduling, Kubernetes, Docker admin APIs, WireGuard
management, and M2.3.

## Design self-review

```ini
DESIGN_SPEC=READY
SPEC_COVERAGE=PASS
LINK_COVERAGE=discover,prepare,start,status,logs,resources,open,stop
LEGACY_DAILY_STOCK_COMPATIBILITY=REQUIRED
TRADING_BOT_COUNT=1
NEW_INFRASTRUCTURE=NONE
WIREGUARD_SCOPE=UNCHANGED
SECRETS_EXPOSED=NO
IMPLEMENTATION_PLAN=NOT_STARTED
```

Self-review found no blocker to the confirmed Freqtrade single-project
closure. No future-bot abstraction or unrelated infrastructure was added.
