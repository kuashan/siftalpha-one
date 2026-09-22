# Home（首页）— Page Override

## Goal
让用户 3 秒内知道：
- 项目在哪里
- 如何导入
- 已有项目现在是什么状态

## Layout
1. SiftTopBar
   - 原始 SiftAlpha X Logo（标志）+ “SiftAlpha X”
   - More（更多）
2. Hero action
   - Primary: 导入项目
   - Secondary: 新建项目
3. Project Location（项目位置）
   - 单行 compact row（紧凑行）
   - 显示当前根目录名称
4. Project List（项目列表）
   - ProjectCard
   - 状态优先于路径
5. Empty State（空状态）
   - 品牌图形简化线条
   - “还没有项目”
   - Primary: 导入项目
   - Secondary: 新建项目

## Import
点击“导入项目”后使用 Modal Bottom Sheet（底部弹层）：
- Python 文件
- ZIP 项目
- GitHub 链接

不要把三种入口永久堆在首页。

## Forbidden
- Runtime / Termux / PID / logs（运行时 / Termux / PID / 日志）
- Bottom Navigation（底部导航）——普通用户模式明确禁止
- Developer diagnostics（开发者诊断）


## R48-D2 Parity（R48-D2 等价要求）
Home（首页）视觉重设计必须保留：
- Project search（项目搜索）
- All / Python / Node filters（全部 / Python / Node 筛选）
- Project Open（打开）
- Project Details（详情）
- Project Delete（删除）

ProjectCard（项目卡片）可以重新排版，但不能把 Details / Delete 功能删除或藏成不可发现的能力。
