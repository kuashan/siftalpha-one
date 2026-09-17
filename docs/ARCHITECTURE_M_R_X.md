# SiftAlpha M / R / X Architecture Definition

最后更新：2026-09-16  
alpha30 实现起始基线与 Run #90 真实设备证据仍为历史记录。  
当前 alpha32 source baseline：本分支 alpha31 version-identity baseline 之后的 M → R integration commit。  
工作分支：`codex/siftalpha-x-embedded-cpython-spike`  
当前 Runtime prototype：`versionCode 108` / `versionName 0.8.0-alpha32`

本文件是 SiftAlpha M / R / X 的规范架构定义。它取代当前术语中的：

> SiftAlpha X = SiftAlpha Execution Runtime / Execution System

旧定义仍可在历史 DEV_LOG、历史审计、源码命名和设备诊断中出现；那些内容记录当时真实使用的术语，不应被机械改写。自本文件生效后，当前正式语义统一为：

> M 管。R 跑。X = M + R。

## 1. 三个架构概念

| 概念 | 正式名称 | 负责什么 | 当前状态 |
|---|---|---|---|
| M | SiftAlpha M — Management System | 管理、控制、协调、观察和用户交互 | 现有 Studio 能力的架构归类 |
| R | SiftAlpha R — Runtime System | 让项目真正运行，并维护 Runtime / Session / Lifecycle | 当前由 Embedded CPython prototype 代表的 Runtime 方向 |
| X | SiftAlpha X — M + R | M、R、稳定的 M ↔ R Interface 和完整产品验收的组合 | 尚未宣称完成 |

M、R、X 是架构概念，不是必须对应三个 package、三个进程、三个 APK 或三个仓库。现有代码中的 `SiftAlphaX`、`siftalphax`、`SIFTALPHA_X_*` 等名称属于历史实现命名，本轮不迁移。

## 2. SiftAlpha M — Management System

### 2.1 定义

M 是 SiftAlpha 的 Management Layer、Control Layer 和 User Interaction Layer：

- Management：管理项目、身份、配置、准备和运行选择；
- Control：表达 START、STOP、STATUS、LOGS、RESTART 等管理意图；
- User Interaction：向用户呈现状态、日志、失败、Web 和恢复结果。

M 决定：

- 运行什么；
- 什么时候准备和运行；
- 什么时候停止或重启；
- 当前项目属于谁；
- 当前事实如何呈现；
- Web 页面是否满足安全打开条件。

M 本身不等于 Python Runtime，也不需要拥有 CPython 执行引擎才能成立。M 可以协调不同 Runtime Provider。

### 2.2 M 的职责

当前和目标架构中的 M 职责包括：

- Import：导入项目和维护项目入口；
- Detect：识别项目类型、入口、配置和 Web 特征；
- Project Management：项目列表、工作区、配置和维护；
- Project Identity：使用稳定的项目身份，而不是名称或列表位置；
- Configuration：配置模型、预检、保存、脱敏和安全注入编排；
- Prepare orchestration：请求目标 Runtime 准备环境；
- Runtime selection / coordination：选择或协调可用 Runtime Provider；
- START / STOP / STATUS / LOGS control：向 Runtime 发出管理意图；
- Restart orchestration：协调旧 Session 结束与新 Session 建立；
- Monitor：刷新、恢复、状态归并和失败呈现；
- Web Discovery：发现 Web candidate、执行 Endpoint Probe、维护 Browser policy；
- Browser：在项目、Runtime 和端点事实满足条件时提供浏览器入口；
- UI：Activity、Compose、页面状态、按钮策略和用户反馈。

M 不应通过猜测 stdout 文本来替代 R 已经提供的结构化 lifecycle facts。M 也不应依赖 CPython 的 GIL、JNI symbol 或 native thread 细节。

当前源码证据包括：

- `V04Activity`、`ProjectStore`、`V04ProjectGateway`：项目入口、项目身份和管理协调；
- `ProjectConfigurationInspector`、`ProjectConfigurationUiController`、`ProjectActionPolicy`：配置与动作策略；
- `StudioBrowser`、`RuntimeWebEndpointProbe`、`RuntimeWebAvailabilityTracker`：浏览器入口和端点可达性；
- `RuntimeWebPortDiscovery`、`RuntimeWebLogDiscoveryShell`：实现迁移期的 Runtime-scoped candidate discovery，产品控制权仍属于 M；
- `RuntimeWebStateStore`：当前 candidate 持久化，仍有按 `projectKey` 继承旧 candidate 的 ownership backlog。

