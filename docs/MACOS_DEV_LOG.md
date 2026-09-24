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

## 2026-09-23 · M1.1 — Runtime Lifecycle（运行生命周期）抽取完成

### 目标

把真正平台无关的 Runtime Lifecycle（运行生命周期）事实解析和状态决策从 Android（安卓）模块下沉到 SiftAlpha Core（跨平台核心），同时保持现有 Android（安卓）接口和行为不变。

### 审查结论

原有生命周期代码混合了两类职责：

1. 平台无关：
   - Runtime output parsing（运行时输出解析）
   - Runtime execution state（运行执行状态）
   - project lifecycle decision（项目生命周期决策）
   - operation priority（操作优先级）
   - recovery precedence（恢复优先级）

2. Android（安卓）专属：
   - `Context`
   - `R.string`
   - UI label（界面文案）
   - Android persistence（安卓持久化）

因此本轮不迁移 Android UI（安卓界面）与持久化，只迁移纯生命周期规则。

### 实现

新增 Core（核心）文件：

- `core/src/main/kotlin/com/siftalpha/core/lifecycle/RuntimeLifecycleCore.kt`

Core（核心）现负责：

- `RuntimeExecutionState`
- `ProjectLifecycleState`
- `ProjectLifecycleOperation`
- `RuntimeLifecyclePolicy`
- `RuntimeOutputStateParser`

Android（安卓）现有：

- `RuntimeState`
- `RuntimeLifecycleState`
- `RuntimeLifecycleOperation`
- `RuntimeLifecycleResolver`

继续保留为 compatibility facade（兼容外壳），但状态解析和生命周期决策已经委托给 `:core`。

因此现有 Android（安卓）调用方、UI（界面）、持久化和 Runtime（运行时）执行路径无需同步重写。

### 行为保持

本轮没有改变：

- START / STARTING（启动 / 启动中）语义
- RUNNING（运行中）语义
- STOP / STOPPING / STOPPED（停止 / 停止中 / 已停止）语义
- EXITED_SUCCESS / EXITED_ERROR（正常结束 / 异常结束）语义
- PREPARING（准备中）语义
- RECOVERING（恢复中）优先级
- project-scoped STOP（项目级停止）
- Embedded CPython（内嵌 CPython）
- Internal Alpine（内部 Alpine）
- External Provider / Termux（外部运行提供者 / Termux）
- Normal Mode / Developer Mode（普通模式 / 开发者模式）

### 云端验证

- W0 Cloud Build（W0 云端构建） #677 / run ID `35813725333`: **PASS（通过）**
- Internal Alpine Probe（内部 Alpine 探针） #115: **PASS（通过）**
- `:core:test`: **PASS（通过）**
- Android unit tests（安卓单元测试）: **PASS（通过）**
- `assembleDebug`（安卓调试包构建）: **PASS（通过）**
- versionCode: `225`
- versionName: `0.8.0-alpha43-r48d11-m1.1`
- APK SHA-256: `43619a6f3aed4d960fc2efdfb99804faf1804f0d69d00da42f15c2f60944cf67`
- signer SHA-256: `3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`
- artifact（云端制品）: `siftalpha-w0-677`, ID `10731002876`

### M1.1 结论

**PASS（通过）**

Runtime Lifecycle（运行生命周期）的第一层生产逻辑已经进入 SiftAlpha Core（跨平台核心），Android（安卓）回归通过。

### 下一步

**M1.2 — Environment Plan（环境计划）**

下一轮审查现有 Environment Detection / Environment Plan（环境检测 / 环境计划），区分：

- 平台无关的“项目需要什么”
- 平台专属的“当前平台如何满足这些需要”

目标是让 Core（核心）表达需求，而 Android / macOS / Windows Platform Adapter（安卓 / 苹果 / 微软平台适配层）各自决定实现方式。

### M1.1 Real Device Acceptance（真机验收）

用户已在 Android（安卓）真实设备完成 v225 / `0.8.0-alpha43-r48d11-m1.1` 回归测试。

已验证：

- Internal Environment（内部环境）准备流程：PASS（通过）
- External Environment（外部环境）准备流程：PASS（通过）
- 从开始准备到准备结束全过程：PASS（通过）
- 准备完成后可正常打开/继续操作：PASS（通过）
- 与此前 Android Baseline（安卓基线）相比，当前未发现功能差异：PASS（通过）
- 当前用户实际测试范围内未发现回归：PASS（通过）

结论：

**M1.1 Runtime Lifecycle（运行生命周期）Real Device Acceptance（真机验收）= PASS（通过）。**

因此 M1.1（第一小阶段）正式关闭，后续开发进入 M1.2 — Environment Plan（环境计划）。

## 2026-09-23 · M1.2 — Environment Plan（环境计划）Core Extraction（核心抽取）

### 目标

把“项目需要什么环境”与“某个平台如何满足这些环境需求”分离。

### 审查结论

原有 `ProjectEnvironmentPlan.kt` 同时包含：

1. Project Needs（项目需求）
   - primary runtime（主运行时）
   - supplemental runtime（补充运行时）
   - dependency count（依赖数量）
   - Python version requirement（Python 版本要求）
   - optional dependency groups（可选依赖组）
   - Vite components（Vite 组件）
   - preparation sequence（准备顺序）

2. Android（安卓）当前 provider/backend（提供者 / 后端）决策
   - Embedded CPython（内嵌 CPython）
   - Internal Alpine（内部 Alpine）
   - External Provider / Termux（外部运行提供者 / Termux）

3. Android（安卓）当前兼容性实现
   - Embedded CPython compatibility（内嵌 CPython 兼容性）
   - Internal Alpine fallback（内部 Alpine 回退）
   - 当前外部 Provider（提供者）可用性

M1.2（环境计划）明确：第 1 类进入 Core（核心）；第 2、3 类不能因为 macOS（苹果）开发而被强行写进 Core（核心）。

### 实现

新增：

- `core/src/main/kotlin/com/siftalpha/core/environment/ProjectEnvironmentNeeds.kt`
- `core/src/test/kotlin/com/siftalpha/core/environment/ProjectEnvironmentNeedsTest.kt`

Core（核心）新增通用模型：

- `ProjectEnvironmentNeeds`
- `EnvironmentPreparationStep`
- `ProjectEnvironmentNeedPolicy`

Core（核心）现在能够表达：

- 项目是否需要 Python（Python 运行时）
- 项目是否需要 Node.js（Node 运行时）
- 是否需要 Node install / build（Node 安装 / 构建）
- 是否需要 Python install（Python 依赖安装）
- Python 版本要求
- Python optional groups（Python 可选依赖组）
- 直接依赖数量
- 是否存在阻塞性检测问题
- provider-neutral preparation steps（提供者中立的准备步骤）

Core（核心）**不知道也不选择**：

- Embedded CPython（内嵌 CPython）
- Internal Alpine（内部 Alpine）
- Termux（外部终端）
- macOS Host Runtime（苹果主机运行时）
- Windows WSL2（Windows Linux 子系统）
- Docker（容器）具体实现

Android（安卓）的 `ProjectEnvironmentPlanner` 继续选择当前 Android（安卓）后端，但通用的准备步骤与 Python install extras（Python 安装额外组）已经委托给 Core（核心）。

