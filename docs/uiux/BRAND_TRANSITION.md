# SiftAlpha X Brand Transition（品牌启动过渡）

## Goal
用户启动 App 时不是“点击图标 → 突然出现界面”，而是通过一次短暂、含义明确的品牌过渡，把 Logo（标志）的“筛选 / 收敛”概念带进首页。

## Cold Start Sequence（冷启动）
总目标时长：约 900–1150ms。

### Phase A — Input（输入）0–180ms
- 深色/浅色品牌背景稳定出现
- 3–4 条短 signal lines（信号线）从外围轻微进入
- 不做粒子爆炸，不做随机噪声

### Phase B — Sift（筛选）180–520ms
- signal lines 向中心收敛
- 中央节点形成
- 交叉路径自然解析成 Sift Mark 的 X 结构

### Phase C — Resolve（成形）480–760ms
- Logo 完整稳定
- 输出侧最后一段用 Sift Teal（筛选青）点亮
- 只发生一次，不持续 glow（发光）

### Phase D — Name（名称）620–900ms
- “SiftAlpha X” 以短淡入 + 轻微 tracking settle（字距收拢）出现
- 不逐字打字，不做 terminal typing（终端打字）效果

### Phase E — Handoff（交接）820–1150ms
- Logo 轻微缩小/上移
- Home（首页）内容淡入
- transition 不应阻塞可交互首页超过必要时间

## Warm Start（热启动）
不要每次回到 App 都完整播放。
Warm start：
- 直接进入
或
- ≤ 200ms 的非常短品牌淡入

## Slow Initialization（初始化较慢）
Brand animation（品牌动画）不能无限循环掩盖真实等待。
Logo 成形后保持静态，并切换为真实 Loading / Activity state（加载 / 活动状态）。
Runtime / startup timeout（运行时 / 启动超时）仍由真实系统规则决定。

## Reduced Motion（减少动态效果）
系统开启 Reduce Motion（减少动态效果）时：
- 跳过 signal convergence（信号收敛）
- 直接显示最终 Logo
- 150–200ms fade 或直接进入首页

## Implementation Boundary
Brand Transition 属于 Presentation Layer（表现层）。
不得改变：
- Runtime initialization
- project loading semantics
- Developer Mode
- Shared Core lifecycle
