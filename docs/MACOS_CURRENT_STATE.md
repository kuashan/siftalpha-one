# SiftAlpha X macOS Current State（macOS 当前状态）

> 本文件只记录“现在”的权威事实。历史过程、旧状态和已结束切片统一保留在 `MACOS_DEV_LOG.md`。
> 每轮 macOS（苹果桌面系统）工作开始前必须重新读取真实远端和本文件。

## 1. 当前阶段

- Product Display Name（产品显示名称）：**SiftAlpha X**
- Repository（仓库）：`kuashan/siftalpha-one`
- Branch（分支）：`feature/cross-platform-core`
- M0 Governance（开发治理）：PASS / CLOSED（通过 / 关闭）
- M1 Core Boundary（核心边界）：PASS / CLOSED（通过 / 关闭）
- M2 macOS Host Skeleton（macOS 主机骨架）：PASS / CLOSED（通过 / 关闭）
- M3 Host Runtime Provider（主机运行提供者）：**PASS / CLOSED（通过 / 关闭）**
- 当前阶段：**M6 — Container / Multi-service（容器 / 多服务）— M6.1 COMPLETE；M6.2 CLOUD PASS / REAL VENTURA PENDING**
- M4 implementation（实现）：**M4.1 PASS / COMPLETE；M4.2 PASS / COMPLETE；M4 PASS / CLOSED（M4 已通过并关闭）**
- M5 implementation（实现）：**M5.1 PASS / COMPLETE；M5.2 PASS / COMPLETE；M5 PASS / CLOSED（M5 已通过并关闭）**

M3 最后一个已验收功能 HEAD：
`259d94fe7d7501a1232f1d7114445f4673cfc0a1`

Android（安卓）最新已验收回归基线仍为：
`0.8.0-alpha43-r48d11-m1.6-r1` / versionCode `232`.

## 2. 品牌与技术身份

用户可见品牌统一为：

**SiftAlpha X**

macOS 窗口标题、应用标题与测试包显示名称使用 `SiftAlpha X`。

技术身份不因品牌显示名改变而自动迁移：

- Android applicationId（安卓应用 ID）保持 `com.siftalpha.studio`
- macOS package identifier（苹果包标识）保持 `com.siftalpha.macos`
- repository / module / internal protocol names（仓库 / 模块 / 内部协议名）按技术需要保持稳定

## 3. macOS 最低系统版本

当前完整支持最低版本冻结为：

**macOS 13 Ventura**

当前真实验收机：

- macOS **13.7.8 Ventura**
- Intel x86_64
- MacBook Pro 13-inch, 2017

规则：

- 从 M6 开始，不再把 macOS 10.15.7 Catalina 兼容性作为必须维持的 Blocking Requirement（阻塞要求）。
- 可以使用需要 macOS 13+ 的现代 Container / Runtime / Virtualization（容器 / 运行时 / 虚拟化）能力，只要仍满足 M6/M7/M8 的其他冻结边界。
- 不需要为了保留 Catalina 兼容而固定旧版 Docker / Podman / Colima / Compose。
- 历史 M2～M5 在 Catalina 10.15.7 / Intel x86_64 上的 PASS 记录继续保留，作为历史兼容证据；它不再定义当前最低支持版本。
- 现有构建产物如果仍具有低于 macOS 13 的 Mach-O deployment target（部署目标），可以继续保留，直到后续实现确实需要提高；但后续验收不再要求 10.15.7 可运行。

## 4. M3 最终能力事实

M3 已建立 macOS Platform Adapter（苹果平台适配层）的 Host Runtime Provider（主机运行提供者）基础闭环：

- Python 3 discovery（Python 3 发现）
- Node.js discovery（Node.js 发现）
- Bun discovery（Bun 发现）
- Git discovery（Git 发现）
- Shell discovery（Shell 命令环境发现）
- Host process start（主机进程启动）
- stdout / stderr capture（标准输出 / 标准错误采集）
- STATUS（状态）
- project-scoped STOP（项目级停止）
- Project A / B concurrent isolation（并行项目隔离）

Runtime Discovery（运行时发现）规则：

- AVAILABLE / UNAVAILABLE（可用 / 不可用）都是合法事实；
- Python Host Runtime（主机 Python）只接受 Python 3，不接受 Python 2；
- Discovery（发现）必须是 passive（被动的），不得仅因探测而弹出 Apple Command Line Tools（命令行开发者工具）安装窗口；
- Catalina 未安装 Command Line Tools 时，已知 Apple developer-tool shim（开发者工具占位命令）必须跳过；
- 用户自己安装的 Homebrew / MacPorts / asdf / Volta / Bun / PATH 工具仍可被发现。

## 5. M3 Closure Audit（关闭审计）

M3 Frozen Exit Criteria（冻结退出条件）最终结果：

1. Python discovery fact：PASS
2. Node.js discovery fact：PASS
3. Bun discovery fact：PASS
4. Git discovery fact：PASS
5. Shell discovery fact：PASS
6. Process start：PASS
7. stdout / stderr capture：PASS
8. STATUS：PASS
9. selected-project STOP：PASS
10. Project A/B concurrent isolation：PASS
11. Android regression：PASS — W0 #719
12. Catalina compatibility：PASS — macOS Run #10
13. Real Mac M3 Self-Test：PASS — macOS 10.15.7 Catalina / Intel x86_64

Real Mac（真实 Mac）还确认：

- 打开修复包不再触发 Apple Command Line Tools 安装提示；
- legacy Python 2.7.16 不再被判为可用 Host Python；
- `Process self-test: PASS`。

最终结论：

**M3 = PASS / CLOSED（通过 / 关闭）。**

不得再新增新的必做 M3.x 切片；非阻塞优化进入 Backlog（待办）。

## 6. 当前尚未实现

以下仍属于后续阶段，不得误报为已经完成：

- 完整 Import → Detect → Plan → Prepare → Run → Logs → Stop / Restart（导入 → 检测 → 计划 → 准备 → 运行 → 日志 → 停止 / 重启）工作流
- Web Discovery / Endpoint Probe（网页发现 / 端点探测）的 macOS 项目工作流接线
- macOS secure storage（苹果安全存储）
- Normal Mode / Developer Mode（普通模式 / 开发者模式）正式产品 UI
- Container execution / Compose runtime lifecycle / Multi-service orchestration（容器执行 / Compose 运行生命周期 / 多服务编排；M6.2）
- OpenBot（开放机器人）真实验收
- Developer ID / Hardened Runtime / Notarization / DMG-PKG（签名 / 强化运行时 / 公证 / 正式分发）

## 7. 跨平台硬边界

