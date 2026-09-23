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