### 行为保持

现有 Android（安卓）以下行为未改变：

- pure Python（纯 Python）仍可优先 Embedded CPython（内嵌 CPython）
- Python + Vite（Python + Vite）仍先执行 Node install / build（Node 安装 / 构建），再执行 Python install（Python 安装）
- Node.js（Node 运行时）项目的现有准备顺序不变
- blocking detection issue（阻塞检测问题）仍只执行 VALIDATE_PLAN（验证计划）
- Android backend selection（安卓后端选择）未迁入 Core（核心）
- Environment Plan ID（环境计划标识）与现有 Android（安卓）行为保持由现有回归测试验证

### 云端验证

- code/build SHA: `565a002f50b8330e875c023fb81328264deccdf7`
- W0 Cloud Build（W0 云端构建） #681 / run ID `35816048625`: **PASS（通过）**
- Internal Alpine Probe（内部 Alpine 探针） #116: **PASS（通过）**
- `:core:test`: **PASS（通过）**
- Android unit tests（安卓单元测试）: **PASS（通过）**
- `assembleDebug`（安卓调试包构建）: **PASS（通过）**
- versionCode: `226`
- versionName: `0.8.0-alpha43-r48d11-m1.2`
- APK SHA-256: `049e74acd8a85420f373d32464a4473947c50e2bc0b32e19e649044ce8963fca`
- signer SHA-256: `3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`
- artifact（云端制品）: `siftalpha-w0-681`, ID `10731741579`

### 当前状态

**M1.2 Cloud PASS（云端通过），Real Device Acceptance（真机验收）待完成。**

真机验收通过后才正式关闭 M1.2（环境计划）。

## 2026-09-23 · Stage Exit Contract（阶段退出条件）冻结

### 目的

为避免 M0～M8（第 0～8 阶段）在开发中无限细分，正式建立 Stage Exit Criteria（阶段退出条件）。

规则：

1. 子任务只用于完成当前阶段，不自动成为新的长期阶段。
2. 当一个阶段的 Exit Criteria（退出条件）全部满足并留下测试证据后，该阶段必须标记 PASS（通过）并进入下一阶段。
3. 阶段通过后发现的非阻塞优化进入 Backlog（待办），不得把已通过阶段无限延长。
4. 只有会破坏该阶段核心目标、安全性、跨平台边界或已验收行为的问题，才允许阻止阶段关闭。
5. 不以“还能继续优化”为理由拒绝进入下一阶段。

### M0 — Governance（开发治理）通过条件

满足以下全部条件即 PASS（通过）：

- Current State（当前状态）、Dev Log（开发日志）、Test Matrix（测试矩阵）存在。
- Platform Capability Contract（平台能力契约）建立。
- 每轮工作有固定记录和验证流程。
- 已明确 Core（核心）与 Platform Adapter（平台适配层）边界原则。

当前状态：**PASS（通过）**。

### M1 — Core Boundary（核心边界）通过条件

满足以下全部条件即 PASS（通过）：

- Runtime Lifecycle（运行生命周期）的平台无关规则由 Core（核心）承载。
- Environment Needs / Plan（环境需求 / 计划）的平台无关规则由 Core（核心）承载。
- Project Operation（项目操作）的通用动作、状态和归属规则有平台无关契约。
- Storage / Filesystem / Process Control（存储 / 文件系统 / 进程控制）的平台边界已形成接口或明确适配边界。
- Core（核心）不直接依赖 Android API（安卓接口）、macOS API（苹果接口）或 Windows API（微软接口）。
- Android（安卓）原有功能通过自动化回归；涉及真实运行行为的关键抽取通过真机回归。
- Core Boundary Audit（核心边界总审计）确认不存在为了 macOS（苹果）而强迫 Android（安卓）或 Windows（微软）实现的平台专属能力。

全部满足后，M1（核心边界）立即关闭，不因为仍有可优化的 Core（核心）代码而继续拆分。

### M2 — macOS Host Skeleton（macOS 主机骨架）通过条件

满足以下全部条件即 PASS（通过）：

- 生成真正的 macOS App（苹果桌面应用）。
- App（应用）可以在真实 Mac（苹果电脑）启动和退出。
- macOS（苹果）模块可以加载 SiftAlpha Core（跨平台核心）。
- 可以读取并显示基本 Platform Capability Snapshot（平台能力快照）。
- macOS（苹果）构建不依赖 Android Framework（安卓框架）。
- 至少一次真实 Mac（苹果电脑）验收通过。

### M3 — Host Runtime Provider（主机运行提供者）通过条件

满足以下全部条件即 PASS（通过）：

- 能检测本机 Python / Node.js / Bun / Git / Shell（Python / Node / Bun / Git / 命令环境）的可用性。
- 能启动至少一个普通主机进程。
- 能捕获 stdout / stderr（标准输出 / 标准错误）。
- STATUS（状态）能区分运行和结束。
- STOP（停止）可以终止当前项目及其归属进程。
- 同时运行两个项目时，停止 A 不影响 B。
- 上述行为在真实 Mac（苹果电脑）通过。

### M4 — Project Workflow（项目工作流）通过条件

满足以下全部条件即 PASS（通过）：

- 普通 Python（Python 运行时）项目完成 Import → Detect → Plan → Prepare → Run → Logs → Stop / Restart（导入 → 检测 → 计划 → 准备 → 运行 → 日志 → 停止 / 重启）完整闭环。
- 普通 Node.js（Node 运行时）项目完成同样闭环，或若当前范围明确不包含 Node.js（Node 运行时），需在状态文档中明确记录范围。
- Environment Plan（环境计划）只表达需求，Provider（提供者）负责满足需求。
- Web 项目能够通过 Web Discovery + Endpoint Probe（网页发现 + 端点探测）得到可用结果入口。
- 项目失败时能得到可理解的失败状态与日志。
- 真实 Mac（苹果电脑）验收通过。

### M5 — Product UI Parity（产品界面对齐）通过条件

满足以下全部条件即 PASS（通过）：

- Normal Mode（普通用户模式）可以完成正常项目工作流，不要求理解 Runtime（运行时）细节。
- Developer Mode（开发者模式）能够看到高级 Runtime（运行时）事实与诊断信息。
- 两种模式共享同一 Core（核心）、同一项目状态、同一 Runtime Lifecycle（运行生命周期）。
- 同一个项目在两个模式之间切换时不存在状态冲突或重复执行。
- 真实 Mac（苹果电脑）完成两种模式验收。

### M6 — Container / Multi-service（容器 / 多服务）通过条件

满足以下全部条件即 PASS（通过）：

- 可以检测 Docker / Podman（容器运行时）能力，且不可用时能明确报告 UNAVAILABLE（不可用）。
- 至少一个标准 Compose（多服务编排）项目能够 Prepare / Run / Status / Logs / Stop（准备 / 运行 / 状态 / 日志 / 停止）。
- 多服务日志能够归属到当前项目。
- 端口映射能够被发现并通过 Endpoint Probe（端点探测）。
- STOP（停止）当前容器项目不影响其他项目或无关容器。
- 不要求为验收项目写项目专属补丁。

### M7 — OpenBot Acceptance（OpenBot 验收）通过条件

满足以下全部条件即 PASS（通过）：

