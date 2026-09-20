# SiftAlpha R Environment & Dependency Model Architecture Audit

## 文档性质与审计基线

本文件是 alpha43 之后的 **Research + Architecture Audit**。它记录当前真实 Embedded R 实现、权威上游资料以及未来可实施的架构建议；不实现依赖安装器、环境管理器、`pip`、`venv`、wheel 安装、UI 或 Runtime 行为。

审计开始时重新读取的真实仓库状态：

| 项目 | 值 |
|---|---|
| Repository | `kuashan/siftalpha-one` |
| Branch | `codex/siftalpha-x-embedded-cpython-spike` |
| Starting HEAD | `9a32488a4c1d03e754c00eefd26eb38dcc697b70` |
| Parent HEAD | `860645af9745ebbb978dad6d177359f5fddb23f1` |
| versionCode | `119` |
| versionName | `0.8.0-alpha43` |
| Workspace | 远端分支干净；审计 clone 在检查时干净 |

本文件中的判断使用以下标签：

- **Verified fact**：由当前仓库源码、构建脚本、测试或权威上游文档直接确认。
- **Inference**：由 Verified fact 推导出的工程风险或约束。
- **Recommendation**：SiftAlpha 未来应采用的设计选择，不是当前已经实现的能力。

**状态边界：** 本轮只新增本审计文档。没有修改 `app/**`、`native/**`、`tools/**`、Gradle、workflow、测试、资源或版本号；没有启动 alpha44 production implementation。

## Executive Summary

当前 Embedded R 已经是一个可在 Android app-private 文件系统中运行受限、file-backed、pure-Python project script 的 CPython 实现方向，但还不是完整的 Project Environment Runtime：

- **Verified fact**：CPython `3.14.7`，目标 ABI 为 `arm64-v8a` / `aarch64-linux-android`；Python home、stdlib 和 `lib-dynload` 被复制到 app-private `files/siftalphax/python`。
- **Verified fact**：CPython 在一个 app process 中初始化一次；当前只有一个 active Embedded Session；native 代码不调用 `Py_FinalizeEx`。
- **Verified fact**：`PyConfig.use_environment = 0`、`config.user_site_directory = 0`；当前准备脚本删除 `venv` 与 `ensurepip`；没有 Embedded `pip` bootstrap、第三方 package installer 或 site-packages environment contract。
- **Verified fact**：SAF project 只被 bounded-copy 到 `files/siftalphax/projects/<sessionId>`；这是 Project Source Staging，不是依赖环境。
- **Verified fact**：现有 `PythonRuntimeAdapter` 的 `python3 -m venv`、`pip install`、`/root/venvs` 都属于 Termux + PRoot + Ubuntu External Provider，不属于 Embedded R。

最终建议不是把桌面 Linux 的目录和命令直接搬到 Android，而是分层建立：

1. **Logical Project Environment**：每个项目有稳定 identity、依赖指纹、安装清单、generation 和状态；不复制 Python executable。
2. **SiftAlpha-managed wheel installation**：第一阶段只接受经过选择、哈希验证、内容检查的纯 Python wheel；不在手机端执行 arbitrary build backend。
3. **Pre-resolved / verified manifest**：解析可以在受信任的开发/构建环境完成；设备端只下载或复用明确的 artifact，并进行结构化验证。
4. **Atomic generation update**：`env-v2` 构建并验证成功后再切换；失败不能破坏当前 READY environment。
5. **Process boundary gate**：当前 process-scoped CPython 的 gate 必须绑定 project-owned `environmentId` / Project Environment Identity，而不是只比较 dependency fingerprint。即使 Project A 与 Project B 的 dependency fingerprint 相同，B 仍可能通过 `sys.modules` 复用 A 已加载的 module object 和进程级状态；进程已加载 environment A 后，不允许切换到 environment B。若需要强隔离，应采用 per-environment fresh process，或未来经证明的更强 isolation mechanism；在此之前必须明确拒绝或回到 External Provider。

因此推荐的长期方案是 **Hybrid Model**：预解析/预构建的可信分发清单 + SiftAlpha 管理的、仅允许受控 wheel 的设备端安装器。alpha44 的最小真机范围应是 **Pure-Python Project Environment v1**，并带有明确的 process-scoped capability gate；不包括 arbitrary native wheels、source build、完整 `venv`、直接 `pip` 或 concurrent Embedded sessions。

## Current State

### 当前真实运行基础

| 事实 | 当前源码结论 | 主要证据 |
|---|---|---|
| CPython | `3.14.7` | `tools/prepare_embedded_cpython_android.sh`、`third_party/cpython/PROVENANCE.md` |
| CPython artifact | `python-3.14.7-aarch64-linux-android.tar.gz`，固定 SHA-256 | preparation script / provenance |
| Android ABI | `arm64-v8a` / `aarch64-linux-android` | script、`app/build.gradle.kts` |
| Android app compatibility | `minSdk = 26`，`targetSdk = 36` | `app/build.gradle.kts` |
| NDK | `27.3.13750724` | `app/build.gradle.kts` |
| Python home | `filesDir/siftalphax/python` | `EmbeddedPythonFiles.kt` |
| stdlib | `home/lib/python3.14` | generated asset copy与 native `home`配置 |
| dynamic modules | `home/lib/python3.14/lib-dynload` | `EmbeddedPythonFiles.prepare()` 检查 |
| libpython | APK/JNI native library，由 CMake 链接 | preparation script、`CMakeLists.txt` |
| Project Source Staging | `filesDir/siftalphax/projects/<sessionId>` | `EmbeddedPythonFiles.kt`、`EmbeddedPythonProjectStager.kt` |
| temporary directory | 当前为共享的 `home/tmp`，运行时设置 `TMPDIR` 指向这里 | `EmbeddedPythonFiles.kt`、`EmbeddedPythonNative.cpp` |
| process lifecycle | `std::call_once` 初始化一次；保留 bootstrap thread state；无 `Py_FinalizeEx` | `EmbeddedPythonNative.cpp` |
| Session boundary | 同时只接受一个 active session；terminal snapshot 在 app process 存活期间保留 | `EmbeddedPythonSession.kt`、native globals |
| current dependency environment | 没有 Embedded project environment、environment identity、dependency manifest 或 package facts | `EmbeddedPythonExecutionSpec.kt`、`EmbeddedPythonResult.kt`、native bridge |

### CPython preparation 的真实边界

`prepare_embedded_cpython_android.sh`：

- 只下载一个固定的官方 Python.org Android artifact，并检查固定 SHA-256。
- 复制 `include/python3.14`、`libpython3.14.so`、`lib/python3.14` 和 native `.so` 到构建输入。
- 从 bundled stdlib 删除 `test`、`idlelib`、`tkinter`、`turtledemo`、`venv`、`ensurepip`。
- 没有安装 `pip`、没有创建 virtual environment、没有解析 requirements、没有生成 site-packages。

因此当前可以确认的是 **Embedded R 不支持依赖安装**，而不是“pip 已经在 Embedded R 中可用”。`ensurepip` 被删除，且没有其他 bootstrap 或 installer 路径；不能仅因 CPython 本身能执行 Python 代码，就推导出 `pip`、`venv` 或第三方包能力存在。

### 当前 PyConfig 与路径处理

`EmbeddedPythonNative.cpp` 当前使用 `PyConfig_InitPythonConfig`，然后明确设置：

```text
config.use_environment = 0
config.user_site_directory = 0
config.install_signal_handlers = 0
config.parse_argv = 0
config.home = <app-private files/siftalphax/python>
```

当前源码没有显式设置 `module_search_paths_set`、`module_search_paths` 或独立的 `isolated` 配置，也没有 environment 参数。native execution 会：

1. 保存 CPython 初始化时的原始 `sys.path`、`sys.argv`、`__main__`、stdout/stderr；
2. 把 entrypoint directory 和 project execution root 放到 `sys.path` 前面；
3. 设置 `sys.argv[0]` 与 working directory；
4. 执行脚本；
5. 清理 project staging path 下可识别的 modules，并恢复上述 process state。

这意味着当前路径模型是“base CPython path + 当前 project path”，并不是“base + 一个可识别的 project environment site-packages”。未来不能只把一个目录插入 `sys.path` 就宣称完成了 dependency isolation。

### 当前 module cleanup 与 re-entry

当前 native cleanup 会删除 `__file__` 位于 `projectsBase` 下的 modules，并恢复 `sys.path`、`sys.argv`、`__main__` 和标准输出状态；它不会可靠地清理：

- 位于未来 environment site-packages 的第三方 modules；
- `sys.path_importer_cache`、package `__path__` 或其他 import hooks；
- 由模块对象、全局变量、后台线程或 native code 保留的引用；
- 已加载 native extension 的 `.so` 生命周期和静态/global state。

这对“同一 app process 先运行 project A 的 `foo==1`，再运行 project B 的 `foo==2`”构成真实风险。Python import system 会首先检查 `sys.modules`，删除一个键也不保证仍被其他对象引用的 module 被销毁；这不是简单清空目录或 `sys.path` 可以解决的问题。

### 当前现有 External Provider 环境不是 Embedded 环境

`PythonRuntimeAdapter` 的真实外部路径要求：

- `python3`、`python3 -m pip`、`python3 -m venv`；
- 在 PRoot/Ubuntu 中准备 `/root/venvs/<runtime-id>`；
- 需要时用 apt 安装 `python3-venv` 与 `python3-pip`；
- `requirements.txt` 用 `venv/bin/python -m pip install -r`；
- `pyproject.toml` 用 `pip install -e <project>`；
- 用 source/hash marker 判断环境是否 READY 或 dependency manifest 是否改变。