1. Core（核心）只承载真正平台无关的模型、策略与接口。
2. macOS 平台实现留在 macOS Platform Adapter（苹果平台适配层）。
3. 不为了 macOS 修改 Android（安卓）特有实现。
4. 不要求 Android / Windows（安卓 / 微软）实现 macOS 专属能力。
5. Normal Mode / Developer Mode（普通 / 开发者模式）必须共享同一 Core（核心）和 Runtime State（运行状态）。
6. STOP（停止）永远是 project-scoped（项目级）：只停止当前项目及其归属进程，不影响其他项目。
7. OpenBot（开放机器人）不得使用项目专属补丁作为验收手段。
8. macOS 不建立独立 Core（核心）副本。

## 8. Change Discussion Gate（修改前讨论门禁）

确认边界以“阶段 / 功能方案 / 范围变化”为单位，而不是以每一行代码为单位。

### 需要用户确认的情况

在以下情况开始前，必须先完成 Read-only Audit（只读审查），并说明 Problem / Root Cause / Plan / Scope / Boundary / Risk / Acceptance，再取得用户明确确认：

- 开始新的一级 Stage（阶段）或新的 Implementation Slice（实现切片）；
- 改变已批准的功能方案或用户可见行为；
- 扩大修改范围；
- 修改 Core（核心）架构或跨平台边界；
- 触碰 Frozen Boundary（冻结边界）；
- 提高 macOS 最低版本；
- 引入新的运行提供者、依赖体系或重大技术路线；
- 发现问题后，修复方案需要改变原批准设计。

### 已批准范围内可直接修复的情况

一旦某个 Implementation Slice（实现切片）已经获得用户批准，以下问题由开发执行方直接修复、继续验证，不需要逐项再次请求确认：

- 编译错误；
- Gradle / YAML / CI（构建 / 工作流）语法或接线错误；
- 单元测试 / 探针 / 测试接线错误；
- 明确的拼写、路径、转义、配置错误；
- 已批准方案内部的实现 bug；
- 不改变功能目标、架构、冻结边界和用户行为的最小修复。

这些修复必须记录根因和证据，并继续运行原定 Cloud Verification / Real Device / Real Mac Acceptance（云端 / 真机验收）。

如果修复过程中发现需要改变原方案、扩大范围或突破冻结边界，则立即停止自动修复，重新进入用户确认。

## 9. Remote HEAD Drift Gate（远端漂移门禁）

任何写操作前必须再次读取真实远端 HEAD。

如果与本轮已确认起点不同：

- 立即停止写操作；
- 报告 `REMOTE_HEAD_CHANGED`；
- 不自行 rebase（变基）；
- 不自行 merge（合并）；
- 不 force push（强制推送）；
- 重新审查漂移后，再按 Change Discussion Gate（修改前讨论门禁）讨论。

## 10. Stage Closure Rule（阶段关闭规则）

- M0～M8 是固定一级阶段。
- Mx.y 只允许作为有限 Implementation Slice（实现切片）。
- 一级阶段 Exit Criteria（退出条件）全部有证据后，只执行一次 Closure Audit（关闭审计）。
- Closure Audit PASS 后必须立即 PASS / CLOSED 并进入下一阶段。
- “还能继续优化”不能阻止关闭。
- 非阻塞优化进入 Backlog（待办）。
- 不允许通过新增无限 Mx.y 延迟收尾。

## 11. M4 当前唯一方向

当前正式进入：

**M4 — Project Workflow（项目工作流）**

M4 只负责把现有 Core（核心）与 macOS Host Runtime Provider（主机运行提供者）接成真实项目工作流。

冻结 Exit Criteria（退出条件）继续以 `MACOS_DEV_LOG.md` / `MACOS_TEST_MATRIX.md` 已定义内容为准，包括至少：

- 普通 Python 项目完整 Import → Detect → Plan → Prepare → Run → Logs → Stop / Restart 闭环；
- Node.js 项目同样闭环，或明确记录本阶段范围；
- Environment Plan（环境计划）继续只表达需求，Provider（提供者）负责满足需求；
- Web Discovery + Endpoint Probe（网页发现 + 端点探测）可得到可用结果入口；
- 失败状态与日志可诊断；
- 真实 Mac 验收通过。

已批准有限 Implementation Slice（实现切片）：

- **M4.1 — Import + Detect + Plan（导入 + 检测 + 环境计划）**
- M4.2 — Prepare + Run + Observe + Stop/Restart（准备 + 运行 + 观察 + 停止/重启）

本轮只执行 M4.1；M4.2 必须在 M4.1 收尾并再次按 Change Discussion Gate（修改前讨论门禁）讨论后开始。


## 12. M3 Post-Closure Brand / Governance Verification（M3 关闭后品牌 / 治理复验）

收口提交：
`a0b4bb739840d05fc0ab2cb59d4fd08ade42503e`

验证结果：

- SiftAlpha X macOS Host Runtime Run #11: PASS（通过）
- Android W0 Cloud Build #720: PASS（通过）
- macOS artifact（苹果制品）: `siftalpha-macos-m3.1-catalina-x64-11`
- artifact ID: `10788263661`
- artifact digest: `sha256:9c2febc8da42b1dfd39b9bfce75138c28eaa8b6432c18ffd4862b4c3c0db867e`
- inner SiftAlpha X test ZIP SHA-256:
  `af39515bd544978f1ee0056b5839ab20e1d84f5aa0fa1dec0ee24caf45f34624`
- Android W0 artifact: `siftalpha-w0-720`
- Android W0 artifact digest:
  `sha256:d72f875cbf18ea96ef5ae4790be826d62cb4af4313c77c9d8f8fc1e080666746`
- packaged app（打包应用）: `SiftAlpha X.app`
- launcher architecture（启动器架构）: Intel x86_64
- launcher / libjli / libjvm MIN_OS: 10.12 / 10.12 / 10.12
- M3 process probe（M3 进程探针）: PASS
- STOP A → B remains RUNNING（停止 A、B 继续运行）: PASS

结论：

品牌显示与治理清理没有重新打开 M3，也没有引入 Android（安卓）或 Catalina（苹果 10.15）回归。

当前阶段保持：

**M4 — Project Workflow（项目工作流） / M4.1 IN PROGRESS（M4.1 进行中）**。


## 13. M4.1 Implementation（实现）

Approved scope（已批准范围）：

