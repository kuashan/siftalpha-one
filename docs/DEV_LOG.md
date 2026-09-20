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

该段记录 alpha30 实现提交时的 source/CI 状态；随后 Run #90 的真实 Android 设备验收已在本日志后续条目中单独记录。instrumentation source 和 cloud build 仍不能替代真实设备证据。

### 已知限制

当前 boundary 只覆盖 app-private file-backed pure-Python scope。未实现 pip、dependency installation、venv、native wheels、arbitrary C extension support、blocking native/syscall interruption、universal hard-stop、SAF imported projects、并发 sessions、production sandbox 或 M ↔ R production integration。STOP 仍是 cooperative / limited interruption behavior。

### 下一步建议（只推荐）

优先研究 R Environment Model 与 Dependency Model 的边界，或在真实设备验收后研究 R Session Isolation hardening；不要把当前 fixture boundary 误称为 arbitrary project support。下一任务仍需单独决策，本记录不实施后续方向。


### alpha30 deterministic boundary coverage follow-up

补充 PROJECT_MISSING_ENTRYPOINT fixture（staged root 中故意没有 entrypoint），instrumentation source 会验证 native R 发布明确 FAILED/exitCode 1，并保留 SIFTALPHA_X_ENTRYPOINT_ERROR=missing entrypoint。该测试仍等待真实 Android device，不在 CI 中宣称执行通过。


### alpha30 CI / artifact evidence

- Workflow：SiftAlpha W0 Cloud Build。
- Run：#83；Run ID：35078323509；结果：success。
- Source HEAD：328ff10222a4fb188d5932130a16c04a3c0ccabb。
- Artifact：siftalpha-w0-83；Artifact ID：10439551021；size：55,813,639 bytes；digest：sha256:0dcdede705ee6e27e0a646704ab0376d5ac490ad3f5119ae9f0970cefcc9777a。
- APK：siftalpha-studio-debug.apk；size：46,651,177 bytes；SHA-256：18800bd47c4ebfccb5fcb3f6cff531e812e0f79cc48486e9c8d6013dd9204247。
- APK badging 确认 package com.siftalpha.studio、versionCode 106、versionName 0.8.0-alpha30、native-code arm64-v8a。
- signing verification：APK Signature Scheme v2=true；1 signer；certificate DN 为 SiftAlpha Studio Test；其余签名摘要见 workflow evidence。
- CPython preparation：YES；CPython 3.14.7、arm64-v8a、官方组件 SHA-256 6d50cc3aa66e414a439594089bcdfb5f1264358155c70c1f00471c24cfb477fb。
- Gradle task：testDebugUnitTest assembleDebug；当前源码 49 个 JVM test files、323 个 @Test methods；任务成功。
- instrumentation：source 已加入，但当前 workflow 没有编译 androidTest target、Android emulator/device；未执行，不标记 PASS。
- APK 内含 A/B/C/D、SystemExit 和 missing-entrypoint fixtures；这只证明打包，不替代真实设备执行。



## 2026-09-16 · alpha30 Real-Device Acceptance Evidence Consolidation

### 证据分类与构建身份

本条归档的是用户在真实 Android 设备上连续执行得到的 **REAL-DEVICE EVIDENCE**，不是 instrumentation source 或 CI 推断。CI / JVM / APK 构建证据单独记录；没有把 instrumentation source 写成已执行。

- 版本：`versionCode=106`、`versionName=0.8.0-alpha30`；
- 测试分支：`codex/siftalpha-x-embedded-cpython-spike`；
- 测试源码 HEAD：`0e2b069d93a0a8cd87df4f57f0e97da1cf918643`；
- GitHub Actions：SiftAlpha W0 Cloud Build Run #90，Run ID `35083943639`；
- artifact：`siftalpha-w0-90`，Artifact ID `10441785815`；
- artifact digest：`sha256:afc012248071e5b43884aa8985ee07e94a4e23e1f7ecf4a5c4a960f01ff228da`；
- APK：`app-debug.apk`；
- APK SHA-256：`d9bdeac5a0df867cc52b5b71226e90a560a831a3097e9341af0f2e1898e2a547`。

### Generation 1 — Test A（真实设备）

- `PROJECT_ID=fixture-project-a`；
- `GENERATION=1`；
- `STATE=SUCCEEDED`、`RUNTIME_PHASE=TERMINAL`；
- `STOP_PHASE=IDLE`、`STOP_RESULT=NONE`；
- `exitCode=0`；
- Embedded CPython `3.14.7`、`sys.platform=android`、`platform.machine=aarch64`；
- `TERMUX=NOT_USED`、`PROOT=NOT_USED`；
- stdout 确认 `SIFTALPHA_X_PROJECT_A_SUCCESS`、helper import 的 `PROJECT_A_HELPER`、`__name__=__main__`、真实 staged `__file__`、真实 staged `argv[0]`、app-private staged working directory 和 stdlib `json PASS`；
- stderr 为空。

此前的 Android app-private path 错误 `SIFTALPHA_X_PROJECT_SPEC_ERROR=symlink path components are not supported: /data/user/0` 未再次出现，Python 已真正开始并完成 file-backed project execution。因此 Android App-private Path Validation Repair 的真实设备 Test A = PASS。

### Generation 2 — Test B（真实设备）

- `PROJECT_ID=fixture-project-b`；
- `GENERATION=2`；
- `STATE=FAILED`、`RUNTIME_PHASE=TERMINAL`；
- `STOP_PHASE=IDLE`、`STOP_RESULT=NONE`；
- `exitCode=1`；
- stdout：`SIFTALPHA_X_TEST_B_STDOUT`、`SIFTALPHA_X_PROJECT_B_FAILURE`；
- stderr：`SIFTALPHA_X_TEST_B_STDERR`；
- traceback 指向真实 staged `.../main.py` 第 8 行，并以 `RuntimeError: SIFTALPHA_X_TEST_B_FAILURE` 结束，不是 `<string>`。

这是预期的 intentional Python failure；stdout、stderr 和真实文件 traceback 行为 = PASS。

### Generation 3 — Test C running（真实设备）

- `PROJECT_ID=fixture-project-c`；
- `GENERATION=3`；
- `STATE=RUNNING`；
- `RUNTIME_PHASE=PYTHON_EXEC_BEGIN`；
- `STOP_PHASE=IDLE`、`STOP_RESULT=NONE`；
- exit code 当时尚不可用。

这证明真实 project script 已进入 active Python execution，是 STOP 验收前置条件 = PASS。

### Generation 3 — Test C cooperative STOP（真实设备）

STOP 使用与 running Test C 相同的 Session ID 和 generation 3。最终观察为：

- `STATE=STOPPED`、`RUNTIME_PHASE=TERMINAL`；
- `STOP_PHASE=STOP_REQUEST_RETURNED`；
- `STOP_RESULT=INTERRUPT_DELIVERED`；
- `exitCode=130`；
- stdout 含 `SIFTALPHA_X_TEST_C_STARTED`、`PROJECT_C_FILE`、多次 `SIFTALPHA_X_TEST_C_TICK` 和最终 `SIFTALPHA_X_TEST_C_COOPERATIVE_STOP`；
- stderr：`SIFTALPHA_X_STOP=COOPERATIVE`；
- 未观察到普通 FAILED traceback。

