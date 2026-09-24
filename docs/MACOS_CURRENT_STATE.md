# SiftAlpha macOS Current State（macOS 当前状态）

> 本文件是 macOS（苹果桌面系统）开发的当前状态事实源。
> 每一轮 macOS（苹果桌面系统）开发结束后都必须更新本文件；历史过程写入 `MACOS_DEV_LOG.md`。

## 1. 当前阶段

状态：**M1 — Core Boundary（核心边界）与 M2 — macOS Host Skeleton（macOS 主机应用骨架）均已正式 PASS / CLOSED（通过 / 关闭）；当前阶段为 M3 — Host Runtime Provider（主机运行提供者）。**

当前工作分支：
- `feature/cross-platform-core`

当前跨平台 Core（核心）版本：
- `0.8.0-alpha43-r48d11-m1.6`
- 最新已验收 Android（安卓）回归包：`0.8.0-alpha43-r48d11-m1.6-r1` / versionCode `232`

当前 Core（核心）能力隔离规则：
- `docs/PLATFORM_CAPABILITY_CONTRACT.md`
- Core（核心）可以定义能力，但不得强迫所有平台实现同一能力。
- macOS（苹果）专属能力必须留在 macOS Platform Adapter（苹果平台适配层）。
- Android（安卓）现有行为是跨平台抽取期间的重要回归基线。

## 2. macOS 产品目标

macOS（苹果桌面系统）版不是新的独立产品，而是：

```
SiftAlpha Core（跨平台核心）
        +
macOS Platform Adapter（苹果平台适配层）
        +
macOS UI（苹果平台界面）
        +
Host Runtime Provider（主机运行提供者）
```

产品仍保留两种使用方式：

- Normal Mode（普通用户模式）
- Developer Mode（开发者模式）

两种模式必须共享同一套 Core（核心）和 Runtime（运行时）事实，不允许重新复制两套运行逻辑。

## 3. macOS 第一阶段执行原则

macOS（苹果桌面系统）优先使用 Host Runtime Provider（主机运行提供者），直接调用本机已有能力，例如：

- Python（Python 运行时）
- Node.js（Node 运行时）
- Bun（Bun 运行时）
- Git（版本控制）
- Docker / Podman（容器）
- Shell（命令环境）

Android（安卓）现有 Embedded CPython（内嵌 CPython）、Internal Alpine（内部 Alpine）、Termux（外部终端）不是 macOS（苹果）首要运行路径。

这些 Android（安卓）能力不得为了 macOS（苹果）而被删除或重写。

## 4. 当前已具备的可复用基础

当前已经具备并计划复用的能力包括：

- RuntimeKind（运行时类型）
- Project Runtime Profile（项目运行时画像）
- Environment Detection（环境检测）方向
- Environment Plan（环境计划）方向
- Session（会话）
- Generation（代际）
- Runtime Lifecycle（运行生命周期）
- project-scoped operation（项目级操作）
- START / STOP / STATUS / LOGS（启动 / 停止 / 状态 / 日志）
- Web Discovery / Endpoint Probe（网页发现 / 端点探测）策略
- Normal Mode / Developer Mode（普通模式 / 开发者模式）共享核心原则
- Platform Capability Contract（平台能力契约）

其中仍有大量实现位于 Android（安卓）模块，后续需逐步抽取，不得一次性大迁移。

## 5. 当前尚未实现

以下内容尚未开始，因此不得宣称 macOS（苹果）版已经可运行：

- 完整 macOS Platform Adapter（苹果平台适配层）功能（M2 仅完成最小宿主/能力发布骨架）
- macOS Host Process（苹果主机进程执行）
- macOS filesystem bridge（苹果文件系统桥接）
- macOS secure storage（苹果安全存储）
- macOS project import（苹果项目导入）
- macOS Normal Mode UI（苹果普通模式界面）
- macOS Developer Mode UI（苹果开发者模式界面）
- macOS Runtime Provider selection（苹果运行提供者选择）
- Docker / Bun / Python host execution（容器 / Bun / Python 主机执行）
- 正式 macOS signing / notarization / distribution packaging（签名 / 公证 / 分发打包；M2 内部测试 app-image 已完成）
- OpenBot（开放机器人）真实运行验收

## 6. 开发顺序