- pure Runtime Detect / Select rules（纯运行时检测 / 选择规则）迁入 Core（核心），Android 与 macOS 共用；
- `MacProjectFilesystem`：真实 macOS 目录导入与安全文件系统适配；
- `MacProjectSnapshotBuilder`：有界项目快照，忽略依赖/构建目录并阻止 symlink（符号链接）逃逸；
- `MacProjectWorkflowPlanner`：从项目事实生成 provider-neutral Environment Needs（提供者中立环境需求），再结合 M3 Host Runtime facts（主机运行时事实）得到 READY_TO_PREPARE / RUNTIME_MISSING / BLOCKED；
- temporary M4.1 diagnostic UI（临时诊断界面）：选择真实项目目录并显示 Import / Detect / Plan 事实；
- cloud M4.1 probe（云端探针）：Python + Node 项目 Import → Detect → Plan。

本轮不安装依赖、不执行第三方项目代码、不进入 M4.2。


## 14. M4.1 Cloud Verification（云端验证）— PASS

Accepted functional HEAD（已验证功能提交）:
`4e57066c4aaf4110c66b07448afedd6b680734c1`

Evidence（证据）:

- SiftAlpha X macOS Host Runtime Run #15: PASS
- Android W0 Cloud Build #724: PASS
- M4.1 plan probe: `SIFTALPHA_M4_PLAN_PROBE=PASS`
- Python sample: Import → Detect → Plan = `READY_TO_PREPARE`, primary = `python`
- Node.js sample: Import → Detect → Plan = `READY_TO_PREPARE`, primary = `nodejs`
- M3 process regression probe: PASS
- STOP A → B remains RUNNING: PASS
- Android dependency isolation: PASS
- Catalina packaging: PASS
- launcher / libjli / libjvm MIN_OS: 10.12 / 10.12 / 10.12
- macOS artifact ID: `10789876177`
- macOS artifact digest:
  `sha256:ed0eb285ebb7781f40aec12099eb57e68ea3662a3e28b412f7b90b79ac9d7dae`
- user-facing M4.1 test ZIP SHA-256:
  `059ea96ca0cd6300fd991e86b1039d19f374d8a441c6db5d7568abb272d6e5fe`
- Android W0 artifact ID: `10789427452`
- Android W0 artifact digest:
  `sha256:5752990025ac839c9b51e586a4d081039c9cb9d1ea5a02a833445337af39ba1a`

M4.1 is not closed yet. Remaining acceptance item:

**Real Mac Import → Detect → Plan（真实 Mac 导入 → 检测 → 计划）**.


## 15. M4.1 real-Mac launch blocker repair — CLOUD RE-VERIFIED

Real macOS 10.15.7 testing of the first M4.1 package exposed a packaging-only startup blocker before Import（导入） could be tested.

Root cause:
- jpackage received `--java-options "-Dapple.awt.application.name=SiftAlpha X"`;
- the space split the generated launcher configuration into:
  - `java-options=-Dapple.awt.application.name=SiftAlpha`
  - `java-options=X`;
- the standalone `X` caused the packaged JVM launcher to exit before Swing UI startup.

Repair HEAD:
`53e7cf637239ae6d92ca11946aae7d8a4d43cf8c`

Repair:
- remove the packaging-time display-name Java option;
- keep the runtime `System.setProperty("apple.awt.application.name", "SiftAlpha X")`;
- add a CI guard that rejects packaged config containing standalone `java-options=X`;
- rename the macOS artifact from stale M3.1 naming to M4.1 naming.

Cloud re-verification:
- SiftAlpha X macOS Host Runtime Run #17: PASS;
- Android W0 #726: PASS;
- packaged launcher config contains only `java-options=-Djpackage.app-version=1.0`;
- M4.1 plan probe: PASS;
- M3 process regression probe: PASS;
- Catalina packaging: PASS;
- launcher / libjli / libjvm MIN_OS: 10.12 / 10.12 / 10.12;
- macOS artifact ID: `10790410614`;
- macOS artifact digest: `sha256:e28186c3c29688d0cd2ed394aabf7abf01b10687e8c517170346d405875f0b50`;
- user-facing repaired ZIP SHA-256:
  `edad50c534e4579ab0f559cd8e72c0ebfa1b886f44c73c756018eccebb591357`;
- Android W0 artifact ID: `10790305510`;
- Android W0 artifact digest:
  `sha256:b739ee110bdf72854f6d5da312373ac8181f6037a8b502cf59fb5d1dba11fe59`.

M4.1 remains:

**CLOUD PASS / REAL MAC PENDING（云端通过 / 真实 Mac 待验收）**.

Real Mac still needs to confirm:
1. double-click launch succeeds;
2. Import Project Folder works;
3. Import → Detect → Plan returns the expected Python result on the Catalina host.


## 16. M4.1 Real Mac Acceptance（真实 Mac 验收）— PASS

Real acceptance machine:
- macOS 10.15.7 Catalina
- Intel x86_64

Observed on the repaired M4.1 package:

- `SiftAlpha X.app` double-click launch: PASS
- `Import Project Folder`（导入项目目录）: PASS
- project identity generated: PASS
- `SIFTALPHA_M4_PLAN_STATUS=RUNTIME_MISSING`
- `SIFTALPHA_M4_PRIMARY_RUNTIME=python`
- `SIFTALPHA_M4_DIRECT_DEPENDENCIES=1`
- preparation plan generated:
  `VALIDATE_PLAN, ACQUIRE_RUNTIME, CREATE_ENVIRONMENT, PYTHON_INSTALL, VERIFY_ENVIRONMENT`

Interpretation:
- Import（导入）succeeded;
- Detect（检测）correctly identified the test project as Python;
- Plan（计划）correctly reported that this Catalina host is missing a usable Python 3 Runtime;
- the result is expected and proves provider-neutral planning rather than a failure.

M4.1 final result:

**PASS / COMPLETE（通过 / 完成）**.

The next approved development decision boundary is **M4.2 — Prepare + Run + Observe + Stop/Restart（准备 + 运行 + 观察 + 停止/重启）**. M4.2 code must not begin until its implementation plan is discussed and approved.


## 17. M4.2 Implementation（实现）— STARTED

User-approved scope:

- bundle a fixed SiftAlpha-managed CPython 3.14.7 x86_64 macOS Runtime into the Catalina app;
- do not require Homebrew, Xcode, Command Line Tools, or a preinstalled Python;
- create project-owned generation environments under SiftAlpha X application data;
- Python Prepare: validate → acquire managed runtime → create venv → install wheel-only dependencies → verify → atomic commit;
- reject requirements options, editable/VCS/direct-URL/local-path installs in this slice;
- execute Python through the committed project environment;
- use Core project-operation and lifecycle policies rather than a second macOS state machine;
- STOP must cancel current-project Prepare or Run without touching other projects;
- share Python entrypoint, local Web URL, and endpoint-probe rules through Core;
- macOS Web Discovery uses project logs first and project-process-scoped lsof fallback, never a global 1..65535 scan;
- preserve M3 process isolation and Android behavior;
- M4.2 temporary acceptance UI may expose Prepare / Run / Logs / Web / Stop / Restart; M5 owns final UI.