Python-layer cooperative STOP for the tested long-running project = PASS。该结果不扩展为 blocking native code、blocking native extension、任意 C extension 或 uninterruptible syscall 的 hard-stop 保证。

### Generation 4 — Test A re-entry（真实设备）

STOP 完成后不重启 Android application process，创建新的 Session：

- `PROJECT_ID=fixture-project-a`；
- `GENERATION=4`；
- `STATE=SUCCEEDED`、`RUNTIME_PHASE=TERMINAL`；
- `STOP_PHASE=IDLE`、`STOP_RESULT=NONE`；
- `exitCode=0`；
- 再次确认 CPython `3.14.7`、`sys.platform=android`、`aarch64`、`TERMUX=NOT_USED`、`PROOT=NOT_USED`、stdlib `json PASS`、project success/helper import、`__name__=__main__`、真实 `__file__`、真实 `argv[0]`、正确 project working directory 和空 stderr。

### 正式验收结论

连续真实设备生命周期为：

`Generation 1 PROJECT A success`
→ `Generation 2 PROJECT B intentional failure`
→ `Generation 3 PROJECT C running`
→ `cooperative STOP`
→ `Generation 4 PROJECT A new-session re-entry success`。

结论：

**SiftAlpha R Embedded CPython alpha30 Project Script Execution Boundary real-device acceptance = PASS（仅针对上述 app-private、file-backed、pure-Python fixture 测试范围）。**

这形成了当前测试范围内的：

`Project Identity`
→ `Execution Specification`
→ `app-private staged project root`
→ `real Python entrypoint`
→ `Session`
→ `Generation`
→ `Success / Failure`
→ `cooperative STOP`
→ `cleanup`
→ `re-entry`

闭环。该结论不表示 R 已完成，也不表示 X 已完成。

### alpha30 未被本证据证明的能力

仍未实现或未被本验收证明的能力包括：

- arbitrary external project import；
- SAF project import/integration；
- pip、requirements/pyproject dependency installation、venv；
- arbitrary third-party packages、native wheels；
- arbitrary C extensions；
- blocking native code/syscalls 的 hard-stop；
- concurrent Sessions、多项目并发；
- full process-death recovery；
- production M ↔ R integration；
- R Web Discovery / Browser integration；
- production-grade sandboxing。

Android 路径可能以 `/data/user/0/...` 或 `/data/data/...` 表示；alpha30 通过 canonical containment 处理当前 app-private staging 场景，但这不是完整 filesystem sandbox。

## 2026-09-16 · Version Identity Correction — alpha31 baseline

### 纠正内容

- alpha30 app-private path validation 修复后的 Runtime source 曾继续沿用 `versionCode=106`、`versionName=0.8.0-alpha30`，与“新 Runtime source → 新 APK → 新版本身份”的规则不一致。
- 为恢复严格单调且唯一的 APK/source version identity，当前基线提升为 `versionCode=107`、`versionName=0.8.0-alpha31`。
- 本次 alpha31 只修正版本 metadata，不包含新的 Runtime behavior change；其 Runtime behavior lineage 来自已经通过 Run #90 真机验收的修复后 source。
- Run #90 的 alpha30 历史证据保持原样：Run #90 / ID `35083943639`、source HEAD `0e2b069d93a0a8cd87df4f57f0e97da1cf918643`、artifact `siftalpha-w0-90`、版本 `106 / 0.8.0-alpha30` 及其真实设备 A → B → C cooperative STOP → A re-entry 结果均不改写。
- 后续新的 Runtime source behavior changes 必须从 `107 / 0.8.0-alpha31` 之后继续单调递增。

## 2026-09-16 · alpha32 M → R Embedded CPython First Integration

### 目标

在不重构 M、R 或 Termux provider 的前提下，让 M 当前管理的 SAF Python project 通过一条明确、最小的边界交给 Embedded R 执行。alpha32 只建立 first integration，不宣称 R complete 或 X complete。

### M → R 闭环

本轮源码形成：

`M Project (SAF documentId)`
→ `M deterministic entrypoint resolution`
→ `EmbeddedPythonProjectStager`
→ `app-private bounded staging root`
→ `EmbeddedPythonSession`
→ `EmbeddedPythonExecutionSpec`
→ `Embedded CPython`
→ `EmbeddedPythonSnapshot`
→ `RuntimeState / Project Output / STOP`。

M 只在现有 Runtime selection 已明确为 Python 时显示显式 Embedded R 入口。M 不伪造 sessionId 或 generation；R 继续自行分配并维护它们。Embedded R 使用既有 CPython/native execution path，不通过 RuntimeCommand、Termux、RUN_COMMAND、PRoot 或 Ubuntu。既有 Termux Run/Prepare/Status/Logs/STOP 路径保持为独立的默认路径。

### SAF staging boundary

- source-of-record 是 SAF project directory，Project Identity 使用 documentId；
- stager 将整个可见项目树复制到 app-private `files/siftalphax/projects/session-<uuid>`，不把 content URI 当作 POSIX native path；
- source 相对路径在复制前验证，entrypoint 必须来自已复制的 regular file；
- staging 限制为最多 1,024 nodes、512 files、单文件 4 MiB、总大小 32 MiB；ProjectStore 的既有递归深度/节点上限也作为完整性边界；
- SAF 读取失败、路径不安全、入口缺失、冲突或大小超限都会使本次 staging 失败，并清理不完整 app-private 根目录；不会删除 SAF source；
- terminal 后由 Controller 清理本次 session 关联的 staging root；本轮不引入 cache manager 或并发 staging。

### Entrypoint / result contract

M 优先使用 `.project.json` 的 entry；没有声明时只接受确定性 conventional root entry（main.py、app.py、run.py、manage.py），或唯一的 root-level `.py`。声明入口不安全、缺失或无法唯一解析时，不启动 R。workingDirectory 固定为 staged project root，arguments/environment/dependency installation 不在本轮实现。

EmbeddedPythonSnapshot 直接映射到 M `RuntimeState`：IDLE→UNKNOWN、STARTING→STARTING、RUNNING→RUNNING、SUCCEEDED→EXITED_SUCCESS、FAILED→EXITED_ERROR、STOPPED→STOPPED_BY_USER。stdout、stderr、exitCode、sessionId、generation 和 runtime facts 通过现有 project output/UI 展示；M 不将 snapshot 回编码为 Termux markers，也不制造假的 Termux executionId。STOP request accepted 仍不等于 STOPPED，只有 R terminal snapshot 才改变 M 的终态。

### 验证范围

本轮增加了 staging/entrypoint/state mapping JVM coverage，并更新了 Embedded R instrumentation source，继续保留 alpha29/alpha30 re-entry、failure、STOP、path-validation 和 file-backed regression。CI 没有真实 Android device 时，instrumentation 只能记录为 source/compiled/not executed；alpha32 真机验收仍需人工执行。

已知未覆盖：pip、venv、third-party dependency installation、native wheels/arbitrary C extensions、blocking native/syscall hard-stop、SAF arbitrary project compatibility、并发、process-death recovery、Web/Browser integration、production M ↔ R integration。


### alpha42 — Rich Result Presentation + Unified Open