这证明 External Provider 已经有一个面向 Linux/Ubuntu 的依赖路径，但它不能作为 Embedded R 当前已有能力，也不能作为 Android app-private environment 的安全模型直接复用。特别是，外部 path 的 hash marker 是原始 manifest hash，不是解析后的完整 dependency graph 或 installed package manifest；它还使用原地安装，不提供本文建议的 generation + atomic switch。

### 当前测试覆盖与缺口

现有测试真实覆盖：

- `EmbeddedPythonExecutionSpecTest`：路径 containment、entrypoint、working directory、symlink escape、generation/session 输入；
- `EmbeddedPythonProjectStagerTest`：SAF/project tree bounded copy、unsafe path、节点/文件/字节限制、partial staging cleanup；
- `EmbeddedPythonEntrypointPolicyTest`：显式入口、确定性 conventional entrypoint、歧义和不安全入口；
- `EmbeddedPythonResultTest`：structured snapshot、runtime phase、stdout/stderr、one-active-session policy；
- `EmbeddedPythonCapabilityRoutingTest`：纯脚本可进入 Embedded R，依赖环境需求被拒绝，非 Python、supplemental runtime、任意 run command 被拒绝；
- `PythonDependencyDiagnosticsTest`：External Provider shell 的结构化诊断，包括 package missing、wheel incompatibility、native build、network、storage 等；
- `RuntimeEnvironmentComposerTest`：External Python + Node environment marker 合成。

现有测试没有证明：

- requirements 或 `pyproject.toml` 的 authoritative dependency graph；
- wheel tag、哈希、archive path 和 `.dist-info/RECORD` 验证；
- per-project site-packages isolation；
- environment reuse/stale/generation/atomic switch；
- download cache 与 installed environment 分离；
- pip/SSL/CA/build isolation/source build；
- process-scoped CPython 下不同 package version 的安全切换；
- Android native extension 的 bionic/API/DT_NEEDED 兼容性。

## Current Files / Ownership Map

| 文件/组件 | 当前职责 | 对未来依赖模型的结论 |
|---|---|---|
| `tools/prepare_embedded_cpython_android.sh` | 固定 CPython Android artifact 的下载、校验、裁剪、打包 | 继续负责 Runtime Base provenance；不应变成项目依赖安装脚本 |
| `app/src/main/cpp/EmbeddedPythonNative.cpp` | 一次性 CPython 初始化、GIL、cwd/TMPDIR、脚本执行、snapshot、STOP | 未来需要接收明确 environment facts 或转到隔离 process；当前不能承载任意环境切换 |
| `.../siftalphax/EmbeddedPythonFiles.kt` | app-private CPython home 与 fixture staging | 未来应区分 runtime base、environment、session workspace；当前仅有前两者中的 base/staging |
| `EmbeddedPythonExecutionSpec.kt` | project root、entrypoint、working directory、session/generation 合同 | 未来需要显式关联 environment identity，但本轮不改 |
| `EmbeddedPythonSession.kt` | 一个 process-scoped singleton、单 active session、native start/stop | 是 process isolation 风险的主要 owner |
| `EmbeddedPythonBridge.kt` | JNI API 与固定 home/project/session 参数 | 当前没有 environment 或 package contract |
| `EmbeddedPythonResult.kt` | Embedded snapshot、runtime phase、terminal state、诊断文本 | 未来 environment state 应是独立 structured facts，不应塞进 stdout/stderr |
| `EmbeddedPythonProjectStager.kt` | SAF → bounded app-private project source copy | 只负责源码，不负责依赖包、缓存或环境清理 |
| `EmbeddedPythonEntrypointPolicy.kt` | M 侧确定性 entrypoint 解析 | 不应通过 import scanning 猜依赖 |
| `EmbeddedPythonCapabilityRouting.kt` | Embedded R eligibility gate；有 dependency environment requirement 时拒绝 | 是 alpha44 capability boundary 的入口 |
| `PythonDependencyDiagnostics.kt` | External Provider 的 marker/log diagnosis | 可复用 failure vocabulary；不能把 pip stdout 变成 Embedded state |
| `RuntimeEnvironmentComposer.kt` | External Python + Node environment 的项目级 marker 合成 | 现有 External Provider model，不是 Embedded Project Environment |
| `ProjectRuntimeExecutionPlanner.kt` | 根据项目证据选择 Runtime kind、识别 requirements/pyproject | 目前是 runtime selection，不是 dependency resolver |
| `PythonRuntimeAdapter.kt` | Termux/PRoot/Ubuntu 下的 venv/pip/install/start/status/clean | 保持 production external path；不应被误读为 Embedded 实现 |
| 对应 JVM tests | 当前路径、staging、routing、snapshot、external diagnostics regression | 未来增加 environment contract/installer/atomic/process tests |

## Environment Model

### 五种必须分开的存储/生命周期概念

这五类对象不能共用一个目录，再依赖清理规则猜测用途：

| 概念 | 内容 | 生命周期 | 是否允许跨项目复用 |
|---|---|---|---|
| **Runtime Base** | CPython binary/libpython、stdlib、`lib-dynload`、Runtime-owned libraries、provenance | App/runtime 版本生命周期；通常只读 | 允许作为所有项目的只读 base |
| **Project Source Staging** | SAF 导入的当前项目源码、entrypoint、项目相对目录 | project/session staging 生命周期 | 不应把依赖包写入这里 |
| **Project Environment** | 已验证的第三方 packages、`.dist-info`、安装清单、environment metadata | 项目级、generation 级；可被顺序 Session 复用 | 不直接共享 mutable tree |
| **Dependency Download Cache** | wheel archives、hash、metadata、受控 index responses | App cache 生命周期；可 quota/GC | 可以复用 immutable artifact |
| **Session Workspace** | cwd、TMPDIR、运行日志、session state、瞬态输出 | 单次 Session/generation | 不跨 Session 作为依赖来源 |

### 推荐的 app-private 逻辑布局

下面是 **Recommendation**，不是当前目录已经实现的布局：

```text
files/siftalphax/
  runtime-base/
    cpython/3.14.7/arm64-v8a/
      stdlib/
      lib-dynload/
      provenance.json
  projects/
    <project-id>/source/<staging-generation>/
  environments/
    <project-id>/<environment-id>/
      environment.json
      resolved-manifest.json
      site-packages/
      verification.json
  download-cache/
    sha256/<artifact-digest>/artifact.whl
    metadata/<request-key>.json
  sessions/
    <session-id>/
      cwd/
      tmp/
      logs/
      state.json
```

当前 `files/siftalphax/python` 与 `files/siftalphax/projects/<sessionId>` 可以作为历史实现事实保留；未来环境模型不能把 `python/tmp`、project source 和 package tree 混在一起。

### Project Environment Identity

用户提出的候选式：

```text
Project Identity + Runtime ABI + Python major/minor + dependency fingerprint
```

其优点是简单、可解释，能识别“同一项目、依赖未变”的复用；缺点是：

- 只写 Python major/minor，不能区分 `3.14.7` 与未来有行为/ABI差异的 patch runtime；
- 只写 `arm64-v8a`，不能表达 Android API floor、native library closure 或 app runtime provenance；
- 只 hash 原始 requirements 文本，不能表达规范化后的依赖 graph、选择的 extras、hash policy 和 installer policy；
- 把 Project Identity 放进 environment identity 后，不能直接把 mutable site-packages 在项目之间共享；但这正是安全默认值，跨项目复用应发生在 immutable download/artifact 层，而不是 shared mutable environment 层。

**Recommendation：** 使用两个 identity，而不是一个过载的 hash：

这里必须明确区分两个概念：

- **Dependency Fingerprint** 是 dependency content compatibility identity：描述规范化声明、完整解析 graph、exact artifact/hash 与安装 policy 是否兼容。它可以用于 stale detection、manifest 比较和不可变 artifact cache 命中判断。
- **Environment Identity / `environmentId`** 是 project-owned runtime environment identity：标识某个项目拥有的 environment binding、其 runtime contract 和 generation/ownership。它不是“只由 dependency fingerprint 得出的共享 key”。不同 Project 即使 fingerprint 完全相同，也必须拥有不同的 `environmentId`；同一个 `environmentId` 也不因此获得 clean-session 语义。

因此，process gate 的最小安全条件是“当前 process 已绑定哪个 `environmentId`”，而不是“当前 fingerprint 是否相等”：

```text
process loaded environment A
  → environment B is rejected
  → even when dependencyFingerprint(A) == dependencyFingerprint(B)
```

只有在 fresh process/process-per-environment，或已经证明的更强隔离机制下，才可以讨论在同一个 app 生命周期内切换不同 environment。跨项目复用应停留在 immutable artifact/download cache 层，不能借助相同 fingerprint 隐式共享 mutable site-packages 或 module runtime state。

```text
ProjectEnvironmentIdentity = SHA-256(
  schemaVersion,
  stableProjectIdentity,
  CPython implementation + full version,
  runtime artifact/provenance digest,
  CPython ABI contract,
  Android ABI,
  device/API compatibility policy,
  canonical dependency declaration,
  resolved dependency graph or lock digest,
  installer/security policy version
)

PackageArtifactIdentity = SHA-256(
  normalized distribution name,
  distribution version,
  exact wheel filename/tags,
  artifact bytes
)
```