Managed Python build input is pinned to CPython 3.14.7 / python-build-standalone release 20260901, x86_64-apple-darwin install_only_stripped, with SHA-256 verification in CI.

M4.2 remains IN PROGRESS until cloud and real Catalina full-workflow acceptance pass.


## 18. M4.2 Cloud Verification（云端验证）— PASS

Accepted functional HEAD:
`50a87264e3b5f8d5c352b345a1bc61d7d499c6d1`

Cloud evidence:

- SiftAlpha X macOS Host Runtime Run #21: PASS
- Android W0 Cloud Build #730: PASS
- Managed Python artifact checksum verification: PASS
- Managed Python: CPython 3.14.7 / Intel x86_64
- bundled pip: 26.2.1
- M4.2 full workflow probe:
  - managed_python=Python 3.14.7
  - prepare=PASS
  - run=PASS
  - web discovery/probe=PASS
  - stop=true
  - stopped_state=STOPPED
  - restart=true
  - web_after_restart=PASS
  - final_stop=true
- M3 project-scoped process regression: PASS
- Catalina package: PASS
- launcher MIN_OS: 10.12
- managed Python executable MIN_OS: 10.15
- managed libpython3.14 MIN_OS: 10.15
- bundled libjli MIN_OS: 10.12
- bundled libjvm MIN_OS: 10.12
- macOS artifact: `siftalpha-macos-m4.2-catalina-x64-21`
- macOS artifact ID: `10791113378`
- macOS artifact digest:
  `sha256:7a36e17e87a59ba55007a22cd57170d3b01b7969b71b8ffe92a50e39bb36ea9f`
- extracted user-facing app ZIP SHA-256:
  `3797ec78d2d2422c46932a7f4b887d4818aee6b2d14d5de9dbbe8e16b460b172`
- Android W0 artifact ID: `10791815841`
- Android W0 artifact digest:
  `sha256:371e84271918d0b7eedcabd3470c80d5c2f5a420d5b84c07369f035cceeb88ef`

M4.2 remains OPEN until real macOS 10.15.7 acceptance proves:

Import → Prepare → Run → Logs/Web → Stop → Restart.

No Homebrew, Xcode Command Line Tools, system Python 3, or manual runtime installation may be required.


## 19. M4.2 Real Mac Acceptance（真实 Mac 验收）— PASS

Real acceptance machine:
- macOS 10.15.7 Catalina
- Intel x86_64
- no preinstalled accepted Python 3 was required

Observed real-device evidence:

- managed Runtime acquisition: PASS
  - bundled Python path used from SiftAlpha X app bundle
  - Python 3.14.7
- project environment creation: PASS
- dependency installation: PASS
  - `idna==3.10`
- environment verification: PASS
  - `Python 3.14.7`
  - `No broken requirements found.`
- environment commit: PASS
- project Run（运行）: PASS
- lifecycle: `RUNNING`
- environmentReady: `true`
- processState: `RUNNING`
- Web endpoint discovery/probe: PASS
  - real loopback URL observed
- STOP（停止）: PASS
  - `STOP=STOPPED`
- safe repeated STOP during Restart: PASS
  - `STOP=ALREADY_STOPPED`
- Restart（重新运行）: PASS
  - second `START:PASS`
  - second `START:SUCCESS`
  - project returned to RUNNING
- project output after restart: PASS
  - Web URL emitted
  - `SIFTALPHA_TEST_DEPENDENCY=idna:3.10`

Interpretation:

The real Catalina machine completed the frozen M4.2 workflow without Homebrew, Xcode Command Line Tools, manual Python installation, or Termux:

Import → Detect → Plan → Prepare → dependency install → verify → Run → Logs/Web → STOP → Restart.

M4.2 final result:

**PASS / COMPLETE（通过 / 完成）**.


## 20. M4 Final Closure Audit（M4 最终关闭审计）— PASS / CLOSED

Frozen M4 Exit Criteria（退出条件）:

| Exit Criterion | Evidence | Result |
| --- | --- | --- |
| ordinary Python project Import（导入） | M4.1 real Catalina | PASS |
| Detect + Plan（检测 + 计划） | M4.1 real Catalina | PASS |
| Prepare without user-installed Python | M4.2 bundled CPython 3.14.7 real Catalina | PASS |
| Project environment + dependency install | real venv + idna 3.10 | PASS |
| Run（运行） | real `START:SUCCESS` | PASS |
| Logs（日志） | real project stdout | PASS |
| Web entry verifiable（Web 入口可验证） | real loopback endpoint + endpoint probe | PASS |
| STOP project-scoped（项目级停止） | real STOPPED + M3 isolation regression | PASS |
| Restart（重新运行） | real second START success | PASS |
| Errors remain diagnosable（错误可诊断） | structured lifecycle/prepare/process evidence | PASS |
| Catalina compatibility | managed Python/libpython MIN_OS=10.15 + real 10.15.7 | PASS |
| Android regression | W0 #730 | PASS |

Final result:

**M4 — Project Workflow（项目工作流） = PASS / CLOSED（通过 / 关闭）**

Per Stage Closure Rule（阶段关闭规则）:
- no new mandatory M4.x slices may be created;
- non-blocking refinements move to Backlog（待办）;
- current stage advances to:
  **M5 — Product UI Parity（产品界面对齐）**.


## 21. M5.1 Normal Mode Product UI（普通模式产品界面）— IMPLEMENTATION STARTED

User-approved M5 finite plan:
- M5.1 Normal Mode Product UI（普通模式产品界面）
- M5.2 Developer Mode + Shared State Parity（开发者模式 + 共享状态对齐）
- then one M5 Closure Audit（关闭审计）

M5.1 scope:

- replace the temporary M4 engineering window with a real Normal Mode desktop product shell;
- preserve SiftAlpha X brand hierarchy and reuse the repository's original logo artwork;
- use a desktop two-pane layout: Projects（项目列表） + Project Workspace（项目工作区）;
- support project search and All / Python / Node filters;
- Import Project（导入项目） remains the current real folder-import capability; no fake GitHub/ZIP feature is added;
- state card and One Primary Action（单一主操作） are derived from the existing shared `ProjectLifecycleState`, not from a new UI state machine;
- ordinary users see Prepare / Run / Stop / Open Result actions without raw PID/runtime diagnostics;
- Open Result（打开结果） and Refresh（刷新） remain discoverable secondary actions;
- when a result is available while the process is still running, Open Result becomes primary and Stop remains available as a secondary action;
- Result uses the already-verified Web Endpoint and opens through the system browser; M5.1 does not add Chromium/JCEF;
- all actions are executed through one `MacProductController` backed by the existing single `MacProjectWorkflowCoordinator`;
- M5.2 will attach Developer Mode to this same controller rather than creating another coordinator;
- no Runtime / Process Control / Environment / STOP semantics change.