在 alpha41 Web Discovery Scope Repair 基础上，alpha42 增加最小的纯 Kotlin Rich Result 边界：M 对经过 Secret Redaction 的 Runtime output 做 ANSI/OSC 清理，只接受明确的 `[+] Label: http(s)://...` 链接列表格式，去重并限制最多 200 项。结果不会解析普通帮助文本、代理示例、Web 启动日志或 `SIFTALPHA_WEB_URL` 调试行。

项目卡片不再提供并列的“查看结果”和“浏览器”主入口，而是统一显示“打开”。PresentationTargetResolver 按 `WEB > RICH_RESULT > NONE` 选择目标；WEB 继续使用 alpha41 的 Web State、Endpoint Probe 和 Browser 安全链，RICH_RESULT 使用原生文本型 RichResultActivity，NONE 不提供主要打开目标。Raw Log、复制全部、滚动和诊断输出保持不变。

Rich Result 只在同一 Activity 实例内暂存：接受新 START 时清除旧结果，STATUS/LOGS 没有新结果时保留，后续 LOGS/Embedded snapshot 发现新链接时更新。alpha42 不等于 R 或 X 完成，也不包含 WebView、数据库、依赖安装、量化交易或永久结果存储。


## alpha43 — Automatic Project Observation + Contextual Status Guidance

- Added bounded, foreground-only observation for External Provider projects.
- STATUS is the normal observation operation; terminal runs receive one final LOGS read for result detection and diagnostics.
- Observation is generation-scoped, waits for pending user operations, pauses on Activity stop, and resumes without stopping the underlying runtime.
- Added state-derived guidance and a localized visible Back action to RichResultActivity.
- No new Rich Result type, background service, database, WebView, or runtime lifecycle change. Real-device acceptance is closed in the Baseline Closure entry below.

## 2026-09-17 · alpha43 Baseline Closure — Real-Device Acceptance

### 最终版本身份

- 产品名称：`Automatic Project Observation + Contextual Status Guidance`
- versionCode：`119`
- versionName：`0.8.0-alpha43`
- Final source HEAD：`66f9153e57547c4d8b6e50956b48ddf86b9dc656`
- Parent HEAD：`d92e1802b983718280cdd303d56c9293c22a8425`
- Branch：`codex/siftalpha-x-embedded-cpython-spike`
- GitHub Actions：Run #121 / Run ID `35226167054` / conclusion `success`
- Artifact：`siftalpha-w0-121`
- APK SHA-256：`6b70fc222cc8e27124daf2a5a910abc1174380b257c5549959abb279d25def6a`

### Sherlock 一次性项目 — 真实 Android 真机 PASS

用户只点击一次“运行”，没有手动点击 STATUS（状态检测）或 LOGS（刷新日志）。SiftAlpha 自动完成：

`START → RUNNING → Automatic Observation → 自然结束 → EXITED_SUCCESS → 最终 LOGS → Rich Result → Unified Open`

最终真实项目卡显示：

- 状态：已正常结束。
- 状态说明：运行结果已就绪，可以打开查看。
- 结果：Rich Result 可用；本次真实搜索产生 16 项结果。
- 打开：可用。

Unified Open 正常进入 App 内 Rich Result Viewer；Viewer 提供明确可见的“← 返回”按钮，不依赖 Android 返回手势，返回项目卡后状态仍然正确。

用户可见语义已经区分 `EXITED_SUCCESS` 与 `STOPPED_BY_USER`：自然结束不是“已停止”，只有明确点击 STOP 才代表用户停止。

### situation-monitor 长期 Web 项目 — 真实 Android 真机 PASS

项目启动后先执行 initial feed fetch。项目仍处于 RUNNING 时，Web 可以暂时显示未检测到网页；Web Server 真正开始监听后，Automatic Observation / Web Discovery 发现候选地址，并由 Endpoint Probe 验证真实可达，随后自动开放 Web 与 Unified Open。

最终真实项目卡显示：

- 状态：运行中。
- 状态说明：项目正在持续运行，可随时打开实时界面。
- Web：可打开。
- 打开：可用。
- 停止：可用。

外部浏览器真实访问成功，示例请求均返回 HTTP 200：`GET /api/articles`、`GET /api/globe-data`、`GET /api/stats`。关闭或离开浏览器不会停止 Runtime；离开 SiftAlpha 后重新进入，长期 Runtime 仍继续运行并可被重新识别。用户确认该项目可以长期运行。

### alpha43 最终行为封存

- `RUNNING` 不等于 Web Ready。只有 Web Candidate 已发现且 Endpoint Probe 确认端点真实可访问后，才开放“Web：可打开”和“打开”。
- STATUS 是长期自动观察的主要动作，External Provider 约每 2 秒观察一次；Web discovery 的 LOGS 探测按每 3 次 STATUS 一次、最多 3 次执行，不是无限后台刷新。
- 终态项目只执行一次最终 LOGS，用于 Rich Result detection 与 final diagnostics。
- Activity 进入后台时暂停 Automatic Observation，但不停止底层 Runtime；重新进入 App 后恢复观察。
- Rich Result、Unified Open（`WEB > RICH_RESULT > NONE`）和 Rich Result Viewer 的返回链路保持可用。

**alpha43 real-device acceptance = PASS**

本条记录封存 alpha43 当前基线；不表示 Embedded R 或 M/R/X 已完成，也不启动后续版本开发。

## 2026-09-19 · alpha43-r14 Runtime Control Lifecycle Unification Audit + Repair

- Source baseline: `239434ba77c6c1b3ef8e4c8ef302c0d08f4ae859`, branch `codex/siftalpha-no-worker-alpha43`.
- Added a provider-neutral, project-scoped operation lifecycle for PREPARE / START / STATUS / LOGS / STOP / CLEAN. STOPPING, CHECKING and CLEANING are no longer represented as RECOVERING; RECOVERING remains reserved for foreground/process reconciliation.
- External project activities now have per-operation hard deadlines and a PID/PGID-scoped watchdog without depending on GNU `timeout`. Late, cancelled and timed-out results are fenced from newer operations.
- Internal Embedded CPython and Internal Alpine project-environment cleanup now uses an interrupt-aware, deadline-aware, no-follow-symlink tree deletion contract; shared runtime/cache/rootfs and sibling projects are outside the deletion target.
- Runtime operation metadata is persisted minimally so Activity recreation can distinguish unfinished work from recovery reconciliation. Existing pure-Python External supplemental-Node detection remains unchanged.
- Added symmetric lifecycle, policy, watchdog, cancellation, project-isolation and storage-preservation JVM coverage.
- Internal Alpine Probe / W0 Cloud Build: source change submitted; CI result pending.
- Real-device Runtime Control matrix: not executed in this source/CI phase.

## 2026-09-19 · alpha43-r15 External Runtime Regression Restoration

- Restored External project activity execution to the r13 PID/PGID ownership plus natural `wait` contract; the wrapper no longer applies an elapsed-time watchdog or emits a synthetic timeout result.
- External STOP is again a project-scoped control command rather than a normal activity wrapper.
- Runtime operation deadlines are provider-aware: Internal retains its existing Android deadline path, while External operation records remain backend-result-owned and are reconciled through the real Termux result / recovery path.
- Retained the r14 lifecycle states, operation store, generation and late-result fencing, project-scoped activity registry, PID/PGID ownership, and Pure Python supplemental-Node detection behavior.
- Unit/build CI and Internal Alpine Probe are required for this source change; External real-device acceptance has not yet been executed.

