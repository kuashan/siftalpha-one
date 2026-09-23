# SiftAlpha Cross-Platform Core（跨平台核心）

## Status（状态）

This document defines the first extraction boundary for macOS（苹果桌面系统） and Windows（微软桌面系统） support.

Starting point:

- source branch: `codex/r48-shared-core-realignment`
- accepted Android（安卓） line: r48d10
- extraction branch: `feature/cross-platform-core`
- first installable extraction version: `0.8.0-alpha43-r48d11-core1` / versionCode `223`

## Architecture rule（架构规则）

SiftAlpha is not split into three independent products.

The target is:

```
SiftAlpha Core（跨平台核心）
        |
        +-- Android Platform Adapter（安卓平台适配层）
        +-- macOS Platform Adapter（苹果平台适配层）
        +-- Windows Platform Adapter（微软平台适配层）
```

Normal Mode（普通模式） and Developer Mode（开发者模式） remain two presentation surfaces over one shared management/runtime contract.

## Core owns（核心负责）

Platform-independent facts and policy belong in `:core` when they can compile without Android（安卓） APIs:

- Runtime classification（运行时分类）
- Environment Detection / Environment Plan（环境检测 / 环境计划） contracts
- Runtime lifecycle（运行生命周期） contracts
- Session / Generation（会话 / 代际） rules
- project-scoped operation policy（项目级操作策略）
- launch/observation policy（启动 / 观察策略）
- result interpretation（结果解释）
- provider-neutral capability models（提供者中立能力模型）

## Platform adapters own（平台适配层负责）

Platform APIs stay outside `:core`:

- Android Context / Service / Intent / SAF / SharedPreferences
- macOS process / filesystem / Keychain / background integration
- Windows process / filesystem / Credential Manager / Job Object / WSL integration
- platform-specific UI（平台专属界面）
- platform process launching / termination（平台进程启动 / 终止）
- platform secure storage（平台安全存储）

## First extraction slice（第一批抽取）

The first production-used model moved into `:core` is `RuntimeKind` plus `RuntimeCandidate` and `ProjectRuntimeProfile`.

This slice deliberately does not change:

- Runtime selection behavior（运行环境选择行为）
- Android UI（安卓界面）
- Embedded CPython（内嵌 CPython）
- Internal Alpine（内部 Alpine）
- External Provider / Termux（外部运行提供者 / Termux）
- Shared External Action Gate（共享外部操作门禁）
- project-scoped STOP（项目级停止）
- Web Discovery / Endpoint Probe（网页发现 / 端点探测）

## Migration strategy（迁移策略）

1. Extract pure models/policies into `:core` without behavior change.
2. Introduce explicit platform interfaces only when an existing Android dependency blocks extraction.
3. Keep Android（安卓） as the reference implementation while extraction proceeds.
4. Add macOS（苹果桌面系统） Host Provider（主机提供者） after the core boundary is stable.
5. Add Windows（微软桌面系统） Host Provider（主机提供者）, including WSL2（Windows Linux 子系统） integration where appropriate.
6. Never fork the business core into independent macOS / Windows copies.

## Capability isolation rule（能力隔离规则）

Platform-driven work must follow `docs/PLATFORM_CAPABILITY_CONTRACT.md`.

Core（核心） is not a container for every feature discovered on macOS（苹果桌面系统） or Windows（微软桌面系统）. A platform-specific requirement must remain in its platform adapter unless it is a genuine shared rule or a provider-neutral capability contract.

Core（核心） may ask whether a capability exists; it must not assume all platforms implement it. AVAILABLE（可用）, UNAVAILABLE（不可用） and UNKNOWN（未知） are all valid capability states.

This rule is specifically intended to prevent future macOS（苹果） or Windows（微软） development from breaking Android（安卓） by turning platform-only behavior into a mandatory shared-core requirement.

## Verification rule（验证规则）

Every extraction slice must keep the Android（安卓） baseline buildable and must run:

- `:core:test`
- existing Android unit tests（安卓单元测试）
- `assembleDebug`（调试包构建）
- existing APK evidence / stable signing（安装包证据 / 稳定签名）
- Internal Alpine Probe（内部 Alpine 探针） whenever its build inputs are touched

## macOS development records（macOS 开发记录）

macOS（苹果桌面系统）平台工作的固定入口：

- `MACOS_CURRENT_STATE.md` — 当前状态
- `MACOS_DEV_LOG.md` — 开发日志
- `MACOS_TEST_MATRIX.md` — 测试矩阵

macOS（苹果）工作不得只依赖聊天上下文。每轮状态变化、架构决定、测试证据和下一步都必须写回这些文件。

## Environment needs boundary（环境需求边界）

M1.2（环境计划）建立 `ProjectEnvironmentNeeds` 作为 Core（核心）的 provider-neutral（提供者中立）需求模型。

Core（核心）回答“项目需要什么”，例如 Python / Node.js / Vite / dependency preparation（Python / Node / Vite / 依赖准备）。

Platform Adapter（平台适配层）回答“当前平台怎样满足”，例如 Android Embedded R（安卓内部运行时）、macOS Host Runtime（苹果主机运行时）、Windows WSL2（Windows Linux 子系统）或 Container Provider（容器提供者）。

Platform-specific backend selection（平台专属后端选择）不得进入 Core（核心）成为所有平台的强制实现。

## Platform state storage boundary（平台状态存储边界）

M1.4（平台存储接口）建立 `PlatformStateStorage` 作为跨平台应用私有状态持久化端口。

Core（核心）只定义状态值与读写/删除契约，不依赖 Android SharedPreferences（安卓偏好存储）或任何桌面平台持久化 API（接口）。

Android（安卓）当前通过 `AndroidSharedPreferencesStateStorage` 实现该端口；macOS（苹果）与 Windows（微软）后续各自提供平台实现。

Runtime Lifecycle / Runtime Operation（运行生命周期 / 运行操作）已通过该端口持久化。Android SAF（安卓存储访问框架）项目文件访问不属于本端口，由 M1.5（项目文件系统接口）处理。
