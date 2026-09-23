# SiftAlpha macOS Test Matrix（macOS 测试矩阵）

> 本文件定义 macOS（苹果桌面系统）每个阶段必须通过的验收门槛。
> 未达到对应门槛时，不得把阶段标记为完成。

## 全局回归门槛

任何修改 SiftAlpha Core（跨平台核心）的提交都必须满足：

| 验证项 | 要求 |
|---|---|
| Core tests（核心测试） | PASS（通过） |
| Android unit tests（安卓单元测试） | PASS（通过） |
| Android assembleDebug（安卓调试包构建） | PASS（通过） |
| Android stable signing（安卓稳定签名） | 不得意外变化 |
| Android Runtime behavior（安卓运行行为） | 不得因 macOS（苹果）能力被强制改变 |
| Capability isolation（能力隔离） | 不支持的能力必须允许 UNAVAILABLE / UNKNOWN（不可用 / 未知） |

## M0 — Governance（开发治理）

| 项目 | 状态 |
|---|---|
| macOS Current State（当前状态） | PASS（通过） |
| macOS Dev Log（开发日志） | PASS（通过） |
| macOS Test Matrix（测试矩阵） | PASS（通过） |
| Platform Capability Contract（平台能力契约） | PASS（通过） |
| Android regression after capability contract（能力契约后的安卓回归） | PASS（通过），W0 #672 |
| Internal Alpine Probe（内部 Alpine 探针） | PASS（通过），#114 |

## M1 — Core Boundary（核心边界）

必须证明：

- 被迁移到 `:core` 的代码不 import Android API（导入安卓接口）。
- Core（核心）不得包含 macOS / Windows / Android 平台分支实现。
- Android（安卓）继续使用原有平台实现。
- Core tests（核心测试）覆盖迁移后的公共规则。
- Android（安卓）完整回归通过。

当前状态：**IN PROGRESS（进行中）**

### M1.1 Runtime Lifecycle（运行生命周期）

- Core lifecycle policy（核心生命周期策略）: PASS（通过）
- Core output parser（核心输出解析）: PASS（通过）
- Android compatibility facade（安卓兼容外壳）: PASS（通过）
- Existing Android lifecycle regression（现有安卓生命周期回归）: PASS（通过）
- W0 Cloud Build（W0 云端构建） #677: PASS（通过）
- Internal Alpine Probe（内部 Alpine 探针） #115: PASS（通过）
- APK SHA-256: `43619a6f3aed4d960fc2efdfb99804faf1804f0d69d00da42f15c2f60944cf67`

M1.1 结论：**PASS（通过）**

下一项：M1.2 Environment Plan（环境计划）

## M2 — macOS Host Skeleton（苹果主机骨架）

必须证明：

- macOS（苹果）应用可构建。
- macOS（苹果）应用可启动。
- 可加载 SiftAlpha Core（核心）。
- 可读取基本平台能力快照。
- 不要求 Android（安卓）库参与 macOS（苹果）构建。
- 完成真实 Mac（苹果电脑）验收。

当前状态：**IN PROGRESS（进行中）**

当前切片：**M2.1 — Minimal macOS Host + Core Load（最小 macOS 主机应用 + Core 加载）**

当前证据状态：

- macOS host source（苹果宿主源码）: IMPLEMENTED（已实现）
- Core direct dependency（核心直接依赖）: IMPLEMENTED（已实现）
- Capability snapshot read（能力快照读取）: IMPLEMENTED（已实现）
- macOS cloud build（苹果云端构建）: PENDING（待验证）
- Real Mac launch / exit（真实 Mac 启动 / 退出）: PENDING（待验证）
- Real Mac acceptance（真实 Mac 验收）: PENDING（待验证）

## M3 — Host Runtime Provider（主机运行提供者）

必须证明：

- Python（Python 运行时）发现。
- Node.js（Node 运行时）发现。
- Bun（Bun 运行时）发现。
- Git（版本控制）发现。
- 启动一个项目进程。
- 捕获 stdout / stderr（标准输出 / 标准错误）。
- STATUS（状态）正确。
- STOP（停止）只影响当前项目。
- 另一个同时运行的项目不受影响。

当前状态：**NOT STARTED（未开始）**

## M4 — Project Workflow（项目工作流）

必须证明：

