# R48-D2 → R48-D3 Normal Mode Parity Audit（普通用户模式等价审计）

Status: REPAIRED / CLOUD GATE REQUIRED

## Frozen reference

R48-D2 is the immutable capability baseline:
- branch: `baseline/r48d2`
- snapshot: `729659c345e19d249de44fdb6492c47d811701cd`
- functional source: `ab0b5fcad8402dcddb1f0986bcd7dbb2400017fb`
- version: `0.8.0-alpha43-r48d2` / versionCode `192`

The baseline branch itself is not modified.

## Differences found in the previous R48-D3 UI

The previous R48-D3 presentation changed more than visual appearance:

1. Home search / filter（首页搜索 / 筛选）was no longer exposed.
2. Project card Details / Delete（详情 / 删除）actions were no longer exposed.
3. Project Open / Refresh（打开 / 刷新）were not visible in every project state.
4. Runtime Location（运行位置）was only visible in part of the workflow.
5. External Provider recovery（外部执行环境恢复）lost R48-D2 action parity in some states; BRIDGE_UNRESPONSIVE did not keep both Open Termux + Re-check.
6. Configuration（配置）combined Save + Run（保存 + 运行）, which changed the R48-D2 two-step behavior.
7. Home secondary routes（首页二级路径）still used legacy generic dialogs:
   - Import
   - Project Location
   - More
   - New Project
   - GitHub Import
   - Project Details
   - Delete confirmation
8. Normal Runtime Storage（普通用户运行空间）still used the pre-design legacy View presentation.
9. Normal Settings（普通用户设置）still entered the generic Studio theme.

## Repair applied

### Functional parity
- restored Home search / filter;
- restored Project Details / Delete;
- restored persistent project Open / Refresh visibility with the R48-D2 enabled-state rules;
- restored Runtime Location in all project presentation states;
- restored External Provider recovery parity including Open Termux + Re-check;
- changed configuration primary action from “Save and run” back to **Save configuration**;
- running remains a separate next action after configuration;
- no Shared Core / Runtime / Environment semantics were changed.

### Secondary-path design
Normal Mode now uses the SiftAlpha X design system for:
- Import route;
- Project Location route;
- More route;
- New Project route;
- GitHub Import route;
- Project Details route;
- Delete confirmation route;
- Runtime Storage route;
- Settings entry theme.

System file pickers and Android system settings remain system UI by design.

## Version
- versionName: `0.8.0-alpha43-r48d3`
- versionCode: `196`

## Gate
Before delivery:
- repository validators;
- unit tests;
- assembleDebug;
- stable signing;
- R48-D2 parity review.
