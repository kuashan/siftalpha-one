# SiftAlpha X Normal Mode（普通用户模式）UI/UX（用户界面 / 用户体验）Redesign（重设计）
Status: APPROVED DIRECTION / DESIGN FIDELITY CORRECTION

## Brand Rule（品牌规则）
- 软件正式名称统一为 **SiftAlpha X**。
- App Icon（应用图标）、Brand Transition（品牌过渡）和 Home（首页）品牌位统一使用项目原始 Logo（标志）。
- 不再用新绘制的近似 Logo（标志）替代原始 Logo。
- Normal Mode（普通用户模式）首页只保留右上角 More（更多），**不使用 Bottom Navigation（底部导航）**。
- Developer Mode（开发者模式）不在本轮重设计范围内。

## North Star
让一个完全不懂 Runtime（运行时）、Python 环境和 Termux 的普通用户，也能自然完成：

**导入项目 → 准备 → 配置 → 运行 → 查看结果**

## Information Architecture
```
Home
├── Import Project
│   ├── Python File
│   ├── ZIP Project
│   └── GitHub Link
├── New Project
├── Project Location
├── Project List
│   └── Project Workspace
│       ├── Status
│       ├── Prepare / Configure / Run / Stop / Open Result
│       ├── Configuration
│       ├── Run Location
│       └── Result
└── More
    ├── Runtime Storage
    ├── Project Location
    ├── Background & Battery
    └── Settings
```

## Interaction Model
### One Primary Action（单一主操作）
一个页面同时只允许一个强视觉 Primary CTA（主行动按钮）。

### State-driven UI（状态驱动界面）
用户看到的是“现在发生什么”和“下一步是什么”，而不是操作命令列表。

### Progressive Disclosure（渐进披露）
技术信息只有在真正必要时才出现；Normal Mode 不承担 Developer Mode 的诊断职责。

## Prepare UX
默认不打开独立日志页。
ProjectStatusCard 在准备期间展开为 phase-aware（阶段感知）状态：
- 检测项目
- 检查兼容性
- 准备运行环境
- 安装依赖
- 验证环境
- 准备完成

循环活动条只表达“仍在处理”，不表达百分比。
真实 Timeout（超时）后必须停止动画并进入恢复状态。

## Configuration UX
Configuration（配置）是正式页面，不再以技术 AlertDialog（技术弹窗）作为最终产品形态。
区分 REQUIRED / OPTIONAL（必填 / 可选）。
Secret（密钥）按安全字段显示。

## Run UX
Run（运行）点击后立即：
- 状态变“正在启动”
- 显示循环活动条
- 禁止重复启动

真正 RUNNING（运行中）后活动条继续柔和循环。
它只表达 Runtime（运行时）仍活跃，不绑定端口。

## Result UX
所有结果统一收敛到“打开结果”。
Web Discovery / Endpoint Probe / Rich Result type 等内部事实留在 Shared Core / Developer Mode。

## Error UX
先回答：
1. 发生了什么？
2. 对项目有什么影响？
3. 用户现在能做什么？

技术诊断默认隐藏。

## Engineering Boundary
本设计不允许修改：
- Developer Mode
- Runtime semantics
- Environment semantics
- Shared Prepare / Run / Stop behavior
- ownership / lifecycle / timeout facts

Implementation phase（实施阶段）只能替换/重构 Normal Mode Presentation Layer（表现层）并消费现有 Shared Core。