M5.1 remains IN PROGRESS until cloud product-state verification and real Catalina UI acceptance pass.


## 22. M5.1 Cloud Verification（云端验证）— PASS

Accepted M5.1 functional HEAD:
`71e9dba0323aa35a531a3a1b26b55907ac8b907b`

Cloud evidence:

- SiftAlpha X macOS Host Runtime Run #23: PASS
- Android W0 Cloud Build #732: PASS
- M4.2 managed-Python full workflow regression: PASS
- M3 project-scoped process isolation regression: PASS
- M5.1 Normal Mode product-state probe: PASS
  - Import = PASS
  - initial primary action = PREPARE
  - Prepare = true
  - after Prepare primary action = RUN
  - Run = true
  - verified loopback result URL discovered
  - while result is available, primary action = OPEN_RESULT
  - secondary Stop remains available = true
  - Stop = true
  - after Stop primary action returns to RUN
- original SiftAlpha X logo artwork is packaged as `siftalpha_logo.png` inside `macosApp.jar`
- Catalina package remains `LSMinimumSystemVersion=10.15`
- launcher / managed Python / managed libpython / libjli / libjvm MIN_OS:
  `10.12 / 10.15 / 10.15 / 10.12 / 10.12`
- macOS artifact:
  `siftalpha-macos-m5.1-catalina-x64-23`
- artifact ID: `10792990618`
- artifact digest:
  `sha256:449915d6af966ea998e67338915e235b1410ccacb742fa1ef5baa00f6594a6ee`
- user-facing app ZIP SHA-256:
  `33bd2725e63b89fe80884449d2a017845ce6518a5398c48555cdfeb1c7307fc3`
- Android W0 artifact:
  `siftalpha-w0-732`
- W0 artifact ID: `10791464850`
- W0 artifact digest:
  `sha256:fe994b167c52ab67eb81db17bbb802f5c4710fc240d8e9ca779c388784535cae`

Current M5.1 state:

**PASS / COMPLETE（通过 / 完成）**.

Real macOS 10.15.7 Catalina / Intel x86_64 acceptance: **PASS**.

M5.1 final result: **PASS / COMPLETE（通过 / 完成）**.

M5.2 has not started and must not begin before its Change Discussion Gate（修改前讨论门禁） discussion/approval.


## 23. M5.1 Real Mac Acceptance（真实 Mac 验收）— PASS / COMPLETE

Acceptance machine:
- macOS 10.15.7 Catalina
- Intel x86_64

User-confirmed result:
- M5.1 Normal Mode Product UI（普通模式产品界面）真实 Mac 验收：PASS
- frozen M5.1 visual / interaction acceptance boundary：PASS
- no new blocker was reported against the approved M5.1 scope

Combined authority:
- functional HEAD: `71e9dba0323aa35a531a3a1b26b55907ac8b907b`
- macOS Run #23: PASS
- Android W0 #732: PASS
- real Catalina acceptance: PASS

Final M5.1 result:

**M5.1 = PASS / COMPLETE（通过 / 完成）**.

Per Stage Closure Rule（阶段收尾规则）, M5.1 is closed. Do not reopen it for non-blocking polish; such items belong in Backlog（待办）.

Next decision boundary:
**M5.2 — Developer Mode + Shared State Parity（开发者模式 + 共享状态对齐） — PASS / COMPLETE**.


## 24. M5.2 Developer Mode + Shared State Parity（开发者模式 + 共享状态对齐）— CLOUD PASS

Accepted cloud HEAD:
`9e6f3ccb9259704f3efe4f898a97ae3f054f1b33`

Implementation facts:
- Developer Mode（开发者模式）is a second Presentation（表现层） over the existing single `MacProductController`;
- `MacProductController` still delegates mutations to the same single `MacProjectWorkflowCoordinator`;
- no second Runtime State（运行状态）, Lifecycle（生命周期） or Coordinator（协调器） was introduced;
- Developer Mode exposes Project ID / Runtime / Runtime Version / Environment generation / Entrypoint / Lifecycle / Process State / Operation / owned PID(s) / Web URL / discovery source / endpoint state / stdout / stderr / combined logs;
- Developer Prepare / Run / Stop / Restart / Refresh all call the same controller used by Normal Mode;
- switching Normal Mode ↔ Developer Mode only changes the visible window and does not start, stop or restart the project;
- Core（核心）, Android production behavior, Runtime provider rules and project-scoped STOP semantics are unchanged.

Cloud evidence:
- SiftAlpha X macOS Host Runtime Run #34: PASS;
- Android W0 Cloud Build #743: PASS;
- M5.2 shared-state parity probe: PASS;
- Normal Mode running state = RUNNING;
- Developer Mode running state = RUNNING;
- Managed Python = Python 3.14.7;
- same Environment generation = true;
- same Web result URL = true;
- same raw/combined logs = true;
- presentation switching keeps owned PID stable = true;
- Developer Stop → Normal primary action RUN = PASS;
- Normal Run → Developer RUNNING = PASS;
- Environment generation reused after Normal Run = true;
- Project A stop leaves Project B RUNNING = PASS;
- M5.1 Normal Mode regression: PASS;
- M4.2 full workflow regression: PASS;
- M3 project-scoped process isolation regression: PASS.

Catalina packaging remains unchanged:
- LSMinimumSystemVersion = 10.15;
- launcher MIN_OS = 10.12;
- managed Python MIN_OS = 10.15;
- managed libpython MIN_OS = 10.15;
- libjli MIN_OS = 10.12;
- libjvm MIN_OS = 10.12.

Artifact:
- `siftalpha-macos-m5.2-catalina-x64-34`;
- artifact ID: `10793889753`;
- artifact digest: `sha256:5cf4d44918280a8bd05a84cbd3f5a66d21c4a37d5c5b8aafd45227dd09a960a3`;
- user-facing app ZIP SHA-256: `f38fbc492b1dbc9958a498661412e5cf09fae9c3957acb3ab30f6c1e8d22b34e`.