### M0 — Governance（开发治理）
已建立：
- 独立 Current State（当前状态）
- 独立 Dev Log（开发日志）
- 独立 Test Matrix（测试矩阵）
- Capability Isolation（能力隔离）规则

### M1 — Core Boundary（核心边界）
目标：
- 继续识别并抽取真正平台无关的 Core（核心）
- 不改变 Android（安卓）现有行为
- 建立明确的 Platform Interface（平台接口）

### M2 — macOS Host Skeleton（苹果主机骨架）
目标：
- 建立 macOS（苹果）平台模块
- 验证 App（应用）可以启动
- 验证 Core（核心）可以被 macOS（苹果）端加载
- 不运行复杂项目

### M3 — Host Runtime Provider（主机运行提供者）
目标：
- 发现本机 Python / Node.js / Bun / Git
- 启动、观察、停止本机进程
- 建立 project-scoped STOP（项目级停止）

### M4 — Project Workflow（项目工作流）
目标：
- 导入项目
- Environment Detection（环境检测）
- Environment Plan（环境计划）
- Prepare（准备）
- Run（运行）
- Status / Logs（状态 / 日志）

### M5 — Product UI Parity（产品界面对齐）
目标：
- Normal Mode（普通用户模式）
- Developer Mode（开发者模式）
- 两种模式共享同一套 Core（核心）事实

### M6 — Container / Multi-service（容器 / 多服务）
目标：
- Docker / Podman（容器）
- Compose（多服务编排）
- Bun workspace（Bun 工作区）
- 多服务生命周期和端口映射

### M7 — OpenBot Acceptance（OpenBot 验收）
目标：
- 不修改 OpenBot（开放机器人）源码
- 识别项目需求
- 准备依赖
- 启动完整服务
- Web（网页）结果可访问
- STOP（停止）只停止该项目
- 重启可恢复

### M8 — Distribution（分发）
目标：
- macOS packaging（苹果应用打包）
- signing（签名）
- notarization（苹果公证）
- upgrade（升级）
- crash/recovery（崩溃 / 恢复）

## 7. 强制约束

1. 不为了 macOS（苹果）修改 Android（安卓）特有实现。
2. 不为了 macOS（苹果）把平台专属能力强行加入 Core（核心）。
3. Core（核心）修改必须通过 Core tests（核心测试）和 Android regression（安卓回归）。
4. macOS（苹果）专属实现必须位于 Platform Adapter（平台适配层）。
5. 一个能力在 macOS（苹果）可用，不代表 Android（安卓）或 Windows（微软）必须实现。
6. 不复制一套 macOS 专属 Core（核心）。
7. Normal Mode（普通模式）和 Developer Mode（开发者模式）不得各自维护运行状态机。
8. OpenBot（开放机器人）只是最终验收项目之一，不允许出现 OpenBot-specific patch（OpenBot 专属补丁）。
9. 每轮开始先读取本文件、`MACOS_DEV_LOG.md`、`MACOS_TEST_MATRIX.md` 和 `PLATFORM_CAPABILITY_CONTRACT.md`。
10. 每轮完成必须更新状态、测试证据和下一步。

## 8. 下一步唯一方向

当前正式进入 **M3 — Host Runtime Provider（主机运行提供者）**。

M3 的冻结目标：

- 发现本机 Python / Node.js / Bun / Git（主机运行时 / 工具）；
- 启动并观察主机项目进程；
- 捕获 stdout / stderr（标准输出 / 标准错误）；
- 建立 STATUS（状态）与 project-scoped STOP（项目级停止）；
- 验证两个并行项目互不影响。

M2 已关闭，不再新增 M2.x 必做切片。正式产品 UI（界面）仍属于 M5，项目导入/Prepare/Run 完整工作流仍属于 M4。

## 9. 固定开发方法

macOS（苹果桌面系统）开发采用“总路线提前规划、每个阶段再拆小任务”的方式。

当前总路线固定为：

`M0 → M1 → M2 → M3 → M4 → M5 → M6 → M7 → M8`

不得把未来所有实现细节提前写死，但不得改变阶段目标而不记录架构决策。

当前唯一开发方向为：

**M3 — Host Runtime Provider（主机运行提供者）**

状态：**M2 PASS / CLOSED（通过 / 关闭）→ M3 CURRENT（当前阶段）**。

M1 与 M2 均已关闭，不得重新增加新的必做切片。M3 只负责主机运行时发现与项目级进程控制，不提前实现 M4 项目工作流或 M5 产品 UI。

