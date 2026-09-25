# 跨平台宿主端口冲突自动处理设计

- 日期：2026-09-25
- 状态：待评审
- 适用分支：`feature/cross-platform-core`

## 目标

SiftAlpha 面向不熟悉容器和端口概念的用户。当用户同时运行多个项目时，系统应尽可能自动消除宿主端口冲突，让每个项目独立运行、打开结果并单独停止。用户不应因 `Bind for 0.0.0.0:<port> failed: port is already allocated` 而编辑 `compose.yaml`、排查进程或理解 Docker。

## 约束与安全边界

1. 不修改用户项目源文件，包括 `compose.yaml`、脚本及应用配置。
2. 不自动停止、重启或删除其他 SiftAlpha 项目，也不接管外部程序。
3. 原始端口映射只是“请求端口”；实际端口必须由运行时确认。
4. 可控项目允许自动改映射；无法安全改写的固定端口必须明确阻止。
5. 处理有界：一次预检、一次受控启动、一次结果确认；不得无限重试或全端口扫描。

## 共享 Core 的职责

Core 只保存跨平台的模型、决策与结果，不出现 macOS、Windows、Android、Docker Desktop 或 Colima 分支。

- `RequestedBinding`：项目声明的协议、宿主端口、容器端口与来源。
- `BindingOwnership`：当前项目、其他 SiftAlpha 项目、外部进程或未知。
- `PortRemapCapability`：运行器能否通过临时覆盖层或明确参数改写宿主映射。
- `PortResolutionPlan`：保持、自动改映射、等待用户确认停止自己的项目、或安全阻止。
- `EffectiveBinding`：provider 确认的真实地址、端口和访问 URL。
- `PortResolutionOutcome`：UI 和日志使用的稳定结果码及说明。

决策规则：端口空闲则保持；冲突且可安全改映射则自动分配新端口；其他 SiftAlpha 项目冲突时仍默认改映射，仅在用户主动选择时提供停止自己的项目；外部进程绝不触碰，不能安全改映射则阻止启动；无法证明改写安全时不得伪造“已运行”。

## 平台 Adapter 的职责

Adapter 负责平台事实与执行：读取绑定、判定归属、生成候选端口、写入一次性运行覆盖、启动 provider，并在启动后查询真实端口。

### Compose

对可改映射项目，Adapter 生成“项目 + generation”作用域的临时 override。执行使用原 `compose.yaml` 加 override，原文件不变；临时文件在当前运行生命周期保留审计信息，并在停止/清理时回收。

候选端口由 Adapter 用操作系统安全机制分配或短范围选择；预检不是最终事实。以 `docker compose up` 后的 Docker 端口查询为准。若竞争导致再次冲突，只重新分配并重试一次；第二次失败返回可诊断错误。

### Process

只有运行器提供明确可控的端口输入（如 `PORT` 环境变量或已建模参数）时才自动重映射；不得猜测或修改任意命令、硬编码端口或项目源码。

## 用户体验与隔离

成功保持端口时显示实际 URL；自动改映射时显示“原端口正在使用，已自动分配可用端口 <effectivePort>”；无法安全改映射时说明为保护现有程序未自动停止它。开发者详情显示请求端口、实际端口、模式、归属与 override 生命周期，不把 Docker 原始报错作为唯一提示。

每个项目使用独立 Compose project name、generation 与临时 override。Stop 只影响当前项目；Status、Logs、Open Result 一律使用 `EffectiveBinding`；不保留全局端口预约。

## 验收标准

1. 两个声明相同宿主端口的可改映射 Compose 项目可同时运行，第二个自动取得不同实际端口。
2. 原 `compose.yaml` 内容和时间戳不变。
3. 外部程序占用端口时不停止外部程序；可改映射项目自动恢复。
4. 固定且不可改映射项目被安全阻止，且不显示为运行中。
5. 停止 A 后 B 保持可访问，结果不会回退至 A 的地址。
6. Core 覆盖决策矩阵；各 Adapter 覆盖预检、override、启动竞争、真实绑定确认与清理。
7. macOS、Windows、Android 复用同一 Core 合约，只实现 Adapter 差异。

## 非目标

- 自动改写任意项目源码或永久改写用户 Compose 文件。
- 自动杀掉其他项目或外部程序。
- 在 Core 引入平台专用条件。
- 为尚未建模的 OpenBot 或第三方运行器打补丁。