- 使用未为 SiftAlpha（筛选阿尔法）修改的原始 OpenBot（开放机器人）项目。
- SiftAlpha（筛选阿尔法）能够识别其 Bun / Docker / Compose / PostgreSQL + pgvector（Bun / 容器 / 多服务编排 / 数据库 + 向量扩展）需求。
- Prepare（准备）成功。
- 完整服务启动成功。
- Web UI（网页界面）可访问。
- Logs / Status（日志 / 状态）可观察。
- STOP（停止）仅停止 OpenBot（开放机器人）项目相关活动。
- 再次启动可以恢复运行。
- 全程没有 OpenBot-specific patch（OpenBot 专属补丁）。

满足以上条件后即视为 SiftAlpha（筛选阿尔法）复杂真实项目能力验收完成，不继续无限扩大 OpenBot（开放机器人）测试范围。

### M8 — Distribution（正式分发）通过条件

满足以下全部条件即 PASS（通过）：

- 生成正式 SiftAlpha.app（苹果应用）。
- Developer ID Application（开发者身份应用证书）签名通过。
- Hardened Runtime（强化运行时）开启且不破坏 Host Runtime Provider（主机运行提供者）。
- Apple Notarization（苹果公证）通过。
- DMG / PKG（磁盘映像 / 安装包）可在一台干净的普通 Mac（苹果电脑）安装。
- Gatekeeper（安全验证）默认设置下正常打开，不要求绕过安全机制。
- 普通用户不需要开发工具或自己的 Apple Developer（苹果开发者）账号。
- 从旧版本升级到新版本成功。
- 至少完成一次崩溃 / 重启恢复验收。

全部满足后，macOS（苹果桌面系统）首个可分发版本开发流程正式完成。

### 防止无限细分的最终规则

M0～M8（第 0～8 阶段）是固定的一级阶段。

允许出现 M1.1、M1.2 等 Implementation Slice（实现切片），但它们只服务于对应一级阶段的 Exit Criteria（退出条件）。一旦一级阶段条件全部满足：

> **停止继续拆分该阶段 → 标记 PASS（通过）→ 进入下一阶段。**

新的非阻塞优化进入 Backlog（待办），不阻止阶段推进。

## 2026-09-23 · Android Foreground Recovery Repair（安卓前台恢复修复）

### 真机问题

M1.2（环境计划）真机验收期间发现：

- 项目已经正常 Prepare / Run（准备 / 运行）。
- Runtime（运行时）仍在正常工作，日志与结果可访问。
- App（应用）切到后台后再回到前台，Developer Workspace（开发者工作区）可能永久显示 RECOVERING（恢复中）。
- 此时只剩 STOP（停止）与编辑类操作可用，其他操作被恢复门禁禁用。
- 必须 STOP（停止）后才能重新 Run（运行）。

### 根因

这不是 Runtime（运行时）实际失败，而是 Android Activity Lifecycle（安卓页面生命周期）把普通 background -> foreground（后台 → 前台）错误当成了 persisted runtime recovery（持久化运行时恢复）。

两个问题叠加：

1. `restoreStoredState()` 在 Activity（页面）停止时仍可能因异步 card refresh（卡片刷新）把健康运行项目加入 `recoveryProjects`。
2. `onStart()` 每次回前台都会再次调用 `recoverPersistedRuntimeStates()`，即使当前 Activity（页面）实例没有被销毁或重建。

### 修复

- 新增 `RuntimeForegroundRecoveryGate`。
- 一个新建 Activity（页面）实例只允许执行一次 persisted runtime recovery（持久化运行时恢复）。
- 普通后台 → 前台返回不重新进入 persisted recovery（持久化恢复）。
- Activity（页面）真正重建后，新实例仍会获得一次恢复机会。
- `restoreStoredState()` 改为纯状态读取，不再修改 `recoveryProjects`。
- External observation（外部运行观察）在普通前后台切换时继续由原有 resume mechanism（恢复机制）接管，不使用 RECOVERING（恢复中）锁住整个项目。

### 版本

- versionCode = `227`
- versionName = `0.8.0-alpha43-r48d11-m1.2-r1`

### 验收要求

必须验证：

1. External Runtime（外部运行时）项目正常运行。
2. App（应用）切后台。
3. 再回前台。
4. 项目仍显示真实 RUNNING / result-ready（运行中 / 结果可用）状态，不永久显示 RECOVERING（恢复中）。
5. Open / Logs / Refresh（打开 / 日志 / 刷新）等原本应可用操作不被恢复门禁永久锁死。
6. STOP（停止）仍能正常工作。
7. Activity（页面）真正重建后，持久化恢复仍可执行一次。

### Android Foreground Recovery Repair（安卓前台恢复修复）云端验证

- code/build SHA: `e95b07b65a82fe661ce1eeb352744a7da0076827`
- W0 Cloud Build（W0 云端构建） #685 / run ID `35818682810`: **PASS（通过）**
- Internal Alpine Probe（内部 Alpine 探针） #117: **PASS（通过）**
- `:core:test`: **PASS（通过）**
- Android unit tests（安卓单元测试）: **PASS（通过）**
- `assembleDebug`（安卓调试包构建）: **PASS（通过）**
- versionCode: `227`
- versionName: `0.8.0-alpha43-r48d11-m1.2-r1`
- APK SHA-256: `efb75996e7a92eb5cdb82ebae53d774e3ed8cf881b2883041406b8c4c674022a`
- signer SHA-256: `3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`
- artifact（云端制品）: `siftalpha-w0-685`, ID `10731739860`

当前状态：**Cloud PASS（云端通过） / Real Device Pending（真机待确认）**。

## 2026-09-23 · M1.2 Real Device Acceptance（真机验收）完成

用户已完成 v227 / `0.8.0-alpha43-r48d11-m1.2-r1` Android（安卓）真实设备验收。

已确认：

- M1.2 Environment Plan（环境计划）抽取后的 Android（安卓）现有行为正常。
- Internal Runtime（内部运行时）测试通过。
- External Runtime（外部运行时）测试通过。
- App（应用）后台 → 前台恢复修复通过。
- 健康运行项目不再永久停留在 RECOVERING（恢复中）。
- STOP / RUN（停止 / 运行）生命周期正常。
- 当前实测范围内未发现相对 Android Baseline（安卓基线）的新增回归。

结论：

**M1.2 Environment Plan（环境计划）= Cloud PASS + Real Device PASS（云端通过 + 真机通过）。**

M1.2（环境计划）正式关闭。

下一步：

**M1.3 — Project Operation（项目操作）**

目标是把 Prepare / Run / Status / Logs / Stop（准备 / 运行 / 状态 / 日志 / 停止）的通用操作语义、项目归属和动作门禁进一步从 Android（安卓）具体执行机制中分离出来。

## 2026-09-23 · M1.3 — Project Operation（项目操作）Core Extraction（核心抽取）

### 目标

把 Prepare / Start / Status / Logs / Stop / Clean（准备 / 启动 / 状态 / 日志 / 停止 / 清理）的通用操作语义、项目归属和冲突仲裁从 Android（安卓）具体执行实现中分离出来。

### 审查结论

原有 `RuntimeOperationLifecycle.kt` 已经包含较成熟的通用操作语义，但仍混有：

