# SiftAlpha Cloud Manager M2.1 Design

**Status:** Approved for implementation

**Baseline:** `origin/codex/r48d11-external-runtime-baseline-python-resolver` at `b2650a957ca8ad7458ae8f911cd8fb5487d95a83`

**Branch:** `codex/cloud-manager-m2`

## Goal

建立 Cloud Manager Foundation：可跨平台复用的 Cloud domain、M1 Agent API client、操作轮询、错误模型、凭据存储接口和测试基础，同时保持现有 Android 用户流程与 Legacy Runtime 完全不变。

Cloud domain 的时间字段统一使用 `java.time.Instant`；M1 wire DTO 可按真实协议使用 `String?` 或 `Long?`，但 DTO mapper 必须转换为 domain `Instant?`。

## Product Boundary

目标架构为：

`Single UI → Cloud Manager → Cloud Agent Client → WireGuard 已建立的网络路径 → SiftAlpha Agent → Docker Runtime`

WireGuard 不属于 SiftAlpha 管理范围。应用只访问配置中的 Cloud Server URL；不可达时返回 `CLOUD_SERVER_UNREACHABLE`，不得公网 fallback。

本轮不接入首页、Project Card、Normal Workspace、Developer Mode、Prepare/Run/Stop/Logs UI、Result Viewer、Cloud Import、Cloud Config Write、Cloud Secret Injection、Cloud Web Result、多服务器调度或任何 M2.2 行为。

以下 Legacy Runtime 冻结且不得修改或调用：Internal Runtime、External Runtime、Termux、PRoot、Embedded CPython、Internal Alpine、Runtime Center、Developer Mode、Local Agent、Local Web Discovery、PID/PGID、ResultBus、旧 Runtime lifecycle。

## Module Architecture

### `:cloud-core`

纯 Kotlin/JVM module，不依赖 Android SDK、Activity、Compose、Termux、PRoot、Docker 或现有 Runtime。

职责：

- Cloud domain model 与状态枚举；
- `CloudProjectActionPolicy`；
- Cloud error model；
- operation repository interface；
- DTO-independent mapping contracts；
- domain time values represented as `Instant`。

### `:cloud-agent-client`

平台无关 Kotlin/JVM library，仅依赖 `:cloud-core` 与标准 Kotlin/JVM 能力。不得依赖 Android SDK、Activity、Compose、旧 Runtime Controller、Termux 或 PRoot。

职责：

- M1 API DTO；
- DTO → Cloud domain mapping；
- `CloudHttpTransport` 抽象与默认 HTTP 实现；
- `SiftAlphaCloudAgentClient`；
- HTTP 状态、连接失败、超时和 JSON 错误分类；
- `CloudOperationPoller`。

凭据边界：`cloud-agent-client` 只定义平台无关的 `CloudCredentialStore` 接口，不持有 Android Keystore 具体实现，也不依赖 Android SDK。

### `:app` Cloud Android integration

本轮不建立 `:cloud-feature`。在现有 `:app` 中增加最小 Cloud Android integration，仅实现 `CloudCredentialStore` 的 Android 适配：

- Android Keystore 保存 AES key；
- AES/GCM 加密 Bearer Token；
- SharedPreferences 仅保存密文和非敏感 server metadata；
- 独立 Cloud credential namespace；
- 不复用或改造 `ProjectSecretStore` 的业务 namespace。

该 integration 不接入任何现有首页或运行控制流程。

## Domain Model

### `CloudServer`

- `serverId`
- `displayName`
- `baseUrl`
- `connectionState`

不包含 WireGuard key、SSH key、Oracle private key 或 OCI API private key。

### `CloudProject`

- `localProjectId: String?`
- `displayName`
- `description`
- `serverId`
- `remoteProjectId`
- `source`
- `runtimeKind`
- `environmentState`
- `runtimeState`
- `lastOperationId: String?`
- `result: CloudResult?`
- `createdAt: Instant?`
- `updatedAt: Instant?`

### `CloudOperation`

- `operationId`
- `projectId`
- `action`
- `state`
- `startedAt: Instant?`
- `finishedAt: Instant?`
- `exitCode: Int?`
- `failureReason: String?`

Actions: `PREPARE`, `START`, `STOP`。