## 2026-09-19 · alpha43-r16 External Project Activity Foreground Execution Restoration

- Regression analysis confirmed that the remaining r15 difference from the old working External path was the generic project activity wrapper: it backgrounded the real payload and waited on `$!`.
- Restored a foreground PID-only owner shell. It publishes the owner PID before the payload, keeps the real External command synchronous, propagates its exit code and output, and cleans ownership files through EXIT/INT/TERM/HUP traps.
- No dedicated PGID is recorded when `setsid` semantics cannot be proven safe; project STOP continues to use the existing descendant-tree ownership path without risking a shared process group.
- External business commands, STOP routing, Internal Runtime, provider-aware deadlines, result fencing and Pure Python supplemental-Node detection remain unchanged.
- Source/CI validation is pending; External real-device acceptance has not been executed.

## 2026-09-19 · alpha43-r17 External Direct Execution + Lightweight Ownership Restoration

- Removed the second execution shell from the External project activity contract. The ownership prefix no longer injects host preamble/helpers or quotes the payload for another `bash -lc`; it is attached directly to the original Termux RUN_COMMAND shell script.
- Kept only private PID/legacy-PGID conflict checks, PID publication, cleanup traps and diagnostic operation/owner markers. No new PGID is recorded without a proven dedicated process group.
- External business commands, STOP routing, Internal Runtime, Internal Alpine networking, Worker code and Termux transport remain unchanged.
- Source/CI validation is pending; External real-device acceptance has not been executed.

## 2026-09-19 · Cloud-Only Development and Build Policy

### 固定开发执行方式

- SiftAlpha Android 的 Codex（云端代码代理）使用云端执行方式，不依赖用户本地 Android SDK、JDK、Gradle 或本地编译环境。
- 代码修改、源码审查和提交均以 GitHub（代码托管平台）远端仓库的真实分支 / HEAD 为准；开始任务前必须重新读取远端状态，不能把本地工作区或旧对话中的状态当作唯一事实来源。
- Android 单元测试、Gradle 构建、APK 组装、签名、Probe（探针）和其他 CI 验证统一通过 GitHub Actions（GitHub 云端持续集成）执行。
- 可安装测试 APK 必须来自对应最终 HEAD 的 GitHub Actions 云端构建产物；不得用本地 Android SDK / Gradle 构建结果替代正式验证证据。
- 用户真机验收仍是最终 Runtime（运行时）行为证据；CI PASS（云端持续集成通过）不能替代 REAL_DEVICE_PASS（真实设备通过）。
- 后续开发默认保持 cloud-only（仅云端）流程，除非用户明确改变该约束。

### 目的

保持构建环境一致、避免本地 SDK / JDK / Gradle 差异导致不可复现结果，并确保源码提交、CI、APK 与真机测试能够通过同一 GitHub HEAD 精确追溯。

## 2026-09-19 · alpha43-r18 Internal Runtime Reliability Repair

- Starting source baseline: `8d5e0d74c64a0410e1a2deb9f5f0b78107b6c042`, version `0.8.0-alpha43-r17` / `143`.
- Added only the Internal Alpine Web Discovery bridge: the current Alpine host PID and its bounded descendant PID tree are used to collect project-owned socket inodes, which are then matched against LISTEN entries in `/proc/net/tcp` and `/proc/net/tcp6` (with process-scoped proc paths as a restricted fallback).
- Reused the existing `RuntimeWebCandidate`, `RuntimeWebStateStore`, `RuntimeWebAvailabilityTracker` and endpoint probe path. No second URL parser or global port/PID scan was added.
- Internal listener observations are fenced by session ID and generation; Internal discovered candidates and probe results are invalidated at START/STOP/terminal transitions so an older execution cannot leak into a new one.
- Internal Alpine DNS/network code was audited. DNS is refreshed from Android active-network LinkProperties on prepare, and current OCI failures do not prove an SiftAlpha transport defect; no network workaround or OCI-specific change was made.
- External Runtime r17/direct execution, Termux transport, Worker code and project sources remain unchanged.
- JVM coverage added for explicit/log discovery compatibility, empty-stdout project PID/socket discovery, sibling-PID isolation, no-listener behavior and stale session/generation fencing.
- Source/CI validation and real-device Internal acceptance are pending; CI PASS must not be recorded as REAL_DEVICE_PASS.

## 2026-09-19 · alpha43-r19 Internal Alpine Session Continuity Repair

- Starting source baseline: `49e05ffbe8b202ff226080474c69de05c4a2b905`, version `0.8.0-alpha43-r18` / `144`.
- Changed only the Internal Alpine session ownership boundary: `InternalAlpineSession` now exposes a process-scoped `shared(context)` instance backed by `context.applicationContext`.
- `V04Activity` now injects that shared session, so Activity recreation and re-entry retain active project records, session identity and generation continuity within the app process.
- The constructor is private to prevent a second Activity-local session from being created accidentally. External Runtime, network configuration, Worker code and r18 Web Discovery remain unchanged.
- Added JVM coverage proving separate Context instances resolve to the same process-scoped session; raised the installable version to `0.8.0-alpha43-r19` / `145`.
- W0 Cloud Build, Internal Alpine Probe, APK evidence and real-device Internal acceptance must be run against the final r19 HEAD; CI PASS must not be recorded as REAL_DEVICE_PASS.


## 2026-09-19 · alpha43-r20 Internal Alpine Shared Memory Compatibility

- Starting source baseline: `5ea612802ffbb9280a82061f86fdc499ad43b15b`, version `0.8.0-alpha43-r19` / `145`.
- r19 real-device evidence from `primary:AcodeProjects/situation-monitor` reached Flask/Werkzeug startup, then failed in CPython 3.12 `multiprocessing.heap.Arena._choose_dir()` because `/dev/shm` did not exist inside Internal Alpine.
- Root cause is runtime-generic: Internal Alpine binds Android host `/dev`, and Android does not provide the conventional Linux `/dev/shm` path expected by CPython multiprocessing/sharedctypes. The tzlocal UTC warning was non-fatal.
- Added one app-private shared-memory backing directory next to the Internal Alpine rootfs and bind it after `/dev` as guest `/dev/shm`. The rootfs mount point is also ensured before launch.
- This is an Internal Linux compatibility fix, not a Flask, OCI or situation-monitor project patch. External Runtime, Termux transport, Worker code, Web Discovery policy and session ownership remain unchanged.
- Added JVM regression coverage for the exact base bind ordering: Android `/dev` first, app-private `/dev/shm` overlay second, followed by `/proc` and `/sys`.
- Cloud CI / probe / APK and renewed real-device acceptance must be bound to the final r20 HEAD before any REAL_DEVICE_PASS claim.


## 2026-09-19 · alpha43-r21 Internal Background Survival + Stable Web Presentation

