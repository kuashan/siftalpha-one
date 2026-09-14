# SiftAlpha Studio 测试矩阵

状态含义：

- **PASS**：已由云端或用户明确确认通过。
- **待真机**：代码和云端验证已完成，等待手机操作。
- **回归待确认**：历史版本已通过，新 APK 仍应快速复测。
- **阻塞**：发现问题，必须修复后再继续。

## A. 当前版本信息

- 版本：`0.8.0-alpha14`
- versionCode：`90`
- 包名：`com.siftalpha.studio`
- APK：[直接下载](https://github.com/kuashan/siftalpha-one/releases/download/w2-test-0ee299e/app-debug.apk)
- Release：[w2-test-0ee299e](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-0ee299e)
- APK SHA-256：`a1550c94aa8a6183f3705e8d3450591eeb5beac4ec9fbdf55965b7603ec3b6a7`

## B. 云端验证矩阵

| ID | 场景 | 期望 | 状态 | 证据 |
|---|---|---|---|---|
| C-01 | 仓库校验器 | 启动图、本地化和 UI 源码校验通过 | PASS | [Actions Run #27](https://github.com/kuashan/siftalpha-one/actions/runs/34880577089) |
| C-02 | 单元测试 | `testDebugUnitTest` 通过 | PASS | [Actions Run #27](https://github.com/kuashan/siftalpha-one/actions/runs/34880577089) |
| C-03 | APK 组装 | `assembleDebug` 成功 | PASS | [Actions Run #27](https://github.com/kuashan/siftalpha-one/actions/runs/34880577089) |
| C-04 | 稳定测试签名 | 包签名证书与既有测试版本一致 | PASS | Actions evidence / certificate digest |
| C-05 | APK 元数据 | 包名、版本名和 versionCode 正确 | PASS | `com.siftalpha.studio`, alpha14, 90 |
| C-06 | 发布资产 | Release 中存在可下载 APK | PASS | [Release asset](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-0ee299e) |

## C. 安装与升级矩阵

| ID | 操作 | 期望 | 状态 |
|---|---|---|---|
| I-01 | 在 alpha13 上直接安装 alpha14 | 安装器允许覆盖，不要求卸载 | 待真机 |
| I-02 | 覆盖安装后打开 App | 项目、设置和已有本地数据仍可访问 | 待真机 |
| I-03 | 覆盖安装后确认版本 | 显示 alpha14 / versionCode 90 | 待真机 |

## D. 配置功能矩阵

| ID | 操作 | 期望 | 状态 |
|---|---|---|---|
| K-01 | 打开两个真实脚本的项目卡片 | 配置状态可以显示候选数量，但明确“不阻止运行” | 待真机 |
| K-02 | 两个脚本只有候选项时点击“配置” | 直接进入配置列表，不出现强制的第 1/2 项向导 | 待真机 |
| K-03 | 不填写候选项，直接 PREPARE → START | 项目仍可运行，候选提醒不阻止启动 | 待真机 |
| K-04 | 保存一个可选配置 | 值安全保存，重新打开时显示已配置，项目仍可运行 | 待真机 |
| K-05 | 项目明确声明 requiredEnv | 缺失项显示为必需配置；首次运行仍允许通过运行结果完成探测策略 | 待真机 |
| K-06 | 运行输出明确报告缺失环境变量 | 出现配置提示；变量进入下一次启动前的必需预检 | 待真机 |
| K-07 | 为真实缺失变量保存值后再次运行 | 配置预检通过，项目可以重新启动 | 待真机 |
| K-08 | 项目只写普通硬编码 URL | 不自动生成误报配置项 | 待真机 |

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

1. 先直接覆盖安装，不卸载 alpha13。
2. 打开两个脚本，进入“配置”，确认只有候选项时直接显示列表。
3. 返回后不填写候选项，依次执行 PREPARE 和 START。
4. 观察项目是否正常运行，配置提醒是否仍然存在但不阻止运行。
5. 如项目实际会报告缺失密钥或环境变量，确认 Studio 给出配置提示；保存后重新运行。
6. 回归 STOP → START。
7. 回归打开网页、从 Chrome 返回 Studio，以及返回后短时间内页面是否保持正常。
8. 最后确认版本和本地项目数据仍然存在。

每次真机测试完成后，在对应行把“待真机/回归待确认”改成 PASS 或阻塞，并在 DEV_LOG 追加日期、设备、步骤和结果。
