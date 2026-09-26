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

当前状态：**PASS / CLOSED（通过 / 关闭）**

最终切片：**M2.1 — Minimal macOS Host + Core Load（最小 macOS 主机应用 + Core 加载）**

当前证据状态：

- macOS host source（苹果宿主源码）: IMPLEMENTED（已实现）
- Core direct dependency（核心直接依赖）: IMPLEMENTED（已实现）
- Capability snapshot read（能力快照读取）: IMPLEMENTED（已实现）
- macOS cloud build（苹果云端构建）: PASS（通过），Run #1
- Core load probe（核心加载探针）: PASS（通过）
- Capability snapshot read（能力快照读取）: PASS（通过）
- Android dependency leak check（安卓依赖泄漏检查）: PASS（通过）
- SiftAlpha.app packaging（苹果应用打包）: PASS（通过）
- Android regression（安卓回归）: PASS（通过），W0 #709
- Real Mac launch / exit（真实 Mac 启动 / 退出）: PASS（通过，macOS 10.15.7 Catalina）
- Real Mac acceptance（真实 Mac 验收）: PASS（通过，用户确认）

M2 final completion（最终完成度）：**6 / 6 — PASS / CLOSED（通过 / 关闭）**。

## M3 — Host Runtime Provider（主机运行提供者）

必须证明：

- Python（Python 运行时）发现。
- Node.js（Node 运行时）发现。
- Bun（Bun 运行时）发现。
- Git（版本控制）发现。
- Shell（命令环境）发现。
- 启动一个项目进程。
- 捕获 stdout / stderr（标准输出 / 标准错误）。
- STATUS（状态）正确。
- STOP（停止）只影响当前项目。
- 另一个同时运行的项目不受影响。

当前状态：**PASS / CLOSED（通过 / 关闭）**

## M4 — Project Workflow（项目工作流）

当前状态：**PASS / CLOSED（通过 / 关闭）**

必须证明：

- Import（导入）成功。
- Detect（检测）成功。
- Environment Plan（环境计划）正确。
- Prepare（准备）成功。
- Run（运行）成功。
- Logs（日志）可读。
- Web Endpoint（网页端点）可发现和验证。
- Stop / Restart（停止 / 重启）闭环。


## M5 — Product UI Parity（产品界面对齐）

必须证明：

- Normal Mode（普通模式）可以完成导入 → 准备 → 运行 → 查看结果 → 停止。
- Developer Mode（开发者模式）可以查看高级运行事实。
- 两种模式读取同一个 Runtime（运行时）状态。
- 不存在两套独立生命周期。

当前状态：**M5.1 PASS / COMPLETE；M5.2 PASS / COMPLETE；M5 PASS / CLOSED**

## M6 — Container / Multi-service（容器 / 多服务）

必须证明：

- Docker / Podman（容器）能力检测。
- Container Capability（容器能力）可明确 AVAILABLE / UNAVAILABLE / UNKNOWN（可用 / 不可用 / 未知）。
- Compose（多服务编排）项目启动。
- 多服务日志归属正确。
- 端口映射可发现。
- project-scoped STOP（项目级停止）不影响其他项目。

当前状态：**M6.1 PASS / COMPLETE；M6.2 PASS / COMPLETE（真实 Ventura 验收已通过）**

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

当前状态：**IN PROGRESS（进行中）— Managed VM Readiness / DNS Recovery blocker**

### M6 Final Closure Audit — PASS

The final installed Ventura acceptance passed on the current Intel Mac. It covers the real A/B Compose lifecycle, Web endpoint access, project-scoped Stop, Restart, Docker/Compose availability, and the automatic temporary port override path. M6 is closed; M7 can start with the original OpenBot project.

### Managed Container Network Inheritance Regression Repair — CLOUD PASS

This bounded macOS Adapter repair keeps M6 CLOSED and does not create M6.3.

| 验证项 | 结果 |
|---|---|
| Managed Compose proxy inheritance with System Proxy ON | PASS（大小写代理变量均由现有系统代理策略生成） |
| Managed Compose stale proxy clearing with System Proxy OFF | PASS |
| External Docker / Podman proxy isolation | PASS |
| Registry `connection reset by peer` classification | PASS |
| Existing timeout / refused / EOF / loopback DNS recovery regression | PASS |
| macOS Host Runtime CI | PASS，Run `36154688284` |
| Android W0 Shared Core boundary regression | PASS，Run `36154688417` |
| Real Mac Easy-TDX Prepare acceptance | PENDING（待真实设备验收） |

### M7 Readiness / Developer Toolbar Repair — 2026-09-26

| Check | Expected | Status |
|---|---|---|
| Managed VM SSH readiness retry | bounded retry, `MANAGED_VM_SSH_READY=PASS/FAILED` | PASS |
| SSH failure boundary | no `/etc/resolv.conf` `rm` / `tee` before SSH readiness | PASS |
| Resolver inspection | DNS recovery only follows successful `resolv.conf` read | PASS |
| Resolver inspection failure | explicit inspection failure, no blind DNS mutation | PASS |
| Generation recovery | stale recovery does not reclassify the same live generation | PASS |
| New generation cleanup | new `begin()` clears stale recovery state without deleting history | PASS |
| Developer toolbar normal width | 13 buttons reflow to 2 rows | PASS |
| Developer toolbar narrow width | 13 buttons reflow to 3 rows | PASS |
| Very narrow layout | no zero-column result, crash, or relayout loop | PASS |
| macOS Host Runtime CI | compile/tests/package | PASS — Run `36210492001` |
| Android W0 regression | shared Core/Android build | PASS — Run `36210491934` |
| Real Mac OpenBot acceptance | original OpenBot Detect → Prepare → Run → Web → Logs → Stop → Restart | PENDING |