## 3. SiftAlpha R — Runtime System

### 3.1 定义

R 是真正让用户项目在 Android 上运行起来，并维护运行期间事实和生命周期的系统。

R 的职责包括：

- Runtime initialization 和 environment；
- Language runtime hosting；
- Runtime implementation / backend；
- Project execution；
- Session identity、Session ID 和 generation；
- STARTING、RUNNING、terminal 等 execution lifecycle；
- stdout / stderr capture 和 execution result；
- STOP execution semantics；
- Restart / re-entry semantics；
- Runtime / process / session ownership；
- dependency and environment execution；
- failure isolation、recovery 和 capability reporting；
- 对 M 返回结构化 Runtime Facts。

R 是抽象的 Runtime System。R 不应在架构概念上永久等同于：

- CPython；
- Linux 或某个 Linux distribution；
- PRoot；
- Ubuntu 或 Alpine；
- Termux；
- 某个单独的 native library。

这些可以是 Runtime Component、Runtime Implementation、Runtime Environment Component 或 Runtime Backend，但不是 R 的完整定义。

### 3.2 当前源码中的 R 事实

当前 production runtime 源码已经有若干可复用的 Runtime-neutral seams：

- `RuntimeBackend`：可用性和 `RuntimeCommand` 执行抽象；
- `RuntimeAdapter` / `ExecutableRuntimeAdapter`：prepare、start、stop、status、logs、clean 合同；
- `ManagedProcessRuntime`：进程树、日志、状态和停止机制的复用实现；
- `RuntimeIdentity` / `RuntimeIdentityStore`：host/guest process identity、token、start time 和 PID/PGID ownership；
- `RuntimeLifecycle`、`RuntimeState`、`RuntimeResultLifecycleNormalizer`：生命周期事实与展示状态的归并；
- `RuntimeWebPortDiscovery`：消费项目进程和 Runtime identity 事实，而不是做全局端口扫描。

这些 seam 不表示一个完整、独立、已与 M 集成的 SiftAlpha R 已经完成。当前 `ProjectRuntimeController` 仍通过 `TermuxProotRuntimeHost` 组装 production Python/Node 执行命令；它代表当前外部 provider 路径的实现，不应被误写成 R 的永久定义。

## 4. Embedded CPython 与 SiftAlpha R 的关系

Embedded CPython 是 SiftAlpha R 当前第一个真实实现方向：

```
SiftAlpha R
    ↓
Embedded CPython Runtime implementation
    ↓
Python execution
```

它不是 R 的全部，也不是 X 的同义词。

alpha29 分支中的 Embedded CPython prototype 具有以下源码边界：

- `EmbeddedPythonNative.cpp` 以 process scope 初始化 CPython；使用 `std::once_flag`，并由 bootstrap thread 执行 `Py_InitializeFromConfig`；
- 初始化创建的 main thread state 通过 `PyEval_SaveThread()` 脱离并保留到 App process 结束；当前实现不调用 `Py_FinalizeEx()`；
- 每个 execution worker 建立自己的 attached thread state / GIL ownership，通过配对的 `PyGILState_Ensure()` / `PyGILState_Release()` 执行一次 session；
- `shared_ptr<Session>`、`gSession`、`gLastTerminalSnapshot`、monotonic generation 和单 ACTIVE session 共同保护实验生命周期；
- `EmbeddedPythonScripts` 只提供固定、可审查的 A/B/C 脚本；prototype 当前不接受任意项目代码、SAF imported project 或依赖安装；
- `EmbeddedPythonBridge` 与 production `RuntimeCommandHost`、`TermuxBackend`、`RuntimeAdapter` hierarchy 保持隔离；
- 因此 alpha29 证明的是 Embedded CPython Runtime prototype 的当前生命周期测试范围，不是“整个 R 已完成”，也不是 M ↔ R production integration 已完成。

## 4.1 R Project Script Execution Boundary（alpha30）