- Starting source baseline: `aae07f415b2109964eb5f40fca0650e1a6ed1127`, version `0.8.0-alpha43-r20` / `146`.
- Added `InternalRuntimeForegroundService` as a thin Android process-liveness lease for active Internal Alpine sessions. It does not execute projects, own PIDs, supervise Runtime state, or implement STOP.
- Each Internal Alpine session acquires a unique session lease after successful process launch and releases that lease only when that exact process reaches a terminal state. Multiple sessions therefore cannot stop each other's foreground protection.
- Android manifest now declares foreground-service and special-use permissions/type. The foreground notification is user-visible, localized, ongoing, and opens SiftAlpha.
- External Runtime, Termux transport, Worker architecture, PID ownership and project-scoped STOP semantics are unchanged.
- Web availability now retains the last verified fact across Activity stop/start while treating it as presentation identity only. A new foreground lifecycle still requires a fresh endpoint probe before reporting AVAILABLE.
- `RuntimeWebStateStore` persists the last verified Web URL for the current execution candidate. START/STOP/CLEAN/terminal invalidation keeps the existing execution boundary and prevents stale Web identity from crossing runs.
- Unified Open now selects WEB from verified presentation identity rather than only the instantaneous probe result, so a temporary DETECTING state cannot fall through to Rich Result.
- Added JVM coverage for independent foreground session leases, verified-Web presentation priority, and verified Web identity remaining expected while a fresh probe is pending.
- W0 Cloud Build, Internal Alpine Probe, APK evidence and real-device external-browser refresh acceptance are pending for the final r21 HEAD.


## 2026-09-19 · alpha43-r22 Fast Web Detection

- Starting source baseline: `b772074818e65755a5f365c212d1d518f2faf94d`, version `0.8.0-alpha43-r21` / `147`.
- Internal Alpine Web listener observation was removed from the Activity main-thread polling path. A dedicated single-thread observation executor performs the bounded project PID/socket/procfs scan and publishes only session/generation-matched results back to the UI thread.
- Internal heavy listener discovery now stops as soon as the current execution has a Web URL candidate. Endpoint verification then uses the lightweight loopback probe path.
- New endpoint candidates use a bounded startup verification burst: 150 ms, 300 ms and 600 ms follow-up checks after early connection misses. After that, or after the first successful verification, the normal 2-second health cadence resumes.
- During this bounded startup burst the UI remains DETECTING rather than flashing UNAVAILABLE after one early miss.
- External Runtime observation now uses a 500 ms cadence only while bounded Web LOGS discovery is still useful. Up to three discovery LOGS probes are front-loaded (one STATUS between probes); after verification or budget exhaustion the existing 2-second observation cadence resumes.
- Rich Result parser, detection policy, lifecycle policy and viewer production code are unchanged. Added regression coverage that Web DETECTING does not displace an available Rich Result and that repeated empty fast observations cannot erase an existing Rich Result.
- No Worker, no OCI/situation-monitor patch, no External execution-path rewrite, no global PID scan and no port-range scan were introduced.
- W0 Cloud Build, Internal Alpine Probe, APK evidence and real-device acceptance are pending for the final r22 HEAD.


## 2026-09-19 · alpha43-r23 Ownership-Verified Web Hints

- Starting source baseline: `c00fe450784ec7d63167abdd6228434688c37e96`, version `0.8.0-alpha43-r22` / `148`.
- Added `RuntimeWebHintPolicy` with ordered, bounded hints: project-detected/source/config port first, then framework default, then a nine-port common fallback set.
- Hints are never Web evidence. A configured/default/common port must still pass current-project PID/socket ownership discovery before it can be published as a Runtime Web candidate.
- Internal Alpine now has a hint fast path: inspect only hinted LISTEN entries first, map their socket inodes back to the current project PID tree, and publish the highest-priority owned hint when matched. If no owned hint matches, the existing complete bounded PID/socket discovery remains the fallback.
- External Runtime carries the same ordered hints into the existing project-scoped `RuntimeWebPortDiscovery`; dynamic project hints are checked before generic preferred ports, while the existing PID/PGID and PRoot guest identity boundaries remain authoritative.
- Unknown External projects may perform the existing bounded automatic LOGS command for procfs hint discovery, but `webLogDiscoveryAllowed` is not widened. Weak Runtime-log URL discovery therefore remains disabled unless the project was independently identified as Web-capable.
- The UI no longer treats `.project.json`/framework local URLs as ownership proof. Only a Runtime-published candidate enters endpoint verification. This prevents another project already listening on a common loopback port from being misidentified.
- Cached `WebProjectInspector.Profile` facts are reused during the 180 ms Internal session poll; project-file inspection is no longer repeated on every active snapshot poll.
- Rich Result production code remains unchanged. Regression coverage confirms automatic hint LOGS still participate in Rich Result inspection, Web hints cannot invent candidate ports, and an unrelated project's hinted listener cannot pass Internal ownership verification.
- No global PID scan, no 1..65535 port scan, no Worker, no OCI/situation-monitor source patch, and no External START/STOP ownership rewrite were introduced.
- W0 Cloud Build, Internal Alpine Probe, signed APK evidence and real-device acceptance are pending for the final r23 HEAD.


## 2026-09-19 · alpha43-r24 Web Runtime Continuity

- Starting source baseline: `260731c9ccee58b6097269f9c95c9a6d7c7a2ab1`, version `0.8.0-alpha43-r23` / `149`.
- Fixed the r23 Internal Alpine discovery starvation: an initial empty PID/socket observation no longer depends on a changing Runtime snapshot to be retried. Discovery is scheduled before the presentation early-return and uses a bounded 150/300/600/1000 ms miss burst before returning to a 2-second low-frequency cadence.
- Internal discovery retry state is fenced by project + session ID + generation and cleared on candidate discovery, terminal state, STOP/CLEAN invalidation and External ownership changes.
- Foreground lifecycle continuity now restores the current execution's app-private verified Web identity into the endpoint tracker as stale evidence. The card stays AVAILABLE while a silent fresh probe runs after Activity recreation/resume.
- A previously verified endpoint requires two consecutive fresh failures before downgrade. The first miss is confirmation-only and receives a 500 ms recheck.
- Added project-scoped `RuntimeWebLearnedEndpointStore`. Only PID/socket-owned candidates that also pass Android endpoint verification can become cross-run learned endpoints. Runtime-log and merely configured URLs are explicitly excluded.
- Learned endpoint ports are the highest-priority hint on a later START, ahead of detected/configured project ports, framework defaults and the bounded common-port list. They remain hints only and must cross current-run ownership discovery again.
- Current execution Web state is still cleared on START/STOP/CLEAN as before; learned endpoint memory is separate and intentionally survives a new START.
- Embedded CPython now acquires the same `InternalRuntimeForegroundService` session lease used by Internal Alpine. A lightweight monitor releases the exact CPython lease only when that session reaches SUCCEEDED/FAILED/STOPPED or is replaced by a newer session. The service remains a liveness lease only and does not execute, supervise or own Runtime PIDs.
- Foreground-service acquisition now rolls back its in-memory lease if Android rejects service startup, preventing a phantom lease.
- Rich Result parser/lifecycle/viewer production code remains unchanged.
- No Worker, no global PID scan, no 1..65535 port scan, no OCI/situation-monitor project patch and no External START/STOP rewrite were introduced.
- W0 Cloud Build, Internal Alpine Probe, signed APK evidence and real-device acceptance are pending for the final r24 HEAD.


## 2026-09-20 · alpha43-r28 r24 + wake-only baseline

- Restored the r24 Web discovery/readiness/presentation behavior exactly.
- Removed the later four-stage Web presentation integration and HTTP readiness probe.
- Removed the later unified deployment start-contract integration.
- Retained only the Internal Runtime background-protection delta: Foreground Service + PARTIAL_WAKE_LOCK while active Internal Runtime session leases exist.
- Worker remains frozen. STOP remains current-project-only. External Runtime behavior is unchanged.
- versionCode 154 / versionName 0.8.0-alpha43-r28.
- Cloud CI and real-device verification pending.