Result: **M7 IN PROGRESS / CLOUD PASS / REAL MAC PENDING**.

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


### M2.1 Catalina compatibility repair

| Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- |
| CI runner architecture（云端运行器架构） | Intel x86_64 | PASS |
| Packaging JDK（打包 Java） | Liberica JDK 17 x64 | PASS |
| Info.plist minimum OS（最低系统版本） | 10.15 | PASS |
| SiftAlpha launcher Mach-O minimum OS | <= 10.15 | PASS — 10.12 |
| bundled libjli Mach-O minimum OS | <= 10.15 | PASS — 10.12 |
| bundled libjvm Mach-O minimum OS | <= 10.15 | PASS — 10.12 |
| Core load probe（核心加载探针） | PASS | PASS |
| Android dependency leak（安卓依赖泄漏） | none | PASS |
| Catalina 10.15.7 real launch / exit（真实启动 / 退出） | PASS | PASS — user confirmed（用户确认） |
| M2 final status（M2 最终状态） | all 6 exit criteria satisfied | PASS / CLOSED（通过 / 关闭） |


### M2 Final Real Mac Acceptance（M2 最终真实 Mac 验收）

| Exit Criterion（退出条件） | Evidence（证据） | Result（结果） |
| --- | --- | --- |
| Real macOS App exists / builds（真实 macOS App 可构建） | macOS Host Skeleton Run #4; Catalina x64 artifact | PASS |
| App launch / exit（应用启动 / 退出） | user-tested on macOS 10.15.7 Catalina | PASS |
| Loads SiftAlpha Core（加载核心） | cloud probe + visible real-Mac capability window | PASS |
| Reads PlatformCapabilitySnapshot（读取能力快照） | real-Mac window shows 3 UNKNOWN capability facts | PASS |
| No Android Framework dependency（无安卓框架依赖） | dependency leak check | PASS |
| Real Mac acceptance（真实 Mac 验收） | user confirmed launch and normal exit | PASS |

**M2 final result（最终结果）: 6 / 6 — PASS / CLOSED（通过 / 关闭）**

Next stage（下一阶段）: **M3 — Host Runtime Provider（主机运行提供者）**。


### M3.1 Verification Matrix（验证矩阵）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| M3-01 | Python discovery（Python 发现） | explicit AVAILABLE / UNAVAILABLE fact | CLOUD PASS |
| M3-02 | Node.js discovery（Node 发现） | explicit AVAILABLE / UNAVAILABLE fact | CLOUD PASS |
| M3-03 | Bun discovery（Bun 发现） | explicit AVAILABLE / UNAVAILABLE fact | CLOUD PASS — UNAVAILABLE is valid |
| M3-04 | Git discovery（Git 发现） | explicit AVAILABLE / UNAVAILABLE fact | CLOUD PASS |
| M3-05 | Shell discovery（Shell 发现） | explicit AVAILABLE / UNAVAILABLE fact | CLOUD PASS |
| M3-06 | Host process start（主机进程启动） | project-owned process starts | CLOUD PASS |
| M3-07 | stdout / stderr capture（输出采集） | both streams captured independently | CLOUD PASS |
| M3-08 | STATUS（状态） | RUNNING / EXITED / STOPPED facts are correct | CLOUD PASS |
| M3-09 | Project STOP（项目停止） | selected project stops | CLOUD PASS |
| M3-10 | Concurrent A/B isolation（并行项目隔离） | STOP A leaves B RUNNING | CLOUD PASS |
| M3-11 | Android regression（安卓回归） | W0 PASS | PASS — W0 #718 |
| M3-12 | Catalina compatibility（Catalina 兼容） | packaged app remains compatible with 10.15.7 | CLOUD PASS |
| M3-13 | Real Mac M3 Self-Test（真实 Mac 自检） | diagnostic button reports PASS | PASS — macOS 10.15.7 real Mac |


### M3.1 Catalina passive discovery regression

| Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- |
| Opening M3 app without CLT（未安装命令行工具时打开） | no Apple installer prompt | PASS — real Catalina |
| Catalina `/usr/bin/python3` shim | skipped when CLT absent | CLOUD TEST PASS |
| Catalina `/usr/bin/git` shim | skipped when CLT absent | CLOUD TEST PASS |
| Legacy Python 2.7 | not accepted as SiftAlpha host Python | CLOUD TEST PASS |
| User-installed Python 3 / Git | remains discoverable | CLOUD PASS |
| Real Mac M3 Self-Test（真实 Mac 自检） | PASS after passive discovery repair | PASS — real Catalina |


### M3 Final Closure Audit（M3 最终关闭审计） — 2026-09-24

| Exit Criterion（退出条件） | Final Evidence（最终证据） | Result（结果） |
| --- | --- | --- |
| Python discovery（Python 发现） | explicit fact; Python 2 rejected; Python 3 cloud probe | PASS |
| Node.js discovery（Node 发现） | explicit AVAILABLE / UNAVAILABLE fact | PASS |
| Bun discovery（Bun 发现） | explicit UNAVAILABLE is valid | PASS |
| Git discovery（Git 发现） | explicit fact; Catalina shim safely skipped without CLT | PASS |
| Shell discovery（Shell 发现） | real Catalina zsh 5.7.1 AVAILABLE | PASS |
| Host process start（主机进程启动） | cloud self-test + real self-test | PASS |
| stdout / stderr capture（输出采集） | process-control tests / probe | PASS |
| STATUS（状态） | RUNNING / STOPPED facts verified | PASS |
| Project STOP（项目停止） | selected project stops | PASS |
| Concurrent A/B isolation（并行隔离） | STOP A leaves B RUNNING | PASS |
| Android regression（安卓回归） | W0 #719 | PASS |
| Catalina compatibility（Catalina 兼容） | Run #10; MIN_OS 10.12; real 10.15.7 launch | PASS |
| Real Mac M3 Self-Test（真实 Mac M3 自检） | user screenshot: Process self-test: PASS | PASS |

