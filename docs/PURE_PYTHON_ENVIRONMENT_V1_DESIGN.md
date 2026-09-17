# SiftAlpha Pure-Python Environment v1 Design Contract

## 文档性质与冻结基线

本文是 SiftAlpha Embedded R 在 alpha44 implementation 之前的 Design Review + Contract Freeze。它把 R Environment & Dependency Model Architecture Audit 中仍未冻结的 blocking decisions 收敛为后续实现可直接使用的设计合同。

本轮只写文档，不实现：

- pylock.toml parser；
- dependency resolver 或 Locker；
- wheel downloader、cache 或 installer；
- environment filesystem store；
- process isolation、IPC 或 sys.path injection；
- UI、M ↔ R production wiring、测试或版本升级。

本文不改变 alpha43 production behavior。

| 项目 | 冻结值 |
|---|---|
| Repository | kuashan/siftalpha-one |
| Branch | codex/siftalpha-x-embedded-cpython-spike |
| Design-review HEAD | 9368e79670c05c540d285d31b2a57263784fb613 |
| versionCode | 119 |
| versionName | 0.8.0-alpha43 |
| CI baseline | Run #125, Run ID 35249201888, completed / success |
| Current product architecture | M 管 / R 跑 / X = M + R |
| External Runtime Provider | Termux + PRoot + Ubuntu + Python |

文中标记含义：

- **Verified fact**：由当前仓库或权威上游资料确认。
- **Design constraint**：已经冻结，后续实现不得绕开。
- **Recommendation**：本文做出的产品/工程选择，不是当前已经实现的能力。

## Design Goals

Pure-Python Environment v1 的目标，是让 Embedded R 在 Android app-private storage 中，为一个项目准备可复用、可审计、可回滚的纯 Python 依赖环境，同时保持 Runtime Base、项目源码和 Session 数据互不污染。

必须满足：

1. 安装输入是已经解析完成的完整 dependency graph，而不是设备端临时猜测。
2. 每个 Project 拥有自己的 logical environment 和 ownership。
3. 跨 Project 只能复用 immutable verified artifact，不共享 mutable site-packages。
4. environmentId、generation、dependencyFingerprint 与 process binding 不混用。
5. 环境升级采用新 generation → verify → atomic switch。
6. 失败、取消、磁盘不足或 hash 错误不能破坏旧 READY environment。
7. 不在长期 Embedded CPython process 中执行 prepare-time third-party import。
8. 不把 native wheel、source build、editable install、传统完整 venv 或 arbitrary pip 当成 v1 能力。
9. Embedded 不支持时必须返回明确 capability failure，并由 M 让用户显式选择 External Provider。
10. 设计为未来 Android native wheel、process recovery 和 Web/Automatic Observation 集成保留扩展位置，但不提前宣称这些能力已经存在。

## Non-goals

本文不承诺：

- 任意 PyPI package 都能在 Android 上运行；
- requirements.txt 或 pyproject.toml 可以直接被 Embedded R 安装；
- 设备端拥有通用 dependency resolver；
- pure Python 是安全沙箱；
- 同一 process 可以安全切换任意不同项目或不同依赖版本；
- STOP 会删除 environment；
- Android 进程永远不会被系统回收；
- alpha44 会同时交付完整 Pure-Python Environment v1。

## Inherited Audit Constraints

以下结论从前一份审计继承，不在本轮重新争论：

| 已冻结结论 | 对本设计的直接影响 |
|---|---|
| 不采用传统 venv 作为 Embedded R 核心 | 使用 logical environment + project-owned site-packages，不复制 Python executable |
| 不同 Project 拥有不同 environmentId | 相同 dependency fingerprint 也不能共享 environment binding |
| Dependency Fingerprint 与 Environment Identity 分离 | fingerprint 表示依赖内容兼容性，environment identity 表示项目拥有的运行环境合同 |
| mutable site-packages 不跨 Project 共享 | 跨 Project 只复用 content-addressed verified artifact |
| pure-Python first | 后续 Environment v1 只讨论 none ABI + any platform 的纯 Python wheel |
| native wheels/source builds/sdist/editable/direct arbitrary pip 禁止 | 返回结构化 unsupported failure，不 silent fallback |
| generation atomic update | 新 generation 完整验证后才替换 READY pointer |
| process gate 不能只看 fingerprint | 绑定具体 project-owned environment，最终绑定 immutable ProcessBindingId |
| sequential Session 不保证 clean-session semantics | 同一 process 中已加载的 module/global state 可能跨 Session 留存 |
| Termux External Provider 保留 | Embedded capability failure 后由用户/策略明确选择 External Runtime，不静默改 provider |

## M Stability Rule

当前阶段的主要开发目标是 R（Runtime System）。除非存在明确且不可避免的技术必要性，后续实现不得修改 M（Management System）的既有 project-management semantics、用户工作流、项目导入行为、普通用户 UI 或 alpha43 已验收的 Runtime presentation behavior。实现更方便、顺便重构或代码更整洁，都不是修改 M 的充分理由。

如果未来任务发现必须修改 M，必须先停止涉及 M 的实现并报告：

1. 为什么必须修改 M；
2. 不修改会阻塞什么；
3. 具体涉及哪些 M 文件和行为；
4. 是否存在只修改 R 或 M ↔ R Contract 的替代方案；
5. 对现有 alpha43 已验收行为的影响。