`ProjectEnvironmentIdentity` 是项目的可复用 environment binding；`PackageArtifactIdentity` 是可以跨项目复用的不可变缓存对象。这样可以同时满足：

- 相同项目、依赖未变：复用 READY environment；
- dependency declaration 改变：fingerprint 改变，旧 environment 变 STALE；
- 两个项目：各自拥有 environment metadata 和 site-packages ownership；
- 同一 package 不同版本：不会进入同一个 mutable site-packages tree；
- 相同 package artifact：只在 download cache 复用，不隐式共享可变安装树。

### Environment 不是 Session

一个 READY environment 可以被多个**顺序** Session 复用；但每个 Session 仍然拥有自己的 `sessionId`、generation、cwd、TMPDIR、日志和 runtime facts。STOP 通常只终止 Session，不删除 environment。CLEAN 必须显式区分：

1. clear session/workspace；
2. clear project environment；
3. clear download cache。

不能因为用户 STOP 一次运行，就删除可复用的 environment，也不能因为清理 cache 就破坏当前 READY environment。

**限制：** 顺序 Session 复用同一个 environment 只表示复用同一组已安装文件和 manifest，不保证新的 interpreter/module runtime 是干净的。第三方 module 的 global state、callbacks、logging handlers、threads、monkey patches 或其他 retained references 可能跨 Session 留在同一 process；`environmentId` binding 不能被写成 clean-session guarantee。

## Dependency Declaration Model

### 权威来源

