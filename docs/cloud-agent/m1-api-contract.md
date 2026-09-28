# SiftAlpha Agent M1 API Contract

**Contract status:** Audited snapshot for M2.1 client planning

**Audit date:** 2026-09-28

**M1 scope:** health, projects, prepare, start, stop, status, logs, operations

## Source provenance

This contract is extracted from the available M1 Agent implementation snapshot and the approved M1 specification. The Android product repository does not contain the server implementation, and the available `siftalpha-agent` workspace snapshot has no Git remote or commit identity in this checkout. Therefore this document is the GitHub-auditable protocol record for M2.1, but the server repository commit must be attached or otherwise reconciled before Cloud client implementation is allowed to rely on undocumented fields.

The implementation snapshot audited for this document defines the routes in `src/siftalpha_agent/app.py`, the operation projection in `operation_public`, timestamp creation in `src/siftalpha_agent/state.py`, and runtime payload shapes through the `RuntimeClient` contract and tests.

No Bearer Token, private key, or secret value is included here.

## Authentication

Every `/v1/*` endpoint requires:

```http
Authorization: Bearer <token>
```

Missing or incorrect authorization returns:

- HTTP `401`
- `{ "error": { "code": "UNAUTHORIZED", "message": "authentication required" } }`

The client must never log the full header or token.

## Common error envelope

All declared errors use:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "sanitized message"
  }
}
```

Observed mappings:

| Situation | HTTP | Code |
|---|---:|---|
| Missing/incorrect Bearer token | 401 | `UNAUTHORIZED` |
| Unknown project | 404 | `PROJECT_NOT_FOUND` |
| Active destructive operation for the project | 409 | `OPERATION_CONFLICT` |
| Invalid request validation, including `tail < 0` or `tail > 1000` | 422 | `INVALID_REQUEST` |
| Runtime status unavailable | 503 | `DOCKER_ERROR` |
| Runtime logs unavailable | 503 | `DOCKER_ERROR` |
| Unknown operation | 404 | `INVALID_REQUEST` |

## Timestamp representation

The server writes operation timestamps with Python `datetime.now(timezone.utc).isoformat()`.

Wire examples therefore use UTC ISO-8601 strings with an offset, for example:

```text
2026-09-28T12:34:56.123456+00:00
```

`startedAt` and `finishedAt` are nullable. M2.1 DTOs may decode wire timestamps as `String?`, but the mapper must convert them to domain `Instant?` and reject malformed non-null timestamps as `INVALID_RESPONSE`.

## Endpoint contract

### `GET /v1/health`

Request:

- no body
- Bearer authentication required

Successful response: HTTP `200`.

Observed shape:

```json
{
  "status": "ok",
  "agent": "0.1.0",
  "docker": "ok",
  "wireguardBinding": "10.77.0.1"
}
```

`status` is `ok` when the runtime list probe succeeds and `degraded` otherwise. The implementation uses `agent` for the Agent version; it does not currently emit a separate `version` field. The client must not invent one.

### `GET /v1/projects`

Request:

- no body
- Bearer authentication required

Successful response: HTTP `200`.

Observed shape:

```json
{
  "projects": [
    {
      "projectId": "daily-stock-analysis",
      "environmentState": "READY",
      "runtimeState": "STOPPED",
      "image": "siftalpha/daily-stock-analysis:m0-d3fee51"
    }
  ]
}
```

The public project projection does not expose source paths or runtime paths.

### `GET /v1/projects/{projectId}`

Request:

- no body
- `projectId` must satisfy the server registry identifier rule `[A-Za-z0-9._-]+`
- Bearer authentication required

Successful response: HTTP `200`, using the same single-project shape as the item in `GET /v1/projects`:

```json
{
  "projectId": "daily-stock-analysis",
  "environmentState": "READY",
  "runtimeState": "STOPPED",
  "image": "siftalpha/daily-stock-analysis:m0-d3fee51"
}
```

### `POST /v1/projects/{projectId}/prepare`

Request:

- no body
- Bearer authentication required

Successful response: HTTP `202`.

### `POST /v1/projects/{projectId}/start`

Request:

- no body
- Bearer authentication required

Successful response: HTTP `202`.

### `POST /v1/projects/{projectId}/stop`

Request:

- no body
- Bearer authentication required

Successful response: HTTP `202`.

The three mutation routes return the same initial operation projection:

```json
{
  "operationId": "opaque-operation-id",
  "projectId": "daily-stock-analysis",
  "action": "START",
  "state": "PENDING",
  "startedAt": null,
  "finishedAt": null,
  "exitCode": null,
  "failureReason": null,
  "result": null
}
```

`action` is one of `PREPARE`, `START`, `STOP`. `state` is one of `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`. A project cannot have more than one active `PENDING`/`RUNNING` destructive operation; the second request returns HTTP `409` with `OPERATION_CONFLICT`.

### `GET /v1/projects/{projectId}/status`

Request:

- no body
- Bearer authentication required

Successful response: HTTP `200` when the runtime status probe succeeds.

Observed minimum shape:

```json
{
  "projectId": "daily-stock-analysis",
  "environmentState": "READY",
  "runtimeState": "STOPPED",
  "containerId": null,
  "image": "siftalpha/daily-stock-analysis:m0-d3fee51",
  "exitCode": null,
  "oomKilled": null,
  "restartCount": null,
  "startedAt": null,
  "finishedAt": null
}
```

The server passes through the allowlisted runtime helper's JSON object. The client treats the fields above as nullable where the helper may omit them, but requires `projectId`, `environmentState`, and `runtimeState` for a valid Cloud status mapping.

### `GET /v1/projects/{projectId}/logs?tail={tail}`

Request:

- no body
- Bearer authentication required
- `tail` is an integer, defaults to `200`, and must satisfy `0 <= tail <= 1000`

Successful response: HTTP `200`.

Observed shape:

```json
{
  "projectId": "daily-stock-analysis",
  "lines": [],
  "bytes": 0,
  "truncated": false
}
```

This is a snapshot response. M1 does not define WebSocket or SSE logs.

### `GET /v1/operations/{operationId}`

Request:

- no body
- Bearer authentication required

Successful response: HTTP `200`, using the operation projection shown for mutation routes. The `result` field is nullable; a valid stored JSON object is returned as an object. If the stored result cannot be decoded, the server exposes `{ "raw": "..." }`.

Unknown operation returns HTTP `404` with `INVALID_REQUEST` in the current implementation.

## Client safety requirements

The M1 server snapshot does not publish a response body-size limit. The M2.1 client must impose a maximum response body for every endpoint, including success JSON, error JSON, and logs. Exceeding the limit must fail safely as `INVALID_RESPONSE` or a dedicated bounded-response transport error; it must never read indefinitely.

The client must map connection failure to `CLOUD_SERVER_UNREACHABLE` and timeout to `TIMEOUT`; neither may become a project runtime state. The client must not add public fallback URLs or manage WireGuard lifecycle.

## Protocol gaps intentionally not implemented in M2.1

- Cloud Import API
- Cloud Config Write API
- Cloud Secret Injection API
- Cloud Web Result API
- WebSocket/SSE log streaming
- multi-server scheduling

