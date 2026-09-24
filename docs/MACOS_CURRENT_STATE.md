# SiftAlpha X macOS Current State（macOS 当前状态）

> 本文件只记录“现在”的权威事实。历史过程、旧状态和已结束切片统一保留在 `MACOS_DEV_LOG.md`。
> 每轮 macOS（苹果桌面系统）工作开始前必须重新读取真实远端和本文件。

## 1. 当前阶段

- Product Display Name（产品显示名称）：**SiftAlpha X**
- Repository（仓库）：`kuashan/siftalpha-one`
- Branch（分支）：`feature/cross-platform-core`
- M0 Governance（开发治理）：PASS / CLOSED（通过 / 关闭）
- M1 Core Boundary（核心边界）：PASS / CLOSED（通过 / 关闭）
- M2 macOS Host Skeleton（macOS 主机骨架）：PASS / CLOSED（通过 / 关闭）
- M3 Host Runtime Provider（主机运行提供者）：**PASS / CLOSED（通过 / 关闭）**
- 当前阶段：**M4 — Project Workflow（项目工作流）**
- M4 implementation（实现）：NOT STARTED（尚未开始）

M3 最后一个已验收功能 HEAD：
`259d94fe7d7501a1232f1d7114445f4673cfc0a1`

Android（安卓）最新已验收回归基线仍为：
`0.8.0-alpha43-r48d11-m1.6-r1` / versionCode `232`.

## 2. 品牌与技术身份

用户可见品牌统一为：

**SiftAlpha X**

macOS 窗口标题、应用标题与测试包显示名称使用 `SiftAlpha X`。

技术身份不因品牌显示名改变而自动迁移：

- Android applicationId（安卓应用 ID）保持 `com.siftalpha.studio`
- macOS package identifier（苹果包标识）保持 `com.siftalpha.macos`
- repository / module / internal protocol names（仓库 / 模块 / 内部协议名）按技术需要保持稳定

## 3. macOS 最低系统版本

当前最低支持版本冻结为：

**macOS 10.15.7 Catalina**

规则：

- 能继续兼容 10.15.7 时，不主动提高最低系统版本。
- 不得为了开发方便静默提高系统要求。
- 如果未来必要 Apple API（系统接口）、Runtime（运行时）、Container / Virtualization（容器 / 虚拟化）、Signing / Notarization（签名 / 公证）或其他硬依赖要求更高系统，修改前必须先说明具体原因、新最低版本和影响，并获得用户确认。
- 当前 Catalina 测试包 launcher / libjli / libjvm 的 Mach-O MIN_OS 均已验证为 10.12。

## 4. M3 最终能力事实

M3 已建立 macOS Platform Adapter（苹果平台适配层）的 Host Runtime Provider（主机运行提供者）基础闭环：

- Python 3 discovery（Python 3 发现）
- Node.js discovery（Node.js 发现）
- Bun discovery（Bun 发现）
- Git discovery（Git 发现）
- Shell discovery（Shell 命令环境发现）
- Host process start（主机进程启动）
- stdout / stderr capture（标准输出 / 标准错误采集）
- STATUS（状态）
- project-scoped STOP（项目级停止）
- Project A / B concurrent isolation（并行项目隔离）

Runtime Discovery（运行时发现）规则：

- AVAILABLE / UNAVAILABLE（可用 / 不可用）都是合法事实；
- Python Host Runtime（主机 Python）只接受 Python 3，不接受 Python 2；
- Discovery（发现）必须是 passive（被动的），不得仅因探测而弹出 Apple Command Line Tools（命令行开发者工具）安装窗口；
- Catalina 未安装 Command Line Tools 时，已知 Apple developer-tool shim（开发者工具占位命令）必须跳过；
- 用户自己安装的 Homebrew / MacPorts / asdf / Volta / Bun / PATH 工具仍可被发现。

## 5. M3 Closure Audit（关闭审计）

M3 Frozen Exit Criteria（冻结退出条件）最终结果：

1. Python discovery fact：PASS
2. Node.js discovery fact：PASS
3. Bun discovery fact：PASS
4. Git discovery fact：PASS
5. Shell discovery fact：PASS
6. Process start：PASS
7. stdout / stderr capture：PASS
8. STATUS：PASS
9. selected-project STOP：PASS
10. Project A/B concurrent isolation：PASS
11. Android regression：PASS — W0 #719
12. Catalina compatibility：PASS — macOS Run #10
13. Real Mac M3 Self-Test：PASS — macOS 10.15.7 Catalina / Intel x86_64

Real Mac（真实 Mac）还确认：

- 打开修复包不再触发 Apple Command Line Tools 安装提示；
- legacy Python 2.7.16 不再被判为可用 Host Python；
- `Process self-test: PASS`。

最终结论：

**M3 = PASS / CLOSED（通过 / 关闭）。**

不得再新增新的必做 M3.x 切片；非阻塞优化进入 Backlog（待办）。

## 6. 当前尚未实现

以下仍属于后续阶段，不得误报为已经完成：