- Internal / External Provider（内部 / 外部提供者）
- Android（安卓）控制操作超时
- ProjectRuntimeSelection（项目运行时选择）
- 当前 Android（安卓）执行 ID / Provider（提供者）细节

其中真正跨平台的部分是：

- 操作类型
- 操作阶段
- terminal phase（终态）判断
- 同一项目只允许一个活动可变操作
- STOP（停止）可抢占同项目的非 STOP（停止）操作
- duplicate STOP（重复停止）拒绝
- 不同项目互不阻塞
- generation（代际）属于项目级操作身份

### 实现

新增：

- `core/src/main/kotlin/com/siftalpha/core/operation/ProjectOperationCore.kt`
- `core/src/test/kotlin/com/siftalpha/core/operation/ProjectOperationCoreTest.kt`

Core（核心）新增：

- `ProjectOperationAction`
- `ProjectOperationPhase`
- `ProjectOperationOwnership`
- `ProjectOperationPolicy`

Android（安卓）的：

- `RuntimeOperationRecord.terminal`
- `RuntimeOperationContract.canBegin()`
- `RuntimeOperationTracker.begin()`
- `ProjectOperationCoordinator.begin()`

现已使用同一个 Core（核心）操作仲裁规则。

### 已冻结的共享规则

1. 不同项目的操作互不阻塞。
2. 同一项目同时最多一个活动可变 Runtime（运行时）操作。
3. STOP（停止）可以抢占同项目正在进行的 PREPARE / START / STATUS / LOGS / CLEAN（准备 / 启动 / 状态 / 日志 / 清理）。
4. 已经存在 STOP（停止）时不能重复创建第二个 STOP（停止）。
5. SUCCESS / FAILED / CANCELLED / TIMED_OUT（成功 / 失败 / 取消 / 超时）是共享终态。
6. Provider（提供者）、进程 ID、Android executionId（安卓执行编号）仍属于 Platform Adapter（平台适配层），不进入 Core（核心）。

### 云端验证

- code/build SHA: `9c4f807c58ba2625c3c7e7eea647a9acf5152cf0`
- W0 Cloud Build（W0 云端构建） #691 / run ID `35820101971`: **PASS（通过）**
- Internal Alpine Probe（内部 Alpine 探针） #118: **PASS（通过）**
- `:core:test`: **PASS（通过）**
- Android unit tests（安卓单元测试）: **PASS（通过）**
- `assembleDebug`（安卓调试包构建）: **PASS（通过）**
- versionCode: `228`
- versionName: `0.8.0-alpha43-r48d11-m1.3`
- APK SHA-256: `7b3aa037642e5818a027a636dce363fd22d694c60f451e348296371f1fc94994`
- signer SHA-256: `3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`
- artifact（云端制品）: `siftalpha-w0-691`, ID `10732917346`

### 当前状态

**M1.3 Cloud PASS（云端通过），Real Device Acceptance（真机验收）待完成。**

真机通过后关闭 M1.3（项目操作），进入 M1.4 — Platform Storage Interface（平台存储接口）。

## 2026-09-23 · M1.3 Real Device Acceptance（真机验收）完成

用户已完成 v228 / `0.8.0-alpha43-r48d11-m1.3` Android（安卓）真实设备验收。

已确认：

- Prepare → Run → Status / Logs → Stop → Run（准备 → 运行 → 状态 / 日志 → 停止 → 再次运行）链路：PASS（通过）
- RUNNING → STOP（运行中 → 停止）：PASS（通过）
- 同项目冲突操作未出现重复执行问题：PASS（通过）
- 项目级 STOP（停止）未观察到影响其他项目：PASS（通过）
- v227 修复的 background → foreground（后台 → 前台）RECOVERING（恢复中）问题未重新出现：PASS（通过）
- 当前实测范围内未发现相对 Android Baseline（安卓基线）的新增回归。

结论：

**M1.3 Project Operation（项目操作）= Cloud PASS + Real Device PASS（云端通过 + 真机通过）。**

M1.3（项目操作）正式关闭。

下一步：

**M1.4 — Platform Storage Interface（平台存储接口）**

目标是把“Core（核心）需要保存什么状态”与“Android / macOS / Windows（安卓 / 苹果 / 微软）具体怎样持久化”分离，避免 SharedPreferences（安卓偏好存储）之类的平台 API（接口）继续渗入共享核心。

## 2026-09-23 · M1.4 — Platform Storage Interface（平台存储接口）Core Extraction（核心抽取）

### 目标

把“Core（核心）需要持久化什么状态”与“Android / macOS / Windows（安卓 / 苹果 / 微软）具体使用什么存储 API（接口）”分离。

本轮明确不处理 Project Filesystem（项目文件系统）；Android SAF（安卓存储访问框架）留给 M1.5（项目文件系统接口）。

### 审查结论

现有 Android（安卓）应用私有状态中，真正需要跨平台连续性的关键状态是：

- Runtime Lifecycle（运行生命周期）
- Runtime Operation（运行操作）与 generation（代际）

而以下内容当前仍属于 Android（安卓）平台选择或平台偏好，不应为了抽 Core（核心）强行统一：

- ProjectRuntimeSelection（项目运行时选择）中的 TERMUX / EMBEDDED_R（外部终端 / 内部运行时）
- Android SAF（安卓存储访问框架）项目根目录与最近文件
- Android UI（安卓界面）偏好
- Android Web / Provider（安卓网页 / 运行提供者）缓存实现细节

### 实现

Core（核心）新增：

- `core/src/main/kotlin/com/siftalpha/core/storage/PlatformStateStorage.kt`
- `PlatformStateStorage`
- `StoredStateValue`
- `StateStorageMutation`
- typed read helpers（类型化读取辅助函数）

Android（安卓）新增：

- `AndroidSharedPreferencesStateStorage`

Android（安卓）的 SharedPreferences（偏好存储）现在作为 Platform Adapter（平台适配层）实现 Core（核心）的状态存储端口。

已迁移到该端口：

- `RuntimeLifecycleStore`
- `RuntimeOperationStore`

保留兼容：

- 原有 SharedPreferences（偏好存储）文件名不变
- 原有 key（键）结构不变
- 旧 Boolean-as-String（布尔字符串）兼容迁移仍保留
- Runtime Operation generation（运行操作代际）在 clear current operation（清除当前操作）后继续保留
- 覆盖安装不要求迁移用户项目文件

### M1.4 边界

Core（核心）只允许表达：

- read（读取）
- write（写入）
- remove（删除）
- String / Boolean / Int / Long（字符串 / 布尔 / 整数 / 长整数）

Core（核心）不得直接引用：

- Android Context（安卓上下文）
- SharedPreferences（安卓偏好存储）
- macOS Foundation persistence API（苹果持久化接口）
- Windows registry / app-data API（Windows 注册表 / 应用数据接口）

各平台只需实现同一个 `PlatformStateStorage`。

### 云端验证

