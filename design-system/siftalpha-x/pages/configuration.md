# Configuration（配置）— Page Override

## Purpose
把配置从“技术弹窗”升级为正式的一等页面。

## Layout
1. Top bar: 配置
2. Intro summary:
   - “还需要 2 项必要配置”
3. 必要配置 section
4. 可选配置 section
5. Sticky bottom primary action（底部固定主操作）
   - 保存并继续 / 保存配置

## Field rules
- Label 永远可见
- REQUIRED（必填）/ OPTIONAL（可选）用 chip
- Secret（密钥）输入默认隐藏，提供显示/隐藏
- 错误紧邻字段
- 说明只写用户为什么需要填，不解释内部注入机制
- 已配置 secret 不回显明文

## Completion
成功保存后：
“配置完成，可以运行。”
返回项目页时 ProjectStatusCard 应立即变为 Ready（已准备）或下一真实状态。