Statuses: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`。

Runtime states: `READY`, `RUNNING`, `STOPPED`, `EXITED`, `UNKNOWN`。

Environment states: `READY`, `NOT_READY`, `UNKNOWN`。

Connection states: `UNKNOWN`, `CONNECTING`, `CONNECTED`, `UNREACHABLE`, `UNAUTHORIZED`, `ERROR`。

### Result model

只定义模型，不实现 Result Viewer。

`CloudResult` 包含 `remoteProjectId`、`operationId`、`runtimeState`、`environmentState`、可选 `webEndpoint`、`artifacts`、可选 `summary`、可选 `errorCode`、可选 `errorMessage`、`updatedAt: Instant?`。

`CloudArtifact` 包含 `name`、`type`、可选 `url`、可选 `size`、`metadata`。

Cloud result 不得携带 Android local file path、localhost URL 或 `ResultWebStore` ID。

## Action Policy

`CloudProjectActionPolicy` 是独立纯函数策略，只读取 Cloud domain facts：

- `NOT_READY → PREPARE`；
- `READY + STOPPED → START`；
- `RUNNING → STOP`；
- `PENDING/RUNNING operation → 禁止重复 destructive action`；
- `FAILED → 允许 Retry 或 Refresh，具体由服务器事实决定`。

策略不得读取 PID、`executionId`、Termux state、本地 lifecycle file 或旧 `ProjectActionPolicy`。

## M1 Agent API Contract

The authoritative M1 field/status record for M2.1 is maintained in [`docs/cloud-agent/m1-api-contract.md`](../cloud-agent/m1-api-contract.md). DTO work must use that document and the reconciled M1 implementation, not memory or an inferred JSON shape.

只适配已经存在的 M1 endpoint：

- `GET /v1/health`
- `GET /v1/projects`
- `GET /v1/projects/{projectId}`
- `POST /v1/projects/{projectId}/prepare`
- `POST /v1/projects/{projectId}/start`
- `POST /v1/projects/{projectId}/stop`
- `GET /v1/projects/{projectId}/status`
- `GET /v1/projects/{projectId}/logs?tail=`
- `GET /v1/operations/{operationId}`

DTO 与 domain model 分离。UI 或未来 Cloud Manager 不直接消费 JSON DTO。

修改型请求的 client 方法返回 operation reference；长操作由 `CloudOperationPoller` 查询 operation endpoint，不保持长时间 HTTP request。

## Client API

`SiftAlphaCloudAgentClient` 至少提供：

- `health()`
- `listProjects()`
- `getProject(projectId)`
- `getStatus(projectId)`
- `prepare(projectId)`
- `start(projectId)`
- `stop(projectId)`
- `logs(projectId, tail)`
- `getOperation(operationId)`

所有请求必须具备 timeout、bounded response、JSON validation、HTTP status mapping 和错误分类。所有 HTTP response（成功 JSON、错误 JSON、logs 和任意未知 endpoint response）都必须有最大 body limit；超限必须安全拒绝或映射为 `INVALID_RESPONSE`，不得无限读取。请求使用 `Authorization: Bearer <token>`；日志只允许记录 serverId、endpoint path、HTTP status 和 elapsed time，不得记录完整 header 或 Token。

默认 HTTP transport 通过可注入接口实现，单元测试使用 fake transport，不要求真实服务器。

## Error Model

至少定义：

`CLOUD_SERVER_UNREACHABLE`, `UNAUTHORIZED`, `PROJECT_NOT_FOUND`, `OPERATION_CONFLICT`, `ENVIRONMENT_NOT_READY`, `ALREADY_RUNNING`, `ALREADY_STOPPED`, `DOCKER_ERROR`, `PREPARE_FAILED`, `RUNTIME_ERROR`, `INVALID_REQUEST`, `INVALID_RESPONSE`, `TIMEOUT`, `UNKNOWN`。

HTTP 401 映射 `UNAUTHORIZED`；连接失败映射 `CLOUD_SERVER_UNREACHABLE`；超时映射 `TIMEOUT`。网络错误不得映射为项目 stopped 或 failed。

## Operation Polling

`CloudOperationPoller` 接收 operationId，通过 `GET /v1/operations/{operationId}` 轮询，直到 `SUCCEEDED`、`FAILED` 或 `CANCELLED`，或达到最大期限。

轮询必须有：

- 最大期限；
- 可配置间隔；
- cancellation 支持；
- cancellation 时抛出明确的 `CancellationException`；取消不等于 operation 已完成，不能返回最后一次状态冒充终态；
- 明确的 terminal state 判断；
- `OperationRepository` 接口预留持久化；
- 不允许无限循环。

M2.1 不要求 Android operation 持久化实现，但不把架构锁死在内存状态。

## Credential Contract

平台无关接口 `CloudCredentialStore` 由 `cloud-agent-client` 定义，至少支持按 `serverId` 保存、读取和删除 `baseUrl` / Bearer Token credential。

Android 具体实现位于 `:app` Cloud Android integration，使用 Android Keystore + AES/GCM。Bearer Token 不得进入源码、Git、BuildConfig、测试 fixture、日志或报告。WireGuard private key、SSH key、OCI API private key 不进入 App。

## Tests

`:cloud-core`：

- state mapping；
- action policy；
- operation state；
- error model。

`:cloud-agent-client`：

- M1 JSON decode；
- DTO/domain mapping；
- 401、404、409、500；
- connection failure；
- timeout；
- invalid JSON；
- bounded logs；
- bounded success JSON and error JSON；
- terminal operation；
- poller timeout；
- cancellation throws `CancellationException`；
- Authorization 脱敏。

`:app` integration：

- credential round-trip；
- ciphertext-only persistence；
- wrong/rotated key failure不泄漏明文；
- Cloud credential namespace 不与 Project Secret namespace 混用。

## Build and Compatibility

- 更新 Gradle settings 加入新模块；
- 不无意义升级 Kotlin、AGP、Compose 或依赖；
- 不改变现有 Android versionName；
- 如 CI 强制要求 versionCode，仅按现有规则最小递增；
- 现有 Android compile 和重要单元测试必须保持通过；
- 正式 Compile、Unit Test、Regression Test、Android Build 只由 GitHub Actions 执行；本地 Gradle 不构成正式 PASS；
- 新 Cloud modules 不进入现有 Android 主流程；
- 不修改 `r48d11` 原分支历史。

## Documentation

更新开发文档，明确：

- `M2.0R=CLOSED`；
- `M2.1=current phase`；
- Cloud Manager 是独立架构；
- Agent 是服务器事实源；
- WireGuard 不由 SiftAlpha 管理；
- Cloud Manager 不依赖旧 Runtime Provider；
- Legacy Runtime 在本轮冻结。

缺失的 M1 能力记录到 `M2_PROTOCOL_GAPS.md`，不自行发明 API。M1 contract 文档必须记录 request shape、success response、error envelope、HTTP status、nullable fields 和 timestamp format。

GitHub Actions 必须为 `codex/cloud-manager-m2` 提供真实触发路径，显式执行 `:cloud-core:test`、`:cloud-agent-client:test`、现有 Android unit/regression tests、Android compile 和 `assembleDebug`/W0 equivalent，并执行 Cloud-to-Legacy architecture guard。

## Out of Scope

本轮禁止：修改服务器、Agent、WireGuard、OCI；删除或修复 Legacy Runtime；接入 Normal UI；Cloud Web Result；Cloud Import；Cloud Secret Injection；多服务器调度；网站；数据库；M2.2。

## Acceptance Criteria

M2.1 仅在以下条件全部通过后标记 `M2_1_CODE=CLOSED`：

1. 分支从指定 r48d11 HEAD 建立；
2. `:cloud-core` 与 `:cloud-agent-client` 存在且边界成立；
3. `CloudCredentialStore` 为平台无关接口；
4. Android Keystore 实现只存在于 `:app` integration；
5. M1 DTO 与 client 方法完整；
6. error mapping 和 operation polling 测试通过；
7. Token 未泄漏，WireGuard key 未进入 App；
8. Legacy Runtime 无修改、无依赖；
9. 现有 Android compile 与重要回归测试通过；
10. Cloud module 单元测试通过。

正式验证全部以 GitHub Actions 结果为准。若 Android Keystore instrumentation 无可用 emulator，必须记录 `ANDROID_KEYSTORE_TEST=BLOCKED`，此时不得标记 `M2_1_FULLY_CLOSED`。

如果 WireGuard 不可用，真实 Agent read-only smoke 标记 `M2_1_REMOTE_SMOKE=BLOCKED`，不能伪造 PASS。实现完成后停止，不进入 M2.2。