- code/build SHA: `858b080c9974a3d34bad18adb3b3221b8039c8a6`
- W0 Cloud Build（W0 云端构建） #698 / run ID `35821619142`: **PASS（通过）**
- Internal Alpine Probe（内部 Alpine 探针） #119: **PASS（通过）**
- `:core:test`: **PASS（通过）**
- Android unit tests（安卓单元测试）: **PASS（通过）**
- `assembleDebug`（安卓调试包构建）: **PASS（通过）**
- versionCode: `229`
- versionName: `0.8.0-alpha43-r48d11-m1.4`
- APK SHA-256: `af10db03615c8e0a209fb2b69ed8cb7b4d0638f93067a09c82a7423146be6d1b`
- signer SHA-256: `3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`
- artifact（云端制品）: `siftalpha-w0-698`, ID `10733962213`

### 当前状态

**M1.4 Cloud PASS（云端通过），Real Device Acceptance（真机验收）待完成。**

真机通过后关闭 M1.4（平台存储接口），进入 M1.5 — Project Filesystem Interface（项目文件系统接口）。

## 2026-09-23 · M1.4 Real Device Acceptance（真机验收）完成

用户已完成 v229 / `0.8.0-alpha43-r48d11-m1.4` Android（安卓）真实设备验收。

已确认：

- 覆盖安装后既有项目与关键状态保持正常：PASS（通过）
- 既有 Environment Readiness（环境就绪状态）未异常丢失：PASS（通过）
- Internal / External（内部 / 外部）环境状态保持独立：PASS（通过）
- Run → Status / Logs → Stop → Run（运行 → 状态 / 日志 → 停止 → 再次运行）连续正常：PASS（通过）
- App（应用）关闭 / 重新打开后的状态读取正常：PASS（通过）
- background → foreground（后台 → 前台）RECOVERING（恢复中）回归未复发：PASS（通过）
- 当前实测范围内未发现相对 Android Baseline（安卓基线）的新增回归。

结论：

**M1.4 Platform Storage Interface（平台存储接口）= Cloud PASS + Real Device PASS（云端通过 + 真机通过）。**

M1.4（平台存储接口）正式关闭。

下一步：

**M1.5 — Project Filesystem Interface（项目文件系统接口）**

目标是把“Core（核心）需要对项目文件做什么”与“Android SAF（安卓存储访问框架）/ macOS POSIX（苹果 POSIX 文件系统）/ Windows Filesystem（Windows 文件系统）具体怎样访问文件”分离。

## 2026-09-23 · M1.5 — Project Filesystem Interface（项目文件系统接口）Core Extraction（核心抽取）

### 目标

把“Core（核心）需要对项目文件做什么”与“Android / macOS / Windows（安卓 / 苹果 / 微软）具体如何访问项目文件”分离。

### 边界审查

原有 Android（安卓）`ProjectStore` 同时包含：

1. Project metadata（项目元数据）与项目列表展示。
2. Recent-file/UI preference（最近文件 / 界面偏好）。
3. Project filesystem access（项目文件系统访问）：
   - list children / tree（列目录 / 文件树）
   - bounded read（有界读取）
   - write（写入）
   - create file / directory（创建文件 / 文件夹）
   - rename（重命名）
   - delete（删除）
   - execution staging traversal（运行暂存遍历）

M1.5（项目文件系统接口）只抽取第 3 类。第 1、2 类不在本轮范围。

### Core（核心）新增

- `core/src/main/kotlin/com/siftalpha/core/filesystem/ProjectFilesystem.kt`
- `ProjectFileEntry`
- `ProjectFilesystem`
- `ProjectFilesystemPolicy`

共享能力：

- listChildren（列子项）
- readBytes（读取字节）
- writeBytes（写入字节）
- createFile（创建文件）
- createDirectory（创建文件夹）
- rename（重命名）
- delete（删除）
- project-relative path policy（项目相对路径规则）

`ProjectFileEntry.id` 为 opaque identity（不透明身份）：

- Android（安卓）可使用 SAF document ID（文档 ID）
- macOS（苹果）未来可使用规范化 POSIX path / platform identity（POSIX 路径 / 平台身份）
- Windows（微软）未来可使用 Windows path / platform identity（Windows 路径 / 平台身份）

Core（核心）不理解 Uri / ContentResolver / DocumentsContract（URI / 内容解析器 / 文档接口）。

### Android（安卓）实现

新增：

- `AndroidSafProjectFilesystem`

该适配器是 Android SAF（安卓存储访问框架）实现，Android 专属的：

- `Uri`
- `ContentResolver`
- `DocumentsContract`
- SAF exact-name workaround（SAF 文件名保持处理）

全部停留在 Android Platform Adapter（安卓平台适配层）。

现有 `ProjectStore` 已将以下生产路径切换到 `ProjectFilesystem`：

- 项目直接子项列表
- 全项目递归树
- Internal Runtime staging tree（内部运行时暂存树）
- 项目文件有界读取
- 文本文件写入
- 创建文件
- 创建文件夹
- 重命名
- 删除

这意味着 Embedded R / Internal Alpine（内部运行环境）执行前的项目暂存读取也已经过跨平台文件系统端口。

### 明确保留在 Android（安卓）的内容

本轮没有移动：

- Android 项目根目录选择 / persistable Uri permission（持久 URI 权限）
- 项目列表的 SAF root discovery（SAF 根目录发现）
- 项目元数据解析的 Android facade（安卓外观层）
- Recent files（最近文件）界面偏好
- ProjectStore UI helper（项目存储界面辅助逻辑）

这些不影响 Core（核心）的 filesystem port（文件系统端口）成立，也不应为了 M1.5（项目文件系统接口）无限扩大范围。

### 云端验证

- code/build SHA: `597b47b50ff50d1f1e9bf488350c9f00a95c80a5`
- W0 Cloud Build（W0 云端构建） #704 / run ID `35831792955`: **PASS（通过）**
- Internal Alpine Probe（内部 Alpine 探针） #120: **PASS（通过）**
- `:core:test`: **PASS（通过）**
- Android unit tests（安卓单元测试）: **PASS（通过）**
- `assembleDebug`（安卓调试包构建）: **PASS（通过）**
- versionCode: `230`
- versionName: `0.8.0-alpha43-r48d11-m1.5`
- APK SHA-256: `6318488cc7b8c355228e4eb5ceb30708252a7a60b4ac2d98fe41ee49d5c280cf`
- signer SHA-256: `3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`
- artifact（云端制品）: `siftalpha-w0-704`, ID `10737413684`

### 当前状态

**M1.5 Cloud PASS（云端通过），Real Device Acceptance（真机验收）待完成。**

真机通过后关闭 M1.5（项目文件系统接口），进入 M1.6 — Process Control Interface（进程控制接口）。

## 2026-09-23 · M1.5 Real Device Acceptance（真机验收）完成

用户已完成 v230 / `0.8.0-alpha43-r48d11-m1.5` Android（安卓）真实设备验收。

已确认：

- 覆盖安装后既有项目仍可正常访问：PASS（通过）
- Project file tree（项目文件树）与目录层级：PASS（通过）
- Existing text file read/write（已有文本文件读写）：PASS（通过）
- Create file / directory（创建文件 / 文件夹）：PASS（通过）
- Rename / delete（重命名 / 删除）：PASS（通过）
- Internal Runtime staging（内部运行时暂存）后的 Prepare / Run（准备 / 运行）：PASS（通过）
- External Runtime（外部运行时）既有项目运行：PASS（通过）
- Stop → Run（停止 → 再次运行）：PASS（通过）
- Background → Foreground（后台 → 前台）回归：PASS（通过）
- 当前实测范围内未发现相对 Android Baseline（安卓基线）的新增回归。