alpha30 在实验性 Embedded CPython 路径建立了最小的 file-backed Python project-script execution boundary。它是 SiftAlpha R 的当前实现增量，不表示任意 Python 项目、完整导入或生产 M ↔ R 集成已经完成。

### 4.1.1 显式 Project Execution Specification

每次执行由明确的 Project Execution Specification 描述：project identity、app-private execution root、relative entrypoint、relative working directory、runtime kind、session identity 和 generation。R 执行这个显式 target，不扫描或猜测用户想运行的文件。Project identity 与 Runtime Session identity 分离；同一项目的每次运行都创建新的 session/generation。

当前实验页面使用 APK 内置的可审查 project fixtures。M/experimental controller 将 fixture materialize 到 app-private 的 siftalphax/projects/<sessionId> 目录，再把绝对 staging paths 连同其他 spec facts 交给 Embedded R。SAF URI 不直接伪装成 native filesystem path；任意 SAF imported-project integration 不在 alpha30 范围内。

### 4.1.2 Root、entrypoint 与 working directory

R 要求 execution root 是 app-private staging root 的目录；entrypoint 必须是 root 内的 regular file；working directory 必须是 root 内的目录。Kotlin boundary 拒绝 absolute/traversal relative values，native boundary 再做 canonical containment、parent-component、regular-file、directory 和 symlink checks。缺少 entrypoint 会成为带有明确诊断的 FAILED terminal result；不会 fallback 到其他 Python 文件。

working directory 是本次 session 的 cwd，不使用 Android app process 的默认 cwd。native execution 临时切换到该目录，并在 session 结束时恢复原 cwd；当前仍保持 single active session，因此不宣称并发 cwd 安全。TMPDIR 同样在 session 内临时设置并恢复。

### 4.1.3 Python file execution semantics

R 从 staged entrypoint 读取 source bytes，以真实 entrypoint filename 编译，再用独立的 temporary __main__ module 和 globals 执行。__name__ 是 __main__，__file__ 是 entrypoint absolute path，sys.argv[0] 是 entrypoint，sys.path 在执行期间把 entrypoint directory 和 execution root 放在前面，因此当前 scope 支持 project-local sibling imports。执行后恢复原来的 sys.path、sys.argv、sys.modules[__main__]、stdout 和 stderr。

每个 session 都新建 __main__ namespace，并清理位于 app-private project staging base 下的 project modules，避免 tested fixture 的 helper module 或 globals 泄漏到下一 session。stdout/stderr 仍由当前 Session 捕获并绑定到 session ID/generation；uncaught exception 的 traceback 使用真实 entrypoint filename，而不是 <string>。

normal completion 与 SystemExit(0) 映射为 SUCCEEDED/exitCode 0；SystemExit(nonzero) 映射为 FAILED 并保留可表示的非零 exit code；普通 Python exception 映射为 FAILED/exitCode 1 并保留 traceback；alpha29 的 cooperative STOP 仍映射为 STOPPED/exitCode 130，并不把受控 KeyboardInterrupt 显示成普通 FAILED traceback。每条路径都恢复 Python capture/context，随后允许新的 CPython session re-entry。

### 4.1.4 当前边界

alpha30 的实现和 fixtures 只覆盖 app-private、file-backed、pure-Python project script execution。它没有实现 pip、requirements/pyproject dependency installation、venv、native wheels、arbitrary C extensions、blocking native/syscall hard-stop、SAF project import through R、并发 sessions、production sandbox 或 M ↔ R production integration。STOP 仍是 cooperative / limited interruption behavior，不是 universal hard-stop。


### 4.1.5 alpha30 Real-Device Acceptance Status

Run #90（ID `35083943639`）使用版本 `versionCode=106`、`versionName=0.8.0-alpha30`、source HEAD `0e2b069d93a0a8cd87df4f57f0e97da1cf918643` 和 artifact `siftalpha-w0-90`（ID `10441785815`，digest `sha256:afc012248071e5b43884aa8985ee07e94a4e23e1f7ecf4a5c4a960f01ff228da`）。真实 Android 设备在不重启 App process 的情况下完成：

`Generation 1 PROJECT A success`
→ `Generation 2 PROJECT B intentional failure`
→ `Generation 3 PROJECT C running`
→ `cooperative STOP → STOPPED/exitCode 130`
→ `Generation 4 PROJECT A new-session re-entry success`。

