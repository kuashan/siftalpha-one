# More + Runtime Storage（更多 + 运行空间）— Page Override

## More
推荐使用 Modal Bottom Sheet（底部弹层），四个一等入口：
1. 运行空间
2. 项目位置
3. 后台运行与电池
4. Settings（设置）

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
