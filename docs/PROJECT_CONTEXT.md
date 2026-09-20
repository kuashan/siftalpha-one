# SiftAlpha Studio 项目上下文

最后更新：2026-09-17（alpha43 Baseline Closure）
当前仓库：[kuashan/siftalpha-one](https://github.com/kuashan/siftalpha-one)  
当前文档/验收分支：`codex/siftalpha-x-embedded-cpython-spike`  
Current branch/document HEAD：`860645af9745ebbb978dad6d177359f5fddb23f1`  
alpha43 production source baseline：`66f9153e57547c4d8b6e50956b48ddf86b9dc656`  
`main` 保持历史 production baseline，本轮未修改。  
alpha30 source / real-device evidence remains historical; current branch continues from the alpha31 version-identity baseline。  
M / R / X 架构与 Runtime prototype 工作分支：`codex/siftalpha-x-embedded-cpython-spike`  
当前版本：0.8.0-alpha43 / versionCode 119（Automatic Project Observation + Contextual Status Guidance；真实 Android 真机验收 PASS）  
最近 CI：GitHub Actions Run #122 / Run ID `35242617391` / conclusion `success`；Artifact：`siftalpha-w0-122`；APK SHA-256：`6b70fc222cc8e27124daf2a5a910abc1174380b257c5549959abb279d25def6a`。  
alpha43 final production-source CI evidence：Run #121 / Run ID `35226167054` / conclusion `success`；Artifact：`siftalpha-w0-121`；APK SHA-256：`6b70fc222cc8e27124daf2a5a910abc1174380b257c5549959abb279d25def6a`。  
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

当前 Embedded CPython alpha32 implementation 仍位于隔离的 `siftalphax` implementation path，保持 process-scoped CPython initialization、per-session worker attach/detach、single active session 和已验证的 cooperative STOP。alpha32 增加了由 M 显式触发的最小 SAF project staging bridge：M 解析明确入口，stager 复制到 app-private execution root，R 继续负责 Session/generation/native execution。该桥接仍是实验性 source/CI readiness，不等于 R production 或 X acceptance。

### 2.6 M ↔ R Interface 原则

M 向 R 表达结构化 Management Intent，例如 `prepare`、`start`、`stop`、`status`、`logs`、`restart`。R 向 M 返回 Runtime Facts，例如 runtime/session identity、generation、lifecycle state、runtime/stop phase、stop result、exit code、stdout/stderr、structured failure、capability 和 availability。

M 不应依赖 CPython/JNI internals；R 不应依赖 Activity、Compose 或 Browser UI。Web Discovery、Endpoint Probe、Browser policy 和 UI 属于 M；R 可以提供 Runtime Identity、Session Identity、process facts 和 endpoint facts。alpha32 只实现一条最小、显式的 M → R project-script bridge，不建立通用 Provider Framework，也不把 R 伪装成 Termux shell host。

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
- Automatic Project Observation：前台 Activity 生命周期内以低频 STATUS 为主，终态自动读取一次最终 LOGS；后台暂停观察但不停止底层 Runtime。
- Contextual Status Guidance：主要状态说明根据真实 Lifecycle、Web、Rich Result 和失败事实选择，避免 READY_TO_RUN 覆盖终态结果语义。
- Rich Result automatic finalization：终态自动发现 Rich Result，并通过 Unified Open 按 `WEB > RICH_RESULT > NONE` 选择唯一“打开”入口；Viewer 提供明确返回。
- verified Web delayed readiness：`RUNNING` 可以先处于 Web unavailable；只有 Candidate 加 Endpoint Probe 验证真实可达后才开放 Web/Open。
- foreground-only observation semantics：Activity 进入后台时只暂停自动观察；重新进入 App 后恢复观察，底层长期 Runtime 不因离开前台而停止。

后文列出的 Future Direction 是当前推荐开发顺序，不是已经完成的能力；本文件不创建旧式 W6/W7 阶段。

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


## 13. alpha30 R Project Script Execution Boundary

alpha30 把 Embedded CPython prototype 从 fixed source fixtures 推进到 file-backed project-script execution boundary。当前实验路径使用 APK 内置、可审查的纯 Python project fixtures，并为每个 session 在 app-private siftalphax/projects/<sessionId> 下建立独立 staging root。

R 接收显式的 Project Execution Specification：project identity、execution root、relative entrypoint、relative working directory、CPython runtime kind、session identity 和 generation。Project identity 与 Runtime Session identity 分离；R 不扫描文件、不猜测 entrypoint，也不把 SAF URI 当作普通 native path。当前不实现任意 SAF imported project 的 M ↔ R production integration。

native R 重复执行 root containment、canonical path、regular-file、directory 和 symlink 校验。执行期间 cwd、TMPDIR、sys.path、sys.argv、temporary __main__ module、stdout/stderr capture 具有 session-scoped 生命周期，并在 terminal cleanup 中恢复。当前 file-backed Python 语义覆盖真实 __name__、__file__、sys.argv[0]、project-local sibling import、真实 traceback filename、SystemExit mapping、Python exception mapping、cooperative STOP 和 post-STOP re-entry。

这不是 arbitrary Python project support、dependency installation、venv、native wheel、C extension interruption、universal hard-stop、并发 Runtime 或生产级 sandbox。在真实设备验收前，alpha30 曾处于 source/CI ready；随后 Run #90 已对当前测试范围完成真实设备验收，详细证据见 [DEV_LOG.md](DEV_LOG.md) 和 [TEST_MATRIX.md](TEST_MATRIX.md)。


### 13.1 alpha30 Real-Device Acceptance Evidence

Run #90（ID `35083943639`）对应源码 HEAD `0e2b069d93a0a8cd87df4f57f0e97da1cf918643`、版本 `106 / 0.8.0-alpha30` 和 artifact `siftalpha-w0-90`（ID `10441785815`，digest `sha256:afc012248071e5b43884aa8985ee07e94a4e23e1f7ecf4a5c4a960f01ff228da`；APK SHA-256 `d9bdeac5a0df867cc52b5b71226e90a560a831a3097e9341af0f2e1898e2a547`）。用户在真实 Android 设备上确认了连续链：

`generation 1 PROJECT A → SUCCEEDED`
→ `generation 2 PROJECT B → intentional FAILED/exitCode 1`
→ `generation 3 PROJECT C → RUNNING`
→ `cooperative STOP → STOPPED/exitCode 130`
→ `generation 4 PROJECT A → new Session / SUCCEEDED`。

设备确认 CPython `3.14.7`、Android `aarch64`、`sys.platform=android`，且该测试路径 `TERMUX=NOT_USED`、`PROOT=NOT_USED`。Test A 的 app-private path validation 错误未复现；Test B 的 stdout/stderr 和真实 `main.py` traceback、Test C 的 cooperative STOP 以及 STOP 后 re-entry 均 PASS。

因此：**SiftAlpha R Embedded CPython alpha30 Project Script Execution Boundary real-device acceptance = PASS（仅针对上述 app-private、file-backed、pure-Python fixture 范围）**。这不表示 R 或 X 完成，也不证明 arbitrary external/SAF projects、dependency installation、arbitrary third-party/native packages、blocking native/syscall hard-stop、concurrent Sessions、process-death recovery 或 production M ↔ R / Web / Browser integration。

## 14. alpha32 M → R Embedded CPython First Integration

alpha32 在既有 alpha30 file-backed R boundary 之上，建立第一条真实的 M → R 接线：

`M-managed SAF Project → bounded app-private staging → explicit project execution input → R Session → Embedded CPython → structured Snapshot → M RuntimeState/output/STOP`。

M 使用 SAF project directory 的 documentId 作为 Project Identity；R 自己生成 sessionId 和 generation。Project、Session、Generation 保持三个不同事实。M 只在项目被确定为 Python 且入口已经由 metadata 或确定性规则解析成功时显示显式的“使用内置 R 运行”入口；Termux 的既有 Run/Prepare/STOP 路径仍是默认路径，没有被静默切换。

`EmbeddedPythonProjectStager` 只负责 SAF → app-private `files/siftalphax/projects/session-<uuid>` 的 bounded copy，保留相对目录，限制节点/文件数和单文件/总大小；复制失败会清理不完整根目录，不修改或删除 SAF source。R 的现有 `EmbeddedPythonSession`、CPython lifecycle、stdout/stderr、structured snapshot、cooperative STOP 和 re-entry 路径被复用。Embedded R 不经过 `RuntimeCommand`、Termux、RUN_COMMAND、PRoot 或 Ubuntu。

当前 alpha32 是 source/CI readiness boundary，尚未声称任意 Python 项目、依赖安装、SAF arbitrary integration、Web/Browser integration、并发、process-death recovery 或 universal hard-stop 已完成。


## alpha42 — Rich Result Presentation + Unified Open

alpha42 在 M 的安全结果回调中使用已经完成 Secret Redaction 的输出，先移除 ANSI/OSC 终端控制序列，再按通用的强格式
`[+] Label: http(s)://...`
识别有限数量的 Link List Rich Result。结果只保存 label 与经过 HTTP/HTTPS 白名单验证的 URL；Raw Log 仍由既有 ProjectOutputPanelController 完整保留，但作为次级诊断入口。

项目卡片的主要呈现动作统一为“打开”。纯策略路由为 `WEB > RICH_RESULT > NONE`：只有现有 Web endpoint probe 已确认可用时才走原有 Browser 安全链；否则有 Rich Result 时进入原生 Rich Result Viewer；Raw Log 本身不会成为打开目标。一次性 CLI 面向 App 内 Rich Result，长期自动化和未来自动量化面向 Live Web Dashboard，Raw Log 只作为诊断。

本轮结果缓存只覆盖同一 V04Activity 实例的生命周期；接受新的 START 会清除旧结果，空的 STATUS/LOGS 不会清除当前结果，后续发现的新链接列表可以更新结果。alpha42 不引入数据库、WebView、量化能力、依赖安装或新的 Runtime/Web 架构；真机验收仍需分别验证 Sherlock Rich Result 和既有 Web Dashboard。


## alpha43 — Automatic Project Observation + Contextual Status Guidance

alpha43 adds Activity-lifetime automatic observation for External Provider runs. After an accepted START, SiftAlpha issues low-frequency, project-scoped STATUS checks while the Activity is foregrounded. A terminal EXITED_SUCCESS or EXITED_ERROR state triggers one final LOGS read for Rich Result detection and diagnostics, then observation stops. Observation pauses with the Activity and resumes/reconciles on return; it does not stop Termux or the project runtime.

The card exposes state-derived guidance only: it does not ask users to classify a project as a one-shot task or live service. Verified Web availability guides the existing Unified Open path to the browser; a Rich Result guides it to the native viewer. Raw logs and manual STATUS / LOGS remain secondary diagnostic and recovery controls. Embedded R keeps its existing snapshot polling and lifecycle ownership.

## alpha43 Baseline Closure — Real-Device Acceptance

alpha43 的真实 Android 真机验收已经完成并通过。当前基线是 `119 / 0.8.0-alpha43`，source HEAD 为 `66f9153e57547c4d8b6e50956b48ddf86b9dc656`。

Sherlock 一次性流程只需用户点击一次运行：`START → RUNNING → Automatic Observation → EXITED_SUCCESS → final LOGS → Rich Result → Unified Open`。真实项目卡显示“状态：已正常结束”和“状态说明：运行结果已就绪，可以打开查看”，Rich Result 可打开；本次真实搜索产生 16 项结果。Viewer 有明确可见的“← 返回”按钮，返回项目卡后状态正确。

situation-monitor 长期 Web 项目验证了延迟 ready 语义：项目可以先处于 RUNNING 且 Web 暂不可用，待 Web Server 开始监听并由 Endpoint Probe 验证真实可达后，才自动显示 Web 可打开并启用 Unified Open。外部浏览器访问 `/api/articles`、`/api/globe-data`、`/api/stats` 均真实返回 HTTP 200；关闭浏览器、离开 App 或重新进入不会停止长期 Runtime。

真实验收也确认：普通用户不需要手动点击 STATUS 或 LOGS；Web discovery 的 LOGS 探测仍然有界（每 3 次 STATUS 一次、最多 3 次），终态只读取一次 final LOGS，不是后台无限刷新。只有明确 STOP 才代表 `STOPPED_BY_USER`；自然完成保持 `EXITED_SUCCESS`。

**alpha43 real-device acceptance = PASS**

本节封存的是当前 M-facing 观察、交互与呈现基线，不表示 Embedded R、完整 M/R/X 或生产级 M ↔ R 已完成。

## Future Direction — 推荐技术顺序（不是已完成能力）

下一阶段先进行环境与依赖模型审计；当前推荐顺序为：

1. R Environment Model + Dependency Model
2. Embedded R real-project compatibility expansion
3. Embedded R integration with Automatic Observation、Web Discovery、Rich Result、Unified Open
4. Runtime Session Isolation + Recovery hardening
5. Result persistence
6. Developer Mode separation + full normal-user UI redesign

以上是研究与开发顺序，不是能力完成清单。

## Developer Mode — 长期产品方向（本轮不实现）

未来 SiftAlpha UI 计划分层：

- 普通模式面向普通用户，隐藏 Runtime 工程细节。
- Developer Mode 面向开发、诊断和维护，显示 `STATUS`、`LOGS`、Raw Log、Runtime details、PID / PGID、Web Discovery diagnostics、Environment details、failure diagnostics 和 maintenance controls。

Developer Mode 只能改变 Presentation、visible controls 和 diagnostic visibility，不能拥有另一套 Runtime 状态。普通模式与 Developer Mode 必须共享同一个 Project Identity、Runtime State、Lifecycle、Result 和 Web State。本轮不实现 Developer Mode。

## Next Formal Research Direction — R Environment & Dependency Model Architecture Audit

alpha43 文档封存后的下一项正式工作是 `R Environment & Dependency Model Architecture Audit`。这是先审计、后实现的研究任务，本轮不实现任何 Embedded R 能力。

审计范围包括：CPython environment、`sys.path`、`site-packages`、per-project isolation、dependency declaration discovery、`requirements.txt`、`pyproject.toml`、pip feasibility、wheel compatibility、pure-Python packages、native package boundary、cache、upgrade、cleanup、failure model，以及 M ↔ R contract。

## Alpha43 仍未完成或未证明的 Embedded R 边界

alpha43 真实设备通过的是当前 External Runtime Provider 下的 M-facing 观察、Web、Rich Result 与呈现链路，不扩大 Embedded R 的能力边界。以下能力仍未完成或未证明：

- pip
- `requirements.txt` / `pyproject.toml` dependency installation
- venv
- third-party dependencies
- native wheels
- arbitrary C extensions
- blocking native/syscall hard-stop
- arbitrary SAF project compatibility
- concurrent sessions
- full process-death recovery
- Embedded R Web/Browser integration
- production-grade M ↔ R integration
- production sandboxing

Production external runtime 仍主要依赖 Termux + PRoot + Ubuntu + Python 作为 External Runtime Provider。Embedded CPython 仍只是 R 当前第一个实现方向，不是完整 R。


## alpha43-r22 Fast Web Detection direction (2026-09-19)

- r21 real-device acceptance confirmed Internal Alpine background survival and stable Web presentation across external-browser use.
- r22 is a performance-only follow-up for Web detection. It must not change Rich Result parsing, lifecycle, viewer behavior, External execution semantics, project-scoped STOP, Worker freeze, or OCI/project source.
- Internal Alpine heavy PID/socket/procfs listener observation runs off the Activity main thread and stops once the current execution has a URL candidate.
- Endpoint verification uses a bounded startup retry burst (150/300/600 ms after early misses), then returns to the existing 2-second health cadence.
- External Web discovery temporarily uses a 500 ms observation cadence only while its bounded LOGS discovery budget remains useful; it returns to 2 seconds after endpoint verification or budget exhaustion.
- Unified Open remains presentation-neutral: a valid Rich Result stays usable while Web is only DETECTING; verified Web keeps the existing priority when both presentation targets exist.


## alpha43-r23 Web Hint architecture (2026-09-19)

- Web hints are an acceleration layer, not a new Web truth source.
- Ordered evidence preference: detected/configured project port, framework default, bounded common ports, then existing complete listener discovery.
- Every hinted port must cross the existing current-project ownership boundary before becoming a Web candidate.
- Internal ownership proof is current Alpine session/generation + project PID tree + socket inode.
- External ownership proof remains managed PID/PGID and Runtime Identity/PRoot guest PID scope.
- Android loopback reachability is the second gate after ownership; only verified Runtime candidates can establish Web presentation identity.
- Rich Result remains an independent presentation capability. Hint discovery must not alter Rich Result parsing, lifecycle or viewer semantics.


## alpha43-r24 Web Runtime Continuity architecture (2026-09-19)

- Current Execution Web State and Project Learned Endpoint are separate stores with different lifetimes.
- Current execution candidate/verified state is cleared at execution boundaries and remains protected by session/generation and Runtime ownership evidence.
- Project Learned Endpoint is cross-run acceleration only. It stores the last PID/socket-owned + Android-verified loopback endpoint and is never sufficient to establish current Web availability by itself.
- Hint priority: learned endpoint > project-detected/configured port > framework default > bounded common ports > complete existing listener discovery.
- Internal Alpine discovery has a bounded startup miss burst followed by low-frequency discovery so a listener that binds after the first observation cannot strand the UI in DETECTING.
- Foreground Activity lifecycle no longer invalidates user-visible Web identity. Fresh reachability is revalidated silently and repeated failures are required before downgrade.
- Internal Runtime foreground-service leases now cover both Internal Alpine and Embedded CPython sessions. The service remains a process-liveness lease, not a Worker/executor/supervisor.
- Rich Result remains independent; it has no network port and is not persisted as a new run's result.


## alpha43-r28 current test baseline

This build intentionally uses r24 behavior as the product baseline and adds only Internal Runtime Foreground Service + PARTIAL_WAKE_LOCK protection. The later unified deployment start-contract integration, four-stage Web presentation, and HTTP readiness probe are not part of this test baseline.


## alpha43-r29 background execution experiment

r24 remains the known usable functional baseline. r29 does not alter Web behavior. It tests only the first-layer background hypothesis: Android foreground-service state and PARTIAL_WAKE_LOCK must be established before Internal Runtime launch, and the resulting service/process state must be observable through explicit diagnostics. If real-device OCI still stops progressing while FGS heartbeat, wake lock and Runtime PID remain healthy, the next investigation should distinguish Runtime scheduling from network/application progress before any larger ownership migration.


## alpha43-r30 current test line

r24 remains the known usable baseline. r29 added the first-layer foreground-ready launch ordering and diagnostics. r30 builds only on r29: it adds persisted Runtime Center inspection caches to avoid repeated SAF scans and repairs the Internal Runtime log-URL wiring so a current-session SIFTALPHA_WEB_URL reaches the existing endpoint verification path before snapshot presentation can early-return. Runtime execution facts remain live/action-time reads, and the four-stage Web experiment remains removed.

## alpha43-r31 Internal Runtime Foreground Ownership (2026-09-20)

r31 is the second-layer background-continuity experiment. It does not add a Worker or a second execution system. Internal Alpine still uses the existing R implementation, but Android lifecycle ownership changes: InternalRuntimeForegroundService now launches and holds the child Process, stdout/stderr file handles, a service-scoped ownership registry, the project-scoped STOP route, and the process-completion monitor. InternalAlpineSession keeps session/generation/status/log presentation state but no longer owns the child process monitor.

The ownership key is session-specific and project-specific. STOP must match both identities before the service invokes the existing Internal Alpine process-tree termination path, so a STOP for one project cannot terminate sibling sessions. Embedded CPython remains in-process and continues using the existing foreground lease rather than being wrapped in a fake child-process owner.

r31 deliberately does not add WifiLock or broader network-policy changes. The real-device success criterion is application progress, not mere process survival: OCI must keep producing CATCHER_HEARTBEAT / LAUNCH_ATTEMPT activity for at least 10-15 minutes while SiftAlpha is backgrounded. If the service-owned process remains alive and CPU ticks continue while OCI network work stalls, the next isolated layer is Network Background Continuity.

## alpha43-r32 Runtime Center UI stability (2026-09-20)

r32 is a presentation/responsiveness correction on top of r31, not a new Runtime architecture. The r31 foreground-service ownership model remains unchanged. Fast Runtime observation can continue in the background, while expensive snapshot/log reads no longer run on Android's main thread and stdout growth no longer implies rebuilding the whole project card.

Internal live output is an in-place surface. Its update cadence is intentionally slower than lifecycle observation, manual actions bypass the throttle, and structural Runtime transitions still refresh control state immediately. The output controller retains per-project nested scroll state and masks the transient zero-scroll callback created when a TextView replaces a large log body.

STOP remains project-scoped. r32 only changes dispatch threading so the UI does not wait for snapshot lookup or process-tree termination. It does not widen STOP scope, add Worker/supervisor/runtime gate, alter External Runtime behavior, restore the removed four-stage Web state, or add network/Wifi locks.

## alpha43-r33 Background Web Continuity telemetry (2026-09-20)

The current unresolved background issue is same-device localhost continuity, not Wi-Fi reachability. The external browser runs on the same phone and accesses the Internal Runtime through 127.0.0.1/localhost. r33 therefore avoids WifiLock and does not assume a network-radio sleep problem.

The foreground service now owns a diagnostic continuity sampler in addition to the r31 Process lifecycle ownership. Every 15 seconds it records the service and child Runtime process scheduling facts, CPU progress, stdout progress and loopback listener reachability into a session-private bounded history file. This sampler is observation only and is not a Worker/supervisor or keepalive mechanism.

The purpose of r33 is to establish a time series while the Activity is absent. A single post-return PID-alive snapshot cannot distinguish a frozen child from a running process whose HTTP listener stalled. The persisted sample history can. Once real-device evidence identifies which dimension stops first, the next repair must remain isolated to that layer.

## alpha43-r34 Internal Web late-listener recovery (2026-09-20)

A direct r24-to-r33 audit showed that the core Internal Alpine Web discovery machinery was not removed or replaced: PID/socket discovery, bounded late-listener retry cadence, Web availability tracking, state persistence and endpoint verification remain the r24 design. The material gap exposed by situation-monitor is the fallback stream boundary. Internal Runtime snapshots keep stdout and stderr separate, while the Web log fallback inspected only stdout. Flask normally announces its bound localhost URL on stderr after situation-monitor finishes its synchronous startup feed fetch.

r34 adds a stream-neutral fallback without weakening Web truth. Both Runtime streams may contribute candidate evidence, but ordinary log URLs remain allowed only for projects already classified as Web-capable, and every candidate still crosses the existing Android loopback Endpoint Probe before browser availability is presented. The r24 Web state model, r31 foreground ownership, r32 UI stability and r33 background telemetry stay intact.

## Current Known Good Functional Baseline: alpha43-r34

As of 2026-09-20, the project-level Known Good Functional Baseline is `0.8.0-alpha43-r34` / versionCode `160`, commit `466e33d5bb0f18bcc1bfa537f5d7ebe5aef93f1c`, tree `6ec24ca0a5a9cca585946306da61fc36741e2aa4`.

A dedicated frozen reference branch, `baseline/alpha43-r34-known-good`, points directly at that accepted source commit. It is not a development branch and must not move. r24 remains the historical Web-behavior reference, while r34 is now the current product-level baseline for future regression comparison.

The accepted r34 line includes the r31 Internal Runtime foreground ownership model, r32 Runtime Center responsiveness/stable log presentation, r33 background-continuity telemetry, and r34 stdout+stderr late Web candidate fallback while preserving the existing Endpoint Probe truth gate. Same-device localhost background continuity has been demonstrated when SiftAlpha is granted unrestricted battery/background execution by the device.

The baseline is replaced only by a later version that completes cloud verification and receives explicit real-device acceptance.

## alpha43-r35 CLI Launch Configuration

r35 extends the product configuration model with a separate Launch Configuration layer. Environment variables, secrets and config files remain environment configuration; Python CLI argv is not converted into environment variables.

The first high-confidence static contract recognizes conventional literal `argparse` declarations for scalar required positionals and required valued options. Runtime output remains the fallback truth source: argparse's explicit missing-argument diagnostic can teach SiftAlpha project-scoped launch requirements after a failed first run.

The existing `PythonLaunchInvocation` becomes the common argv contract for supported Python-file launches across External Runtime and Internal Runtime. Internal Alpine receives safely quoted distinct arguments, and Embedded CPython now reconstructs `sys.argv` natively for every session. r34 remains frozen as the current Known Good Functional Baseline for regression comparison.

## alpha43-r36 Click/Typer CLI discovery

r35 established the common argv execution contract but its first discovery implementation only modeled argparse. The real `easy_tdx-main` acceptance case uses Click/Typer-style CLI behavior, proven by the runtime's `Usage: ... [OPTIONS] MARKET CODE` and `Missing argument 'MARKET'` output.

r36 broadens only CLI requirement discovery: literal Click required arguments, Typer ellipsis arguments, and Click/Typer runtime diagnostics are now recognized. Likely launcher files are prioritized within the bounded static scan, and the configuration profile cache moves to a new namespace so previously cached empty results are invalidated. The r35 argv transport/execution contract remains the execution layer.

## alpha43-r37 entry-bound CLI launch

r37 fixes the remaining separation between CLI requirement discovery and launch-target selection. CLI requirements now carry entrypoint evidence into action-time resolution. A pyproject console script cannot inherit positional arguments discovered from a different Python file. Only an exact evidence match to the resolved fallback entrypoint allows that Python file to supersede a generic console-script candidate.

## alpha43-r38 Local Result Web Host

r38 introduces a second Web concept that is intentionally separate from Project Web.

- Project Web is owned by the running project and discovered/probed by the existing Runtime Web pipeline.
- Result Web is owned by SiftAlpha. It is generated after successful one-shot output, stored in app-private storage, and served from a loopback-only local HTTP host even though the project process has already exited.

The adaptive Result Web contract is output-first with fail-safe presentation. Structured patterns become sections, metric cards, tables and data-backed curves; anything uncertain remains text. Bounded source inspection provides presentation hints only. Source code never supplies fabricated result values.

The in-app Result Web viewer uses an actual localhost URL, with JavaScript/file/content access disabled. Result history is persisted independently of Runtime process lifetime; the local server itself starts on demand and may use a different ephemeral port after app-process restart.

r37 is the real-device-passed CLI execution baseline for this feature work. r34 remains the frozen product Known Good Functional Baseline until a later explicit baseline promotion.



## alpha43-r39 Android system-bar safe insets

r39 is a presentation-only correction discovered during r38 real-device Result Web acceptance. Android 15+ edge-to-edge enforcement placed the legacy View root at the physical window edge, while the shared StudioActivity inset handler previously protected only against the software keyboard. This allowed the Result Web Back / Copy link / Open in browser row to overlap the status bar.

StudioActivity now applies the device-reported system-bar/display-cutout insets on top of each legacy View root's existing padding and reconciles the bottom system inset with the IME using the larger obstruction. The fix is shared so other legacy View surfaces receive the same correct safe-area behavior without hard-coded status-bar dimensions. Compose surfaces retain their existing safeDrawing handling. r38 adaptive Result Web behavior and Runtime semantics are unchanged.


## alpha43-r40 Polyglot prepare dependency ordering

r40 closes a general environment-composition gap for Python-primary projects that also contain build-time Node/Vite frontend components. Some Python packaging backends intentionally include generated frontend assets in the wheel/editable package while keeping those assets out of source control. Such projects require the frontend build to finish before Python package metadata/build hooks run.

SiftAlpha already had a generic supplemental Vite builder, but the composed PREPARE order was Python first and Node second. r40 reverses that dependency edge for the Python+Node composition: detected frontend assets are built first, then the Python environment is installed against the now-complete project tree. No application-specific patch, package name, or fixed frontend path is introduced.

The composed environment command also suppresses child-level global SIFTALPHA_ENV markers and publishes one final project-level environment result. Runtime lifecycle semantics and Web presentation priorities are unchanged.


## alpha43-r41 Declared Web-extra installation

After building a detected Vite frontend, Python preparation checks the project's own pyproject metadata. If the project explicitly declares a web optional-dependency group, SiftAlpha installs the editable project with that declared extra. This makes the packaged frontend and its Python Web-serving dependencies ready in one PREPARE without application-specific dependency injection. Node preparation also clears its shared prepare log at the beginning of each new run.


## alpha43-r42 Native Web Application Launch Discovery

r42 adds an M-layer launch policy for Python-primary projects that package their own Vite Web UI. It sits ahead of the r37 entry-bound CLI fallback but is intentionally much stricter: explicit run metadata wins, Web capability must already be known, pyproject must declare its own web optional dependencies and console script, Vite build evidence must exist, and bounded source inspection must prove a literal serve subcommand with a browser-suppression option.

When this contract is proven on the accepted External Provider route, SiftAlpha launches the project-owned console script with structured argv, keeps browser ownership inside SiftAlpha, and relies on the existing Runtime Identity -> Web Discovery -> Endpoint Probe truth chain before presenting the browser. A failed proof does not alter project behavior; it falls back to the existing CLI launch resolver.


## alpha43-r43 Internal Alpine polyglot Web path

r43 closes the provider split left by r42. A Python-primary project may now use Internal R when its only supplemental Runtime is Node/Vite. The capability is implemented inside Internal Alpine rather than by delegating to Termux: Alpine installs Node/npm on demand, builds detected Vite components from a staged project tree, installs the packaged Python project with its own declared web extra, and can execute the installed Python console script with structured argv.

Embedded CPython remains the narrow Python-file backend. Node/Vite or console-script Web launches are deliberately forced to Internal Alpine. Project-owned Web availability still uses the established Runtime Identity / project PID ownership / candidate discovery / Endpoint Probe truth chain.


## alpha43-r44 Scalable Internal Project Staging

r44 updates the SAF-to-app-private staging boundary for modern source projects. The earlier 512-file / 1024-node limits were appropriate for the first narrow Embedded Python experiments but became an artificial blocker once Internal Alpine gained Python+Node/Vite preparation.

Staging is still explicitly bounded. The system now prunes dependency installations, interpreter caches and tool caches before counting source nodes, and source-build staging also prunes generated dist/build-style outputs that the Internal Alpine pipeline regenerates. Full-project source copies use a larger bounded profile, while entrypoint-oriented copies remain stricter.

The ordinary project-browser tree limits are not reused as execution limits. Staging has its own bounded traversal contract, preserving the no-silent-truncation guarantee. Path validation, duplicate rejection, app-private destination ownership, symlink defense and cleanup-on-failure remain part of the same trust boundary.


## Current Known Good Functional Baseline: alpha43-r44

As of 2026-09-20, the project-level Known Good Functional Baseline is now `0.8.0-alpha43-r44` / versionCode `170`, accepted source commit `0285db2c26565e1aa2a47624d2cad8fa9c19e89f`.

The dedicated frozen reference branch `baseline/alpha43-r44-known-good` points directly at that accepted source commit and must not move. The earlier `baseline/alpha43-r34-known-good` remains preserved as a historical reference; r44 supersedes r34 as the current regression baseline.

Cloud verification for the accepted source completed successfully with W0 Build #263, Internal Alpine Probe #51, unit tests, debug APK assembly and stable v2 signing. Real-device acceptance also completed successfully.

The accepted r44 baseline includes the cumulative accepted behavior from the r34 line plus the later CLI/result/Web work through r44. In particular, the baseline now proves a complete generic Internal R path for a modern Python-primary project with a Node/Vite supplemental frontend:

1. SAF project import and bounded source staging;
2. generated/dependency directory pruning with explicit mobile-safe limits;
3. Internal Alpine Node/npm preparation;
4. Vite frontend build;
5. Python environment installation using the project's own declared Web extra when present;
6. high-confidence Native Web Application Launch Discovery;
7. installed Python console-script launch inside Internal Alpine;
8. Runtime Identity / project ownership preservation;
9. Web Discovery followed by Endpoint Probe verification;
10. project-owned Web UI presentation in SiftAlpha without depending on Termux.

The accepted real-device test also confirmed the project-owned Web UI can dynamically refresh its data once the project Web service is running. SiftAlpha Result Web remains a separate fallback for terminal one-shot output and does not replace a verified project-owned Web UI.

Worker remains frozen. STOP remains scoped to the current project. A future baseline promotion requires both successful cloud verification and explicit real-device acceptance.


## Runtime Storage Manager provider split

The Runtime Storage Manager must follow the same dual-provider contract as project execution. Internal R app-private storage is owned and maintained directly by SiftAlpha. External Provider storage remains owned through the Termux/PRoot command bridge.

Termux availability must never gate Internal R storage inspection or project-environment cleanup. Conversely, Internal R cleanup must never delete External Provider data. The same project may have both provider environments at the same time; each provider is independently inspectable and independently cleanable.

Project source in Android shared storage is outside the Runtime Storage Manager deletion boundary. Internal R project cleanup removes only app-private Embedded CPython/Internal Alpine environments. Shared Runtime foundations such as the Internal Alpine rootfs are observable but are not ordinary project cleanup targets.

alpha43-r44-storage1 is the first implementation of this provider-aware storage contract. The r44 known-good baseline remains frozen at 0285db2c26565e1aa2a47624d2cad8fa9c19e89f; this pre-r45 change requires separate cloud and real-device acceptance before it can become a new baseline.


## Current temporary baseline after r44 closure

r44 is closed. The current temporary Known Good Functional Baseline is:

- versionName: 0.8.0-alpha43-r44-storage1
- versionCode: 171
- accepted source commit: 25596e855a7a141089568883b5d38e023982b6d3
- frozen reference branch: baseline/alpha43-r44-final-known-good

This baseline includes the previously accepted r44 Internal R modern-project Web path plus the provider-aware Unified Runtime Storage Manager. Internal R storage inspection and cleanup no longer depend on Termux, while External Provider storage management remains independent.

The earlier baseline/alpha43-r44-known-good branch at 0285db2c26565e1aa2a47624d2cad8fa9c19e89f remains a historical frozen baseline and must not be moved.

All subsequent r45+ work should use baseline/alpha43-r44-final-known-good as the primary regression reference until a later baseline is explicitly promoted.


## r45 Multi-Project Concurrent Runtime

r45 is split into two provider-aware acceptance stages.

r45-A freezes and proves the existing External Provider multi-project contract. External observation state is already keyed per project; Runtime process ownership and STOP are keyed by each project's runtimeId. The implementation is not rewritten merely to satisfy the new milestone. Regression tests and real-device dual-project evidence are the acceptance mechanism.

r45-B will address the remaining Embedded R management-layer gap: V04Activity still uses a single `embeddedPollProject` / `embeddedPollInFlight` pair even though InternalAlpineSession and the foreground service can own multiple project sessions. The target is one shared per-project observation contract across providers, while keeping provider-specific process/runtime adapters separate.

The r44 final baseline remains `baseline/alpha43-r44-final-known-good` until r45 is explicitly accepted.
