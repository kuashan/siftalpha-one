# SiftAlpha X Brand Transition（品牌启动过渡）

## Goal（目标）
Brand Transition（品牌启动过渡）强化 **SiftAlpha X + 原始 Logo（标志）**，不重新绘制 Logo（标志）。

## Cold Start（冷启动）
目标总时长约 850–1100ms。

1. Background（背景）：深蓝黑背景与克制的蓝 / 青 / 紫流动光效。
2. Original Logo（原始标志）：原始 Logo 由低透明度平滑显现，可伴随极轻微 scale（缩放）变化。
3. Wordmark（品牌字标）：显示 “SiftAlpha X”。
4. Brand copy（品牌文案）：
   - 让复杂的技术，变成简单的可能
   - 智能筛选 · 简化执行 · 看见结果
5. Handoff（交接）：短淡出进入 Home（首页）。

## Prohibited（禁止）
- 不重新画 Ribbon S（带状 S）。
- 不通过动画改变原始 Logo（标志）几何。
- 不形成额外的 X 图形。
- 不用 Terminal Typing（终端打字）模拟品牌名称。
- 不用 Brand Transition（品牌过渡）掩盖真实 Runtime（运行时）等待。

## Reduced Motion（减少动态效果）
开启 Reduced Motion（减少动态效果）时：
- 直接显示原始 Logo（标志）与 “SiftAlpha X”。
- 仅允许短 fade（淡入）或直接进入首页。

## Boundary（边界）
只属于 Presentation Layer（表现层），不得修改 Developer Mode（开发者模式）、Runtime（运行时）、Environment（环境）或 Shared Core（共享核心）事实。


## R48-D3 v197 Real-device Timing + Motion

- Launch brand transition dwell: approximately 3.9 seconds, within the approved 3-5 second range.
- The original R48-D2 logo asset remains unchanged.
- Motion may animate only opacity, restrained scale, and surrounding cyan/blue glow; logo geometry is never redrawn.
- The logo uses a slow breathing rhythm rather than rotation.
- The underlying Home screen is covered until the brand transition exits.
- When Android system animations are disabled, the logo remains static for the dwell period and exits without decorative motion.


## R48-D3 v198 Code / Data Convergence

The launch brand motion now follows this choreography while preserving the exact original logo asset:

1. Code/data tokens appear in two sparse streams.
2. The upper stream begins on the right and follows a curved inward route.
3. The lower stream begins on the left and follows a curved inward route.
4. Together the streams suggest the directional rhythm of an S without redrawing the logo itself.
5. As the streams converge, the code/data fades and the real R48-D2 logo resolves in the center.
6. Wordmark and tagline appear only after the logo has begun resolving.
7. Total perceived brand dwell remains in the approved ~3-5 second range.
8. Reduced-motion mode removes moving convergence and presents the completed original logo statically.
