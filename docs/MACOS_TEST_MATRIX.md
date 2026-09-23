# SiftAlpha macOS Test Matrix（macOS 测试矩阵）

> 本文件定义 macOS（苹果桌面系统）每个阶段必须通过的验收门槛。
> 未达到对应门槛时，不得把阶段标记为完成。

## 全局回归门槛

任何修改 SiftAlpha Core（跨平台核心）的提交都必须满足：

| 验证项 | 要求 |
|---|---|
| Core tests（核心测试） | PASS（通过） |
| Android unit tests（安卓单元测试） | PASS（通过） |
| Android assembleDebug（安卓调试包构建） | PASS（通过） |
| Android stable signing（安卓稳定签名） | 不得意外变化 |
| Android Runtime behavior（安卓运行行为） | 不得因 macOS（苹果）能力被强制改变 |
| Capability isolation（能力隔离） | 不支持的能力必须允许 UNAVAILABLE / UNKNOWN（不可用 / 未知） |

## M0 — Governance（开发治理）

| 项目 | 状态 |
|---|---|
| macOS Current State（当前状态） | PASS（通过） |
| macOS Dev Log（开发日志） | PASS（通过） |
| macOS Test Matrix（测试矩阵） | PASS（通过） |
| Platform Capability Contract（平台能力契约） | PASS（通过） |
| Android regression after capability contract（能力契约后的安卓回归） | PASS（通过），W0 #672 |
| Internal Alpine Probe（内部 Alpine 探针） | PASS（通过），#114 |

## M1 — Core Boundary（核心边界）

必须证明：

- 被迁移到 `:core` 的代码不 import Android API（导入安卓接口）。
- Core（核心）不得包含 macOS / Windows / Android 平台分支实现。
- Android（安卓）继续使用原有平台实现。
- Core tests（核心测试）覆盖迁移后的公共规则。
- Android（安卓）完整回归通过。

当前状态：**IN PROGRESS（进行中）**

## M2 — macOS Host Skeleton（苹果主机骨架）

必须证明：

- macOS（苹果）应用可构建。
- macOS（苹果）应用可启动。
- 可加载 SiftAlpha Core（核心）。
- 可读取基本平台能力快照。
- 不要求 Android（安卓）库参与 macOS（苹果）构建。

当前状态：**NOT STARTED（未开始）**

## M3 — Host Runtime Provider（主机运行提供者）

必须证明：

- Python（Python 运行时）发现。
- Node.js（Node 运行时）发现。
- Bun（Bun 运行时）发现。
- Git（版本控制）发现。
- 启动一个项目进程。
- 捕获 stdout / stderr（标准输出 / 标准错误）。
- STATUS（状态）正确。
- STOP（停止）只影响当前项目。
- 另一个同时运行的项目不受影响。

当前状态：**NOT STARTED（未开始）**

## M4 — Project Workflow（项目工作流）

必须证明：

- Import（导入）成功。
- Detect（检测）成功。
- Environment Plan（环境计划）正确。
- Prepare（准备）成功。
- Run（运行）成功。
- Logs（日志）可读。
- Web Endpoint（网页端点）可发现和验证。
- Stop / Restart（停止 / 重启）闭环。

当前状态：**NOT STARTED（未开始）**

## M5 — Product UI Parity（产品界面对齐）

必须证明：

- Normal Mode（普通模式）可以完成导入 → 准备 → 运行 → 查看结果 → 停止。
- Developer Mode（开发者模式）可以查看高级运行事实。
- 两种模式读取同一个 Runtime（运行时）状态。
- 不存在两套独立生命周期。

当前状态：**NOT STARTED（未开始）**

## M6 — Container / Multi-service（容器 / 多服务）

必须证明：

- Docker / Podman（容器）能力检测。
- Container Capability（容器能力）可明确 AVAILABLE / UNAVAILABLE / UNKNOWN（可用 / 不可用 / 未知）。
- Compose（多服务编排）项目启动。
- 多服务日志归属正确。
- 端口映射可发现。
- project-scoped STOP（项目级停止）不影响其他项目。

当前状态：**NOT STARTED（未开始）**

## M7 — OpenBot Acceptance（OpenBot 验收）

必须证明：

- 使用原始 OpenBot（开放机器人）项目，不修改其源码适配 SiftAlpha。
- Bun（Bun 运行时）依赖识别正确。
- Docker / Compose（容器 / 多服务编排）需求识别正确。
- PostgreSQL + pgvector（数据库 + 向量扩展）服务启动。
- OpenBot（开放机器人）主服务启动。
- Web UI（网页界面）可访问。
- 日志和状态可观察。
- STOP（停止）只停止该项目。
- 重启后可以重新运行。

当前状态：**NOT STARTED（未开始）**

## M8 — Distribution（分发）

必须证明：

- macOS application bundle（苹果应用包）构建。
- code signing（代码签名）。
- notarization（苹果公证）。
- clean install（全新安装）。
- upgrade install（升级安装）。
- crash recovery（崩溃恢复）。
- 普通用户无需开发工具即可启动 SiftAlpha（筛选阿尔法）。

当前状态：**NOT STARTED（未开始）**