结论：

**M1.5 Project Filesystem Interface（项目文件系统接口）= Cloud PASS + Real Device PASS（云端通过 + 真机通过）。**

M1.5（项目文件系统接口）正式关闭。

下一步：

**M1.6 — Process Control Interface（进程控制接口）**

目标是把“Core（核心）需要怎样表达进程启动、状态、日志与项目级停止”与“Android / macOS / Windows（安卓 / 苹果 / 微软）各自怎样控制真实进程树”分离。

## 2026-09-23 · M1.6 Real Device Acceptance（真机验收）完成

用户已完成 v231 / `0.8.0-alpha43-r48d11-m1.6` Android（安卓）真实设备验收。

已确认：

- 同时运行项目 A / B：PASS（通过）
- STOP（停止）项目 A 后项目 B 继续运行：PASS（通过）
- 项目级 STOP isolation（停止隔离）：PASS（通过）
- 项目 A STOP（停止）后再次 Run（运行）：PASS（通过）
- Internal Runtime（内部运行时）STOP → Run（停止 → 再次运行）：PASS（通过）
- External Runtime（外部运行时）STOP → Run（停止 → 再次运行）：PASS（通过）
- background → foreground（后台 → 前台）RECOVERING（恢复中）回归未复发：PASS（通过）
- 当前实测范围内未发现相对 Android Baseline（安卓基线）的新增回归。

结论：

**M1.6 Process Control Interface（进程控制接口）= Cloud PASS + Real Device PASS（云端通过 + 真机通过）。**

M1.6（进程控制接口）正式关闭。

下一步：

**M1.7 — Core Boundary Audit（核心边界总审计）**

M1.7 不再新增新的架构切片。其任务是逐项核对 M1 Exit Criteria（M1 退出条件），确认：

1. Runtime Lifecycle（运行生命周期）已进入 Core（核心）。
2. Environment Needs / Plan（环境需求 / 环境计划）已进入 Core（核心）。
3. Project Operation（项目操作）通用动作、状态与归属规则已进入 Core（核心）。
4. Platform Storage / Project Filesystem / Process Control（平台存储 / 项目文件系统 / 进程控制）均已有明确跨平台接口。
5. Core（核心）不直接导入 Android / macOS / Windows（安卓 / 苹果 / 微软）平台 API。
6. Android（安卓）自动回归与关键真机回归全部通过。
7. Capability Isolation（能力隔离）规则未迫使 Android / Windows（安卓 / 微软）实现 macOS（苹果）专属能力。

全部满足后，M1（核心边界）必须立即关闭并进入 M2 — macOS Host Skeleton（苹果主机骨架）。

## 2026-09-23 · M1.7 — Core Boundary Audit（核心边界总审计）PASS（通过）/ M1 正式关闭

### 审计范围

M1.7（核心边界总审计）不新增功能，只核对冻结的 M1 Exit Criteria（M1 退出条件）。

### 审计结果

1. Runtime Lifecycle（运行生命周期）进入 Core（核心）：**PASS（通过）**
   - `RuntimeLifecycleCore.kt`
   - platform-neutral（平台无关）状态、操作优先级与输出解析已在 `:core`。

2. Environment Needs / Plan（环境需求 / 环境计划）进入 Core（核心）：**PASS（通过）**
   - `ProjectEnvironmentNeeds.kt`
   - Core（核心）定义“项目需要什么”，Platform Adapter（平台适配层）决定“平台怎样满足”。

3. Project Operation（项目操作）通用动作、状态、归属规则：**PASS（通过）**
   - `ProjectOperationCore.kt`
   - 同项目互斥、STOP（停止）抢占、跨项目独立、generation（代际）规则已共享。

4. Platform interfaces（平台接口）明确：**PASS（通过）**
   - `PlatformStateStorage` — 平台状态存储
   - `ProjectFilesystem` — 项目文件系统
   - `ProjectProcessControl` — 项目进程控制

5. Core（核心）无 Android / macOS / Windows（安卓 / 苹果 / 微软）平台 API（接口）依赖：**PASS（通过）**
   - `:core` 主源码共 8 个 Kotlin（Kotlin 语言）文件。
   - 审计 import（导入）结果：无 `android.*`、Apple/Foundation（苹果 Foundation）、Windows native（Windows 原生）API。
   - `core/build.gradle.kts` 仅使用 `org.jetbrains.kotlin.jvm`，无 Android Plugin（安卓插件）。
   - `:core` 仅声明 JUnit（单元测试库）测试依赖。

6. Android（安卓）自动回归与关键真机回归：**PASS（通过）**
   - M1.1～M1.6 均完成 Cloud PASS + Real Device PASS（云端通过 + 真机通过）。
   - 最新 M1.6：W0 Cloud Build（云端构建） #708 PASS。
   - Internal Alpine Probe（内部 Alpine 探针） #121 PASS。
   - v231 Android（安卓）真机项目级 STOP（停止）隔离 PASS。
   - Stop A while B continues（停止 A、B 继续运行）PASS。
   - Internal / External Runtime（内部 / 外部运行时）STOP → Run（停止 → 再次运行）PASS。

7. Capability Isolation（能力隔离）未把 macOS（苹果）专属能力强迫给 Android / Windows（安卓 / 微软）：**PASS（通过）**
   - `PLATFORM_CAPABILITY_CONTRACT.md` 继续有效。
   - AVAILABLE / UNAVAILABLE / UNKNOWN（可用 / 不可用 / 未知）均为合法平台事实。
   - Core（核心）只声明能力，不假设所有平台实现。

### 最终结论

**M1 — Core Boundary（核心边界）= PASS（通过）。**

根据 Stage Closure Rule（阶段关闭规则）：

- M1 正式关闭。
- 不再创建新的 M1.x 必做切片。
- 非阻塞优化进入 Backlog（待办）。
- 当前开发阶段立即切换到：

**M2 — macOS Host Skeleton（macOS 主机应用骨架）**

M2 的第一目标不是复杂 Runtime（运行时）或完整 UI（界面），而是建立真正的 macOS App（苹果桌面应用）骨架，使其可以：

- 在真实 macOS（苹果桌面系统）上构建；
- 启动并退出；
- 加载现有 SiftAlpha Core（跨平台核心）；
- 读取基础 Platform Capability Snapshot（平台能力快照）；
- 不依赖 Android Framework（安卓框架）。


## 2026-09-23 · M2.1 — Minimal macOS Host + Core Load（最小 macOS 主机应用 + Core 加载）开始

### 唯一目标

建立第一个真实 macOS App（苹果桌面应用）骨架，并直接加载现有 `:core`，不复制 macOS 专属 Core（苹果核心）。

### 实现