**M3 final result（最终结果）: PASS / CLOSED（通过 / 关闭）**

Next stage（下一阶段）: **M4 — Project Workflow（项目工作流）**.


### Post-M3 Governance / Brand Verification（M3 后治理 / 品牌复验）

| Check（检查） | Evidence（证据） | Result（结果） |
| --- | --- | --- |
| SiftAlpha X macOS display/package identity | Run #11 packages `SiftAlpha X.app` | PASS |
| Catalina compatibility after rename（改名后 Catalina 兼容） | launcher / libjli / libjvm MIN_OS = 10.12 | PASS |
| M3 process behavior after rename（改名后 M3 进程行为） | process probe + A/B STOP isolation | PASS |
| Android regression after governance/brand cleanup（治理/品牌清理后安卓回归） | W0 #720 | PASS |
| Stage boundary（阶段边界） | M3 remains closed; M4 not implemented | PASS |

This table is post-closure verification only; it does not add new M3 Exit Criteria（退出条件）.


### M4.1 Import + Detect + Plan Verification（验证）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| M4.1-01 | Shared runtime detector（共享运行时检测器） | Android/macOS use Core policy | PASS |
| M4.1-02 | macOS folder import（目录导入） | canonical project identity | PASS |
| M4.1-03 | Filesystem containment（文件系统边界） | no root/symlink escape | PASS |
| M4.1-04 | Python detect（Python 检测） | primary=python | PASS |
| M4.1-05 | Node detect（Node 检测） | primary=nodejs | PASS |
| M4.1-06 | Ambiguous project（歧义项目） | BLOCKED, no guessing | PASS |
| M4.1-07 | Missing runtime（运行时缺失） | RUNTIME_MISSING | PASS |
| M4.1-08 | Environment Needs（环境需求） | provider-neutral plan | PASS |
| M4.1-09 | Android regression（安卓回归） | W0 PASS | PASS — W0 #724 |
| M4.1-10 | Catalina packaging（Catalina 打包） | PASS, minimum unchanged | PASS — Run #15 |
| M4.1-11 | Real Mac import/detect/plan（真实 Mac 导入/检测/计划） | PASS | PASS — real Catalina |


### M4.1 packaged launcher regression / repair

| Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- |
| Generated launcher config | no standalone `java-options=X` | PASS — Run #17 |
| M4.1 plan probe after launcher repair | PASS | PASS — Run #17 |
| M3 process regression after launcher repair | PASS | PASS — Run #17 |
| Catalina package after launcher repair | PASS / MIN_OS unchanged | PASS — Run #17 |
| Android regression after launcher repair | W0 PASS | PASS — W0 #726 |
| Real Mac double-click launch | app window opens | PASS — real Catalina |
| Real Mac Import → Detect → Plan | expected project facts | PASS — python / RUNTIME_MISSING |


### M4.1 Slice Completion（切片完成）

All M4.1 verification items are now PASS, including real macOS 10.15.7 acceptance.

**M4.1 = PASS / COMPLETE（通过 / 完成）**

Next slice:
**M4.2 — Prepare + Run + Observe + Stop/Restart（准备 + 运行 + 观察 + 停止/重启） — NOT STARTED**.


### M4.2 Prepare + Run + Observe + Stop/Restart Verification（验证）

| ID | Check（检查） | Evidence（证据） | Status（状态） |
| --- | --- | --- | --- |
| M4.2-01 | Bundled managed Python（内置托管 Python） | CPython 3.14.7 x86_64 + checksum | PASS |
| M4.2-02 | Catalina runtime compatibility（运行时 Catalina 兼容） | Python/libpython MIN_OS=10.15 | PASS |
| M4.2-03 | Project environment generation（项目环境代际） | venv + atomic current pointer | PASS |
| M4.2-04 | Wheel-only dependency install（仅 wheel 依赖安装） | idna==3.10 prepare probe | PASS |
| M4.2-05 | Environment verification（环境验证） | python version + pip check | PASS |
| M4.2-06 | Python entrypoint run（Python 入口运行） | run=PASS | PASS |
| M4.2-07 | Logs（日志） | Web URL observed from project output | PASS |
| M4.2-08 | Web discovery + endpoint probe（网页发现 + 端点探测） | loopback endpoint verified | PASS |
| M4.2-09 | Project STOP（项目停止） | stopped_state=STOPPED | PASS |
| M4.2-10 | Restart（重新运行） | new Web endpoint after restart | PASS |
| M4.2-11 | Final STOP（最终停止） | final_stop=true | PASS |
| M4.2-12 | M3 A/B isolation regression（M3 隔离回归） | M3 process probe | PASS |
| M4.2-13 | Android regression（安卓回归） | W0 #730 | PASS |
| M4.2-14 | Catalina app packaging（Catalina 应用打包） | Run #21 | PASS |
| M4.2-15 | Real Mac full workflow（真实 Mac 完整闭环） | user acceptance | PASS — real Catalina |

M4.2 is not closed until M4.2-15 passes.


### M4 Final Closure Audit（M4 最终关闭审计）

| Exit Criterion（退出条件） | Final Evidence（最终证据） | Result（结果） |
| --- | --- | --- |
| Import（导入） | M4.1 real Catalina | PASS |
| Detect + Plan（检测 + 计划） | M4.1 real Catalina | PASS |
| Prepare（准备） | bundled CPython 3.14.7, real Catalina | PASS |
| Dependency install（依赖安装） | idna 3.10 real install | PASS |
| Run（运行） | real START SUCCESS | PASS |
| Logs（日志） | real stdout evidence | PASS |
| Web entry（Web 入口） | real loopback endpoint verified | PASS |
| STOP（停止） | real STOPPED + M3 isolation | PASS |
| Restart（重新运行） | second START SUCCESS | PASS |
| Diagnosability（可诊断） | structured lifecycle/process/prepare facts | PASS |
| Catalina support（Catalina 支持） | real 10.15.7 + MIN_OS checks | PASS |
| Android regression（安卓回归） | W0 #730 | PASS |

