# SiftAlpha Studio 测试矩阵

状态含义：

- **PASS**：已由云端或用户明确确认通过。
- **待真机**：代码和云端验证已完成，等待手机操作。
- **回归待确认**：历史版本已通过，新 APK 仍应快速复测。
- **阻塞**：发现问题，必须修复后再继续。

证据标签：

- **unit-test verified**：由 JVM 单元测试覆盖并通过。
- **GitHub Actions verified**：由主线 GitHub Actions 构建、测试或验证步骤覆盖并通过。
- **real-device verified**：由 Android 真机实际操作覆盖并通过。
- **user-confirmed**：由用户提供并确认的真机结果；与代码/云端证据分开记录。

本文件是 Regression / Verification Evidence（回归与验证证据）。其中旧 W2–W5 名称仅在历史工作项、测试记录、Release/tag 或 artifact 证据中保留，不再表示当前 Roadmap 阶段、完成度 checklist 或开发 gate。

## A. 当前 alpha30 版本信息

- 当前 Runtime prototype 版本：`0.8.0-alpha30`
- versionCode：`106`
- 包名：`com.siftalpha.studio`
- 当前 docs/runtime prototype branch：`codex/siftalpha-x-embedded-cpython-spike`
- alpha30 implementation starting HEAD：`36b0175248a491b85560d68fcfd12889dbf5d0d7`
- main HEAD：`dff275575a9cdbd0564d394c4626cd7d9bb22637`
- canonical architecture：[ARCHITECTURE_M_R_X.md](ARCHITECTURE_M_R_X.md)
- GitHub Actions [Run #80](https://github.com/kuashan/siftalpha-one/actions/runs/35060381162)，Run ID `35060381162`；artifact `siftalpha-w0-80`（ID `10432576095`），digest `sha256:94cf4d1ead49e8500f9b2467765a983b8e0164be354c1e59083670763945eaba`。
- alpha29 APK SHA-256：`2a7b7434817ffec53863ad9fd16745bdfef101677c5470a81efdab78aa269cc6`。
- Run #80 未包含 Android emulator/device；Embedded CPython instrumentation source 已加入并随源码编译，但没有把 CI 视为 instrumentation executed/pass。
- 详细 alpha29 real-device evidence 见本文件 H 节和 [DEV_LOG.md](DEV_LOG.md)。

## B. 云端验证矩阵

| ID | 场景 | 期望 | 状态 | 证据 |
|---|---|---|---|---|
| C-01 | 仓库校验器 | 启动图、本地化和 UI 源码校验通过 | PASS | [Actions Run #35](https://github.com/kuashan/siftalpha-one/actions/runs/34911589787) |
| C-02 | 单元测试 | `testDebugUnitTest` 通过 | PASS | [Actions Run #35](https://github.com/kuashan/siftalpha-one/actions/runs/34911589787) |
| C-03 | APK 组装 | `assembleDebug` 成功 | PASS | [Actions Run #35](https://github.com/kuashan/siftalpha-one/actions/runs/34911589787) |
| C-04 | 稳定测试签名 | 包签名证书与既有测试版本一致 | PASS | Run #35 evidence / certificate digest |
| C-05 | APK 元数据 | 包名、版本名和 versionCode 正确 | PASS | `com.siftalpha.studio`, alpha16, 92 |
| C-06 | 发布资产 | 新版本 Release 中存在可下载 APK | PASS | [w2-test-8a91895](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-8a91895) |
| C-07 | 编辑器云端构建（历史工作项） | 单元测试、APK 组装、签名和证据收集通过 | PASS | [Actions Run #41](https://github.com/kuashan/siftalpha-one/actions/runs/34914830358) |
| C-08 | 测试 APK 发布（历史工作项） | 预发布 Release 可下载，资产校验值已记录 | PASS | [Actions Run #42](https://github.com/kuashan/siftalpha-one/actions/runs/34915100958) / [Release](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-6e259f7) |
| C-09 | Runtime/Web 展示状态分离云端验证（历史工作项） | 单元测试、APK 组装、签名和证据收集通过 | PASS | [Actions Run #44](https://github.com/kuashan/siftalpha-one/actions/runs/34918070668) |
| C-10 | Runtime 展示状态分离 APK 发布（历史工作项） | 预发布 Release 可下载，版本/签名/SHA-256 已记录 | PASS | [Actions Run #45](https://github.com/kuashan/siftalpha-one/actions/runs/34918441426) / [Release](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-febcabb) |
| C-11 | RuntimeLifecycleStore 候选验证（历史工作项） | 单元测试先通过，随后 APK 组装通过 | PASS | [候选 Actions Run #1](https://github.com/kuashan/siftalpha-one/actions/runs/34922851615) |
| C-12 | RuntimeLifecycleStore 正式 APK 发布（历史工作项） | 正式 main 单元测试、APK 组装、稳定签名和 Release 通过 | PASS | [Actions Run #47](https://github.com/kuashan/siftalpha-one/actions/runs/34923173498) / [Release](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-4e899c6) |
| C-13 | Web Discovery Diagnostic Enhancement 云端验证（历史工作项） | 仓库校验、单元测试、APK 组装、签名和证据收集通过；不发布 Release | PASS | [Actions Run #49](https://github.com/kuashan/siftalpha-one/actions/runs/34927655920) / [artifact](https://github.com/kuashan/siftalpha-one/actions/runs/34927655920/artifacts/10379489647) |
| C-14 | 合并后正式 main 构建（历史基线证据） | validators、`testDebugUnitTest`、`assembleDebug`、APK metadata 和签名证据收集通过 | PASS | [Actions Run #70](https://github.com/kuashan/siftalpha-one/actions/runs/34989822426) / [artifact](https://github.com/kuashan/siftalpha-one/actions/runs/34989822426/artifacts/10405122896) |
| C-15 | alpha29 Embedded CPython Runtime prototype CI | CPython preparation、validators、JVM unit tests、CMake/native build、`assembleDebug`、signing verification 和 evidence collection 通过 | PASS | [Actions Run #80](https://github.com/kuashan/siftalpha-one/actions/runs/35060381162) / [artifact `siftalpha-w0-80`](https://github.com/kuashan/siftalpha-one/actions/runs/35060381162/artifacts/10432576095) |

## C. 安装与升级矩阵

| ID | 操作 | 期望 | 状态 |
|---|---|---|---|
| I-01 | 在 alpha16 上直接安装 alpha17 | 安装器允许覆盖，不要求卸载 | 待真机 |
| I-02 | 覆盖安装后打开 App | 项目、设置和已有本地数据仍可访问 | 待真机 |
| I-03 | 覆盖安装后确认版本 | 显示 alpha17 / versionCode 93 | 待真机 |
| I-04 | 在 alpha17 上直接安装 alpha18 | 安装器允许覆盖，版本为 alpha18 / versionCode 94 | 待真机 |
| I-05 | 在 alpha18 上直接安装 alpha19 | 安装器允许覆盖，版本为 alpha19 / versionCode 95 | 历史记录 |
| I-06 | 在 alpha24 上直接安装 alpha25 | 安装器允许覆盖，版本为 alpha25 / versionCode 101；签名保持一致 | real-device verified / user-confirmed |
| I-07 | 在真实 Android aarch64 设备安装并运行 alpha29 Embedded CPython prototype | Experimental 页面可打开；当前设备验收序列见 H 节 | real-device verified / user-confirmed |

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



## D.1 Configuration Editor UX Simplification（历史工作项）

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

## F. 历史 alpha25 建议测试顺序（历史记录）

1. 先直接覆盖安装，不卸载当时的 alpha25。
2. 打开两个脚本，点击“配置”，确认直接进入“项目配置编辑”并看到 OPTIONAL 输入框。
3. 返回后不填写候选项，依次执行 PREPARE 和 START。
4. 观察项目是否正常运行，配置提醒是否仍然存在但不阻止运行。
5. 如项目实际会报告缺失密钥或环境变量，确认 Studio 给出配置提示；保存后重新运行。
6. 回归 STOP → START。
7. 回归打开网页、从 Chrome 返回 Studio，以及返回后短时间内页面是否保持正常。
8. 最后确认当时版本为 alpha25 / versionCode 101，且本地项目数据仍然存在。建议配置缺失时，START 仍然可用；只有必需配置缺失时 START 才应被阻止。

每次真机测试完成后，在对应行把“待真机/回归待确认”改成 PASS 或阻塞，并在 DEV_LOG 追加日期、设备、步骤和结果。


## E.1 Runtime Presentation State Separation（历史工作项）

| ID | 操作 | 期望 | 状态 |
|---|---|---|---|
| R-09 | Python 输出普通 `http://localhost:8000`，但没有 HTTP 服务 | Runtime 显示 RUNNING；Web 独立显示 AUTO_DETECT/UNAVAILABLE；不能显示 STARTING | unit-test verified / GitHub Actions verified |
| R-10 | 纯 Python 脚本，无 URL、无 Web | Runtime 显示 RUNNING | unit-test verified / GitHub Actions verified |
| R-11 | 真实 Web 项目执行 `python -m http.server 8000` | Runtime 显示 RUNNING；Web 显示 AVAILABLE；Browser 可用 | unit-test verified / GitHub Actions verified |
| R-12 | Web 探测输出 `NO_LISTEN_PORT` 或 `NO_SOCKET_INODES` | 只影响 Web 状态，不改写 Runtime RUNNING | unit-test verified / GitHub Actions verified |
| R-13 | STOP 后再次 START | STOP 可用；停止后可以重新运行 | real-device verified / user-confirmed（04D） |


## E.2 RuntimeLifecycleStore SharedPreferences Migration Fix（历史工作项）

| ID | 操作 | 期望 | 状态 |
|---|---|---|---|
| L-01 | 旧数据以字符串 `"true"/"false"` 保存 | 正确读取并迁移为 Boolean，不闪退 | 云端通过，待真机 |
| L-02 | 新数据以 Boolean 保存 | 正确读取环境状态、Runtime 状态和失败原因 | 云端通过，待真机 |
| L-03 | 迁移后重新创建 Store 再读取 | 结果保持一致，SharedPreferences 类型为 Boolean | 云端通过，待真机 |
| L-04 | 空数据或异常类型 | 回退 UNKNOWN/null，不导致运行中心闪退 | 云端通过，待真机 |

## E.3 Web Discovery Diagnostic Enhancement（历史工作项）

| ID | 操作/模拟条件 | 期望诊断输出 | 状态 |
|---|---|---|---|
| W-01 | 项目 PID 列表为空 | `SIFTALPHA_WEB_DISCOVERY_STATUS=NO_PROJECT_PIDS` | 云端单元测试通过，待真机 |
| W-02 | 有项目 PID，但没有可读 socket inode | `SIFTALPHA_WEB_DISCOVERY_STATUS=NO_SOCKET_INODES` | 云端单元测试通过，待真机 |
| W-03 | 项目 procfs 或 TCP 表不可读 | `SIFTALPHA_WEB_DISCOVERY_STATUS=PROCFS_UNREADABLE` | 云端单元测试通过，待真机 |
| W-04 | 有 socket inode，但 TCP 表中没有匹配 inode | `SIFTALPHA_WEB_DISCOVERY_STATUS=NO_INODE_MATCH` | 云端单元测试通过，待真机 |
| W-05 | 匹配到 LISTEN 端口，尚未完成 HTTP 检查 | `SIFTALPHA_WEB_DISCOVERY_STATUS=LISTEN_PORT_FOUND` 与 `SIFTALPHA_WEB_DISCOVERY_STAGE=LISTEN_FOUND` | 云端单元测试通过，待真机 |
| W-06 | 匹配到端口，但 HTTP 探测不可达 | `SIFTALPHA_WEB_DISCOVERY_STATUS=NO_HTTP_ENDPOINT` | 云端单元测试通过，待真机 |
| W-07 | HTTP 探测成功 | `SIFTALPHA_WEB_DISCOVERY_STATUS=PASS`，并保留原有 PASS/URL 输出 | 云端单元测试通过，待真机 |
| W-08 | 任一诊断失败场景 | 原有 `SIFTALPHA_WEB_AUTODISCOVERY` 兼容输出仍存在 | 云端构建通过，待真机 |
| W-09 | Runtime START/PREPARE/STOP 回归 | 生命周期和 Browser 安全条件不受诊断字段影响 | 待后续 APK 真机回归 |



## H. alpha29 Embedded CPython Runtime Prototype — Real-Device Acceptance

本节是用户提供并确认的真实 Android 设备证据，不由 GitHub Actions instrumentation 推断。该证据只覆盖当前 fixed-script Embedded CPython prototype 的生命周期范围。

### H.1 Build identity

| 字段 | 事实 |
|---|---|
| version | `versionCode=105`, `versionName=0.8.0-alpha29` |
| branch | `codex/siftalpha-x-embedded-cpython-spike` |
| source HEAD | `a5691fff049a6be25ccade78f1ef23ce869543bf` |
| workflow | SiftAlpha W0 Cloud Build |
| Run | #80 |
| Run ID | `35060381162` |
| artifact | `siftalpha-w0-80` |
| Artifact ID | `10432576095` |
| artifact digest | `sha256:94cf4d1ead49e8500f9b2467765a983b8e0164be354c1e59083670763945eaba` |
| APK SHA-256 | `2a7b7434817ffec53863ad9fd16745bdfef101677c5470a81efdab78aa269cc6` |

### H.2 Observed device sequence

| Step | Observed facts | Status |
|---|---|---|
| Test A | `ENGINE=CPYTHON`; `TERMUX=NOT_USED`; `PROOT=NOT_USED`; generation 1; `STATE=SUCCEEDED`; `RUNTIME_PHASE=TERMINAL`; `STOP_PHASE=IDLE`; `STOP_RESULT=NONE`; exitCode 0; CPython 3.14.7; `sys.platform=android`; machine `aarch64`; stdlib json OK | PASS |
| Test B | generation 3; `STATE=FAILED`; `RUNTIME_PHASE=TERMINAL`; `STOP_PHASE=IDLE`; `STOP_RESULT=NONE`; exitCode 1; stdout `SIFTALPHA_X_TEST_B_STDOUT`; stderr includes `SIFTALPHA_X_TEST_B_STDERR` and intentional `RuntimeError: SIFTALPHA_X_TEST_B_FAILURE` | PASS（intentional failure） |
| Evidence gap | Generation 2 was not captured in supplied evidence. No cause is inferred and it is not classified as a Runtime failure. | recorded gap |
| Test C running | generation 4; `STATE=RUNNING`; `RUNTIME_PHASE=PYTHON_EXEC_BEGIN`; `STOP_PHASE=IDLE`; `STOP_RESULT=NONE`; exitCode `-` | PASS（STOP precondition） |
| Test C cooperative STOP | Same session ID and generation 4; `STATE=STOPPED`; `RUNTIME_PHASE=TERMINAL`; `STOP_PHASE=STOP_REQUEST_RETURNED`; `STOP_RESULT=INTERRUPT_DELIVERED`; exitCode 130; stdout contains repeated `SIFTALPHA_X_TEST_C_TICK` and `SIFTALPHA_X_TEST_C_COOPERATIVE_STOP`; stderr `SIFTALPHA_X_STOP=COOPERATIVE`; no ordinary FAILED traceback | PASS |
| Post-stop re-entry | Without restarting Android application process; new session ID; generation 5; `STATE=SUCCEEDED`; `RUNTIME_PHASE=TERMINAL`; `STOP_PHASE=IDLE`; `STOP_RESULT=NONE`; exitCode 0; CPython 3.14.7; Android/aarch64; stdlib json OK | PASS |

### H.3 Limited conclusions

alpha29 真实设备证据支持：

- Embedded CPython 3.14.7 在真实 Android aarch64 设备执行；
- 当前 prototype 测试路径不依赖 Termux / PRoot；
- process-scoped CPython initialization 支持当前已测试的 multi-Session re-entry；
- success、intentional failure、stdout/stderr capture、cooperative interruption、STOPPED/130 和 STOP 后新 Session 执行成立。

alpha29 不证明：

- R 已完成或 X 已 achieved；
- arbitrary C extension / blocking native code / blocking syscall interruption；
- universal hard-stop；
- arbitrary Python project、pip、native wheels、numpy/pandas/scipy、venv、dependency installation；
- SAF imported project、Web/Browser 与 R 的 production integration；
- process/lifecycle recovery、concurrent runtimes、multi-project execution、完整 isolation 或 production-grade sandboxing。

`PyThreadState_SetAsyncExc` 相关行为必须描述为 cooperative / limited interruption behavior，不能描述为 universal hard-stop mechanism。

## G. 合并后产品基线审计记录 — Production baseline merge@893229ce（历史审计）

本节记录 Production baseline merge 的历史完成度审计，不覆盖范围外的未来功能；Roadmap/docs base main 为 `38fb60af8e4c7a4b09eafb1cad0e305ae56fc353`。评级只使用 `COMPLETE`、`PARTIAL`、`MISSING`、`BLOCKED`；`PARTIAL` 表示实现存在，但架构收敛或验证证据仍不完整。

| 领域 | 评级 | 当前源码证据与验证边界 |
|---|---|---|
| Import | PARTIAL | `MainActivity` 的 SAF 选择、`ProjectStore`、`V04ProjectGateway` 的 Python/ZIP/GitHub 导入路径存在；缺少当前 alpha25 导入全链路真机证据。 |
| Project Identity | COMPLETE | SAF `documentId` 在 `MainActivity`、`HomeScreen`、`ProjectWorkspaceActivity` 和 `V04ProjectGateway` 间传递；有 JVM policy/identity 测试。 |
| Workspace | PARTIAL | `ProjectWorkspaceActivity` 提供单项目入口，但仍复用 `V04Activity`，没有独立 session/ViewModel 协调层。 |
| Configuration | PARTIAL | `ConfigurationModels`、`ProjectConfigurationInspector/Preflight`、`ProjectConfigurationUiController` 和 `ProjectActionPolicy` 已接入；当前版本缺少完整真机配置编辑证据。 |
| Secret Storage | COMPLETE | `ProjectSecretStore` 使用 Android Keystore AES/GCM，SharedPreferences 只保存密文；有存储、脱敏和读取测试。 |
| Prepare | COMPLETE | `ProjectRuntimeController`、运行时 planner 和 `ManagedProcessRuntime` 提供项目范围 PREPARE；JVM/云端及 04B/04D 覆盖。 |
| Start | COMPLETE | Python 主路径、受限 Node 路径和统一 controller 已接入；有 planner/adapter 测试及 04B/04D 证据。 |
| Status | COMPLETE | `RuntimeState`、`RuntimeLifecycleStore`、STATUS 解析与真实进程 reconciliation 已存在；迁移和生命周期测试通过。 |
| Logs | COMPLETE | Python 使用当前 runtime log 和 bounded tail，Web log fallback 有 raw `logsInner` 测试；04D 验证 Logs 不破坏 RUNNING。 |
| Output | PARTIAL | `ProjectOutputPanelController` 和 bounded output 存在，但当前 alpha25 缺少专门的真机输出闭环证据。 |
| Stop | COMPLETE | `ManagedProcessRuntime` 按项目进程树停止并写入状态；有 STOP 测试，04D 已验证。 |
| Restart | COMPLETE | 04D 已由用户确认 Stop 后重新 Start 和 Browser 恢复；持久 Web candidate 生命周期仍列为风险。 |
| Recovery | PARTIAL | 进入前使用真实 STATUS reconciliation，而非只信旧 UI；尚无 App 重启后的真机 recovery 证据。 |
| Runtime Identity | COMPLETE | `RuntimeIdentity/RuntimeIdentityStore` 贯穿 START、STATUS、LOGS、STOP、恢复和 Web discovery；FULL_IDENTITY/guest root ALIVE 已真机确认。 |
| Python Runtime | COMPLETE | Python 为第一优先路径，使用项目/Runtime identity、ManagedProcess 和 bounded logs；JVM、Run #70、04B/04D 均有证据。 |
| Node Runtime | PARTIAL | 有受限 managed Node/npm 与显式 start 合约，但缺少等价 Android 真机验收，且 catalog 与 controller 的暴露方式不完全一致。 |
| Web Discovery | COMPLETE | project-scoped procfs 与 Runtime Log fallback 均存在，禁止全端口/全局进程扫描；04B/04C 已验证成功发现与拒绝误报。 |
| Endpoint Probe | COMPLETE | `RuntimeWebEndpointProbe` 只验证本地 loopback socket；候选仍需探测，JVM/04B/04C 有证据。 |
| Browser | COMPLETE | `ProjectActionPolicy` 要求 RUNNING、Web AVAILABLE 和 endpoint reachable；`V04Activity` 打开前再次验证，04B/04C 已确认。 |
| Failure Handling | PARTIAL | `RuntimeFailureReason`、结构化 markers 和 diagnostics 已有，但部分 UI 仍直接展示原始输出/对话框。 |
| Action Policy | PARTIAL | `ProjectActionPolicy` 覆盖主要动作并有矩阵测试；`V04Activity` 仍有重复 `canDispatch` 和少量 UI-only guard。 |
| UI State Model | PARTIAL | `ProjectUiSnapshot` 是卡片主要事实来源，但 `V04Activity` 仍持有多份可变 map 并负责构建/归并状态。 |
| Real-device Coverage | PARTIAL | alpha25 的 04B/04C/04D 已由用户确认；导入、配置、Node、App 重启 recovery、清理和 Output 仍缺当前基线的完整真机覆盖。 |

### G.1 Runtime Session Ownership Boundary（当前 backlog）

Project identity 已由 SAF `documentId` 建立可信边界，Runtime Identity 已由 `FULL_IDENTITY` 真机结果验证。当前首要缺口不是单纯的 Activity 规模，而是以下 ownership chain 尚未完全闭合：

`Project Identity → Runtime Identity / Runtime Generation → Runtime Lifecycle → Web Candidate Ownership → Recovery`

当前 `RuntimeWebStateStore` 主要持久化 `candidateUrl`、`framework` 和 `detectedAtEpochMs`，并主要按 `projectKey` 存储。若 Runtime A 停止后 Runtime B 启动，旧 candidate 仍可能存在；Endpoint Probe 只能证明端点可达，不能单独证明端点属于当前 Runtime。若其他本地服务复用同一端口，可能出现 reachability 成立但 ownership 错误的结果。

**P0 — Runtime Session Ownership Boundary**

建立最小 project-scoped runtime session ownership 模型，使以下事实明确绑定：project identity、runtime identity/runtime generation、lifecycle state、current runtime candidate URL 和 recovery state。

最低生命周期合同：

- `START` 创建新的 runtime session/generation；旧 session 的 candidate 不得自动继承。
- `RUNNING` 的 Web candidate 必须属于当前 runtime session。
- `LOGS` 必须读取当前 runtime 对应日志。
- `STATUS` 恢复必须验证当前 runtime identity。
- `STOP` 结束当前 runtime session，并 invalidated/cleared 当前 candidate。
- `CLEAN` 使当前项目相关 runtime candidate/session state 失效。
- `RESTART` 创建新的 runtime session/generation，不得继承旧 runtime candidate。
- `RECOVERY` 只能恢复仍匹配当前 runtime identity 的 session 信息；相同 `projectKey` 不能单独使旧 candidate 继续生效。

Coordinator 不是一次性大规模 UI 重构的要求，而是渐进实现该 ownership boundary 的结构手段：第一阶段建立最小 `RuntimeSession`/`RuntimeSessionState`/`RuntimeGeneration` 等价 abstraction，不重写整个 `V04Activity`；第二阶段逐步迁移 refresh、dispatch、recovery 和 Web invalidation；第三阶段再减少 Activity orchestration 职责。

**P1 — 产品基线硬化与验证**

- Web candidate 绑定 runtime identity/generation。
- STOP/CLEAN 明确清理 candidate。
- 完成 App restart recovery、Import、Configuration 和 Node Runtime 真机验证。
- 按需要逐步抽取 coordinator。

**P2 — 后续增强（未来 Roadmap 待重新定义）**

- Workspace Compose 化。
- 更完整的结构化 Failure UI。
- Node 能力扩展。
- 进一步 Activity cleanup。
- 默认 debug 日志 cleanup。
- 签名流程进一步加固。

如果当前只允许再做一个开发任务，优先完成 **Runtime Session Ownership Boundary**。它直接补齐当前最重要的 correctness/ownership 缺口，并为后续 Web candidate 清理、Recovery、Restart 和 coordinator 抽取提供统一边界；`V04Activity` 过大是后续结构表现，不是第一理由。

当前结论：当前产品基线已经形成，但 `Project → Runtime Session → Web Candidate` 的 ownership chain 尚未完整闭合；这属于普通的产品 backlog / hardening，不再使用阶段完成或阻塞语义。未来 Roadmap 尚待重新定义。


## I. alpha30 R Project Script Execution Boundary

| ID | 场景 | 期望 | 状态 |
|---|---|---|---|
| R-14 | Project Execution Specification validation | 明确 project identity、app-private execution root、entrypoint、working directory、runtime kind、session/generation；拒绝 traversal/absolute relative target | unit-test source added；CI pending |
| R-15 | File-backed PROJECT A | SUCCEEDED/exitCode 0；project-local helper import；真实 __name__、__file__、cwd、sys.argv[0] | instrumentation source added；未执行 |
| R-16 | File-backed PROJECT B | FAILED；保留 stdout/stderr；exitCode 1；traceback 含真实 main.py，不含 <string> | instrumentation source added；未执行 |
| R-17 | File-backed PROJECT C cooperative STOP | RUNNING → STOPPED/exitCode 130；STOP diagnostics 保留；stdout/stderr cleanup 正确 | instrumentation source added；未执行 |
| R-18 | PROJECT D namespace/module isolation | 新 Session 不继承前一 Session 的 __main__ global 或 helper module | instrumentation source added；未执行 |
| R-19 | SystemExit mapping | SystemExit(0) → SUCCEEDED/0；SystemExit(7) → FAILED/7；Runtime 可继续 re-entry | instrumentation source added；未执行 |
| R-20 | Post-STOP project re-entry | STOP terminal cleanup 后新 session/generation 可再次执行 PROJECT A | instrumentation source added；未执行 |
| R-21 | alpha30 real-device acceptance | 不重启 App：PROJECT A → PROJECT B → PROJECT C RUNNING → STOPPED/130 → PROJECT D/E/F as needed → PROJECT A again；session IDs 不复用、generation 单调 | 待 alpha30 真机 |

alpha30 的 instrumentation tests 已加入源码，但当前 workflow 没有 Android emulator/device；没有将这些行标记为 real-device PASS。alpha29 H 节的真实设备证据仍是 accepted baseline，不被 alpha30 source/CI status 替代。
