# SiftAlpha X — Normal Mode Design System
Version: 1.0 / Design Phase
Scope: Normal Mode（普通用户模式）only
Repository skill: .agents/skills/ui-ux-pro-max/

## 1. Product Design Thesis
SiftAlpha X 的普通用户体验围绕：

**Project-first + State-driven + One Primary Action**
（项目优先 + 状态驱动 + 单一主操作）

用户永远优先回答四个问题：
1. 我的项目在哪里？
2. 项目现在是什么状态？
3. 下一步应该做什么？
4. 结果在哪里？

如果一个普通用户页面必须先解释 Runtime（运行时）、Endpoint Probe（端点探测）、PID（进程编号）、PRoot、RootFS 等技术名词，优先视为 UX（用户体验）设计问题。

## 2. Skill-derived Direction
依据仓库内 UI/UX Pro Max Skill（UI/UX 专业设计技能）的产品/风格/移动端规则，本系统采用：

- Product base（产品基底）: **Productivity Tool（生产力工具）**
  - Flat Design（扁平设计）
  - Micro-interactions（微交互）
  - Minimalism & Swiss Style（极简主义与瑞士风格）
- Technical character（技术气质）: 借鉴 Developer Tool / IDE（开发者工具 / IDE）的精密、快速、状态清晰，但不把“控制台感”带入 Normal Mode。
- Mobile character（移动端气质）: 借鉴 Modern Dark Cinema（现代深色电影感）的层级、深色表面和克制光感，但禁止重度 Glassmorphism（玻璃拟态）、持续装饰动画和赛博朋克霓虹。
- Accessibility（无障碍）:
  - Android touch target（触控目标）≥ 48dp
  - 文本对比度目标 ≥ 4.5:1
  - 状态不能只靠颜色
  - 支持 Reduced Motion（减少动态效果）
  - 动态状态需要可被无障碍服务感知
- Compose（Jetpack Compose）:
  - UI state（界面状态）单一事实源
  - Composable（可组合函数）默认 stateless（无状态）
  - 动画仅属于 Presentation Layer（表现层）
  - 不把动画状态写入 Runtime / Shared Core（运行时 / 共享核心）

## 3. Visual Identity
正式风格名称：

**Calm Precision Tech（克制精密科技）**

品牌关键词：
- Precise（精密）
- Calm（稳定）
- Computational（计算感）
- Clear（清晰）
- Capable（可靠）
- Approachable（容易接近）

明确不采用：
- Cyberpunk（赛博朋克）
- Hacker terminal aesthetic（黑客终端风）
- AI purple/pink cliché gradient（AI 紫粉渐变套路）
- Heavy neon glow（重度霓虹发光）
- Decorative data grids（纯装饰数据网格）
- Excessive glass cards（大量玻璃卡片）

## 4. Color System
### Light
- Background: #F7F8FC
- Surface: #FFFFFF
- Surface Subtle: #EFF2F8
- Foreground: #111827
- Foreground Muted: #475569
- Primary / Sift Blue: #4F46E5
- On Primary: #FFFFFF
- Secondary / Signal Blue: #2563EB
- Result Accent / Sift Teal: #0F766E
- Success: #167A4B
- Warning: #B35C00
- Error: #B53A49
- Border: #DCE2EC

### Dark
- Background: #0B0D12
- Surface: #121722
- Surface Elevated: #16202C
- Foreground: #F4F6FA
- Foreground Muted: #C7CED9
- Primary: #7C8DFF
- Result Accent: #35C6AE
- Success: #55D492
- Warning: #F2B04B
- Error: #FF7A88
- Border: rgba(255,255,255,0.10)

Rules:
- Follow system theme（跟随系统主题）为默认。
- 不默认强制 Dark Mode（深色模式）。
- Result / Success（结果 / 成功）用 teal/green + icon + text，不单靠颜色。
- Error（错误）使用 icon + plain-language title（通俗标题）+ recovery action（恢复动作）。

## 5. Typography
Normal Mode 优先 Android system sans（安卓系统无衬线字体），保证中文 / 英文 / 日文 / 韩文一致性与可读性。

Recommended scale:
- Display: 30sp / 38sp / 600
- Headline: 24sp / 32sp / 600
- Title Large: 20sp / 28sp / 600
- Title: 17sp / 24sp / 600
- Body: 16sp / 24sp / 400
- Body Small: 14sp / 20sp / 400
- Label: 13sp / 18sp / 500

Logo wordmark（品牌字标）可以独立采用更几何的字形气质，但 App UI（应用界面）不依赖特殊字体包。