**M4 = PASS / CLOSED（通过 / 关闭）**

Next stage:
**M5 — Product UI Parity（产品界面对齐） — PASS / CLOSED（通过 / 关闭）**.


### M5.1 Normal Mode Product UI Verification（验证）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| M5.1-01 | Product shell（产品外壳） | SiftAlpha X desktop Projects + Workspace | PASS — Run #23 |
| M5.1-02 | Project search/filter（项目搜索/筛选） | All / Python / Node | PASS — compiled product UI |
| M5.1-03 | One Primary Action（单一主操作） | lifecycle-derived, no second state machine | PASS — M5.1 probe |
| M5.1-04 | Import → Prepare | primary PREPARE then RUN | PASS — M5.1 probe |
| M5.1-05 | Run → Result | result becomes primary Open Result | PASS — M5.1 probe |
| M5.1-06 | Running result stop access | secondary Stop remains available | PASS — M5.1 probe |
| M5.1-07 | Stop → Run | primary returns to RUN | PASS — M5.1 probe |
| M5.1-08 | Original brand artwork（原始品牌图） | packaged resource present | PASS — packaged jar |
| M5.1-09 | M4 workflow regression（M4 工作流回归） | M4.2 full workflow PASS | PASS — Run #23 |
| M5.1-10 | Android regression（安卓回归） | W0 PASS | PASS — W0 #732 |
| M5.1-11 | Catalina packaging（Catalina 打包） | minimum unchanged | PASS — Run #23 |
| M5.1-12 | Real Mac Normal Mode acceptance（真实 Mac 普通模式验收） | full ordinary-user flow PASS | PASS — real Catalina 10.15.7 / Intel x86_64 |


### M5.1 Final Closure（M5.1 最终收尾）

M5.1 final status: **12 / 12 PASS — PASS / COMPLETE**.

Authority:
- macOS Run #23 PASS;
- Android W0 #732 PASS;
- real macOS 10.15.7 Catalina / Intel x86_64 acceptance PASS.

M5.2 is now PASS / COMPLETE. No additional M5.1 verification is required unless a later regression explicitly touches this surface.


### M5.2 Developer Mode + Shared State Parity Verification（验证）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| M5.2-01 | Developer Mode surface | Simplified Chinese UI + direct Import + advanced Project / Runtime / Process / Web / Logs facts | PASS — Run #34 |
| M5.2-02 | Single controller | Normal + Developer use one MacProductController | PASS — source + probe |
| M5.2-03 | Single coordinator | no Developer coordinator/runtime state | PASS — source audit |
| M5.2-04 | Normal running → Developer | Developer immediately reads RUNNING | PASS — M5.2 probe |
| M5.2-05 | Shared Runtime version | Managed Python 3.14.7 | PASS — M5.2 probe |
| M5.2-06 | Shared Environment | same generation in both presentations | PASS — M5.2 probe |
| M5.2-07 | Shared Web result | same result URL | PASS — M5.2 probe |
| M5.2-08 | Shared Logs | same raw/combined logs | PASS — M5.2 probe |
| M5.2-09 | Mode switch side effect | owned PID stable; no restart/stop | PASS — M5.2 probe |
| M5.2-10 | Developer Stop → Normal | Normal returns to RUN-ready | PASS — M5.2 probe |
| M5.2-11 | Normal Run → Developer | Developer returns to RUNNING | PASS — M5.2 probe |
| M5.2-12 | Project A/B STOP isolation | Stop A leaves B RUNNING | PASS — M5.2 + M3 probes |
| M5.2-13 | M5.1 regression | Normal Mode product flow remains PASS | PASS — Run #34 |
| M5.2-14 | M4.2 regression | managed-Python full workflow remains PASS | PASS — Run #34 |
| M5.2-15 | Android regression | Android W0 PASS | PASS — W0 #743 |
| M5.2-16 | Catalina packaging | minimum remains 10.15.7-compatible | PASS — Run #34 |
| M5.2-17 | Real Mac shared-state acceptance | frozen Normal ↔ Developer scenario on Catalina | PASS — real Catalina 10.15.7 / Intel x86_64 |

Current M5.2 result:
**17 / 17 PASS — PASS / COMPLETE**.

M5 Final Closure Audit（最终关闭审计）: **PASS / CLOSED**. Do not create M5.3 / M5.4.


#### M5.2 real-Mac repair note

Before final real-Mac acceptance, the Developer Mode surface was repaired to add direct project import and Simplified Chinese presentation. Repair HEAD `9e6f3ccb9259704f3efe4f898a97ae3f054f1b33`; macOS Run #34 PASS; Android W0 #743 PASS. M5.2-17 is PASS on the real Catalina machine; no M5 closure blocker remains.


### M5 Final Closure Audit（最终关闭审计）

| Frozen Exit Criterion（冻结退出条件） | Status（状态） |
| --- | --- |
| Normal Mode Import → Prepare → Run → Result → Stop | PASS |
| Developer Mode advanced Runtime / Process / Web / Logs facts | PASS |
| Normal Mode and Developer Mode read the same Runtime State | PASS |
| No second Lifecycle / Coordinator | PASS |
| Android W0 regression | PASS — #743 |
| Catalina 10.15.7 real Mac acceptance | PASS |

**M5 = PASS / CLOSED（通过 / 关闭）**.