## 2026-09-20 · alpha43-r29 foreground-ready launch barrier

- r24 remains the known usable product/Web baseline. No Web discovery, Web presentation, Rich Result, External Runtime, Worker, or STOP semantics are changed.
- r28 wake-only result is recorded as REAL_DEVICE_FAIL for the OCI background-continuity goal: holding a PARTIAL_WAKE_LOCK after Runtime launch did not keep OCI progressing after SiftAlpha moved to background.
- r29 changes ordering only: each Internal Runtime session acquires its foreground-service lease and waits until the service has successfully entered foreground state and holds PARTIAL_WAKE_LOCK before the Runtime process/interpreter is launched.
- Internal Alpine launches only after the foreground-ready barrier. If service readiness times out or fails, the Runtime process is not created.
- Embedded CPython uses the same pre-launch foreground-ready barrier.
- Added bounded 5-second service-ready wait and 15-second service heartbeat state.
- Added Internal Alpine diagnostics to copied logs: FGS requested/active, wake-lock held, launch-after-FGS, service PID, Runtime PID, Runtime PID alive, Runtime CPU ticks at launch/current, FGS heartbeat timestamp and heartbeat age.
- These diagnostics are intended to distinguish service death, Runtime process death/freeze, and a still-running Runtime whose application/network work has stopped.
- versionCode 155 / versionName 0.8.0-alpha43-r29.
- Cloud CI and real-device verification pending.


## 2026-09-20 · alpha43-r30 project cache + Internal Web URL wiring

- r24 remains the known usable functional/Web baseline and is not modified.
- r30 is based directly on r29.
- Added persisted Project Index Cache for Runtime Center. Normal Activity/lifecycle/runtime refreshes reuse the selected root URI plus cached project-card identity instead of rescanning SAF on every refresh.
- Explicit "刷新项目" and successful local/GitHub imports request a forced real-project inspection and rewrite the cache.
- Forced project-index refresh reads the selected root once and each project root once, then derives name/source/entry/run/runtime-selection facts from that snapshot instead of repeatedly querying the same SAF directory.
- Added persisted WebProjectInspector and ProjectConfigurationInspector/legacy-secret-policy caches, keyed by project identity and selected root URI. Normal card refreshes reuse them; explicit project refresh bypasses and rewrites them.
- Runtime PREPARE/START facts remain authoritative live reads. The UI cache does not replace action-time source/dependency/runtime inspection.
- Fixed Internal Alpine Web wiring: explicit SIFTALPHA_WEB_URL from the current embedded snapshot is reconciled into RuntimeWebStateStore before the snapshot-presentation early return.
- Explicit Runtime URL remains only a candidate. The existing Android loopback Endpoint Probe must still succeed before Browser presentation is enabled.
- No four-stage Web presentation is restored. No port-range scan, global socket scan, Worker, External Runtime, STOP-scope, or Rich Result behavior is changed.
- versionCode 156 / versionName 0.8.0-alpha43-r30.
- Cloud CI and real-device verification pending.

## 2026-09-20 · alpha43-r31 Internal Runtime Foreground Ownership

- Starting source baseline: d1a4127adb9954711fdefa91ff662e95a3783373, version 0.8.0-alpha43-r30 / 156.
- r24 remains the immutable known-good functional/Web baseline. r31 does not restore the removed four-stage Web presentation or RuntimeWebHttpReadinessProbe and does not change the r30 project-index cache/Web URL wiring.
- Internal Alpine launch ownership moves into InternalRuntimeForegroundService: the service-owned executor launches the child process, keeps the Process plus stdout/stderr file handles, and owns waitFor() monitoring until terminal completion.
- InternalAlpineSession remains the session-state facade; it no longer owns the managed Process or monitor executor.
- STOP remains project scoped. Termination requires both project identity and session lease ID to match the owned session; sibling Internal Runtime sessions are not touched.
- Existing foreground-service, wake-lock, PID, CPU-tick and heartbeat diagnostics remain. Added RUNTIME_OWNER, SESSION_OWNER_SERVICE_PID, RUNTIME_PROCESS_HELD, RUNTIME_MONITOR_ACTIVE and RUNTIME_CPU_TICKS_DELTA.
- Embedded CPython keeps the existing in-process foreground lease path. No Worker, supervisor, runtime gate, External Runtime behavior change, WifiLock or network-policy change is introduced.
- versionCode 157 / versionName 0.8.0-alpha43-r31.
- Cloud CI, Internal Alpine Probe, signed APK evidence and real-device 10-15 minute OCI background acceptance remain required; CI success is not REAL_DEVICE_PASS.

## 2026-09-20 · alpha43-r32 Runtime Center live-output stability

- Starting source baseline: `85304c8b2f5d3530cced0e2932cd13550d2bd520`, version `0.8.0-alpha43-r31` / `157`.
- Real-device r31 observation found two UI regressions while OCI produced continuous Internal Alpine output: the expanded output pane visibly flashed its custom scrollbar from top to bottom, and controls such as STOP/STATUS felt delayed or could appear to lose the first tap.
- Root cause 1: the 180 ms Embedded R poll ran `embeddedPythonSnapshotFor()` on Android's main thread. Internal Alpine snapshots include large stdout/stderr tails, so frequent file reads and text construction competed directly with touch dispatch.
- Root cause 2: stdout/stderr-only snapshot growth returned a generic changed result, causing `refresh()` to rebuild the project list with `removeAllViews()`. Buttons and the nested output ScrollView could be destroyed and recreated during interaction.
- r32 moves Internal snapshot/environment observation to a dedicated single-thread executor and applies only the resulting UI state on the main thread.
- Lifecycle observation remains 180 ms, while large live-output rendering is throttled to 750 ms. Manual STATUS/LOGS, structural state changes and terminal transitions remain immediate.
- Added `EmbeddedPythonObservationPolicy.requiresCardRefresh()`: stdout/stderr-only growth updates the existing output surface and no longer rebuilds the whole project card.
- Embedded STOP now dispatches snapshot lookup and the project-scoped Runtime stop request off the touch/main thread; the UI immediately enters STOPPING/DISPATCHING state.
- `ProjectOutputPanelController` now preserves per-project nested scroll position, skips identical text replacement, suppresses transient scroll callbacks during text replacement, and removes the extra posted frame before tail-follow positioning.
- r31 Internal Runtime Foreground Ownership, project-scoped STOP isolation, r24 Web baseline, r30 caches/Web wiring, External Runtime behavior and Worker freeze are unchanged.
- versionCode 158 / versionName 0.8.0-alpha43-r32.
- Cloud CI + signed APK are required before real-device acceptance. CI success must not be labeled REAL_DEVICE_PASS.

## 2026-09-20 · alpha43-r33 Background Web Continuity telemetry

