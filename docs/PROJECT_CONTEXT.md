# SiftAlpha Studio 项目上下文

最后更新：2026-09-15（W2 完成度审计）
当前仓库：[kuashan/siftalpha-one](https://github.com/kuashan/siftalpha-one)  
当前分支：`main`  
当前实现版本：0.8.0-alpha25 / versionCode 101（W2 Runtime Identity 与 Web Discovery 合并基线，云端构建和真机 04B/04C/04D 已验证）
当前实现提交：[893229c](https://github.com/kuashan/siftalpha-one/commit/893229ce26d49a6ea22c79d6e2be85290cb8b0c3)
上一版发布：[W2 test APK · w2-test-80efac5](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-80efac5)  
最近一次带 Release 的 APK：[W2 test APK · w2-test-4e899c6](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-4e899c6)；alpha25 主线构建产物见 [Run #70 artifact](https://github.com/kuashan/siftalpha-one/actions/runs/34989822426/artifacts/10405122896)

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

## 2. 当前阶段

路线仍按 W0 → W1A → W1B → W1C → W2 → W3 → W4 → W5 推进。

| 阶段 | 当前状态 | 说明 |
|---|---|---|
| W0 | 已完成 | GitHub Actions 云端校验、构建、APK、签名和发布证据已经建立。 |
| W1A | 基本完成 | Compose、Material 3、主题和设置能力已有，但部分旧页面仍是 Views。 |
| W1B | 核心完成，持续收敛 | 已有状态快照、动作策略、生命周期和安全守卫；协调逻辑仍需继续从 Activity 收敛。 |
| W1C | 已完成主要验收 | 首页、导航、项目列表和筛选能力已落地并完成真机验证。 |
| W2 | 功能已合并，审计未关闭 | Runtime Identity、配置语义、项目工作区基础和 Web Discovery 闭环已进入 main；完成度审计仍发现 Activity 会话协调、Web 状态 ownership 和 Node/真机覆盖缺口。 |
| W3 | 部分完成 | 输出、脱敏、失败原因和日志能力已有，结构化诊断体验仍需完善。 |
| W4 | 底层能力较完整 | 环境、工具、缓存和清理保护已有，统一环境页面和完整验收仍待完成。 |
| W5 | 部分完成 | 编辑器、设置、语言机制已有；首次使用、无障碍、升级连续性等仍需验收。 |

## 3. 不可破坏的产品规则

### 3.1 状态必须分开表达

环境、配置、进程生命周期和 Web 可用性是四种不同事实，不能合并成一个“全部就绪”。

### 3.2 项目身份必须可信

项目操作使用 SAF 的稳定 `documentId`，不能使用项目名称、列表下标或临时排序位置作为身份。

### 3.3 运行安全

- PREPARE 与 START 分开，准备完成后由用户主动运行。
- 运行中的项目始终保留 STOP 能力。
- UI 按钮禁用只是反馈，底层动作入口仍必须再次检查。
- STOP 完成后，项目必须能够再次 START。
- 不确定状态时优先 STATUS 恢复，不能凭旧界面猜测进程是否存在。
- 清理项目环境、共享工具、缓存和删除源码必须分开确认。

### 3.4 Web 安全

Browser 只有在当前项目进程存在、Android 回环端点真实可达并且 URL 已验证时才开放。检测到“可能是网页”不等于允许打开浏览器。

### 3.5 隐私与配置

运行输出、配置值和日志不得泄露密钥。Studio 保存的配置使用 Android Keystore 保护并在运行时注入，不写回项目源码或 GitHub。

## 4. 当前架构基线

主要职责分布：

- `V04Activity`：Runtime Center 的页面组装、用户动作入口和结果协调；当前仍承担刷新、dispatch、恢复、配置和 Web 协调，需继续拆分。
- `ProjectUiSnapshot`：单项目不可变 UI 事实快照。
- `ProjectActionPolicy`：根据快照统一决定 PREPARE、START、STOP、STATUS、LOGS、CONFIGURE、Browser 等动作。
- ConfigurationModels：统一表达配置严重程度、发现来源、检测证据和脱敏配置项。
- ProjectConfigurationInspector：读取项目元数据、.env、.env.example 和 Python 环境变量读取，并附带来源证据。
- ProjectConfigurationUiController：配置列表、必填向导、建议项提示、来源证据、安全保存和运行时配置提示。
- ProjectConfigurationPreflight：纯配置就绪判断，同时计算 REQUIRED/OPTIONAL 的已配置和缺失数量。
- `RuntimeConfigurationDiagnostic`：只从运行输出中识别高可信的缺失配置，不猜测变量名。
- `ProjectSecretStore`：Android Keystore 保护的本地配置存储。
- `ProjectStore` / `V04ProjectGateway`：SAF 项目树、文件和项目身份访问。
- `WebProjectInspector`、`RuntimeWebPortDiscovery`、`RuntimeWebLogDiscoveryShell`：项目范围 Web 候选发现。
- `RuntimeWebEndpointProbe`、`RuntimeWebAvailabilityTracker`、`RuntimeWebStateStore`：候选 URL 的真实端点验证、轮询和持久化。
- `RuntimeIdentity` / `RuntimeIdentityStore`：START、STATUS、LOGS、STOP、恢复和 Web 发现使用的运行时身份。
- `ProjectRuntimeController`：生成 Python/Node 等运行命令并维持统一动作入口。

## 5. 配置语义

配置检测分为“必需项”和“建议配置”两层：

1. .project.json.requiredEnv 是项目显式声明的权威来源，条目可以明确标记为 REQUIRED 或 OPTIONAL。
2. Python 的 os.environ[NAME] 属于高可信必需读取（STATIC_REQUIRED_READ）。
3. os.getenv(NAME)、os.environ.get(NAME) 和 .env.example 属于建议配置（STATIC_OPTIONAL_READ / ENV_EXAMPLE），因为静态代码无法可靠判断它们是否真的影响运行。
4. NAME 已在项目 .env 中有非空值，或已由 Studio 安全保存，则视为已配置；配置页面不显示值本身。
5. 运行结果明确报告缺失的环境变量时，该变量以 RUNTIME_DIAGNOSTIC 来源升级为当前修复周期的必需项，并影响下一次启动前预检。
6. 每个配置项都保留来源和证据（项目文件、代码文件/行号或运行诊断），让用户知道为什么出现提醒。
7. 普通硬编码 URL 不会自动判定为需要用户配置；只有通过项目声明、环境变量或运行结果明确要求时才进入配置流程。
8. 建议配置显示提醒但不阻止运行。只有 REQUIRED 缺失时，才显示必填向导或在后续运行前阻止 START。

## 6. 构建与发布基线

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

当前正式 main 构建证据：GitHub Actions [Run #70](https://github.com/kuashan/siftalpha-one/actions/runs/34989822426)，head 为 `893229ce26d49a6ea22c79d6e2be85290cb8b0c3`，`Build and verify APK` 成功；artifact 为 `siftalpha-w0-70`（ID `10405122896`）。该构建完成仓库校验、`testDebugUnitTest`、`assembleDebug`、APK 元数据和签名证据收集。

## 7. 历史 alpha16 验收计划（已过时，仅保留记录）

当前 W2 配置语义实现（历史基线，不是当前开发计划）：

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
6. 继续完善单项目工作区和 W2 的完整任务闭环。

## 8. 记录维护约定

- 开发行为写入 [DEV_LOG.md](DEV_LOG.md)。
- 测试状态写入 [TEST_MATRIX.md](TEST_MATRIX.md)。
- 任何“通过”必须注明是云端通过、真机通过，还是用户确认通过。
- 不把旧仓库、旧分支、旧版本号重新当作当前基线。


## 9. W2 Runtime/Web 展示状态分离

Runtime 生命周期与 Web 可用性是两个独立维度：

- `RuntimePresentationState` 只表达进程生命周期：STARTING、RUNNING、STOPPED/终止状态。
- `RuntimeWebUiStatus` 独立表达 Web：AVAILABLE、DETECTING、UNAVAILABLE、WAITING 或 AUTO_DETECT。
- 日志中发现的 URL 只是 candidate URL，不能单独把项目判定为 Web，也不能让 Web 未就绪覆盖 Runtime RUNNING。
- Browser 仍然只在真实端点可达且 URL 已验证时开放。
- 本轮不改变 START/PREPARE/STOP 执行流程、Configuration 系统或 Web 探测执行器。

本轮版本为 `0.8.0-alpha18 / versionCode 94`，用于覆盖安装。GitHub Actions Run #44 已成功完成 `testDebugUnitTest assembleDebug`，APK SHA-256 为 `3e61e76b1c80ff11dba53d9afb135c9759cc742fe0b489f704b22d550629f922`；[Run #44 artifact ZIP](https://github.com/kuashan/siftalpha-one/actions/runs/34918070668/artifacts/10377191664) 可下载。直接 APK：[下载 alpha18 测试 APK](https://github.com/kuashan/siftalpha-one/releases/download/w2-test-febcabb/app-debug.apk)；Release：[w2-test-febcabb](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-febcabb)。Run #45：[Actions](https://github.com/kuashan/siftalpha-one/actions/runs/34918441426) 已成功，真机结果待用户验收。


## 10. W2 RuntimeLifecycleStore SharedPreferences Migration Fix

RuntimeLifecycleStore 读取历史 SharedPreferences 时必须兼容旧字符串布尔值和当前 Boolean 值。读取逻辑通过安全类型解析和迁移处理，异常类型回退到默认状态；新写入使用按项目、按字段区分的键。该修复只涉及生命周期恢复数据的持久化读取，不改变 Runtime 执行、START/PREPARE/STOP 或 Configuration。

候选云端验证 Run #1 已成功完成单元测试和 APK 组装；[查看候选 Run #1](https://github.com/kuashan/siftalpha-one/actions/runs/34922851615)。正式 main 分支 Run #47 已完成单元测试、APK 组装、稳定签名和发布；[查看 Run #47](https://github.com/kuashan/siftalpha-one/actions/runs/34923173498)。APK：[直接下载](https://github.com/kuashan/siftalpha-one/releases/download/w2-test-4e899c6/app-debug.apk)，SHA-256 为 `e7ebfdc81fa770ef020c4527a2069103b1fdcae287fdd1af9c4291307ef2496a`；该条为历史记录。

## 11. W2 合并后正式基线与完成度审计

本节是截至 `main@893229ce26d49a6ea22c79d6e2be85290cb8b0c3` 的当前审计结论；前文历史记录保留，不再作为当前版本事实。

### 11.1 证据分类

- **unit-test verified**：当前源码包含 314 个 JUnit `@Test` 方法；覆盖 Runtime Identity、配置 preflight/editor、ActionPolicy、生命周期恢复、Python/Node adapter、Web URL/端点/发现和失败诊断。
- **GitHub Actions verified**：Run #70（ID `34989822426`）成功；仓库 validators、`testDebugUnitTest`、`assembleDebug`、APK metadata、`apksigner` 和稳定签名证据收集均成功。artifact `siftalpha-w0-70`（ID `10405122896`）未过期。
- **real-device verified**：alpha25 已完成真实 Android 设备 smoke；Test 04B 明确 loopback Web discovery 成功，04C 拒绝裸 `PORT`/模糊端口和 external URL，04D 验证 Start → Browser → Logs/仍 RUNNING → Stop → Restart。
- **user-confirmed**：上述 04B、04C、04D PASS 由用户提供并确认；它们不替代尚未覆盖的导入、配置全流程、Node 和 App 重启恢复测试。

### 11.2 已完成的 W2 工作

- 修复 Runtime Identity wiring，使 START、STATUS、LOGS、STOP、恢复和 Web discovery 共享 project/runtime identity；Android 真机已进入 `FULL_IDENTITY`，guest root 为 `ALIVE`。
- 保持 project-scoped PID/PGID 与 socket inode ownership discovery。Android/Termux/PRoot 诊断确认 `/proc/<pid>/fd` 能发现 socket inode，但 `/proc/net/tcp{,6}` 及 per-PID net 表不可用；没有引入全端口扫描、全局进程扫描或不可信端口猜测。
- 将 `RuntimeWebLogDiscoveryShell` 接入 Python 当前 runtime log 的 bounded fallback。procfs candidate 优先；只有 procfs 没有可信结果时才读取当前 runtime log，并且仍交给 Android `RuntimeWebEndpointProbe` 做最终可达性验证。
- 完成 Web Discovery Contract：candidate 与 reachable 分离；loopback URL 才能成为候选，Browser 必须满足当前 Runtime RUNNING、URL 安全校验和真实 endpoint probe。
- 完成 alpha25 cleanup，删除 per-PID TCP/TCP6 investigation instrumentation，保留 Web 状态协议和少量基础 procfs diagnostics；PR #1 已解决冲突并合并，merge commit 为 `893229ce26d49a6ea22c79d6e2be85290cb8b0c3`。

### 11.3 当前审计缺口

- Project identity 已通过 SAF `documentId` 建立可信边界，Runtime Identity 也已经通过 `FULL_IDENTITY` 真机结果验证；尚未完全闭合的是 `Project Identity → Runtime Identity / Runtime Generation → Runtime Lifecycle → Web Candidate Ownership → Recovery` ownership chain。
- `RuntimeWebStateStore` 当前主要持久化 `candidateUrl`、`framework` 和 `detectedAtEpochMs`，并主要按 `projectKey` 存储；如果 Runtime A 停止后 Runtime B 启动，旧 candidate 仍可能存在。Endpoint Probe 只能证明端点可达，不能单独证明端点属于当前 Runtime；若其他本地服务复用端口，可能出现可达但 ownership 错误的结果。
- `V04Activity` 仍同时拥有刷新、UI 组装、动作二次守卫、Runtime orchestration、配置 orchestration、Web orchestration、恢复和结果归并。Coordinator 方向仍然有价值，但应作为逐步实现 Runtime Session Ownership Boundary 的结构手段，而不是为了“架构漂亮”进行一次性大规模重构。
- Node 主运行时仅支持受限的 npm/managed Node 与显式 `run` 或 `scripts.start` 合约；Node/Vite 主要有 JVM 结构测试，缺少与 Python 同等的真实 Android 运行验收。
- 导入、配置编辑、Output、App 重启后 recovery 和清理的当前 alpha25 真机覆盖不完整；现有测试主要是 JVM/生成脚本测试。
- `RuntimeWebPortDiscovery` 仍输出紧凑的 `SIFTALPHA_WEB_DEBUG_*` PID/FD/TCP 可访问性摘要。它不改变发现结果，但属于默认日志噪声，是否保留应在后续 cleanup 中明确决定。

### 11.4 审计结论与后续优先级

**P0 — Runtime Session Ownership Boundary**

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

- **P1 — W2 应完成**：把 Web candidate 绑定到 runtime identity/generation；在 STOP/CLEAN 明确清理 candidate；完成 App restart recovery、Import、Configuration 和 Node Runtime 真机验证；按需要逐步抽取 coordinator。
- **P2 — W3/W4 可延后**：Workspace Compose 化、更完整结构化 Failure UI、Node 能力扩展、进一步 Activity cleanup、默认 debug 日志 cleanup 和签名流程进一步加固。

若 W2 只允许再做一个开发任务，优先完成 **Runtime Session Ownership Boundary**。它直接补齐当前最重要的 correctness/ownership 缺口，并为后续 Web candidate 清理、Recovery、Restart 和 coordinator 抽取提供统一边界；`V04Activity` 过大是后续结构表现，不是第一理由。

当前 W2 完成度审计结论：**W2 NOT COMPLETE**。阻塞原因是 `Project → Runtime Session → Web Candidate` 的 ownership chain 尚未完整闭合，而不是单独因为 `V04Activity` 规模较大。
