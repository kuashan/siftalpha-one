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

## 2026-09-17 · alpha44 Slice 2 — Dedicated Worker + Typed IPC Handshake

- alpha44 Slice 1 Runtime Load Binding Contract remains complete at version `120 / 0.8.0-alpha44`.
- Added the R-only `EmbeddedPythonWorkerService` in the dedicated `:siftalpha_r_worker` Android process.
- Added a typed AIDL handshake that exposes worker identity/PID and validates `RuntimeLoadBindingV1` before applying the process-scoped binding gate.
- The worker accepts a first valid binding, treats the same binding as idempotent, and deterministically rejects a different binding without replacing the original.
- Slice 2 does not load CPython or JNI, does not move the existing execution path, and is not connected to normal user flow. Instrumentation is compile-only until a device/emulator run is explicitly performed.

## 2026-09-17 · alpha44 Slice 3 — Worker Termination + Fresh Process Transition

- Added a fenced typed termination request requiring the current `WorkerInstanceId` and `ProcessBindingId`.
- A valid request moves the process-scoped worker from `ACTIVE` to `TERMINATING`; repeated requests are idempotent, while all further binds are rejected until the OS process dies.
- The worker verifies it is running as `:siftalpha_r_worker`, returns the structured exit result before scheduling a short delayed self-termination, and never exposes a binding reset operation.
- Instrumentation now specifies one A → Binder DeathRecipient → fresh worker B flow. It is compiled in CI but has not been executed on a real device or emulator.
- Slice 3 does not load CPython/JNI, migrate execution, add a supervisor, or connect the worker to normal user flow.

## 2026-09-18 · alpha44 Slice 4 — Worker-side Embedded CPython Smoke Execution

- The dedicated :siftalpha_r_worker process now loads and executes the fixed stdlib-only CPython smoke fixture through typed Binder IPC.
- Real-device instrumentation EmbeddedPythonWorkerCpythonSmokeInstrumentedTest#workerCpythonSmokeExecutesInsideDedicatedProcess completed successfully: OK (1 test), Time 1.254.
- The evidence covered caller/test process -> typed Binder IPC -> Worker CPython, os.getpid() == Worker PID, and typed smoke result fields.
- Slice 4 remains R-only and is not connected to normal-user flow.

## 2026-09-18 · alpha44 Slice 5 — Worker-side File-backed Project Execution Contract

- Added the R-only typed Binder contract for finite execution of caller-staged, app-private Python project files in :siftalpha_r_worker.
- The Worker obtains projectIdentity from its immutable process-scoped RuntimeLoadBindingV1; callers provide only the staged execution root and safe relative entrypoint/working-directory values.
- Runtime preparation now repairs only files/siftalphax/python; it preserves caller-owned files/siftalphax/projects staging so a first Worker prepare cannot delete a staged project.
- Added process-local finite execution state, native snapshot identity fencing, typed success/failure/internal-error results, sequential re-entry, and Smoke-versus-project native-session exclusion.
- Added a single source/instrumentation flow for success then expected Python failure, including runtime-rebuild preservation. The Slice 5 real-device acceptance subsequently passed; CI still compiles the instrumentation APK without running instrumentation.
- Slice 5 does not connect the Worker to M, migrate production START/STATUS/STOP, add long-running STOP, or implement a production Worker Supervisor.

### alpha44 Slice 5 — Real-device acceptance

`EmbeddedPythonWorkerProjectExecutionInstrumentedTest#workerExecutesCallerStagedFiniteProjectWithTypedResults`
completed on a real Android device with `Time: 1.484` and `OK (1 test)`. The evidence covers caller-staged app-private project execution, typed results, expected Python failure/traceback, runtime-rebuild preservation, and sequential re-entry in the dedicated Worker process.

## 2026-09-18 · alpha44 Slice 6 — Worker-side Long-running STOP + Re-entry

- Added the R-only typed cooperative STOP contract for a specific project execution, fenced by WorkerInstanceId, ProcessBindingId, execution sessionId, and execution generation.
- STOP is accepted only for the current `RUNNING` execution. The exact project runtime-use lease remains held until the native terminal snapshot is published; stale STOP requests cannot reach `nativeRequestStop()` or affect a later execution.
- Project polling no longer has a fixed 30-second wall-clock timeout. The existing 30-second bound remains only for the one-shot CPython smoke path.
- A stopped execution can re-enter sequentially in the same Worker process and RuntimeLoadBinding with a new session and higher generation. STOP uses the existing native cooperative-stop capability and does not kill the Worker process.
- The new long-running instrumentation flow and Slice 6 JVM coverage are included in source; CI compiles the instrumentation APK but does not execute it on a device. The real-device acceptance is recorded in the following entry.
- Slice 6 does not connect the Worker to M, add a production Supervisor, add hard-kill fallback, or migrate production START/STATUS/STOP.

