# Project Workspace（项目页）— Page Override

## Hierarchy
1. Project name + back
2. ProjectStatusCard（项目状态卡）
3. Primary Action（主操作）
4. Result preview / next useful info（结果预览 / 下一条有用信息）
5. Secondary settings（次要设置）

## ProjectStatusCard states
### Not prepared（未准备）
标题：项目还没准备好
说明：SiftAlpha X 会检测环境并安装项目需要的依赖。
Primary：准备项目

### Preparing（准备中）
标题：正在准备项目
说明：SiftAlpha X 正在准备运行环境和依赖。
Indeterminate bar（循环活动条）
显示当前真实 phase（阶段）。
Primary：停止

### Configuration required（需要配置）
标题：还需要完成配置
说明：还有 N 项必要配置没有填写。
Primary：完成配置

### Ready（已准备）
标题：可以运行
说明：环境和必要配置已经准备完成。
Primary：运行

### Starting（正在启动）
标题：正在启动
循环活动条
Primary：停止

### Running（运行中）
标题：运行中
循环活动条（柔和）
可显示 elapsed time（已运行时间），但不显示虚假完成百分比。
Primary：停止

### Result ready（结果可用）
标题：结果已准备好
Primary：打开结果
Secondary：再次运行（tonal/text）

### Failed（失败）
标题按 plain language（通俗语言）解释。
Primary：最合理恢复动作，例如重新尝试 / 完成配置。
技术细节不作为首屏内容。

## Secondary information
“运行位置”作为折叠/设置项：
- SiftAlpha X 内部运行
- Termux 外部运行

不要把 Internal / External Runtime（内部 / 外部运行时）作为主状态。


## R48-D2 Persistent Secondary Actions（R48-D2 持续次操作）

One Primary Action（单一主操作）只定义视觉优先级。

Project Workspace（项目页）必须持续保留：
- Open（打开）
- Refresh（刷新）

它们按照 R48-D2 的真实 enabled / disabled（可用 / 不可用）规则显示。

Runtime Location（运行位置）也必须保持可见；不可切换时显示锁定状态，而不是从页面删除。

External Provider（外部执行环境）恢复必须保持 R48-D2 行为：
- permission（权限）
- Open Termux（打开 Termux）
- BRIDGE_UNRESPONSIVE（桥无响应）时同时保留 Open Termux + Re-check（重新检测）


## Running Motion Override（运行中动效覆盖）

Real-device rule:
- STARTING（启动中）may use directional rotation because it communicates transition.
- RUNNING（运行中）must not look like an endless loading spinner.
- RUNNING uses restrained breathing motion: blue/cyan halo opacity + very small non-layout scale modulation.
- If `Open（打开）` is already enabled, the result phase must immediately reflect availability; do not continue to show “waiting for result”.
- Running Stop（停止运行）remains a high-priority action but uses the established blue system tone; destructive red is reserved for irreversible/destructive actions, not ordinary runtime stop.
- Reduced-motion users receive the same state information without continuous animation.