- Starting source baseline: `e8bac8defc822b7cb2195aa34d5119c642ac6548`, version `0.8.0-alpha43-r32` / `158`.
- Real-device observation: on the same Android phone, SiftAlpha remains in a running state while backgrounded, but the external browser page backed by the Internal Runtime localhost endpoint stops progressing; returning SiftAlpha to foreground causes the page to resume and refresh to current state.
- Same-device loopback access removes Wifi/WLAN continuity as the primary hypothesis. r33 therefore does not add WifiLock.
- r33 adds service-owned background continuity telemetry. The foreground service samples active owned Internal Alpine sessions every 15 seconds even while the Runtime Center Activity is stopped.
- Each sample records service process importance/state/cgroup, Runtime PID alive/state/cgroup, Runtime CPU ticks and per-sample tick delta, stdout byte count/mtime, the latest validated SIFTALPHA_WEB_URL, loopback TCP reachability, wake-lock state, power-save/device-idle state and battery-optimization exemption state.
- Telemetry is written into the current session's app-private `background-continuity.log`, bounded in size, and is included in the next Internal Alpine snapshot/log refresh so real-device evidence survives the Activity background interval.
- The service monitor is diagnostic only: it does not restart, supervise, poll application semantics, widen STOP scope, create a Worker, or alter Runtime ownership.
- r31 foreground ownership, r32 UI responsiveness fixes, r24 Web presentation baseline and External Runtime behavior remain unchanged.
- versionCode 159 / versionName 0.8.0-alpha43-r33.
- Real-device acceptance is diagnostic: background SiftAlpha while the same-device browser remains on the localhost page for 5-10 minutes, then return without restarting and refresh logs. The sample history must identify whether CPU ticks/stdout/Web reachability stop together or diverge.

## 2026-09-20 · alpha43-r34 Internal Web stderr fallback

- Starting source baseline: `2590acef1ec79680c512d55d6d59f970eb638d9a`, version `0.8.0-alpha43-r33` / `159`.
- Trigger: real-device `situation-monitor` remains indefinitely in Web DETECTING even though r24 is the known-good Web baseline.
- r24 vs r33 audit found the core Internal Alpine Web discovery, hint policy, retry cadence, endpoint availability tracker, Web state store, endpoint-probe callback wiring, and descendant-PID traversal unchanged. r30 project-cache identity also preserves the real SAF documentId, and EXPLICIT/PID_SOCKET candidates are not cleared by Web-profile scope reconciliation.
- `situation-monitor` is a Flask project whose Web server starts only after a synchronous initial feed fetch. Flask commonly writes its bound local URL to stderr. Internal Embedded-R snapshot Web fallback was inspecting stdout only, leaving no generic fallback when PID/socket procfs observation misses.
- Added `RuntimeWebDiscoveryScopePolicy.candidateFromStreams()`: inspect both stdout and stderr while preserving evidence priority PID_SOCKET > EXPLICIT > RUNTIME_LOG.
- Generic local URLs from stderr remain gated by an M-confirmed Web profile; non-Web projects cannot promote arbitrary localhost text. EXPLICIT SIFTALPHA_WEB_URL and PID/socket evidence retain their existing semantics.
- Candidate URLs still require the existing Android loopback Endpoint Probe before Web becomes AVAILABLE. No static hint is promoted directly to truth.
- No four-stage Web presentation, RuntimeWebHttpReadinessProbe, unified Project Start Contract, Worker, supervisor, runtime gate, External Runtime change, global PID scan or port-range scan is introduced.
- versionCode 160 / versionName 0.8.0-alpha43-r34.
- Cloud CI + signed APK are required before real-device acceptance. Situation-monitor must transition from DETECTING to AVAILABLE only after its late Flask listener is actually reachable.

## 2026-09-20 · alpha43-r34 baseline freeze

- User real-device assessment: r34 is almost completely usable and is now the current Known Good Functional Baseline.
- Frozen source identity: commit `466e33d5bb0f18bcc1bfa537f5d7ebe5aef93f1c`, tree `6ec24ca0a5a9cca585946306da61fc36741e2aa4`, versionCode `160`, versionName `0.8.0-alpha43-r34`.
- Frozen baseline branch: `baseline/alpha43-r34-known-good`, pointing directly at the accepted r34 source commit.
- Accepted APK SHA-256: `2d91657c07dc7cf75af2545f45587f96d15897ebb3fa5846fdb72cac417d8d0e`.
- Stable test certificate SHA-256: `3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`.
- Cloud evidence: W0 Run #240 PASS, Internal Alpine Probe Run #41 PASS, unit tests + assembleDebug PASS.
- r24 remains the historical Web reference; r34 supersedes it as the current product-level known-good baseline.
- This freeze is documentation-only. No application source, applicationId, Runtime behavior, Web behavior, STOP behavior or version number is changed by the freeze commit.
- Future regressions must compare against the exact r34 commit above. The baseline branch must not be advanced or rewritten.

## 2026-09-20 · alpha43-r35 CLI Launch Configuration

- Starting development HEAD: `3964ad160fd931b1bf41b05c1541e30ac385c028`, whose parent r34 source is frozen separately at `baseline/alpha43-r34-known-good`.
- Trigger: a newly imported Python project exited with code 2 while Project Config reported no required configuration. The project requires CLI positional arguments such as `MARKET` and `CODE`.
- Root gap: SiftAlpha already had `PythonCliLaunchResolver`, `PythonLaunchInvocation`, `RuntimeArgumentParser`, and an External Runtime argument dialog, but Project Config modeled only environment/config/secret requirements and the Internal Runtime launch boundary dropped argv entirely.
- Added high-confidence static argparse inspection for literal scalar required positionals and valued options declared with `required=True`. Dynamic parser construction, boolean actions and repeated nargs remain fail-closed and are left to runtime diagnostics.
- Project configuration profiles now carry CLI requirements separately from environment requirements. The UI explicitly labels them as runtime launch parameters and no longer says there is no required configuration when required CLI input is known.
- Added argparse runtime diagnosis for `the following arguments are required: ...`; runtime-discovered CLI tokens are persisted as project-scoped hints and participate in the next Run flow.
- Python Run now enters the CLI flow for both External Runtime and Internal Runtime when a non-Web Python CLI project or required CLI arguments are known.
- Internal Runtime argv is now carried through `PythonLaunchInvocation` -> `ProjectRuntimeController` -> Embedded CPython/Internal Alpine.
- Embedded CPython now builds `sys.argv = [entrypoint] + arguments` at the native execution boundary. Internal Alpine builds a safely single-quoted argv command without eval or shell interpolation of user values.
- Internal runtime terminal failures also feed the existing runtime-configuration diagnostic once per session/generation.
- r34 remains immutable as the Known Good Functional Baseline; r35 does not modify the frozen baseline branch.
- versionCode 161 / versionName 0.8.0-alpha43-r35.
- First contract intentionally supports file-backed Python argv end-to-end. Existing External Runtime console-script support remains; Internal Runtime console-script execution is not widened by this change.

### r35 pre-release native argv wiring correction

- Pre-release source review caught that the first r35 native edit added the argv vector to the snapshot struct but not the live Session struct while later code referenced `session->arguments`.
- Corrected the live Embedded CPython Session to own `std::vector<std::string> arguments`. No released APK existed from the broken commit, so versionCode remains 161 / versionName 0.8.0-alpha43-r35.
- The frozen r34 baseline remains unchanged.

### r35 external final-log CLI diagnosis

- External automatic observation already requests one final LOGS result after STATUS detects a terminal project. r35 now feeds that final Runtime output through the same configuration diagnostic.
- Automatic final-log diagnosis is silent: it persists high-confidence missing CLI hints without opening a repeated dialog. A later Run uses those hints to present launch-argument input.
- Manual LOGS may present the actionable configuration finding. Unrelated failures continue through the existing Runtime error path.