- 新增 `:macosApp` Kotlin/JVM（Kotlin / Java 虚拟机）Application（应用）模块。
- `:macosApp` 直接依赖 `project(":core")`。
- 新增最小 Swing（Java 桌面窗口）宿主，仅用于 M2 骨架验收，不作为 M5 正式产品 UI（界面）。
- 窗口从 Core（核心）的 `PlatformCapabilitySnapshot` 读取基础 CapabilityAvailability（能力可用状态）。
- M2 不进行主机运行时探测；HOST_PROCESS_EXECUTION / SECURE_SECRET_STORAGE / CONTAINER_RUNTIME（主机进程执行 / 安全存储 / 容器运行时）当前均保持 UNKNOWN（未知）。
- 新增 `--probe`（探针）模式，在无图形交互条件下证明 macOS 宿主 classpath（类路径）真实加载 Core（核心）并读取能力快照。
- 新增 `SiftAlpha macOS Host Skeleton` GitHub Actions（GitHub 云端构建）：
  - macOS runner（苹果运行器）；
  - Core tests（核心测试）；
  - macOS host tests（苹果宿主测试）；
  - Core load probe（核心加载探针）；
  - Android dependency leak check（安卓依赖泄漏检查）；
  - `jpackage` 生成真正 `SiftAlpha.app`；
  - 上传 ZIP（压缩包）与验证证据。

### 边界

- 未修改 `:core` 生产源码。
- 未修改 Android Runtime（安卓运行时）逻辑。
- 未实现 Python / Node.js / Bun / Git / Docker（Python / Node / Bun / Git / 容器）探测；这些属于 M3。
- 未实现正式产品 UI（界面）、签名、公证、DMG / PKG（磁盘映像 / 安装包）；这些不属于 M2.1。

### 验证状态

提交时：**Cloud Verification Pending（云端验证待完成）**。

M2 不会因源码提交而提前 PASS（通过）；仍需真实 Mac（苹果电脑）启动 / 退出与最终验收证据。


## 2026-09-23 · M2.1 Cloud Verification（云端验证）PASS（通过）

Source commit（源码提交）：

`3661165ae4a0ea7b700cb11c43018fe82f9864de`

验证结果：

- SiftAlpha macOS Host Skeleton（苹果主机骨架）Run #1：PASS（通过）
- Core tests（核心测试）：PASS（通过）
- macOS host tests（苹果宿主测试）：PASS（通过）
- Core load probe（核心加载探针）：PASS（通过）
- PlatformCapabilitySnapshot（平台能力快照）读取：PASS（通过）
- Android dependency leak check（安卓依赖泄漏检查）：PASS（通过）
- `jpackage` 生成 `SiftAlpha.app`：PASS（通过）
- macOS artifact（苹果制品）：`siftalpha-macos-m2.1-1`
- artifact ID（制品编号）：`10740189814`
- artifact digest（制品摘要）：`sha256:e2c60e6ca7214fbcd81f267d1f1b7cfca37e2d323f3d9509b1ddb0125faba32c`
- W0 Android Regression（安卓回归）#709：PASS（通过）
- W0 artifact（安卓制品）：`siftalpha-w0-709`
- Android Runtime（安卓运行时）生产代码：未修改
- Core（核心）生产代码：未修改

M2 当前 Closure（关闭）检查：

- macOS App build（苹果应用构建）：PASS（通过）
- Core load（核心加载）：PASS（通过）
- Capability snapshot（能力快照）：PASS（通过）
- No Android Framework dependency（无安卓框架依赖）：PASS（通过）
- Real Mac GUI launch / exit（真实 Mac 图形界面启动 / 退出）：PENDING（待验收）
- Real Mac Acceptance（真实 Mac 最终验收）：PENDING（待验收）

当前完成度：**4 / 6**。

剩余阶段阻塞项只有 2 个，均属于真实 Mac（苹果电脑）验收。不得因为非阻塞优化继续扩大 M2。


## 2026-09-24 · M2.1 Catalina-compatible package rebuild

### Trigger

The first M2.1 test package was packaged with a Java runtime whose resulting app required macOS 11. The available real acceptance machine runs macOS 10.15.7 Catalina.

### Scope

This is a packaging compatibility repair inside the existing M2.1 slice, not M2.2 and not M3.

- No SiftAlpha Core（核心） production code change.
- No Android（安卓） production code change.
- No Host Runtime Provider（主机运行提供者） implementation.
- No product UI expansion.

### Build strategy

- Move macOS CI to GitHub's Intel x64 runner label `macos-15-intel`.
- Replace Temurin JDK 17 packaging runtime with Liberica JDK 17 x64.
- Use the same Liberica `jpackage` to create the bundled app runtime.
- Set `LSMinimumSystemVersion=10.15` for the internal M2 test bundle.
- Re-sign the test app ad hoc after the plist compatibility edit.
- Inspect Mach-O deployment minimum for:
  - SiftAlpha launcher
  - `libjli.dylib`
  - `libjvm.dylib`
- Fail CI if any inspected binary requires a version newer than macOS 10.15.
- Verify packaged launcher is x86_64.

### Acceptance boundary

Cloud verification can prove architecture, Core load, capability snapshot, dependency isolation, packaging, and binary minimum OS metadata. Only the user's real macOS 10.15.7 machine can close launch / exit and final M2 real-Mac acceptance.

Cloud status at implementation commit: **PENDING（待完成）**.


### Workflow parse correction

Initial Catalina rebuild push triggered macOS Run #2, but GitHub rejected the workflow before creating any job because the embedded Python heredoc body was not indented as YAML block content. No macOS build or compatibility check executed in that run.

The workflow-only correction indents the heredoc correctly; functional macOS host source and Core（核心） remain unchanged.


### Catalina Run #3 packaging-script correction

macOS Host Skeleton Run #3 reached the real packaging step and proved:
- Liberica JDK 17.0.20 x64 setup: PASS.
- Intel x86_64 toolchain: PASS.
- Core build/tests: PASS.
- Core load probe: PASS.
- Android dependency leak check: PASS.
- generated SiftAlpha launcher architecture: x86_64.
- Info.plist `LSMinimumSystemVersion`: 10.15.

Run #3 then failed before Mach-O minimum-version assertions because the shell heredoc used by the assertion helper retained indentation after YAML decoding, causing `syntax error: unexpected end of file`.

No compatibility rejection occurred. The assertion helper is replaced with a single-line Python version comparison so the next run can execute the actual launcher/libjli/libjvm minimum-OS checks.


### Catalina rebuild final cloud verification — PASS（通过）

Final source: `0e138142117367452990a01e09e687a6a9bd53d4`.

macOS Host Skeleton Run #4 completed successfully. Evidence:

- GitHub Intel x86_64 runner: PASS.
- Liberica JDK 17 x64: PASS.
- Core build/tests: PASS.
- Core load probe: PASS.
- Capability snapshot: PASS.
- Android dependency isolation: PASS.
- `SiftAlpha.app` packaging: PASS.
- `LSMinimumSystemVersion=10.15`.
- launcher minimum OS: 10.12.
- bundled `libjli.dylib` minimum OS: 10.12.
- bundled `libjvm.dylib` minimum OS: 10.12.
- W0 Android regression #713: PASS.
- artifact: `siftalpha-macos-m2.1-catalina-x64-4`, ID `10787246281`.
- artifact digest: `sha256:4af428ea7f75d07ba84de62d982c32ca923d37c9b8169318f3edf7ab0c130423`.
- final inner ZIP SHA-256: `6a426bdcc73392bddebad9eb89aa80657f8c64038b8dcb3d354e5212a9d6a654`.

The Catalina-compatible M2.1 rebuild task is **PASS / COMPLETE（通过 / 完成）**. M2 remains open only for real-Mac launch/exit acceptance.


