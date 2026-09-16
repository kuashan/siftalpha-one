# SiftAlpha Studio 项目上下文

最后更新：2026-09-16（M / R / X Architecture Definition + alpha29 Real-Device Evidence Consolidation）
当前仓库：[kuashan/siftalpha-one](https://github.com/kuashan/siftalpha-one)  
产品基线分支：`main`（当前 main HEAD：`dff275575a9cdbd0564d394c4626cd7d9bb22637`）  
M / R / X 架构与 Runtime prototype 工作分支：`codex/siftalpha-x-embedded-cpython-spike`  
当前验证版本：0.8.0-alpha29 / versionCode 105（Embedded CPython Runtime prototype；alpha29 real-device evidence 已记录）  
当前文档定义：`M = Management System`，`R = Runtime System`，`X = M + R`  
规范架构定义：[ARCHITECTURE_M_R_X.md](ARCHITECTURE_M_R_X.md)  
Production baseline merge：[893229c](https://github.com/kuashan/siftalpha-one/commit/893229ce26d49a6ea22c79d6e2be85290cb8b0c3)（历史基线记录）  
上一版历史发布：[W2 test APK · w2-test-80efac5](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-80efac5)  
最近一次带 Release 的历史 APK：[W2 test APK · w2-test-4e899c6](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-4e899c6)；Roadmap/docs base main 构建产物见 [Run #70 artifact](https://github.com/kuashan/siftalpha-one/actions/runs/34989822426/artifacts/10405122896)

## 1. 项目目标

SiftAlpha Studio 是一个 Android 端项目工作台，用于在手机上：

- 导入和管理项目；
- 准备、运行、观察、停止、恢复项目；
- 使用 Python 作为第一优先运行路径；
- 支持 Node.js 独立或辅助运行路径；
- 读取运行状态、日志和输出；
- 检测本地可视化页面，并在端点真正可达时通过浏览器打开；
- 维护项目环境、配置、缓存和运行数据。

项目采用“手机真机使用 + GitHub 云端构建”的工作方式。开发环境不要求本地安装 Gradle、JDK 或 Android SDK。

## 2. 产品定义与 SiftAlpha M / R / X

> 当前规范定义见 [ARCHITECTURE_M_R_X.md](ARCHITECTURE_M_R_X.md)。本节保留项目上下文中的摘要；旧的 “SiftAlpha X = Execution Runtime / Execution System” 只在历史记录中保留，不再作为当前定义。

### 2.1 SiftAlpha Studio 与 M

SiftAlpha Studio 是一个面向 Android 的项目运行与管理平台。它的主要目标不是成为 IDE，也不是替代 VS Code、PyCharm、Codex 等外部开发工具。用户可以在外部开发环境完成项目开发，再把已经写好的项目导入 Studio，在 Android 手机上准备、运行、管理和使用。

当前产品流程是：

Import（导入）→ Detect（识别）→ Prepare（准备）→ Run（运行）→ Monitor（监控）→ Use（使用）→ Stop / Restart（停止 / 重启）。

这些管理、控制、协调和用户交互能力在架构上属于：

**SiftAlpha M — Management System**。

M 负责 Import、Detect、Project Management、Project Identity、Configuration、Prepare orchestration、Runtime selection / coordination、START、STOP、STATUS、LOGS、Restart orchestration、Monitor、Web Discovery、Endpoint Probe、Browser 和 UI。M 决定运行什么、何时运行、何时停止、向用户显示什么以及 Web 页面是否可以安全打开。M 本身不等于 Python Runtime。

### 2.2 SiftAlpha R

**SiftAlpha R — Runtime System** 是真正让项目在 Android 上运行，并维护 Runtime / Process / Session / Lifecycle 的系统。

R 负责 Runtime initialization、environment、language runtime hosting、project execution、Session identity、generation、stdout/stderr、execution result、STOP semantics、restart/re-entry、ownership、dependency/environment execution、failure isolation、recovery 和 capability reporting。

R 是抽象 Runtime System，不永久等同于 CPython、Linux、PRoot、Ubuntu、Alpine、Termux 或某个 system library。当前已经真实实现并在 alpha29 真机验证的 Embedded CPython，是 R 的第一个 Runtime implementation / backend prototype，不是 R 的完整定义，也不表示整个 R 已完成。

### 2.3 SiftAlpha X

**SiftAlpha X = SiftAlpha M + SiftAlpha R**，并需要稳定的 M ↔ R Interface 和完整产品集成/验收。

因此：

- M 完成 ≠ X 完成；
- R prototype 成功 ≠ X 完成；
- Embedded CPython 成功 ≠ X 完成；
- 只有 M、R、接口集成和产品验收都满足定义，才可称 X achieved / X acceptance passed。

历史 DEV_LOG 和历史审计中的 “SiftAlpha X — SiftAlpha Execution Runtime” 是当时真实使用的旧架构命名，现已 superseded；本文件不改写历史事实。

### 2.4 Termux 与 Runtime Provider

Termux is NOT M. Termux is NOT R. Termux is NOT X。

当前 Termux + PRoot + Ubuntu + Python 属于 **External Runtime Provider / External Runtime Environment**，是 M 可以调用的运行能力来源。Runtime Provider 不等于 SiftAlpha R；它描述“向 M 提供运行能力的实现来源”。

迁移期允许：

```
M
├── External Runtime Provider → Termux
└── SiftAlpha R direction → Embedded Runtime implementations
```

长期目标是 M 通过稳定的 M ↔ R Interface 协调自主的 SiftAlpha R，但本轮只定义边界，不实现 M ↔ R production integration。

### 2.5 当前架构状态

当前 production `ProjectRuntimeController` 仍通过 `RuntimeCommandHost` / `TermuxProotRuntimeHost` 使用外部 Termux provider；`RuntimeBackend`、`RuntimeAdapter`、`ManagedProcessRuntime`、`RuntimeIdentity` 和 lifecycle models 提供可复用的 runtime-neutral seam。

当前 Embedded CPython alpha29 prototype 位于隔离的 `siftalphax` implementation path，使用 process-scoped CPython initialization、per-session worker attach/detach、single active session 和固定实验脚本。它尚未接入 production `ProjectRuntimeController`，不能把 prototype 验收写成 R production 或 X acceptance。

### 2.6 M ↔ R Interface 原则

M 向 R 表达结构化 Management Intent，例如 `prepare`、`start`、`stop`、`status`、`logs`、`restart`。R 向 M 返回 Runtime Facts，例如 runtime/session identity、generation、lifecycle state、runtime/stop phase、stop result、exit code、stdout/stderr、structured failure、capability 和 availability。

M 不应依赖 CPython/JNI internals；R 不应依赖 Activity、Compose 或 Browser UI。Web Discovery、Endpoint Probe、Browser policy 和 UI 属于 M；R 可以提供 Runtime Identity、Session Identity、process facts 和 endpoint facts。接口本轮只定义，不实现。

### 2.7 R Core Independence Principle

**SiftAlpha R Core Independence Principle**：R 的 Runtime architecture、control layer、Session lifecycle、Runtime/Process ownership、Environment management、Recovery semantics 和 M ↔ R contract 应尽可能由 SiftAlpha 自主定义和控制。

这不意味着重新实现 Python interpreter 或所有 system libraries。CPython 等成熟基础组件可以按其许可证使用；不通过 rename / rewrite 规避许可证，也不在项目文档中作未经验证的法律保证。

### 2.8 Legacy Implementation Naming

源码、package、native symbols、diagnostic keys、filesystem paths 和测试仍包含 `SiftAlphaX`、`siftalphax`、`SIFTALPHA_X_*` 等历史命名。本轮不 mass rename，不修改 alpha29 已验证的 Runtime source 或 evidence keys。

这些名称属于 Legacy Implementation Naming，不代表当前 “X = Execution”。未来如需统一 class/package/JNI/native filename/diagnostics/path/test 命名，应另立 M/R/X Naming Migration 任务，并审查 compatibility 与证据可追踪性。

## 3. 历史基础阶段与当前产品基线

早期项目曾使用 W0、W1A、W1B、W1C 记录基础建设历史。原 W2、W3、W4、W5 是历史规划标签；它们不再表示当前阶段、未来阶段、完成度门槛、阻塞容器或开发顺序。原规划覆盖的能力已经按真实实现并入当前产品基线。

### 3.1 Historical Foundation Stages

| 历史基础阶段 | 状态 | 说明 |
|---|---|---|
| W0 | 已完成 | GitHub Actions 云端校验、构建、APK、签名和发布证据已经建立。 |
| W1A | 基本完成 | Compose、Material 3、主题和设置能力已有，但部分旧页面仍是 Views。 |
| W1B | 核心完成，持续收敛 | 已有状态快照、动作策略、生命周期和安全守卫；协调逻辑仍需继续从 Activity 收敛。 |
| W1C | 已完成主要验收 | 首页、导航、项目列表和筛选能力已落地并完成真机验证。 |

### 3.2 Current Product Baseline

当前产品基线直接按真实能力组织，而不是按已退役的阶段编号组织：

- 项目管理与身份：SAF 项目导入、项目列表、单项目工作区和稳定 project identity。
- 配置与安全：配置模型、检查/预检、编辑器、ActionPolicy、ProjectSecretStore、脱敏和安全守卫。
- Runtime：Python 第一优先运行路径、受限 Node 路径、PREPARE、START、STATUS、LOGS、OUTPUT、STOP、RESTART 和恢复。
- Runtime identity：当前项目运行时的身份、进程归属、日志归属和 Web discovery ownership 边界。
- Web：project-scoped procfs discovery、Runtime log fallback、Endpoint Probe、可用性跟踪和 Browser 安全入口。
- 环境与维护：环境检测、工具管理、缓存、清理保护、编辑器、设置和语言能力。
- 诊断与证据：结构化失败信息、运行日志、云端构建、单元测试、APK 签名和真机回归记录。

未来 Roadmap 尚待重新定义。本文件不创建 W6 或其他新的阶段编号。

## 4. 不可破坏的产品规则

### 4.1 状态必须分开表达

环境、配置、进程生命周期和 Web 可用性是四种不同事实，不能合并成一个“全部就绪”。

### 4.2 项目身份必须可信

项目操作使用 SAF 的稳定 `documentId`，不能使用项目名称、列表下标或临时排序位置作为身份。

### 4.3 运行安全

- PREPARE 与 START 分开，准备完成后由用户主动运行。
- 运行中的项目始终保留 STOP 能力。
- UI 按钮禁用只是反馈，底层动作入口仍必须再次检查。
- STOP 完成后，项目必须能够再次 START。
- 不确定状态时优先 STATUS 恢复，不能凭旧界面猜测进程是否存在。
- 清理项目环境、共享工具、缓存和删除源码必须分开确认。

### 4.4 Web 安全

Browser 只有在当前项目进程存在、Android 回环端点真实可达并且 URL 已验证时才开放。检测到“可能是网页”不等于允许打开浏览器。

### 4.5 隐私与配置

运行输出、配置值和日志不得泄露密钥。Studio 保存的配置使用 Android Keystore 保护并在运行时注入，不写回项目源码或 GitHub。

## 5. 当前 M / R / Runtime Provider 架构基线

当前源码处于迁移期：M 的产品管理能力已经存在；production runtime 仍通过外部 Termux provider 执行；Embedded CPython 是隔离的 R Runtime implementation prototype。

### 5.1 M 的当前实现

- `V04Activity`：Runtime Center 页面组装、用户动作入口、结果协调和 Web/配置 orchestration。
- `ProjectUiSnapshot`、`ProjectActionPolicy`：UI 事实快照和动作策略。
- `ProjectStore` / `V04ProjectGateway`：SAF 项目树、文件和稳定 project identity。
- `ProjectConfigurationInspector`、`ProjectConfigurationUiController`、`ProjectSecretStore`：配置检测、编辑、安全保存和运行时注入编排。
- `StudioBrowser`、`RuntimeWebEndpointProbe`、`RuntimeWebAvailabilityTracker`：Browser policy、端点验证和可用性呈现。
- `RuntimeWebPortDiscovery`、`RuntimeWebLogDiscoveryShell`、`RuntimeWebStateStore`：当前 shared runtime package 中的 Web candidate 发现/持久化实现；产品 ownership 仍属于 M。

### 5.2 R 的当前 seam 与 prototype

- `RuntimeBackend`、`RuntimeAdapter` / `ExecutableRuntimeAdapter`：runtime-neutral command 和 action contract。
- `ManagedProcessRuntime`：当前 external provider 路径复用的进程树、日志、状态和 stop mechanics。
- `RuntimeIdentity` / `RuntimeIdentityStore`：host/guest identity、token、start time、PID/PGID 和 ownership facts。
- `RuntimeLifecycle`、`RuntimeState`、`RuntimeResultLifecycleNormalizer`：生命周期事实、持久化和结果归并。
- `EmbeddedPythonNative.cpp` 与 `EmbeddedPython*`：alpha29 真实设备验证的隔离 Embedded CPython Runtime prototype。

### 5.3 当前 provider 边界

`ProjectRuntimeController` 当前通过 `TermuxProotRuntimeHost` 生成 Python/Node 命令；`TermuxBackend` / `TermuxContract` 维护 Termux control path。这些是 current external Runtime Provider implementation，不能被写成 R 的永久定义。

当前 alpha29 Embedded CPython prototype 不依赖 Termux/PRoot，并未接入 production `ProjectRuntimeController`。这两个事实可以同时成立：M 已经能使用 External Runtime Provider；R 的第一个 Runtime implementation prototype 正在独立验证。

`RuntimeWebStateStore` 仍主要按 `projectKey` 持久化 candidate，旧 Runtime 的 candidate 与新 Runtime identity/generation 的 ownership chain 尚未完全闭合；这属于后续 hardening backlog，不在本轮实现。

## 6. 配置语义

配置检测分为“必需项”和“建议配置”两层：

1. .project.json.requiredEnv 是项目显式声明的权威来源，条目可以明确标记为 REQUIRED 或 OPTIONAL。
2. Python 的 os.environ[NAME] 属于高可信必需读取（STATIC_REQUIRED_READ）。
3. os.getenv(NAME)、os.environ.get(NAME) 和 .env.example 属于建议配置（STATIC_OPTIONAL_READ / ENV_EXAMPLE），因为静态代码无法可靠判断它们是否真的影响运行。
4. NAME 已在项目 .env 中有非空值，或已由 Studio 安全保存，则视为已配置；配置页面不显示值本身。
5. 运行结果明确报告缺失的环境变量时，该变量以 RUNTIME_DIAGNOSTIC 来源升级为当前修复周期的必需项，并影响下一次启动前预检。
6. 每个配置项都保留来源和证据（项目文件、代码文件/行号或运行诊断），让用户知道为什么出现提醒。
7. 普通硬编码 URL 不会自动判定为需要用户配置；只有通过项目声明、环境变量或运行结果明确要求时才进入配置流程。
8. 建议配置显示提醒但不阻止运行。只有 REQUIRED 缺失时，才显示必填向导或在后续运行前阻止 START。

## 7. 构建与发布基线

工作流：[.github/workflows/w0-cloud-build.yml](../.github/workflows/w0-cloud-build.yml)

云端流程包括：

1. GitHub Actions 安装 JDK、Android SDK 和 Gradle；
2. 恢复稳定测试签名；
3. 执行仓库校验器；
4. 执行单元测试并组装 APK；
5. 记录 APK SHA-256、签名证书和包信息；
6. 只在提交消息严格为 `Publish W2 test APK` 时创建测试 Release。

稳定测试签名的密码只存在于 GitHub Actions Secret 和云端运行环境，项目文档不保存密码。当前已确认的证书 SHA-256 摘要为：

`3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`

历史 alpha25 Production baseline 构建证据：GitHub Actions [Run #70](https://github.com/kuashan/siftalpha-one/actions/runs/34989822426)，其 head 为 Production baseline merge `893229ce26d49a6ea22c79d6e2be85290cb8b0c3`；artifact 为 `siftalpha-w0-70`（ID `10405122896`）。该历史构建完成仓库校验、`testDebugUnitTest`、`assembleDebug`、APK 元数据和签名证据收集。

alpha29 Embedded CPython Runtime prototype 构建证据：GitHub Actions [Run #80](https://github.com/kuashan/siftalpha-one/actions/runs/35060381162)，artifact `siftalpha-w0-80`（ID `10432576095`），artifact digest `sha256:94cf4d1ead49e8500f9b2467765a983b8e0164be354c1e59083670763945eaba`，APK SHA-256 `2a7b7434817ffec53863ad9fd16745bdfef101677c5470a81efdab78aa269cc6`。Run #80 的 preparation、validators、JVM unit tests、CMake/native build、APK assemble、signing verification 和 evidence collection 已完成；alpha29 真实设备结果见 [ARCHITECTURE_M_R_X.md](ARCHITECTURE_M_R_X.md)、[DEV_LOG.md](DEV_LOG.md) 和 [TEST_MATRIX.md](TEST_MATRIX.md)。

本轮架构文档变更不修改 Production Code、Embedded CPython behavior、STOP behavior、workflow 或版本号。

## 8. 历史 alpha16 验收计划（已过时，仅保留记录）

本节以及后续带“历史记录”标题的内容保留当时真实版本、术语和审计结论。尤其是历史记录中的旧 “SiftAlpha X” 表述不回写为当前架构定义；当前定义以本文件第 2 节和 [ARCHITECTURE_M_R_X.md](ARCHITECTURE_M_R_X.md) 为准。

历史配置语义实现（alpha16 基线，仅保留记录，不是当前开发计划）：

- 实现提交：[0873f23](https://github.com/kuashan/siftalpha-one/commit/0873f23e4626757b4cf2bd0681adc24e98492ecb)
- 云端构建：[Run #30](https://github.com/kuashan/siftalpha-one/actions/runs/34885104600)，成功
- 版本：0.8.0-alpha16 / versionCode 92
- 应用修复提交：[5e2d739](https://github.com/kuashan/siftalpha-one/commit/5e2d7395acef37ab7965e517918c02fef19c7cb6)
- Run #35：[Actions](https://github.com/kuashan/siftalpha-one/actions/runs/34911589787)，成功
- Release：[w2-test-8a91895](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-8a91895)
- APK：[直接下载](https://github.com/kuashan/siftalpha-one/releases/download/w2-test-8a91895/app-debug.apk)
- APK SHA-256：e1ecfd0c62315c1d940ab0f3d2c21a0d466bbb11fdbc9f2214c6061c84d26ba7
- 稳定测试签名证书摘要：3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928
- 稳定测试签名证书摘要：3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928

历史 alpha16 下一步按优先级（已过时，仅保留记录）：

1. 直接覆盖安装 alpha16，不卸载 alpha15，并确认本地数据保留。
2. 用两个真实脚本验证建议配置可进入输入框、保存后刷新状态，且不阻止运行。
3. 验证配置摘要的“查看配置”入口和配置列表能够显示检测来源、文件/行号证据。
4. 验证运行时明确缺失项会在下一次启动前变成 REQUIRED。
5. 回归 STOP → START、Chrome 返回、配置保存和覆盖安装。
6. 继续完善当时规划的单项目工作区和完整任务闭环。

## 9. 记录维护约定

- 开发行为写入 [DEV_LOG.md](DEV_LOG.md)。
- 测试状态写入 [TEST_MATRIX.md](TEST_MATRIX.md)。
- 任何“通过”必须注明是云端通过、真机通过，还是用户确认通过。
- 不把旧仓库、旧分支、旧版本号重新当作当前基线。


## 10. Runtime/Web 展示状态分离（历史实现记录）

Runtime 生命周期与 Web 可用性是两个独立维度：

- `RuntimePresentationState` 只表达进程生命周期：STARTING、RUNNING、STOPPED/终止状态。
- `RuntimeWebUiStatus` 独立表达 Web：AVAILABLE、DETECTING、UNAVAILABLE、WAITING 或 AUTO_DETECT。
- 日志中发现的 URL 只是 candidate URL，不能单独把项目判定为 Web，也不能让 Web 未就绪覆盖 Runtime RUNNING。
- Browser 仍然只在真实端点可达且 URL 已验证时开放。
- 本轮不改变 START/PREPARE/STOP 执行流程、Configuration 系统或 Web 探测执行器。

本轮版本为 `0.8.0-alpha18 / versionCode 94`，用于覆盖安装。GitHub Actions Run #44 已成功完成 `testDebugUnitTest assembleDebug`，APK SHA-256 为 `3e61e76b1c80ff11dba53d9afb135c9759cc742fe0b489f704b22d550629f922`；[Run #44 artifact ZIP](https://github.com/kuashan/siftalpha-one/actions/runs/34918070668/artifacts/10377191664) 可下载。直接 APK：[下载 alpha18 测试 APK](https://github.com/kuashan/siftalpha-one/releases/download/w2-test-febcabb/app-debug.apk)；Release：[w2-test-febcabb](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-febcabb)。Run #45：[Actions](https://github.com/kuashan/siftalpha-one/actions/runs/34918441426) 已成功，真机结果待用户验收。


## 11. RuntimeLifecycleStore SharedPreferences Migration Fix（历史实现记录）

RuntimeLifecycleStore 读取历史 SharedPreferences 时必须兼容旧字符串布尔值和当前 Boolean 值。读取逻辑通过安全类型解析和迁移处理，异常类型回退到默认状态；新写入使用按项目、按字段区分的键。该修复只涉及生命周期恢复数据的持久化读取，不改变 Runtime 执行、START/PREPARE/STOP 或 Configuration。

候选云端验证 Run #1 已成功完成单元测试和 APK 组装；[查看候选 Run #1](https://github.com/kuashan/siftalpha-one/actions/runs/34922851615)。正式 main 分支 Run #47 已完成单元测试、APK 组装、稳定签名和发布；[查看 Run #47](https://github.com/kuashan/siftalpha-one/actions/runs/34923173498)。APK：[直接下载](https://github.com/kuashan/siftalpha-one/releases/download/w2-test-4e899c6/app-debug.apk)，SHA-256 为 `e7ebfdc81fa770ef020c4527a2069103b1fdcae287fdd1af9c4291307ef2496a`；该条为历史记录。

## 12. 合并后正式基线与产品能力审计（历史记录）

本节记录以 Production baseline merge `893229ce26d49a6ea22c79d6e2be85290cb8b0c3` 为依据的历史审计结果；原审计使用的阶段标签只作为历史记录保留，不再作为当前路线、阶段状态或完成度门槛。Roadmap/docs base main 为 `38fb60af8e4c7a4b09eafb1cad0e305ae56fc353`。

### 12.1 证据分类

- **unit-test verified**：当前源码包含 314 个 JUnit `@Test` 方法；覆盖 Runtime Identity、配置 preflight/editor、ActionPolicy、生命周期恢复、Python/Node adapter、Web URL/端点/发现和失败诊断。
- **GitHub Actions verified**：Run #70（ID `34989822426`）成功；仓库 validators、`testDebugUnitTest`、`assembleDebug`、APK metadata、`apksigner` 和稳定签名证据收集均成功。artifact `siftalpha-w0-70`（ID `10405122896`）未过期。
- **real-device verified**：alpha25 已完成真实 Android 设备 smoke；Test 04B 明确 loopback Web discovery 成功，04C 拒绝裸 `PORT`/模糊端口和 external URL，04D 验证 Start → Browser → Logs/仍 RUNNING → Stop → Restart。
- **user-confirmed**：上述 04B、04C、04D PASS 由用户提供并确认；它们不替代尚未覆盖的导入、配置全流程、Node 和 App 重启恢复测试。

### 12.2 已进入当前产品基线的能力

- 修复 Runtime Identity wiring，使 START、STATUS、LOGS、STOP、恢复和 Web discovery 共享 project/runtime identity；Android 真机已进入 `FULL_IDENTITY`，guest root 为 `ALIVE`。
- 保持 project-scoped PID/PGID 与 socket inode ownership discovery。Android/Termux/PRoot 诊断确认 `/proc/<pid>/fd` 能发现 socket inode，但 `/proc/net/tcp{,6}` 及 per-PID net 表不可用；没有引入全端口扫描、全局进程扫描或不可信端口猜测。
- 将 `RuntimeWebLogDiscoveryShell` 接入 Python 当前 runtime log 的 bounded fallback。procfs candidate 优先；只有 procfs 没有可信结果时才读取当前 runtime log，并且仍交给 Android `RuntimeWebEndpointProbe` 做最终可达性验证。
- 完成 Web Discovery Contract：candidate 与 reachable 分离；loopback URL 才能成为候选，Browser 必须满足当前 Runtime RUNNING、URL 安全校验和真实 endpoint probe。
- 完成 alpha25 cleanup，删除 per-PID TCP/TCP6 investigation instrumentation，保留 Web 状态协议和少量基础 procfs diagnostics；PR #1 已解决冲突并合并，merge commit 为 `893229ce26d49a6ea22c79d6e2be85290cb8b0c3`。

### 12.3 当前审计缺口

- Project identity 已通过 SAF `documentId` 建立可信边界，Runtime Identity 也已经通过 `FULL_IDENTITY` 真机结果验证；尚未完全闭合的是 `Project Identity → Runtime Identity / Runtime Generation → Runtime Lifecycle → Web Candidate Ownership → Recovery` ownership chain。
- `RuntimeWebStateStore` 当前主要持久化 `candidateUrl`、`framework` 和 `detectedAtEpochMs`，并主要按 `projectKey` 存储；如果 Runtime A 停止后 Runtime B 启动，旧 candidate 仍可能存在。Endpoint Probe 只能证明端点可达，不能单独证明端点属于当前 Runtime；若其他本地服务复用端口，可能出现可达但 ownership 错误的结果。
- `V04Activity` 仍同时拥有刷新、UI 组装、动作二次守卫、Runtime orchestration、配置 orchestration、Web orchestration、恢复和结果归并。Coordinator 方向仍然有价值，但应作为逐步实现 Runtime Session Ownership Boundary 的结构手段，而不是为了“架构漂亮”进行一次性大规模重构。
- Node 主运行时仅支持受限的 npm/managed Node 与显式 `run` 或 `scripts.start` 合约；Node/Vite 主要有 JVM 结构测试，缺少与 Python 同等的真实 Android 运行验收。
- 导入、配置编辑、Output、App 重启后 recovery 和清理的当前 alpha25 真机覆盖不完整；现有测试主要是 JVM/生成脚本测试。
- `RuntimeWebPortDiscovery` 仍输出紧凑的 `SIFTALPHA_WEB_DEBUG_*` PID/FD/TCP 可访问性摘要。它不改变发现结果，但属于默认日志噪声，是否保留应在后续 cleanup 中明确决定。

### 12.4 当前产品 backlog 与后续优先级

**P0 — Runtime Session Ownership Boundary（产品硬化 backlog）**

目标是建立最小的 project-scoped runtime session ownership 模型，把以下事实绑定在一起：project identity、runtime identity/runtime generation、lifecycle state、current runtime candidate URL 和 recovery state。P0 的重点是 correctness 与 ownership，不是单纯缩小 `V04Activity`。

最低合同：

- `START` 创建新的 runtime session/generation；旧 session 的 candidate 不得自动继承。
- `RUNNING` 期间的 Web candidate 必须属于当前 runtime session。
- `LOGS` 只能读取当前 runtime 对应日志。
- `STATUS` 恢复必须验证当前 runtime identity。
- `STOP` 结束当前 runtime session，并 invalidated/cleared 当前 candidate。
- `CLEAN` 使当前项目相关 runtime candidate/session state 失效。
- `RESTART` 创建新的 runtime session/generation，不继承旧 runtime candidate。
- `RECOVERY` 只能恢复仍匹配当前 runtime identity 的 session 信息；相同 `projectKey` 不能单独使旧 candidate 继续生效。

Coordinator 是渐进式实现手段：第一阶段建立最小 `RuntimeSession`/`RuntimeSessionState`/`RuntimeGeneration` 等价 abstraction，不要求一次性重写 `V04Activity`；第二阶段逐步迁移 refresh、dispatch、recovery 和 Web invalidation；第三阶段再减少 Activity orchestration 职责。

- **P1 — 产品基线硬化与验证**：把 Web candidate 绑定到 runtime identity/generation；在 STOP/CLEAN 明确清理 candidate；完成 App restart recovery、Import、Configuration 和 Node Runtime 真机验证；按需要逐步抽取 coordinator。
- **P2 — 后续体验与基础设施增强（未来 Roadmap 待重新定义）**：Workspace Compose 化、更完整结构化 Failure UI、Node 能力扩展、进一步 Activity cleanup、默认 debug 日志 cleanup 和签名流程进一步加固。

如果当前只允许再做一个开发任务，优先完成 **Runtime Session Ownership Boundary**。它直接补齐当前最重要的 correctness/ownership 缺口，并为后续 Web candidate 清理、Recovery、Restart 和 coordinator 抽取提供统一边界；`V04Activity` 过大是后续结构表现，不是第一理由。

当前结论：当前产品基线已经形成，但 `Project → Runtime Session → Web Candidate` 的 ownership chain 尚未完整闭合；这属于普通的产品 backlog / hardening，不再使用阶段完成或阻塞语义。未来 Roadmap 尚待重新定义。