Test A 确认 CPython `3.14.7`、Android `aarch64`、`sys.platform=android`、`TERMUX=NOT_USED`、`PROOT=NOT_USED`，且此前 `/data/user/0` symlink-component validation error 未复现；Test B 确认 stdout/stderr 和真实 staged `main.py` traceback；Test C 确认当前纯 Python project 的 cooperative STOP。结论为：

**SiftAlpha R Embedded CPython alpha30 Project Script Execution Boundary real-device acceptance = PASS（仅针对 app-private、file-backed、pure-Python fixture 测试范围）。**

这更新了 alpha30 从 source/CI ready 到真实设备验收通过的状态，但不把 R 或 X 写成已完成，也不扩展对 arbitrary external/SAF projects、dependencies、native packages、blocking native/syscall hard-stop、concurrency、process-death recovery、production M ↔ R 或 Web/Browser integration 的承诺。完整设备证据见 [DEV_LOG.md](DEV_LOG.md) 和 [TEST_MATRIX.md](TEST_MATRIX.md)。

## 5. Runtime Provider 与 SiftAlpha R

### 5.1 术语区分

Runtime Provider 是“向 M 提供项目运行能力的实现来源”。它不是 SiftAlpha R 的同义词。

| 术语 | 含义 | 当前例子 |
|---|---|---|
| Runtime Provider | M 可以调用的运行能力来源 | Termux-based external Runtime Provider |
| Runtime implementation | Provider 内部实际承载代码的实现 | 当前 Embedded CPython implementation prototype |
| Runtime Environment | 运行所需的环境组件集合 | PRoot、Ubuntu userspace、Python environment |
| SiftAlpha R | SiftAlpha 对 Runtime 架构、控制、Session、Lifecycle、Ownership、Recovery 和契约的系统定义 | 当前正在建立的 Runtime System |

迁移期允许 M 同时协调：

```
M
├── External Runtime Provider → Termux
└── SiftAlpha R direction → Embedded Runtime implementations
```

长期如果 R 成为 M 的自主 Runtime Provider，目标形态可以是：

```
M
    ↓
M ↔ R Interface
    ↓
SiftAlpha R
    ↓
Runtime implementation
    ↓
User Project
```

本轮只定义这种边界，不实现 provider 替换或 M ↔ R 集成。

### 5.2 Termux 的正式分类

Termux is NOT M.  
Termux is NOT R.  
Termux is NOT X.

当前 Termux + PRoot + Ubuntu + Python 属于 External Runtime Provider / External Runtime Environment。M 已经能够通过现有 production control path 调用它。这个分类不删除 Termux 支持，也不否认它当前提供的真实运行能力。

R 不应被定义成 Termux、PRoot、Ubuntu 或 Linux distribution。未来 R 可以选择、封装或替换这些组件，但 R 的架构责任仍然是 Runtime control、Session、Lifecycle、Identity、Environment、Process ownership、Recovery 和 M ↔ R contract。

## 6. SiftAlpha X — M + R

### 6.1 正式定义

SiftAlpha X 是：

```
SiftAlpha M
    +
SiftAlpha R
    +
stable M ↔ R Interface
    +
complete product integration and acceptance
```

X 的完成条件至少包括：

1. M 的管理、控制、观察和用户交互能力形成稳定产品闭环；
2. R 能以明确的 Runtime contract 承载目标项目和生命周期；
3. M 与 R 通过结构化 interface 集成，M 不依赖 CPython/JNI internals；
4. START、STOP、STATUS、LOGS、RESTART、RECOVERY、Web/Browser 等产品流程完成对应范围验收；
5. 失败边界、Runtime capability、identity、ownership 和安全限制被明确报告。

因此：

- M 完成 ≠ X 完成；
- R prototype 成功 ≠ X 完成；
- Embedded CPython 成功 ≠ X 完成；
- alpha29 real-device lifecycle acceptance ≠ X acceptance passed。

当前仓库仍应使用 “SiftAlpha X Experimental” 作为既有实验页面的兼容显示名称，但该 UI 名称不改变 X 的新架构定义。

## 7. M ↔ R Interface 原则（本轮只定义）

