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
