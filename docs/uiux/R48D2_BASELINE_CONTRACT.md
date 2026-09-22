# R48-D2 UI / Functional Baseline Contract（R48-D2 界面 / 功能基线契约）

Status: FROZEN / HARD BOUNDARY（冻结 / 硬边界）

This document is the highest-priority boundary for all SiftAlpha X Normal Mode（普通用户模式）UI/UX（用户界面 / 用户体验）work.

## 1. Frozen Baseline Identity（冻结基线身份）

- Baseline branch（基线分支）: `baseline/r48d2`
- Baseline snapshot commit（基线快照提交）: `729659c345e19d249de44fdb6492c47d811701cd`
- Functional source（功能源码）: `ab0b5fcad8402dcddb1f0986bcd7dbb2400017fb`
- Version（版本）: `0.8.0-alpha43-r48d2`
- versionCode（版本号）: `192`
- W0 Cloud Build（云端构建）: #599
- Artifact（构建产物）: `siftalpha-w0-599` / ID `10652097606`
- APK SHA-256（安装包校验）: `3acf37223d7a5ec7708215d851bf513ec403c2942c46f8cd026958b4aca12a4f`
- Signer SHA-256（签名校验）: `3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`

The baseline branch and snapshot are read-only references.

## 2. Absolute Source Rule（绝对源码规则）

Never modify the baseline itself.

Forbidden on `baseline/r48d2`:
- commit（提交）
- merge（合并）
- rebase（变基）
- reset（重置）
- force push（强制推送）
- delete（删除）
- “cleanup” or “modernization”（清理或现代化）
- UI refactor（界面重构）

All future UI work happens on a separate working branch and is compared back to R48-D2.

R48-D2 is the reference, not a development target.

## 3. Functional Parity Rule（功能等价规则）

UI redesign（界面重设计）may change:
- color（颜色）
- typography（字体）
- spacing（间距）
- card style（卡片样式）
- iconography（图标）
- visual hierarchy（视觉层级）
- animation（动画）
- component composition（组件组合）
- information wording（通俗文案）

UI redesign must NOT remove, weaken, silently hide, or change the semantics of any R48-D2 user capability.

**R48-D2 decides what the product can do. The design system decides how those capabilities are presented.**

## 4. One Primary Action Means Visual Priority Only（单一主操作只代表视觉优先级）

`One Primary Action（单一主操作）` does **not** mean “only one button exists”.

It means:
- one action may be visually dominant;
- all R48-D2 secondary actions remain available under the same state rules.

The R48-D2 project workspace contract includes:

Primary state-driven action（状态驱动主操作）:
- Prepare Project（准备项目）
- Configure（完成配置）
- Run（运行）
- Stop（停止）

Persistent secondary project actions（持续保留的项目次操作）:
- **Open（打开）**
- **Refresh（刷新）**

Open（打开）and Refresh（刷新）must not be deleted merely because another action is Primary（主操作）.
They remain visible project-page secondary actions and keep the R48-D2 enabled / disabled semantics.

## 5. R48-D2 Normal Mode Capability Inventory（普通模式能力清单）

### Home（首页）
Preserve the R48-D2 capabilities:
- Import Project（导入项目）
  - local Python file（本地 Python 文件）
  - ZIP project（ZIP 项目）
  - GitHub import（GitHub 导入）
- New Project（新建项目）
- Project Location（项目位置）
- Project List（项目列表）
- project search / filtering（项目搜索 / 筛选）
- project open（打开项目）
- More（更多）
  - Refresh Projects（刷新项目）
  - New Project（新建项目）
  - Runtime Storage（运行空间）
  - Settings（设置）

Normal Mode（普通用户模式）does not gain a Bottom Navigation（底部导航）unless the user explicitly changes this decision.

### Project Workspace（项目页）
Preserve:
- Project Status（项目状态）
- Runtime Location selection（运行位置选择）
  - Internal（内部）
  - External / Termux（外部 / Termux）
- Prepare（准备）
- Configure（配置）
- Run（运行）
- Stop（停止）
- **Open（打开）**
- **Refresh（刷新）**
- External Provider recovery（外部执行环境恢复）
  - permission（权限）
  - Open Termux（打开 Termux）
  - Re-check（重新检测）
- Background Reliability Guidance（后台可靠性提醒）
- Runtime Activity Indicator（运行活动指示条）
- existing timeout / lifecycle / enabled-state behavior（既有超时 / 生命周期 / 按钮可用状态行为）

### Configuration（配置）
The UI may become a formal page, but must consume the same configuration facts and protected value storage used by R48-D2. It must not invent a second configuration model.

### Result（结果）
The UI may make Open Result（打开结果）more prominent, but must not use that as a reason to remove Refresh（刷新）, rerun capability where already supported, or existing result / Web presentation behavior.

## 6. Shared Core Boundary（共享核心边界）

UI-only work must not change:
- Runtime semantics（运行时语义）
- Environment semantics（环境语义）
- Prepare / Run / Stop dispatch（准备 / 运行 / 停止调度）
- timeout facts（超时事实）
- ownership / project isolation（归属 / 项目隔离）
- Runtime selection behavior（运行环境选择行为）
- Web Discovery / result availability facts（网页发现 / 结果可用事实）
- External Provider readiness（外部执行环境就绪事实）
- Background Reliability behavior（后台可靠性行为）

Presentation Layer（表现层）consumes these facts; it does not redefine them.

## 7. Developer Mode Boundary（开发者模式边界）

Developer Mode（开发者模式）remains frozen and is not redesigned as part of Normal Mode UI work.

No Normal Mode design change may:
- delete Developer Mode UI;
- move Developer Mode controls;
- rewrite Developer Mode workflow;
- use Developer Mode as a hidden implementation shortcut.

## 8. Mandatory Baseline Parity Gate（强制基线等价门禁）

Before any future UI APK（安装包）is presented for real-device testing, compare it with R48-D2 and verify:

1. all R48-D2 Normal Mode capabilities are still reachable;
2. Prepare / Configure / Run / Stop still map to the same Shared Core actions;
3. Open + Refresh are still present as project-page secondary actions;
4. runtime-location selection is preserved;
5. External Provider recovery actions are preserved;
6. project import / location / search / list / More capabilities are preserved;
7. no R48-D2 enabled / disabled rule was weakened by visual refactoring;
8. no Runtime / Environment / lifecycle behavior was modified for design convenience;
9. Developer Mode remains unchanged;
10. the baseline branch still points to `729659c345e19d249de44fdb6492c47d811701cd`.

If any item fails, the UI change is a regression and must not be treated as an accepted design delivery.

## 9. Design Authority Order（设计权威顺序）

For future Normal Mode design work, resolve conflicts in this order:

1. **R48-D2 functional baseline contract（本文件）**
2. explicit new user decisions（用户之后明确的新决定）
3. approved SiftAlpha X UI/UX design direction（已批准设计方向）
4. UI/UX Pro Max Skill（UI/UX 专业设计技能）
5. implementation convenience（实现便利）

A visual design rule can never override R48-D2 functionality unless the user explicitly authorizes that functional change.