Backlog-only polish cannot reopen M5. Next stage is M6 — Container / Multi-service（容器 / 多服务）, currently NOT STARTED.


### Current macOS support baseline for M6+

| Item | Current Authority |
| --- | --- |
| Full-support minimum | macOS 13 Ventura |
| Real acceptance OS | macOS 13.7.8 Ventura |
| Real acceptance architecture | Intel x86_64 |
| Historical Catalina M2～M5 evidence | retained as historical PASS evidence |
| Catalina compatibility required for M6/M7/M8 | NO |

Future M6/M7/M8 verification must use the current Ventura baseline. Older Catalina deployment-target evidence remains historical only and must not block modern container/runtime implementation.


### M6.1 Container Capability + Compose Detection/Plan Verification（验证）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| M6.1-01 | Core capability model | CONTAINER_RUNTIME uses AVAILABLE / UNAVAILABLE / UNKNOWN | PASS — Core tests |
| M6.1-02 | Docker discovery | CLI/runtime/Compose facts are adapter-owned | PASS — macOS tests |
| M6.1-03 | Podman discovery | CLI/runtime/Compose facts are adapter-owned | PASS — macOS tests |
| M6.1-04 | Compose manifest detection | standard root manifest names recognized deterministically | PASS — Core tests / Run #36 |
| M6.1-05 | Multi-service parsing | service list extracted | PASS — db,web |
| M6.1-06 | Service dependencies | depends_on preserved | PASS — web → db |
| M6.1-07 | Port plan | published / target facts preserved | PASS — 18080 → 80 |
| M6.1-08 | Capability unavailable | valid plan state, not architecture failure | PASS — CAPABILITY_UNAVAILABLE |
| M6.1-09 | Capability unknown | valid plan state | PASS — CAPABILITY_UNKNOWN |
| M6.1-10 | Existing Python/Node workflow regression | M4.1 + M4.2 | PASS — Run #36 |
| M6.1-11 | Product UI/shared-state regression | M5.1 + M5.2 | PASS — Run #36 |
| M6.1-12 | Project-scoped STOP regression | A/B isolation preserved | PASS — Run #36 |
| M6.1-13 | Android regression | W0 PASS | PASS — W0 #745 |
| M6.1-14 | Ventura application baseline | LSMinimumSystemVersion 13.0 | PASS — Run #36 |
| M6.1-15 | Real Ventura capability/detection acceptance | macOS 13.7.8 / Intel x86_64 | PASS — real probe: container UNAVAILABLE, Docker UNAVAILABLE, Podman UNAVAILABLE, managed Python 3.14.7 AVAILABLE |

Current M6.1 result:
**15 / 15 PASS — PASS / COMPLETE**.

M6.2 Compose Runtime Workflow + Project Isolation remains NOT STARTED.


### M6.2 Compose Runtime Workflow + Project Isolation Verification（验证）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| M6.2-01 | Universal provider boundary | no machine/vendor special case in Core | PASS |
| M6.2-02 | Environment Advisor | system/resource/provider facts produce advice without auto-install | PASS |
| M6.2-03 | Provider selection | compatible Docker preferred; compatible Podman allowed | PASS |
| M6.2-04 | Stable project identity | A and B receive distinct stable Compose identities | PASS |
| M6.2-05 | Compose Prepare | image pull/build path routed through provider | PASS — controlled provider probe |
| M6.2-06 | Compose Run | A and B start through shared coordinator | PASS |
| M6.2-07 | Multi-service status | db + web reported RUNNING | PASS |
| M6.2-08 | Compose Logs | project-scoped logs available | PASS |
| M6.2-09 | Runtime port discovery | active mapped port → Endpoint Probe → Web URL | PASS — source CONTAINER_PORT |
| M6.2-10 | Project-scoped STOP | Stop A does not stop B | PASS — B remains RUNNING |
| M6.2-11 | Restart | stopped A returns to RUNNING | PASS |
| M6.2-12 | Normal / Developer shared state | one Controller / Coordinator | PASS |
| M6.2-13 | M4/M5/M6.1 regressions | previous workflows remain valid | PASS — Run #39 |
| M6.2-14 | Android regression | W0 PASS | PASS — W0 #748 |
| M6.2-15 | Ventura package | LSMinimumSystemVersion = 13.0 | PASS — Run #39 |
| M6.2-16 | Real supported Mac with real Docker/Podman + Compose | full Prepare→Run→Status→Logs→Web→Stop→Restart and A/B isolation | REAL VENTURA PENDING |

Current M6.2 result:
**15 / 16 PASS — CLOUD PASS / REAL VENTURA PENDING**.

M6 Final Closure Audit remains NOT STARTED.


### M6.2 Managed Container Installer Verification（验证）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| M6.2-I01 | Missing provider detection | Compose project receives install recommendation | PASS |
| M6.2-I02 | User confirmation boundary | no install before explicit confirmation | PASS |
| M6.2-I03 | Managed install plan | INSTALL_MANAGED_DOCKER generated generically | PASS |
| M6.2-I04 | Intel / ARM architecture selection | x86_64 and arm64 supported without model-specific branches | PASS |
| M6.2-I05 | Package manager independence | no Homebrew/MacPorts required | PASS |
| M6.2-I06 | Config isolation | managed Docker/Colima/Lima state does not overwrite user Docker config | PASS |
| M6.2-I07 | Re-detect | provider becomes READY after controlled install | PASS |
| M6.2-I08 | Auto-continue | successful install automatically continues Prepare | PASS |
| M6.2-I09 | Normal Mode transition | INSTALL_CONTAINER → RUN | PASS |
| M6.2-I10 | Upstream assets | Intel + ARM managed assets reachable | PASS — Run #56 |
| M6.2-I11 | Android regression | W0 remains PASS | PASS — #765 |
| M6.2-I12 | Real Ventura managed install | real download/start/verify/Prepare on supported Mac | PENDING |
| M6.2-I13 | Real provider A/B isolation | A/B Run, Stop A leaves B RUNNING, Restart A | PENDING |