- Import（导入）成功。
- Detect（检测）成功。
- Environment Plan（环境计划）正确。
- Prepare（准备）成功。
- Run（运行）成功。
- Logs（日志）可读。
- Web Endpoint（网页端点）可发现和验证。
- Stop / Restart（停止 / 重启）闭环。

当前状态：**NOT STARTED（未开始）**

## M5 — Product UI Parity（产品界面对齐）

必须证明：

- Normal Mode（普通模式）可以完成导入 → 准备 → 运行 → 查看结果 → 停止。
- Developer Mode（开发者模式）可以查看高级运行事实。
- 两种模式读取同一个 Runtime（运行时）状态。
- 不存在两套独立生命周期。

当前状态：**NOT STARTED（未开始）**

## M6 — Container / Multi-service（容器 / 多服务）

必须证明：

- Docker / Podman（容器）能力检测。
- Container Capability（容器能力）可明确 AVAILABLE / UNAVAILABLE / UNKNOWN（可用 / 不可用 / 未知）。
- Compose（多服务编排）项目启动。
- 多服务日志归属正确。
- 端口映射可发现。
- project-scoped STOP（项目级停止）不影响其他项目。

当前状态：**NOT STARTED（未开始）**

## M7 — OpenBot Acceptance（OpenBot 验收）

必须证明：

- 使用原始 OpenBot（开放机器人）项目，不修改其源码适配 SiftAlpha。
- Bun（Bun 运行时）依赖识别正确。
- Docker / Compose（容器 / 多服务编排）需求识别正确。
- PostgreSQL + pgvector（数据库 + 向量扩展）服务启动。
- OpenBot（开放机器人）主服务启动。
- Web UI（网页界面）可访问。
- 日志和状态可观察。
- STOP（停止）只停止该项目。
- 重启后可以重新运行。

当前状态：**NOT STARTED（未开始）**

## M8 — Distribution（分发）

必须证明：

- macOS application bundle（苹果应用包）构建。
- code signing（代码签名）。
- notarization（苹果公证）。
- clean install（全新安装）。
- upgrade install（升级安装）。
- crash recovery（崩溃恢复）。
- 普通用户无需开发工具即可启动 SiftAlpha（筛选阿尔法）。

当前状态：**NOT STARTED（未开始）**

## macOS Distribution Gate（macOS 分发门槛）

M8（正式分发）最终验收必须同时覆盖：

- Developer ID Application（开发者身份应用证书）签名验证。
- Hardened Runtime（强化运行时）启用并兼容 SiftAlpha（筛选阿尔法）的 Host Runtime Provider（主机运行提供者）。
- Apple Notarization（苹果公证）成功。
- Gatekeeper（macOS 安全验证）在普通用户默认安全设置下接受应用。
- DMG / PKG（磁盘映像 / 安装包）下载后可正常安装。
- 用户不需要 Apple Developer Program（苹果开发者计划）账号即可安装和运行。
- 不依赖“右键打开”“关闭 Gatekeeper（安全验证）”等绕过方式作为正式安装流程。
- 若使用 Mac App Store（Mac 应用商店）分发，则作为另一条分发渠道单独验收，不替代 Developer ID（开发者身份）直接分发测试。

### M1.1 Real Device Acceptance（真机验收）

- Android（安卓）内部环境准备：PASS（通过）
- Android（安卓）外部环境准备：PASS（通过）
- Prepare lifecycle（准备生命周期）：PASS（通过）
- Baseline behavior parity（与安卓基线行为一致）：PASS（通过）
- User-observed regression（用户实测回归）：未发现

最终结论：**M1.1 = PASS（通过，云端 + 真机）**

下一阶段：**M1.2 — Environment Plan（环境计划）**

### M1.2 Environment Plan（环境计划）

Cloud Verification（云端验证）：

- ProjectEnvironmentNeeds（项目环境需求）Core model（核心模型）: PASS（通过）
- Provider-neutral preparation policy（提供者中立准备策略）: PASS（通过）
- Pure Python preparation sequence（纯 Python 准备顺序）: PASS（通过）
- Python + Vite preparation order（Python + Vite 准备顺序）: PASS（通过）
- Plain Node.js preparation sequence（普通 Node.js 准备顺序）: PASS（通过）
- Blocking issue fail-closed（阻塞问题安全关闭）: PASS（通过）
- Existing Android ProjectEnvironmentPlan tests（现有安卓环境计划测试）: PASS（通过）
- W0 Cloud Build（W0 云端构建） #681: PASS（通过）
- Internal Alpine Probe（内部 Alpine 探针） #116: PASS（通过）
- APK SHA-256: `049e74acd8a85420f373d32464a4473947c50e2bc0b32e19e649044ce8963fca`