### r35 pre-release Kotlin diagnostic dedupe correction

- W0 Run #245 passed repository validators but Kotlin compilation found the terminal embedded-configuration diagnostic referenced an Activity-lifetime dedupe set that was missing from the final merged declaration block.
- Restored `embeddedConfigurationFindings` as a session/generation-keyed set. This is a pre-release correction only; r35 remains versionCode 161.

## 2026-09-20 · alpha43-r36 Click/Typer CLI discovery

- Trigger: r35 cloud validation passed, but real-device Project Config for `easy_tdx-main` still reported no required configuration while the runtime exited with code 2.
- Real-device evidence and prior runtime output showed the actual CLI error format:
  `Usage: run_all_strategies.py [OPTIONS] MARKET CODE`
  followed by `Error: Missing argument 'MARKET'.`
- Root cause: r35's first CLI discovery contract recognized argparse only. The affected project uses Click/Typer-style semantics, so both static discovery and runtime diagnostic fallback missed the required positionals.
- Added high-confidence Click decorator inspection for literal required `@click.argument(...)` positionals.
- Added high-confidence Typer inspection for literal `typer.Argument(...)` parameters using the required ellipsis contract.
- Runtime diagnostics now understand Click/Typer `Missing argument`, `Missing option`, and required unbracketed metavars from a `Usage: ... [OPTIONS] ...` line. Generic COMMAND/ARGS meta words are excluded.
- Project Python inspection now prioritizes likely executable files such as `main.py`, `app.py`, `cli.py`, `__main__.py`, and top-level `run*.py` / `start*.py` before the bounded scan cap, so a real launcher is not displaced by unrelated alphabetically earlier modules.
- Project configuration profile cache namespace advanced to v3 so r35's cached empty CLI profile cannot survive into r36.
- r35 argv execution wiring is retained unchanged; this round changes discovery and stale-cache behavior, not Runtime ownership or Web behavior.
- r34 remains the frozen Known Good Functional Baseline.
- versionCode 162 / versionName 0.8.0-alpha43-r36.

## 2026-09-20 · alpha43-r37 entry-bound CLI launch

- Trigger: r36 real-device run correctly discovered two CLI values and passed two argv items, but selected the unrelated `easy-tdx` console script from `pyproject.toml` instead of the project entrypoint `run_all_strategies.py`.
- Real-device evidence: `SIFTALPHA_LAUNCH_KIND=CONSOLE_SCRIPT`, `SIFTALPHA_LAUNCH_EXECUTABLE=easy-tdx`, `SIFTALPHA_LAUNCH_ARGUMENT_COUNT=2`, followed by `Error: No such command 'SH'.`
- Root cause: project-level CLI requirements were applied to whichever structured Python launch resolver won. The resolver prefers pyproject console scripts before fallback Python files, so requirements discovered in `run_all_strategies.py` were incorrectly attached to `easy-tdx`.
- Added entry-bound CLI compatibility policy: a console-script resolution is overridden by the fallback Python file only when every required CLI item has explicit file evidence and that evidence points to the same resolved fallback entrypoint.
- Runtime-only hints or requirements from another file never change the launch target.
- r35 argv transport and r36 Click/Typer discovery remain unchanged.
- versionCode 163 / versionName 0.8.0-alpha43-r37.

## 2026-09-20 · alpha43-r38 Local Result Web Host

- r37 CLI Launch Configuration received real-device acceptance: `exitCode=0`, `SIFTALPHA_LAUNCH_KIND=PYTHON_FILE`, executable `python`, and the easy_tdx strategy run completed all 16 strategies successfully.
- New UX gap: successful one-shot CLI analysis results were still mixed into Raw Log diagnostics, making rankings, metrics and trade records difficult to inspect.
- r38 adds an app-owned Local Result Web Host bound only to loopback. Result pages are served from `localhost` and opened inside SiftAlpha through a hardened WebView; no result data is uploaded.
- Successful terminal one-shot output is separated from SiftAlpha Runtime envelope lines, redacted before result generation, analyzed, rendered to HTML, persisted in app-private storage, and exposed as the project's primary Result Web presentation when no project-owned Web page is available.
- Adaptive layout is output-first and source-aware:
  - section banners / Markdown headings -> sections;
  - key/value runs and pipe-separated key/value summaries -> metric cards;
  - whitespace tables and CSV/TSV -> responsive tables;
  - date/numeric x-axis tables with numeric series -> inline SVG line charts;
  - unrecognized content -> safe preformatted text;
  - selected Python entrypoint source is read only as bounded Presentation Hints for tabular/chart/JSON/CSV/Markdown intent. Source hints never override actual Runtime output and never fabricate chart data.
- Generated HTML escapes program content, disables JavaScript in the in-app WebView, serves only app-private stored result IDs, and applies restrictive HTTP response headers.
- The result store keeps recent generated result pages in app-private files and deduplicates repeated final-log observations by content fingerprint.
- Project-owned Web remains higher priority than Result Web. Result Web is higher priority than the legacy LINK_LIST Rich Result viewer.
- A new START suppresses the previous run's Result Web from the active project card until the new run produces a result; historical files remain stored.
- External structured Python-file launches now emit `SIFTALPHA_LAUNCH_ENTRYPOINT` so the result analyzer can inspect the exact source file that produced the output. Internal Runtime already exposes its entrypoint in the session snapshot.
- r34 remains the frozen Known Good Functional Baseline. r38 does not change Worker freeze, project-scoped STOP, Runtime ownership, Web discovery truth gates, or External/Internal argv semantics.
- versionCode 164 / versionName 0.8.0-alpha43-r38.

### r38 pre-release Python launch marker correction

- W0 Run #249 reached Kotlin compilation after repository validators passed and exposed a malformed string splice in the new `SIFTALPHA_LAUNCH_ENTRYPOINT` marker.
- The malformed line truncated the existing r37 launch-marker block, causing the remaining embedded shell script to be parsed as Kotlin and producing a large cascade of unrelated syntax errors.
- Restored the exact r37 launch-kind/executable/argument-count block and inserted the new entrypoint marker as one additional guarded line only.
- No r38 APK was released from the broken commit, so versionCode remains 164 / versionName 0.8.0-alpha43-r38.
- r37 CLI execution behavior and the frozen r34 baseline remain unchanged.

### r38 pre-release WebViewClient nullability correction

- W0 Run #251 passed repository validators and reached Kotlin compilation.
- Android WebView's `webViewClient` property is non-null in the current SDK stubs, so assigning `null` during ResultWebActivity teardown failed compilation.
- Teardown now replaces the client with a fresh inert `WebViewClient` before destroy. No released APK existed from the failed build, so r38 remains versionCode 164.

### r38 pre-release adaptive result section-copy correction

- W0 Run #252 reached unit tests and exposed four `ConcurrentModificationException` failures in AdaptiveResultTest.
- Root cause: `trimBlankEdges()` returned a `subList` view of the mutable section buffer; `splitSections.flush()` then cleared the backing buffer, invalidating the stored section view.
- The helper now materializes an immutable copy before the buffer is cleared. The parser/rendering contract is otherwise unchanged.
- No r38 APK was released from the failed build, so versionCode remains 164.
