# SiftAlpha Studio 测试矩阵

状态含义：

- **PASS**：已由云端或用户明确确认通过。
- **待真机**：代码和云端验证已完成，等待手机操作。
- **回归待确认**：历史版本已通过，新 APK 仍应快速复测。
- **阻塞**：发现问题，必须修复后再继续。

## A. 当前版本信息

- 版本：`0.8.0-alpha17`
- versionCode：`93`
- 包名：`com.siftalpha.studio`
- 构建产物：[Run #41 artifact ZIP](https://github.com/kuashan/siftalpha-one/actions/runs/34914830358/artifacts/10374759918)
- Release：本次提交触发发布，待 Actions 完成
- APK SHA-256：b1ed2c060af2804bae611a543852f8e2afc8d70650de00c661acb9aefbfc17da

## B. 云端验证矩阵

| ID | 场景 | 期望 | 状态 | 证据 |
|---|---|---|---|---|
| C-01 | 仓库校验器 | 启动图、本地化和 UI 源码校验通过 | PASS | [Actions Run #35](https://github.com/kuashan/siftalpha-one/actions/runs/34911589787) |
| C-02 | 单元测试 | `testDebugUnitTest` 通过 | PASS | [Actions Run #35](https://github.com/kuashan/siftalpha-one/actions/runs/34911589787) |
| C-03 | APK 组装 | `assembleDebug` 成功 | PASS | [Actions Run #35](https://github.com/kuashan/siftalpha-one/actions/runs/34911589787) |
| C-04 | 稳定测试签名 | 包签名证书与既有测试版本一致 | PASS | Run #35 evidence / certificate digest |
| C-05 | APK 元数据 | 包名、版本名和 versionCode 正确 | PASS | `com.siftalpha.studio`, alpha16, 92 |
| C-06 | 发布资产 | 新版本 Release 中存在可下载 APK | PASS | [w2-test-8a91895](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-8a91895) |
| C-07 | W2 编辑器云端构建 | 单元测试、APK 组装、签名和证据收集通过 | PASS | [Actions Run #41](https://github.com/kuashan/siftalpha-one/actions/runs/34914830358) |

## C. 安装与升级矩阵

| ID | 操作 | 期望 | 状态 |
|---|---|---|---|
| I-01 | 在 alpha15 上直接安装 alpha16 | 安装器允许覆盖，不要求卸载 | 待真机 |
| I-02 | 覆盖安装后打开 App | 项目、设置和已有本地数据仍可访问 | 待真机 |
| I-03 | 覆盖安装后确认版本 | 显示 alpha16 / versionCode 92 | 待真机 |

## D. 配置功能矩阵

| ID | 操作 | 期望 | 状态 |
|---|---|---|---|
| K-01 | 打开两个真实脚本的项目卡片 | 建议项显示提醒，并明确“不阻止运行” | 待真机 |
| K-02 | 两个脚本只有候选项时点击“配置” | 先显示摘要，点击“查看配置”后进入编辑列表；不出现强制的第 1/2 项向导 | 待真机 |
| K-03 | 不填写候选项，直接 PREPARE → START | 项目仍可运行，候选提醒不阻止启动 | 待真机 |
| K-04 | 点击未配置的 OPTIONAL 项 | 直接出现输入框；保存后显示已配置，且不自动启动项目 | 待真机 |
| K-05 | 项目明确声明 requiredEnv 或代码直接读取 | 缺失项显示为必需配置，并在首次 START 前阻止运行 | 待真机 |
| K-06 | 运行输出明确报告缺失环境变量 | 标记为运行诊断来源，并进入下一次启动前的必需预检 | 待真机 |
| K-07 | 为真实缺失变量保存值后再次运行 | 配置预检通过，项目可以重新启动 | 待真机 |
| K-08 | 项目只写普通硬编码 URL | 不自动生成误报配置项 | 待真机 |
| K-09 | 点击配置项 | 显示 REQUIRED/OPTIONAL、检测来源和文件/行号证据；不显示秘密值 | 待真机 |
| K-10 | OPTIONAL 编辑闭环 | 提醒 → 查看配置 → 输入 → 保存 → 状态刷新完成 | 待真机 |
| K-11 | OPTIONAL 不填写 | 关闭编辑后仍可 PREPARE → START | 待真机 |
| K-12 | REQUIRED 回归 | 缺少 REQUIRED 仍阻止 START；填写后恢复运行 | 待真机 |
| K-13 | Mixed 配置 | REQUIRED 完成、OPTIONAL 缺失时可运行；填写 OPTIONAL 只更新其状态 | 待真机 |



## D.1 W2 Configuration Editor UX Simplification

| ID | 操作 | 期望 | 状态 |
|---|---|---|---|
| K-14 | 点击项目卡片“配置” | 直接进入“项目配置编辑”，不再先显示 Summary | 待真机 |
| K-15 | 编辑列表中同时存在 REQUIRED 与 OPTIONAL | 两类配置都显示输入框和来源/证据 | 待真机 |
| K-16 | 填写 OPTIONAL 后保存 | 状态更新为已配置，且不改变 START 权限 | 待真机 |
| K-17 | OPTIONAL 留空后保存/关闭 | 不报必填错误，仍可 START | 待真机 |
| K-18 | 填写 REQUIRED 后保存 | 状态更新，缺失 REQUIRED 消失，原有运行策略保持 | 待真机 |
| K-19 | Mixed 配置 | REQUIRED 与 OPTIONAL 状态独立显示，OPTIONAL 不阻塞 START | 待真机 |
| K-20 | STOP 后打开配置 | 配置入口仍可用，OPTIONAL/REQUIRED 仍可编辑保存 | 待真机 |

## E. Runtime 回归矩阵

| ID | 操作 | 期望 | 状态 |
|---|---|---|---|
| R-01 | PREPARE 完成后手动 START | 准备和运行分开，流程正常 | 回归待确认 |
| R-02 | 运行中点击 STOP | 项目停止，STOP 后可以再次 RUN/START | 回归待确认（历史已通过） |
| R-03 | 从 Studio 打开真实可达网页 | Chrome 正常打开 | 回归待确认 |
| R-04 | Chrome 返回 Studio | 不出现明显白屏；旧界面保持或快速恢复 | 回归待确认（历史已通过） |
| R-05 | Web 端点不可达 | Browser 不应开放 | PASS/持续回归 |
| R-06 | 配置值和日志 | 不显示密钥明文 | PASS/持续回归 |
| R-07 | 清理操作 | 项目环境、共享工具、缓存和源码删除分别确认 | 待真机 |
| R-08 | 后台恢复/重新进入 | 运行状态通过 STATUS 等真实结果恢复，不凭旧 UI 判断 | 待真机 |

## F. 当前版本建议测试顺序

1. 先直接覆盖安装，不卸载 alpha16。
2. 打开两个脚本，点击“配置”，确认直接进入“项目配置编辑”并看到 OPTIONAL 输入框。
3. 返回后不填写候选项，依次执行 PREPARE 和 START。
4. 观察项目是否正常运行，配置提醒是否仍然存在但不阻止运行。
5. 如项目实际会报告缺失密钥或环境变量，确认 Studio 给出配置提示；保存后重新运行。
6. 回归 STOP → START。
7. 回归打开网页、从 Chrome 返回 Studio，以及返回后短时间内页面是否保持正常。
8. 最后确认版本为 alpha16 / versionCode 92，且本地项目数据仍然存在。建议配置缺失时，START 仍然可用；只有必需配置缺失时 START 才应被阻止。

每次真机测试完成后，在对应行把“待真机/回归待确认”改成 PASS 或阻塞，并在 DEV_LOG 追加日期、设备、步骤和结果。
