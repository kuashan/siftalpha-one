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

## 2026-09-23 · macOS Master Development Roadmap（macOS 总开发路线）冻结

### 目的

为了避免 macOS（苹果桌面系统）开发过程中失去方向、重复工作、跳过关键阶段或为了单个平台破坏 Shared Core（共享核心），正式冻结 M0～M8 总开发路线。

### 总路线

1. M0 — Governance（开发治理）
   - Current State（当前状态）
   - Dev Log（开发日志）
   - Test Matrix（测试矩阵）
   - Capability Isolation（能力隔离）

2. M1 — Core Boundary（核心边界）
   - Runtime Lifecycle（运行生命周期）
   - Environment Plan（环境计划）
   - Project Operation（项目操作）
   - Platform Storage Interface（平台存储接口）
   - Project Filesystem Interface（项目文件系统接口）
   - Process Control Interface（进程控制接口）
   - Core Boundary Audit（核心边界总审计）

3. M2 — macOS Host Skeleton（macOS 主机骨架）
   - 建立真正可运行的 macOS App（苹果桌面应用）
   - 加载 SiftAlpha Core（跨平台核心）
   - 读取 Platform Capability Snapshot（平台能力快照）
   - 不要求 Android（安卓）库参与 macOS（苹果）构建

4. M3 — Host Runtime Provider（主机运行提供者）
   - 发现 Python / Node.js / Bun / Git / Shell（Python / Node / Bun / Git / 命令环境）
   - 启动、观察、停止本机进程
   - 建立 Project Ownership（项目归属）
   - STOP（停止）只影响当前项目

5. M4 — Project Workflow（项目工作流）
   - Import（导入）
   - Detect（检测）
   - Environment Plan（环境计划）
   - Prepare（准备）
   - Run（运行）
   - Status / Logs（状态 / 日志）
   - Web Discovery / Endpoint Probe（网页发现 / 端点探测）
   - Stop / Restart（停止 / 重启）

6. M5 — Product UI Parity（产品界面对齐）
   - Normal Mode（普通用户模式）
   - Developer Mode（开发者模式）
   - 两个界面共享同一个 Core（核心）和 Runtime State（运行状态）

7. M6 — Container / Multi-service（容器 / 多服务）
   - Docker / Podman（容器）
   - Compose（多服务编排）
   - Bun workspace（Bun 工作区）
   - 多服务生命周期、日志与端口归属

8. M7 — OpenBot Acceptance（OpenBot 验收）
   - 使用原始 OpenBot（开放机器人）项目
   - 不修改 OpenBot（开放机器人）源码适配 SiftAlpha
   - 作为完整复杂项目验收，不建立 OpenBot-specific patch（OpenBot 专属补丁）

9. M8 — Distribution（正式分发）
   - macOS application bundle（苹果应用包）
   - Developer ID signing（开发者身份签名）
   - Hardened Runtime（强化运行时）
   - Notarization（苹果公证）
   - DMG / PKG（磁盘映像 / 安装包）
   - clean install / upgrade（全新安装 / 升级）
   - crash recovery（崩溃恢复）

### 每轮固定工作流程

每一轮 macOS（苹果）开发必须按以下顺序执行：

```
读取 MACOS_CURRENT_STATE（当前状态）
        ↓
读取 MACOS_DEV_LOG（开发日志）
        ↓
读取 MACOS_TEST_MATRIX（测试矩阵）
        ↓
读取 PLATFORM_CAPABILITY_CONTRACT（平台能力契约）
        ↓
确定本轮唯一目标
        ↓
审查现有源码
        ↓
设计修改范围
        ↓
实现
        ↓
Core Tests（核心测试）
        ↓
macOS Build / Test（苹果构建 / 测试）
        ↓
如果修改 Core（核心）
        ↓
Android Regression（安卓回归）
        ↓
真实 Mac 验收（进入平台阶段后）
        ↓
更新 Current State（当前状态）
        ↓
更新 Dev Log（开发日志）
        ↓
更新 Test Matrix（测试矩阵）
```

### 阶段推进规则

- 不允许因为后续阶段“看起来更有成果”而跳过当前阶段。
- 每个阶段必须满足对应 Test Matrix（测试矩阵）后才能标记完成。
- 某个 macOS（苹果）平台专属需求不能自动升级为 Core（核心）必备能力。
- OpenBot（开放机器人）只作为 M7（第七阶段）验收项目，不提前写特殊适配。
- 分发问题从 M2（应用骨架）开始持续考虑，但正式签名、公证、安装验收归 M8（正式分发）。