Android W0 artifact:
- `siftalpha-w0-743`;
- artifact ID: `10794027577`;
- artifact digest: `sha256:f56f93c0d03ec7607c94b7699109a4a01f1bf622805be3807b7b077b265603fa`.

Current M5.2 state:

**PASS / COMPLETE（通过 / 完成）**.

The frozen real Catalina Normal ↔ Developer shared-state acceptance has passed.


### M5.2 Real-Mac Repair — Developer Import + Simplified Chinese（开发者导入 + 简体中文）

Real Catalina acceptance exposed two presentation-layer blockers before M5.2 closure:
- Developer Mode had no direct Import Project（导入项目） entry when the shared project list was empty;
- the Developer Mode user-visible surface was primarily English rather than Simplified Chinese（简体中文）.

Approved minimal repair completed:
- Developer Mode now has a direct **导入项目** action;
- that action delegates to the same `MacProductController.importProject()` used by the product layer and does not create a second import path or coordinator;
- empty-state guidance is explicit in Simplified Chinese;
- window title, toolbar actions, Projects label, shared-state heading, diagnostic labels and log tabs are localized to Simplified Chinese;
- technical values such as `RUNNING`, PID, `stdout` and `stderr` remain visible as technical facts;
- Core / Runtime / STOP semantics / Android production code remain unchanged.

Repair cloud verification:
- functional repair HEAD `9e6f3ccb9259704f3efe4f898a97ae3f054f1b33`;
- macOS Run #34 PASS;
- Android W0 #743 PASS;
- M5.2 shared-state parity probe remains PASS;
- M5.1 / M4.2 / M3 regressions remain PASS;
- Catalina MIN_OS chain unchanged.

The repaired Developer Mode UI has now been re-accepted on the real Catalina machine. **M5.2 = PASS / COMPLETE**.


## 25. M5.2 Real Mac Acceptance + M5 Final Closure Audit（真实 Mac 验收 + M5 最终关闭审计）— PASS / CLOSED

Acceptance machine:
- macOS 10.15.7 Catalina
- Intel x86_64

Final real-Mac evidence:
- Developer Mode（开发者模式）Simplified Chinese UI: PASS;
- direct **导入项目** from Developer Mode: PASS;
- imported project visible in the same shared project list: PASS;
- Developer Mode observed the running project as `RUNNING`: PASS;
- Runtime Version = Python 3.14.7: PASS;
- Environment generation / Entrypoint / PID / Web URL / Logs visible: PASS;
- Developer Stop → Normal Mode immediately becomes stopped / run-ready: PASS;
- Normal Mode Run → Developer Mode immediately returns to `RUNNING`: PASS;
- mode switching itself does not restart or stop the project: PASS.

Frozen M5 Exit Criteria audit:

| Exit Criterion | Evidence | Result |
| --- | --- | --- |
| Normal Mode Import → Prepare → Run → Result → Stop | M5.1 Run #23 + real Catalina acceptance | PASS |
| Developer Mode advanced Runtime / Process / Web / Logs facts | Run #34 + real Catalina acceptance | PASS |
| Normal + Developer read one Runtime State | M5.2 parity probe + real cross-mode stop/run acceptance | PASS |
| No second Lifecycle / Coordinator | source audit: one MacProductController + one MacProjectWorkflowCoordinator | PASS |
| Project-scoped STOP isolation | M5.2 + M3 A/B probes | PASS |
| Android regression | W0 #743 | PASS |
| Catalina 10.15.7 real-machine acceptance | real Intel Catalina machine | PASS |

Blocking Issues（阻塞项）:
- none.

Backlog Boundary（待办边界）:
- optional UI polish, extra diagnostics, additional non-required tests and future enhancements do not reopen M5;
- no M5.3 / M5.4 is permitted by the frozen finite plan.

Final result:

**M5.1 = PASS / COMPLETE**  
**M5.2 = PASS / COMPLETE**  
**M5 — Product UI Parity = PASS / CLOSED（通过 / 关闭）**

Next stage:
**M6 — Container / Multi-service（容器 / 多服务） — NOT STARTED**.

M6 implementation must begin only after its Change Discussion Gate（修改前讨论门禁） plan is discussed and approved.


## 26. macOS 13 Ventura Support Baseline（macOS 13 支持基线）— 2026-09-24

User-confirmed platform boundary change:
- the real acceptance machine has been upgraded from macOS 10.15.7 Catalina to macOS 13.7.8 Ventura;
- Intel x86_64 remains a supported architecture target;
- the current full-support minimum is now **macOS 13 Ventura**;
- future M6 / M7 / M8 work no longer needs to preserve Catalina compatibility;
- historical Catalina PASS evidence remains valid historical evidence and is not deleted or rewritten.

This change specifically removes the need to constrain Container / Compose / Bun / virtualization work to legacy Catalina-era tool versions.


## 27. M6.1 Container Capability + Compose Detection/Plan（容器能力 + Compose 检测/计划）— CLOUD PASS

Accepted cloud HEAD:
`7d3c102032b1aeaae9182b5b972ec17391e80ee7`

Functional implementation HEAD:
`8fe74e86ed90490b84d63c894b0de5d37da2d45e`

Implemented boundary:
- Core（核心）adds platform-neutral Compose manifest detection and multi-service planning;
- supported root manifest names: `compose.yaml`, `compose.yml`, `docker-compose.yaml`, `docker-compose.yml`;
- Compose plan records services, image/build facts, `depends_on`, and published/target port facts;
- container availability is consumed only through existing `CapabilityAvailability = AVAILABLE / UNAVAILABLE / UNKNOWN`;
- macOS Adapter（苹果适配层）passively discovers Docker and Podman CLI/runtime/Compose facts;
- `StandardPlatformCapabilities.CONTAINER_RUNTIME` is now published from real macOS adapter discovery rather than hard-coded UNKNOWN;
- imported macOS projects retain an optional Compose plan alongside the existing Python/Node language plan;
- no container Start / Stop / Logs implementation was added; that remains M6.2;
- no second lifecycle/coordinator was introduced.

Cloud evidence:
- SiftAlpha X macOS Host Runtime Run #36: PASS;
- Android W0 Cloud Build #745: PASS;
- M6.1 container capability / Compose plan probe: PASS;
- Compose manifest = `compose.yaml`;
- services = `db,web`;
- web published port = `18080`, target port = `80`;
- web depends_on = `db`;
- AVAILABLE plan = `READY`;
- UNAVAILABLE plan = `CAPABILITY_UNAVAILABLE`;
- UNKNOWN plan = `CAPABILITY_UNKNOWN`;
- cloud-host Docker = UNAVAILABLE;
- cloud-host Podman = UNAVAILABLE;
- published container capability = UNAVAILABLE;
- M4.1 / M4.2 / M5.1 / M5.2 / project-scoped STOP regressions all PASS;
- Android dependency isolation PASS.

