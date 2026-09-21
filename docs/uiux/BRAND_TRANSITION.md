# SiftAlpha Brand Transition（品牌启动过渡）

## Goal（目标）
Brand Transition（品牌启动过渡）只强化一个品牌记忆：**SiftAlpha 的 S**。

不再通过交叉轨迹去形成 X，也不把 X 作为动画高潮。

## Cold Start Sequence（冷启动）
总目标时长：约 850–1100ms。

### Phase A — Trace（描迹）0–220ms
- 深色/浅色品牌背景稳定出现。
- 两段短 ribbon trace（带状轨迹）从 S 的上端和下端开始出现。
- 轨迹遵循最终 S 的真实几何，不做随机粒子和无意义飞线。

### Phase B — Form S（形成 S）180–560ms
- 两段 ribbon（带状路径）自然连接并完成 S 轮廓。
- 中央负空间保持干净，让用户第一眼就是 S。
- 不形成 X，不做交叉爆发。

### Phase C — Resolve（成形）500–760ms
- S Mark（S 标志）完成。
- Blue → Cyan → Violet（蓝 → 青 → 紫）的品牌渐变短暂完成一次流动后稳定。
- 如保留 >_ 作为 secondary motif（次级符号），只允许在这一阶段轻微淡入，且视觉权重明显低于 S。

### Phase D — Name（名称）650–900ms
- “SiftAlpha” Wordmark（品牌字标）短淡入。
- 不显示醒目的 X。
- 不逐字打字，不模拟 Terminal Typing（终端打字）。

### Phase E — Handoff（交接）820–1100ms
- S Mark（S 标志）轻微缩小并自然进入 Home（首页）的品牌位置，或淡出让首页接管。
- 首页内容同步淡入。
- 动画结束后立即回到真实 App State（应用状态）。

## Warm Start（热启动）
- 不重复完整品牌动画。
- 直接进入，或 ≤ 180ms 的 SiftAlpha 淡入。

## Slow Initialization（初始化较慢）
Brand Transition（品牌过渡）不能掩盖真实等待：
- S 成形后保持静态。
- 系统切换为真实 Loading / Activity State（加载 / 活动状态）。
- Timeout（超时）发生时停止等待动画并显示真实错误。

## Reduced Motion（减少动态效果）
开启 Reduced Motion（减少动态效果）时：
- 跳过 S 的路径绘制。
- 直接显示最终 S Mark（S 标志）与 SiftAlpha Wordmark（品牌字标）。
- 使用 150–200ms fade（淡入）或直接进入首页。

## Implementation Boundary（实施边界）
Brand Transition（品牌过渡）只属于 Presentation Layer（表现层），不得修改：
- Runtime initialization（运行时初始化）
- project loading semantics（项目加载语义）
- Developer Mode（开发者模式）
- Shared Core lifecycle（共享核心生命周期）
