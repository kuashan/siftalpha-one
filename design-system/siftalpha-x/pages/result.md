# Result（结果）— Page Override

## Principle
Result（结果）是普通用户流程终点，不暴露技术结果类型。

## Unified Result Entry
项目页只显示：
**打开结果**

内部可以是：
- Web Page（网页）
- Rich Result（富结果）
- Text（文本）
- Table（表格）
- Chart（图表）
- Local Result Host（本地结果宿主）

Normal Mode 不要求用户知道分类。

## Result screen
- Back
- Project name
- Result content
- Optional refresh / open externally / share-like actions only when真实可用
- 不显示 raw Runtime diagnostics（原始运行时诊断）

## Empty result
如果项目已运行但还没有可展示结果：
“项目仍在运行，结果出现后会自动显示。”
配合真实 activity indicator（活动指示条）。