本接口是架构 contract，不是本轮实现的 source API。

### 7.1 M → R：Management Intent

| Intent | M 表达的意图 | R 负责的事实结果 |
|---|---|---|
| `prepare` | 为指定项目准备目标 Runtime | environment readiness、failure 或 capability facts |
| `start` | 以新的 Session / generation 开始执行 | session identity、lifecycle、output 和 result |
| `stop` | 请求当前 Session 停止 | request state、interrupt/stop result、terminal publication |
| `status` | 查询当前 Runtime 和 Session 事实 | authoritative lifecycle、identity、phase、ownership |
| `logs` | 读取当前 Session / Runtime 的日志 | session-scoped stdout/stderr/log facts |
| `restart` | 结束旧 Session 后建立新 Session | generation change、new session identity 和 recovery facts |

未来可扩展 `clean`、`recover` 等 intent，但必须保持结构化、显式和可验证。

### 7.2 R → M：Runtime Facts

R 应向 M 返回结构化事实，至少包括：

- runtime identity、runtime kind、runtime availability；
- runtime capability 和 capability limitations；
- session identity、session ID 和 generation；
- lifecycle state、runtime phase 和 terminal result；
- stop phase、stop result 和 stop limitation；
- exit code；
- stdout、stderr、structured failure 和 diagnostic fields；
- process / environment facts；
- future endpoint candidate 或 endpoint facts（如 R 提供）。

M 不应读取或解释 `PyGILState`、`PyThreadState`、JNI function name、native pointer 或 CPython finalization state。R 不应依赖 Activity、Compose、Browser UI 或 Android presentation state。

### 7.3 Web 边界

Web Discovery、Endpoint Probe、Browser policy 和 UI presentation 属于 M 的管理能力。R 可以提供 Runtime Identity、Session Identity、process facts、endpoint facts 或 endpoint candidates；M 负责：

- 判断 candidate 是否属于当前项目/Session；
- 执行 Endpoint Probe；
- 应用 Browser safety policy；
- 向用户显示 Web 状态和打开入口。

当 R 已提供结构化 lifecycle state 时，M 不应通过 stdout 关键字猜测核心生命周期。当前源码的 Web Discovery 位于迁移期 shared runtime package，不改变上述产品 ownership 定义。

## 8. SiftAlpha R Core Independence Principle

SiftAlpha R 的 Runtime architecture、Runtime control layer、Session lifecycle、Runtime ownership、Process ownership、Environment management、Recovery semantics 和 M ↔ R contract 应尽可能由 SiftAlpha 自主定义和控制。

“R 自主”不意味着重新实现 Python interpreter，也不意味着重新实现所有 system libraries。CPython、system libraries 和其他 foundational runtime libraries 可以按其许可证作为成熟基础组件使用。

自主性的重点是：

- Runtime Architecture；
- Runtime Control Layer；
- Session 与 Lifecycle；
- Identity 与 Ownership；
- Environment 与 Project Preparation；
- Process Ownership；
- Recovery；
- M ↔ R Contract；
- capability 和 failure boundary。

对第三方实现的许可证、商业使用和 copyleft 风险，不通过 rename / rewrite 规避，也不在本文件中作未经验证的法律保证。后续应以公开文档、标准、接口和可观察行为为基础进行独立架构设计。

## 9. alpha29 Real-Device Evidence（正式记录）

完整证据同时保存在 [DEV_LOG.md](DEV_LOG.md) 和 [TEST_MATRIX.md](TEST_MATRIX.md)。下表只记录本架构定义需要的事实。

### 9.1 构建身份

| 字段 | 事实 |
|---|---|
| version | `versionCode=105`, `versionName=0.8.0-alpha29` |
| branch tested | `codex/siftalpha-x-embedded-cpython-spike` |
| source HEAD | `a5691fff049a6be25ccade78f1ef23ce869543bf` |
| GitHub Actions | Run #80, ID `35060381162` |
| artifact | `siftalpha-w0-80`, ID `10432576095` |
| artifact digest | `sha256:94cf4d1ead49e8500f9b2467765a983b8e0164be354c1e59083670763945eaba` |
| APK SHA-256 | `2a7b7434817ffec53863ad9fd16745bdfef101677c5470a81efdab78aa269cc6` |

