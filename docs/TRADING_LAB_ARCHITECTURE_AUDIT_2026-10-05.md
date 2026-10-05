# SiftAlpha Trading Lab Architecture Audit

**Date:** 2026-10-05  
**Repository:** `kuashan/siftalpha-one`  
**Audit branch:** `codex/trading-lab-architecture-audit`  
**SiftAlpha baseline:** `feature/cross-platform-core@fbd1e74259e4cc83a7d530df121015abbc216e52`

## 1. Purpose

This audit was performed before continuing Cloud Manager M2.2.

Question:

> Should SiftAlpha Cloud Manager directly reuse an existing Docker/server management product for Trading Lab, or keep SiftAlpha Agent as the runtime authority and borrow proven design patterns?

Trading Lab is expected to manage allowlisted workloads such as:

- Freqtrade
- OctoBot
- Hummingbot
- Jesse
- SSSS

The Oracle server also hosts unrelated projects. Therefore Trading Lab must never become a general-purpose remote root/Docker administration surface.

## 2. External source baselines

The audit is pinned to the following upstream heads:

| Project | Commit audited | License |
|---|---|---|
| Dockge | `f809ae192b571944ad773e9866d3e67064ae8043` | MIT |
| Coolify | `34e4da1095726c6f9e4bb4272aa01b5a6dd43901` | Apache-2.0 |
| CasaOS AppManagement | `debfa317f0f996b91b43210e8d57799461388704` | Apache-2.0 |
| CasaOS AppStore | `0909364b800950030e71ea82355a5969a1c08b39` | Apache-2.0 |
| CasaOS Gateway | `606a84523802dde7269f10660c867d089e7ed660` | Apache-2.0 |
| CasaOS UserService | `800c630c3443364cde142a9aadea0dc3880e6232` | Apache-2.0 |

## 3. Dockge findings

Dockge is a Compose-stack-first manager.

Relevant implementation properties:

- Uses a filesystem stacks directory.
- Treats Compose project as the main management unit.
- Uses `docker compose ls --all --format json` for discovery/status.
- Uses `docker compose ps --format json` for service/container state.
- Uses `docker compose up -d --remove-orphans` for start/deploy.
- Uses `docker compose stop` for stop.
- Uses `docker compose restart` for restart.
- Uses `docker compose logs -f --tail 100` for live combined logs.
- Supports multiple remote Dockge agents.
- Supports interactive container terminal and Compose editing.
- Mounts Docker socket in its standard deployment.

### What SiftAlpha should borrow

- Compose stack as first-class project unit.
- File-based stack identity.
- Status from Compose rather than guessing from one PID.
- Project -> multiple service/container model.
- `docker compose ps --format json` as a simple source for runtime-unit state.
- Clear separation between stack status and individual service status.

### What SiftAlpha should NOT inherit

- General Compose editing from the phone.
- Interactive container terminal.
- Arbitrary stack creation/deletion in M2.2.
- Unrestricted Docker socket administration surface.
- Broad Docker host control.

These exceed SiftAlpha's Trading Lab safety boundary.

## 4. Coolify findings

Coolify is a full remote deployment control plane.

Relevant implementation properties:

- Models remote servers as managed resources.
- Runs remote Docker/deployment work through SSH.
- Has a large deployment/job lifecycle.
- Uses stable resource UUIDs and Docker labels to identify container ownership.
- Filters Docker queries by ownership labels instead of relying only on container names.
- Supports CPU/memory limits and metrics.
- Handles multi-container services/applications.
- Has explicit deployment ownership and server status concepts.

The strongest pattern for SiftAlpha is ownership labeling.

A current Coolify pattern queries containers using labels such as a resource UUID and Compose project identity. This prevents unrelated containers from being accidentally included when names change or hosts are migrated.

### What SiftAlpha should borrow

- Stable project identity independent of mutable container name.
- Ownership labels on every managed runtime unit.
- Query only containers owned by the selected project.
- Server/project/status/operation separation.
- Resource snapshots for CPU/memory.
- Explicit operation state instead of optimistic UI state.