### alpha44 Slice 6 — Real-device acceptance

`EmbeddedPythonWorkerLongRunningStopInstrumentedTest#workerStopsLongRunningProjectAndAllowsReentry`
completed on a real Android device with `Time: 0.958` and `OK (1 test)`. The evidence covers long-running execution, typed cooperative STOP, exit code 130, stale STOP fencing, and same-Worker sequential re-entry.

## 2026-09-18 · alpha44 Slice 7 — Worker Execution Status / Result Snapshot Hardening

- Added the R-only typed `EmbeddedPythonWorkerExecutionSnapshotV1` Parcelable and one-transaction Binder STATUS path. Scalar execution getters remain only for Slice 4–6 compatibility and diagnostics.
- The snapshot captures Worker process identity/lifecycle, binding identity, current execution identity, project paths, `stopRequested`, and bounded terminal output from immutable process/execution state captures.
- Terminal results remain sealed until a later execution is accepted; Binder clients can recover the current execution identity after reconnect, while a fresh Worker process starts with a new `WorkerInstanceId` and an empty execution slot.
- Slice 7 source and JVM/instrumentation coverage are present. The real-device instrumentation
  `EmbeddedPythonWorkerExecutionSnapshotInstrumentedTest#workerSnapshotSurvivesBinderReconnectAndFencesProcessLifetime`
  completed with `Time: 1.608` and `OK (1 test)`. The cross-domain stabilization race is covered
  by deterministic JVM tests.

## 2026-09-18 · alpha44 Slice 8 — Production Worker Supervisor Foundation

- Added the R-only `EmbeddedPythonWorkerSupervisor` connection/lifetime foundation without
  connecting it to the Management layer or normal-user flow.
- The Supervisor uses explicit bind/connect and disconnect/reconnect, typed
  `WorkerExecutionSnapshotV1` refresh, `linkToDeath`, and connection-epoch fencing for stale
  ServiceConnection and Binder callbacks.
- Worker loss transitions to `CONNECTION_LOST`, clears the live Worker snapshot, and preserves
  only `lastWorkerInstanceId` for process-epoch diagnostics. There is no automatic reconnect,
  Worker restart, RuntimeLoadBinding rebind, or project restart.
- Slice 8 source, JVM state-machine coverage, and instrumentation source are present; CI only
  compiles the instrumentation APK. Slice 8 real-device instrumentation has not yet been
  executed.


## 2026-09-18 · alpha44 R Compatibility Repair — Automatic Execution-Space Foundation

### Product invariant

This repair restores the intended product direction: users import a project and use it; they are not
required to understand or choose Embedded R versus External Provider. R evolves by adding compatible
execution spaces and capabilities, not by sacrificing project classes that already worked.

The compatibility baseline is monotonic for accepted regressions:

`SupportedProjects(R_new) ⊇ AcceptedRegressionProjects(R_previous)`.

### Routing repair

- Standard project cards no longer expose the execution-space selector.
- START now resolves execution space automatically from current project/runtime facts.
- Python projects with the accepted file-backed entrypoint boundary can continue into Embedded R.
- A non-Python project, unresolved Embedded R entrypoint, unavailable Embedded R runtime, supplemental
  runtime, or non-direct declared launch command can route to the External Provider automatically.
- Dependency-manifest presence and protected-configuration metadata are no longer hard bans on
  Embedded R. They remain capability evidence for future environment/configuration work.
- Runtime recovery is based on actual active Embedded R ownership rather than a stale persisted
  provider preference.

### alpha44 Worker boundary

Slice 1–8 Worker/Snapshot/Supervisor work is retained unchanged. This repair does not migrate
production START/STATUS/STOP to the Worker and does not delete the existing Embedded R production
path. Worker migration remains gated on capability parity.

### Verification status

JVM/CI coverage is updated for automatic routing and compatibility semantics. Real-device regression
for the user's Oracle Cloud long-running project remains pending and must be executed after Source + CI
review before this compatibility repair is considered closed.


## 2026-09-18 · alpha44 R Compatibility Repair — CPython main-thread signal semantics

