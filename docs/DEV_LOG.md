# SiftAlpha Studio 开发日志

本日志按时间顺序追加。每条记录包含目标、变更、验证和后续事项。公开日志不记录密码、密钥值、用户项目配置值或其他敏感内容。

历史条目中的 W2、W3、W4、W5 仅按当时命名记录已发生的工作、构建或发布，不表示当前 Roadmap 阶段。

## 2026-09-14 · 项目记录初始化

### 目标

将项目上下文、开发历史、构建证据和测试状态保存到当前唯一开发仓库，避免后续对话遗失连续性。

### 当前基线

- 仓库：[kuashan/siftalpha-one](https://github.com/kuashan/siftalpha-one)
- 分支：`main`
- 版本：`0.8.0-alpha14` / `versionCode 90`
- 当前发布：[w2-test-0ee299e](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-0ee299e)
- 当前 APK SHA-256：`a1550c94aa8a6183f3705e8d3450591eeb5beac4ec9fbdf55965b7603ec3b6a7`

## 2026-09-14 · 干净源码迁移到新仓库

### 目标

以全新的 `siftalpha-one` 作为唯一开发仓库，避免旧仓库历史记录、开发文档和旧工作流造成混淆。

### 结果

- 迁移了 W2 最终候选源码、Gradle 必要文件和测试文件。
- 修复了启动图资源导入为空的问题。
- 排除了旧开发日志、旧工作流和旧仓库上下文。
- 初始导入版本为 `0.8.0-alpha12` / `versionCode 88`。
- 导入基线提交：[5f8e095](https://github.com/kuashan/siftalpha-one/commit/5f8e095b41a995cd9adad76cf0a2d0dcd4831cf1)。

### 备注

之后为恢复云端构建和持续验收，当前仓库重新加入了必要的 GitHub Actions 工作流；这不等同于恢复旧仓库历史。

## 2026-09-14 · STOP 后无法再次运行修复

### 问题

用户发现点击 STOP 后，项目全部操作变灰，无法再次运行。

### 修复

在 STOP 结果处理完成后清除过期的恢复标记，使动作策略重新识别项目已经停止，并恢复下一次 START 能力。

### 验证

- 版本提升至 `0.8.0-alpha13` / `versionCode 89`。
- 发布：[w2-test-c000564](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-c000564)。
- APK SHA-256：`e7547fb124dd86d1e6317249e06d8da29b86a9036011a5394dbcc8cdd3423495`。
- 用户已确认本轮测试全部通过。

## 2026-09-14 · Chrome 返回白屏修复

### 问题

从应用检测到的可视化页面进入 Chrome，再返回 Studio 时短暂出现约 1–2 秒白屏，随后恢复正常。

### 原因

`V04Activity.onResume()` 原先同步执行完整刷新：先清空项目列表，再进行 SAF 项目读取、配置扫描和 Web 检测。返回应用时，旧界面已被清除，而新界面尚未准备好。

### 修复

提交：[41bcb5d](https://github.com/kuashan/siftalpha-one/commit/41bcb5dbe17af2f78c4b7b0dde7888cbc62dbf29)

- 将项目、配置和 Web 读取移到单线程后台刷新。
- 保留当前已渲染界面，后台数据准备好后再一次性替换。
- 使用刷新代次丢弃过期结果。
- 合并 onStart、onResume、结果回调和 Web 探测触发的重复刷新。
- 页面销毁时关闭刷新执行器。

### 验证

- 发布：[w2-test-cadf3c7](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-cadf3c7)。
- 云端构建和签名验收通过。
- 用户已确认该白屏问题解决，进入网页和返回应用后功能正常。

## 2026-09-14 · 配置语义和配置向导修复

### 用户反馈

两个脚本都显示两项配置。用户认可保留配置提醒，但要求“不需要配置时不能阻止运行”，并希望配置来自脚本实际需要，而不是误把候选项当成必填项。

### 审计结论

检测器本身已经区分了显式必需项和候选项，但界面把所有未配置项都送进了配置向导：

`pendingItems = items.filterNot { isConfigured(...) }`

因此候选项虽然内部标记为可选，用户仍会先看到一个“第 1/2 项”的配置流程，造成“必须配置两项”的印象。

同时，运行时新发现的缺失环境变量只保存为 UI 提示，没有完整进入下一次启动的 preflight。

### 修复

实现提交：[788a235](https://github.com/kuashan/siftalpha-one/commit/788a235abc653df7eb6b479e03868ade9c1deaeb)

- 配置向导只接收“未配置的必需项”。
- 只有候选项时，直接显示配置列表；候选项仍会标注为可选提醒。
- 运行时明确报告的缺失变量会被加入 preflight 的有效必需集合。
- 新增单元测试覆盖运行时缺失项升级为必需项、以及已保存值满足该项的情况。
- 版本提升至 `0.8.0-alpha14` / `versionCode 90`，确保可覆盖安装。

### 云端验证

- 实现提交构建：[Run #26](https://github.com/kuashan/siftalpha-one/actions/runs/34880102358)，成功。
- 发布触发提交：[0ee299e](https://github.com/kuashan/siftalpha-one/commit/0ee299e0635e5ad8fcb23098bb466b9677eba00e)。
- 发布构建：[Run #27](https://github.com/kuashan/siftalpha-one/actions/runs/34880577089)，成功。
- 包信息：`com.siftalpha.studio` / `0.8.0-alpha14` / `versionCode 90`。
- APK 签名证书摘要与稳定测试签名一致。
- APK SHA-256：`a1550c94aa8a6183f3705e8d3450591eeb5beac4ec9fbdf55965b7603ec3b6a7`。

### 真机状态

- 用户已确认前一版 STOP、运行流程和 Chrome 返回白屏问题通过。
- 本版配置候选项语义、覆盖安装和回归场景等待用户验收。

## 后续记录格式

后续每次开发按以下结构追加：

`日期 · 主题`

- 用户问题或产品目标
- 代码/配置变更
- 提交和云端构建
- APK/签名证据
- 云端测试结果
- 真机测试结果
- 未完成事项和下一步


## 2026-09-14 · W2 Configuration Semantics Refinement

### 用户目标

参考《SiftAlpha Studio W2 Configuration Semantics Refinement》重新定义配置语义：

- 区分 REQUIRED（必需配置）与 OPTIONAL（建议配置）。
- 建议配置只提醒，不阻止 START。
- 只有可信的真实必需配置缺失才阻止 START。
- 在界面展示配置来源和检测证据，避免把候选项误解为必填项。

### 实现

- 新增统一配置模型：ConfigurationSeverity、ConfigurationSource、ConfigurationEvidence 和 ConfigurationItem。
- ProjectConfigurationInspector 为项目声明、Python 直接读取、Python 可选读取、.env.example 和运行时诊断记录不同来源。
- Python 文件证据包含相对文件路径和行号；项目声明证据标记 .project.json.requiredEnv。
- ProjectConfigurationPreflight 同时计算必需配置和建议配置的已配置/缺失数量。
- ProjectUiSnapshot 暴露 canRun、必需/建议计数和 ConfigurationSummary。
- ProjectActionPolicy 通过快照统一阻止真实必需缺失；建议配置不影响 START。
- 配置页面只对未配置的必需项打开向导；建议项先显示“填写/稍后”说明，并在列表中显示来源和证据。
- 保留 RuntimeController、Termux 执行、PREPARE/START 核心流程、SecretStore、SAF 和 Web 检测不变。
- 增加解析器、preflight 和 action policy 测试，覆盖必需缺失、建议缺失、运行时诊断升级和来源证据。

### 提交与云端验证

- 初始实现提交：[a2763b8](https://github.com/kuashan/siftalpha-one/commit/a2763b8ee1247c5a4fc6e7197c2cc1783d5708f7)。
- 首次构建 Run #29 因行号证据生成中的 Kotlin 换行字面量失败；仓库校验和环境准备通过。
- 修复提交：[0873f23](https://github.com/kuashan/siftalpha-one/commit/0873f23e4626757b4cf2bd0681adc24e98492ecb)。
- Run #30：[Actions](https://github.com/kuashan/siftalpha-one/actions/runs/34885104600) 成功：仓库校验、单元测试、APK 组装、签名和证据收集全部通过。
- 发布版本：0.8.0-alpha15 / versionCode 91。
- 发布提交：[80efac5](https://github.com/kuashan/siftalpha-one/commit/80efac5f1324c91229cf53d884b90dd8bf2bcf4c)。
- 发布构建：[Run #32](https://github.com/kuashan/siftalpha-one/actions/runs/34885615075)，成功。
- Release：[w2-test-80efac5](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-80efac5)。
- APK：[直接下载](https://github.com/kuashan/siftalpha-one/releases/download/w2-test-80efac5/app-debug.apk)。
- APK SHA-256：4da88d0d8d2bd757d5a0b29f6d83ab9d8b9fbe15bf2d844659b43c45a439d562。
- 稳定测试证书 SHA-256：3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928。

### 真机状态与下一步

- 新 APK 已发布，等待用户进行覆盖安装和真机验收。
- 使用“直接覆盖安装，不卸载上一版”的方式测试配置页面与两个脚本。
- 重点确认：只有建议项时不出现强制向导；建议项缺失不阻止 PREPARE → START；来源和证据显示清楚。


## 2026-09-15 · W2 Optional Configuration Editing Flow

### 用户问题

OPTIONAL（建议配置）已经能够被检测和展示，但点击配置后会停留在说明弹窗，用户无法稳定进入输入框，因而没有形成“提醒 → 编辑 → 保存 → 状态更新”的闭环。

### 修复

- 配置入口先显示当前 REQUIRED/OPTIONAL 摘要，并提供“查看配置”入口。
- 配置编辑列表继续使用已有的 ConfigurationItem，保留所有配置项的严重程度、来源和检测依据。
- 点击配置项直接打开输入框；OPTIONAL 不再经过没有输入入口的说明弹窗。
- 保存后写入既有 ConfigurationStore，并刷新 ProjectUiSnapshot/UI 状态。
- OPTIONAL 保存只改变配置状态，不改变 START 权限，也不会因为保存建议项自动启动项目。
- REQUIRED 缺失仍由原有向导处理，并继续阻止 START。
- 未修改 ConfigurationAnalyzer、RuntimeController、PREPARE/START/STOP、SAF、Web 检测或 Secret Storage。

### 提交与云端验证

- 应用修复提交：[5e2d739](https://github.com/kuashan/siftalpha-one/commit/5e2d7395acef37ab7965e517918c02fef19c7cb6)。
- 版本：0.8.0-alpha16 / versionCode 92，用于覆盖安装。
- Run #34 因 GitHub Actions 的 Android SDK 初始化请求已不存在的 tools 包失败；该次未进入应用编译。
- 工作流修复提交：[903ad94](https://github.com/kuashan/siftalpha-one/commit/903ad94cbe47a1d24ff14f65788312cd4ea3352f)，改为只请求现行 platform-tools。
- Run #35：[Actions](https://github.com/kuashan/siftalpha-one/actions/runs/34911589787) 成功：SDK、仓库校验、单元测试、APK 组装、签名和证据收集全部通过。
- 发布：[w2-test-8a91895](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-8a91895)。
- Run #37：[Actions](https://github.com/kuashan/siftalpha-one/actions/runs/34912031461) 成功。
- APK：[直接下载](https://github.com/kuashan/siftalpha-one/releases/download/w2-test-8a91895/app-debug.apk)。
- APK SHA-256：e1ecfd0c62315c1d940ab0f3d2c21a0d466bbb11fdbc9f2214c6061c84d26ba7。
- 稳定测试证书 SHA-256：3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928。

### 真机测试与下一步

- 新 APK 已发布，等待用户进行 alpha15 → alpha16 的覆盖安装和真机验收。
- 发布后直接覆盖安装 alpha15，不卸载；重点测试 OPTIONAL 进入输入框、保存后显示已配置、跳过仍可运行，以及 REQUIRED/Runtime 回归。

## 2026-09-15 · W2 Configuration Editor UX Simplification

### 修改目标

- 配置按钮直接进入配置编辑列表，不再先显示 Configuration Summary。
- 编辑界面明确标记为“项目配置编辑”。
- REQUIRED 与 OPTIONAL 使用同一编辑列表，均可直接填写。
- OPTIONAL 留空或保存不会影响 START 权限。
- 删除旧的 Summary → item list → value editor 多层弹窗路径，改为一个包含输入框的编辑弹窗。
- 已配置项输入框留空时保持原值；仍保留已保存 Studio 配置的清除入口。

### 测试

新增 `ProjectConfigurationEditorFlowTest`，覆盖：

- OPTIONAL 编辑并生成保存计划；
- REQUIRED 编辑、保存和缺失阻止；
- Mixed 配置中 OPTIONAL 留空不阻止保存；
- STOP 后 CONFIGURE 与 START 仍可用，配置仍可编辑保存。

### 构建与真机

- 版本提升为 `0.8.0-alpha17 / versionCode 93`，用于从 alpha16 直接覆盖安装。
- GitHub Actions Run #41 成功：仓库校验、单元测试、APK 组装、签名和证据收集全部通过。
- Run #41：[Actions](https://github.com/kuashan/siftalpha-one/actions/runs/34914830358)。
- Run #41 构建产物：[artifact ZIP](https://github.com/kuashan/siftalpha-one/actions/runs/34914830358/artifacts/10374759918)。
- APK SHA-256：b1ed2c060af2804bae611a543852f8e2afc8d70650de00c661acb9aefbfc17da。
- 稳定测试证书 SHA-256：3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928。
- 正式 W2 测试 APK：[直接下载](https://github.com/kuashan/siftalpha-one/releases/download/w2-test-6e259f7/app-debug.apk)。
- Release：[w2-test-6e259f7](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-6e259f7)。
- Run #42：[Actions](https://github.com/kuashan/siftalpha-one/actions/runs/34915100958) 成功，包含单元测试、APK 组装、签名、证据收集和发布。
- APK SHA-256：b1ed2c060af2804bae611a543852f8e2afc8d70650de00c661acb9aefbfc17da。
- 稳定测试证书 SHA-256：3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928。



## 2026-09-15 · W2 Runtime Presentation State Separation

### 问题

Test_04_Default_Value 的 Python 项目已经由 Runtime 输出确认进入 RUNNING，但 Android UI 长时间显示“正在启动”。原因不是配置缺失、Python 执行失败或 START/PREPARE/STOP 执行失败，而是 Web 就绪展示逻辑覆盖了 Runtime 生命周期。

### 原因

Runtime 的 RUNNING 状态曾因 `webExpected=true` 且端点暂不可达而被重新展示为 STARTING。普通日志中的本地 URL 也可能在端点验证前被保存为 Web 线索，使无实际 HTTP 服务的项目进入等待 Web 的展示路径。

### 修改

- `RuntimePresentationState` 现在只由进程 RuntimeState 决定，RUNNING 不会被 Web 不可达改写为 STARTING。
- `RuntimeWebUiStatus` 独立表达 AVAILABLE、DETECTING、UNAVAILABLE、WAITING 和 AUTO_DETECT。
- Runtime 发现的 URL 明确作为 candidate URL 保存；只有配置/声明或 Android 端点探测结果参与 Web 能力判断。
- 未修改 `ProjectRuntimeController`、Python/ManagedProcess Runtime、RuntimeEnvironmentComposer、PREPARE/START/STOP 执行流程、Configuration 系统、SAF 或 Web 探测执行器。

### 验证状态

- GitHub Actions Run #44：成功（仓库校验、`testDebugUnitTest`、`assembleDebug`、签名和证据收集全部通过）。[查看 Run #44](https://github.com/kuashan/siftalpha-one/actions/runs/34918070668)
- 云端 Gradle 结果：`testDebugUnitTest assembleDebug` 成功，构建耗时约 3 分 5 秒。
- 当前云端 APK 产物：[下载 artifact ZIP](https://github.com/kuashan/siftalpha-one/actions/runs/34918070668/artifacts/10377191664)；APK SHA-256：`3e61e76b1c80ff11dba53d9afb135c9759cc742fe0b489f704b22d550629f922`。
- 稳定测试证书 SHA-256：`3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`。
- GitHub Actions Run #45：成功，已完成同一版本的云端单元测试、APK 组装、签名、证据收集和发布。[查看 Run #45](https://github.com/kuashan/siftalpha-one/actions/runs/34918441426)
- 直接 APK：[下载 alpha18 测试 APK](https://github.com/kuashan/siftalpha-one/releases/download/w2-test-febcabb/app-debug.apk)
- Release：[w2-test-febcabb](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-febcabb)
- 发布工作流 artifact：[下载 artifact ZIP](https://github.com/kuashan/siftalpha-one/actions/runs/34918441426/artifacts/10376164249)
- 真机测试：待用户使用 alpha18 覆盖安装 APK 验收。
- 版本：`0.8.0-alpha18 / versionCode 94`，用于直接覆盖安装。


## 2026-09-15 · W2 RuntimeLifecycleStore SharedPreferences Migration Fix

### 问题

App 进入运行中心时，`RuntimeLifecycleStore.read()` 在读取历史 SharedPreferences 时因 `String` 被当作 `Boolean` 读取而抛出 `ClassCastException`，导致 `V04Activity.restoreStoredState()` 闪退。

### 修改

- 读取布尔值时通过 `SharedPreferences.all` 安全识别 `Boolean` 和字符串 `"true"/"false"`。
- 成功识别的历史字符串值会迁移为当前布尔类型；缺失、未知类型或读取异常回退到安全默认值。
- 修复生命周期存储键按项目和字段生成，保留旧键作为只读兼容回退，避免历史状态再次触发崩溃。
- 新增字符串旧数据、Boolean 新数据、迁移后二次读取、空数据和异常类型回退测试。
- 未修改 Runtime 执行流程、START/PREPARE/STOP 逻辑或 Configuration 系统。

### 验证状态

- 候选分支提交：`c8beeab`。
- 候选云端验证 Run #1：成功，严格按“先 `testDebugUnitTest`、后 `assembleDebug`”执行。[查看候选 Run #1](https://github.com/kuashan/siftalpha-one/actions/runs/34922851615)
- 候选单元测试：通过；候选 APK 组装：通过。
- 正式 main 分支 Run #47：成功，完成单元测试、APK 组装、稳定签名、证据收集和预发布 Release。[查看 Run #47](https://github.com/kuashan/siftalpha-one/actions/runs/34923173498)
- 正式 APK：[直接下载 alpha19 测试 APK](https://github.com/kuashan/siftalpha-one/releases/download/w2-test-4e899c6/app-debug.apk)
- Release：[w2-test-4e899c6](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-4e899c6)
- APK SHA-256：`e7ebfdc81fa770ef020c4527a2069103b1fdcae287fdd1af9c4291307ef2496a`
- 稳定测试证书 SHA-256：`3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`
- Run #47 artifact：[下载 artifact ZIP](https://github.com/kuashan/siftalpha-one/actions/runs/34923173498/artifacts/10378838326)
- 真机测试：待使用 `0.8.0-alpha19 / versionCode 95` 覆盖安装验收。

## 2026-09-15 · W2 Web Discovery Diagnostic Enhancement

### 问题

真实 HTTP 服务已经输出 `HTTP_READY`，但自动发现仍可能只报告
`SIFTALPHA_WEB_AUTODISCOVERY=NO_LISTEN_PORT`。旧输出无法区分没有项目进程、
无法读取 procfs、没有 socket inode、inode 未匹配监听表，还是确实没有监听端口。

### 修改

- 在 `RuntimeWebPortDiscovery` 增加独立的诊断状态分类：
  `NO_PROJECT_PIDS`、`PROCFS_UNREADABLE`、`NO_SOCKET_INODES`、
  `NO_INODE_MATCH`、`NO_LISTEN_PORT`、`NO_HTTP_ENDPOINT`。
- 在发现阶段增加 `LISTEN_PORT_FOUND` 中间状态和
  `SIFTALPHA_WEB_DISCOVERY_STAGE=LISTEN_FOUND`，用于区分“发现监听端口”和“HTTP
  检查可达”。
- 成功时增加 `SIFTALPHA_WEB_DISCOVERY_STATUS=PASS`；失败时按主进程范围和
  PRoot 范围分别输出 `source`。
- 保留原有 `SIFTALPHA_WEB_PRIMARY_SCOPE`、`SIFTALPHA_WEB_GUEST_SCOPE` 和
  `SIFTALPHA_WEB_AUTODISCOVERY` 输出，未改变现有发现算法及兼容日志。
- 未修改 Runtime 生命周期、PREPARE/START/STOP、Configuration 或 Browser
  启用条件。

### 验证状态

- 诊断分类单元测试已补充，等待可用的项目 Gradle 环境执行。
- 诊断分类单元测试已补充；本地执行环境不含 Gradle Wrapper，云端负责执行项目测试。
- 已通过 `git diff --check` 以及主进程/PRoot 诊断 shell 语法检查。
- GitHub Actions Run #49 成功：仓库校验、`testDebugUnitTest`、`assembleDebug`、APK 证据收集全部通过；[查看 Run #49](https://github.com/kuashan/siftalpha-one/actions/runs/34927655920)。
- 云端构建结果：`BUILD SUCCESSFUL`，`testDebugUnitTest assembleDebug` 耗时约 1 分 8 秒。
- APK：包名 `com.siftalpha.studio`，版本 `0.8.0-alpha19`，versionCode `95`；APK SHA-256 为 `851925953f85b11f52d6d2d504ce92b7c9ae86ce419df1c1db5f294095a243d3`。
- 稳定测试证书 SHA-256：`3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`。
- 云端 artifact：[下载 siftalpha-w0-49](https://github.com/kuashan/siftalpha-one/actions/runs/34927655920/artifacts/10379489647)；Release 发布步骤已跳过。
- 已推送当前范围验证提交 `14733f7`，未增加额外功能；等待用户进行真机覆盖安装测试。

## 2026-09-15 · W2 Completion Audit 与项目文档同步（历史审计记录）

### 审计基线

- 仓库：[kuashan/siftalpha-one](https://github.com/kuashan/siftalpha-one)。
- 审计目标：`main`。
- 当前 main HEAD：`893229ce26d49a6ea22c79d6e2be85290cb8b0c3`。
- 当前正式开发版本：`0.8.0-alpha25` / `versionCode 101`，包名 `com.siftalpha.studio`。
- 当前开放 PR：无。
- `AGENTS.md`：仓库中不存在；本次按现有项目文档维护规则执行。

### 已完成工作记录

- Runtime Identity wiring repair 已完成；`START`、`STATUS`、`LOGS`、`STOP`、Recovery 和 Web Discovery 共用 project/runtime identity。
- Android 真机已验证 `FULL_IDENTITY`、`FULL_IDENTITY` source 和 guest root `ALIVE`。
- project-scoped socket ownership discovery 已保留。诊断确认 Android/Termux/PRoot 中 `/proc/<pid>/fd` 可以得到 socket inode，但 `/proc/net/tcp`、`/proc/net/tcp6` 以及 per-PID network table 不可用；没有采用全端口扫描、全局进程扫描或不可信端口猜测。
- `RuntimeWebLogDiscoveryShell` 已正式接入 Python 当前 Runtime log 的 bounded fallback。procfs discovery 先执行，只有没有可信 procfs endpoint 时才读取当前 runtime log；日志候选仍必须通过 Android Endpoint Probe。
- Web Discovery Contract 已固定：candidate 不等于 reachable；仅本地 loopback candidate 可进入探测；Browser 只在当前 Runtime RUNNING 且 endpoint probe 成功后开放。
- alpha25 cleanup 已删除 per-PID TCP/TCP6 investigation-only instrumentation，没有恢复这些调查代码；保留 Web 状态协议和少量基础 procfs 诊断。
- PR #1 冲突已解决并合并；merge commit 为 `893229ce26d49a6ea22c79d6e2be85290cb8b0c3`。

### 验证证据分类

- **unit-test verified**：当前源码有 314 个 JUnit `@Test` 方法；涵盖 Runtime Identity、Configuration、ActionPolicy、生命周期、Python/Node adapter、Web Discovery、Endpoint Probe、Browser 状态和失败诊断。
- **GitHub Actions verified**：SiftAlpha W0 Cloud Build Run #70（ID `34989822426`）成功；仓库 validators、`testDebugUnitTest`、`assembleDebug`、APK metadata、`apksigner` 和稳定测试签名证据收集成功。artifact 为 `siftalpha-w0-70`（ID `10405122896`）。
- **real-device verified**：alpha25 smoke 已完成。Test 04B 明确 loopback Web discovery 和 Browser 打开项目页面 PASS；Test 04C 无 HTTP server 时拒绝裸 `PORT`、模糊端口和 external URL PASS；Test 04D Start → Browser available → Logs/Runtime remains RUNNING → Stop → Restart PASS。
- **user-confirmed**：上述 04B、04C、04D PASS 由用户提供并确认。导入、配置编辑全流程、Node 主运行时和 App 重启后 recovery 没有被这些结果覆盖。

### 原阶段完成度审计摘要（历史记录）

- 已完成：Runtime Identity、Python Runtime、Prepare/Start/Status/Logs/Stop、Web candidate/probe/browser contract、配置 REQUIRED/OPTIONAL 语义和基本单项目入口。
- 部分完成：Import/SAF 全链路当前版本真机覆盖、Workspace 独立协调层、Output 全链路、App 重启 recovery、Node Runtime 真机验收、Failure UI 结构化程度。
- 已识别首要风险：Project identity 已可信，Runtime Identity 也已可信，但 `Project Identity → Runtime Identity / Runtime Generation → Runtime Lifecycle → Web Candidate Ownership → Recovery` ownership chain 尚未完全闭合。`RuntimeWebStateStore.clear()` 尚未在 STOP/CLEAN 结果路径统一调用，持久 candidate 的跨 Runtime 生命周期 ownership 需要加固。
- 当时审计结论：**W2 NOT COMPLETE**；这不是 Web 04B/04C/04D 功能失败，而是 `Project → Runtime Session → Web Candidate` 的 ownership chain、恢复边界和覆盖范围当时尚未达到关闭标准。该结论仅保留为历史审计记录，不代表当前 Roadmap 状态。

### 原审计后续事项（现归入产品 backlog / hardening）

**历史审计中的 P0：Runtime Session Ownership Boundary（现归入产品硬化 backlog）**：建立最小 project-scoped runtime session ownership 模型，把 project identity、runtime identity/runtime generation、lifecycle state、current runtime candidate URL 和 recovery state 明确绑定。P0 首先解决 correctness/ownership，不把“Activity 太大”本身作为第一理由。

最低生命周期合同：

- `START` 创建新的 runtime session/generation，旧 session candidate 不得自动继承。
- `RUNNING` 的 Web candidate 必须属于当前 runtime session；`LOGS` 只能读取当前 runtime 对应日志。
- `STATUS` 恢复必须验证当前 runtime identity。
- `STOP` 结束当前 session，并 invalidated/cleared 当前 candidate；`CLEAN` 使项目相关 session/candidate state 失效。
- `RESTART` 创建新的 session/generation；`RECOVERY` 只能恢复仍匹配当前 identity 的 session 信息，相同 `projectKey` 不能单独恢复旧 candidate。

**产品硬化与验证 backlog**：把 Web candidate 绑定 runtime identity/generation，在 STOP/CLEAN 明确清理；完成 App restart recovery、Import、Configuration、Node Runtime 真机验证，并按需要逐步抽取 coordinator。

**后续体验与基础设施增强 backlog**：Workspace Compose 化、更完整结构化 Failure UI、Node 能力扩展、进一步 Activity cleanup、默认 debug 日志 cleanup 和签名流程进一步加固。

Coordinator 是渐进式结构手段：先建立最小 `RuntimeSession`/`RuntimeSessionState`/`RuntimeGeneration` 等价 abstraction，不要求一次性重写 `V04Activity`；再逐步迁移 refresh、dispatch、recovery、Web invalidation，最后减少 Activity orchestration 职责。

如果当前只允许再做一个开发任务，优先完成 **Runtime Session Ownership Boundary**，因为它直接补齐当前最重要的 ownership 缺口，并为 Web candidate 清理、Recovery、Restart 和 coordinator 抽取提供统一边界。

本次仅同步 `PROJECT_CONTEXT.md`、`DEV_LOG.md`、`TEST_MATRIX.md`；没有修改 Production Code、Test Code、workflow、版本号或签名配置。

## 2026-09-15 · Roadmap Simplification / Planning Model Cleanup

### 正式项目决策

- 原 W2、W3、W4、W5 规划阶段概念自本记录起退役；它们不再表示当前阶段、未来阶段、完成度门槛、阻塞容器或开发顺序。
- 原规划覆盖的 Runtime、Configuration、Web、Environment、Tools、Cache、Editor、Settings、Language、Diagnostics 等能力已经进入当前产品基线；本次决策不删除任何源码、测试、架构能力或验证证据。
- 当前正式基线为 `main@38fb60af8e4c7a4b09eafb1cad0e305ae56fc353`、`versionCode 101`、`versionName 0.8.0-alpha25`。
- 历史提交、Release/tag、artifact 名称、测试记录和旧审计结论继续保留，因为它们记录真实发生过的工作；它们不再充当当前 Roadmap 状态。
- TEST_MATRIX 的职责是 Regression / Verification Evidence（回归与验证证据），不再作为 W2–W5 completion checklist。
- Runtime Session Ownership Boundary 仍是有价值的技术问题，但现在归类为普通产品 backlog / hardening，不再称为 W2 P0 或 W2 blocker。
- 不创建 W2 COMPLETE、W3 COMPLETE、W4 COMPLETE 或 W5 COMPLETE 等新的阶段状态；未来 Roadmap 尚待重新定义，也不在本次创建 W6。

### 变更边界

本次只同步文档；没有修改 Production Code、Test Code、版本号、GitHub Actions workflow 或 Runtime/Web 行为。

## 2026-09-15 · SiftAlpha X Product Definition

### 正式产品决策

Roadmap Simplification / Planning Model Cleanup 完成后，SiftAlpha Studio 不再使用 W2、W3、W4、W5 作为当前或未来 Roadmap 阶段。下一条产品主线改为基于当前真实产品能力定义 SiftAlpha X；本记录不恢复旧阶段编号，也不创建新的阶段编号。

SiftAlpha Studio 定位为面向 Android 的项目运行与管理平台：用户在外部开发环境完成项目，再导入 Studio 进行 Detect、Prepare、Run、Monitor、Use、Stop 和 Restart。Editor 与 Terminal 保持辅助定位，不成为产品主要方向。

### SiftAlpha X 定义

SiftAlpha X 的正式工作名称为 SiftAlpha X — SiftAlpha Execution Runtime。Studio 主要负责 Import、Detect、Manage、Monitor 和 Use；SiftAlpha X 主要负责 Prepare、Execute / Run、Status、Logs、Stop、Restart 和 Recovery。SiftAlpha X 是 Runtime / Execution System，不是 Android/Linux Kernel。

当前实现仍依赖外部 Termux。长期方向是让 Studio 不再要求用户安装或操作独立 Termux App，并逐步走向 Embedded Runtime → Project Runtime → User Project 的架构。这里记录的是产品方向，不是已完成的技术实现；在 Runtime Architecture Audit 前不预先选择 PRoot、Ubuntu、Alpine、Termux fork、bootstrap 或其他第三方 runtime。

### 第一阶段目标与工程顺序

SiftAlpha X 第一阶段只以 Python 项目为目标 Runtime。无 Termux Android 设备上的最终目标包括自动准备 Python Runtime、建立项目环境、处理依赖、START、STATUS、LOGS、STOP、RESTART，以及在本地 Web 服务经过验证后打开 Browser；整个过程不要求用户安装或操作 Termux。该目标不表示当前已经实现，也不扩展到 Node.js、Java、Go 或 Rust。

SiftAlpha X 的第一项工程工作是 Runtime Architecture Audit，而不是立即重写 Runtime。审计将确定 Termux coupling points、RuntimeCommandHost、PRoot、Ubuntu/Linux userspace、PythonRuntimeAdapter、Runtime Identity、Web Discovery / Endpoint Probe、process/session ownership、file/path/bind 的现有职责边界，并判断是否需要 Runtime Provider abstraction。审计完成前不决定最终 Embedded Runtime 技术方案。

### 产品原则与当前边界

正式原则为：“可靠运行优先于盲目兼容；明确失败优先于错误猜测。”SiftAlpha X 不承诺任意桌面项目都能直接在 Android 上运行；遇到 Runtime、CPU architecture、native dependencies、operating-system dependencies 或 Android platform restrictions 导致的不兼容，应明确报告失败，不进行危险猜测或静默降级。

本次只建立产品定义和下一项研究任务。没有修改 Production Code、Test Code、build configuration、GitHub Actions workflow、versionCode、versionName 或 Runtime behavior；没有删除 Termux 支持，也没有开始 Embedded Runtime 实现。


## 2026-09-16 · M / R / X Architecture Definition + alpha29 Real-Device Evidence Consolidation

### Baseline

- Repository：`kuashan/siftalpha-one`
- Branch：`codex/siftalpha-x-embedded-cpython-spike`
- Source HEAD：`a5691fff049a6be25ccade78f1ef23ce869543bf`
- Expected main HEAD：`dff275575a9cdbd0564d394c4626cd7d9bb22637`
- Runtime prototype：`versionCode=105`、`versionName=0.8.0-alpha29`
- `AGENTS.md`：在目标提交的完整仓库树中未找到；本轮遵循现有 docs maintenance rules 和用户的 docs-only scope。
- 本轮开始前已重新读取目标 branch、main ref、版本文件、当前 workflow 和关键 Runtime / Embedded CPython 源码；workspace 通过 GitHub remote tree/API 校验为 clean，scratch 当前目录本身不是 checkout 的 Git worktree。

### 规范架构决策

从本记录起，当前架构术语为：

- **SiftAlpha M — Management System**：Import、Detect、Project Management、Project Identity、Configuration、Prepare orchestration、Runtime selection/coordination、START、STOP、STATUS、LOGS、Restart orchestration、Monitor、Web Discovery、Endpoint Probe、Browser、UI 和 M 与 Runtime 的控制协调。M 管。
- **SiftAlpha R — Runtime System**：Runtime initialization/environment、language runtime hosting、Session、session identity、generation、execution lifecycle、project execution、stdout/stderr、result、STOP semantics、restart/re-entry、ownership、environment/dependency execution、failure isolation、recovery、capability reporting。R 跑。
- **SiftAlpha X = M + R**：加上稳定的 M ↔ R Interface 和完整产品级集成/验收。M 完成、R prototype 成功或 Embedded CPython 成功，都不等于 X achieved。
- [`docs/ARCHITECTURE_M_R_X.md`](ARCHITECTURE_M_R_X.md) 是规范定义；旧 DEV_LOG/历史审计中的 “SiftAlpha X = SiftAlpha Execution Runtime / Execution System” 保留为历史事实并标记 superseded，不做机械历史改写。

### 源码审计结论

- Production `ProjectRuntimeController` 当前通过 `RuntimeCommandHost` / `TermuxProotRuntimeHost` 生成 Python/Node 的外部 provider 命令；`TermuxBackend` / `TermuxContract` 是现有 Termux control path。
- `RuntimeBackend`、`RuntimeAdapter`、`ManagedProcessRuntime`、`RuntimeIdentity`、lifecycle models 和 Web discovery/probe 组成可复用的 runtime-neutral seams，但不应被文档宣称为已经完成的独立 R product。
- `EmbeddedPythonNative.cpp`、`EmbeddedPythonSession.kt`、`EmbeddedPythonResult.kt`、`EmbeddedPythonTestActivity.kt` 和 `EmbeddedPythonScripts.kt` 组成隔离 prototype；process-scoped CPython 与 fixed scripts 的真实边界保持不变。Embedded CPython 是 R 的第一个 Runtime implementation/backend prototype，不是 R 的全部，也不是 X。
- Termux + PRoot + Ubuntu + Python 正式分类为 External Runtime Provider / External Runtime Environment，不是 M、R 或 X。
- `RuntimeWebStateStore` 仍主要按 `projectKey` 持久化 Web candidate；Project → Runtime Identity/Generation → Lifecycle → Web Candidate → Recovery ownership chain 仍是后续 hardening backlog。本轮不实现 M ↔ R integration 或 ownership refactor。

### alpha29 构建与真实设备验收证据

构建身份：

- GitHub Actions Run #80，Run ID `35060381162`；
- artifact `siftalpha-w0-80`，Artifact ID `10432576095`；
- artifact digest：`sha256:94cf4d1ead49e8500f9b2467765a983b8e0164be354c1e59083670763945eaba`；
- APK SHA-256：`2a7b7434817ffec53863ad9fd16745bdfef101677c5470a81efdab78aa269cc6`。

用户提供并确认的真实设备事实：

1. **Test A**：`ENGINE=CPYTHON`、`TERMUX=NOT_USED`、`PROOT=NOT_USED`；generation 1；`STATE=SUCCEEDED`、`RUNTIME_PHASE=TERMINAL`；`STOP_PHASE=IDLE`、`STOP_RESULT=NONE`；exitCode 0；CPython 3.14.7；`sys.platform=android`；machine `aarch64`；stdlib `json OK`。结论：PASS。
2. **Test B**：generation 3；`STATE=FAILED`、`RUNTIME_PHASE=TERMINAL`；`STOP_PHASE=IDLE`、`STOP_RESULT=NONE`；exitCode 1；stdout 为 `SIFTALPHA_X_TEST_B_STDOUT`；stderr 含 `SIFTALPHA_X_TEST_B_STDERR` 和预期 `RuntimeError: SIFTALPHA_X_TEST_B_FAILURE`。结论：PASS（intentional failure）。
3. **generation gap**：用户提供的 B 证据从 generation 1 到 generation 3；generation 2 未出现在 supplied evidence 中。本记录只写 observed evidence gap / unexplained intermediate generation，不发明原因，也不将其分类为 Runtime failure。
4. **Test C running**：generation 4；`STATE=RUNNING`；`RUNTIME_PHASE=PYTHON_EXEC_BEGIN`；`STOP_PHASE=IDLE`、`STOP_RESULT=NONE`；exitCode `-`。结论：PASS（证明 STOP 前置条件）。
5. **Test C cooperative STOP**：同一 session、generation 4；`STATE=STOPPED`、`RUNTIME_PHASE=TERMINAL`；`STOP_PHASE=STOP_REQUEST_RETURNED`、`STOP_RESULT=INTERRUPT_DELIVERED`；exitCode 130；stdout 含 repeated `SIFTALPHA_X_TEST_C_TICK` 和 `SIFTALPHA_X_TEST_C_COOPERATIVE_STOP`；stderr 为 `SIFTALPHA_X_STOP=COOPERATIVE`；没有普通 FAILED traceback。结论：PASS。
6. **post-stop re-entry**：不重启 Android application process；新的 session；generation 5；`STATE=SUCCEEDED`、`RUNTIME_PHASE=TERMINAL`；`STOP_PHASE=IDLE`、`STOP_RESULT=NONE`；exitCode 0；CPython 3.14.7、Android/aarch64、stdlib json OK。结论：PASS。

这些证据只证明当前 alpha29 Embedded CPython Runtime prototype 的测试范围：真实 Android aarch64 执行、无 Termux/PRoot 的实验路径、process-scoped initialization 下的已测试 multi-Session re-entry、success/failure/output、cooperative interruption、STOPPED/130 和 STOP 后 re-entry。它们不证明 R 已完成或 X 已通过。

### 明确限制

未被 alpha29 证明的能力包括：任意 Python project 兼容性；arbitrary C extension、blocking native code、blocking syscall 或 uninterruptible native code interruption；universal hard-kill；pip/native wheel/numpy/pandas/scipy/venv/dependency installation；SAF imported project execution through R；Web/Browser 与 R 的 production integration；M ↔ R production integration；process/Android lifecycle recovery；concurrency、multi-project execution、完整 isolation 和 production-grade sandboxing。

`PyThreadState_SetAsyncExc` 相关 STOP 只能描述为 cooperative / limited interruption behavior，不能描述为 universal hard-stop mechanism。

### 变更边界

本轮是 docs-only / architecture-only consolidation：

- 新增 canonical `ARCHITECTURE_M_R_X.md`；
- 更新 `PROJECT_CONTEXT.md`、`DEV_LOG.md`、`TEST_MATRIX.md` 和 `docs/README.md`；
- 保持 `versionCode=105`、`versionName=0.8.0-alpha29`；
- 不修改 production Runtime、Embedded CPython、STOP、JNI、Session lifecycle、Runtime Identity、Web Discovery、Termux path、tests、workflow 或 source naming；
- 不实现 M ↔ R Interface，不做 mass rename，不删除 Termux 支持；
- alpha29 real-device evidence 与 CI evidence 分开记录。

### 下一项 R 工程方向（只推荐，不在本记录实施）

基于当前源码边界，优先候选为 **R Project Script Execution Boundary**：当前 Embedded CPython prototype 只运行 `EmbeddedPythonScripts.kt` 中固定、可审查的 A/B/C 脚本，尚未定义 imported project 的 root、entry、working directory、environment、stdout/stderr、failure 和 Session ownership 合同。先固化这个边界，再评估 dependency/environment model，能避免把 alpha29 fixed-script evidence 误写成通用 project execution capability。

现有 production `RuntimeWebStateStore` 的 projectKey-only candidate ownership 仍是重要的 M/R hardening backlog；它应与未来 M ↔ R Session facts contract 协调，但本轮不实现。

本次文档 consolidation 没有修改生产行为或版本，没有创建 PR、merge、tag、release。


## 2026-09-16 · R Project Script Execution Boundary — alpha30

### 目标

在不修改生产 Termux-based External Runtime Provider 的前提下，让 SiftAlpha R 的 Embedded CPython prototype 从 fixed built-in source 进入明确的 file-backed Python project-script boundary。

### 实施结果

- 版本从 0.8.0-alpha29 / versionCode 105 升级为 0.8.0-alpha30 / versionCode 106。
- 增加显式 Project Execution Specification：project identity、execution root、entrypoint、working directory、runtime kind、session identity 和 generation。
- APK 内置可重复的 project A/B/C/D/SystemExit fixtures；每次执行先 materialize 到 app-private siftalphax/projects/<sessionId> staging root。
- Embedded R native boundary 重复校验 staging containment、canonical path、symlink、regular-file、working-directory 和 source size/NUL 限制；缺少 entrypoint 产生明确 FAILED 结果，不静默 fallback。
- 真实文件 source 通过 CPython compile/eval 执行，建立临时 __main__ namespace，设置真实 __file__、__name__、sys.argv[0] 和 project-local sys.path，并恢复 Python context、cwd、TMPDIR、stdout/stderr。
- project-local module cleanup 加入 session terminal 路径；A/D fixture 用不同 helper 值验证 namespace/module isolation。
- SystemExit(0)、SystemExit(nonzero)、ordinary exception 和现有 cooperative STOP 保持明确 result mapping；alpha29 re-entry/STOP lifecycle 没有被替换。
- snapshot 与 Copy all diagnostics 扩展 project identity、execution root、entrypoint 和 working directory；既有 A/B/C/STOP diagnostics keys 保留。
- instrumentation source 扩展 file semantics、traceback filename、namespace isolation、SystemExit、STOP 和 post-STOP re-entry；当前 CI 无 emulator/device，因此不把 instrumentation 标记为 executed/pass。

### SAF 与生产路径边界

当前 fixture staging 是可重复的 app-private test asset strategy，不是完整 SAF import implementation。SAF URI 未直接交给 native；TermuxBackend、TermuxProotRuntimeHost、RuntimeCommandHost、PythonRuntimeAdapter production path、ProjectRuntimeController production execution、Runtime Identity 和 Web Discovery 未修改。

### 当前验证状态

本提交完成后应由 GitHub Actions 执行 CPython preparation、JVM tests、CMake/native、assembleDebug、signing 和 artifact evidence。alpha30 在 CI 成功后状态为 source/CI ready for real-device acceptance；真实设备验收仍需单独记录，不能由 instrumentation source 或 cloud build 替代。

### 已知限制

当前 boundary 只覆盖 app-private file-backed pure-Python scope。未实现 pip、dependency installation、venv、native wheels、arbitrary C extension support、blocking native/syscall interruption、universal hard-stop、SAF imported projects、并发 sessions、production sandbox 或 M ↔ R production integration。STOP 仍是 cooperative / limited interruption behavior。

### 下一步建议（只推荐）

优先研究 R Environment Model 与 Dependency Model 的边界，或在真实设备验收后研究 R Session Isolation hardening；不要把当前 fixture boundary 误称为 arbitrary project support。下一任务仍需单独决策，本记录不实施后续方向。


### alpha30 deterministic boundary coverage follow-up

补充 PROJECT_MISSING_ENTRYPOINT fixture（staged root 中故意没有 entrypoint），instrumentation source 会验证 native R 发布明确 FAILED/exitCode 1，并保留 SIFTALPHA_X_ENTRYPOINT_ERROR=missing entrypoint。该测试仍等待真实 Android device，不在 CI 中宣称执行通过。