## 6. Shape & Elevation
- Hero / Status Card（主状态卡）: 20dp radius
- Standard Card（标准卡片）: 16dp
- Field / Secondary Button（输入框 / 次按钮）: 12dp
- Chip（标签）: pill / 999dp
- Shadows（阴影）: 极少；优先 tonal surface（色调表面）+ border（边界）区分层级
- Dark mode（深色）禁止大面积 glow（发光）

## 7. Spacing
4 / 8 / 12 / 16 / 24 / 32 dp

Screen horizontal padding（页面横向边距）:
- Phone: 20dp
- Compact fallback: 16dp

Major section gap（主区块间距）: 24dp
Card internal gap（卡片内部间距）: 12–16dp

## 8. Motion
原则：**Motion conveys state, not decoration（动画表达状态，不做装饰）**。

- Press feedback（按压反馈）: 120–180ms
- Card/state change（卡片/状态变化）: 180–260ms
- Screen enter/exit（页面进入/退出）: 200–300ms
- Brand Transition（品牌过渡）: cold start（冷启动）约 900–1150ms
- Indeterminate activity（不确定活动动画）只用于真实 waiting/running（等待/运行）状态
- Reduced Motion（减少动态效果）下：
  - 禁用收敛轨迹动画
  - 直接显示最终 Logo（标志）
  - 页面只做短淡入或直接进入

## 9. Icon System
采用单一 Outline Icon（线性图标）体系，视觉语义参考 Skill（技能）的 curated icon（精选图标）目录。

Core semantics:
- Import: Upload / UploadSimple
- Project: Folder / FolderOpen
- Run: Play
- Stop: Stop-square equivalent（停止方块）或明确 Stop icon
- Success: CheckCircle
- Error: WarningCircle
- More: MoreVert
- Settings: Gear
- Storage: Database / HardDrive
- GitHub: GithubLogo
- Result: Chart / Browser / Sparkle-like result symbol（结果符号）
- Filter / brand motif: Funnel

禁止 emoji（表情符号）作为正式功能图标。

## 10. Component System
### SiftTopBar
- Leading: compact SiftAlpha X mark（品牌标志）or back
- Title: page title
- Trailing: More（更多）only on Normal Home

### ProjectCard
只显示：
- Project Name（项目名称）
- Status Label（状态）
- 一行 human summary（通俗摘要）
- optional last activity（可选最近活动）
不显示 Runtime internals（运行时内部信息）。

### ProjectStatusCard
整个 App 最重要的 component（组件）。
包含：
- status icon
- large status title
- one-line explanation
- optional Indeterminate Activity Indicator（不确定活动指示条）
- optional phase summary（阶段摘要）
- one Primary Action（主操作）

### PrimaryAction
同一页面最多一个 filled primary（实心主按钮）。
状态映射：
- 未准备 → 准备项目
- 缺配置 → 完成配置
- READY → 运行
- RUNNING → 停止
- Result ready → 打开结果

Secondary actions（次操作）使用 text / tonal / overflow，不与主按钮抢层级。

### PhaseStepper
Prepare（准备）六阶段：
检测项目 → 检查兼容性 → 准备运行环境 → 安装依赖 → 验证环境 → 准备完成

只显示真实阶段，不显示虚假百分比。

### ConfigurationField
- visible label（可见标签）
- REQUIRED / OPTIONAL chip（必填 / 可选标签）
- secret field（密钥字段）支持隐藏/显示
- inline validation（就地校验）
- 不使用 placeholder-only label（仅占位符标签）

### RecoveryCard
Error（错误）页面/状态：
- 发生了什么
- 对用户有什么影响
- 下一步可以做什么
- one recovery CTA（一个恢复动作）
技术诊断默认折叠或转 Developer Mode（开发者模式）。

### ResultEntry
只对普通用户表达：
**打开结果**
不暴露 Web / Rich / Local Result Web 分类。

## 11. Architecture Boundary
Developer Mode（开发者模式）继续冻结。

Normal Mode 的设计层只能：
- consume shared state（消费共享状态）
- invoke existing shared actions（调用现有共享动作）

不得：
- 修改 Runtime（运行时）语义
- 修改 Environment（环境）逻辑
- 建立第二套 Prepare / Run / Stop（准备 / 运行 / 停止）
- 模拟 Developer Mode（开发者模式）按钮
- 为设计方便改变 Shared Core（共享核心）事实

## 12. Pre-delivery Gate
任何 Normal Mode UI（普通用户界面）交付前必须检查：
- safe-area / system bars（安全区 / 系统栏）
- 48dp touch targets（触控目标）
- light/dark contrast（浅色/深色对比度）
- reduced motion（减少动态效果）
- error recovery（错误恢复）
- loading feedback（加载反馈）
- no technical jargon（无不必要技术术语）
- one clear primary action（一个明确主操作）