- macOS ProjectFilesystem（苹果项目文件系统）生产实现与真实项目导入
-完整 Import → Detect → Plan → Prepare → Run → Logs → Stop / Restart（导入 → 检测 → 计划 → 准备 → 运行 → 日志 → 停止 / 重启）工作流
- Web Discovery / Endpoint Probe（网页发现 / 端点探测）的 macOS 项目工作流接线
- macOS secure storage（苹果安全存储）
- Normal Mode / Developer Mode（普通模式 / 开发者模式）正式产品 UI
- Container / Compose / Multi-service（容器 / 多服务）
- OpenBot（开放机器人）真实验收
- Developer ID / Hardened Runtime / Notarization / DMG-PKG（签名 / 强化运行时 / 公证 / 正式分发）

## 7. 跨平台硬边界

1. Core（核心）只承载真正平台无关的模型、策略与接口。
2. macOS 平台实现留在 macOS Platform Adapter（苹果平台适配层）。
3. 不为了 macOS 修改 Android（安卓）特有实现。
4. 不要求 Android / Windows（安卓 / 微软）实现 macOS 专属能力。
5. Normal Mode / Developer Mode（普通 / 开发者模式）必须共享同一 Core（核心）和 Runtime State（运行状态）。
6. STOP（停止）永远是 project-scoped（项目级）：只停止当前项目及其归属进程，不影响其他项目。
7. OpenBot（开放机器人）不得使用项目专属补丁作为验收手段。
8. macOS 不建立独立 Core（核心）副本。

## 8. Change Discussion Gate（修改前讨论门禁）

从现在起，每一个源码、CI（持续集成）、架构、行为或产品显示修改任务，都必须按以下顺序执行：

1. **Read-only Audit（只读审查）**
   - 重新读取真实远端 HEAD；
   - 读取本文件、`MACOS_DEV_LOG.md`、`MACOS_TEST_MATRIX.md`、`PLATFORM_CAPABILITY_CONTRACT.md`、`CROSS_PLATFORM_CORE.md`；
   - 读取与问题直接相关的真实源码。
2. **Problem（问题）** — 用明确语言说明哪里异常。
3. **Root Cause（根因）** — 说明为什么发生。
4. **Repair Plan（修复方案）** — 说明准备怎样修。
5. **Modification Scope（修改范围）** — 明确会改哪些模块/文件。
6. **Frozen Boundary（冻结边界）** — 明确哪些模块/行为不会改。
7. **Risk（风险）** — 明确可能影响。
8. **Acceptance（验收）** — 说明什么证据才算 PASS。
9. **User Approval（用户确认）** — 未获得明确“开始 / 同意 / 执行”等授权前，不得写代码或修改 CI/文档事实。
10. 获得授权后才进入 Implementation → Cloud Verification → Real Device / Real Mac Acceptance → Documentation → Closure（实现 → 云端验证 → 真机验收 → 文档 → 关闭）。

这条门禁适用于“已知怎么修”的问题；知道答案不能替代修改前讨论。

## 9. Remote HEAD Drift Gate（远端漂移门禁）

任何写操作前必须再次读取真实远端 HEAD。

如果与本轮已确认起点不同：

- 立即停止写操作；
- 报告 `REMOTE_HEAD_CHANGED`；
- 不自行 rebase（变基）；
- 不自行 merge（合并）；
- 不 force push（强制推送）；
- 重新审查漂移后，再按 Change Discussion Gate（修改前讨论门禁）讨论。

## 10. Stage Closure Rule（阶段关闭规则）

- M0～M8 是固定一级阶段。
- Mx.y 只允许作为有限 Implementation Slice（实现切片）。
- 一级阶段 Exit Criteria（退出条件）全部有证据后，只执行一次 Closure Audit（关闭审计）。
- Closure Audit PASS 后必须立即 PASS / CLOSED 并进入下一阶段。
- “还能继续优化”不能阻止关闭。
- 非阻塞优化进入 Backlog（待办）。
- 不允许通过新增无限 Mx.y 延迟收尾。

## 11. M4 当前唯一方向

当前正式进入：

**M4 — Project Workflow（项目工作流）**

M4 只负责把现有 Core（核心）与 macOS Host Runtime Provider（主机运行提供者）接成真实项目工作流。

冻结 Exit Criteria（退出条件）继续以 `MACOS_DEV_LOG.md` / `MACOS_TEST_MATRIX.md` 已定义内容为准，包括至少：

- 普通 Python 项目完整 Import → Detect → Plan → Prepare → Run → Logs → Stop / Restart 闭环；
- Node.js 项目同样闭环，或明确记录本阶段范围；
- Environment Plan（环境计划）继续只表达需求，Provider（提供者）负责满足需求；
- Web Discovery + Endpoint Probe（网页发现 + 端点探测）可得到可用结果入口；
- 失败状态与日志可诊断；
- 真实 Mac 验收通过。

**当前不开始 M4 实现，直到下一轮按照 Change Discussion Gate（修改前讨论门禁）先讨论具体实现方案。**
