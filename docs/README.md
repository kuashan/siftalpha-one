# SiftAlpha Studio 项目记录

这里保存项目的长期上下文、架构定义、开发日志和测试矩阵。它们是后续开发的连续性记录，不是临时说明。

## 文档入口

- [ARCHITECTURE_M_R_X.md](ARCHITECTURE_M_R_X.md)：SiftAlpha M / R / X 的规范架构定义、M ↔ R Interface 原则、Termux/Runtime Provider 分类和 alpha29 evidence scope。
- [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md)：项目目标、基线、架构边界、关键决策和当前状态。
- [DEV_LOG.md](DEV_LOG.md)：按时间追加的开发、修复、构建、发布和用户验收记录。
- [TEST_MATRIX.md](TEST_MATRIX.md)：云端验证、APK 安装和真机测试场景的状态。

## 维护规则

1. 每次代码或产品行为发生变化后，追加一条 DEV_LOG。
2. 每次新增或修改用户可见行为后，更新 TEST_MATRIX。
3. 只有架构、产品边界、构建流程或长期决策变化时，才更新 PROJECT_CONTEXT 和对应的 canonical architecture doc。
4. 日志中只记录公开提交、构建运行、版本、APK 校验值和测试结论；绝不记录密码、密钥值或用户项目中的敏感配置。
5. “云端构建通过”与“真机验收通过”分开记录，不能互相替代。
6. 历史术语、Release/tag、artifact 和用户验收记录保持真实；当前术语变化通过 superseded note 解释，不做全局机械替换。