## 10. macOS 分发策略

macOS（苹果桌面系统）和 iOS（苹果手机系统）的安装限制不同。

开发阶段可以先生成本机可运行的 macOS App（苹果桌面应用）进行开发和测试；正式提供给普通用户直接下载安装时，目标分发方式为：

```
SiftAlpha.app（苹果应用）
    ↓
Developer ID Application（开发者身份应用证书）签名
    ↓
Hardened Runtime（强化运行时）
    ↓
Apple Notarization（苹果公证）
    ↓
DMG / PKG（磁盘映像 / 安装包）
    ↓
用户直接下载安装
```

正式对外分发阶段需要 Apple Developer Program（苹果开发者计划）提供的 Developer ID（开发者身份）证书和 Notarization（苹果公证）能力。

普通用户安装 SiftAlpha（筛选阿尔法）不需要自己的开发者账号，也不采用 iOS（苹果手机系统）那种每台设备注册的开发模式。

未经 Developer ID（开发者身份）签名和 Notarization（苹果公证）的开发包可以用于内部开发/测试，但不作为最终普通用户分发方案。


## 11. M1.2 Environment Plan（环境计划）当前边界

Core（核心）现已拥有 provider-neutral（提供者中立）的 `ProjectEnvironmentNeeds`。

原则：

```
Project（项目）
    ↓
Core（核心）：项目需要什么
    ↓
Platform Adapter（平台适配层）：当前平台如何满足
```

例如同一个“需要 Python（Python 运行时）”事实：

- Android（安卓）可由 Embedded CPython / Internal Alpine / External Provider（内嵌 CPython / 内部 Alpine / 外部提供者）满足。
- macOS（苹果）未来可由 Host Python / Managed Runtime（主机 Python / 托管运行时）满足。
- Windows（微软）未来可由 Host Python / WSL2 / Managed Runtime（主机 Python / Windows Linux 子系统 / 托管运行时）满足。

Core（核心）不得因为某个平台的实现方式而要求其他平台实现同一个 backend（后端）。

## 12. Stage Exit Rule（阶段退出规则）

为防止开发无限细分，M0～M8（第 0～8 阶段）现在都有固定 Exit Criteria（退出条件），完整定义见 `MACOS_DEV_LOG.md` 与 `MACOS_TEST_MATRIX.md`。

执行原则：

- M1.1 / M1.2 等只属于 Implementation Slice（实现切片），不是无限扩展的新阶段。
- 一级阶段的全部退出条件满足后必须结束该阶段并进入下一阶段。
- 非阻塞优化进入 Backlog（待办）。
- “还能继续优化”本身不能作为延迟进入下一阶段的理由。
- 当前 M1（核心边界）只有在其明确退出条件尚未满足时才继续拆分。

M1（核心边界）全部 Exit Criteria（退出条件）均已满足，阶段已关闭。当前进入 M2（macOS 主机应用骨架）。


## 18. M1 正式关闭 / M2 当前入口

M1.1～M1.6（实现切片）全部完成 Cloud PASS + Real Device PASS（云端通过 + 真机通过）。

M1.7 Core Boundary Audit（核心边界总审计）确认 7 条 M1 Exit Criteria（退出条件）全部 PASS（通过）。

因此：

**M1 — Core Boundary（核心边界）= CLOSED / PASS（已关闭 / 通过）。**

当前唯一阶段：

**M2 — macOS Host Skeleton（macOS 主机应用骨架）**

M2 Exit Criteria（退出条件）：

1. 真正的 macOS App（苹果桌面应用）可在真实 Mac（苹果电脑）构建。
2. App（应用）可以启动并正常退出。
3. App（应用）能够加载现有 SiftAlpha Core（跨平台核心）。
4. App（应用）可以读取基础 Platform Capability Snapshot（平台能力快照）。
5. macOS 模块不依赖 Android Framework（安卓框架）。
6. 完成真实 Mac（苹果电脑）验收。

满足以上条件后 M2 必须立即 PASS（通过）并进入 M3（主机运行提供者）。

## 19. M2.1 当前执行状态 — 2026-09-23

目标：建立最小真实 macOS App（苹果桌面应用）并证明其直接加载现有 SiftAlpha Core（跨平台核心）。

实现边界：

