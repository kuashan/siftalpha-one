# R48-D3 — SiftAlpha X Normal Mode Design Fidelity Implementation

Status: CORRECTED / CLOUD GATE REQUIRED

## Correction scope
This correction follows the approved UI/UX Pro Max Skill（UI/UX 专业设计技能）direction and the user-approved Normal Mode（普通用户模式）scheme.

Corrected:
- software name restored to **SiftAlpha X**;
- original repository Logo（标志）restored for Launcher（启动图标）, Brand Transition（品牌过渡）and Home（首页）brand position;
- removed Bottom Navigation（底部导航）from Normal Mode;
- Home（首页）keeps only More（更多）at the top right;
- project names and key project titles use explicit dark-mode foreground colors instead of inheriting an incorrect black text color;
- Project Location（项目位置）is always visible on Home;
- ProjectStatusCard（项目状态卡）makes current state visually dominant;
- Prepare（准备）uses the approved six truthful phases:
  检测项目 → 检查兼容性 → 准备运行环境 → 安装依赖 → 验证环境 → 准备完成;
- Configuration（配置）, Run（运行）, Result（结果）remain Normal Mode product pages;
- no fake percentage is introduced.

## Frozen boundaries
Developer Mode（开发者模式）, Runtime（运行时）, Environment（环境）, Shared Core（共享核心）, lifecycle（生命周期）and Prepare / Run / Stop semantics（准备 / 运行 / 停止语义）remain unchanged.

## Version
- versionName: 0.8.0-alpha43-r48d3
- versionCode: 195
- rollback baseline: baseline/r48d2 @ 729659c345e19d249de44fdb6492c47d811701cd

## Verification gate
W0 Cloud Build（W0 云端构建）must pass repository validators, unit tests, assembleDebug（调试构建）, stable test signing（稳定测试签名）and APK evidence collection.


## v196 — R48-D2 parity + secondary-path repair

R48-D3 is now explicitly constrained by `R48D2_BASELINE_CONTRACT.md`.

Repaired capability regressions:
- Home search/filter restored;
- project Details/Delete restored;
- Open/Refresh retained throughout the project workflow;
- Runtime Location restored across project states;
- External Provider recovery restored to R48-D2 action parity;
- Configuration no longer merges Save + Run.

Secondary Normal Mode routes now receive the D3 visual system:
Import, Project Location, More, New Project, GitHub Import, Project Details, Delete confirmation, Runtime Storage, and the Normal Settings entry.

VersionCode for this correction: `196`.