## 2026-09-24 · macOS 10.15.7 minimum-version policy frozen

User decision:

Keep macOS 10.15.7 Catalina as SiftAlpha's minimum supported macOS version for now.

Development rule:
- do not raise the minimum macOS version merely for convenience;
- continue using Catalina-compatible implementations when they remain correct and maintainable;
- if a future mandatory feature/API/runtime requires a newer system, report the concrete dependency, reason, proposed new minimum version, and user impact before changing the support floor;
- no silent minimum-version bump.

This policy is compatible with the current M2.1 Catalina x64 package evidence.


## 2026-09-24 · M2 Real Mac Acceptance PASS / M2 Closed

### Real machine

- macOS 10.15.7 Catalina
- Intel x86_64
- M2.1 Catalina-compatible test package
- source HEAD `0e138142117367452990a01e09e687a6a9bd53d4`

### User-observed acceptance

The user launched `SiftAlpha.app` on the real Catalina machine and supplied a screenshot showing the M2 host window.

Observed facts:

- application window opened normally;
- SiftAlpha host UI rendered normally;
- Core capability snapshot was visible;
- `host_process_execution = UNKNOWN`;
- `secure_secret_storage = UNKNOWN`;
- `container_runtime = UNKNOWN`;
- user then closed the window and explicitly confirmed the application exited normally.

The UNKNOWN values are expected M2 facts, because actual host runtime/capability discovery belongs to M3.

### M2 closure audit

Frozen M2 Exit Criteria:

1. real macOS app exists / can be built — PASS;
2. app starts and exits normally on real Mac — PASS;
3. app loads existing SiftAlpha Core — PASS;
4. app reads/displays PlatformCapabilitySnapshot — PASS;
5. macOS module has no Android Framework dependency — PASS;
6. real Mac acceptance completed — PASS.

**M2 = 6 / 6, PASS / CLOSED（通过 / 关闭）.**

Per Stage Closure Rule（阶段关闭规则）, no new mandatory M2.x slice may be added. Non-blocking M2 improvements go to Backlog（待办）.

Current stage moves to:

**M3 — Host Runtime Provider（主机运行提供者）**.


## 2026-09-24 · M3.1 Host Runtime Discovery + Project Process Control

### Scope frozen

M3.1 is a finite implementation slice intended to cover the complete M3 core control loop.

### Implementation

- Added `MacHostRuntimeDiscovery` for Python / Node.js / Bun / Git.
- Discovery searches inherited PATH plus common Homebrew, MacPorts, Volta, asdf, Bun and nvm locations.
- A tool is AVAILABLE only after a bounded successful version probe.
- Added `MacProjectProcessControl` implementing the existing shared `ProjectProcessControl` port.
- START is direct ProcessBuilder execution with explicit argument lists.
- stdout and stderr are captured independently into bounded buffers.
- STATUS maps host process facts into shared `ProjectProcessState`.
- STOP is project-scoped and terminates only the selected project's recorded root and descendants.
- No global process scan is permitted.
- Added concurrent A/B isolation tests: STOP A must leave B RUNNING.
- Added a temporary M3 real-host Self-Test button and CLI probe.
- macOS `HOST_PROCESS_EXECUTION` capability changes from UNKNOWN to AVAILABLE.
- secure secret storage and container runtime remain UNKNOWN.

### Boundaries

- No Core contract change.
- No Android production-source change.
- No M4 project workflow.
- No M5 final product UI.

Cloud verification: **IN PROGRESS（进行中）**.


### M3 Exit Criteria reconciliation — Shell discovery

The pre-existing Stage Exit Contract（阶段退出条件） requires host discovery facts for Python / Node.js / Bun / Git / Shell.

The initial M3.1 implementation covered Python / Node.js / Bun / Git but omitted Shell from its later closure checklist. That later checklist is not allowed to silently weaken an already frozen stage exit condition.

Correction inside the same finite M3.1 slice:

- add `MacHostToolKind.SHELL`;
- discover `zsh`, then `bash`, then `sh` through the same bounded executable + version-probe mechanism;
- require an explicit shell discovery fact in cloud probe output;
- add a cloud-host test proving a system shell is AVAILABLE.

No new M3.2 slice is created. Core（核心）, Android（安卓） production source, M4 workflow, and M5 product UI remain unchanged.


### M3 Shell test wiring correction

The first Shell-discovery test addition was syntactically valid Kotlin but appended at file scope, outside `MacHostRuntimeDiscoveryTest`. JUnit 4 would not execute it as a test method on the test class.

The test is moved inside the class before accepting any cloud result. Production discovery logic is unchanged.


## 2026-09-24 · M3.1 Cloud Verification PASS

Latest accepted M3.1 functional HEAD:
`54e9ed98a1d8b8a856aafc16786641adeb007fd3`.

Cloud results:

- macOS Host Runtime Run #9: PASS.
- Android W0 Cloud Build #718: PASS.
- Python discovery: AVAILABLE / Python 3.14.7.
- Node.js discovery: AVAILABLE / v22.23.2.
- Bun discovery: UNAVAILABLE (valid explicit fact).
- Git discovery: AVAILABLE / git 2.55.0.
- Shell discovery: AVAILABLE / /bin/zsh 5.9.
- host process execution capability: AVAILABLE.
- project A stop: STOPPED.
- project B after stopping A: RUNNING.
- project B stop: STOPPED.
- packaged Catalina app: PASS.
- launcher / libjli / libjvm MIN_OS: 10.12.
- M3 Catalina test artifact ID: `10787089864`.
- inner test ZIP SHA-256: `f9d2c8c04acd004f580f57ed7ebb50d4a464a84560753ba173bd469364ad1fec`.

Current status:
**Cloud PASS（云端通过） / Real Mac M3 Self-Test Pending（真实 Mac M3 自检待完成）**.

No M3 Closure Audit may run until the real macOS 10.15.7 self-test passes.


## 2026-09-24 · M3.1 Catalina passive-discovery repair

### Real-Mac evidence

On macOS 10.15.7 Catalina, opening the M3.1 test package showed:

- host process execution AVAILABLE;
- shell AVAILABLE;
- legacy Python 2.7.16 reported AVAILABLE;
- Node.js / Bun / Git UNAVAILABLE;
- macOS simultaneously displayed the system prompt asking to install Command Line Developer Tools for the `python3` command.

### Root cause

Catalina can expose Apple developer-tool shim executables even when Command Line Tools are not installed. The discovery implementation treated executable-file presence as safe to probe and executed `/usr/bin/python3 --version`, which triggered Apple's installer UI. After that failed candidate it accepted `/usr/bin/python` / Python 2.7.16.

### Repair

- discovery becomes passive: first check `xcode-select -p` without opening installation UI;
- if developer tools are absent, skip the known `/usr/bin/python3` and `/usr/bin/git` shims;
- keep user-managed Homebrew / MacPorts / asdf / PATH installations discoverable;
- Python candidates are accepted only when the version probe reports Python 3;
- legacy Python 2 is rejected as host Python;
- add pure policy tests for Python 3 acceptance and developer-tool shim skipping.

This remains part of the single M3.1 implementation slice. No M3.2 is created. Core and Android production source are unchanged.