在获得明确批准前不得实施 M 修改。本轮只有 docs/** 变化，因此没有修改 M production code。

## Current Embedded R Facts

以下是 alpha43 当前实现边界，属于 Verified fact，不是本轮新能力：

- Embedded CPython 为 3.14.7，Android target 为 arm64-v8a / aarch64-linux-android。
- native 侧使用 process-scoped initialization，当前通过 std::call_once 初始化；不调用 Py_FinalizeEx。
- 当前只有一个 active Embedded Session，terminal snapshot 在 app process 生命周期内保留。
- PyConfig 已采用 use_environment = 0 和 user_site_directory = 0 的隔离方向。
- preparation script 已删除 venv 与 ensurepip；当前没有 Embedded pip bootstrap、third-party installer 或 project site-packages contract。
- 当前 Runtime Base 路径是 files/siftalphax/python；当前 Project Source Staging 使用 files/siftalphax/projects/<sessionId> 形态。

这些事实决定了 Process Isolation 必须先成为 foundation；它们不会在本文档中被假设为已经完成的 Environment v1 能力。

## Final Resolver Decision

### 决定

**Pure-Python Environment v1（在 Process-Isolated Foundation 之后实施）的 authoritative install input 是标准 pylock.toml，并且该环境 slice 只消费已经存在的 lock file。alpha44 foundation 本身不安装 third-party dependency。**

责任分工冻结为：

    requirements.txt / pyproject.toml
            │
            ▼
    Trusted Locker（在设备外运行）
            │  complete graph + exact artifacts + hashes
            ▼
    pylock.toml
            │  authoritative install input
            ▼
    SiftAlpha-managed Installer（设备端只验证和安装）

设备端不进行 dependency resolution，不 backtracking，不查询 index 来补缺失的 transitive dependency，也不根据 import 名字猜包。

### 为什么选择标准 pylock.toml

[PEP 751](https://peps.python.org/pep-0751/) 当前状态为 Final。它定义了用于可重复安装的 lock file，并明确区分 Locker 与 Installer：Locker 写入已解析结果，Installer 消费 lock file，安装时不需要重新进行 dependency resolution。当前 PyPA 已发布对应的 [pylock.toml specification](https://packaging.python.org/en/latest/specifications/pylock-toml/)。

该标准能够表达后续 Pure-Python Environment v1 需要的核心事实：

- lock-version；
- requires-python；
- environments；
- extras、dependency-groups、default-groups；
- created-by；
- packages 的 name、version、marker、requires-python；
- 一个 package 的多个 wheel candidate；
- 每个 wheel 的 URL/path、size、hash；
- package dependency edges 与未来的多平台扩展。

这比自定义 manifest 更容易与 Python 生态工具互操作，也比 hash-complete requirements.txt 更适合表达完整 graph、多 wheel 选择和 environment markers。

### Resolver authority 的具体合同

alpha44 不规定一个必须运行在手机上的 resolver service。它规定一个明确的 Trusted Locker role：

- Locker 在受信任 CI、开发机或其他受信任构建环境运行；
- Locker 负责读取 top-level declarations、执行 resolution，并生成标准 pylock.toml；
- Locker 必须写出完整 transitive graph、exact versions、exact wheel candidates、environment markers、requires-python 和 artifact hashes；
- 生成的 lock 必须与项目一起交付，或由项目导入流程明确提供；
- Embedded R 只验证和安装 lock 中已经指定的 artifact；
- 没有可信 lock 时，Embedded 返回 LOCK_MISSING，不偷偷改为设备端 pip resolver。

alpha44 不实现 Locker。是否由未来 SiftAlpha CI 提供官方 Locker，不改变“设备端不解析”的合同。

### 方案比较

| 方案 | 可重复性 | Android 设备复杂度 | 标准互操作性 | alpha44 决定 |
|---|---|---:|---:|---|
| A. SiftAlpha 自定义 lock manifest | 可控，但引入私有格式 | 中 | 低 | 不选作 authoritative format |
| B. 标准 pylock.toml | 可表达完整 graph、markers、多 wheel、hash | 低到中 | 高 | **选择** |
| C. hash-complete requirements.txt | 可固定文件，但 graph/markers/多平台语义不足 | 中 | 中 | 作为 Locker 输入/导出，不作为最终输入 |
| D. 设备端 resolver | 生态覆盖高，但网络、资源、代码执行和可重复性风险最大 | 高 | 表面高、行为难控 | 明确禁止 |
| E. Trusted remote resolution service | 能集中维护 graph，但增加在线依赖、服务信任和可用性风险 | 中 | 取决于服务输出 | 不作为 alpha44 必需路径；可作为未来 Locker |

## pylock.toml Decision

### Discovery priority

Pure-Python Environment v1 只自动发现项目根目录的单一 pylock.toml。命名变体 pylock.<name>.toml、多个 lock file 或嵌套 lock file 不在 v1 自动选择范围内，避免“选哪个 lock”变成隐式 resolver。

角色冻结为：

| 文件 | v1 角色 |
|---|---|
| pylock.toml | authoritative resolved install input |
| requirements.txt | human/project dependency declaration；只用于 provenance/staleness diagnostics，不直接安装 |
| pyproject.toml 的 [project].dependencies | project metadata dependency declaration；只用于 provenance/staleness diagnostics，不直接安装 |
| pyproject.toml 的 [build-system] | source-build metadata；v1 不执行 |
| import statements | diagnostics only；不推导 PyPI package |

### Lock 与 source declaration 同时存在

有 pylock.toml 时，不能再把它与 requirements.txt / pyproject.toml 视为两个并列 install inputs，也不能返回旧式 MULTIPLE_DEPENDENCY_DECLARATIONS。

规则是：

1. pylock.toml 永远拥有安装优先级。
2. source declarations 不参与设备端解析，不会偷偷合并进 lock。
3. 可选的 SiftAlpha provenance extension 只能服务于诊断和 provenance 展示；它不是 v1 的安装前置条件：

       [tool.siftalpha]
       contract = "pure-python-environment-v1"
       source-selection = "requirements.txt"
       source-declaration-digest = "sha256:<hex>"

4. source-selection 可以记录 Locker 实际使用的是哪个 declaration；当 requirements.txt 与 pyproject.toml 同时存在时，Locker 不应隐式合并，但该记录不是设备端的安装条件。
5. source-declaration-digest 可以是选定 declaration 的 deterministic canonical digest，用于 provenance display；相对路径、UTF-8 内容、LF 归一化和无 BOM 规则属于未来独立 provenance contract 的设计，不是 v1 installer 的输入。
6. 没有任何 source declaration 或 [tool.siftalpha] 时，lock 仍可以作为 self-contained authoritative input。
7. [tool] table 遵循 PyPA 规范：其中的数据必须是 disposable，且不得影响安装。缺少 [tool.siftalpha]、source-selection 不存在、source-declaration-digest 不匹配或 source declaration 与 lock 的 provenance 不一致，都不能使一个其他方面有效的 pylock.toml 变为 LOCK_INVALID、LOCK_MISSING 或安装阻塞。
8. v1 不承担 source declaration → lock staleness 的强制检测。未来若要建立严格 provenance contract，必须使用独立的 SiftAlpha-owned metadata/record，而不能利用标准 [tool] 内容改变 pylock 安装结果。

SiftAlpha provenance extension 只用于 provenance 和诊断，不改变标准 pylock.toml 的安装语义。它不替代标准字段，也不让设备端获得 resolver 能力。

### Staleness 与 lock digest

Environment v1 不通过比较 requirements.txt 与 lock 中的文本行来猜测 stale。staleness 只由以下结构化事实决定：

- lock file semantic digest 是否与 EnvironmentRecord 记录一致；
- selected environment marker 是否仍兼容；
- Runtime Base provenance、Python ABI、Android ABI/API policy 是否仍匹配；
- installer policy version 是否仍兼容。

source declaration 的存在、内容变化或 provenance mismatch 不属于 v1 installer 的强制 staleness 判定。安装相关 staleness 只由 lock、selected runtime、artifact 和 installer policy 等 authoritative facts 决定；诊断层可以显示 source/provenance 差异，但不能据此拒绝安装有效 lock。

## Supported Lock Subset

Environment v1 不宣称支持整个 PEP 751 / PyPA pylock.toml specification。下表是冻结的 v1 subset。

| Lock 内容 | alpha44 v1 |
|---|---|
| lock-version | 必须为字符串 "1.0"；未知 major 或不支持的版本为 LOCK_INVALID |
| requires-python | 必须存在且与 Embedded CPython 3.14.7 兼容 |
| environments | 可选；存在时至少一个 marker 必须匹配当前 Android/CPython context，否则 LOCK_INCOMPATIBLE |
| extras | 只接受空数组；非空为 PACKAGE_UNSUPPORTED |
| dependency-groups | 只接受空数组；v1 不提供 group selection |
| default-groups | 只接受空数组；v1 不提供 group selection |
| created-by | 必须是非空字符串，用于 provenance |
| packages | 必须存在；选中的每个 package 必须唯一、可定位、可验证 |
| packages.name | 必须存在，按 PyPA normalized name 比较 |
| packages.version | 必须存在且为有效版本 |
| packages.marker | 只支持能够由当前 runtime context 评估的 marker；未知变量或无法确定的表达式拒绝 |
| packages.requires-python | 存在时必须与当前 interpreter 兼容 |
| packages.dependencies | 可选；存在时只解析用于 diagnostics/audit display；不要求完整 edges，不参与安装、依赖解析或拒绝条件 |
| packages.wheels | 每个 selected package 至少一个 candidate；允许多个 platform candidate，但设备只选一个匹配的纯 Python wheel |
| wheel URL/path | 必须提供 exact URL 或 project-contained relative path；禁止根据 index 可用内容替换 lock 指定 artifact |
| wheel size | Environment v1 要求存在并在下载后逐字节验证 |
| wheel hashes | 必须存在 sha256；其他 hash 可作为额外信息，不能替代 SHA-256 |
| VCS/directory/archive/sdist | 拒绝；返回 PACKAGE_UNSUPPORTED |
| editable | 拒绝；不执行 editable behavior |
| [tool] | 只接受不影响安装的 provenance/diagnostic data；不把任意 tool table 解释为 resolver 指令 |

PyPA 规范的原文约束是：Data recorded in the [tool] table MUST be disposable（MUST NOT affect installation）；对 packages.dependencies，Tools MUST NOT use this information when doing installation，它 is purely informational for auditing purposes。alpha44/后续 v1 不要求 dependency edges 完整，不因缺失或不完整 edges 拒绝其他方面有效的 lock，也不沿这些 edges 重新解析。实际 install set 只来自经过 lock environment、package marker、requires-python 与 source uniqueness 筛选后的 packages entries；DependencyFingerprintV1 也不以 edges 完整为前提。

### Environment marker context

设备端只使用结构化 runtime context，不读取未受控的环境变量来改变选择：

    implementation_name              = cpython
    platform_python_implementation   = CPython
    python_version                   = 3.14
    python_full_version              = 3.14.7
    sys_platform                     = android
    platform_machine                 = aarch64
    os_name                          = posix
    android_api_level                = SiftAlpha runtime policy fact; not a pylock marker variable

实际 marker evaluator 只允许实现已冻结的变量集合；未知变量、动态执行、调用函数或依赖网络的表达式直接 LOCK_INCOMPATIBLE。environments 和 package markers 都必须在安装前得到确定的 true/false 结果。

### Multiple wheel selection

标准 lock 可以为同一个 package 列出多个 wheel candidate。Environment v1 的选择规则是：

1. 只考虑 filename 合法、hash 完整、与当前 CPython 相容的 candidate；
2. 只允许 platform = any 且 ABI = none；
3. Python tag 允许 py3、py314 或 cp314，并按 exact cp314 → py314 → py3 的顺序优先；
4. 若最高优先级仍有多个不同 artifact，按 normalized filename 做 deterministic tie-break；如果候选语义冲突或无法唯一选择，返回 LOCK_INVALID；
5. android_<api>_<abi> candidate 在本轮只识别为 future capability，Environment v1 不安装；
6. manylinux_*、musllinux_* 或其他 Linux native tag 不能降级当作 Android wheel。

PyPA 当前 platform tag 规范使用 android_apilevel_abi 形式，例如 android_27_arm64_v8a；pure-Python v1 只使用 none + any，未来 native Android wheel 才进入 Android tag compatibility contract。[Platform compatibility tags](https://packaging.python.org/en/latest/specifications/platform-compatibility-tags/)

### Lock validation results

| 结果 | 含义 |
|---|---|
| LOCK_INVALID | lock 结构、字段或 authoritative semantic consistency 不符合 v1；可选 [tool] provenance 数据不属于阻塞条件 |
| LOCK_INCOMPATIBLE | lock 有效，但 requires-python、environment marker、Runtime Base 或 Android policy 不匹配当前设备 |
| ARTIFACT_INVALID | lock 指向的 artifact 存在，但 hash、size、filename、metadata、RECORD 或 archive safety 验证失败 |
| UNSUPPORTED_PACKAGE_KIND | lock 有效但包含 v1 明确拒绝的 VCS、sdist、directory、editable 或 native package |

普通用户可以看到简短原因；Developer Mode 才显示 lock path、selected wheel、tag、hash 和具体 validation fact。

## Environment Identity Model

### 三个不同层次

    DependencyFingerprint
            │ resolved dependency content
            ▼
    EnvironmentIdentity / environmentId
            │ project + runtime + dependency contract
            ▼
    Generation / generationId
            │ one physical immutable installation
            ▼
    ProcessBindingId
            │ exact runtime load boundary
            ▼
    Embedded worker process

### DependencyFingerprintV1

DependencyFingerprintV1 表示 selected dependency content compatibility identity，不包含 project ownership，也不允许跨项目直接共享 mutable files。

规范化输入必须是 deterministic semantic object，而不是原始 TOML bytes：

    {
      "schema": "siftalpha.dependency-fingerprint.v1",
      "lockVersion": "1.0",
      "selectedEnvironment": {
        "python": "3.14.7",
        "pythonImplementation": "cpython",
        "pythonAbi": "cp314",
        "androidAbi": "arm64-v8a",
        "androidApiPolicy": "min:26"
      },
      "selectedExtras": [],
      "selectedDependencyGroups": [],
      "packages": [
        {
          "name": "normalized-name",
          "version": "1.2.3",
          "marker": null,
          "requiresPython": ">=3.10",
          "wheelFilename": "normalized-name-1.2.3-py3-none-any.whl",
          "artifactSha256": "<lowercase hex>",
          "pythonTags": ["py3"],
          "abiTags": ["none"],
          "platformTags": ["any"],
          "artifactOrigin": "https://..."
        }
      ],
      "installerPolicyVersion": "pure-python-v1"
    }

规范化规则：

- object keys 按 UTF-8 字典序；
- arrays 按合同定义的顺序或 normalized name 排序；
- package name 使用 PyPA normalized name；
- hash 算法名统一小写，SHA-256 使用 lowercase hex；
- URL 使用 lock 中的 exact URL，经 URL parser 规范化后纳入；
- omitted 与 explicit empty 只有在 schema 明确定义等价时才等价；
- 不支持字段不能静默丢弃，必须在 validation 前转为 failure。

最终值：

    dependencyFingerprint =
      SHA-256(UTF-8(canonical-json(DependencyFingerprintV1)))

### environmentId

environmentId 是 project-owned logical environment identity，不是共享 cache key：

    environmentId =
      SHA-256(
        "siftalpha.environment.v1",
        stableProjectIdentity,
        runtimeContract,
        dependencyFingerprint,
        runtimeProvenanceDigest,
        installerPolicyVersion
      )

因此：

- dependency graph 或 exact artifact 改变 → dependencyFingerprint 改变 → 新 environmentId；
- CPython patch/runtime artifact/API policy/installer policy 改变 → 新 environmentId；
- Project A 与 Project B 即使 fingerprint 相同 → 不同 environmentId；
- source code 普通内容改变但 dependency contract 不变 → 不自动改变 environmentId，但会产生新的 Project Source Staging generation；
- environmentId 不等于当前物理目录，不承载 Session transient data。

### generation

generationId 表示同一个 logical environment 的一次具体物理安装：

- 每次 fresh install、corruption recovery、reinstall 或 storage relocation 都创建新的不可复用 generation；
- generationId 使用不可预测、不可重用的 ID，例如 gen-<UUID>；
- READY generation 的 site-packages、manifest、PackageFacts 与 verification facts 在切换后视为 immutable；
- generation 不随 Session 创建而变化；
- STOP Session 不删除 generation。

即使 dependency contract 不变，旧 generation gen-v1 与重装后的 gen-v2 物理内容也可能不同，不能仅凭相同 environmentId 让 process 热切换。

### RuntimeLoadBindingV1 与 ProcessBindingId

ProcessBindingId 必须先绑定“本次 worker 将加载的完整 runtime layer”，而不能依赖 alpha44 尚未实现的 environment generation、pylock parser 或 site-packages tree。为此冻结可扩展的 RuntimeLoadBindingV1：

    RuntimeLoadBindingV1 =
      projectIdentity
      + projectSourceGeneration
      + runtimeProvenanceDigest
      + dependencyLayerBinding

alpha44 foundation 的 dependencyLayerBinding 固定为：

    STDLIB_ONLY

因此 alpha44 可以在没有 pylock、environment installer、site-packages 或 generation store 的情况下真实计算并验证：

    ProcessBindingId =
      SHA-256(
        "siftalpha.runtime-load-binding.v1",
        projectIdentity,
        projectSourceGeneration,
        runtimeProvenanceDigest,
        "stdlib-only"
      )

实际实现必须对字段使用明确的 canonical serialization；不得用伪造 environment、伪造 lockDigest 或空的 sitePackagesTreeDigest 绕过 binding。Worker startup 必须收到并确认：

    projectIdentity
    projectSourceGeneration
    runtimeProvenanceDigest
    dependencyLayerBinding
    processBindingId

Worker 一旦 loaded binding set，就不能接受另一个 binding。Project A → Project B 必须启动 fresh worker；同一 Project 的 source generation A → B 也必须产生 fresh binding 并启动 fresh worker。Activity re-entry 只能复用仍然匹配的 project/source/runtime binding，不能静默复用错误 worker。

alpha45 Environment v1 才把 dependencyLayerBinding 扩展为具体的 EnvironmentGenerationBinding：

    EnvironmentGenerationBinding =
      environmentId
      + generationId
      + lockDigest
      + sitePackagesTreeDigest

其中 lockDigest 是 validated pylock semantic document 的 canonical digest，sitePackagesTreeDigest 是 generation 中 sorted relative path、file size 和 file bytes/RECORD digest 的组合，均不包含绝对 app-private path。环境 generation 改变必须产生新的 RuntimeLoadBindingV1 / ProcessBindingId，并要求 fresh worker；这些字段不属于 alpha44 foundation 的前置依赖。

## Process Boundary Decision

### 决定

**Process Isolation 进入 alpha44，但作为 foundation slice；完整 Pure-Python Environment v1 installer 不与它强行捆绑交付。**

alpha44 的最终 scope 改为：

> **Process-Isolated Embedded R Foundation**

Pure-Python Environment v1 的 lock consumer、artifact cache、wheel verifier 和 managed installer 在 process foundation 通过真机验收后作为 alpha45 scope 实施。本文先冻结它们的合同，避免在没有可靠 process boundary 时实现一套会误导用户的 environment installer。

### 为什么不选择 strict single-process gate 作为 alpha44 完整方案

| 方案 | Correctness | UX | Android lifecycle | 实现风险 | 决定 |
|---|---|---|---|---|---|
| A. 当前单 process + strict environment binding | 对同一 binding 可预测；不能安全切换不同 Project | 用户运行过 A 后再运行 B 会 deterministic reject 或要求外部环境 | 复用当前模型，但 process death 仍会丢运行上下文 | 低 | 不作为完整 Environment v1 交付 |
| B. per-runtime-load-binding fresh OS worker process | 能以 process exit 清除 sys.modules、线程和 native state 边界 | M 可在后台重启 worker，普通用户不需要理解 modules | 必须持久化 binding/session facts，处理被系统杀死 | 中高 | **选择，先作为 alpha44 foundation** |
| C. subinterpreter / module cleanup | 对 arbitrary third-party code 的隔离证据不足 | 失败模式难解释 | 与 extension/module lifetime 复杂耦合 | 高且不确定 | 不选择 |

Android 官方文档说明，系统会根据 process importance 在内存压力下杀死进程；cached process 尤其不能被当成永久 worker。因此 Process-Isolated Foundation 必须把 RuntimeLoadBindingRecord、ProcessBindingId 和 Session facts 持久化，进程死亡要变成结构化 failure，而不是静默重跑或切换 provider。[Android processes and app lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle)

### Process contract

alpha44 foundation 只允许：

    one worker process instance
            ↔ one ProcessBindingId
            ↔ one RuntimeLoadBindingV1
            ↔ at most one active Session

新 Project、新 source generation 或未来新 environment generation 的流程是：

1. M/R 停止当前 Session；
2. 旧 worker 完成 cooperative STOP 或在明确 timeout 后被终止；
3. supervisor 确认旧 worker 已退出；
4. 创建新的 worker instance；
5. worker 校验新的 ProcessBindingId、Runtime Base provenance 和 path containment；
6. 通过 typed IPC 建立 Session；
7. 任何绑定不匹配都返回 PROCESS_ENVIRONMENT_SWITCH_UNSUPPORTED，不在旧 worker 中偷偷换 path。

这不是 concurrent sessions contract。alpha44 foundation 仍只支持一个 active Embedded worker/session。

### IPC 与生命周期边界

这是未来实现合同，不是本轮实现：

- 使用同一 Android app UID 下的专用 Embedded worker process；不把 android:isolatedProcess 当作默认方案，因为 worker 需要按合同访问 app-private Runtime Base、environment 和 session files；
- 使用 typed Binder/Service IPC，不解析 stdout 作为核心 state；
- start、status、stop、process-death、result/facts 都有结构化 request/response；
- OS PID 只是诊断字段，真正的 identity 是 workerInstanceId + ProcessBindingId；
- worker death 后，M 读取持久化 record，返回 WORKER_PROCESS_DIED 对应的 developer detail；不得自动切换到另一个 environment 或 silent fallback；
- long-running Web/Automatic Observation 继续遵守 alpha43 既有 contract，但 Web integration 不属于 alpha44 foundation acceptance；
- alpha44 不承诺 full process-death recovery；先证明 binding 不错配、状态不丢失、失败可解释。

## Import Verification Decision

### 决定

**后续 Environment v1 采用方案 A：Prepare 阶段 metadata/file-only verification；alpha44 Foundation 不执行 third-party preparation。**

Prepare 阶段允许：

- TOML/lock schema validation；
- environment marker 和 requires-python validation；
- wheel filename/tag validation；
- ZIP structure/path/symlink/payload validation；
- METADATA identity and dependency fact validation；
- WHEEL 与 Root-Is-Purelib validation；
- .dist-info/RECORD validation；
- SHA-256、size、quota 和 static package facts validation。

Prepare 阶段禁止：

- import package；
- python -c "import ..."；
- 为了验证而执行第三方 top-level code；
- 在长期 Embedded worker 中临时改变 sys.modules、sys.path_hooks 或 global state。

Python import system 会先查询 sys.modules，命中时直接返回已有 module object；即使删除 key，其他引用也可能保留。Import 还会执行 loader/module code。因此长期 process 内的 prepare import 同时带来污染、任意代码执行和 crash isolation 问题。[Python import system](https://docs.python.org/3.14/reference/import.html)

### 为什么 alpha44 不选择 disposable import process

disposable process 的 compatibility signal 更强，但会增加：

- 第二套 process startup/IPC 与 path contract；
- package top-level code 的执行风险；
- 额外内存和磁盘成本；
- native/source boundary 尚未解决时的误导性“通过”；
- alpha44 foundation 尚未完成前的实现耦合。

真实 import 应在用户真正启动 Runtime Session 时自然发生；失败由 Runtime Session 返回结构化 runtime failure。Prepare 的 READY 只表示 lock、artifact、wheel、metadata、RECORD 和静态 payload 合同通过，不表示每个 package 的 import side effect 已被执行。

## Filesystem Layout

Android 官方文档区分 app-specific internal files 与 cache：internal storage 不需要额外 storage permission，其他 app 默认不能访问；cache 可能在低存储压力下被系统清理。因此 immutable installed environment 和可丢失 download cache 不能共用目录。[Android app-specific storage](https://developer.android.com/training/data-storage/app-specific)

### 五类对象

| 类型 | 内容 | owner/lifecycle | v1 规则 |
|---|---|---|---|
| Runtime Base | CPython、stdlib、lib-dynload、Runtime-owned libraries、provenance | app/runtime version | 只读，不写 third-party package |
| Project Source Staging | SAF 导入源码、entrypoint、源码 generation | project/source generation | 不放 wheel、cache 或 installed package |
| Project Environment | lock snapshot、generation、site-packages、PackageFacts、verification | project/environment/generation | mutable tree 只归一个 Project |
| Dependency Download Cache | exact wheel archive、hash、origin、verification marker | app cache/quota | immutable，可跨 Project 复用，不作为 sys.path |
| Session Workspace | cwd、TMPDIR、logs、transient output、session state | one Session | 不作为 dependency store |

### 兼容当前 alpha43 的路径

本设计尽量不重新解释已有路径：

    files/siftalphax/python/
      # 当前已有 Runtime Base；alpha44 不迁移、不写 package

    files/siftalphax/projects/<project-id>/source/<source-generation>/
      # Project Source Staging
      # 当前已有 projects/<sessionId> 目录只作为历史 staging 事实，不等于 environment

    files/siftalphax/environments/<project-id>/<environment-id>/
      environment.json
      current.json
      generations/<generation-id>/
        lock.snapshot.toml
        package-facts.json
        verification.json
        site-packages/
      transactions/<transaction-id>/
        input.json
        marker.json
        partial/

    <context.cacheDir>/siftalphax/artifacts/sha256/<lowercase-digest>/
      artifact.whl
      artifact.json
      verified.marker

    <context.cacheDir>/siftalphax/metadata/
      # 可丢失的 index/response metadata；不作为 install authority

    files/siftalphax/sessions/<session-id>/
      cwd/
      tmp/
      logs/
      state.json

files/siftalphax/python 和 files/siftalphax/projects/<sessionId> 是当前真实实现边界；后续实现必须新增明确的 environments、cache、sessions ownership，不把旧 staging path 当成 site-packages。

## Environment State Machine

### Public state

    Environment v1 只对 M 暴露：

    MISSING
    PREPARING
    READY
    STALE
    FAILED

PREPARING 的 phase 是：

    DISCOVERING
    VALIDATING_LOCK
    FETCHING
    VERIFYING_ARTIFACT
    INSTALLING
    VERIFYING_ENVIRONMENT
    SWITCHING

### State semantics

| State | 进入条件 | 可否启动新 Session |
|---|---|---|
| MISSING | 没有 valid generation | 否；先 prepare |
| PREPARING | 正在处理 transaction | 否；可 cancel |
| READY | current pointer 指向已验证 immutable generation | 是，前提是 ProcessBinding/Session contract 满足 |
| STALE | desired lock/runtime/source provenance 与 current generation 不一致 | 否；旧 generation 仍保留用于 rollback/diagnostics |
| FAILED | transaction 或验证失败 | 否；如果存在旧 READY，必须保留其完整内容并明确 oldReadyEnvironmentPreserved=true |

FAILED 不删除旧 READY generation。实现可以在 record 中保留 lastReadyGeneration，但新 Session 不得把不匹配的旧 generation 当成当前 desired environment。

## Atomic Generation Model

### Transaction contract

禁止在正在使用的 site-packages 原地 install/upgrade。固定流程：

    current.json → gen-v1 READY
           │
           ├─ new lock/runtime contract
           ▼
    transaction PREPARING
           │
           ├─ fetch exact wheel to cache temp
           ├─ hash/size/tag/metadata/RECORD validation
           ├─ install to generations/gen-v2.partial
           ├─ static verification
           ▼
    gen-v2 VERIFIED
           │
           ├─ write generation metadata
           ├─ fsync files and marker
           ├─ atomically replace current.json
           ▼
    current.json → gen-v2 READY

实现要求：

1. current.json 与 generation 位于同一个 app-private filesystem；
2. pointer update 使用同目录 temporary file + flush/sync + atomic rename；
3. generation directory 完成前不能出现在 READY pointer；
4. transaction marker 记录 phase、input digest、partial paths 和 cleanup status；
5. app/process death 后可从 marker 判断继续清理或恢复旧 pointer；
6. Session 启动时复制/固定 ProcessBindingId 和 generation path，不读取变化中的 current pointer；
7. old generation 只有在没有 active Session/worker binding 引用，且不再是 current/rollback retention target 时才允许 GC；
8. GC 失败不影响 READY environment，应成为 cleanup diagnostic。

### Corruption recovery

如果 current generation 的 metadata、manifest、site-packages 或 verification marker 损坏：

- 不原地修复；
- 创建新的 generation；
- 重新从 exact lock/cache/URL 准备；
- 新 generation 完成前保留旧目录用于诊断；
- 若旧 generation 也不可用，状态为 FAILED，不 silent fallback。

## Wheel Installer Contract

本节冻结未来 SiftAlpha-managed Wheel Installer 的责任，不实现它。

### Installer responsibilities

Installer 必须负责：

1. 根据 lock 中 exact URL/path 和 artifact SHA-256 做 cache lookup/fetch；
2. 不进行 dependency resolution；
3. 验证 filename tags、size、hash、METADATA、WHEEL、RECORD；
4. 检查 ZIP member path、duplicate entries、symlink/hardlink、absolute path 和 .. escape；
5. 拒绝 .so、.dylib、.dll、ELF/native executable、native extension 和未授权 .pth；
6. 只把允许的 purelib 内容安全提取到 generation-owned site-packages；
7. 产生 PackageFact；
8. 产生结构化 verification facts；
9. 将失败留在 transaction 内，不切换 READY pointer；
10. 不执行 package import、setup script、build backend、console script 或 subprocess。

### Exact v1 wheel subset

允许：

- wheel archive；
- filename platform tag = any；
- ABI tag = none；
- Python tag = py3、py314 或 cp314；
- Root-Is-Purelib: true；
- wheel root 中的 pure Python/data files；
- .data/purelib，映射到 generation-owned site-packages；
- .dist-info、METADATA、WHEEL、RECORD 和静态 license/metadata files；
- package data 只要仍是 archive 内的 purelib-safe files。

拒绝并明确返回 PACKAGE_UNSUPPORTED 或 UNSUPPORTED_NATIVE_PACKAGE：

- android_<api>_<abi> native wheel；
- manylinux_*、musllinux_* 或其他 platform-specific native wheel；
- Root-Is-Purelib: false；
- platlib；
- .data/data；
- .data/scripts；
- .data/headers；
- entry_points.txt / console-script installation contract；
- .pth；
- C/C++/Rust extension；
- sdist、source archive、VCS、directory、editable；
- 任何需要 build backend、compiler、NDK、Rust、CMake 或系统库的 artifact。

Wheel 规范规定 .data 下的 scripts、headers、documentation 等内容会在安装时移动到各自目的路径；v1 为了避免 Android path/launcher/system integration 的隐式语义，只接受 purelib，其余 .data schemes 显式拒绝。[Binary distribution format](https://packaging.python.org/en/latest/specifications/binary-distribution-format/)

### Static validation order

固定顺序：

    lock candidate
      → URL/path containment
      → download/cache bytes
      → SHA-256 + size
      → wheel filename/tag
      → ZIP safety
      → WHEEL / Root-Is-Purelib
      → METADATA Name/Version/Requires-Python/Requires-Dist facts
      → RECORD paths/hashes/sizes
      → pure-Python payload scan
      → install into new generation only

验证失败不执行后续 install；任何 partial extraction 都只能存在 transaction directory。

## PackageFact Contract

PackageFact 是结构化事实，不来自 pip/stdout：

    PackageFact {
      normalizedName
      version
      wheelFilename
      artifactSha256
      artifactSize
      pythonTags[]
      abiTags[]
      platformTags[]
      requiresPython
      metadataDigest
      recordDigest
      rootIsPurelib
      dataSchemes[]
      origin { kind, exactUrlOrRelativePath }
      staticPayloadClass = PURE_PYTHON
    }

PackageFact 产生规则：

- normalizedName 来自 METADATA: Name，并与 lock package name 比较；
- version 来自 METADATA: Version，并与 lock version 比较；
- artifactSha256 来自实际 bytes，不接受只相信 filename；
- requiresPython 来自 metadata/lock 的结构化字段；
- metadataDigest 与 recordDigest 对 canonical bytes 计算；
- origin 记录 exact URL/path，但不允许 installer 通过 origin 改变 lock 选择；
- staticPayloadClass 只能在无 native payload、无 .pth、无 rejected .data scheme 时为 PURE_PYTHON。

PyPA Core Metadata 规定 Metadata-Version、Name、Version 是 required fields，并要求比较 distribution name 前进行 normalization；Requires-Dist 和 Requires-Python 也是结构化 metadata，不能从日志文本猜测。[Core metadata specifications](https://packaging.python.org/en/latest/specifications/core-metadata/)

## EnvironmentRecord Contract

EnvironmentRecord 是持久化的 environment 事实，不塞入 transient Session 数据：

    EnvironmentRecord {
      schemaVersion
      environmentId
      bindingId
      projectIdentity
      dependencyFingerprint
      generation
      state
      phase
      runtimeVersion
      runtimeProvenanceDigest
      pythonAbi
      androidAbi
      androidApiPolicy
      lockDigest
      selectedPackages[]
      sitePackagesPath
      currentPointerPath
      createdAt
      updatedAt
      lastUsedAt
      failure
      oldReadyEnvironmentPreserved
    }

不放入 EnvironmentRecord 的内容：

- sessionId；
- 当前 cwd/TMPDIR；
- transient logs；
- active UI pending；
- user-visible presentation state；
- Runtime Session stdout/stderr。

Session binding 另行持久化，避免 environment lifecycle 与 one-run lifecycle 混在一起。

## PrepareEnvironmentIntent Contract

M → R 的输入必须明确区分 Project Source 与 Environment Input：

    PrepareEnvironmentIntent {
      requestId
      projectIdentity
      sourceRoot
      sourceGeneration
      authoritativeLockSource {
        kind = PROJECT_ROOT_PYLOCK
        relativePath = "pylock.toml"
        sourceDigest
        lockDigest
      }
      desiredRuntime {
        implementation = CPython
        version = 3.14.7
        pythonAbi = cp314
        androidAbi = arm64-v8a
        androidApiPolicy
        runtimeProvenanceDigest
      }
      selectedExtras = []
      selectedDependencyGroups = []
      offlinePolicy
      networkPolicy
      cachePolicy
      securityPolicy
      generationRequest
      cancelToken
    }

sourceRoot 只用于项目源码/entrypoint 和 lock provenance；它不是 installed environment path。authoritativeLockSource 是 v1 的 dependency input，不能由 M 传一段 requirements 文本让 R 临时解析。

## EnvironmentFacts Contract

R → M 返回结构化事实：

    EnvironmentFacts {
      requestId
      environmentId
      bindingId
      generation
      state
      phase
      dependencyFingerprint
      lockDigest
      packageFacts[]
      cacheFacts
      runtimeCompatibility
      capabilities[]
      failure
      oldReadyEnvironmentPreserved
    }

capabilities 至少可以表达：

- PURE_PYTHON_WHEEL_ENVIRONMENT；
- METADATA_FILE_VERIFIED；
- PROCESS_ISOLATED_WORKER；
- NATIVE_PACKAGES_UNSUPPORTED；
- SOURCE_BUILD_UNSUPPORTED；
- CONCURRENT_EMBEDDED_SESSION_UNSUPPORTED；
- IMPORT_VERIFICATION_NOT_RUN。

M 不解析 installer logs，不从 stdout/stderr 猜 READY；R 不依赖 Activity、Compose、Browser 或 Presentation。

## M ↔ R Contract

责任边界保持：

    M 管项目、source staging、用户选择、provider policy 和 Presentation。
    R 负责 lock validation、artifact facts、environment transaction、worker binding 和 Runtime facts。
    X = M + R。

M 发送 PrepareEnvironmentIntent，不能发送“请运行 pip”或一段未经锁定的 requirements 文本来要求 R 自己解析。R 返回 EnvironmentFacts 和 Structured Failure，不能依赖 Activity、Compose、Browser 或用户可见 UI。M 不能把 installer log、shell marker 或 stdout 当成 READY/environment state。

## Failure Model

### Structured Failure V1

alpha44 foundation 与后续 Pure-Python installer 共享以下最小 failure vocabulary：

| Failure kind | 典型 stage | retryable | 语义 |
|---|---|---:|---|
| LOCK_MISSING | DISCOVERING | 否，除非用户提供 lock | 没有 authoritative pylock.toml |
| LOCK_INVALID | VALIDATING_LOCK | 否，需重新生成 lock | schema、field 或 authoritative semantic consistency 无效；可选 [tool] provenance data 不属于阻塞条件 |
| LOCK_INCOMPATIBLE | VALIDATING_LOCK | 否，需其他 lock/runtime | Python/marker/Android/runtime 不兼容 |
| PACKAGE_UNSUPPORTED | VALIDATING_LOCK/VERIFYING_ARTIFACT | 否，需受支持的 package | VCS、sdist、directory、editable、extras/group 或其他 v1 之外能力 |
| UNSUPPORTED_NATIVE_PACKAGE | VERIFYING_ARTIFACT | 否，走 External Provider | native payload、native tag、C/C++/Rust extension |
| ARTIFACT_NOT_FOUND | FETCHING | 是，URL/cache 修复后可重试 | exact artifact 不在 cache，URL 不可取得或离线 cache miss |
| HASH_MISMATCH | VERIFYING_ARTIFACT | 否 | bytes 与 lock SHA-256 不一致 |
| ARCHIVE_UNSAFE | VERIFYING_ARTIFACT | 否 | path traversal、symlink/hardlink、duplicate/absolute member |
| WHEEL_INVALID | VERIFYING_ARTIFACT | 否 | filename、WHEEL、METADATA、RECORD 或 tag invalid |
| DISK_QUOTA | INSTALLING | 可能 | 单 artifact、文件数、environment 或 app storage quota 不足 |
| NETWORK_UNAVAILABLE | FETCHING | 是 | online policy 要求 exact URL，但网络不可用 |
| INSTALL_CANCELLED | any transaction phase | 是 | 用户/系统取消；不切换 pointer |
| ENVIRONMENT_VERIFY_FAILED | VERIFYING_ENVIRONMENT | 视 detail | generation 静态 facts、manifest 或 marker 不一致 |
| PROCESS_ENVIRONMENT_SWITCH_UNSUPPORTED | process binding | 否，需 fresh worker | 当前 worker 已绑定另一个 ProcessBindingId |
| WORKER_PROCESS_DIED | process lifecycle | 视 policy | worker 被系统/异常终止；不 silent fallback |

每个 failure 必须携带：

    stage
    retryable
    safeUserMessage
    developerDetail
    oldReadyEnvironmentPreserved
    packageName/version（when applicable）
    artifactSha256（when applicable）

developerDetail 必须经过 secret redaction；普通 UI 只显示稳定、可行动的 guidance。

## Network / Offline Contract

### Exact artifact access

后续 Environment v1 只访问 lock 指定的 exact artifact：

- URL 必须是 https://；
- 禁止 HTTP downgrade、file:、VCS URL、任意 package index resolution；
- 最多允许 3 次 redirect，最终 URL 仍必须是 HTTPS；
- redirect 不能改变被验证的 artifact bytes；最终 bytes 必须匹配 lock SHA-256；
- packages.index 如果存在只作为 provenance/诊断，不作为候选替换或 fallback；
- 不根据“当前 index 上有什么 wheel”改变选择；
- 请求有连接、读取、总时长上限；
- partial file 只能写入 cache temp，失败后清理或 quarantine。

建议的 v1 policy defaults（属于可版本化 security policy，不是隐式系统默认）：

    maxRedirects          = 3
    connectTimeout        = 15s
    totalArtifactTimeout  = 5min
    maxArtifactBytes      = 64 MiB
    maxSingleFileBytes     = 16 MiB
    maxEnvironmentBytes   = 256 MiB
    maxEnvironmentFiles   = 20,000

### Offline

| 条件 | 行为 |
|---|---|
| offline + verified cache hit | 继续验证并安装 |
| offline + no verified cache | NETWORK_UNAVAILABLE，不尝试隐式联网 |
| online + exact URL 404 | ARTIFACT_NOT_FOUND |
| cache bytes corrupt | quarantine；online 时重新取 exact URL，offline 时结构化失败 |
| HTTPS/certificate failure | NETWORK_UNAVAILABLE，developer detail 记录 TLS/certificate stage |

不 silent network fallback，也不让 installer 通过另一个 index 找“看起来相同”的 artifact。

## Cache Contract

### Key and states

Cache key 冻结为：

    sha256:<lowercase-hex>

cache object 必须由实际 artifact bytes 的 SHA-256 命名。URL、package name、version 不是 cache identity。

artifact cache state：

    MISSING
    PARTIAL
    VERIFIED
    CORRUPT
    QUARANTINED

规则：

- download 先写随机 temp；
- hash/size/ZIP validation 全部通过后 atomic rename 为 digest path；
- VERIFIED object immutable，不能被另一个 Project 原地覆盖；
- PARTIAL 不可被 installer 当作 artifact；
- CORRUPT 不作为 cache hit；
- cache 可以跨 Project 复用，但不能成为 sys.path；
- Installed Environment 必须复制/安全提取到 Project/generation-owned tree；
- cache 有总大小与单 artifact quota；
- 第一版可以不实现 LRU，但必须保留 createdAt、lastUsedAt 和 safe GC 位置；
- cache 被 Android 系统清理后，environment generation 仍必须依靠自身已安装文件运行，不得要求 cache 常驻。

## Session Binding

Session 与 Environment 是不同对象：

    Session {
      sessionId
      processBindingId
      environmentId
      generationId
      projectSourceGeneration
      cwd
      TMPDIR
      runtimeState
      start/stop/result facts
    }

Session 开始后：

1. alpha44 固定 ProcessBindingId、RuntimeLoadBindingV1 和 source generation；alpha45 再固定 environment generation；
2. 不重新读取项目 current pointer 作为运行时依赖；
3. current pointer 后续改变不影响该 Session；
4. STOP 结束 Session，不删除 Environment；
5. CLEAN 必须分别支持 clear Session Workspace、clear Project Environment、clear Download Cache；
6. environment GC 必须检查 active worker/session binding references；
7. Activity 离开前台不能因此自动删除 environment 或停止 alpha43 已有的长期 Runtime semantics。

alpha45 的同一 environment generation 可以被多个顺序 Session 复用，但这不提供 clean-session guarantee。不同 generation 即使同一 environmentId 也要求新的 ProcessBindingId 和 fresh worker；alpha44 foundation 对应的边界是 source generation。

## External Provider Boundary

以下任一条件出现时，Embedded R 返回 capability failure：

- 没有 supported pylock.toml；
- selected package 没有 allowed pure wheel；
- native wheel、.so、C/C++/Rust extension；
- VCS、directory、archive/sdist、source build；
- editable、.pth、scripts/headers/data/platlib scheme；
- Runtime Base、Python ABI、Android API/ABI 不兼容；
- 当前 worker 不能满足 requested ProcessBindingId；
- storage/network/security policy 不满足。

随后 M 可以向用户显示：

    Embedded R 不支持该项目的依赖环境。
    可选择：使用 External Runtime

要求：

- 用户必须明确选择 External Runtime；
- 不得把 External Provider 当作 Embedded prepare 的 hidden fallback；
- Termux + PRoot + Ubuntu + Python production path 保留；
- Embedded failure 与 External start 之间保留可审计的 provider decision；
- M 不把 External pip/shell output 当作 Embedded EnvironmentFacts。

## Normal User Flow

未来普通用户流程：

    Import Project
          ↓
    发现 pylock.toml 与 Embedded capability
          ↓
    Prepare Environment
          ↓
    启动 fresh worker process
          ↓
    Runtime Session
          ↓
    Result / Web（沿用既有 Runtime 与 Presentation contract）

普通用户不需要理解 wheel、hash、dependency fingerprint、site-packages、generation 或 ProcessBindingId。

普通用户只看到“正在准备环境”“环境不受支持”“使用外部运行环境”等简单说明。Developer Mode 未来才显示 lock、artifact、hash、generation、cache、marker 和 failure detail。

## Implementation Dependency Graph

本轮不实现。后续 production implementation 必须按依赖顺序推进：

    1. Contract types / stable enums / serialization
            ↓
    2. Process-Isolated Worker Foundation
       worker identity + Binder IPC + start/status/stop/death
            ↓
    3. RuntimeLoadBindingV1 + foundation ProcessBindingId
            ↓
    4. pylock.toml parser + supported-subset validator
            ↓
    5. canonical semantic lock + DependencyFingerprintV1
            ↓
    6. EnvironmentIdentity + EnvironmentRecord store
            ↓
    7. app-private filesystem containment + transaction markers
            ↓
    8. content-addressed artifact cache
            ↓
    9. wheel verifier / PackageFact
            ↓
    10. managed purelib installer
            ↓
    11. atomic generation switch + GC
            ↓
    12. M ↔ R prepare/start/facts wiring
            ↓
    13. alpha45 EnvironmentGenerationBinding + atomic generation switch
            ↓
    14. alpha45 real-device Pure-Python Environment acceptance

不能先实现 installer 再补 process binding；否则会把可安装文件误认为可安全加载的 runtime environment。

## alpha44 Scope

### Final alpha44 name

**Process-Isolated Embedded R Foundation**

### alpha44 includes

alpha44 只进入以下最小可证明范围：

1. 一个 dedicated Embedded worker process 对应一个 ProcessBindingId；
2. typed start/status/stop/process-death IPC contract；
3. worker 启动时验证 Runtime Base provenance、binding identity 和 path containment；
4. 新 Project/source generation 不在旧 worker 内热切换；未来 environment generation 也必须走同一 fresh-worker boundary；
5. worker death 产生持久化、结构化 failure；
6. stdlib-only project 在 worker 中维持当前 alpha43 Embedded execution contract；
7. explicit External Provider boundary 与不 silent fallback；
8. 为后续 pylock.toml/Environment v1 预留 contract types，但不安装第三方 wheels。

### alpha44 excludes

- pylock parser/installer production implementation；
- dependency resolver/Locker；
- wheel download/cache/extraction；
- project site-packages environment；
- third-party package import verification；
- native/source build；
- Web/Browser/Automatic Observation 新集成；
- concurrent sessions；
- full process-death recovery；
- UI redesign；
- version bump。

### alpha45 candidate

Process foundation 真机通过后，alpha45 才实施本文冻结的 Pure-Python Project Environment v1：

- pre-existing pylock.toml consumption；
- exact wheel/cache；
- purelib verifier/installer；
- per-project environment/generation；
- dependency fingerprint and stale model；
- atomic switch；
- offline/cache/hashes；
- structured PackageFacts/EnvironmentFacts；
- A–J environment acceptance matrix。

这不是把 alpha44 计划无限推迟，而是把 correctness prerequisite 与 dependency installation 分离，避免在单 process isolation 尚未证明时交付错误的 environment promise。

## Acceptance Plan

### Alpha44 Foundation real-device acceptance

alpha44 的最小真机验收不包含 third-party package install，包含：

| ID | 场景 | 通过标准 |
|---|---|---|
| P1 | stdlib-only project | 在 dedicated worker 中正常 START/STATUS/STOP |
| P2 | foundation binding identity | worker 记录并回报唯一 workerInstanceId 与可由 projectIdentity、projectSourceGeneration、runtimeProvenanceDigest、STDLIB_ONLY 真实计算的 ProcessBindingId |
| P3 | Project boundary | Project A → Project B 必须退出旧 worker、创建 fresh worker；不得在旧 worker 内切换 source/path |
| P4 | source generation boundary | 同一 Project 的 source generation A → B 必须产生 fresh binding 并创建 fresh worker |
| P5 | process death | worker 被杀死后得到 structured WORKER_PROCESS_DIED，无 silent provider fallback |
| P6 | lifecycle safety | Activity leave/re-entry 不造成错误 binding reuse；现有 alpha43 Runtime lifecycle 与 Presentation 不被文档设计回退 |
| P7 | M stability / External boundary | 现有 M 行为保持不变；不满足 worker/binding contract 时清晰提供 External Runtime 选项。若实现需要修改 M，必须先停止、报告并获得明确批准 |

### Pure-Python Environment v1 acceptance after foundation

下面 A–J 是 alpha45 或明确重新扩展 alpha44 后才执行的 package environment acceptance：

| ID | 场景 | 通过标准 |
|---|---|---|
| A | stdlib-only project | prepare/start 成功 |
| B | 一个 pure-Python dependency | pylock.toml、exact wheel、hash、metadata、RECORD 验证和安装成功 |
| C | 第二次运行 | 复用相同 environment/generation，不重复安装 |
| D | transitive pure-Python graph | lock graph 完整，selected packages 全部安装且不现场 resolution |
| E | hash mismatch | 明确 HASH_MISMATCH，旧 READY 保留 |
| F | native dependency | 明确 UNSUPPORTED_NATIVE_PACKAGE，不 silent fallback |
| G | environment update failure | gen-v2 失败，gen-v1 READY pointer、files 和 facts 完整 |
| H | offline cache hit | exact SHA-256 cache hit 可继续 |
| I | offline cache miss | NETWORK_UNAVAILABLE，不隐式联网 |
| J | Project A/B | 不同 environmentId 使用不同 binding/worker；同 worker 不允许切换 |
| K | environment generation switch | 属于 alpha45 Environment acceptance；gen-v1 → gen-v2 产生新的 EnvironmentGenerationBinding / ProcessBindingId，并使用 fresh worker；alpha44 不执行此项 |

## Future Extensions

以下是明确延后的能力，不属于本次冻结的 alpha44：

1. alpha45 pure-Python environment installer；
2. trusted SiftAlpha Locker / CI automation；
3. extras、dependency-groups、multi-profile lock selection；
4. disposable import verification process；
5. full process-death recovery and session resume；
6. concurrent per-environment workers；
7. Android native wheels with android_<api>_<abi> provenance、API floor、DT_NEEDED closure 和 linker policy；
8. source builds、compiler/NDK/Rust/CMake；
9. Web/Browser/Automatic Observation integration with worker process；
10. result/environment persistence beyond current product policy；
11. Developer Mode diagnostic presentation。

这些是扩展方向，不是当前已完成能力。

## Authoritative References

以下资料在本轮重新核对；“上游事实”与“SiftAlpha 选择”分开记录：

1. [PEP 751 — A file format to record Python dependencies for installation reproducibility](https://peps.python.org/pep-0751/) — **Verified fact**：状态为 Final；定义 lock file、Locker/Installer 分工、lock-version、environment/package/wheel/hash 字段及安装时不需要重新 resolution 的模型。
2. [PyPA pylock.toml specification](https://packaging.python.org/en/latest/specifications/pylock-toml/) — **Verified fact**：当前 interoperability specification；支持 lock-version = "1.0"、requires-python、environments、extras/groups、packages、多 wheel candidate、URL/path/size/hashes；[tool] 数据必须 disposable 且不得影响安装，packages.dependencies 只能用于 auditing，安装时不得使用。**Recommendation**：后续 Environment v1 只接受本文定义的 pure-Python subset。
3. [PyPA Binary distribution format](https://packaging.python.org/en/latest/specifications/binary-distribution-format/) — **Verified fact**：wheel archive、.dist-info、.data、purelib/platlib 语义。**Recommendation**：v1 只接受 purelib，拒绝 scripts/headers/data/platlib。
4. [PyPA Platform compatibility tags](https://packaging.python.org/en/latest/specifications/platform-compatibility-tags/) — **Verified fact**：tag 形式、android_apilevel_abi、manylinux/glibc、musllinux/musl。**Recommendation**：v1 只接受 none + any。
5. [PyPA Core metadata specifications](https://packaging.python.org/en/latest/specifications/core-metadata/) — **Verified fact**：Metadata-Version、Name、Version required；Requires-Dist、Requires-Python 为结构化 metadata。
6. [Python 3.14 import system](https://docs.python.org/3.14/reference/import.html) — **Verified fact**：import 首先检查 sys.modules；删除 key 不保证 module object 被销毁，import 会执行 loader/module code。
7. [Python 3.14 embedding](https://docs.python.org/3.14/extending/embedding.html) — **Verified fact**：embedded application 负责初始化 interpreter，可调用 embedding API；**Recommendation**：worker process 是 SiftAlpha 的 process boundary，不在本轮实现。
8. [Python 3.14 initialization configuration](https://docs.python.org/3.14/c-api/init_config.html) — **Verified fact**：PyConfig 提供 use_environment、user_site_directory 等 isolated configuration；**Current repository fact**：alpha43 已关闭环境变量和 user site 影响。
9. [Python 3.14 Using Python on Android](https://docs.python.org/3.14/using/android.html) — **Verified fact**：Android 采用 embedded mode，由 native Android app 打包 libpython、stdlib 和 private Python code。
10. [Android processes and app lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle) — **Verified fact**：Android 可因 process importance/memory pressure 终止进程；**Recommendation**：worker death 必须持久化并结构化报告。
11. [Android app-specific storage](https://developer.android.com/training/data-storage/app-specific) — **Verified fact**：internal app-specific files 私有且不需额外 storage permission，cache 可能被系统清理；**Recommendation**：installed environment 放 filesDir，download cache 放 cacheDir 并可重建。

## FINAL DESIGN DECISIONS

1. **alpha44 及其后续 Environment v1 的 authoritative dependency input 是什么？**
   对后续 Pure-Python Environment v1，输入是 pylock.toml，且必须是已经由设备外 Trusted Locker 生成的、通过 supported subset validation 的 lock。alpha44 Foundation 本身不安装 third-party dependency，因此没有可供它安装的 requirements/pyproject 输入；未来 Environment slice 没有 lock 就 LOCK_MISSING，设备端不解析 requirements/pyproject。

2. **是否采用 pylock.toml？**
   **采用。** 使用标准 PEP 751/PyPA format，alpha44 只支持本文冻结的 pure-Python subset，不宣称支持整个标准。

3. **requirements.txt / pyproject.toml 在 v1 中是什么角色？**
   它们是 human/project dependency declaration 与可选 provenance display，不是 Embedded installer 的最终输入。pylock.toml 优先；同时存在时不合并。缺少 [tool.siftalpha]、source selection/digest 不存在或 provenance mismatch，都不能阻塞一个其他方面有效的 pylock.toml；v1 不强制检测 source declaration → lock staleness。

4. **谁负责 resolver？**
   设备外的 Trusted Locker（CI、开发机或受信任 resolution service）负责完整 graph、exact versions、exact artifacts、markers 和 hashes。alpha44 app 不运行 resolver。

5. **设备端是否做 dependency resolution？**
   **不做。** 设备端只选择 lock 中匹配当前 runtime 的已列 wheel、下载/命中 exact artifact、验证并安装。

6. **Prepare 阶段是否执行 import verification？**
   **不执行。** alpha44 使用 metadata/file/wheel/RECORD/static payload verification；不在长期 Embedded CPython process 中 import 第三方 package。真实 import 留给 Runtime Session。

7. **Process Isolation 是否进入 alpha44？**
   **进入，但只交付 Process-Isolated Embedded R Foundation。** per-runtime-load-binding worker、typed IPC、binding gate、start/status/stop/death contract 是 alpha44；完整 Pure-Python Environment installer 调整到 alpha45。

8. **ProcessBindingId 精确定义是什么？**
   alpha44 使用可真实计算的 RuntimeLoadBindingV1：projectIdentity + projectSourceGeneration + runtimeProvenanceDigest + STDLIB_ONLY。ProcessBindingId = SHA-256("siftalpha.runtime-load-binding.v1" + 这组 canonical fields)；不伪造 environmentId、generationId、lockDigest 或 sitePackagesTreeDigest。alpha45 才把 dependencyLayerBinding 扩展为包含 environmentId、generationId、lockDigest、sitePackagesTreeDigest 的 EnvironmentGenerationBinding；worker 一旦加载该 binding，不得加载另一个 binding。

9. **environmentId / generation / fingerprint 如何区分？**
   dependencyFingerprint = selected dependency content，且不假设 packages.dependencies 完整；environmentId = project + runtime + dependency contract；generationId = alpha45 中一次具体物理安装；alpha44 foundation 使用 projectSourceGeneration + STDLIB_ONLY 的 RuntimeLoadBindingV1。dependency contract 变化创建新 environmentId；source generation 改变要求 fresh binding/worker；重装同 contract 的新 generation 也要求新的 binding/worker。

10. **Wheel Installer 支持的精确 subset 是什么？**
    仅支持 exact-hash wheel、Python tag py3/py314/cp314、ABI none、platform any、Root-Is-Purelib true、root purelib 或 .data/purelib；拒绝 native、Android native tag、manylinux/musllinux、sdist、VCS、editable、.pth、scripts、headers、data、platlib 和 build backend。

11. **site-packages 放在哪里？**
    files/siftalphax/environments/<project-id>/<environment-id>/generations/<generation-id>/site-packages/；它是 Project/generation-owned，不放 Runtime Base、Source Staging、Download Cache 或 Session Workspace。

12. **cache key 是什么？**
    实际 artifact bytes 的 sha256:<lowercase-hex>。URL、package name/version 都不是 cache identity；cache immutable、可跨 Project 复用、不作为 sys.path。

13. **atomic upgrade 如何工作？**
    在新 generation transaction 中下载、验证、安装和静态检查；旧 generation 保持 current/READY；新 generation 全部通过后以同目录 atomic pointer switch 替换 current.json；失败、取消或进程死亡不切换。

14. **alpha44 最小真机验收是什么？**
    P1 stdlib-only dedicated worker START/STATUS/STOP；P2 workerInstanceId + foundation ProcessBindingId；P3 Project A → Project B fresh worker；P4 同一 Project source generation A → B fresh binding/worker；P5 worker death structured failure；P6 Activity leave/re-entry 不错误复用 binding；P7 既有 M 行为不变且无 silent External fallback。environment generation switch 移到 alpha45；第三方 pure wheel A–J 验收属于 foundation 通过后的 alpha45。

15. **alpha44 最终名称与范围是什么？**
    **Process-Isolated Embedded R Foundation**：只建立可靠 per-runtime-load-binding worker/process boundary 和 typed lifecycle contract，不在本轮或 alpha44 foundation 中实现 wheel installer、resolver、cache、site-packages、UI 或版本升级。Pure-Python Environment v1 是 alpha45 candidate。

**No alpha44 production implementation was started.**