当前结论：**Cloud PASS（云端通过） / Real Device Pending（真机待确认）**

## Stage Exit Summary（阶段退出汇总）

> 本表是 M0～M8（第 0～8 阶段）的最终 Gate（门禁）。Implementation Slice（实现切片）数量不决定阶段是否结束；只看本表对应条件是否全部满足。

| 阶段 | 最终通过条件 |
|---|---|
| M0 Governance（开发治理） | 治理文档、测试矩阵、能力隔离规则、固定工作流程全部建立 |
| M1 Core Boundary（核心边界） | 生命周期、环境需求、项目操作的通用规则进入 Core（核心）；存储/文件/进程边界明确；Core（核心）无平台 API（接口）依赖；Android（安卓）回归通过；核心边界审计通过 |
| M2 macOS Host Skeleton（苹果主机骨架） | 真正的 macOS App（苹果桌面应用）可构建、可启动、可加载 Core（核心）、可读取平台能力，且不依赖 Android Framework（安卓框架） |
| M3 Host Runtime Provider（主机运行提供者） | 可发现本机运行时、启动进程、采集输出、查询状态、项目级停止，并验证两个并行项目互不影响 |
| M4 Project Workflow（项目工作流） | 至少普通 Python（Python 运行时）项目完成导入→检测→计划→准备→运行→日志→停止/重启闭环；Web（网页）入口可验证；错误可诊断 |
| M5 Product UI Parity（产品界面对齐） | Normal Mode（普通模式）与 Developer Mode（开发者模式）均可用，并共享同一 Core（核心）与 Runtime State（运行状态），无双状态机 |
| M6 Container / Multi-service（容器 / 多服务） | 容器能力可检测；标准 Compose（多服务编排）项目完整运行；日志/端口/停止均项目级隔离；无项目专属补丁 |
| M7 OpenBot Acceptance（OpenBot 验收） | 原始 OpenBot（开放机器人）无需源码适配即可检测、准备、启动、访问 Web（网页）、观察、停止并重新启动 |
| M8 Distribution（正式分发） | Developer ID（开发者身份）签名、Hardened Runtime（强化运行时）、Notarization（苹果公证）、Gatekeeper（安全验证）、干净安装、升级和恢复全部通过 |

### Stage Closure Rule（阶段关闭规则）

当某一行的全部条件都有证据并为 PASS（通过）时：

1. 将该一级阶段标记为 PASS（通过）。
2. 停止为该阶段继续创建新的必做 Implementation Slice（实现切片）。
3. 未完成但不阻塞上述退出条件的优化移入 Backlog（待办）。
4. Current State（当前状态）立即切换到下一一级阶段。

这条规则优先于“继续完善”“顺便重构”“还能再优化”等开放式理由。

### Android Foreground Recovery Regression（安卓前台恢复回归）

M1.2（环境计划）真机验收新增阻塞项：

- Normal background -> foreground（普通后台 → 前台）不得进入永久 RECOVERING（恢复中）。
- 健康 RUNNING（运行中）项目返回前台后应保持可观察、可打开、可停止。
- Runtime logs（运行日志）与 Web/result presentation（网页 / 结果呈现）不得因 UI recovery gate（界面恢复门禁）失效。
- Activity recreation（页面重建）仍必须允许一次 persisted recovery（持久化恢复）。
- STOP（停止）后重新 Run（运行）保持正常。

当前状态：**Cloud Verification Pending（云端验证中） / Real Device Pending（真机待确认）**。

Android Foreground Recovery Regression（安卓前台恢复回归）云端结果：

- RuntimeForegroundRecoveryGate（前台恢复门禁）单元测试：PASS（通过）
- W0 Cloud Build（W0 云端构建） #685：PASS（通过）
- Internal Alpine Probe（内部 Alpine 探针） #117：PASS（通过）
- Android APK build（安卓安装包构建）：PASS（通过）
- APK SHA-256：`efb75996e7a92eb5cdb82ebae53d774e3ed8cf881b2883041406b8c4c674022a`

真机仍需验证普通后台 → 前台不会永久进入 RECOVERING（恢复中）。

### M1.2 Real Device Acceptance（真机验收）

