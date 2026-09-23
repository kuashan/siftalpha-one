# SiftAlpha macOS Current State（macOS 当前状态）

> 本文件是 macOS（苹果桌面系统）开发的当前状态事实源。
> 每一轮 macOS（苹果桌面系统）开发结束后都必须更新本文件；历史过程写入 `MACOS_DEV_LOG.md`。

## 1. 当前阶段

状态：**M0 — Development Governance（开发治理）已建立，平台代码尚未开始。**

当前工作分支：
- `feature/cross-platform-core`

当前跨平台 Core（核心）版本：
- `0.8.0-alpha43-r48d11-core2`
- Android（安卓） versionCode = `224`

当前 Core（核心）能力隔离规则：
- `docs/PLATFORM_CAPABILITY_CONTRACT.md`
- Core（核心）可以定义能力，但不得强迫所有平台实现同一能力。
- macOS（苹果）专属能力必须留在 macOS Platform Adapter（苹果平台适配层）。
- Android（安卓）现有行为是跨平台抽取期间的重要回归基线。

## 2. macOS 产品目标

macOS（苹果桌面系统）版不是新的独立产品，而是：

```
SiftAlpha Core（跨平台核心）
        +
macOS Platform Adapter（苹果平台适配层）
        +
macOS UI（苹果平台界面）
        +
Host Runtime Provider（主机运行提供者）
```

产品仍保留两种使用方式：

- Normal Mode（普通用户模式）
- Developer Mode（开发者模式）

两种模式必须共享同一套 Core（核心）和 Runtime（运行时）事实，不允许重新复制两套运行逻辑。

## 3. macOS 第一阶段执行原则

macOS（苹果桌面系统）优先使用 Host Runtime Provider（主机运行提供者），直接调用本机已有能力，例如：

- Python（Python 运行时）
- Node.js（Node 运行时）
- Bun（Bun 运行时）
- Git（版本控制）
- Docker / Podman（容器）
- Shell（命令环境）

Android（安卓）现有 Embedded CPython（内嵌 CPython）、Internal Alpine（内部 Alpine）、Termux（外部终端）不是 macOS（苹果）首要运行路径。

这些 Android（安卓）能力不得为了 macOS（苹果）而被删除或重写。

## 4. 当前已具备的可复用基础

当前已经具备并计划复用的能力包括：

- RuntimeKind（运行时类型）
- Project Runtime Profile（项目运行时画像）
- Environment Detection（环境检测）方向
- Environment Plan（环境计划）方向
- Session（会话）
- Generation（代际）
- Runtime Lifecycle（运行生命周期）
- project-scoped operation（项目级操作）
- START / STOP / STATUS / LOGS（启动 / 停止 / 状态 / 日志）
- Web Discovery / Endpoint Probe（网页发现 / 端点探测）策略
- Normal Mode / Developer Mode（普通模式 / 开发者模式）共享核心原则
- Platform Capability Contract（平台能力契约）

其中仍有大量实现位于 Android（安卓）模块，后续需逐步抽取，不得一次性大迁移。

## 5. 当前尚未实现

以下内容尚未开始，因此不得宣称 macOS（苹果）版已经可运行：

- macOS Platform Adapter（苹果平台适配层）
- macOS Host Process（苹果主机进程执行）
- macOS filesystem bridge（苹果文件系统桥接）
- macOS secure storage（苹果安全存储）
- macOS project import（苹果项目导入）
- macOS Normal Mode UI（苹果普通模式界面）
- macOS Developer Mode UI（苹果开发者模式界面）
- macOS Runtime Provider selection（苹果运行提供者选择）
- Docker / Bun / Python host execution（容器 / Bun / Python 主机执行）
- macOS packaging / signing / notarization（打包 / 签名 / 公证）
- OpenBot（开放机器人）真实运行验收

## 6. 开发顺序

### M0 — Governance（开发治理）
已建立：
- 独立 Current State（当前状态）
- 独立 Dev Log（开发日志）
- 独立 Test Matrix（测试矩阵）
- Capability Isolation（能力隔离）规则

### M1 — Core Boundary（核心边界）
目标：
- 继续识别并抽取真正平台无关的 Core（核心）
- 不改变 Android（安卓）现有行为
- 建立明确的 Platform Interface（平台接口）

### M2 — macOS Host Skeleton（苹果主机骨架）
目标：
- 建立 macOS（苹果）平台模块
- 验证 App（应用）可以启动
- 验证 Core（核心）可以被 macOS（苹果）端加载
- 不运行复杂项目

### M3 — Host Runtime Provider（主机运行提供者）
目标：
- 发现本机 Python / Node.js / Bun / Git
- 启动、观察、停止本机进程
- 建立 project-scoped STOP（项目级停止）

### M4 — Project Workflow（项目工作流）
目标：
- 导入项目
- Environment Detection（环境检测）
- Environment Plan（环境计划）
- Prepare（准备）
- Run（运行）
- Status / Logs（状态 / 日志）

### M5 — Product UI Parity（产品界面对齐）
目标：
- Normal Mode（普通用户模式）
- Developer Mode（开发者模式）
- 两种模式共享同一套 Core（核心）事实

### M6 — Container / Multi-service（容器 / 多服务）
目标：
- Docker / Podman（容器）
- Compose（多服务编排）
- Bun workspace（Bun 工作区）
- 多服务生命周期和端口映射

### M7 — OpenBot Acceptance（OpenBot 验收）
目标：
- 不修改 OpenBot（开放机器人）源码
- 识别项目需求
- 准备依赖
- 启动完整服务
- Web（网页）结果可访问
- STOP（停止）只停止该项目
- 重启可恢复

### M8 — Distribution（分发）
目标：
- macOS packaging（苹果应用打包）
- signing（签名）
- notarization（苹果公证）
- upgrade（升级）
- crash/recovery（崩溃 / 恢复）

## 7. 强制约束

1. 不为了 macOS（苹果）修改 Android（安卓）特有实现。
2. 不为了 macOS（苹果）把平台专属能力强行加入 Core（核心）。
3. Core（核心）修改必须通过 Core tests（核心测试）和 Android regression（安卓回归）。
4. macOS（苹果）专属实现必须位于 Platform Adapter（平台适配层）。
5. 一个能力在 macOS（苹果）可用，不代表 Android（安卓）或 Windows（微软）必须实现。
6. 不复制一套 macOS 专属 Core（核心）。
7. Normal Mode（普通模式）和 Developer Mode（开发者模式）不得各自维护运行状态机。
8. OpenBot（开放机器人）只是最终验收项目之一，不允许出现 OpenBot-specific patch（OpenBot 专属补丁）。
9. 每轮开始先读取本文件、`MACOS_DEV_LOG.md`、`MACOS_TEST_MATRIX.md` 和 `PLATFORM_CAPABILITY_CONTRACT.md`。
10. 每轮完成必须更新状态、测试证据和下一步。

## 8. 下一步唯一方向

下一轮不是直接制作完整 macOS UI（苹果界面）。

下一步应继续完成 **M1 — Core Boundary（核心边界）**：

- 审查现有 Runtime Lifecycle（运行生命周期）、Environment Plan（环境计划）、Project Operation（项目操作）中哪些代码可以安全进入 `:core`。
- 对仍依赖 Android Context / SharedPreferences / Service / SAF（安卓上下文 / 存储 / 服务 / 文件框架）的代码建立明确 Platform Interface（平台接口）。
- 每一次抽取都要求 Android（安卓）回归通过。