- 新增 `:macosApp` Kotlin/JVM（Kotlin / Java 虚拟机）宿主模块；
- `:macosApp` 直接 `implementation(project(":core"))`；
- 最小窗口只呈现 M2 验证信息，不建设 M5（产品界面对齐）的正式 Normal / Developer UI（普通 / 开发者界面）；
- `MacPlatformCapabilities` 只发布 M2 阶段的基础 Capability Snapshot（能力快照），未知能力保持 UNKNOWN（未知）；
- 新增 macOS GitHub Actions（GitHub 云端构建）验证，使用 macOS runner（苹果运行器）+ `jpackage` 生成 `SiftAlpha.app`；
- 未引入 Android Framework（安卓框架）依赖；
- 未修改 `:core` 生产源码；
- 未提前实现 M3 Host Runtime Provider（主机运行提供者）。

当前状态：

**CLOUD PASS（云端通过） / REAL MAC PENDING（真实 Mac 待验收）**。

Cloud Verification（云端验证）：

- source commit（源码提交）：`3661165ae4a0ea7b700cb11c43018fe82f9864de`
- SiftAlpha macOS Host Skeleton（苹果主机骨架）Run #1：PASS（通过）
- Core tests（核心测试）：PASS（通过）
- macOS host tests（苹果宿主测试）：PASS（通过）
- Core load probe（核心加载探针）：PASS（通过）
- PlatformCapabilitySnapshot read（平台能力快照读取）：PASS（通过）
- Android dependency leak check（安卓依赖泄漏检查）：PASS（通过）
- `jpackage` SiftAlpha.app（苹果应用包）构建：PASS（通过）
- macOS artifact（苹果制品）：`siftalpha-macos-m2.1-1`
- artifact ID（制品编号）：`10740189814`
- artifact digest（制品摘要）：`sha256:e2c60e6ca7214fbcd81f267d1f1b7cfca37e2d323f3d9509b1ddb0125faba32c`
- W0 Android Regression（安卓回归）#709：PASS（通过）
- W0 artifact（安卓制品）：`siftalpha-w0-709`

M2 Exit Criteria（退出条件）当前完成度：**4 / 6**。

已满足：
1. 真正的 macOS App（苹果桌面应用）可在 macOS 构建环境生成。
2. App（应用）能够加载现有 SiftAlpha Core（跨平台核心）。
3. App（应用）能够读取基础 PlatformCapabilitySnapshot（平台能力快照）。
4. macOS 模块不依赖 Android Framework（安卓框架）。

剩余 Blocking Items（阻塞项）：
- 真实 Mac（苹果电脑）GUI 启动并正常退出验收。
- 真实 Mac Acceptance（真实 Mac 最终验收）。

M2 仍未关闭；除上述两个真实 Mac（苹果电脑）阻塞项外，不新增新的 M2 必做条件。


## 19. M2.1 Catalina compatibility rebuild

真实 Mac（苹果电脑）首次 M2.1 验收发现原测试包要求 macOS 11，而实际验收机器为 macOS 10.15.7 Catalina。

这不是新的 M2 功能切片，也不改变 M2 Exit Criteria（退出条件）。当前只重新构建同一 M2.1 Host Skeleton（主机骨架）测试包：

- target OS（目标系统）: macOS 10.15.7 Catalina
- target architecture（目标架构）: Intel x86_64
- JDK（Java 运行时）: Liberica JDK 17 x64
- packaging（打包）: Liberica `jpackage`
- `LSMinimumSystemVersion`: 10.15
- CI（持续集成）必须检查 app launcher、`libjli.dylib`、`libjvm.dylib` 的 Mach-O minimum OS（最低系统版本）不高于 10.15。
- 不修改 `:core`、Android（安卓）功能源码或 M3 Host Runtime Provider（主机运行提供者）功能。

状态：**CATALINA REBUILD CLOUD PENDING（Catalina 兼容重构云端待验证）**。

M2 仍保持 **4 / 6**；真实 Mac 启动 / 退出与最终真实 Mac 验收仍是唯一剩余 M2 Exit Criteria（退出条件）。


### M2.1 Catalina rebuild cloud result — PASS（通过）