- Android Internal Runtime（安卓内部运行时）: PASS（通过）
- Android External Runtime（安卓外部运行时）: PASS（通过）
- Environment Plan behavior parity（环境计划行为与基线一致）: PASS（通过）
- Background → Foreground recovery（后台 → 前台恢复）: PASS（通过）
- Permanent RECOVERING regression（永久恢复中回归）: FIXED / PASS（已修复 / 通过）
- STOP / RUN lifecycle（停止 / 运行生命周期）: PASS（通过）
- User-observed regression（用户实测新增回归）: 未发现

最终结论：

**M1.2 Environment Plan（环境计划）= PASS（通过，云端 + 真机）。**

下一项：

**M1.3 — Project Operation（项目操作）**

### M1.3 Project Operation（项目操作）

Cloud Verification（云端验证）：

- ProjectOperationAction（项目操作类型）: PASS（通过）
- ProjectOperationPhase（项目操作阶段）: PASS（通过）
- ProjectOperationOwnership（项目操作归属）: PASS（通过）
- Same-project mutual exclusion（同项目操作互斥）: PASS（通过）
- STOP supersession（停止抢占）: PASS（通过）
- Duplicate STOP rejection（重复停止拒绝）: PASS（通过）
- Cross-project independence（跨项目互不影响）: PASS（通过）
- Android RuntimeOperationTracker integration（安卓操作跟踪器接线）: PASS（通过）
- Android ProjectOperationCoordinator integration（安卓操作协调器接线）: PASS（通过）
- W0 Cloud Build（W0 云端构建） #691: PASS（通过）
- Internal Alpine Probe（内部 Alpine 探针） #118: PASS（通过）
- APK SHA-256: `7b3aa037642e5818a027a636dce363fd22d694c60f451e348296371f1fc94994`

当前结论：**Cloud PASS（云端通过） / Real Device Pending（真机待确认）**。

### M1.3 Real Device Acceptance（真机验收）

- Single-project operation flow（单项目操作链）: PASS（通过）
- RUNNING → STOP（运行中 → 停止）: PASS（通过）
- Same-project conflict protection（同项目冲突保护）: PASS（通过）
- Project-scoped STOP isolation（项目级停止隔离）: PASS（通过）
- Background → Foreground recovery regression（后台 → 前台恢复回归）: PASS（通过）
- User-observed regression（用户实测新增回归）: 未发现

最终结论：

**M1.3 Project Operation（项目操作）= PASS（通过，云端 + 真机）。**

下一项：

**M1.4 — Platform Storage Interface（平台存储接口）**

### M1.4 Platform Storage Interface（平台存储接口）

Cloud Verification（云端验证）：

- PlatformStateStorage（平台状态存储端口）: PASS（通过）
- Typed primitive persistence contract（类型化基础值存储契约）: PASS（通过）
- Android SharedPreferences Adapter（安卓偏好存储适配器）: PASS（通过）
- RuntimeLifecycleStore（运行生命周期存储）接线: PASS（通过）
- Legacy boolean migration（旧布尔字符串迁移）: PASS（通过）
- RuntimeOperationStore（运行操作存储）接线: PASS（通过）
- Operation generation preservation（操作代际保留）: PASS（通过）
- W0 Cloud Build（W0 云端构建） #698: PASS（通过）
- Internal Alpine Probe（内部 Alpine 探针） #119: PASS（通过）
- APK SHA-256: `af10db03615c8e0a209fb2b69ed8cb7b4d0638f93067a09c82a7423146be6d1b`

当前结论：**Cloud PASS（云端通过） / Real Device Pending（真机待确认）**。

### M1.4 Real Device Acceptance（真机验收）

- Overlay install state continuity（覆盖安装状态连续性）: PASS（通过）
- Existing environment readiness persistence（既有环境就绪状态持久化）: PASS（通过）
- Internal / External environment separation（内部 / 外部环境状态隔离）: PASS（通过）
- Run / Stop / Re-run continuity（运行 / 停止 / 再次运行连续性）: PASS（通过）
- App restart persistence readback（应用重启后的持久化读取）: PASS（通过）
- Background → Foreground recovery regression（后台 → 前台恢复回归）: PASS（通过）
- User-observed regression（用户实测新增回归）: 未发现

最终结论：

**M1.4 Platform Storage Interface（平台存储接口）= PASS（通过，云端 + 真机）。**

下一项：

**M1.5 — Project Filesystem Interface（项目文件系统接口）**

