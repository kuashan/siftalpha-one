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


## R48-D3 v199 — Full-screen approved launch composition

The approved concept is implemented directly in Compose rather than embedded as a rendered image.

Final launch frame:
- background: deep navy/black;
- upper-right: dense blue/cyan code and data lanes curve inward;
- lower-left: dense blue/violet code and data lanes curve inward;
- convergence point: the **existing real SiftAlpha app logo**;
- below logo: `SiftAlpha X`;
- below wordmark: existing Chinese brand tagline;
- bottom: restrained luminous accent line + `INTELLIGENCE IN MOTION`.

Animation sequence:
1. data lanes and code fragments become visible from both sides;
2. fragments travel toward the center on curved S-like trajectories;
3. convergence glow increases;
4. the actual logo asset resolves in place;
5. product name/taglines settle;
6. completed frame holds briefly before entering Home.

Hard rule:
The launch animation may animate data, glow, opacity and scale around the logo, but it must never substitute another logo or redraw the logo geometry.


## R48-D3 v203 — Approved Four-Scene Sequence

The launch experience is now defined by the approved four-scene storyboard and must be implemented as live Compose motion rather than as a static image.

### 01 起始 / FROM THE EDGES
- code/data enter from both sides;
- upper stream originates from the right;
- lower stream originates from the left;
- streams remain broad and separated around the center.

### 02 汇聚 / FLOW TOGETHER
- paths become more strongly curved;
- both sides visually create an S-shaped flow;
- moving highlights and code fragments communicate direction and speed.

### 03 成形 / SHAPE THE FUTURE
- stream fragments detach and assemble into an S;
- the S is made from luminous fragments and code/data energy;
- no substitute final logo is shown at this stage.

### 04 完成 / READY FOR WHAT'S NEXT
- the actual app logo asset resolves;
- final halo, SiftAlpha X wordmark and INTELLIGENCE IN MOTION appear;
- generated/reference artwork is never used as the final logo.

### Layout lock
Every scene keeps:
- top brand header: SiftAlpha X;
- FROM CODE TO POSSIBILITY;
- Intelligence Converges. A Smarter Tomorrow.;
- numbered stage heading;
- bottom localized caption + English microcopy.

### Motion lock
- continuous spatial continuity between scenes;
- no hard-cut stream resets;
- no layout-shifting animations;
- Reduced Motion presents the completed frame without decorative travel;
- total perceived launch duration remains approximately 5 seconds.


## R48-D3 v204 Override — Current authority

The v203 four-scene page chrome is **not** part of the current app presentation.

Current launch authority is:
- restore the v202 composition, layout, copy, logo, background and timing;
- modify only the convergence density to better match the approved visual reference;
- keep the same two curved flow directions and the same S-endpoint targets;
- use 40 curved strands per side, 120 code tokens, and 144 luminous particles per side.

No extra stage headings, captions, or storyboard chrome may be added unless explicitly requested again.


## R48-D3 v205 Override — Current launch motion authority

The v204 page composition remains authoritative. v205 changes only the motion inside that page:

- upper-right stream -> spiral around right/bottom -> lower-left S endpoint;
- lower-left stream -> spiral around left/top -> upper-right S endpoint;
- no direct center crossing;
- incoming code continues into the S body;
- the S becomes visibly composed of code/data fragments;
- that fragment-built S continuously resolves into the actual app logo.

Do not add phase cards, headings, extra captions, or other storyboard chrome.


## R48-D3 v206 Override — Final current opening authority

Current opening is one full-screen sequential animation with no stage labels/chrome.

Visual sequence only:
1. chaotic code/data inflow from lower-left and upper-right;
2. smooth S-shaped convergence;
3. dense code/particle nebula forms an S;
4. unchanged real app logo resolves with SiftAlpha X + INTELLIGENCE IN MOTION.

Do not render:
- stage titles;
- 01/02/03/04 numbers;
- explanatory captions;
- storyboard cards.

The real launcher logo remains the final mark.


## R48-D3 v207 Override — Direct code-to-logo formation

Current opening-motion authority:

- code enters from lower-left and upper-right;
- code does **not** first form a separate oversized S path;
- incoming code directly occupies the final logo geometry:
  rounded-square shell + S ribbon + terminal >_;
- that code-built logo then resolves into the real app logo asset in place;
- no hard cut and no extra storyboard UI.


## R48-D3 v208 Override — Code-built logo must already read as the logo

Current authority:
- converging code must settle directly into a recognisable code-built version of the app logo;
- do not use a generic particle cloud or unrelated intermediate object;
- the code-built state uses the final logo geometry itself: rounded-square shell + thick S ribbon + >_;
- only after that code-built logo is readable may the real launcher logo resolve in place.


## R48-D3 v209 Override — Pure code-S intermediate

The intermediate formation must match the approved reference:
- code converges directly into a dense S made from code characters;
- no rounded-square shell in the code-built stage;
- no >_ terminal in the code-built stage;
- no generic cloud/blob around the S;
- the code-S must remain visually readable before the real launcher logo resolves in place.