- source HEAD（源码提交）: `0e138142117367452990a01e09e687a6a9bd53d4`
- macOS Host Skeleton Run #4: PASS（通过）
- W0 Android regression（安卓回归） #713: PASS（通过）
- final artifact（最终制品）: `siftalpha-macos-m2.1-catalina-x64-4`
- artifact ID: `10787246281`
- artifact digest: `sha256:4af428ea7f75d07ba84de62d982c32ca923d37c9b8169318f3edf7ab0c130423`
- inner test ZIP SHA-256: `6a426bdcc73392bddebad9eb89aa80657f8c64038b8dcb3d354e5212a9d6a654`
- target architecture（目标架构）: Intel x86_64
- `LSMinimumSystemVersion`: 10.15
- launcher Mach-O MIN_OS: 10.12
- bundled `libjli.dylib` MIN_OS: 10.12
- bundled `libjvm.dylib` MIN_OS: 10.12
- Core load probe（核心加载探针）: PASS（通过）
- Android dependency leak check（安卓依赖泄漏检查）: PASS（通过）

Catalina-compatible package rebuild（Catalina 兼容测试包重建）已完成。

M2 stage（M2 阶段）本身仍保持 **4 / 6**，仅剩真实 macOS 10.15.7 机器上的 GUI launch / normal exit（图形界面启动 / 正常退出）与最终 Real Mac acceptance（真实 Mac 验收）。


## 20. macOS Minimum Version Policy（macOS 最低系统版本策略）

当前最低支持版本冻结为：

**macOS 10.15.7 Catalina**

规则：

- 在现有功能与后续功能能够继续兼容 Catalina（10.15.7）的前提下，不主动提高最低系统版本。
- 新功能设计优先寻找仍兼容 10.15.7 的实现方式，但不得为了维持旧系统兼容而破坏安全性、正确性或核心架构。
- 只有当某项必要功能、Apple system API（苹果系统接口）、第三方运行时、签名/公证链、容器/虚拟化能力或其他硬性依赖明确要求更高 macOS 版本时，才允许提出提高最低版本。
- 在提高最低版本之前，必须先向用户说明：
  1. 哪个具体功能或依赖导致升级要求；
  2. 为什么 10.15.7 已无法继续支持；
  3. 新的最低 macOS 版本；
  4. 对现有 Catalina 用户的影响。
- 未经明确确认，不把最低系统版本从 10.15.7 提高。

当前 M2.1 Catalina x64 测试包已经验证：
- `LSMinimumSystemVersion=10.15`
- launcher / bundled JVM minimum OS = 10.12
- 因此当前 M2.1 技术栈继续支持 macOS 10.15.7。


## 21. M2 Real Mac Acceptance（真实 Mac 验收）— PASS / CLOSED

真实验收机器：

- OS（系统）: macOS 10.15.7 Catalina
- Architecture（架构）: Intel x86_64
- Test package（测试包）: M2.1 Catalina x64
- source HEAD（源码提交）: `0e138142117367452990a01e09e687a6a9bd53d4`

用户真实验收结果：

1. `SiftAlpha.app` 可以在 macOS 10.15.7 正常启动：PASS（通过）。
2. Swing GUI（桌面窗口）正常显示：PASS（通过）。
3. SiftAlpha Core（跨平台核心）成功加载：PASS（通过）。
4. Platform Capability Snapshot（平台能力快照）正常显示：
   - `host_process_execution = UNKNOWN`
   - `secure_secret_storage = UNKNOWN`
   - `container_runtime = UNKNOWN`
   这些 UNKNOWN（未知）符合 M2 设计，真实能力发现属于 M3。
5. App（应用）可通过窗口关闭正常退出：PASS（通过）。
6. 用户确认真实 Mac 验收完成：PASS（通过）。

M2 Exit Criteria（退出条件）全部满足：**6 / 6**。

最终结论：

**M2 — macOS Host Skeleton（macOS 主机应用骨架）= PASS / CLOSED（通过 / 关闭）。**

当前唯一阶段：

**M3 — Host Runtime Provider（主机运行提供者）**。


## 22. M3.1 — Host Runtime Discovery + Project Process Control

M3 使用一个有限实现切片完成主机运行提供者的核心闭环，不允许无限拆分。

### Goal（目标）

在 macOS Platform Adapter（苹果平台适配层）实现：

- Python / Node.js / Bun / Git / Shell（运行时 / 工具 / 命令环境）发现；
- host process execution（主机进程执行）；
- stdout / stderr（标准输出 / 标准错误）采集；
- STATUS（状态）；
- project-scoped STOP（项目级停止）；
- 两个并行项目的 STOP isolation（停止隔离）。