### M1.5 Project Filesystem Interface（项目文件系统接口）

Cloud Verification（云端验证）：

- ProjectFileEntry（项目文件条目）model（模型）: PASS（通过）
- ProjectFilesystem（项目文件系统端口）: PASS（通过）
- Project-relative path safety（项目相对路径安全规则）: PASS（通过）
- Android SAF Adapter（安卓 SAF 适配器）compile integration（编译接线）: PASS（通过）
- Project child listing（项目子项列表）production path（生产路径）: PASS（通过）
- Recursive project tree（递归项目树）production path（生产路径）: PASS（通过）
- Internal staging tree（内部运行暂存树）production path（生产路径）: PASS（通过）
- Bounded file read（有界文件读取）production path（生产路径）: PASS（通过）
- File write/create/rename/delete（文件写入 / 创建 / 重命名 / 删除）production path（生产路径）: PASS（通过）
- W0 Cloud Build（W0 云端构建） #704: PASS（通过）
- Internal Alpine Probe（内部 Alpine 探针） #120: PASS（通过）
- APK SHA-256: `6318488cc7b8c355228e4eb5ceb30708252a7a60b4ac2d98fe41ee49d5c280cf`

当前结论：**Cloud PASS（云端通过） / Real Device Pending（真机待确认）**。

### M1.5 Real Device Acceptance（真机验收）

- Overlay install project access（覆盖安装后项目访问）: PASS（通过）
- Project file tree（项目文件树）: PASS（通过）
- Text file read/write（文本文件读写）: PASS（通过）
- Create file / directory（创建文件 / 文件夹）: PASS（通过）
- Rename / delete（重命名 / 删除）: PASS（通过）
- Internal Runtime staging + Prepare / Run（内部运行时暂存 + 准备 / 运行）: PASS（通过）
- External Runtime project access（外部运行时项目访问）: PASS（通过）
- Stop → Run continuity（停止 → 再次运行连续性）: PASS（通过）
- Background → Foreground regression（后台 → 前台回归）: PASS（通过）
- User-observed regression（用户实测新增回归）: 未发现

最终结论：

**M1.5 Project Filesystem Interface（项目文件系统接口）= PASS（通过，云端 + 真机）。**

下一项：

**M1.6 — Process Control Interface（进程控制接口）**

### M1.6 Real Device Acceptance（真机验收）

- Concurrent project A / B execution（项目 A / B 同时运行）: PASS（通过）
- Stop A while B continues（停止 A、B 继续运行）: PASS（通过）
- Project-scoped STOP isolation（项目级停止隔离）: PASS（通过）
- Project A re-run after STOP（A 停止后再次运行）: PASS（通过）
- Internal Runtime STOP → Run（内部运行时停止 → 再次运行）: PASS（通过）
- External Runtime STOP → Run（外部运行时停止 → 再次运行）: PASS（通过）
- Background → Foreground recovery regression（后台 → 前台恢复回归）: PASS（通过）
- User-observed regression（用户实测新增回归）: 未发现

最终结论：

**M1.6 Process Control Interface（进程控制接口）= PASS（通过，云端 + 真机）。**

下一项：

**M1.7 — Core Boundary Audit（核心边界总审计）**

### M1.7 Core Boundary Audit（核心边界总审计）

| Exit Criterion（退出条件） | Result（结果） |
| --- | --- |
| Runtime Lifecycle（运行生命周期）in Core（核心） | PASS（通过） |
| Environment Needs / Plan（环境需求 / 环境计划）in Core（核心） | PASS（通过） |
| Generic Project Operation（通用项目操作）contract（契约） | PASS（通过） |
| Platform Storage Interface（平台存储接口） | PASS（通过） |
| Project Filesystem Interface（项目文件系统接口） | PASS（通过） |
| Process Control Interface（进程控制接口） | PASS（通过） |
| Core imports no platform API（核心无平台 API 导入） | PASS（通过） |
| Core module remains pure Kotlin/JVM（核心保持纯 Kotlin/JVM） | PASS（通过） |
| Android automated regression（安卓自动回归） | PASS（通过） |
| Android key real-device regression（安卓关键真机回归） | PASS（通过） |
| Capability Isolation（能力隔离） | PASS（通过） |

最终结论：

**M1 — Core Boundary（核心边界）= PASS（通过） / CLOSED（关闭）。**

下一阶段：

**M2 — macOS Host Skeleton（macOS 主机应用骨架）**。