Ventura packaging:
- `LSMinimumSystemVersion = 13.0`;
- user-facing ZIP SHA-256 = `7fe17f94269b94b8e4f6a37c7a8946a36c5f0dbbfd236afb2a00e6357f8e846d`;
- artifact = `siftalpha-macos-m6.1-ventura-x64-36`;
- artifact ID = `10805349421`;
- artifact digest = `sha256:38bce4b3e7c1863460bf01234b1f68f483a16447ade6effdc7ac96aecd883611`.

Current M6.1 state:

**PASS / COMPLETE（通过 / 完成）**.

Real Ventura capability/detection acceptance has passed. M6.2 remains NOT STARTED and still requires its own implementation approval.


## 28. M6.1 Real Ventura Acceptance（真实 Ventura 验收）— PASS / COMPLETE

Acceptance host:
- macOS 13.7.8 Ventura
- Intel x86_64

Real probe evidence:
- `SIFTALPHA_MACOS_HOST=READY`;
- `SIFTALPHA_CORE_LOAD=PASS`;
- `host_process_execution = AVAILABLE`;
- `container_runtime = UNAVAILABLE`;
- Docker provider = `UNAVAILABLE`;
- Podman provider = `UNAVAILABLE`;
- managed Python = `AVAILABLE` / Python 3.14.7;
- shell = `AVAILABLE` / zsh 5.9 x86_64;
- missing Docker/Podman was correctly represented as a valid capability fact rather than a project/runtime failure.

Observed first-launch note:
- one initial probe invocation was terminated by the OS shell as `killed`;
- the immediately repeated identical probe completed normally with full valid output;
- because the same binary then completed all required diagnostics, this is not a current M6.1 blocker.

M6.1 final result:

**PASS / COMPLETE**.

M6.2 — Compose Runtime Workflow + Project Isolation remains **NOT STARTED**.


## 29. M6.2 Compose Runtime Workflow + Project Isolation（Compose 运行工作流 + 项目隔离）— CLOUD PASS

Accepted cloud HEAD:
`abde6c6a311d0c3c722ad7385bd44351df566f94`

Implemented boundary:
- keeps one existing `MacProductController` and one `MacProjectWorkflowCoordinator`;
- Compose is routed through the shared project lifecycle instead of a second runtime center;
- adds a generic `MacComposeContainerProvider` contract;
- Docker is preferred when Docker Runtime + Compose are both available;
- Podman may be selected when it provides the same required Compose capability;
- no Colima-specific, Docker-Desktop-specific, Mac-model-specific, Intel-only, Apple-Silicon-only, 8GB-only, or OpenBot-specific branch was added;
- adds `MacContainerEnvironmentAdvisor` using system version / architecture / CPU / memory / disk / provider facts for advice only;
- advisor never installs software or changes VM/container CPU, memory, or disk settings;
- Compose Prepare performs provider validation plus image pull/build as required by the plan;
- Compose Run / Status / Logs / Web / Stop / Restart are now wired into the shared coordinator;
- every project receives a stable hashed Compose project identity;
- all Compose operations carry the selected project's project identity;
- STOP uses targeted project-scoped Compose down and never performs a global container stop;
- runtime published ports are discovered from the active Compose project and still pass through Endpoint Probe before exposing a Web result;
- Normal Mode shows simplified Compose lifecycle/advice;
- Developer Mode exposes Provider / Compose project identity / per-service status / logs.

Cloud verification:
- SiftAlpha X macOS Host Runtime Run #39: PASS;
- Android W0 Cloud Build #748: PASS;
- M6.2 Compose workflow / isolation probe: PASS;
- Project A and Project B both detected as Compose;
- project identities are distinct;
- Prepare A / Prepare B PASS;
- Start A / Start B PASS;
- A = RUNNING and B = RUNNING;
- service states = db:RUNNING, web:RUNNING;
- Web source = CONTAINER_PORT and Endpoint Probe path PASS;
- Stop A PASS;
- after Stop A: A = STOPPED while B = RUNNING;
- Restart A PASS and A returns to RUNNING;
- final Stop A / Stop B PASS;
- M6.1 regression PASS;
- M4.1 / M4.2 / M5.1 / M5.2 / project-scoped process STOP regressions PASS;
- Android dependency isolation PASS;
- Ventura package baseline PASS with `LSMinimumSystemVersion = 13.0`.

Artifact:
- name = `siftalpha-macos-m6.2-ventura-x64-39`;
- artifact ID = `10808969382`;
- artifact digest = `sha256:a1898ea9a71d6474d5f1095324b575a476717543887b69da1ae33703acba42de`;
- user-facing ZIP SHA-256 = `b261767f3f373aeb9494ac92fdf125a8448b0399eadcb546c5c3546d69b41f2d`.

Current M6.2 state:

**CLOUD PASS / REAL VENTURA PENDING（云端通过 / 真实 Ventura 容器环境待验收）**.

M6 Final Closure Audit must not run until real macOS container execution, Web, Stop isolation, and Restart acceptance pass.


## 30. M6.2 Managed Container Environment Installer（托管容器环境安装器）— CLOUD PASS

Approved M6.2 scope expansion:

**Detect → Recommend → User Confirm → Install → Re-detect → Continue**

Accepted cloud implementation HEAD:
`1d3c8bcc23c94064c82b1ff652fc4eda7fd87ae4`

Behavior:
- Compose project import detects missing/unready container capability;
- generic system/provider facts produce an install recommendation;
- Normal Mode exposes an explicit **安装推荐容器环境** action;
- installation never starts without user confirmation;
- SiftAlpha-managed container toolchain is installed into the current user's SiftAlpha data directory;
- no Homebrew / MacPorts dependency is required;
- Intel x86_64 and Apple Silicon arm64 are selected by architecture capability, not device-model branches;
- managed environment currently consists of pinned Colima + Lima + Docker CLI + Docker Compose + Docker Buildx;
- managed Docker / Colima / Lima state and Docker CLI plugins use SiftAlpha-owned directories instead of overwriting the user's existing Docker configuration;
- upstream assets are version-pinned; release checksums are verified when upstream publishes them;
- after installation SiftAlpha re-detects Docker + Compose and automatically continues Prepare for the current project;
- an existing external provider in an unresolved state is preserved rather than overwritten by the managed installer;
- no automatic CPU / memory / disk tuning is performed.

