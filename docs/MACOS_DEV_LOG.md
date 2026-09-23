# SiftAlpha macOS Development Log（macOS 开发日志）

> 只记录 macOS（苹果桌面系统）开发和跨平台 Core（核心）变化。
> Android（安卓）历史开发仍记录在 `DEV_LOG.md`。
> 当前真实状态以 `MACOS_CURRENT_STATE.md` 为准。

---

## 2026-09-23 · M0 — Development Governance（开发治理）

### 背景

决定开始为 SiftAlpha（筛选阿尔法）建立 macOS（苹果桌面系统）版本。

不采用“复制 Android（安卓）源码后独立发展”的方案。

正式方向为：

```
SiftAlpha Core（跨平台核心）
        |
        +-- Android Platform Adapter（安卓平台适配层）
        +-- macOS Platform Adapter（苹果平台适配层）
        +-- Windows Platform Adapter（微软平台适配层）
```

### 已完成的前置工作

- 建立 `feature/cross-platform-core` 分支。
- 建立 Kotlin/JVM（Kotlin/JVM 平台） `:core` 模块。
- 第一批迁移 `RuntimeKind`、`RuntimeCandidate`、`ProjectRuntimeProfile`。
- 建立 `PLATFORM_CAPABILITY_CONTRACT.md`。
- 建立 CapabilityAvailability（能力可用状态）：AVAILABLE（可用） / UNAVAILABLE（不可用） / UNKNOWN（未知）。
- 明确平台专属能力不得因为 macOS（苹果）需要而变成 Android（安卓）强制依赖。
- W0 Cloud Build（W0 云端构建） #672：PASS（通过）。
- Internal Alpine Probe（内部 Alpine 探针） #114：PASS（通过）。
- Android APK（安卓安装包） versionCode `224` 回归构建：PASS（通过）。

### 本轮新增 macOS（苹果）治理文件

- `docs/MACOS_CURRENT_STATE.md`
- `docs/MACOS_DEV_LOG.md`
- `docs/MACOS_TEST_MATRIX.md`

### 当前结论

macOS（苹果）开发尚未进入平台代码阶段。

当前必须先继续稳定 Cross-Platform Core（跨平台核心）边界，再建立 macOS Platform Adapter（苹果平台适配层）。

### 下一步

M1 — Core Boundary（核心边界）：
审查并逐步抽取 Runtime Lifecycle（运行生命周期）、Environment Plan（环境计划）和 Project Operation（项目操作）中的平台无关部分。