Current managed-installer result:
**11 / 13 PASS — CLOUD PASS / REAL VENTURA ACCEPTANCE PENDING**.


### M6.2 Unified Prepare UX Verification（验证）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| M6.2-U01 | Normal Mode primary action | missing managed capability is shown as PREPARE, not INSTALL_CONTAINER | PASS — Run #69 |
| M6.2-U02 | One-click preparation | PREPARE invokes required environment provisioning exactly once | PASS — Run #69 |
| M6.2-U03 | Shared provisioning | Normal / Developer use the same controller/provisioner | PASS |
| M6.2-U04 | Developer diagnostics | install plan / phase / components / side effects / raw log exposed | PASS |
| M6.2-U05 | Developer repair action | explicit install/repair entry available when a plan exists | PASS |
| M6.2-U06 | Re-detect | provider becomes READY after controlled provision | PASS |
| M6.2-U07 | Auto-continue | successful provision continues project Prepare | PASS |
| M6.2-U08 | Final Normal state | environmentReady=true and primary action RUN | PASS |
| M6.2-U09 | Existing Compose lifecycle | Run / Logs / Web / Stop / Restart unchanged | PASS |
| M6.2-U10 | A/B project isolation | Stop A leaves B RUNNING | PASS |
| M6.2-U11 | Android regression | W0 remains PASS | PASS — #778 |
| M6.2-U12 | Real Ventura one-click Prepare | real machine provisions required environment and reaches RUN | PENDING |
| M6.2-U13 | Real Developer diagnostics | real failure/success phases and installer log visible | PENDING |
| M6.2-U14 | Real Compose A/B isolation | A/B run, Stop A leaves B RUNNING, Restart A | PENDING |

Current result:
**11 / 14 PASS — CLOUD PASS / REAL VENTURA ACCEPTANCE PENDING**.


### M6.2 Checksum Manifest Regression Verification（校验清单回归）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| M6.2-C01 | Multi-file manifest exact match | Lima x86_64 selects its own digest, not the first manifest line | PASS |
| M6.2-C02 | Unknown named asset | no unrelated digest fallback | PASS |
| M6.2-C03 | Single-digest sidecar | bare SHA-256 remains accepted | PASS |
| M6.2-C04 | macOS regression | Core/macOS tests and M6 probes pass | PASS — Run #73 |
| M6.2-C05 | Android regression | W0 remains PASS | PASS — #782 |
| M6.2-C06 | Real Ventura retry | Prepare passes checksum and proceeds to next installer phase | PENDING |

Current checksum-repair result:
**5 / 6 PASS — CLOUD PASS / REAL VENTURA RETEST PENDING**.


### M6.2 Buildx Darwin Digest Verification（验证）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| M6.2-B01 | Buildx Darwin manifest behavior | installer does not expect Darwin in generic checksums.txt | PASS |
| M6.2-B02 | Buildx x86_64 digest | official v0.37.1 release digest pinned | PASS |
| M6.2-B03 | Buildx arm64 digest | official v0.37.1 release digest pinned | PASS |
| M6.2-B04 | Binary-mode checksum syntax | `*filename` entries parse correctly | PASS |
| M6.2-B05 | macOS regression | Core/macOS + M6 probes PASS | PASS — Run #77 |
| M6.2-B06 | Android regression | W0 PASS | PASS — #786 |
| M6.2-B07 | Real Ventura retry | Buildx verifies and installer advances beyond download phase | PENDING |

Current Buildx repair result:
**6 / 7 PASS — CLOUD PASS / REAL VENTURA RETEST PENDING**.


### M6.2 R5 Managed Container Path Safety Verification（路径安全验证）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| M6.2-P01 | Normal home path | short home-local state root selected | PASS |
| M6.2-P02 | Long ASCII home path | compact home-local state root selected | PASS |
| M6.2-P03 | Non-ASCII home path | UTF-8 byte length determines safe root | PASS |
| M6.2-P04 | Extreme home path | deterministic isolated short system fallback selected | PASS |
| M6.2-P05 | Toolchain/state separation | versioned toolchain path does not lengthen Lima socket path | PASS |
| M6.2-P06 | Project/provider isolation | no user Docker/Colima configuration override | PASS |
| M6.2-P07 | macOS regressions | Core/macOS, M4/M5/M6 probes PASS | PASS — Run #85 |
| M6.2-P08 | Android regression | W0 PASS | PASS — #794 |
| M6.2-P09 | Real Ventura VM start | Colima/Lima advances beyond socket creation | PENDING |
| M6.2-P10 | Real Compose acceptance | Prepare → Run → A/B isolation → Restart | PENDING |

Current R5 result:
**8 / 10 PASS — CLOUD PASS / REAL VENTURA RETEST PENDING**.


### M6.2 R6 Compose Prepare Diagnostics Verification（诊断验证）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| M6.2-D01 | Failed Compose operation | operation name + exit code retained | PASS |
| M6.2-D02 | Failed Compose stdout/stderr | useful output tail retained in error detail | PASS |
| M6.2-D03 | Large failure output | diagnostic detail bounded | PASS |
| M6.2-D04 | Developer Environment tab | LAST_ERROR + Compose Prepare Diagnostics shown | PASS |
| M6.2-D05 | macOS regressions | Core/macOS, M4/M5/M6 probes PASS | PASS — Run #90 |
| M6.2-D06 | Android regression | W0 PASS | PASS — #799 |
| M6.2-D07 | Real Compose failure diagnosis | root cause visible without Terminal/manual reproduction | PENDING |
| M6.2-D08 | Real Compose Prepare | Prepare succeeds after root-cause repair | PENDING |

