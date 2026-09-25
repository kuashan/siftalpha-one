# SiftAlpha Platform Capability Contract（平台能力契约）

## Purpose（目的）

This contract prevents macOS（苹果桌面系统）, Windows（微软桌面系统） or Android（安卓系统） development from accidentally forcing platform-specific behavior into SiftAlpha Core（跨平台核心）.

## Hard rule（硬规则）

> Core（核心） may define what a capability means. Core（核心） must never assume that every platform implements that capability.

A new requirement discovered while developing one platform must be classified before code is changed.

## Classification（分类）

### 1. Universal product rule（通用产品规则）

If the rule is truly platform-independent and applies to every platform, it may live in Core（核心）.

Examples:

- Runtime lifecycle state transitions（运行生命周期状态转换）
- Environment Plan semantics（环境计划语义）
- Session / Generation rules（会话 / 代际规则）
- project-scoped STOP policy（项目级停止策略）

### 2. Same capability, different implementation（同一能力，不同实现）

Core（核心） defines only the capability/interface. Each platform implements it independently.

Example:

- Core asks for secure secret storage（安全密钥存储）
- Android uses Android Keystore（安卓密钥库）
- macOS uses Keychain（苹果钥匙串）
- Windows uses Credential Manager（Windows 凭据管理器）

### 3. Platform-only feature（平台专属功能）

It must stay in that platform adapter and must not be added to Core（核心） merely because one platform needs it.

Examples:

- macOS-only integration（苹果平台专属集成）
- Windows-only WSL2 integration（Windows Linux 子系统集成）
- Android-only Foreground Service（安卓前台服务）

### 4. Optional cross-platform capability（可选跨平台能力）

A capability may exist on some platforms and not others.

Examples:

- Container Runtime（容器运行时）
- Linux Compatibility Layer（Linux 兼容层）
- Virtual Machine Runtime（虚拟机运行时）

Core（核心） may reason about these capabilities only after querying platform availability.

## Availability semantics（可用性语义）

Every optional capability is one of:

- AVAILABLE（可用）
- UNAVAILABLE（不可用）
- UNKNOWN（未知）

UNKNOWN（未知） and UNAVAILABLE（不可用） are valid platform facts, not architecture errors.

A missing capability must never be interpreted as AVAILABLE（可用）.

## Forbidden pattern（禁止模式）

Do not write platform branching inside Core（核心） such as:

```
if (platform == macOS) ...
else if (platform == Windows) ...
else if (platform == Android) ...
```

Do not add a macOS（苹果） requirement to Core（核心） and then force Android（安卓） or Windows（微软） to implement a meaningless stub.

Do not copy Core（核心） into three permanent platform forks.

## Required pattern（要求模式）

```
Core（核心）
    |
    +-- declares capability / rule
    |
Platform Adapter（平台适配层）
    |
    +-- reports AVAILABLE / UNAVAILABLE / UNKNOWN
    +-- implements the platform-specific mechanism when available
```

## Change gate（修改门禁）

Before any platform-driven Core（核心） change is accepted, answer these questions:

1. Is this a universal product rule（通用产品规则）?
2. If not, is it the same abstract capability with a different platform implementation?
3. If not, keep it platform-only and do not modify Core（核心）.
4. Can Android（安卓）, macOS（苹果） and Windows（微软） independently report capability availability?
5. Does an unavailable capability remain a valid state instead of causing unrelated platform failure?
6. Have Core tests（核心测试） and existing Android regression tests（安卓回归测试） passed?

If any answer is unclear, the change must not be treated as a Core（核心） requirement yet.

## Current code guard（当前代码约束）

`:core` provides:

- `PlatformCapability`
- `CapabilityAvailability`
- `PlatformCapabilitySnapshot`
- `StandardPlatformCapabilities`

The initial standard capability identifiers are intentionally optional:

- host process execution（主机进程执行）
- secure secret storage（安全密钥存储）
- container runtime（容器运行时）
- Linux compatibility layer（Linux 兼容层）
- virtual machine runtime（虚拟机运行时）

Adding an identifier does not mean all platforms must implement it.


## Android Reference First（安卓成熟实现优先）— 2026-09-25 冻结

从本条开始，Android（安卓）是跨平台开发的 **Reference Implementation（成熟参考实现）**，不是一个与 macOS（苹果）平行、互不参考的历史产品。

任何 macOS / Windows（苹果 / 微软）新增能力或故障修复，在提出新设计前必须先执行：

1. 查找 Android（安卓）是否已经处理过同类问题；
2. 提取 Android 已真机验证的 invariant / behavior contract（不变量 / 行为契约），不得只复制平台代码；
3. 判断该契约属于 Core（共享核心）、optional capability（可选能力）还是 Platform Adapter（平台适配层）；
4. 已属于 Core 的规则必须直接复用 Core，禁止在 macOS / Windows 再实现第二套策略；
5. 平台专属机制只留在 Adapter，但必须实现同一行为契约；
6. 只有 Android 不存在成熟答案、且问题确属新平台特有时，才允许提出新的平台方案。

禁止模式新增：

- 未审查 Android 成熟实现就直接为 macOS 发明新的生命周期、准备、恢复、网络、STOP、Web Discovery、状态持久化或环境身份规则；
- 为了修复单台设备/单个网络，把平台临时 workaround（绕行方案）升级成 Core 规则；
- 让 Android、macOS、Windows 名义上共享 Core，实际各自维护第二套行为语义。

开发顺序冻结为：

`Android 成熟事实 → 通用行为契约 → Core / Capability 分类 → 平台 Adapter 实现 → 跨平台回归`。