### 9.2 用户确认的真实设备结果

| 场景 | 观察结果 | 结论 |
|---|---|---|
| Test A | `ENGINE=CPYTHON`, `TERMUX=NOT_USED`, `PROOT=NOT_USED`; generation 1; `SUCCEEDED`, `TERMINAL`; STOP `IDLE/NONE`; exit 0; Python 3.14.7; `sys.platform=android`; machine `aarch64`; stdlib `json OK` | PASS |
| Test B | generation 3; `FAILED`, `TERMINAL`; STOP `IDLE/NONE`; exit 1；stdout 为 `SIFTALPHA_X_TEST_B_STDOUT`；stderr 含 `SIFTALPHA_X_TEST_B_STDERR` 和预期 `RuntimeError: SIFTALPHA_X_TEST_B_FAILURE` | PASS（intentional failure） |
| Test C running | generation 4; `RUNNING`; `RUNTIME_PHASE=PYTHON_EXEC_BEGIN`; STOP `IDLE/NONE`; exit `-` | PASS（STOP 前置条件） |
| Test C cooperative STOP | 同一 session、generation 4；`STOPPED`, `TERMINAL`; `STOP_REQUEST_RETURNED/INTERRUPT_DELIVERED`; exit 130；stdout 含 repeated `SIFTALPHA_X_TEST_C_TICK` 和 `SIFTALPHA_X_TEST_C_COOPERATIVE_STOP`；stderr 为 `SIFTALPHA_X_STOP=COOPERATIVE`；无普通 FAILED traceback | PASS |
| post-stop re-entry | 不重启 Android application process；新 session；generation 5；`SUCCEEDED`, `TERMINAL`; STOP `IDLE/NONE`; exit 0；CPython 3.14.7、Android/aarch64、stdlib json OK | PASS |

Test B 的 generation 从 1 到 3。generation 2 没有出现在用户提供的真实设备证据中；本记录只标记为 observed evidence gap / unexplained intermediate generation，不推断原因，也不把它分类为 Runtime failure。

这些结果支持以下有限结论：

- Embedded CPython 3.14.7 可在真实 Android aarch64 设备执行；
- 当前 prototype 的 A/B/C 生命周期测试不依赖 Termux 或 PRoot；
- 当前 process-scoped initialization 支持本测试范围内的 multi-Session re-entry；
- success、intentional failure、stdout/stderr capture、cooperative interruption、STOPPED/130 和 STOP 后 re-entry 在该测试范围内成立。

## 10. 明确未被 alpha29 证明的能力

alpha29 不表示 R 已完成，也不表示 X 已达到 acceptance。以下能力没有被本证据证明：

- 任意第三方 Python project 的兼容性；
- arbitrary C extension、blocking native code、blocking syscall 或 uninterruptible native code 的 interruption；
- universal hard-kill 或 forced native-thread termination；
- pip、native wheel、numpy、pandas、scipy、venv 或通用 dependency installation；
- SAF imported project execution through R；
- Web Discovery、Endpoint Probe、Browser 与 R 的 production integration；
- M ↔ R production integration；
- process death recovery、Android lifecycle recovery；
- concurrent runtime、多项目并行、完整 isolation 或 production-grade sandboxing；
- 生产 Runtime provider 被 Embedded CPython 取代。

`PyThreadState_SetAsyncExc` 相关 STOP 只能描述为 cooperative / limited interruption behavior，不能描述为 universal hard-stop mechanism。

## 11. Legacy naming 与后续迁移

本轮保持以下名称不变：

- Kotlin package/class：`siftalphax`、`EmbeddedPython*`；
- native symbols 和文件名：包括 `EmbeddedPythonNative.cpp`；
- diagnostic compatibility keys：包括 `SIFTALPHA_X_*`；
- app-private asset/filesystem paths：包括 `siftalphax/...`；
- tests、workflow、evidence labels 和历史 artifact 名称。

它们是 Legacy Implementation Naming，不再表达 “X = Execution”。未来如需统一命名，应单独建立 M/R/X Naming Migration 任务，逐项审查：

- class/package rename；
- JNI symbol 和 native filename；
- diagnostics compatibility；
- filesystem/asset path migration；
- tests、APK evidence 和 historical traceability；
- production source 与 UI resource 的迁移顺序。