Cloud verification:
- macOS Run #56 PASS;
- Android W0 #765 PASS;
- `SIFTALPHA_M62_CONTAINER_INSTALL_PROBE=PASS`;
- before install: advice = INSTALL_PROVIDER;
- generated plan = INSTALL_MANAGED_DOCKER;
- Normal Mode primary action = INSTALL_CONTAINER;
- user-approved installer invocation = exactly once in controlled probe;
- re-detect after install = READY;
- project environment ready = true;
- install phase = COMPLETE;
- automatic continuation ends at primary action = RUN;
- managed upstream assets for Intel and ARM were reachable in CI;
- previous M6.2 Compose workflow/isolation probe remains PASS;
- Ventura package baseline remains `LSMinimumSystemVersion = 13.0`.

Artifact:
- name = `siftalpha-macos-m6.2-managed-container-ventura-x64-56`;
- artifact ID = `10813026065`;
- artifact digest = `sha256:359d98ff4bba539529b9ef87031a8de278decd775dd11b6fc285ca405c1840c1`;
- user-facing ZIP SHA-256 = `46cef1fb0b29c9c58a51bee5930b7d9b9a95082d7d96ad39e7e16feac516f348`.

Current state:

**M6.2 = CLOUD PASS / REAL VENTURA MANAGED-INSTALL ACCEPTANCE PENDING**.

Real acceptance must still prove on macOS 13+:
1. click install recommendation;
2. confirm installation;
3. real managed container environment downloads / starts / verifies;
4. project automatically continues Prepare;
5. Run → Web works;
6. A/B Compose isolation and Restart still pass with the real provider.

M6 Final Closure Audit remains blocked until those real-device checks pass.


## 31. M6.2 Managed Installer R1 stale-error repair — CLOUD PASS

The Normal Mode presentation now prioritizes a valid managed-container install plan over stale Compose Prepare failure state. This repairs the real-machine case where the user previously saw “上一步没有完成 / 重新准备” instead of “安装推荐容器环境”.

Authority:
- functional HEAD `2e957e3130066ddc8fecbef04584afe470549cb9`;
- macOS Run #61 PASS;
- Android W0 #770 PASS;
- managed installer stale-error regression probe PASS;
- artifact ID `10814991305`;
- artifact digest `sha256:2c7e8a1e0018121e04f7aa92c4724e845e79fa89a857122e5d7bec40c5b4d303`;
- user-facing ZIP SHA-256 `5ac42a76cbaa5f77cf862d89074cff4071c65c83220e77d1a6022cb09d7d2ac3`.

Current state:
**M6.2 CLOUD PASS / REAL VENTURA MANAGED-INSTALL ACCEPTANCE PENDING**.


## 32. M6.2 Unified Prepare UX — CLOUD PASS

Latest user-approved M6.2 UX correction restores the product goal:

**the user imports a project and asks SiftAlpha to prepare it; SiftAlpha decides how the required environment is satisfied.**

Normal Mode:
- no separate `INSTALL_CONTAINER` primary action;
- missing Compose/container capability appears as ordinary **准备环境**;
- clicking Prepare may reuse an existing host provider or invoke the approved SiftAlpha-managed environment provisioner;
- Normal Mode only shows simple preparation state, not Colima/Lima/Docker/Buildx installation details;
- successful provisioning automatically continues project Prepare and ends at **运行**.

Developer Mode:
- retains the same shared Controller / Coordinator / provisioning path;
- adds **安装/修复环境** for explicit diagnostics/retry;
- exposes environment plan, provider/advisor facts, components, side effects, phase, status and installer raw log;
- does not create a second installer or second runtime state machine.

Cloud authority:
- functional HEAD `29a1ddd9709e07d5b17cd6cc9561f68bd2fee170`;
- macOS Run #69 PASS;
- Android W0 #778 PASS;
- `SIFTALPHA_M62_CONTAINER_INSTALL_PROBE=PASS`;
- before primary = PREPARE;
- installer calls = 1;
- Prepare + environment provision = PASS;
- after advice = READY;
- after environment ready = true;
- developer install phase = COMPLETE;
- after primary = RUN;
- original M6.2 Compose A/B isolation / STOP / Restart / Web probe remains PASS;
- Ventura package minimum remains macOS 13.0.

Artifact:
- `siftalpha-macos-m6.2-unified-prepare-r2-ventura-x64-69`;
- artifact ID `10817335040`;
- artifact digest `sha256:7a6921d51d0853800bc2fc5588a2a515b6fe55e13088ab0855e5b11cff4ed8c7`;
- user ZIP SHA-256 `a95b215a2dcb3de909260b37e4cd18c06a551ca5b4d942c0fc0c67cbc4f50df5`.

Current state:
**M6.2 CLOUD PASS / REAL VENTURA UNIFIED-PREPARE ACCEPTANCE PENDING**.

M6 remains open until real managed-environment preparation and real Compose A/B isolation pass. Do not create M6.3.


## 33. M6.2 R3 checksum manifest repair — CLOUD PASS

Real Ventura Developer Mode evidence exposed a checksum-manifest parsing bug during managed environment preparation:

`lima-2.2.0-Darwin-x86_64.tar.gz`
- downloaded SHA-256: `0d6f99c19f6e4bc3c92730c4c29d929e6927f0cb0a0ba1a84383367135a8ff31`;
- official GitHub Release asset digest: the same value;
- previous installer incorrectly selected a different digest from Lima's multi-file `SHA256SUMS` manifest.

Root cause:
the checksum parser accepted the first line beginning with any 64-character SHA-256 digest, even when that line belonged to a different asset.

Repair:
- multi-file checksum manifests now require an exact asset filename match;
- a bare checksum is accepted only for a true single-digest sidecar;
- missing filenames fail closed rather than selecting another asset's digest;
- regression tests cover exact Intel Lima selection, unknown-file rejection, and single-digest sidecars.

Authority:
- functional HEAD `d1cf3ba4b90a8b47ded7e07f3c09140c5d754ae9`;
- macOS Run #73 PASS;
- Android W0 #782 PASS;
- macOS Core / macOS tests PASS;
- managed-container flow probe PASS;
- Compose A/B isolation / STOP / Restart / Web regressions PASS;
- artifact `siftalpha-macos-m6.2-unified-prepare-r3-ventura-x64-73`;
- artifact ID `10819645032`;
- artifact digest `sha256:27160dc93331dd2c545e559a8492953d43be2ff4f3d2d1455c3027a97a3487d8`;
- user ZIP SHA-256 `5a7165eade86a179c3933543b578b84a45b273b735d5955882a21d3d73c100ba`.

Current state:
**M6.2 CLOUD PASS / REAL VENTURA MANAGED-ENVIRONMENT RETEST PENDING**.
