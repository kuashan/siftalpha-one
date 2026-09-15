# SiftAlpha Studio 开发日志

本日志按时间顺序追加。每条记录包含目标、变更、验证和后续事项。公开日志不记录密码、密钥值、用户项目配置值或其他敏感内容。

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
