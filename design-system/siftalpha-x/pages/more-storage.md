# More + Runtime Storage（更多 + 运行空间）— Page Override

## R48-D2 Baseline Rule
More（更多）入口不得用新设计替换掉 R48-D2 原有功能。

Normal Mode More 必须保留四个入口：
1. Refresh Projects（刷新项目）
2. New Project（新建项目）
3. Runtime Storage（运行空间）
4. Settings（设置）

Project Location（项目位置）继续作为 Home（首页）一等入口，不拿 More 替代。
Background Reliability Guidance（后台可靠性提醒）继续按 R48-D2 在真实 Prepare / Run（准备 / 运行）前触发，不改造成另一个重复设置模型。

## Runtime Storage
只显示：
- SiftAlpha X 运行空间总占用
- 项目环境合计
- 每个项目占用
- 清理这个项目的运行空间

允许真实 storage proportion（存储比例）可视化，因为数据可计算。

不显示：
RootFS / PRoot / apt / wheelhouse internals / PID / session internals。

清理确认必须明确：
“不会删除项目源码；下次运行前需要重新准备项目。”

Runtime Storage redesign（运行空间重设计）只能替换 Presentation Layer（表现层），必须继续复用 R48-D2 的 Internal / External storage controllers（内部 / 外部存储控制器）与 project-scoped cleanup（项目级清理）语义。