PyPA 当前 `pyproject.toml` 规范区分 `[build-system]`、`[project]` 与 `[tool]`；`[build-system].requires` 是构建后端所需的 build-time dependencies，不等同于项目运行时 dependencies。[PEP 621](https://peps.python.org/pep-0621/) 规定 `[project].dependencies` 是静态核心元数据的一部分，同时允许 metadata 通过 `dynamic` 声明由 backend 提供。

建议的 discovery contract：

| 来源 | v1 角色 | 处理原则 |
|---|---|---|
| `requirements.txt` | 可作为直接运行依赖声明 | 只支持明确的受控子集；应支持注释、规范化名称、固定版本/哈希；拒绝或明确报告 VCS、editable、本地路径、任意脚本选项 |
| `pyproject.toml` `[project].dependencies` | 可作为项目元数据依赖声明 | 只读取静态数组；动态依赖、未声明 `[project]`、extras/optional dependency 未明确选择时报告 unsupported |
| `pyproject.toml` `[build-system].requires` | build-time dependency declaration | 第一阶段不执行 source build，不把它偷偷当 runtime dependency |
| `setup.py`、`setup.cfg`、Pipfile 等 | capability evidence | 第一阶段报告需要 External Provider/unsupported，不执行其代码来猜 graph |
| import scanning | diagnostics | 只提示可能缺失的 import；不把 `import PIL` 映射为 `pip install PIL`，不作为 authoritative declaration |

### 同时存在 requirements 与 pyproject 时

不能偷偷合并两个可能不同的 dependency graph。推荐策略是：

- 如果只有一个受支持来源存在，使用该来源；
- 如果两个来源同时存在，默认返回结构化的 `MULTIPLE_DEPENDENCY_DECLARATIONS` / declaration conflict；
- 只有未来增加明确项目 policy 或锁定清单后，才允许选择一个来源；选择规则必须进入 fingerprint 与诊断；
- 不能用“requirements 优先但 pyproject 仍偷偷补依赖”的隐式合并。

这是比“自动猜哪个更完整”更可预测的行为。对于 alpha44 最小范围，建议一个项目只提供一个 authoritative source；两个同时存在的项目可以继续走 External Provider。

### Canonical dependency fingerprint

fingerprint 不应只是文件字节 hash。推荐包含：

- source kind 与 source schema version；
- 规范化 distribution name、specifier、markers、selected extras；
- exact artifact hash/URL policy；
- `requires-python` 与 target CPython compatibility；
- complete resolved graph / lock digest；
- runtime ABI、Android ABI/API、installer policy version；
- 不支持的选项、dynamic metadata 或 source build 不能被忽略，应转为 structured failure。

### Resolver authority 是 alpha44 的 blocking decision

Hybrid Model 依赖一个 pre-resolved / trusted manifest，但该 manifest 的权威来源尚未确定。alpha44 implementation 开始前必须明确由谁把 top-level declarations 转成：

1. complete transitive dependency graph；
2. exact versions；
3. exact wheel artifacts；
4. 每个 artifact 的 SHA-256。

候选来源包括：

- trusted CI / trusted build machine；
- trusted resolution service；
- explicit SiftAlpha lock manifest；
- fully pinned and hash-complete project input。

在这个来源和信任边界确定前，设备端 v1 不能悄悄退化为通用 pip resolver，也不能把不完整的 top-level declaration 当成已解析 graph。该决定是 alpha44 的 implementation-blocking design decision，不在本轮实现。

## Installation Model Options

| 模型 | Android compatibility | Security | Resolution | Maintenance | Ecosystem compatibility | Offline reuse | Upgrade/cleanup | Failure reporting | Native boundary | 结论 |
|---|---|---|---|---|---|---|---|---|---|---|
| **A. Embedded CPython directly runs pip** | 可行但需补齐 pip/SSL/CA/TMP/权限/进程与工具链 | 最宽攻击面；默认会运行 distribution/build code | 最完整，含 backtracking/build isolation | 高；要维护 pip/bootstrap/build backend/subprocess | 最高 | 有 pip cache，但需设计受控 cache | pip 原地行为不满足 atomic env，需外包一层 | 可解析但不应依赖 stdout | 默认会遇到 native/sdist | 技术可行，第一版不推荐 |
| **B. SiftAlpha-managed wheel installer** | 适合 app-private 文件与 Android policy | 可拒绝源码、脚本、错误 tag；可强制 hash | 需要外部/受控 resolver；设备端不必全解析 | 中；需实现 wheel/metadata/transaction | 纯 wheel 子集很好 | content-addressed artifact cache 很好 | generation + manifest 可控 | 结构化、可枚举 | 第一版可拒绝 native | 第一阶段推荐基础 |
| **C. Pre-resolved / prebuilt wheel environment** | 最可预测；由 CI/受信任机器产出 | artifact 可签名、哈希、审计 | 设备不做通用 resolver | 维护预构建矩阵成本高 | 对任意用户项目覆盖较窄 | 最好，可离线复用 | 整包 generation 简单 | manifest 驱动 | native 可单独审核 | 适合 production fleet/离线包 |
| **D. Hybrid** | 设备只做受控安装，兼容性边界明确 | resolution 与 install 分层；hash/签名/only-binary | 预解析清单 + 受控 fallback | 中高，但职责清楚 | 纯 wheel 起步，未来可扩展 | cache 与预解析都可用 | atomic generation + artifact GC | structured facts，不靠 pip log | native 需单独 capability | **长期推荐** |

最终选择：**D Hybrid**。alpha44 v1 的设备端实现应主要落在 B：SiftAlpha-managed pure-Python wheel installer；resolution 可以来自预解析清单或严格受控的已锁定输入。A 可以作为未来 compatibility adapter 研究，但不能成为第一版的隐式基础设施。

## pip Feasibility

“pip 是 Python 写的，所以可以运行”不足以证明 Embedded Android 可行。需要分别审计：

| 条件 | 技术状态 | 第一版判断 |
|---|---|---|
| pip bootstrap | 当前 `ensurepip` 被删除，也没有 pip bundle | 需要额外打包、license/provenance 与升级策略；不纳入 v1 |
| venv | 当前 `venv` 被删除，且当前没有独立 Python executable contract | 不应靠 venv bootstrap 解决 environment identity |
| SSL/TLS/CA | pip 需要可信 HTTPS；Embedded CPython 的 CA 路径、Android trust store、代理/证书轮换需显式设计 | 需要 CA bundle/trust policy、证书错误分类；不能默认已可用 |
| network | Android app 网络权限、生命周期、离线、超时、取消、重试与 index policy 都要由 SiftAlpha 管理 | 只允许受控 index/URL；离线优先使用 cache |
| temp/storage | pip 解包、build isolation、wheel cache 需要大量临时空间；当前 `TMPDIR` 是共享 `home/tmp` | 需要 session/environment 专属 temp、配额、清理与 crash recovery |
| filesystem | 需要 app-private 可写目录、archive extraction 安全、atomic rename 与 partial cleanup | 需要独立 transaction root；不能在 READY tree 原地 install |
| subprocess | pip/build backend 可能启动 Python、compiler、`rustc`、`cmake`、系统命令 | 当前 Embedded boundary 没有任意 subprocess/build contract；v1 禁止 source build |
| build isolation | PEP 517/518 build process 会创建隔离环境并安装 build dependencies；pip 文档说明 backend 可能编译 C/C++ 等扩展 | 这是另一个 dependency graph 与 arbitrary code execution 面；v1 不执行 |
| console scripts | wheel entry points 可能需要生成 executable/wrapper，并依赖 `sys.executable`/PATH | v1 只运行明确 Python entrypoint；console-script launch 另行设计 |
| metadata | 需要准确读 `.dist-info/METADATA`、`RECORD`、direct URL/hash、Requires-Dist | 应成为 structured package facts，不解析 pip stdout |
| resolver | pip resolver 会下载依赖元数据并 backtrack | 设备端通用 resolver 增加网络、时间和可重复性风险；v1 使用预解析/严格锁定输入 |
| upgrade/uninstall | 原地 pip install 可能留下混合版本或 partial state | 必须由 generation transaction 包裹；不是直接调用 pip |
| editable install | PEP 660 backend 可运行任意构建逻辑且不能按普通 wheel cache 处理 | v1 明确拒绝 |
| sdist/source build | 需要 compiler、headers、NDK、CMake/Rust、pkg-config、system libraries | v1 明确拒绝 |

pip 官方文档明确指出：默认 pip 不提供远程 tampering 保护且会运行 distribution 中的 arbitrary code；安全安装至少需要 hash-checking 与 `--only-binary :all:`。pip build-system 文档也说明现代 `pyproject.toml` 安装可能创建隔离 build environment、安装 build dependencies、生成 wheel，并且 backend 可能编译 C/C++ 扩展。[pip secure installs](https://pip.pypa.io/en/stable/topics/secure-installs/)、[pip build system](https://pip.pypa.io/en/stable/reference/build-system/)

结论：direct pip 在“完整补齐基础设施、明确安全 policy、进程隔离、工具链与失败回滚”以后是 technically feasible；它不适合成为当前 Embedded R 的默认第一版 installer。

## venv Decision

**结论：传统 CPython `venv` 不应成为当前 Embedded R 的核心存储/启动模型。**

PEP 405 的传统模型包含独立 environment 的 Python binary、自己的 site directories，并通过 `sys.prefix` 等机制识别 environment；它很适合桌面上执行 `python -m venv`，但当前 Embedded R 的事实不同：

- interpreter 是 app process 内一次性初始化的共享 CPython；
- Python home 是 app-private runtime base；
- 当前没有 environment-specific Python executable；
- `use_environment=0`、`user_site_directory=0`；
- bundled `venv`、`ensurepip` 已删除；
- 不调用 `Py_FinalizeEx`，也不能把重启解释器作为每次切换的普通操作。

**Recommendation：** 采用 SiftAlpha logical Project Environment：

```text
environment metadata
+ resolved package manifest
+ verified site-packages tree
+ controlled sys.path injection
+ runtime/environment compatibility facts
```

这保留了“每个项目有独立 packages”的核心价值，不复制 libpython/stdlib，也不制造多个假的 Python executable。已知限制是：依赖 `sys.prefix`、`sys.executable`、console script wrapper、`.pth` 或 virtualenv-specific behavior 的包不保证工作；这些包应在 capability check 中被拒绝或回到 External Provider。未来如果引入 per-environment OS process，可以再提供更接近传统 venv 的 compatibility layer，但不应反过来强迫当前 embedded process 复制桌面 venv。

## sys.path Model

### 当前真实顺序

当前 native 代码保存 CPython 的原始 `sys.path`，再把 entrypoint directory 与 execution root 插到前面；没有 environment site-packages 参数，也没有明确把 base stdlib/lib-dynload 与 environment 层分开。因此当前事实是：

```text
<entrypoint directory>
<project execution root>
<original CPython sys.path>
```

其中 original path 的精确内容依赖 CPython home/path calculation；不能把它当作已验证的 per-project environment path。

### 推荐顺序

为了保持普通 Python 项目的相对导入直觉，同时降低 third-party package shadowing stdlib 的风险，建议未来显式构造并去重为：

```text
1. entrypoint directory
2. project root / source root
3. Runtime Base stdlib: .../lib/python3.14
4. Runtime Base dynamic modules: .../lib/python3.14/lib-dynload
5. Project Environment site-packages
```

理由：项目自身代码仍可用相对文件 import；标准库先于第三方 packages，避免某个依赖目录意外覆盖 `json`、`email` 等 base modules；第三方 packages 最后集中在 project environment。若为了兼容现有 CPython site initialization 而采用另一顺序，也必须把顺序写进 contract、测试 shadowing，并禁止隐含系统/user site。

必须同时做到：

- 不读取 `PYTHONPATH`、`PYTHONHOME` 等外部环境影响；
- 不启用 user site；
- 不把另一个 project 的 source/environment path 留在列表；
- environment 切换时清理/重建 `sys.path_importer_cache` 与 package path state；
- 不把 `sys.path` isolation 误称为 `sys.modules` 或 native extension isolation。

## Pure-Python Boundary

**Recommendation：纯 Python wheel 是 Embedded R 第一阶段唯一合理的 package boundary。**

最低可接受定义：

- wheel platform tag 为 `any`，ABI tag 为 `none`，Python tag 与当前 interpreter 相容，例如 `py3-none-any` 或明确兼容的 CPython pure wheel；
- archive 内不含 `.so`、`.dylib`、`.dll`、ELF/native executable 或其他 native payload；
- 不含 `.pth` 等导入时执行额外代码的隐式 hook；
- wheel metadata、`RECORD`、distribution name/version 与 declared hash 可验证；
- transitive dependencies 也必须落在同一允许集合，不能因为 top-level package 是纯 Python 就放行 native child；
- package 安装到 project-owned environment generation，不修改 Runtime Base 或 Project Source Staging；
- 不能把 prepare 阶段的 `import package` 当作无副作用的验证：import 会填充 `sys.modules`、执行 package top-level code，并可能改变 process-global state（例如 callbacks、logging handlers、threads 或 monkey patches）；
- alpha44 必须在实现前决定验证策略：**A.** prepare 阶段只做 metadata/file verification；或 **B.** 把 import verification 放进 disposable isolated process。当前审计只记录这个 blocking design decision，不选择实现方式；
- import scanning 只能作为 diagnostics，不能把 `import PIL` 等名字推断为 dependency declaration；
- upgrade 生成新 generation，旧 generation 在无 Session 引用后清理。

这类 package 可以覆盖相当一部分纯 Python ecosystem，但不等于所有 PyPI package：

- 某些纯 Python 包在 import 时需要系统命令或特定 OS 行为；
- console scripts、editable install、data files、`.pth`、dynamic metadata 仍需单独 policy；
- 纯 Python 依赖仍然会执行第三方 Python code，因此没有脱离 security model。

## Wheel Compatibility

PyPA 的 platform compatibility tag 形式是：

```text
{python tag}-{abi tag}-{platform tag}
```

例如 Python tag 区分 `cp`（CPython），ABI tag 可以是 implementation-specific ABI 或 `abi3`，`none` 表示没有 extension ABI 要求。[Platform compatibility tags](https://packaging.python.org/en/latest/specifications/platform-compatibility-tags/)

PyPA 当前规范明确规定 Android platform tag schema 为 `android_apilevel_abi`，也就是本文使用的 `android_<api level>_<abi>`；`android_27_arm64_v8a` 表示 API level 27 or later 与 `arm64-v8a`。`manylinux_*` 是 glibc Linux contract，`musllinux_*` 是 musl Linux contract；二者都不是 Android platform tag，不能因为架构同为 AArch64 就视为 Android-compatible。alpha44 pure-Python wheels 的主要 allowlist 应是 ABI `none`、platform `any`，例如 `py3-none-any`；未来 native Android wheel 才进入 `android_<api>_<abi>` 兼容性判断。

对当前 Embedded Android 目标，必须同时评估：

| 维度 | 当前/推荐判断 |
|---|---|
| Python implementation | CPython `3.14.7`；二进制 extension 的 CPython tag 应按 `cp314` 合同判断，不能只看 `cp3` |
| Stable ABI | `abi3` 只表达 CPython stable ABI 可能性，不解决 Android libc、动态库依赖或 API floor |
| Android ABI | `arm64-v8a` / AArch64；Android NDK 文档列出 `arm64-v8a` 对应 AArch64 |
| OS/libc | Android bionic，不是 glibc Linux；`manylinux` 语义针对 glibc，`musllinux` 语义针对 musl |
| Android platform tag | PyPA schema 为 `android_<api level>_<abi>`；例如 `android_27_arm64_v8a` 表示 Android API level 27 或更高、`arm64-v8a` ABI |
| Platform tag | `manylinux`/`musllinux` 不能自动被视为 Android-compatible；Android wheel 必须有明确 Android build/provenance 与真机/模拟器验证 |
| API floor | 当前 app `minSdk=26`；native artifact 还必须声明并验证自身 Android API floor，不能仅由 wheel filename 推断 |
| external libs | `DT_NEEDED`、`dlopen`、libpython/OpenSSL 等依赖必须由 Android packaging/linker contract 管理 |

Python 官方 Android 文档说明 Android 使用 embedded mode，app 应打包 libpython、stdlib 和自己的 Python code；也说明 Python packages 可以构建为 Android wheels，并推荐使用 cibuildwheel 处理 cross-compilation 与 emulator testing。[Using Python on Android](https://docs.python.org/3.14/using/android.html) 当前页面对应 Python 3.14.7，2026-09-16 更新。cibuildwheel 当前文档也列出 CPython 3.14 Android wheel build，但“可构建”不等于某个任意 PyPI artifact 能在 SiftAlpha 的 Android API、ABI、linker namespace 和 app process 中安全运行。[cibuildwheel platforms](https://cibuildwheel.pypa.io/en/stable/)

因此第一版应使用 allowlist/inspection，而不是“只要是 `.whl` 或 `aarch64` 就安装”。

## Native Package Boundary

带有以下任一项的 distribution 视为 **Native Package**：

- `.so` / C extension / C++ extension / Rust extension；
- `DT_NEEDED` 指向除明确 Android-owned libraries 以外的 shared library；
- 需要 `dlopen`、JNI/native linker、系统命令或额外 system library；
- wheel 虽然有 `abi3`，但没有明确 Android build、API floor、AArch64 与 linker closure 证据。

Android NDK 的 `arm64-v8a` 是 AArch64 ABI，但 Android 的 bionic 与普通 Linux/glibc 不同。AOSP linker namespace 文档说明动态 linker 会处理 ELF `DT_NEEDED` 与 `dlopen` 依赖，并且 search/permitted paths、namespace isolation 和 exported libraries 会影响能否加载 shared library。[AOSP linker namespace](https://source.android.com/docs/core/architecture/vndk/linker-namespace)

**第一版决策：不支持 arbitrary native wheels。** 对这类包返回 `UNSUPPORTED_NATIVE_PACKAGE`，不尝试把 manylinux/musllinux wheel 当 Android wheel，不在手机上编译，也不 silent fallback 到一个未说明的 provider。

未来要放开必须至少具备：

1. Android target build（AArch64、CPython ABI、API floor）；
2. 完整 `DT_NEEDED` closure 与每个 shared library 的 ownership/provenance；
3. linker namespace、library extraction/loading 和 package path contract；
4. 真机矩阵测试，包括不同 Android API/device；
5. process isolation，避免不同 native module version 的 static/global state 互相污染；
6. 签名、hash、license、crash/rollback 与 quota 设计。

## Source Build Boundary

`sdist`、`setup.py`、`pyproject.toml` build backend、PEP 517 build hooks 都不只是“解压源码”：pip 文档描述了 isolated build environment、build dependencies、metadata generation、wheel generation，backend 还可能编译 C/C++ 或其他语言扩展。

手机端任意 source build 需要：

- compiler 与 NDK；
- C/C++ headers、Python headers、CMake、Rust toolchain、`pkg-config`；
- Android system library mapping；
- build isolation 与 cache；
- 超时、取消、磁盘/CPU/RAM 限制；
- build backend arbitrary code 的 sandbox/secret isolation；
- reproducible failure and cleanup。

“技术上能把 compiler 打包进 APK”不代表产品上应该让任意项目在用户手机编译。**Recommendation：第一版默认 `SOURCE_BUILD_UNSUPPORTED`，source build 继续交给 External Provider 或受信任的预构建 pipeline。**

## Security Model

Dependency installation 本身就是执行第三方代码的攻击面，风险不止是下载 URL：

| 风险 | 第一版控制 |
|---|---|
| package provenance / dependency confusion | 受控 index policy；禁止未声明的额外 index/VCS/local path；记录 source 与 origin |
| HTTPS/TLS/CA | 只允许 HTTPS；显式 CA/trust policy；将 TLS/certificate failure 结构化记录 |
| archive tampering | 完整 SHA-256/hash manifest；哈希缺失或 mismatch 立即失败 |
| wheel tag spoofing | 解析文件名和内部 metadata；检查 Python/ABI/platform；不能只看扩展名 |
| archive path traversal | 拒绝绝对路径、`..`、非法/重复成员；canonical containment |
| symlink/hardlink escape | 第一版拒绝 symlink 或无法安全表达的 link entry；所有目标必须在 transaction root |
| malicious wheel | pure-Python 也会在 import 时执行 code；拒绝 `.pth`、native、安装脚本；尽量在 isolated process 验证 |
| build backend / setup code | 第一版不执行 source build、editable install 或 arbitrary backend |
| disk exhaustion | archive size、expanded size、file count、single-file、total environment quota |
| network/cache abuse | request/response timeout、partial download cleanup、cache quota、offline mode |
| cancellation | transaction state 可恢复；取消不切换 pointer；旧 READY 保留 |
| secret leakage | 不把 credentials、index token、project secrets 写进 package logs；日志 redaction |
| project source | 保持现有 bounded staging、NUL/path/symlink/size 检查；manifest parse 也有输入上限 |
| dependency metadata | 以 structured facts/manifest 为 state source，不从 pip stdout 猜 READY |

第一版默认应拒绝 source-build scripts，直到 SiftAlpha 具备更完整的 process/sandbox/credential isolation。即使只装 pure-Python wheel，也不能承诺 arbitrary untrusted Python code 的 sandbox；“pure”是 native compatibility boundary，不是 security sandbox。

同样，prepare 阶段的 import verification 不是无副作用的 metadata check：它会 populate `sys.modules`、执行第三方 top-level code，并可能污染 process-global state。alpha44 必须把“metadata/file-only verification”与“disposable isolated-process import verification”作为 design-review blocking choice；在选择前不能把长期 Embedded CPython process 中的 import 当成安全验证步骤。

## Atomic Environment Update

禁止在正在使用的 READY `site-packages` 目录中直接执行原地 install/upgrade。推荐流程：

```text
READY env-v1
   │ dependency fingerprint changed
   ▼
mark STALE / create transaction
   ▼
resolve or accept verified manifest
   ▼
download to cache temp → hash verify
   ▼
install to env-v2.tmp
   ▼
validate metadata, files, imports, quotas
   ▼
atomic switch project pointer to env-v2
   ▼
retain env-v1 until no Session references it
   ▼
garbage-collect old generation later
```

安装失败、取消、进程杀死或验证失败都必须留下 structured failure，并保持旧 READY environment 完整。正在运行的 Session 固定其 `environmentId`/generation；不能在 Session 中途看到半安装的目录。

## Cache / Upgrade / Cleanup Model

### 两层 cache

1. **Download Cache**：以 artifact SHA-256 content-addressed；可保存 wheel archive、hash、origin、metadata response 和 fetch time；允许项目间复用，但不作为 import path。
2. **Installed Environment**：项目/generation-owned `site-packages` tree + manifest；默认不可跨项目直接共享 mutable files。

pip 官方文档也区分 HTTP response cache 与 locally built wheel cache，并指出 cache 减少重复下载/构建但不等于完全不访问网络。[pip caching](https://pip.pypa.io/en/stable/topics/caching/)。SiftAlpha 可以吸收这一分层思想，但要加入 Android quota、partial cleanup 和项目 ownership。

第一版不必实现复杂 LRU，但 layout 必须允许以后增加：总大小 quota、单 artifact quota、按最后使用时间的 LRU、坏缓存隔离、离线命中、升级后旧 artifact GC。

## Environment Lifecycle

### 推荐的最小公开状态

建议对 M 暴露以下最少状态；细节通过 `phase` 与 structured facts 表达：

```text
MISSING
PREPARING
READY
STALE
FAILED
CLEANUP_PENDING
```

`PREPARING` 的 phase 可以是：

```text
DISCOVERING
RESOLVING
DOWNLOADING
VERIFYING
INSTALLING
VALIDATING
SWITCHING
```

不建议把每个 pip 内部阶段都升格为产品 state。核心状态必须来自 environment record、manifest、verification result 和 filesystem transaction marker，不能解析 pip stdout 来决定 READY。

### 推荐 EnvironmentRecord

至少包含：

- `environmentId`、`projectIdentity`、`generation`；
- CPython version、runtime provenance digest、CPython ABI、Android ABI/API policy；
- declaration source、canonical dependency fingerprint、resolved graph/manifest digest；
- state、phase、created/updated time、last-used time；
- site-packages path、manifest path、transaction path；
- installed package facts（normalized name/version/origin/hash/tags）；
- active session reference count；
- structured failure（kind、package、stage、detail、retryability、safe user message）。

## Failure Taxonomy

第一版至少需要以下稳定 failure kinds；它们必须由 R/Environment Manager 产生，不能由 M 通过 pip 文本猜：

```text
DEPENDENCY_DECLARATION_INVALID
MULTIPLE_DEPENDENCY_DECLARATIONS
DEPENDENCY_CONFLICT
NETWORK_UNAVAILABLE
TLS_CERTIFICATE_FAILURE
PACKAGE_NOT_FOUND
VERSION_UNSATISFIED
UNSUPPORTED_PYTHON_RUNTIME
UNSUPPORTED_WHEEL
UNSUPPORTED_NATIVE_PACKAGE
SOURCE_BUILD_UNSUPPORTED
HASH_MISMATCH
ARCHIVE_UNSAFE
DISK_FULL_OR_QUOTA
INSTALL_TIMEOUT
INSTALL_CANCELLED
PARTIAL_INSTALL_CLEANUP_FAILED
ENVIRONMENT_VERIFICATION_FAILED
IMPORT_VERIFICATION_FAILED
PROCESS_ENVIRONMENT_SWITCH_UNSUPPORTED
RUNTIME_BASE_INCOMPATIBLE
```

每个 failure 至少带 `stage`、`retryable`、`oldReadyEnvironmentPreserved`、`packageName/version`（如果有）、`detail`（已脱敏）和 Developer Mode 诊断字段。普通 UI 只消费稳定的 user-facing guidance；Developer Mode 将来可以显示完整 artifact/tag/DT_NEEDED/CA/cache facts。

## M ↔ R Contract

保持 canonical architecture：

```text
M 管
R 跑
X = M + R
```

### M 向 R 发送 `PrepareEnvironmentIntent`

建议字段：

- stable `ProjectIdentity`；
- Project Source Staging root 与 source provenance；
- dependency declaration source + bounded canonical bytes/digest；
- desired runtime（CPython version/ABI、Android ABI/API policy）；
- selected extras/profile（如果未来支持）；
- network/offline/cache policy；
- security policy（allowed tags、hash requirement、native/source policy）；
- transaction/generation request。

M 负责项目管理、SAF source staging、用户选择和 capability presentation；M 不应解析 pip stdout，不应把一个 shell marker 当成完整 Runtime state。

### R 返回 `EnvironmentFacts`

建议字段：

- `environmentId`、state、phase、generation；
- dependency fingerprint、resolved manifest digest；
- runtime compatibility facts；
- installed package facts、artifact provenance、cache hits；
- structured progress；
- structured failure；
- capabilities，例如 `PURE_PYTHON_IMPORTS`、`CONCURRENT_SESSION_UNSUPPORTED`、`NATIVE_PACKAGES_UNSUPPORTED`；
- `oldReadyEnvironmentPreserved`。

R 不依赖 Activity、Compose、Browser 或 Android UI。M 将结构化事实映射为当前/未来 Presentation；R 负责 environment/runtime correctness。

## Session 与 Environment 的关系

| 对象 | Identity | 典型生命周期 | STOP/CLEAN 语义 |
|---|---|---|---|
| Project Environment | `environmentId` + project + fingerprint + runtime contract | 多次顺序 Session | STOP 不删除；明确 clear environment 才删除 |
| Runtime Session | `sessionId` + generation | 一次运行 | STOP 结束本次运行，保留结果/diagnostics policy |
| Session Workspace | `sessionId` | 临时 | STOP/terminal 后可清理；不可作为 package store |
| Download Cache | artifact digest | App cache | 可按 quota/GC 清理；不得破坏 environment |

当 dependency fingerprint 改变：新 Session 不能绑定旧 READY environment；应先准备新 generation。当前 process-scoped CPython 的特殊限制见下一节：如果新 environment 需要不同 package versions，不能仅靠换 `sys.path`。

## Process-Scoped CPython Risks

### 当前风险结论

**会。当前 process-scoped CPython 会阻碍“不同项目 arbitrary dependency versions 的可靠隔离”。**

原因是：

1. 当前 `std::call_once` 初始化后不 finalize；所有 Session 共用同一个 interpreter。
2. Python import system 首先查 `sys.modules`；已加载的 package module 可能在新 environment path 生效前直接复用。[Python import system](https://docs.python.org/3.14/reference/import.html#the-module-cache)
3. 当前 cleanup 主要按 project staging path 删除 module，不知道未来各 environment 的 package ownership。
4. 删除 `sys.modules` 键也不保证模块对象、类型、回调、线程或其他引用消失。
5. native extension 的 `.so`、static/global state 和 linker lifetime 不能通过清空 `sys.modules` 保证卸载；CPython 文档也专门说明 extension module 的 repeated initialization、module instance isolation 与 single-phase legacy state 风险。[CPython extension modules](https://docs.python.org/3.14/c-api/extension-modules.html)

### 可选解决方向

| 方向 | 隔离强度 | 结论 |
|---|---|---|
| 只改 `sys.path` | 低 | 不足以隔离 `sys.modules` 或 native state |
| 清空 environment modules | 中低 | 仍有引用、finder cache、线程、extension lifetime 风险 |
| subinterpreter | 取决于所有 extension 是否支持隔离 | 不能把它当作 arbitrary third-party native package 的自动解决方案 |
| 每个 environment 一个 OS process | 高；进程退出提供最清晰边界 | **长期推荐**，尤其是 native/不同版本 |
| 严格 process lock | 可预测但能力较窄 | alpha44 可作为临时 gate：同 process 只允许一个 project-owned `environmentId` / Project Environment Identity |

**Recommendation：** 在 process-per-environment 还没有实现前，Embedded R 不应承诺 arbitrary version switching；为不同 environment 请求返回 `PROCESS_ENVIRONMENT_SWITCH_UNSUPPORTED`，并由 M 让用户明确选择 External Provider。不能静默在当前 app process 中加载第二个版本。

### Process gate 绑定 environment identity，而不是 fingerprint

当前 process 的 loaded-environment binding 至少应记录 project-owned `environmentId` / Project Environment Identity：

```text
loaded environment A
  → reject environment B
  → even when dependencyFingerprint(A) == dependencyFingerprint(B)
```

原因是相同 fingerprint 只说明 dependency content 兼容，不会清空或隔离 A 已经进入 `sys.modules` 的 module object、global state、callbacks、logging handlers、threads、monkey patches 或其他 retained references。相同 environment 在多个顺序 Session 中可以复用 environment files，但第三方 module runtime state 仍可能跨 Session 保留；environmentId binding 不是 clean-session semantics。

### Import verification 的进程污染风险

如果 environment preparation 阶段执行 `import package`，该动作会：

- populate `sys.modules`；
- execute package top-level code；
- potentially mutate process-global state。

因此 alpha44 不能默认把长期 process 内的 import 当作无副作用验证。必须在 design review 中二选一：metadata/file verification only，或在 disposable isolated process 中做 import verification。本审计记录该阻塞点，但不实现任一方案。

## Native Extension 的进程级污染

native extension 即使删除 `sys.modules` 项，也可能：

- 仍有 Python 对象、class/type、callback、background thread 引用；
- `.so` 仍由 dynamic linker 保持映射；
- C/C++/Rust static/global state 仍存活；
- 单阶段 CPython extension 在重复初始化时复用保存的内容或表现出 singleton-like behavior；
- 不同 package version 若导出同名 extension module，可能出现旧代码、旧符号或旧 ABI 互相影响。

所以当前 single-process Embedded R 的安全默认必须是：**第一阶段不支持 native package；即使未来支持，也优先用独立 process，而不是承诺 module unload。**

## Compatibility Test Strategy

以下仅是未来实现的测试设计，本轮不创建 fixture、不实现 installer：

| Fixture | 场景 | 必须验证 |
|---|---|---|
| A | stdlib-only project | 无 declaration 时不误创建第三方 environment；base stdlib 可运行 |
| B | 一个 pure-Python dependency | wheel tag/hash/archive/metadata 验证，site-packages import 成功 |
| C | transitive pure-Python dependencies | 完整 resolved graph 安装；缺少 transitive node 不能静默成功 |
| D | unsupported native dependency | 明确 `UNSUPPORTED_NATIVE_PACKAGE`，不 source-build、不 silent fallback |
| E | requirements 改变 | 旧 READY → STALE；新 generation 构建；失败时旧 READY 保持 |
| F | 相同 dependency fingerprint | 不重新安装；environment reuse facts 可审计 |
| G | Project A/B 同 package 不同版本 | 只有在 process isolation contract 具备时 PASS；否则 deterministic `PROCESS_ENVIRONMENT_SWITCH_UNSUPPORTED`，不能假 PASS |
| H | STOP → re-entry | Session workspace 可清理/重建；environment 不损坏，旧 READY 仍可复用 |
| I | installation failure | partial generation 清理；旧 READY pointer、manifest、site-packages 完整 |
| J | dependency cache reuse | second project 可复用 verified download artifact，但不共享 mutable site-packages |

额外必须覆盖：TLS/CA failure、offline cache hit/miss、hash mismatch、archive traversal、disk quota、timeout/cancel、API floor mismatch、Python version mismatch、project source symlink/path escape、crash recovery、cache corruption、`.pth`/native payload rejection。

## Termux Migration Strategy

Termux + PRoot + Ubuntu + Python 仍是当前 production External Runtime Provider，不能因 Embedded R audit 删除或改变它。

推荐迁移策略：

1. Embedded R 能力明确声明支持范围；范围内项目使用 Embedded R。
2. Embedded R 不支持的 declaration/native/source/build/API/process 情况，返回结构化 capability boundary。
3. 用户可明确选择 External Provider；UI 显示“为什么不能在 Embedded R 运行”。
4. 不默认 silent automatic provider fallback；如果未来提供自动 fallback，必须是用户/项目 policy 明确打开、可观察、可诊断、可回退的选择。

这样更符合 predictability、security、UX 和 debuggability：用户不会看到一个看似 Embedded 的启动，却实际悄悄进入另一套 Termux 运行路径。

## Recommended Architecture

### 推荐分层

```text
M: Project Identity + SAF Source Staging + declaration discovery
        │ PrepareEnvironmentIntent
        ▼
R Environment Manager:
  canonical declaration → resolved manifest → verified artifacts
  → transaction generation → package facts → EnvironmentFacts
        │ environmentId + sys.path contract
        ▼
R Runtime Session:
  fixed session/generation/cwd/tmp + one compatible environment
        │ structured snapshot
        ▼
M Presentation / existing observation / result routing
```

### 推荐实现原则

- Runtime Base 只读、版本化、可审计；不向其中写 package。
- Project Source Staging 只放项目源码，不放 cache 和 installed packages。
- Project Environment 是 project-owned generation；不直接共享 mutable site-packages。
- Download Cache content-addressed、可验证、可清理；不是 import path。
- Environment Manager 产生 structured state/failure/package facts；不要求 M 解析 pip 输出。
- 默认 pure-Python wheel；hash 必须完整；source/native/editable/build backend 默认拒绝。
- 通过 controlled `sys.path` 注入 environment；同时处理 `sys.modules`/finder/process boundary。
- environment update 采用 build → verify → atomic switch；Session 固定 environment generation。
- process-scoped CPython 在同一个 app process 中只允许一个 project-owned `environmentId`；即使两个 environment 的 dependency fingerprint 相同，也必须拒绝切换。不同 environment 需要新的 OS process 或明确 External Provider；复用同一个 environment 跨顺序 Session 仍不保证 clean-session semantics。

## Minimal alpha44 Proposal

根据当前源码事实，最小、可真机验收的 alpha44 研究后范围应是：

### Pure-Python Project Environment v1

1. M 识别根目录 `requirements.txt` 或静态 `pyproject.toml` `[project].dependencies`；两者同时存在时报告 conflict，不合并。
2. 只接受受控依赖声明子集：明确版本/可解析输入、无 VCS/local/editable/source-build 选项；未锁定或无法预解析时报告 structured failure。
3. 使用预解析/可信 manifest 或等价的严格锁定输入；不把手机端通用 pip resolver 作为 alpha44 必需条件。
4. 只安装经过 hash、tag、archive、metadata 和 pure-Python payload 验证的 wheel。
5. 每个项目有独立 logical environment、project-owned `environmentId`、dependency fingerprint、manifest、generation、READY/STALE/FAILED 等状态。
6. Download Cache 与 Installed Environment 分离；相同 artifact 可以跨项目复用，mutable site-packages 不直接共享。
7. 安装失败、取消、quota、hash mismatch 保持旧 READY environment；验证完成后 atomic switch。
8. 返回结构化 package facts、environment facts 和 failure taxonomy；不解析 pip stdout。
9. 对当前 process-scoped CPython 增加明确 capability gate：同 process 不切换不同 project-owned `environmentId`；即使 fingerprint 相同也 deterministic reject 或走 External Provider，不能宣称完整 isolation。
10. 在 alpha44 implementation 前冻结两个 blocking decisions：resolver authority（谁产出 complete graph、exact versions/artifacts 与 SHA-256）以及 import verification（metadata/file-only，或 disposable isolated process）；设备端不能在未决时悄悄运行通用 pip resolver 或长期 process 内 import。
11. 真实 Android 验收覆盖 Fixture A–J 中 alpha44 声明支持的范围，并特别验证 offline/cache、hash、quota、旧环境回滚和 re-entry。

### 明确不纳入 alpha44 最小范围

- arbitrary native wheels、C/C++/Rust extensions、`abi3` native package 的默认支持；
- sdist、`setup.py`、PEP 517 source build、build isolation、compiler/NDK/Rust/CMake toolchain；
- 传统 full `venv` 与复制 Python executable；
- Embedded CPython 直接执行 arbitrary `pip` 作为默认 installer；
- editable install、console-script launch contract、`.pth` 执行；
- concurrent Embedded sessions；
- 当前 process 内 arbitrary dependency-version switching；
- Web/Browser/Automatic Observation 集成；
- UI redesign、Developer Mode implementation、result persistence；
- 修改 alpha43 production behavior、version、workflow 或 External Provider 默认路径。

这个范围是对用户候选方向的技术收敛，而不是机械接受：它保留了 pure-Python environment 的可验收价值，同时不掩盖当前 CPython process boundary 尚未解决的隔离限制。

## Explicit Out of Scope

本审计不实现以下任何内容：

- `pip` bootstrap、dependency installer、resolver、wheel extractor；
- `venv`、site-packages、environment metadata 或 runtime code；
- native/CMake/NDK/CPython preparation 变化；
- UI、M/R contract code、Activity、Web、Rich Result、Automatic Observation；
- tests/fixtures、workflow、APK、版本升级；
- 删除或改变 Termux/PRoot/Ubuntu External Provider；
- alpha44 production code。

## Open Questions

1. **alpha44 blocking decision：** 依赖解析最终由受信任 CI/开发机、受信任服务、显式 SiftAlpha lock manifest，还是 fully pinned/hash-complete input 提供？在决定前不得把设备端变成隐式通用 resolver。
2. 是否要定义 SiftAlpha-specific lock/manifest，还是采用未来 PyPA lockfile 规范的受限子集？
3. requirements 与 pyproject 同时存在时，是否长期保持 hard conflict，还是增加显式 project policy？
4. app-private 环境的大小、文件数、单包和总缓存 quota 分别是多少？
5. Android CA bundle 与 index trust root 如何随 app/runtime 更新？
6. 是否必须在 alpha44 就采用 per-environment OS process，还是先用绑定 `environmentId` 的 process lock + External Provider boundary？即使先不实现 process isolation，也不能以相同 fingerprint 代替 environment identity。
7. 纯 Python package 的 `.pth`、data files、console scripts 是否永远拒绝，还是建立可审计 allowlist？
8. project source 是否允许作为本地 package 参与安装？如果允许，如何避免 editable/source-build 语义？
9. **alpha44 blocking decision：** import verification 采用 metadata/file-only，还是 disposable isolated process？不能把长期 Embedded process 内的 import 当成无副作用验证。
10. 结果、environment manifest 和 failure facts 是否需要超出当前 Activity-lifetime cache 做持久化？
11. 未来 native wheel 的 Android build provenance、API floor 与 system-library ownership 由谁签发？

## Risk Register

| 风险 | 严重度 | 当前判断 | 推荐缓解 |
|---|---:|---|---|
| process-scoped module/native leakage | 高 | 当前真实存在 | process-per-environment 或明确拒绝 version switch |
| direct pip arbitrary code/build execution | 高 | pip 默认模型不适合 app | managed verified wheels；source build off |
| Android wheel/libc/tag mismatch | 高 | manylinux/musllinux 不能代替 Android | Android build provenance + tag/API/DT_NEEDED checks |
| non-atomic install破坏 READY | 高 | External adapter 当前偏原地安装 | generation transaction + atomic pointer |
| dependency declaration ambiguity | 中高 | requirements/pyproject 可冲突 | one authoritative source or hard conflict |
| TLS/CA/offline behavior | 中高 | 当前 Embedded 没有 pip network contract | explicit trust/cache/offline states |
| disk/quota/partial cleanup | 中高 | mobile storage有限 | per-file/total quota + crash-safe cleanup |
| pure-Python package import side effects | 中高 | pure != sandbox | isolated validation process、logs redaction、policy |
| cache poisoning/corruption | 中高 | cross-project cache会扩大影响面 | content hash、quarantine、manifest provenance |
| app API floor/device matrix | 中 | current app minSdk=26不代表包兼容 | record target API and real-device matrix |
| silent Termux fallback | 中 | debug/UX 不可预测 | explicit user/policy choice and structured reason |

## Future Files Likely To Change

以下只是未来实现时可能涉及的文件/新组件清单，本轮没有修改它们：

- `app/src/main/java/com/siftalpha/studio/runtime/EmbeddedPythonCapabilityRouting.kt`：capability gate、environment compatibility；
- `app/src/main/java/com/siftalpha/studio/runtime/ProjectRuntimeController.kt`：M → R prepare/start contract；
- `app/src/main/java/com/siftalpha/studio/siftalphax/EmbeddedPythonFiles.kt`：runtime/environment/session storage separation；
- `app/src/main/java/com/siftalpha/studio/siftalphax/EmbeddedPythonExecutionSpec.kt`：environment identity binding；
- `app/src/main/java/com/siftalpha/studio/siftalphax/EmbeddedPythonSession.kt`：process lock 或 process boundary；
- `app/src/main/java/com/siftalpha/studio/siftalphax/EmbeddedPythonResult.kt`：structured environment facts；
- `app/src/main/cpp/EmbeddedPythonNative.cpp`：explicit sys.path/process/environment contract；
- new `EmbeddedPythonEnvironment*`, declaration parser, manifest verifier, wheel verifier、transaction store；
- `app/src/test/**` 中的 environment identity、wheel/security、atomic update、process-isolation fixtures；
- 只有在后续决定改变 Runtime Base provenance 时，才重新审查 `tools/prepare_embedded_cpython_android.sh` 与 `app/build.gradle.kts`。

本清单不是变更承诺；alpha43 本轮没有触碰这些 production files。

## Acceptance Strategy

未来 implementation 应分四层验收：

### 1. Contract/unit

- declaration canonicalization、both-source conflict、PEP 508 subset；
- identity/fingerprint stability and stale detection；
- wheel tag/hash/archive/path/metadata validation；
- environment state/failure serialization；
- atomic pointer update and old READY preservation；
- cleanup/quota/cancel/crash recovery；
- sys.path ordering and shadowing tests；
- process environment lock/isolation behavior。

### 2. Build/compatibility

- current CPython 3.14.7 provenance；
- `arm64-v8a`、Android API 26+ target device；
- base stdlib/lib-dynload 与 pure wheel imports；
- no Termux/PRoot dependency for the Embedded path；
- explicit rejection of native/sdist/build backend/unsupported API artifacts。

### 3. Real-device matrix

- A–C success and transitive dependency facts；
- D deterministic native rejection；
- E stale/rebuild and I rollback；
- F reuse without reinstall；
- G two projects/version isolation only after process boundary；
- H STOP/re-entry；
- J cache reuse without shared mutable tree；
- offline, TLS failure, quota, cancellation and cache corruption。

### 4. Product/provider boundary

- Embedded-supported project stays Embedded；
- unsupported project receives clear capability explanation；
- External Provider remains available and explicit；
- no silent provider switch；
- M receives structured facts rather than pip/log parsing。

## Final Ten Conclusions

### 1. 是否应该使用传统 venv？

**结论：不应作为当前 Embedded R 核心。** 当前是 process-scoped embedded interpreter，`venv`/`ensurepip` 已移除且没有 environment-specific executable。用 logical environment + site-packages + controlled `sys.path` 更贴合 Android；限制是依赖 `sys.prefix`/`sys.executable`/console scripts 的包需要拒绝或另一路径。

### 2. 是否应该在 Embedded R 中直接运行 pip？

**结论：技术上可行，但第一阶段不应直接运行。** 需要 bootstrap、CA/TLS、network/offline、temp/quota、subprocess、build isolation、atomic transaction 与安全 policy；pip 默认还会运行 distribution/build code。第一阶段用 managed wheel installer；未来可研究受控 pip adapter。

### 3. 第一阶段依赖支持是否应该限制为 pure-Python？

**结论：是。** `py3-none-any`/等价纯 Python wheel 能避开 Android bionic/native linker/NDK/source build 边界，同时仍支持 transitive graph。限制是 pure Python 仍可能依赖 OS 命令、`.pth`、console script 或执行恶意代码，不能当 sandbox。

### 4. site-packages 应该放在哪里？

**结论：放在 app-private、project-owned、environment-generation-owned 的目录，例如 `files/siftalphax/environments/<project-id>/<environment-id>/site-packages`。** 不放 Runtime Base、Project Source Staging、Download Cache 或 Session Workspace；限制是目录隔离仍不能单独解决同一 process 的 `sys.modules` 污染。

### 5. 不同 Project 是否应拥有独立 environment？

**结论：应当。** 这是避免同名 package 不同版本互相污染的最清晰 ownership model；可跨项目复用 immutable wheel artifact。限制是 storage 增长和当前 process-scoped interpreter 仍可能阻止同时/顺序切换不同 environment。

### 6. environment 是否应该跨 Session 复用？

**结论：应当在 project-owned `environmentId`、fingerprint、runtime compatibility、verification 与 process policy 都匹配时复用。** Environment 与 Session 分离能避免每次运行重装；STOP 不删除 environment。限制是已加载 module/native state 可能要求新 process，不能只凭 READY marker 或相同 fingerprint 复用；同一 environment 跨顺序 Session 也不保证 clean-session semantics。

### 7. 依赖改变后如何安全升级？

**结论：标旧环境 STALE，构建新 generation，验证后 atomic switch；失败保持旧 READY。** Session 固定 environment generation，旧 generation 等无引用后清理。限制是需要 transaction metadata、crash recovery 和磁盘双份空间。

### 8. native wheels 第一阶段应该如何处理？

**结论：明确不支持，返回 `UNSUPPORTED_NATIVE_PACKAGE`。** Android bionic、API floor、DT_NEEDED、linker namespace、CPython ABI 不能由 manylinux/musllinux filename 证明。限制是大量生态包必须走 External Provider 或未来专门的 Android wheel pipeline。

### 9. process-scoped CPython 会不会阻碍不同项目依赖版本隔离？

**结论：会。** `sys.modules`、finder/path state、module references、native `.so` 与 static/global state 使 `sys.path` 注入不够。限制和处理方案是：在 process-per-environment 到来前，process gate 绑定一个 project-owned `environmentId`；即使 fingerprint 相同也拒绝切换到另一个 environment，或走 deterministic reject/External fallback；不能宣称 arbitrary version isolation。即使复用同一个 environment，跨 Session 的 third-party runtime state 仍可能保留。

### 10. alpha44 最小实现到底应该是什么？

**结论：Pure-Python Project Environment v1。** 支持受控 requirements 或静态 pyproject 单一来源、预解析/哈希 wheel manifest、per-project logical environment、reuse/stale、structured state/failure、atomic generation、safe cleanup、cache separation 与绑定 `environmentId` 的 process gate。alpha44 implementation 前还必须决定 resolver authority 以及 import verification 是否只做 metadata/file check 或放入 disposable isolated process。明确不包括 direct pip default、venv、native wheels、source builds、concurrent sessions、Web integration、UI redesign 和 process内 arbitrary version switch。

## Authoritative References

以下是本审计实际查阅的上游资料；仓库源码事实以审计起始 HEAD 为准，下面的建议不是这些资料直接规定的产品结论。

1. [Python 3.14.7 — Using Python on Android](https://docs.python.org/3.14/using/android.html) — Python 3.14.7 documentation；页面标注 2026-09-16 更新。确认 Android 主要使用 embedded mode、打包 libpython/stdlib/code，以及 Android wheel build 方向。
2. [Python 3.14.7 — Embedding Python in Another Application](https://docs.python.org/3.14/extending/embedding.html) — CPython embedding API 背景。
3. [Python 3.14.7 — Python Initialization Configuration](https://docs.python.org/3.14/c-api/init_config.html) — `PyConfig`、`use_environment`、`user_site_directory`、`module_search_paths` 与 isolated configuration。
4. [Python 3.14.7 — The import system](https://docs.python.org/3.14/reference/import.html) — `sys.modules` module cache、`sys.path`、finder/importer cache。
5. [Python 3.14.7 — Defining extension modules](https://docs.python.org/3.14/c-api/extension-modules.html) — extension module instances、repeated initialization、multi-phase/single-phase state。
6. [PEP 405 — Python Virtual Environments](https://peps.python.org/pep-0405/) — traditional venv 的 Python binary、prefix 与 site directory model。
7. [PEP 621 — Storing project metadata in pyproject.toml](https://peps.python.org/pep-0621/) — `[project].dependencies` 与 static/dynamic metadata。
8. [PyPA — `pyproject.toml` specification](https://packaging.python.org/en/latest/specifications/pyproject-toml/) — `[build-system]` 与 `[project]` 的职责边界。
9. [PyPA — Platform compatibility tags](https://packaging.python.org/en/latest/specifications/platform-compatibility-tags/) — `{python}-{abi}-{platform}`、`cp`、`abi3`、manylinux/glibc、musllinux/musl，以及 `android_apilevel_abi`（例如 `android_27_arm64_v8a`）语义。
10. [PyPA — Binary distribution format](https://packaging.python.org/en/latest/specifications/binary-distribution-format/) — wheel archive/metadata 规范。
11. [pip 26.2.1 — Secure installs](https://pip.pypa.io/en/stable/topics/secure-installs/) — hash-checking、`--only-binary :all:` 与 arbitrary code 风险。
12. [pip 26.2.1 — Build System Interface](https://pip.pypa.io/en/stable/reference/build-system/) — build isolation、build dependencies、backend 与 extension build。
13. [pip 26.2.1 — Caching](https://pip.pypa.io/en/stable/topics/caching/) — HTTP response 与 locally-built wheel cache 的职责。
14. [pip 26.2.1 — Dependency Resolution](https://pip.pypa.io/en/stable/topics/dependency-resolution/) — transitive dependency graph 与 backtracking。
15. [Android NDK — ABIs](https://developer.android.com/ndk/guides/abis) — `arm64-v8a` / AArch64 ABI。
16. [AOSP — Linker namespace](https://source.android.com/docs/core/architecture/vndk/linker-namespace) — `DT_NEEDED`、`dlopen`、search/permitted paths 与 linker namespace isolation。
17. [cibuildwheel documentation](https://cibuildwheel.pypa.io/en/stable/) — 当前工具对 CPython 3.14 Android wheel build/test 的支持边界；用于说明 Android native wheel 需要专门构建/测试，不作为 SiftAlpha 的自动兼容证明。

## Audit Closure

本审计的下一项正式工作仍然是：先基于本文件的 contract 再进行 Pure-Python Environment v1 的设计评审；不能把本文件当作已经实现的依赖安装能力。

**No alpha44 production implementation was started.**

## r46 compatibility closure

The earlier persistence model already allowed repeated runs to reuse a prepared project environment. r46 therefore narrows the remaining contract to environment correctness across project re-prepare and Runtime changes.

- Embedded CPython manifests now carry a concrete Runtime Identity. Environment schema advances to v3, so older bindings without a compatible Runtime identity are not silently reused.
- Internal Alpine records both the actual Python full version and an identity derived from the pinned Alpine/rootfs plus that Python version. Project `requires-python` also participates in the dependency fingerprint.
- External Python preparation is transactional at the project-venv level: build and validate a fresh candidate, then replace the previous venv. A failed prepare leaves the previous venv on disk, but its old readiness marker cannot satisfy changed project/runtime evidence.
- External ready markers bind the prepared Python version and the project's declared Python requirement.
- Runtime version selection is constrained by actually available Runtime implementations. r46 does not treat a newer Python as automatically compatible with a project requesting an older major/minor version, and it does not claim Python 2 support when no Python 2 Runtime is packaged.
- Future Python Runtime providers can participate in the same compatibility-selection contract without changing the project-environment semantics.

## r46.1 legacy marker migration closure

r46 correctly strengthened environment compatibility checks but initially treated missing new metadata as equivalent to an incompatible Runtime. Real-device overwrite testing exposed that this invalidated every pre-r46 prepared environment.

r46.1 separates these cases:

- Missing new metadata + provable legacy compatibility -> migrate metadata in place and reuse.
- Dependency evidence changed -> require Prepare.
- Runtime/version/base RootFS changed or cannot be proven -> require Prepare.
- Project requires-python is incompatible with the proven legacy Python version -> require Prepare.

Provider evidence:
- Embedded CPython v2 manifests are accepted only for the known CPython 3.14.7 Android arm64-v8a Runtime identity and matching project/source fingerprints.
- Internal Alpine requires current RootFS asset identity, matching pre-r46 dependency fingerprint, valid legacy pyvenv.cfg version evidence and Python requirement compatibility.
- External Python requires matching dependency hash plus pyvenv.cfg creation-version equality with the current venv interpreter, then validates any declared Python specifier before upgrading the ready marker.

This preserves r46 Runtime safety while preventing App-only upgrades from causing unnecessary dependency rebuilds.