R5 path acceptance is already REAL PASS. M6.2 remains open for real Compose Prepare / Run / A-B isolation acceptance.


### Installed-test DMG / App Translocation Verification（安装与转移保护）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| DIST-PRE-01 | macOS DMG build | DMG produced by CI | PASS — Run #95 |
| DIST-PRE-02 | DMG integrity | `hdiutil verify` succeeds | PASS — Run #95 |
| DIST-PRE-03 | DMG application payload | contains `SiftAlpha X.app` | PASS |
| DIST-PRE-04 | Applications target | DMG contains symlink to `/Applications` | PASS |
| DIST-PRE-05 | Installed path guard | packaged app under `/Applications` is allowed | PASS |
| DIST-PRE-06 | App Translocation guard | translocated packaged path is blocked | PASS |
| DIST-PRE-07 | Downloads/non-installed guard | packaged app outside `/Applications` is blocked | PASS |
| DIST-PRE-08 | Development/CI path | non-packaged probe execution remains allowed | PASS |
| DIST-PRE-09 | Android regression | W0 remains PASS | PASS — #804 |
| DIST-PRE-10 | Real DMG install | drag to Applications / replace / launch from Applications | PENDING |
| DIST-PRE-11 | Real no-translocation launch | no App Translocation warning on installed build | PENDING |

Current result:
**9 / 11 PASS — CLOUD PASS / REAL INSTALLED-DMG ACCEPTANCE PENDING**.


### M6.2 R8 Managed DNS Repair Verification（托管 DNS 修复验证）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| M6.2-D01 | macOS resolver discovery | non-loopback `scutil --dns` resolvers selected | PASS |
| M6.2-D02 | loopback-only host DNS | safe fallback resolver set selected | PASS |
| M6.2-D03 | failure classifier | `[::1]:53` / `127.x.x.x:53` connection failure recognized | PASS |
| M6.2-D04 | external DNS error | non-loopback DNS/NXDOMAIN is not treated as managed-loopback repair | PASS |
| M6.2-D05 | Colima launch args | explicit resolver list becomes repeated `--dns` arguments | PASS |
| M6.2-D06 | managed-only repair boundary | automatic restart applies only to SiftAlpha-managed Docker | PASS |
| M6.2-D07 | one retry boundary | Compose Prepare is retried only after a successful managed DNS repair | PASS |
| M6.2-D08 | macOS regressions | M4/M5/M6 and package verification PASS | PASS — Run #100 |
| M6.2-D09 | Android regression | W0 remains PASS | PASS — #809 |
| M6.2-D10 | Real installed Mac | Docker Hub image pull succeeds after DNS repair | PENDING |
| M6.2-D11 | Real Compose workflow | Prepare → Run → A/B isolation → Restart | PENDING |

Current R8 result:
**9 / 11 PASS — CLOUD PASS / REAL INSTALLED-MAC RETEST PENDING**.


### M6.2 R9 Managed VM Resolver Recovery Verification（托管 VM DNS 解析器恢复验证）

| ID | Check（检查） | Expected（期望） | Status（状态） |
| --- | --- | --- | --- |
| M6.2-R9-01 | VM resolver parser | usable non-loopback nameserver is accepted | PASS |
| M6.2-R9-02 | Loopback-only resolver | `::1` / `127.x` is rejected as recovery-ready DNS | PASS |
| M6.2-R9-03 | Missing resolver | empty/unreadable resolver state requires recovery | PASS |
| M6.2-R9-04 | Recovery rendering | validated resolvers are deduplicated and loopback values excluded | PASS |
| M6.2-R9-05 | Managed-provider boundary | resolver materialization occurs only in SiftAlpha-managed Colima | PASS |
| M6.2-R9-06 | Existing Compose workflow/isolation | Prepare/Run/Stop/Restart probes unchanged | PASS — macOS #102 |
| M6.2-R9-07 | Android regression | W0 remains PASS | PASS — #811 |
| M6.2-R9-08 | Real installed Mac pull | Docker Hub pull no longer resolves through `[::1]:53` | PENDING |
| M6.2-R9-09 | Full real Compose A/B acceptance | A/B Run, Stop A leaves B RUNNING, Restart A | PENDING |

Current R9 result:
**7 / 9 PASS — CLOUD PASS / REAL INSTALLED-MAC RETEST PENDING**.


### R9 final package authority

Final packaging/documentation HEAD:
`5c57293adf13feac7b5cc289179fe71d720ff3b6`

Verification:
- macOS Run #103 PASS;
- Android W0 #812 PASS;
- artifact `siftalpha-macos-m6.2-installed-r9-vm-dns-ventura-x64-103`;
- artifact ID `10844679726`;
- artifact digest `sha256:a28424e98d183b539cc186cb22bdf85cd5ff40ae3f30350990ac7c08628f5c7f`;
- DMG SHA-256 `ce9044d318be6b28ef9fbc2acd341739106aeeffe2c0bacd2ded887a080d2795`.

State remains:
**M6.2 R9 CLOUD PASS / REAL INSTALLED-MAC RETEST PENDING**.


### Cross-platform Reference Gate（跨平台参考门禁）— 2026-09-25

macOS 后续每个实现/修复在进入代码前必须增加以下证据：

| 检查项 | 要求 |
|---|---|
| Android reference audit（安卓成熟实现审查） | 已检索同类成熟实现 / 开发日志；若不存在，明确记录 NONE |
| Shared behavior ownership（共享行为归属） | Core / Optional Capability / Platform Adapter 三选一明确 |
| Duplicate policy guard（重复策略防护） | 不得在 Mac* 代码中复制 Core 已有行为规则 |
| Closed-stage protection（已关闭阶段保护） | M2～M5 无真实回归不得重开 |
| Finite repair（有限修复） | 一个阻塞问题最多一个自动恢复链；失败后返回证据，不无限重试 |