### Architecture（架构）

- 复用 Core（核心）现有 `ProjectProcessControl` 契约；
- macOS 具体实现位于 `macosApp`；
- STOP 只终止该项目记录的 root process + descendants（根进程 + 子进程），不做全局进程扫描；
- Runtime Discovery（运行时发现）同时检查 Finder（访达）可能缺失的 PATH 与常见 Homebrew / MacPorts / Volta / asdf / Bun 路径；
- AVAILABLE（可用）必须通过实际 `--version` 探测，不以文件存在作为唯一依据；
- `HOST_PROCESS_EXECUTION` 现在由 macOS Adapter（苹果适配器）发布为 AVAILABLE（可用）。

### Boundary（边界）

- Core（核心）契约不修改；
- Android（安卓）功能源码不修改；
- 不提前实现 M4 Import / Prepare / Project Workflow（导入 / 准备 / 项目工作流）；
- 不提前建设 M5 final UI（最终产品界面）；
- 当前 Self-Test（自检）按钮仅服务于 M3 真机验收。

### Frozen M3 closure path（冻结收尾路径）

只检查以下 9 个 M3 功能条件：

1. Python discovery fact（发现事实）；
2. Node.js discovery fact；
3. Bun discovery fact；
4. Git discovery fact；
5. Shell discovery fact（命令环境发现事实）；
6. process start（进程启动）；
7. stdout / stderr capture（输出采集）；
8. STATUS（状态）；
9. selected-project STOP（选中项目停止）；
10. Project A/B concurrent isolation（并发隔离）。

Cloud（云端）与真实 Catalina Mac（苹果电脑）关键验证通过后，只执行一次 M3 Closure Audit（关闭审计），随后 M3 必须 PASS / CLOSED（通过 / 关闭）并进入 M4。

当前状态：**CLOUD PASS（云端通过） / REAL MAC M3 SELF-TEST PENDING（真实 Mac M3 自检待完成）**。


### M3.1 Cloud Verification（云端验证）— PASS

- source HEAD: `54e9ed98a1d8b8a856aafc16786641adeb007fd3`
- macOS Host Runtime Run #9: PASS
- Android W0 Cloud Build #718: PASS
- macOS artifact: `siftalpha-macos-m3.1-catalina-x64-9`
- artifact ID: `10787089864`
- artifact digest: `sha256:30f98467a31e3c9a975fa18c10ff6787b9cd04ffd41c5979c5f7aac3633d4b04`
- inner Catalina test ZIP SHA-256: `f9d2c8c04acd004f580f57ed7ebb50d4a464a84560753ba173bd469364ad1fec`
- Android W0 artifact: `siftalpha-w0-718`
- Android W0 artifact digest: `sha256:8dc6a6538f5ebc4dd67e48822ffdf340f755201ea673bde379bba03412e794bc`

Cloud runtime facts:

- Python: AVAILABLE — Python 3.14.7
- Node.js: AVAILABLE — v22.23.2
- Bun: UNAVAILABLE（合法不可用事实）
- Git: AVAILABLE — 2.55.0
- Shell: AVAILABLE — /bin/zsh 5.9
- host_process_execution: AVAILABLE
- project-scoped process self-test: PASS
- STOP A leaves B RUNNING: PASS
- Catalina binary minimum OS checks: 10.12 / 10.12 / 10.12

M3 尚未关闭。唯一剩余阶段阻塞项是 Real Mac M3 Self-Test（真实 Mac M3 自检）。


### M3.1 Catalina real-Mac discovery regression — FIX IN PROGRESS

Real macOS 10.15.7 acceptance exposed two discovery defects before the process self-test:

- probing the Catalina `/usr/bin/python3` developer-tool shim opened the Apple Command Line Tools installer;
- legacy system Python 2.7.16 was then accepted as SiftAlpha host Python.

Required invariant:

- Runtime Discovery（运行时发现） must be passive and must not trigger installation UI;
- Python host capability means Python 3, not legacy Python 2;
- when Command Line Tools are absent, Apple developer-tool shims for `/usr/bin/python3` and `/usr/bin/git` are skipped and reported as unavailable unless another real installation is found.

M3 remains open. Cloud PASS from the previous package is retained as historical evidence but the corrected package must be re-verified before real-Mac acceptance resumes.