Oracle Cloud real-device regression reached Embedded R successfully
(`ENGINE=CPYTHON`, `TERMUX=NOT_USED`, `PROOT=NOT_USED`) and exposed a deeper R compatibility defect:
`signal.signal(SIGTERM, ...)` failed with
`ValueError: signal only works in main thread of the main interpreter`.

Root cause was the alpha29 re-entry bootstrap design: CPython was initialized on a temporary native
bootstrap thread which then exited, while every real project session executed on a different detached
thread. That made standard-library signal registration impossible even though ordinary Python code and
STOP/re-entry fixtures passed.

The repair keeps one process-lifetime native execution thread. CPython is initialized on that thread,
its main-interpreter thread state is detached between sessions and restored for each project execution,
and all sessions are serialized onto the same thread. This preserves the existing single-session R
contract, STOP control thread, re-entry behavior, and process-lifetime interpreter while restoring
CPython main-thread semantics required by ordinary scripts.

A real-device instrumentation regression was added to register `SIGTERM` handlers in two sequential
Embedded R sessions. OCI compatibility remains pending until the new artifact passes CI and the real
Oracle Cloud project is rerun on device.


## 2026-09-18 · OCI real-project regression — dependency execution-space routing

The unchanged Oracle Cloud catcher progressed beyond the previous signal failure on real device.
Observed evidence:

- Embedded CPython started with Termux/PRoot unused.
- The Web dashboard started and published `http://127.0.0.1:8080/`.
- The project then detected that the third-party `oci` module was missing and entered its existing
  auto-install path.
- That path launches `[sys.executable, "-m", "pip", ...]`. Current Embedded R intentionally packages
  the CPython shared library + stdlib only; it does not package a standalone Python executable,
  `pip`, or `ensurepip`. The resulting empty `sys.executable` caused
  `PermissionError: [Errno 13] Permission denied: ''`.

This is an R capability/routing gap, not a project-source defect. The OCI source remains unchanged.

AUTO routing is corrected accordingly: dependency requirements and protected configuration are routing
evidence. Until Embedded R gains its planned environment/dependency/configuration capabilities, AUTO
selects the already-capable External Provider for those projects. Explicit Embedded R remains an
internal diagnostic/compatibility path; normal users do not choose providers.

This preserves the product contract: import project -> SiftAlpha detects requirements -> SiftAlpha
chooses a compatible execution space. The user is not asked to rewrite code to fit a narrower runtime.


## 2026-09-18 · alpha45 Slice 1 — Pure-Python Environment Lock Contract

alpha45 starts after the accepted alpha44 process-isolated Worker foundation. This slice is R-only and
does not connect the Worker to the normal-user Management flow.

Implemented a bounded typed `pylock.toml` consumer for the Pure-Python Environment v1 preparation
pipeline. It accepts only the frozen SiftAlpha v1 structural subset: lock-version 1.0, explicit
requires-python, non-empty created-by, empty extras/dependency-groups/default-groups, package records,
and exact wheel origins with positive size plus lowercase SHA-256. VCS, directory, archive and sdist
package sources are rejected as unsupported. Project-local wheel paths are constrained to safe relative
POSIX paths and remote wheel origins require HTTPS without embedded credentials.

This slice deliberately does not resolve dependencies, access the network, evaluate markers or
Requires-Python, select wheel tags, download/extract/install wheels, create site-packages, or modify M.
Those remain later alpha45 slices. Existing External Provider behavior and alpha44 Worker execution
contracts are unchanged.

Version advances to `121 / 0.8.0-alpha45`.


## 2026-09-18 · alpha45 Slice 2 — Dependency Fingerprint + Environment Identity

Implemented the next R-only Pure-Python Environment v1 identity layer.

This slice adds deterministic `DependencyFingerprintV1` from an already-selected dependency set and a
project-owned `EnvironmentIdV1`. The dependency fingerprint includes the frozen runtime context,
exact wheel filename/hash/origin, normalized package identity, selected tags and installer policy.
Package and tag input order are canonicalized so equivalent selections produce the same fingerprint.

`EnvironmentIdV1` binds the dependency fingerprint to stable project identity, runtime contract,
Runtime Base provenance and installer policy. Therefore two Projects with identical dependencies still
receive different environment IDs; this prevents mutable site-packages ownership from being shared by
accident.

This slice does not yet evaluate markers or Requires-Python, select wheels from pylock candidates,
download/install artifacts, create generations/site-packages, or change M/normal-user routing.
Existing alpha44 Worker and External Provider behavior remain unchanged.

VersionCode advances to `122`; versionName remains `0.8.0-alpha45`.