### M6.2 Host Network Inheritance Correction（宿主网络继承纠偏）

必须验证：

- managed Colima profile 的 `network.dns` 为空；
- Colima start 不传显式 `--dns`；
- R8/R9 legacy resolver（旧解析器配置）能够在托管 profile 内迁移；
- VM 不再使用 `::1 / 127.x` 失败 resolver；
- 不写入 1.1.1.1 / 8.8.8.8 作为产品级 fallback；
- VPN 开启时 A Compose Prepare 实机验证；
- A/B Compose 原冻结验收链保持不变；
- 外部 Docker / Podman 未被修改；
- Android W0 回归 PASS。


### M6.2 Host Egress Provisioning Verification（宿主出口补齐验证）

| Check（检查） | Expected（期望） |
|---|---|
| macOS `scutil --proxy` parser | HTTP / HTTPS / SOCKS / exceptions 正确解析 |
| Finder launch independence | 不依赖 shell 的 HTTP_PROXY / HTTPS_PROXY |
| Colima env bridge | 当前 HTTP_PROXY / HTTPS_PROXY / NO_PROXY 通过官方 `--env` 入口进入 managed profile |
| Loopback proxy | `127.0.0.1:<dynamic-port>` 不硬编码；由 Colima 转换到 VM host gateway |
| Proxy disable migration | 系统代理关闭时旧 HTTP/HTTPS/NO_PROXY 被显式清空 |
| Docker daemon proof | `docker info` 显示 daemon HTTP/HTTPS proxy，且端口与当前 macOS 系统代理一致 |
| DNS inheritance | 继续保留 Lima Host Resolver；不重新引入公共 DNS fallback |
| Repair bound | 网络失败最多自动 repair + retry 一次 |
| External providers | Docker / Podman 外部安装不修改 |
| Android regression | W0 PASS |
| Real Ventura + VPN | Compose A Prepare PASS |


### M6.2 EOF / SOCKS5h Closure Verification

| Check | Expected |
|---|---|
| SOCKS preference | 系统 SOCKS 存在时 daemon HTTP/HTTPS proxy 均使用 `socks5h` |
| Remote DNS | `socks5h` 保留 hostname 到宿主代理链，不依赖 guest registry Fake-IP |
| HTTP fallback | 无 SOCKS 时保持当前 HTTP/HTTPS system proxy |
| Docker proof | `docker info` 的 proxy scheme + port 与选定策略一致 |
| EOF classifier | registry `EOF` / `proxyconnect tcp: EOF` 可进入唯一一次 repair |
| Retry bound | 仍只有一次 repair + retry |
| No hard-code | 无固定 7897 / VPN 产品 / 用户路径 |
| Android regression | W0 PASS |
| Real Mac | VPN 开启下 Compose A Prepare PASS |


## Pre-M7 Functional Parity Verification（M7 前功能对齐验证）

| Check | Expected |
|---|---|
| Project catalog restart | 导入 A/B → 退出 App → 重开 → A/B 自动恢复 |
| Source safety | restore / remove / clean 均不删除项目源代码 |
| Compose readiness restart | 已准备 Compose 项目重启 App 后仍识别为 prepared |
| Remove project | 仅移除 catalog；重启后不再出现；其他项目不受影响 |
| Clean running guard | RUNNING / busy 项目拒绝清理 |
| Python/Node clean scope | 只删除当前 projectId 的 SiftAlpha managed data |
| Compose clean scope | 只执行当前 Compose project 的 down + volumes + orphans |
| Compose image-only | pull --ignore-buildable |
| Compose image+build | 不 pull 同名 buildable image，直接进入 build |
| Mixed Compose | pull --ignore-buildable → build |
| Android regression | W0 PASS |

## Pre-M7 Shared Core / Platform Parity Closure — 2026-09-25

| Check | Expected | Status |
|---|---|---|
| Durable operation state | operation + generation survive process recreation through Core store | PASS — macOS #116 |
| Generation fence | clearing current operation does not reset last generation | PASS |
| Recovery convergence | stale unfinished operation enters bounded RECOVERING and returns to live facts | PASS |
| Host process ownership | projectId + generation + PID + start time persisted | PASS |
| PID reuse guard | recovered PID must match original process start time | PASS |
| Project-scoped STOP | stopping A does not target unrelated project B | PASS |
| Shared cleanup policy | running/busy project cleanup blocked | PASS |
| macOS Keychain capability | secure-secret storage AVAILABLE when system security tool exists | PASS |
| Secret file safety | secret values not written into SiftAlpha state files | PASS |
| Launch configuration | Python / Node / Compose receive project-scoped secure environment | PASS |
| Diagnostic redaction | configured secret values redacted before UI/log presentation | PASS |
| Runtime storage inventory | managed project/toolchain/cache/state categories reported | PASS |
| Safe orphan cleanup | only proven inactive + reproducible orphan data can auto-clean | PASS |
| Ambiguous data guard | unowned/Compose-ambiguous runtime data conservatively retained | PASS |
| Current toolchain guard | current managed toolchain never auto-cleaned | PASS |
| Normal/Developer parity | both modes use same controller and expose config/storage entry points | PASS |
| Closed-stage regression | M4.1/M4.2/M5.1/M5.2/M6.1/M6.2 probes remain PASS | PASS — macOS #116 |
| Android regression | existing Android app remains green after Core changes | PASS — W0 #828 |

Closure:
**PRE-M7 SHARED CORE REALIGNMENT = PASS / CLOSED.**