本轮不做 global blind replacement。

## 12. 当前变更边界与维护规则

alpha30 在保留 alpha29 lifecycle acceptance 的前提下，增加了实验性 file-backed project-script boundary；Run #90 的真实设备证据仍是已归档的 alpha30 事实。alpha32 在此基础上增加第一条最小 M → R 接线：M 以 SAF documentId 管理项目，解析明确 Python entrypoint，将受限项目树复制到 app-private staging，再调用既有 Embedded R Session/native path。alpha32 的真实设备验收须另行记录，不得与 alpha30 Run #90 事实混写。

本轮变更边界：

- 当前版本为 `versionCode=108`、`versionName=0.8.0-alpha32`；alpha30/Run #90 的 real-device evidence 仍作为历史 accepted baseline 保留；
- 不修改 production Termux Runtime path、Runtime Identity、Web Discovery 或既有 M 管理路径的默认行为；alpha32 只增加明确 opt-in 的 Embedded R bridge；
- alpha32 实现的是受限的 M-managed SAF project → app-private staging → Embedded R execution boundary，不实现完整 M ↔ R production integration、依赖安装、Web、Browser、PRoot 或 Node 新功能；
- alpha29/alpha30 real-device evidence 是 user-confirmed evidence，与 CI/build evidence 分开；
- alpha32 的 CI 和后续真实设备验收必须以新的 source/version identity 单独记录。

相关入口：

- [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md)：项目上下文和当前基线；
- [DEV_LOG.md](DEV_LOG.md)：按时间追加的工程和验收记录；
- [TEST_MATRIX.md](TEST_MATRIX.md)：Regression / Verification Evidence；
- [README.md](README.md)：文档维护规则。

## 13. alpha32 First M → R Integration Boundary

alpha32 的架构闭环是：

`SiftAlpha M (SAF Project/documentId)`
→ `deterministic entrypoint resolution`
→ `bounded app-private staging`
→ `SiftAlpha R EmbeddedPythonSession`
→ `Embedded CPython`
→ `structured Snapshot`
→ `M RuntimeState / output / STOP`。

这不是新的 Runtime Provider Framework。M 继续由 `ProjectRuntimeController` 协调，R 继续由现有 `EmbeddedPythonSession` 管理 Session/generation/native lifecycle；本轮只增加一个业务边界组件 `EmbeddedPythonProjectStager` 和必要的纯策略/状态映射 helper。R 不实现 Termux shell semantics，M 的 Embedded R 入口也不调用 `RuntimeCommandHost`、`RuntimeBackend.execute`、RUN_COMMAND、PRoot 或 Ubuntu。

SAF source project 与 execution staging root 是不同的 ownership boundary：M/ProjectStore 读取 source，stager 创建和清理 app-private copy，R 只接收 explicit execution input。M 不伪造 sessionId/generation，R 不读取 Activity/Compose/Browser 状态。未实现 pip、venv、依赖安装、Web/Browser integration、并发、process-death recovery 或 universal hard-stop；alpha32 仍不表示 R 或 X 已完成。


## alpha42 Presentation Boundary

M 的一次性 CLI 呈现边界为：

`safe Runtime output`
→ `ANSI/OSC sanitization`
→ `generic Link List Rich Result`
→ `Unified Open`
→ `native Rich Result Viewer`。

项目卡片只有一个主要“打开”动作。Presentation routing 为 `WEB > RICH_RESULT > NONE`：已通过现有 Endpoint Probe 的 Web Dashboard 继续进入外部 Browser；没有可用 Web 但存在安全 Link List 时进入 App 内 Result Viewer；只有 Raw Log 时不生成打开目标。长期自动化以及未来 Automated Quant Trading 的主要用户界面仍是 Live Web Dashboard，Raw Log 是 Secondary Diagnostics。

该 alpha42 结果缓存是 Activity-lifetime 级别，不引入数据库；接受新的 START 清除旧 Rich Result，空 STATUS/LOGS 保留已有结果。alpha42 不改变 Runtime lifecycle、Web Discovery scope、Termux/Embedded R 运行链，也不表示 arbitrary Rich Result types、R complete 或 X complete。