### What SiftAlpha should NOT inherit

- General remote SSH command execution from the App.
- Full deployment platform features.
- Docker installation/cleanup administration.
- Database/service provisioning.
- Arbitrary post/pre-deployment shell commands.
- Broad server control.

Coolify is much more privileged than Trading Lab needs.

## 5. CasaOS findings

CasaOS is especially useful as a product/model reference.

Its modern architecture is split across services such as:

- AppManagement
- Gateway
- UserService

CasaOS AppManagement directly models Compose applications and exposes operations for:

- install
- update/apply
- start
- stop
- restart
- containers
- logs
- health checks

Its implementation uses Docker Compose v2 APIs and Docker APIs. It also gathers per-container stats.

CasaOS AppStore adds a top-level `x-casaos` metadata block alongside standard Compose content.

Important metadata ideas include:

- stable app id
- main service
- index/path
- web port
- scheme
- icon
- title
- developer
- supported architectures

CasaOS explicitly keeps container-runtime details in standard Compose and stores app/UI metadata in a separate metadata contract.

### What SiftAlpha should borrow

- Keep Docker runtime semantics in standard Compose.
- Add a small SiftAlpha metadata contract rather than inventing a second runtime format.
- Record main service and Web UI entrypoint.
- Record supported architecture before installation/deployment.
- Treat one Compose app as one product even when it contains multiple containers.
- Health checks can target the declared private Web UI endpoint.
- CPU/memory statistics belong to runtime units, not only top-level project state.

### What SiftAlpha should NOT inherit in M2.2

- App store installation.
- Arbitrary user-supplied Compose YAML.
- Container terminal.
- Full Docker network/container administration.
- Runtime settings editor.
- Uninstall/delete operations.

Those can be considered later only if separately approved.

## 6. Decision

### DECISION: keep SiftAlpha Agent as the only Trading Lab runtime authority.

Do **not** make Dockge, Coolify, Portainer or CasaOS a mandatory runtime dependency.

Do **not** expose their administrator APIs directly to SiftAlpha X.

Reasons:

1. Existing SiftAlpha Cloud Manager already has the correct product boundary:
   `SiftAlpha X -> Cloud Manager -> Agent Client -> private network -> SiftAlpha Agent -> Docker Runtime`.
2. Third-party products have much broader administration privileges than Trading Lab requires.
3. The Oracle host contains unrelated workloads.
4. SiftAlpha must enforce an allowlisted project boundary.
5. A thin Agent is easier to audit for secret exposure and destructive operations.
6. Adding another control plane creates another credential/security/update dependency.

Third-party products remain optional human-admin tools, not part of the SiftAlpha runtime contract.

## 7. Recommended M2.2 runtime model

### 7.1 Project registry

The Agent owns a static/controlled registry.

Example conceptual record:

```yaml
project_id: freqtrade
display_name: Freqtrade
group: trading-lab
runtime_kind: docker_compose
compose_path: /opt/siftalpha-trading-lab/freqtrade/compose.yaml
main_service: freqtrade
web:
  scheme: http
  service: freqtrade
  port: 8080
  path: /
architectures:
  - arm64
```

The Android App never supplies an arbitrary filesystem path or Docker command.

### 7.2 SiftAlpha Compose metadata

Prefer standard Docker Compose plus a small metadata block, conceptually similar to CasaOS:

```yaml
x-siftalpha:
  id: com.siftalpha.trading.freqtrade
  group: trading-lab
  main: freqtrade
  web:
    scheme: http
    port: 8080
    path: /
  architectures:
    - arm64
```

Do not redefine standard Compose service/volume/network/environment semantics.

M2.2 may keep this metadata in Agent registry first; embedding it into Compose is optional until the schema is frozen.

### 7.3 Ownership labels

Every managed service/container should carry immutable ownership labels.

Recommended namespace:

```text
com.siftalpha.managed=true
com.siftalpha.group=trading-lab
com.siftalpha.project.id=<stable project id>
com.siftalpha.project.uuid=<stable uuid>
```

The Agent must validate both:

1. the allowlisted Compose project/path; and
2. ownership labels / Compose project identity

before returning or mutating runtime units.

Never stop containers merely because a container name resembles a project name.

### 7.4 Allowed M2.2 actions

Only:

- LIST
- STATUS
- PREPARE
- START
- STOP
- RESTART (optional; only if explicitly added to M2.2)
- LOGS
- RESOURCES
- HEALTH
- OPEN_WEB metadata

Explicitly absent:

- arbitrary shell
- container exec
- compose editor
- docker socket proxy
- image delete
- volume delete
- network delete
- arbitrary container create
- uninstall/delete project
- secret readback

### 7.5 Compose execution

For the current Python Agent, the preferred M2.2 implementation is a small, bounded wrapper over Docker Compose V2.

Suggested read operations:

```text
docker compose -p <project> -f <allowlisted-file> ps --format json
docker compose -p <project> -f <allowlisted-file> logs --no-color --tail <bounded-N>
docker stats --no-stream --format json <owned-container-ids>
```

Suggested lifecycle operations:

```text
docker compose -p <project> -f <allowlisted-file> up -d --remove-orphans
docker compose -p <project> -f <allowlisted-file> stop
```

Arguments must be constructed from registry values, not concatenated from arbitrary App input.

Do not expose a generic command endpoint.

### 7.6 Runtime-unit model

CloudProject must no longer assume `1 project = 1 container`.

Add a list of runtime units:

```text
CloudProject
  -> RuntimeUnit[]
```

Each runtime unit should include at least:

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

Keep old top-level container fields temporarily for compatibility if required.

### 7.7 Web entrypoint

The Agent registry is authoritative for Web entrypoint metadata.

The App may open only validated private endpoints derived from the registered project.

Allow:

- http
- https

Reject:

- file:
- javascript:
- arbitrary intent/deep-link schemes

No public fallback when the private network path is unavailable.

## 8. Revised M2.2 scope

M2.2 should implement one minimal real loop with Freqtrade:

```text
SiftAlpha X
  -> Cloud Manager
  -> SiftAlpha Agent
  -> allowlisted Freqtrade Compose project
  -> STATUS
  -> START
  -> operation SUCCEEDED
  -> STATUS RUNNING
  -> LOGS
  -> RESOURCES
  -> HEALTH
  -> OPEN FreqUI
  -> STOP
  -> STATUS STOPPED
```

Only after this passes CI + Oracle smoke + Android device verification should M2.2 be closed.

Do not install OctoBot/Hummingbot/Jesse in parallel during the first acceptance loop.

## 9. Impact on the previous M2.2 plan

The previous M2.2 plan remains directionally valid, with these refinements:

1. **No third-party control-plane dependency.**
2. **Add stable ownership labels as a mandatory safety boundary.**
3. **Use Compose project as the primary runtime abstraction.**
4. **Separate project metadata from Compose runtime semantics.**
5. **Add RuntimeUnit[] for multi-container stacks.**
6. **Agent registry remains the allowlist and source of permitted paths/actions.**
7. **No general terminal/compose editor/app-store installer in M2.2.**
8. **Freqtrade remains the only first real acceptance workload.**

## 10. Status

```text
DOCKGE_AUDIT=PASS
COOLIFY_AUDIT=PASS
CASAOS_AUDIT=PASS

THIRD_PARTY_CONTROL_PLANE_REQUIRED=NO
SIFTALPHA_AGENT_RETAINED=YES
DOCKER_COMPOSE_PROJECT_MODEL=ADOPT
PROJECT_OWNERSHIP_LABELS=ADOPT
CASAOS_STYLE_METADATA_SPLIT=ADOPT
GENERAL_DOCKER_ADMIN_SURFACE=REJECT
REMOTE_SHELL_IN_M2_2=REJECT

RESULT=ARCHITECTURE_DECISION_READY
NEXT=M2_2_REALIGNMENT_AND_AGENT_PROVENANCE_AUDIT
```

M2.2 implementation must not begin until the current Oracle SiftAlpha Agent provenance and deployed API contract are re-verified.
