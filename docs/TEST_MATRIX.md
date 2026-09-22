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

## R48a6.1 — Real Device Failure Repair（真机失败修复）

| ID | 验证项 | 期望 | 状态 |
|---|---|---|---|
| R48A6.1-01 | External operation deadline | PREPARE/START/STATUS/LOGS/STOP/CLEAN 都有 action-specific control deadline | PASS — unit + W0 #487 |
| R48A6.1-02 | Shared External watchdog | Activity 离开、重建或跨到另一 UI 不丢失 project+generation watchdog | PASS — unit/source + W0 #487 |
| R48A6.1-03 | PREPARE timeout recovery | 释放 operation lock；无旧 READY 时进入 `ENVIRONMENT_ERROR`，不写 READY | PASS — unit + W0 #487 |
| R48A6.1-04 | STOP timeout recovery | 释放 operation lock；进入 `UNKNOWN`，failure reason 为 `RUNTIME_OPERATION_TIMED_OUT:STOP`，不写 `STOPPED_BY_USER` | PASS — unit + W0 #487 |
| R48A6.1-05 | Late callback fencing | 旧 PREPARE/STOP executionId 不能修改新 generation | PASS — unit + W0 #487 |
| R48A6.1-06 | Project isolation | 项目 A timeout/STOP 不影响项目 B | PASS — unit + W0 #487 |
| R48A6.1-07 | Normal Mode diagnostics | 根布局可纵向滚动到完整当前 failure reason/页面底部 | PASS — source/cloud；待真机 |
| R48A6.1-08 | Internal Alpine scope | 不修改 Internal Alpine 安装逻辑；exit=4 保留为完整尾部待诊断 | PASS — scope + Alpine Probe #76 |
| R48A6.1-09 | R48a6 real-device acceptance | External PREPARE indefinite wait、External STOP indefinite wait、Normal non-scrollable diagnostic | FAIL — user-confirmed（旧 r48a6） |
| R48A6.1-10 | External Provider Preflight | 已确认 Termux、权限、allow-external-apps、bridge、READY 全部通过 | PASS — user-confirmed |
| R48A6.1-11 | Normal Mode External PREPARE progress | 复用现有 PrepareLiveProgressController，只读显示并可在 Activity 恢复后重挂接 | PASS — source/cloud；待真机 |
| R48A6.1-12 | Installable version | `0.8.0-alpha43-r48a6.1` / versionCode `187` / applicationId 不变 | PASS — W0 #487 APK badging |
| R48A6.1-13 | Cloud gate | validators + unit tests + assembleDebug + signing；Internal Alpine Probe | PASS — W0 #487 + Alpine Probe #76 |

R48a6.1 APK evidence（安装包证据）:
- functional source: `049e35f8cd32803f13dd33672f059406874b8a9c`
- artifact: `siftalpha-w0-487` / ID `10639401416`
- APK SHA-256: `ccf988663935e59c238c2805adb8fd6ed613f565e535c25467a2007fc35124af`
- signer SHA-256: `3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`
- Real-device repair acceptance: **PENDING（待真机）**

## R48a6 — Shared Core Realignment（共享核心重新对齐） / External Provider Preflight（外部执行环境前置检查）

| ID | 验证项 | 期望 | 状态 |
|---|---|---|---|
| R48A6-01 | 唯一源码起点 | `8c8ede3eff9a8c0cf4dcdea4d2fd67a7d808e8e2`，新分支 `codex/r48-shared-core-realignment` | PASS — source review |
| R48A6-02 | 版本 | `0.8.0-alpha43-r48a6` / versionCode `186` | PASS — source |
| R48A6-03 | 两个 UI（界面）共享 operation owner | Normal Mode 不直接创建 operation；两界面委托 `ProjectOperationCoordinator` | PASS — source + unit test |
| R48A6-04 | External Provider Preflight | Termux、RUN_COMMAND、桥探测和 READY 由共享服务归一化 | PASS — unit test |
| R48A6-05 | Prepare / Run 阻断 | 权限缺失返回 UI action；桥未就绪返回打开 Termux/setup action | PASS — unit test |
| R48A6-06 | 共享桥探测 | 复用 `TermuxBackend.CONNECTION_TEST`，结果进入共享 readiness store | PASS — unit test |
| R48A6-07 | STOP 项目隔离 | 项目 A STOP 不影响项目 B | PASS — unit test + existing contract |
| R48A6-08 | Environment 分离 | Provider Preflight 不替代 Project Environment Detection / Plan | PASS — source review |
| R48A6-09 | V04Activity 保护 | 不重写 Web Discovery、Auto Observation、Result Presentation、Recovery | PASS — focused diff |
| R48A6-10 | Internal R 保护 | Embedded CPython / Internal Alpine 不自动触发 Termux 或 provider 切换 | PASS — source review |
| R48A6-11 | W0 Cloud Build | validators、unit tests、assembleDebug、APK evidence、stable signing | PASS — [Run #475](https://github.com/kuashan/siftalpha-one/actions/runs/35592554128), artifact `siftalpha-w0-475`, APK SHA-256 `7c0cdfbaca6c799c92684f3da7f1379c3dad70303a3d98820abb1409b227406e` |
| R48A6-12 | Internal Alpine Probe | R48a6 分支探针通过 | PASS — [Run #75](https://github.com/kuashan/siftalpha-one/actions/runs/35591474623) |
| R48A6-13 | 真机验收 | 安装 APK，验证权限、打开 Termux、桥探测、Prepare/Run/STOP 和跨界面一致性 | FAIL — External PREPARE/STOP indefinite wait；Normal long diagnostic 不可滚动 |

## A. 当前 alpha43 版本信息

- 当前产品基线版本：`0.8.0-alpha43`
- versionCode：`119`
- 产品名称：`Automatic Project Observation + Contextual Status Guidance`
- 当前文档/验收分支：`codex/siftalpha-x-embedded-cpython-spike`
- Current source baseline HEAD：`66f9153e57547c4d8b6e50956b48ddf86b9dc656`
- Parent HEAD：`d92e1802b983718280cdd303d56c9293c22a8425`
- 最近 CI：GitHub Actions Run #121 / Run ID `35226167054` / conclusion `success`
- Artifact：`siftalpha-w0-121`
- APK SHA-256：`6b70fc222cc8e27124daf2a5a910abc1174380b257c5549959abb279d25def6a`
- 当前基线记录的是 M-facing 项目观察、状态引导与结果呈现能力；不代表 Embedded R 或完整 M/R/X 已完成。
- `main` 未在本轮修改；旧版本与历史验证证据继续保留在本矩阵的历史区段和 `docs/DEV_LOG.md`。

### Historical alpha32 / alpha30 reference

- 当前 Runtime prototype 版本：`0.8.0-alpha32`
- versionCode：`108`
- alpha32 目标：M-managed SAF Python project → bounded app-private staging → explicit Embedded R execution
- 包名：`com.siftalpha.studio`
- 当前 docs/runtime prototype branch：`codex/siftalpha-x-embedded-cpython-spike`
- alpha30 implementation starting HEAD：`36b0175248a491b85560d68fcfd12889dbf5d0d7`
- alpha30 source HEAD：`328ff10222a4fb188d5932130a16c04a3c0ccabb`
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
| C-16 | alpha30 R Project Script Execution Boundary CI | CPython preparation、validators、JVM unit tests、CMake/native build、`assembleDebug`、APK evidence 和 signing verification 通过；无 Android device | PASS（CI；instrumentation 未执行） | [Actions Run #83](https://github.com/kuashan/siftalpha-one/actions/runs/35078323509) / [artifact `siftalpha-w0-83`](https://github.com/kuashan/siftalpha-one/actions/runs/35078323509/artifacts/10439551021) |

| C-17 | alpha43 Final Source Baseline CI | repository validators, testDebugUnitTest, assembleDebug, CPython preparation, native/CMake, APK signing/evidence | PASS | [Actions Run #121](https://github.com/kuashan/siftalpha-one/actions/runs/35226167054) / source HEAD `66f9153e57547c4d8b6e50956b48ddf86b9dc656` / artifact `siftalpha-w0-121` / APK SHA-256 `6b70fc222cc8e27124daf2a5a910abc1174380b257c5549959abb279d25def6a` |
| C-18 | alpha43 Baseline Closure Documentation CI | repository validators, testDebugUnitTest, assembleDebug, CPython preparation, native/CMake, APK signing/evidence | PASS | [Actions Run #122](https://github.com/kuashan/siftalpha-one/actions/runs/35242617391) / HEAD `860645af9745ebbb978dad6d177359f5fddb23f1` / conclusion `success` / artifact `siftalpha-w0-122` / APK SHA-256 unchanged: `6b70fc222cc8e27124daf2a5a910abc1174380b257c5549959abb279d25def6a` |

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
| R-14 | Project Execution Specification validation | 明确 project identity、app-private execution root、entrypoint、working directory、runtime kind、session/generation；拒绝 traversal/absolute relative target | JVM/CI verified（Run #83；Run #90 build success）；instrumentation 未执行 |
| R-15 | File-backed PROJECT A | SUCCEEDED/exitCode 0；project-local helper import；真实 __name__、__file__、cwd、sys.argv[0] | REAL-DEVICE PASS（Generation 1；Generation 4 re-entry） |
| R-16 | File-backed PROJECT B | FAILED；保留 stdout/stderr；exitCode 1；traceback 含真实 main.py，不含 <string> | REAL-DEVICE PASS（Generation 2；intentional failure） |
| R-17 | File-backed PROJECT C cooperative STOP | RUNNING → STOPPED/exitCode 130；STOP diagnostics 保留；stdout/stderr cleanup 正确 | REAL-DEVICE PASS（Generation 3） |
| R-18 | PROJECT D namespace/module isolation | 新 Session 不继承前一 Session 的 __main__ global 或 helper module | instrumentation source added；未执行 |
| R-19 | SystemExit mapping | SystemExit(0) → SUCCEEDED/0；SystemExit(7) → FAILED/7；Runtime 可继续 re-entry | instrumentation source added；未执行 |
| R-20 | Post-STOP project re-entry | STOP terminal cleanup 后新 session/generation 可再次执行 PROJECT A | REAL-DEVICE PASS（Generation 3 STOP → Generation 4 re-entry） |
| R-21 | alpha30 real-device acceptance | 不重启 App：PROJECT A → PROJECT B → PROJECT C RUNNING → cooperative STOPPED/130 → PROJECT A again；new Session IDs 不复用、generation 1 → 2 → 3 → 4 单调 | REAL-DEVICE PASS（Run #90；tested fixture scope） |

alpha30 的 instrumentation tests 已加入源码；Run #83 CI 成功，Run #90 也完成 CI/build/artifact 验证，但 workflow 没有 Android emulator/device，因此 instrumentation 仍未执行。下方新增的 alpha30 real-device acceptance 仅依据用户提供的真实 Android 设备证据；没有把 source 或 cloud build 写成设备 PASS。alpha29 H 节的历史证据仍作为 accepted baseline 保留。


| R-22 | Missing file-backed entrypoint | staging root 中缺失显式 entrypoint 时，R 发布 FAILED/exitCode 1 和明确 entrypoint error；不 fallback | instrumentation source added；未执行 |


### I.1 alpha30 Real-Device Acceptance Evidence

证据类别：**REAL-DEVICE EVIDENCE**。以下结果来自同一 Android application process 的连续操作，不是 CI/JVM 推断；instrumentation source 在当前 workflow 中未执行。

构建身份：版本 `106 / 0.8.0-alpha30`；branch `codex/siftalpha-x-embedded-cpython-spike`；source HEAD `0e2b069d93a0a8cd87df4f57f0e97da1cf918643`；Run #90 / ID `35083943639`；artifact `siftalpha-w0-90` / ID `10441785815` / digest `sha256:afc012248071e5b43884aa8985ee07e94a4e23e1f7ecf4a5c4a960f01ff228da`；APK `app-debug.apk` / SHA-256 `d9bdeac5a0df867cc52b5b71226e90a560a831a3097e9341af0f2e1898e2a547`。

| 连续步骤 | 真实设备观察 | 结论 |
|---|---|---|
| Generation 1 — PROJECT A | `SUCCEEDED` / `TERMINAL` / STOP `IDLE/NONE` / exit 0；CPython 3.14.7、Android/aarch64、`TERMUX=NOT_USED`、`PROOT=NOT_USED`；project marker、helper import、`__name__=__main__`、真实 staged `__file__`、argv[0]、cwd、stdlib json 均确认；旧 `/data/user/0` symlink-component error 未复现 | PASS |
| Generation 2 — PROJECT B | `FAILED` / `TERMINAL` / STOP `IDLE/NONE` / exit 1；stdout/stderr marker 保留；traceback 指向真实 `main.py` 第 8 行并以预期 `RuntimeError: SIFTALPHA_X_TEST_B_FAILURE` 结束 | PASS（intentional failure） |
| Generation 3 — PROJECT C running | `RUNNING` / `RUNTIME_PHASE=PYTHON_EXEC_BEGIN`；STOP `IDLE/NONE` | PASS（STOP 前置条件） |
| Generation 3 — cooperative STOP | 同一 Session/generation；`STOPPED` / `TERMINAL` / `STOP_REQUEST_RETURNED` / `INTERRUPT_DELIVERED` / exit 130；stdout 含 started、real project file、ticks 和 cooperative-stop marker；stderr `SIFTALPHA_X_STOP=COOPERATIVE`；无普通 FAILED traceback | PASS |
| Generation 4 — PROJECT A re-entry | STOP 后不重启 App process；new Session；`SUCCEEDED` / `TERMINAL` / STOP `IDLE/NONE` / exit 0；再次确认 CPython 3.14.7、Android/aarch64、project-local import、真实文件语义、正确 cwd、stdlib json、空 stderr | PASS |

正式结论：**SiftAlpha R Embedded CPython alpha30 Project Script Execution Boundary real-device acceptance = PASS（仅针对 app-private、file-backed、pure-Python fixture 测试范围）**。这不表示 R 或 X 已完成。

本证据仍不证明：arbitrary external/SAF project import、pip/dependencies/venv、arbitrary third-party packages/native wheels、arbitrary C extensions、blocking native/syscall hard-stop、concurrent Sessions、process-death recovery、production M ↔ R integration、R Web Discovery/Browser integration 或完整 filesystem sandbox。Android `/data/user/0/...` 与 `/data/data/...` 可能是不同字符串表示；当前结论只依赖 canonical containment 的 app-private staging 语义。

## J. alpha32 M → R Embedded CPython First Integration

本轮新增的验证边界是：

`SAF Project documentId → bounded app-private staging → explicit entrypoint → EmbeddedPythonSession → structured Snapshot → M RuntimeState/output/STOP`。

### J.1 JVM/source checks

- `EmbeddedPythonEntrypointPolicyTest`：覆盖显式嵌套入口、确定性 conventional entry、歧义入口和不安全/缺失入口。
- `EmbeddedPythonProjectStagerTest`：覆盖相对目录复制、helper 文件、unsafe relative path、文件数/单文件/总大小限制和 SAF 读取失败后的 partial staging cleanup。
- `EmbeddedPythonRuntimeStateMappingTest`：覆盖 IDLE/STARTING/RUNNING/SUCCEEDED/FAILED/STOPPED 的结构化映射、Project/Session/Generation 与 stdout/stderr 保留。
- `EmbeddedPythonExecutionSpecTest` 与既有 R lifecycle tests：继续覆盖路径、SystemExit、namespace、STOP 和 re-entry 回归。
- Android instrumentation source 增加 explicit project execution input 测试；没有 Android device 时不得将其写成 executed/pass。

### J.2 alpha32 行为边界

- Embedded R 只能通过 M 的显式 opt-in 入口启动已解析为 Python 的项目。
- R 不扫描或猜测入口；M 无法得到唯一安全入口时不启动。
- SAF source 与 app-private staging root 分离；staging 有界且失败清理。
- Termux provider、RUN_COMMAND、PRoot、Ubuntu 和生产 M path 保持既有行为。
- 本节的 CI 与真实设备状态以提交后的实际执行证据为准；source/CI readiness 不等于 real-device acceptance。


## alpha42 — Rich Result Presentation + Unified Open

- `RichResultParserTest`：覆盖强格式 Link List、顺序、URL 去重、中文标签、ANSI/OSC 清理、代理/帮助/Web 日志误识别、非 HTTP(S) scheme 和 200 项上限。
- `PresentationTargetResolverTest`：覆盖 WEB 优先、Rich Result 回退、NONE，以及 Raw Log 不成为打开目标。
- `RichResultLifecyclePolicyTest`：覆盖新 START 清除、空 STATUS/LOGS 保留和后续结果更新。
- 生产接线覆盖 M 的安全结果输出、Termux 与 Embedded snapshot 的结果识别、项目卡片结果计数和统一“打开”；现有 Web Discovery/Endpoint Probe/Browser 测试继续作为回归。
- Rich Result Viewer 为原生 Android UI；外部 URL 二次通过 http/https 与 Android 浏览器目标检查。没有真实 Android device 的 CI 不能替代 Sherlock/Web 真机验收。


## alpha43 — Automatic Project Observation + Contextual Status Guidance

| Area | alpha43 coverage |
|---|---|
| Automatic Observation | Activity 前台期间以低频 STATUS 为主；终态自动执行一次最终 LOGS，用于 Rich Result 与诊断。后台暂停观察但不停止 Runtime。 |
| Contextual Status Guidance | 用户主要状态说明来自当前真实事实；`EXITED_SUCCESS`、`STOPPED_BY_USER`、长期 RUNNING/Web Ready 语义分别呈现。 |
| Web readiness | `RUNNING` 不等于 Web Ready；只有 Candidate 加 Endpoint Probe 验证真实可达后才开放 Web 与 Unified Open。 |
| Rich Result / Unified Open | 终态自动发现 Rich Result；Unified Open 按 `WEB > RICH_RESULT > NONE` 路由；Viewer 有明确可见的返回按钮。 |
| Automatic LOGS boundary | Web discovery 的 LOGS 按每 3 次 STATUS 一次、最多 3 次执行；终态只读一次最终 LOGS，不是后台无限刷新。 |
| CI evidence | Run #121 是 alpha43 final production-source CI；Run #122 是 Baseline Closure Documentation CI；两次均 success，APK SHA-256 unchanged。 |
| Real-device boundary | 下表记录真实 Android 真机验收；alpha43 已完成并通过。 |

### alpha43 real-device acceptance

| ID | Scenario | Result | Evidence |
|---|---|---|---|
| RD-43-01 | Sherlock one-shot: START → automatic observation → EXITED_SUCCESS → automatic final LOGS → Rich Result → Open | PASS / real-device verified / user-confirmed | 一次点击运行；无需手动 STATUS 或 LOGS；最终产生 16 项结果。 |
| RD-43-02 | `EXITED_SUCCESS` presentation | PASS / real-device verified | 最终显示“状态：已正常结束”，不显示“已停止”。 |
| RD-43-03 | `STOPPED_BY_USER` distinction | PASS / real-device verified / user-confirmed | 明确 STOP 与自然完成语义不同；只有明确 STOP 代表用户停止。 |
| RD-43-04 | Rich Result Viewer / Unified Open / visible Back | PASS / real-device verified | Unified Open 进入 Viewer；“← 返回”可见；返回项目卡后状态正确。 |
| RD-43-05 | Long-running Web delayed readiness | PASS / real-device verified | RUNNING 且 Web 暂不可用是正常过渡；Server 监听并完成 Endpoint Probe 后自动变为可打开。 |
| RD-43-06 | Web endpoint safety | PASS / real-device verified | Candidate 不等于 Ready；只有真实可达 Endpoint Probe 成功后开放 Web/Open。 |
| RD-43-07 | External browser | PASS / real-device verified | Verified Web 在外部浏览器打开；`/api/articles`、`/api/globe-data`、`/api/stats` 真实返回 HTTP 200。 |
| RD-43-08 | Browser exit does not stop Runtime | PASS / real-device verified / user-confirmed | 关闭或离开浏览器后长期 Runtime 继续运行。 |
| RD-43-09 | App leave/re-entry does not stop long-running Runtime | PASS / real-device verified / user-confirmed | 离开 SiftAlpha 后重新进入，Runtime 仍运行并可重新识别；后台只暂停观察。 |
| RD-43-10 | Automatic observation | PASS / real-device verified / user-confirmed | 普通用户只需点击一次运行，不需要手动 STATUS 或 LOGS。 |
| RD-43-11 | Automatic LOGS bounded behavior | PASS / source + unit + behavior evidence | Web discovery 最多 3 次有界 LOGS probe；终态一次 final LOGS；不声称后台无限刷新。 |

**alpha43 real-device acceptance = PASS**

### alpha43-r18 Internal Runtime Reliability Repair

- Internal Alpine Web Discovery: source change adds project PID-tree/socket-inode observation and reuses the existing endpoint probe; real-device validation is pending.
- Internal Alpine network audit: `refreshDns()` and the PRoot guest environment were reviewed; no SiftAlpha network defect is proven from the available OCI disconnect evidence, so no network code was changed.
- External Runtime r17 production code: unchanged.
- JVM coverage: explicit URL/log compatibility, empty-stdout scoped listener discovery, unrelated-PID exclusion, no-listener no-candidate, and stale session/generation fencing.
- W0 Cloud Build / Internal Alpine Probe: pending for the final HEAD.
- Real-device Internal matrix: pending. Required first checks are `primary:AcodeProjects/situation-monitor` Web discovery and `primary:AcodeProjects/OCI` network observation.


### alpha43-r20 Internal Alpine Shared Memory Compatibility

- r19 real-device result: FAIL for `primary:AcodeProjects/situation-monitor` before Web availability; Flask reached server setup but CPython 3.12 sharedctypes terminated with `FileNotFoundError: /dev/shm`.
- Expected r20 behavior: every Internal Alpine command sees an app-private writable directory at guest `/dev/shm`, including project execution and environment preparation.
- JVM regression: verify base bind order keeps Android `/dev` while overlaying app-private storage specifically at `/dev/shm`.
- External Runtime production code: unchanged.
- W0 Cloud Build / Internal Alpine Probe / APK: pending for final r20 HEAD.
- Real-device acceptance: pending. First retest is the same `primary:AcodeProjects/situation-monitor` launch; expected result is no `/dev/shm` traceback, process remains RUNNING, then existing Web Discovery + Endpoint Probe may proceed.


### alpha43-r21 Internal Background Survival + Stable Web Presentation

- Internal Alpine long-running session: foreground-service lease must be acquired after launch and released on the exact session's terminal transition.
- Multi-session isolation: releasing one session lease must not remove foreground protection for sibling Internal sessions.
- External Runtime / Termux: unchanged.
- Web presentation: a previously verified Web execution remains PresentationTarget.WEB while foreground endpoint revalidation is pending; Rich Result must not temporarily replace it.
- Endpoint truth remains strict: AVAILABLE still requires a fresh current-lifecycle probe; retained/persisted verification only stabilizes presentation identity.
- Real-device acceptance: start `primary:AcodeProjects/situation-monitor`, open in the configured external browser, refresh after 30 seconds and 2 minutes, return to SiftAlpha, confirm Web stays Web during DETECTING/AVAILABLE, then STOP and rerun.


### alpha43-r22 Fast Web Detection

- Internal Alpine: bounded PID/socket/procfs observation must execute off the Activity main thread.
- Internal Alpine: once a current-execution Web candidate exists, heavy listener discovery must stop; endpoint health remains on the lightweight probe path.
- Endpoint startup verification: first misses retry at 150/300/600 ms and remain DETECTING; after the bounded burst, a still-closed endpoint becomes UNAVAILABLE and uses the normal 2-second cadence.
- External Runtime: Web discovery observation may run at 500 ms only while the bounded discovery budget is useful; after verification/budget exhaustion it must return to 2 seconds.
- Rich Result: Web DETECTING with a valid Rich Result still resolves Open to RICH_RESULT; empty STATUS/LOGS observations must preserve the current Rich Result.
- Existing Web+Rich Result priority remains unchanged: verified Web wins only after Web presentation identity is known.
- Real-device Internal acceptance: run `primary:AcodeProjects/situation-monitor` and measure transition from listener startup to Web AVAILABLE; Open/refresh/background survival from r21 must remain passing.
- Real-device External acceptance: start a known Web project through Termux and confirm faster initial Web discovery without changing START/STOP/STATUS/LOGS semantics.


### alpha43-r23 Ownership-Verified Web Hints

- Hint order: project-detected port > framework default > bounded common port set.
- Hints must never directly create Web availability or presentation identity.
- Internal hint fast path must accept a hinted LISTEN port only when its socket inode belongs to the exact current project PID tree/session/generation.
- A common port owned by another project must be rejected even when it has higher hint priority.
- Internal no-match path must fall back to the existing bounded full PID/socket discovery.
- External dynamic hints must only reorder candidates already discovered inside the current project PID/PGID or verified PRoot guest identity scope.
- Unknown External projects may use bounded procfs hint discovery without enabling weak Runtime-log URL discovery.
- Explicit/configured local URLs are hints only; Android endpoint verification starts only after Runtime ownership discovery publishes the candidate.
- Rich Result parser/lifecycle/viewer remain unchanged; hint-triggered LOGS must not suppress Rich Result inspection or erase an existing result.
- No global process scan and no port-range scan.
- Real-device Internal: verify `situation-monitor` reaches Web AVAILABLE faster and still survives external-browser backgrounding.
- Real-device isolation: while another project owns a common port, start a different project and confirm SiftAlpha never opens the other project's page.
- Real-device Rich Result: run a Rich Result project with no verified Web endpoint and confirm unified Open still opens Rich Result.


### alpha43-r24 Web Runtime Continuity

- Internal Alpine empty discovery must retry at 150/300/600/1000 ms, then 2 seconds, even when the Embedded snapshot itself is unchanged.
- Internal retry state must be fenced by session ID/generation and cannot publish an older execution's result.
- Once a current-run candidate exists, heavy Internal discovery retries must stop.
- A verified Web endpoint must remain AVAILABLE when the Activity returns to foreground while a fresh probe runs silently.
- One fresh failure after a previously verified endpoint must not downgrade presentation; two consecutive fresh failures may downgrade it.
- Activity recreation may restore current-execution verified identity from `RuntimeWebStateStore`, but that restored identity still triggers a fresh probe.
- Cross-run learned endpoint memory must accept only PID_SOCKET + Android-verified loopback endpoints with an explicit valid port.
- Runtime-log, configured-only, credential-bearing, external-host and no-port URLs must never become learned endpoints.
- Learned endpoint port must outrank project/framework/common hints but must still pass current-run ownership verification before becoming a candidate.
- New START clears current execution Web candidate/verification but must not clear learned endpoint memory.
- Embedded CPython must acquire an Internal Runtime foreground lease after native START acceptance and release only for its terminal/replaced session; blank IDLE snapshots must not prematurely release it.
- Internal Alpine and CPython leases must remain independent; project-scoped STOP must not drop a sibling session lease.
- Foreground-service start failure must roll back the attempted lease.
- Rich Result production behavior remains unchanged and remains usable when Web is not currently verified.
- Real-device situation-monitor: verify no permanent DETECTING after an early empty discovery, verify fast availability, external-browser background survival, and stable AVAILABLE when returning to SiftAlpha.
- Real-device OCI: test both reported Internal engines; background SiftAlpha + browser, use another app, then return and verify Runtime survival and silent Web revalidation.
- Real-device learned endpoint: after one ownership-verified successful Web run, STOP/START and confirm the previous port is tried first but a changed project port is rediscovered and replaces it safely.


### alpha43-r28 r24 + wake-only acceptance

- Web discovery/status behavior must match r24; no SEARCHING -> LISTENER_FOUND -> STARTING_WEB -> AVAILABLE four-stage presentation is present.
- No RuntimeWebHttpReadinessProbe is used.
- No unified render.yaml / Procfile / package.json scripts.start / pyproject.toml command takeover is present.
- Active Internal Runtime sessions retain Foreground Service + PARTIAL_WAKE_LOCK protection.
- STOP releases only the current project/session lease and must not stop sibling projects.
- Re-test OCI and situation-monitor Web behavior against the r24 baseline, then separately test background execution continuity.


### alpha43-r29 first-layer background execution test

- Baseline: r24 product/Web behavior must remain unchanged.
- Before Internal Runtime launch, diagnostics/implementation must require an active session lease, foreground-service state and held PARTIAL_WAKE_LOCK.
- Internal Alpine and Embedded CPython must not launch first and acquire foreground protection afterward.
- Service-ready wait is bounded; failure must roll back the new lease and must not leave a phantom active lease.
- Internal Alpine copied logs expose:
  - SIFTALPHA_X_FGS_ACTIVE
  - SIFTALPHA_X_WAKE_LOCK_HELD
  - SIFTALPHA_X_RUNTIME_LAUNCH_AFTER_FGS
  - SIFTALPHA_X_SERVICE_PID
  - SIFTALPHA_X_RUNTIME_PID
  - SIFTALPHA_X_RUNTIME_PID_ALIVE
  - SIFTALPHA_X_RUNTIME_CPU_TICKS_START / NOW
  - SIFTALPHA_X_FGS_HEARTBEAT_EPOCH_MS / AGE_MS
- Real-device OCI acceptance: start OCI in Internal Runtime, confirm retries are progressing, background SiftAlpha for 5-10 minutes, return and copy logs. Compare heartbeat freshness, Runtime PID liveness, CPU ticks and OCI application log progress.


### alpha43-r30 project cache + Internal Web wiring acceptance

- r24 Web/product behavior remains the baseline; r29 foreground-ready Runtime ordering remains intact.
- First r30 load may populate caches from SAF. Subsequent ordinary Activity/runtime/Web refreshes must reuse cached project/configuration/Web inspection facts.
- Explicit "刷新项目" must bypass and rewrite all three inspection caches.
- A selected-root URI change must make old cached entries ineligible.
- Runtime PREPARE/START continues to read authoritative current project facts rather than trusting UI cache content.
- Internal Alpine current-session stdout containing SIFTALPHA_WEB_URL=http://127.0.0.1:<port>/ must publish that URL as a candidate even when PID/socket observation returns NO_CANDIDATE.
- Browser/Open remains disabled until the existing Android Endpoint Probe verifies the loopback listener.
- No SEARCHING/LISTENER_FOUND/STARTING_WEB/AVAILABLE four-stage presentation is reintroduced.
- Real-device OCI test: with OCI RUNNING and logging SIFTALPHA_WEB_URL, Web should progress from detection to the normal r24 available/open state once 127.0.0.1 listener verification succeeds.

### alpha43-r31 Internal Runtime Foreground Ownership acceptance

- r24 Web/product behavior and r30 cache/Web wiring remain unchanged.
- Internal Alpine launch occurs through the active InternalRuntimeForegroundService ownership executor after the r29 foreground-ready + PARTIAL_WAKE_LOCK barrier.
- Active Internal Alpine diagnostics must report RUNTIME_OWNER=FOREGROUND_SERVICE, matching SESSION_OWNER_SERVICE_PID, RUNTIME_PROCESS_HELD=YES, RUNTIME_MONITOR_ACTIVE=YES, RUNTIME_PID_ALIVE=YES and a non-negative RUNTIME_CPU_TICKS_DELTA.
- Service-owned waitFor() publishes SUCCEEDED/FAILED/STOPPED back into the existing session record and releases only that session lease.
- STOP routing requires exact project identity + session ID and cannot stop sibling Internal Alpine/CPython sessions or External Runtime.
- No Worker, supervisor, runtime gate, WifiLock, four-stage Web state, unified Project Start Contract, global PID scan or port-range scan is introduced.
- Real-device OCI: confirm CATCHER_HEARTBEAT/LAUNCH_ATTEMPT progress, background SiftAlpha for at least 10-15 minutes, return without restarting, refresh logs, and verify attempts continued throughout.
- If ownership/PID/CPU remain healthy but network attempts stop, classify r31 as PARTIAL/FAIL and isolate the next investigation to Network Background Continuity.

### alpha43-r32 Runtime Center live-output stability acceptance

- Embedded R snapshot reads execute off Android's main thread; result presentation remains on the UI thread.
- The 180 ms lifecycle observation cadence may continue, but active stdout/stderr rendering is bounded to at most once per 750 ms unless a manual STATUS/LOGS action, structural Runtime transition or terminal state requires immediate presentation.
- stdout/stderr-only growth must not trigger a Runtime Center project-card rebuild.
- Runtime state/session/generation/runtime-phase/stop-phase/terminal changes must still rebuild the card when controls or labels can change.
- Expanded project output must preserve follow-tail behavior without a visible custom-scrollbar top-to-bottom flash. If the user scrolls upward, a later card refresh must restore the nested position rather than reset to the top.
- STOP must give immediate UI feedback and perform the potentially blocking Internal Runtime stop request off the touch/main thread.
- STATUS and Refresh Logs must not perform Internal snapshot/environment reads synchronously on the touch/main thread.
- Real-device OCI: keep a large live log active for at least 5 minutes, interact with the output scrollbar and exercise STATUS/Refresh Logs/STOP. No first-tap loss, repeated card flicker or sustained UI freeze is acceptable.
- r31 Runtime ownership diagnostics and the separate 10-15 minute background-continuity acceptance remain required; r32 CI success is not REAL_DEVICE_PASS.

### alpha43-r33 Background Web Continuity telemetry acceptance

- Foreground Service telemetry sampling must continue independently of Runtime Center Activity lifecycle, on the existing 15-second service heartbeat cadence.
- Each active Internal Alpine session must persist bounded background samples containing:
  - BACKGROUND_SAMPLE_EPOCH_MS
  - BACKGROUND_SERVICE_IMPORTANCE / SERVICE_PROC_STATE / SERVICE_CGROUP
  - BACKGROUND_RUNTIME_PID / PID_ALIVE / PROC_STATE / RUNTIME_CGROUP
  - BACKGROUND_RUNTIME_CPU_TICKS / CPU_TICKS_DELTA
  - BACKGROUND_STDOUT_BYTES / STDOUT_MTIME_MS
  - BACKGROUND_WEB_URL / WEB_LOOPBACK_REACHABLE
  - BACKGROUND_WAKE_LOCK_HELD
  - BACKGROUND_POWER_SAVE_MODE / DEVICE_IDLE_MODE / BATTERY_OPTIMIZATION_IGNORED
- Only validated loopback SIFTALPHA_WEB_URL values may be probed; arbitrary external URLs must not be probed.
- Telemetry must be diagnostic-only: no process restart/supervision, Worker, new execution layer, widened STOP, WifiLock, Web four-stage state or HTTP readiness-state restoration.
- Unit tests must cover proc-state/CPU-tick parsing and latest validated loopback URL selection.
- Real-device same-phone test: keep browser on the Runtime localhost page, put SiftAlpha in background for 5-10 minutes, observe the page stall, return to SiftAlpha without rerunning, refresh logs and copy the Background Continuity History.
- Interpretation:
  - CPU tick delta ~0 + stdout unchanged + Web unreachable while service samples continue => Runtime child scheduling/freeze path.
  - CPU ticks/stdout continue + Web unreachable => HTTP listener/Web-server path.
  - CPU ticks/stdout continue + Web reachable while browser UI appears stale => browser/frontend refresh path.
  - Battery optimization/idle evidence is supporting context, not by itself proof of root cause.

### alpha43-r34 Internal Web stderr fallback acceptance

- r24 remains the functional Web baseline; existing Internal Alpine PID/socket discovery, retry cadence, Endpoint Probe and presentation semantics must remain unchanged.
- For a confirmed Web project, a local Flask-style stderr line such as `Running on http://127.0.0.1:5001` may become a RUNTIME_LOG candidate.
- A generic localhost URL on stderr must remain rejected when static Web capability is not enabled.
- PID_SOCKET evidence must outrank weaker Runtime-log evidence; an explicit `SIFTALPHA_WEB_URL` must outrank generic Runtime-log evidence.
- A stderr-derived candidate remains only a candidate and must pass the existing Android loopback Endpoint Probe before AVAILABLE/Open presentation.
- Situation-monitor real-device test: start with Internal Alpine, allow the initial feed fetch to finish, and verify Web changes from DETECTING to AVAILABLE without manual log refresh or project restart.
- If situation-monitor remains DETECTING, copy full logs and verify whether stderr contains the Flask bound URL, whether the candidate reaches RuntimeWebStateStore, and whether the Endpoint Probe completes. CI success is not REAL_DEVICE_PASS.

### alpha43-r35 CLI Launch Configuration acceptance

- Static argparse:
  - `add_argument("MARKET")` and `add_argument("CODE")` are detected as required positional CLI arguments in declaration order.
  - `add_argument("--market", required=True)` is detected as a required valued option.
  - optional options, boolean/count actions, dynamic declarations and repeated nargs are not guessed.
- Project Config:
  - required CLI inputs are displayed separately from environment variables/secrets;
  - the summary must not claim there is no required configuration when CLI requirements exist;
  - CLI values are not injected or saved as environment variables.
- Runtime diagnosis:
  - argparse `the following arguments are required: MARKET, CODE` produces project-scoped CLI hints;
  - ordinary exit-code-2 failures without this evidence are not reclassified as CLI configuration.
- External Runtime:
  - existing Python-file/console-script argv path remains functional;
  - required static CLI fields are collected before START.
- Internal Alpine:
  - Python-file arguments are shell-quoted as distinct argv values;
  - no eval, command concatenation with unquoted user data, global port/PID scan, Worker or STOP-scope change is introduced.
- Embedded CPython:
  - execution spec bounds argument count/length and rejects NUL;
  - native execution publishes `sys.argv[0] = entrypoint` and appends each supplied argument as one Python argv item.
- Internal terminal failure:
  - one high-confidence configuration diagnosis is allowed per session/generation so a missing-argument failure cannot create repeated dialogs on every poll.
- Regression:
  - r34 Web discovery, foreground ownership, background telemetry, Runtime Center stability and project-scoped STOP semantics remain unchanged.
- Cloud requirement: repository validators, unit tests and `assembleDebug` must pass before APK release. Real-device test must run the affected project with values for `MARKET` and `CODE`.

### alpha43-r36 Click/Typer CLI discovery acceptance

- Static discovery:
  - Click `@click.argument("market")` and `@click.argument("code")` are recognized as required positional launch inputs.
  - Click `required=False` remains non-blocking and is not promoted.
  - Typer `name: str = typer.Argument(...)` is recognized as a required positional input.
  - argparse coverage from r35 remains unchanged.
- Runtime diagnostics:
  - `Usage: run_all_strategies.py [OPTIONS] MARKET CODE` + `Error: Missing argument 'MARKET'.` yields `MARKET` and `CODE` in order.
  - `Missing option '--region'` is retained as a required option token.
  - generic Click `COMMAND [ARGS]...` metadata is not turned into project arguments.
- File selection:
  - likely top-level launchers (main/app/cli/__main__/run*/start*) are inspected before unrelated Python modules under the existing bounded scan.
- Cache:
  - r36 must not reuse the r35 v2 configuration profile cache; the first r36 inspection writes a fresh v3 profile.
- Real-device easy_tdx-main acceptance:
  - Project Config must show two required CLI launch arguments instead of claiming no configurable items.
  - Run must present MARKET and CODE fields.
  - Supplying valid values must launch with argv and eliminate the missing-MARKET exit-code-2 failure.
- Regression:
  - r34 Web, r31 ownership, r32 UI stability, r33 telemetry, r35 argv execution, project-scoped STOP and External Runtime behavior remain unchanged.

### alpha43-r37 entry-bound CLI launch acceptance

- When required CLI evidence for MARKET/CODE points to `run_all_strategies.py` and pyproject also declares `easy-tdx`, launch resolution must select `PythonFile("run_all_strategies.py")`.
- CLI evidence from a different Python file must not override a console-script resolution.
- Runtime-only CLI hints without a file path must not guess a new entrypoint.
- easy_tdx-main real-device acceptance: final launch must report PYTHON_FILE / `run_all_strategies.py`, pass MARKET and CODE as argv, and must not execute `easy-tdx MARKET CODE`.
- r34 baseline behavior, project-scoped STOP, Web discovery, Internal ownership and External Runtime semantics remain unchanged.

### alpha43-r38 Local Result Web Host acceptance

- Output extraction:
  - Runtime/Web/identity envelope markers are removed from the user result.
  - Program stdout remains byte-order/line-order faithful after ANSI control sanitization.
  - explicit Python launch entrypoint is retained as result source evidence.
- Adaptive analysis:
  - repeated key/value output becomes metric cards;
  - easy_tdx-style whitespace performance tables become HTML tables;
  - CSV/TSV tables are recognized generically;
  - date/numeric x-axis tables with >=3 points and numeric value columns produce inline SVG curves;
  - ranking tables do not become misleading time-series curves;
  - unknown output always falls back to readable preformatted text.
- Source-aware presentation:
  - bounded entrypoint source inspection may increase confidence for table/chart/CSV/JSON/Markdown presentation;
  - source hints must never synthesize missing values or override actual Runtime output.
- Security/privacy:
  - Result Web Host binds to 127.0.0.1 only;
  - user-facing URL uses localhost;
  - generated result HTML is stored only in app-private storage;
  - HTML program content is escaped;
  - WebView JavaScript/file/content access is disabled;
  - only GET/HEAD and safe result IDs are served.
- Lifecycle:
  - repeated final LOGS with identical program output do not create duplicate results;
  - new START hides the previous result from the active card until the new run succeeds;
  - app restart can reopen the latest persisted result after the localhost host starts on demand.
- Presentation priority:
  - verified project Web > SiftAlpha Result Web > legacy Rich Result > none.
- Regression:
  - r37 CLI entry binding/argv behavior remains unchanged;
  - r34 Web baseline, r31 ownership, r32 Runtime Center stability, r33 telemetry and project-scoped STOP remain unchanged.
- Cloud: repository validators, `testDebugUnitTest`, native build and `assembleDebug` must pass.
- Real-device: rerun easy_tdx-main; after `EXITED_SUCCESS`, project card should show Result Web ready and Open should display formatted sections/metrics/tables instead of requiring Raw Log reading.



### alpha43-r39 Android system-bar inset acceptance

- On Android 15+ edge-to-edge devices, the Result Web action row (Back / Copy link / Open in browser) must render fully below the status bar and any display cutout.
- Existing Result Web URL text and WebView content must remain below the action row without overlap.
- Legacy View roots must preserve their existing app padding while adding left/top/right system-bar/display-cutout insets.
- Bottom padding must preserve the navigation-bar inset when the IME is hidden and use the larger IME obstruction when the keyboard is visible; navigation and IME insets must not be double-counted.
- Android 14-and-earlier View layout behavior remains unchanged.
- Regression: r38 adaptive result extraction/rendering, r37 CLI entry binding/argv, r34 Web truth gates, Runtime ownership and project-scoped STOP remain unchanged.
- Cloud: repository validators, unit tests and assembleDebug must pass before APK release.
- Real-device: open an r38-style Result Web page and verify the top three actions no longer overlap the system clock/network/battery area.


### alpha43-r40 polyglot prepare ordering acceptance

- Given a Python-primary project with a detected nested Vite component, supplemental Node preparation must execute before Python dependency installation.
- A generated Vite output required by pyproject/setup packaging must exist before `pip install -e` is invoked.
- Supplemental Node preparation failure must stop the composed PREPARE before Python installation and end with one project-level `SIFTALPHA_ENV=NOT_READY`.
- Successful Node preparation followed by successful Python preparation must end with one authoritative project-level `SIFTALPHA_ENV=READY`.
- Child global environment markers are filtered; `SIFTALPHA_NODE_ENV` remains visible for diagnostics.
- Projects without a required Vite component continue to accept `SIFTALPHA_NODE_ENV=NOT_REQUIRED` and proceed to Python preparation.
- Regression: r38 Result Web, r39 system-bar safe insets, Python CLI launch binding, Web truth gates, Runtime ownership and project-scoped STOP remain unchanged.
- Cloud: repository validators, unit tests and assembleDebug must pass before APK release.
- Real-device: re-prepare a source archive where pyproject force-includes a generated frontend dist directory; frontend build must complete first and Python editable install must no longer fail with `Forced include not found`.


### alpha43-r41 Python Web-extra preparation

- pyproject + declared web extra + detected Vite component => editable install uses the project's web extra.
- No declared web extra or no detected Vite component => base editable install remains unchanged.
- SiftAlpha must not hard-code FastAPI, Uvicorn or application-specific dependency names.
- PREPARE emits SIFTALPHA_PYPROJECT_EXTRAS=web|none.
- Node PREPARE starts with a fresh log; previous failures must not appear in a new successful run.
- Regression: r40 Vite-first ordering, r39 safe insets, r38 Result Web, Web discovery, CLI launch binding and project-scoped STOP remain unchanged.


### alpha43-r42 Native Web Application Launch Discovery acceptance

- High-confidence Python+Vite Web project:
  - Web classification is already enabled;
  - pyproject has [project.optional-dependencies].web;
  - Vite package.json + vite.config.* exists;
  - a safe console script is declared;
  - Python source contains a literal serve command plus a browser-suppression option.
  - Result: Native Web launch wins before unrelated required CLI arguments from a one-shot helper.
- The generated argv must remain structured: console script + serve + optional --host 127.0.0.1 + detected browser-suppression flag. No shell concatenation of user input is introduced.
- Explicit project run metadata remains authoritative and disables automatic Native Web launch.
- Missing Web extra, missing Vite evidence, missing serve declaration, or missing browser-suppression capability must fail closed and preserve existing CLI behavior.
- Multiple console scripts are not guessed unless exactly one canonically matches the declared project name.
- Existing r37 behavior remains mandatory: if Native Web proof fails, CLI requirements from one exact Python entrypoint may still bind launch to that Python file.
- Existing Web truth gate remains mandatory: launch evidence does not make the browser available; Runtime Web Discovery + Endpoint Probe must still verify a project-owned local HTTP endpoint.
- External Provider only for this round; Internal R console-script support is unchanged.
- Regression: r41 Web-extra preparation, r40 Vite-first build, r39 safe insets, r38 Result Web, r37 CLI argv, Runtime ownership and project-scoped STOP remain unchanged.
- Cloud validators, unit tests and assembleDebug must pass before APK release.
- Real-device: run the prepared modern easy_tdx sample. The confirmation should show a project-owned serve command rather than run_all_strategies.py; after START, Uvicorn should remain running and SiftAlpha should verify/open the project Web UI.


### alpha43-r43 Internal Alpine polyglot Web acceptance

- Capability routing:
  - Python-primary + NODE_JS supplemental => PREPARE and START may route to EMBEDDED_R.
  - Any non-Node supplemental Runtime remains rejected.
  - Python-only Embedded R behavior remains unchanged.
- Internal environment:
  - Node/Vite-required environment fingerprint differs from the same Python dependency metadata without Node/Vite.
  - Internal Alpine installs nodejs/npm only for the Node/Vite path and retains bounded apk retries.
  - Vite component discovery stays project-scoped and excludes node_modules/.git/dist/build.
  - npm package-lock => npm ci; no lock => npm install without creating a lock; pnpm/yarn lockfiles fail closed.
  - Vite build completes before Python package installation.
  - pyproject with a declared web extra installs /workspace[web]; absent extra falls back to /workspace.
- Internal launch:
  - Native Web console script executes from /siftalpha-env/venv/bin using structured argv.
  - Console-script names remain bounded to the existing safe command-name grammar.
  - Node/Vite or console-script launches stage the complete project tree; ordinary Python-file launches retain the narrower staging path.
  - Embedded CPython is never selected for a console-script Native Web launch.
- Presentation:
  - r42 Native Web discovery applies to EMBEDDED_R as well as External Provider.
  - Web is not AVAILABLE until the existing Endpoint Probe verifies the project-owned localhost endpoint.
- Regression:
  - r42 External Native Web launch, r41 Web-extra preparation, r40 Vite-first preparation, r39 insets, r38 Result Web, r37 CLI argv, Runtime ownership and project-scoped STOP remain unchanged.
- Cloud: validators, testDebugUnitTest, Internal Alpine Probe and assembleDebug must pass before APK release.
- Real-device: select SiftAlpha R for easy_tdx_1-main, PREPARE once, verify SIFTALPHA_X_ENVIRONMENT_READY=true, then RUN. The launch should use the project-owned serve console script and the verified Web UI should become available without Termux.


### alpha43-r44 scalable Internal project staging acceptance

- Generated/dependency pruning:
  - node_modules, virtual environments, Python caches and common tool caches never enter Internal R staging.
  - source-build staging additionally excludes dist/build/out and generated Web/coverage directories because Internal Alpine rebuilds those outputs.
  - ordinary non-source-build staging preserves prebuilt dist/build assets.
- Limits remain explicit:
  - entrypoint staging: <=4096 nodes, <=2048 files, <=8 MiB/file, <=64 MiB total;
  - full-project staging: <=8192 nodes, <=4096 files, <=16 MiB/file, <=128 MiB total.
  - exceeding post-filter limits must still fail closed and remove any partial staging root.
- Traversal:
  - the normal project/UI tree keeps its existing historical bound;
  - staging uses a separate bounded traversal and prunes ignored directories before they consume the execution-source budget.
- Security regression:
  - unsafe relative paths, duplicates, symlink destinations, path escape, oversized files and oversized total payload remain rejected.
- Internal Alpine integration:
  - Python+Node/Vite PREPARE uses sourceBuild=true;
  - Python+Node/Vite Native Web START uses sourceBuild=true;
  - non-Node console-script START may use full-project staging without dropping prebuilt dist/build.
- Cloud: repository validators, testDebugUnitTest, Internal Alpine Probe and assembleDebug must pass before APK release.
- Real-device: select SiftAlpha R for easy_tdx_1-main, PREPARE again and verify the previous 512-file staging failure is gone. The next observed output must come from the actual Internal Alpine Node/Vite/Python preparation stages.


### alpha43-r44 baseline closure — ACCEPTED

- Cloud verification: PASS.
  - W0 #263: SUCCESS.
  - Internal Alpine Probe #51: SUCCESS.
  - testDebugUnitTest + assembleDebug: SUCCESS.
  - stable v2-signed test APK produced.
- Real-device verification: PASS.
  - SiftAlpha R / EMBEDDED_R selected.
  - modern Python+Vite project PREPARE completed beyond the former 512-file staging limit.
  - Internal Alpine Node/npm + Vite build + Python web-extra preparation completed.
  - environment reached READY.
  - project-owned Native Web launch completed without Termux.
  - SiftAlpha opened the project Web UI.
  - Web UI dynamic refresh behavior was observed as working in the accepted test.
- Accepted source:
  - commit 0285db2c26565e1aa2a47624d2cad8fa9c19e89f
  - versionCode 170
  - versionName 0.8.0-alpha43-r44
  - frozen branch baseline/alpha43-r44-known-good
- Result: PASS. r44 replaces r34 as the project-level Known Good Functional Baseline. r34 remains a frozen historical reference and must not be moved.


### alpha43-r44-storage1 Unified Runtime Storage Manager

Cloud requirements:
- localization parity for all new storage strings;
- testDebugUnitTest PASS;
- assembleDebug PASS;
- stable v2 signing unchanged;
- Internal Alpine Probe remains PASS.

JVM safety coverage:
- Internal R environment identity hashing remains stable and matches the existing project-environment layout;
- clearing Embedded CPython Wheel cache removes only cache data;
- clearing Internal Alpine pip/npm caches removes only reusable cache data;
- cache cleanup must preserve Internal Alpine rootfs executables and project environments.

Real-device acceptance:
- open Runtime Storage Manager with Termux unavailable/not running and verify Internal R usage still renders;
- verify External Provider reports unavailable without disabling the Internal R section;
- clean one stopped Internal R project environment and verify Android shared-storage source remains intact;
- return to Runtime Center and verify that Internal R must be prepared again for the cleaned project;
- verify cleaning a currently running Internal R project is rejected;
- verify Internal R Wheel/pip/npm cache cleanup works without opening Termux;
- with Termux available, verify External Provider storage statistics and existing clean actions still work.


### alpha43-r44 final closure — ACCEPTED

- Version: 0.8.0-alpha43-r44-storage1 / versionCode 171.
- Accepted commit: 25596e855a7a141089568883b5d38e023982b6d3.
- W0 #266: PASS.
- Internal Alpine Probe #52: PASS.
- Real-device Unified Runtime Storage Manager acceptance: PASS.
- Internal R inspection and cleanup without Termux: PASS.
- Internal R project-source preservation after environment cleanup: PASS.
- Internal R re-prepare requirement after cleanup: PASS.
- External Provider storage path regression check: PASS per user acceptance.
- Result: r44 is closed and becomes the temporary/current Known Good Functional Baseline.
- Frozen branch: baseline/alpha43-r44-final-known-good.


### r45-A External Multi-Project Concurrency Proof

Automated contract:
- Different External Provider projects may own the same operation type concurrently.
- Activity ownership paths are project-scoped by runtimeId.
- Completing project B must not clear project A's live activity ownership.
- A STOP shell must not reference B runtime ownership files, and vice versa.
- Same-project duplicate operation protection remains fail-closed.
- Existing Web Discovery / Endpoint Probe / project-scoped STOP behavior must remain unchanged.

Cloud gate:
- repository validators PASS;
- testDebugUnitTest PASS;
- assembleDebug PASS;
- stable v2 signing unchanged.

Real-device External Provider gate:
- prepare and start project A;
- while A remains RUNNING, start project B;
- confirm A remains RUNNING after B starts;
- confirm STATUS and LOGS stay associated with their own project cards;
- if both expose Web endpoints, verify each project retains its own endpoint observation;
- STOP A and verify B continues running and remains observable;
- STOP B independently;
- separately exercise a same-port conflict when two Web projects request the same fixed port and confirm failure is isolated to the conflicting project rather than stopping the already-running project.

Only after this gate passes should r45-B replace the Embedded R single-project poll pointer with per-project observation state.


### r45-B Embedded R Multi-Project Observation

Automated contract:
- project A and project B can both be tracked for Embedded R polling;
- A in-flight polling must not block B from entering in-flight state;
- finishing A polling must not clear B in-flight state;
- untracking A must not untrack or modify B;
- a non-tracked project cannot acquire polling ownership;
- V04Activity no longer contains a global embeddedPollProject or global embeddedPollInFlight state.

Cloud gate:
- repository validators PASS;
- testDebugUnitTest PASS;
- assembleDebug PASS;
- Internal Alpine Probe PASS;
- stable v2 signing unchanged.

Real-device Internal R gate:
- prepare/start Internal R project A and keep it RUNNING;
- start Internal R project B without stopping A;
- confirm A and B remain independently RUNNING/observable;
- verify STATUS and LOGS for each project remain project-scoped;
- for Web projects, verify each project's Web Discovery and Endpoint Probe remain associated with its own Session/Generation;
- STOP A and confirm B continues running, continues polling and keeps its Web/log state;
- STOP B independently;
- leave and return to Runtime Center while A+B are running and confirm both observations recover;
- if two projects require the same fixed Web port, verify the conflict affects only the later conflicting project and never terminates the existing project.

r45-B is not accepted until real-device evidence passes.


### r45-B2 Web Availability lifecycle resume

Automated contract:
- a reachable endpoint revalidated in a new Activity lifecycle must notify presentation even when reachability remains true;
- an unchanged reachable result within the same lifecycle must not create a redundant presentation notification;
- a previously verified project-owned Web URL remains the provisional presentation target while the fresh foreground probe runs;
- browser launch still performs verifyNow before ACTION_VIEW.

Cloud gate:
- repository validators PASS;
- testDebugUnitTest PASS;
- assembleDebug PASS;
- Internal Alpine Probe PASS;
- stable v2 signing unchanged.

Real-device gate:
- run a Web project until Open is enabled;
- open the project Web UI in the external browser or switch to another app;
- return to SiftAlpha without pressing Refresh Logs;
- confirm Open remains enabled and opens only after normal Endpoint Probe verification;
- if the Web service is actually unavailable, confirm repeated fresh probe failure removes availability instead of preserving stale Open state.

## r46 Runtime compatibility / environment consistency

| Check | Expected | Evidence / status |
| --- | --- | --- |
| Embedded CPython environment reuse | Reuse only when project fingerprint and Runtime Identity both match | Unit coverage added; W0 #287 PASS |
| Internal Alpine project requirement | `requires-python` participates in source fingerprint and must match actual Alpine Python | Unit coverage added; W0 #287 PASS |
| Internal Alpine Runtime upgrade | Old project binding without/mismatching Python Runtime Identity is not READY | Source contract + W0 #287 PASS |
| External Python re-prepare | Build a fresh candidate venv and replace old venv only after successful install/validation | Unit coverage added; W0 #287 PASS |
| External Python version requirement | Reject declared Python requirement when current external Python cannot satisfy it | Unit coverage added; W0 #287 PASS |
| External Python ready binding | Python version / requirement change makes old environment NOT_READY | Unit coverage added; W0 #287 PASS |
| Internal Alpine assets | Existing reproducible rootfs/proot asset pipeline remains valid | Internal Alpine Probe #56 PASS |
| Real-device upgrade/re-prepare | Existing r45b2 install upgrades to v175; old environment invalidation and re-prepare behave correctly | PENDING DEVICE ACCEPTANCE |

## r46.1 legacy environment migration

| Check | Expected | Status |
| --- | --- | --- |
| Embedded CPython v2 -> v3 marker migration | Reuse compatible site-packages without reinstall; write v3 Runtime Identity marker | Unit regression + W0 #302 PASS |
| Embedded CPython incompatible Runtime | Do not migrate legacy environment when current Runtime identity is not the known v2-compatible Runtime | Existing runtime mismatch coverage + W0 #302 PASS |
| Internal Alpine pre-r46 fingerprint | Preserve the exact pre-r46 fingerprint calculation for migration comparison | Unit regression + W0 #302 PASS |
| Internal Alpine venv version evidence | Parse virtualenv and builtin venv pyvenv.cfg version formats before migration | Unit regression + W0 #302 PASS |
| Internal Alpine RootFS compatibility | Legacy migration requires current pinned RootFS identity/assets | Source gate + Internal Alpine Probe #57 PASS |
| Internal Alpine mixed marker state | Old project marker can migrate even if another project already upgraded the shared Runtime marker | Source path + W0 #302 PASS |
| External Python legacy ready marker | Migrate only when dependency hash, venv creation/current Python version, and requires-python are compatible | Generated-shell regression + W0 #302 PASS |
| Existing incompatible/changed project | Keep NOT_READY and require Prepare | Preserved r46 checks + W0 #302 PASS |
| Overwrite install from r46 to r46.1 | Previously prepared compatible projects remain usable without unnecessary re-prepare | PENDING REAL DEVICE |



## alpha43-r46.2 — Web recognition

| Area | Case | Expected |
|---|---|---|
| Fast signature | Dependency name only | Must not take high-confidence fast path |
| Streamlit | dependency + conventional entry/source | Detect Web, default 8501, resolve `streamlit run` |
| FastAPI | dependency + FastAPI app + Uvicorn evidence | Detect Web and resolve importable `module:app` |
| Django | dependency + manage.py + settings.py + urls.py | Detect Web and resolve bounded runserver launch |
| Python+Vite | project script + web extra + Vite + Web subcommand | Hybrid signature wins over backend-only framework |
| Node | Next/Vite/Nuxt with package-script evidence | Detect common framework/default port |
| Node server | Express/Fastify/Koa dependency + source listen | Detect only with combined source evidence |
| PREPARE concurrency | Begin PREPARE | Background Web rediscovery runs without gating PREPARE |
| Learned launch | Static candidate only | Store DISCOVERED; never auto-reuse |
| Learned launch | Current owned endpoint passes Endpoint Probe | Promote to VERIFIED |
| Learned launch | Later run | VERIFIED launch may skip deep rediscovery; endpoint is probed again |
| Fresh install | No learned state | Rebuild Web capability from project evidence |
| Result Web | Project Web unavailable | Result Web remains result presentation only; does not prove project Web |


## alpha43-r46.3 — External Python venv stable-prefix regression

| Area | Case | Expected |
|---|---|---|
| venv creation | PREPARE replacement | Create new venv directly at final `/root/venvs/<runtime-id>` path |
| relocation | Completed venv | Never rename a completed `.prepare-*` venv into final location |
| console scripts | pip-generated entry point | Shebang/interpreter prefix is generated against final stable venv |
| dependency install | requirements / pyproject | Use final `$venv/bin/python` throughout installation |
| prefix identity | Post-install validation | `sys.executable == $venv/bin/python` before READY |
| rollback | Dependency/install/validation failure | Delete partial replacement and restore prior venv |
| READY rollback | Failed PREPARE with prior valid env | Restore prior READY marker with prior venv |
| rollback failure | Previous env cannot be restored | Clear READY and emit `ENVIRONMENT_ROLLBACK_FAILED` |
| successful activation | New env validated | Write new READY, delete backups, disarm rollback |
| structured console launch | `$venv/bin/<script>` | Execute normally without exit 127 caused by stale temporary shebang |


## 2026-09-21 · R47 Environment architecture acceptance

| Area | Verification | Result |
| --- | --- | --- |
| Environment Detection（环境检测） | shared detection contract for Internal / External providers | PASS |
| Environment Plan（环境计划） | deterministic Plan ID / runtime / dependency / backend / build-step contract | PASS |
| Embedded CPython（内置 CPython） | deep dependency compatibility resolution before install | PASS |
| Internal Alpine（内部 Alpine） | consumes planned Python extras without install-time pyproject rediscovery | PASS |
| External Python（外部 Python） | consumes planned Python extras without install-time pyproject/Vite rediscovery | PASS |
| External READY（外部就绪状态） | Environment Plan ID binding and compatible migration | PASS |
| Build order（构建顺序） | existing Node/Vite → Python composition preserved | PASS |
| W0 Cloud Build（云端构建） | Run #365 | PASS |
| Internal Alpine Probe（内部 Alpine 探针） | release candidate probe | PASS |
| APK（安装包） | versionCode 180 / 0.8.0-alpha43-r47 | PASS |
| Real-device acceptance（真机验收） | user-confirmed real Android device test | PASS |

Accepted functional source: `d611d18d08cf03b411cc687259e507ca91fcbaa8`.
Frozen reference: `baseline/r47-environment-plan`.

## R48 — Dual Surface Architecture（双界面架构） / Unified User Workflow（统一用户工作流） planned gates

R48（第 48 阶段） starts from the accepted R47（第 47 阶段） environment baseline. These are planned acceptance gates and do not imply implementation has started.

### R48-0 — Developer Mode（开发者模式） / routing foundation

| Area | Verification | Expected |
| --- | --- | --- |
| Default mode（默认模式） | fresh/normal app entry | Developer Mode（开发者模式） defaults OFF and user-facing navigation enters Normal Mode（普通模式） |
| Developer access（开发者入口） | enable setting | current complete Developer Workspace（开发者工作区） remains reachable |
| Runtime neutrality（运行中立） | toggle mode while a project is RUNNING（运行中） | mode switch does not STOP/restart/change provider/change Environment Plan（环境计划） |
| State identity（状态一致） | switch Normal -> Developer -> Normal | both surfaces report the same project lifecycle/operation/environment/Web/result truth |
| Multi-project（多项目） | A and B active while switching surfaces | switching UI surface does not alter either project's ownership/observation state |
| Frozen contracts（冻结契约） | regression suite | R47 Environment Detection / Plan / Prepare, project-scoped STOP, Runtime Identity, Session / Generation, Web Discovery and Endpoint Probe remain unchanged |

### R48-A — Normal Project Workspace（普通项目工作区）

| Area | Verification | Expected |
| --- | --- | --- |
| State source（状态来源） | render normal project card | use existing ProjectUiSnapshot（项目界面快照） / ProjectActionPolicy（项目动作策略） rather than a second state machine |
| Primary action（主操作） | NOT_READY / CONFIG_REQUIRED / READY / RUNNING / terminal states | primary action matches existing policy decision |
| Advanced controls（高级控制） | normal surface | low-level STATUS / LOGS / CLEAN / Runtime Selection / raw diagnostics are not primary controls |
| Developer parity（开发者一致性） | open same project in Developer Workspace | developer surface observes the same underlying state |

### R48-B — Unified Refresh（统一刷新）

| Area | Verification | Expected |
| --- | --- | --- |
| Running project（运行项目） | user presses Refresh（刷新） | STATUS（状态查询） is authoritative; LOGS（日志查询） is requested only when policy/lifecycle requires it |
| Terminal project（终态项目） | refresh after natural exit | final LOGS may complete Result / Rich Result / Result Web extraction without changing terminal lifecycle incorrectly |
| Automatic observation（自动观察） | manual refresh races automatic observation | no duplicate conflicting operation; existing observation generation/pending rules win |
| Raw log presentation（原始日志呈现） | normal refresh | automatic/internal observation does not unexpectedly expand or overwrite developer raw-log presentation |
| Project scope（项目范围） | refresh project A while B runs | B remains untouched |

### R48-C — Prepare Project Workflow（准备项目工作流）

| Area | Verification | Expected |
| --- | --- | --- |
| Detection（检测） | Prepare Project invoked | existing R47 Environment Detection（环境检测） is used; no duplicate detector |
| Plan（计划） | valid project | existing deterministic Environment Plan（环境计划） drives preparation |
| Blocking issue（阻塞问题） | malformed/incomplete/unsupported project | workflow stops before Prepare（准备环境） and exposes the reason |
| Prepare failure（准备失败） | dependency/runtime preparation fails | workflow stops; START（运行） is not dispatched |
| Prepare success（准备成功） | environment reaches READY（就绪） | first-stage workflow stops at READY; no automatic START |
| Provider ownership（运行环境归属） | selected Internal/External provider | workflow respects existing selection/ownership and does not silently switch provider |

### R48-D/E/F — UI consolidation（界面融合） guards

| Area | Verification | Expected |
| --- | --- | --- |
| Import Project（导入项目） | PY / ZIP / GitHub choices | one UI entry may select existing import source; underlying import behavior stays separate |
| Project Location（项目目录） | AcodeProjects / other SAF root | one UI entry may select source; persisted SAF identity/permission semantics unchanged |
| Open（打开） | Web / Result Web / Rich Result | continue existing PresentationTargetResolver（呈现目标解析器） priority and current Endpoint Probe truth gate |
| STOP（停止） | any normal workflow active | STOP remains independent, project-scoped and preemptive |
| CLEAN（清理） | project active | destructive cleanup remains blocked while active and is not silently chained from STOP |
| Delete project（删除项目） | source + runtime data exist | no one-tap transactional deletion is introduced in first-stage R48 |
| Runtime Selection（运行环境选择） | project active | provider switch remains blocked and never occurs invisibly |
| Developer tools（开发者工具） | Developer Mode enabled | existing advanced controls remain available for diagnosis/regression testing |

### R48 cloud / device rule

Every installable R48 test build must:
- increment versionCode（版本代码） from the accepted R47 value 180;
- use the existing stable Debug signature（调试签名） for overwrite installation;
- pass repository validators（仓库校验）;
- pass unit tests（单元测试）;
- pass assembleDebug（Debug 构建）;
- keep Internal Alpine Probe（内部 Alpine 探针） PASS;
- receive real-device acceptance before the next R48 slice is treated as accepted.

### R48-0 linked-control hard gates（联动控制硬门禁）

| Area | Verification | Expected |
| --- | --- | --- |
| Normal default（普通模式默认） | fresh launch / Developer Mode（开发者模式） preference unset | Normal Mode（普通模式） is shown; Developer Mode（开发者模式） is OFF（关闭） |
| Developer unlock（开发者解锁） | enable Developer Mode（开发者模式） | Normal Mode（普通模式） remains primary; an additional Developer Workspace（开发者工作区） entry becomes available |
| Normal Run linkage（普通运行联动） | press Run（运行） in Normal Mode（普通模式）, then inspect Developer Workspace（开发者工作区） | Developer Workspace observes the same project as RUNNING（运行中）; no second Runtime（运行时） is created |
| Normal Stop linkage（普通停止联动） | press Stop（停止） in Normal Mode（普通模式）, then inspect Developer Workspace（开发者工作区） | the same project Runtime reaches STOPPED（已停止）; other projects remain unaffected |
| Developer-to-normal linkage（开发者到普通联动） | start/stop from existing Developer Workspace（开发者工作区）, then return to Normal Mode（普通模式） | Normal Mode reflects the same authoritative state |
| No hidden UI automation（禁止隐藏界面自动化） | review control path | Normal Mode does not start a hidden V04Activity（运行中心页面）, does not invoke UI button callbacks, and does not simulate clicks |
| Protected developer surface（保护开发者界面） | source diff for first R48 slice | existing Developer Workspace（开发者工作区） control behavior is unchanged; new Normal Mode（普通模式） integration is additive |
| Single backend（单一后端） | Run/Stop/Prepare/Refresh path review | Normal Mode delegates through Project Control Hub（项目控制枢纽） to existing shared controller/policy/state contracts |
| Mode neutrality（模式中立） | toggle Developer Mode（开发者模式） while RUNNING（运行中） | no Runtime restart/stop/clean/provider switch/environment mutation occurs |
| Project isolation（项目隔离） | project A and B active; control A from Normal Mode | B lifecycle, observation, Web and result state remain unchanged |

Implementation-note gate:
- “Do not modify Developer Mode（开发者模式）” means the accepted Developer Workspace（开发者工作区） runtime/business-control path is not rewritten for R48-0.
- Additive shell/routing/settings code may be introduced to expose Normal Mode（普通模式） and the Developer Mode（开发者模式） preference.
- Any edit to an existing Developer Workspace（开发者工作区） control file requires explicit justification as an unavoidable integration fix and must be called out separately before acceptance.

### R48-0A — Project Control Hub（项目控制枢纽） implementation evidence

| Area | Verification | Result |
| --- | --- | --- |
| Hub boundary（枢纽边界） | new ProjectControlHub exposes only RUN / STOP in first slice | PASS |
| Current Runtime selection（当前运行环境选择） | selection is re-read per action | PASS — unit coverage |
| Project identity（项目身份） | exact document identity forwarded to executor | PASS — unit coverage |
| Project isolation（项目隔离） | STOP A does not target B | PASS — unit coverage |
| Second state machine（第二状态机） | hub stores no independent RuntimeState | PASS — source + unit coverage |
| Hidden UI automation（隐藏界面自动化） | no hidden V04Activity launch / button simulation | PASS — source review |
| Developer Workspace protection（开发者工作区保护） | V04Activity and existing developer control source unchanged | PASS — source diff |
| Internal RUN（内部运行） | executor delegates to existing ProjectRuntimeController.startEmbeddedPython | PASS — source contract / compile |
| Internal STOP（内部停止） | executor delegates to existing ProjectRuntimeController.requestEmbeddedPythonStop | PASS — source contract / compile |
| External RUN（外部运行） | existing start + project-scoped external activity wrapper + RuntimeBackend | PASS — source contract / compile |
| External STOP（外部停止） | existing project-scoped stop + RuntimeBackend | PASS — source contract / compile |
| W0 Cloud Build（W0 云端构建） | #378 / source b8933b4e... | PASS |
| Internal Alpine Probe（内部 Alpine 探针） | #63 | PASS |
| APK（安装包） | v181 / 0.8.0-alpha43-r48a1 / SHA-256 6edaef1d... | PASS |
| Real-device linked-control acceptance（真机联动验收） | requires Normal Mode shell / routing to invoke hub | PENDING NEXT SLICE |

R48-0A is a cloud-verified foundation only and is not a frozen baseline.

### R48-0B — Dual Surface first user-visible slice（双界面首个可见版本）

| Area | Verification | Result |
| --- | --- | --- |
| Developer Mode default（开发者模式默认值） | unset preference | OFF（关闭） by implementation default |
| Normal project routing（普通项目路由） | Home project Open（打开） | routes to NormalProjectWorkspaceActivity（普通项目工作区） |
| Developer unlock（开发者解锁） | Developer Mode ON | Normal Mode remains primary and exposes Developer Workspace entry |
| Developer home hiding（开发者首页入口隐藏） | Developer Mode OFF | Runtime Center / environment / terminal / bridge diagnostics hidden |
| Shared RUN linkage（共享运行联动） | Normal RUN -> shared lifecycle | STARTING is published before dispatch; accepted state reuses existing Runtime |
| Failed RUN rollback（失败运行回滚） | hub rejects RUN | previous shared lifecycle snapshot restored |
| Shared STOP linkage（共享停止联动） | Normal STOP | same selected project/provider is targeted; terminal state is not fabricated early |
| Internal provider（内部运行环境） | RUN / STOP source contract | existing Embedded CPython / Internal Alpine controller/session path reused |
| External provider（外部运行环境） | RUN / STOP source contract | existing ProjectRuntimeController / Termux project-scoped path reused |
| Hidden UI automation（隐藏 UI 自动化） | source review | no hidden V04Activity launch / no simulated developer button click |
| Developer Workspace protection（保护开发者工作区） | source comparison | V04Activity.kt unchanged |
| Localization（多语言） | repository localization validators | PASS |
| ProjectControlHub unit tests（枢纽单元测试） | shared-state sequencing + project identity/isolation | PASS |
| W0 Cloud Build（W0 云端构建） | #405 / functional source 9266d5ad... | PASS |
| Internal Alpine Probe（内部 Alpine 探针） | #64 / r48a2 build configuration | PASS |
| APK（安装包） | versionCode 182 / 0.8.0-alpha43-r48a2 | PASS |
| APK SHA-256 | 050fa184165f61a164f3b4051558dd0cfe9a57f145feaecd835176a255400895 | RECORDED |
| Real-device dual-surface acceptance（双界面真机验收） | RUN/STOP cross-surface + project isolation | PENDING USER TEST |

R48-0B is cloud-verified but not yet an accepted/frozen baseline.

### R48-0B real-device acceptance（双界面首版真机验收）

| Area | Result |
| --- | --- |
| Normal Mode（普通模式） default user surface | PASS — user real-device test |
| Developer Mode（开发者模式） unlock from Settings（设置） | PASS — user real-device test |
| Shared RUN / STOP linkage（共享运行 / 停止联动） | PASS — user real-device test |
| R48-0B overall | **PASS（通过）** |

### R48-0C — Normal Runtime Selection（普通模式运行环境选择）

| Area | Verification | Result |
| --- | --- | --- |
| Current Runtime visibility（当前运行环境可见） | Normal Mode shows persisted per-project selection | PASS — source/cloud |
| Internal selection（内部执行环境） | user can select EMBEDDED_R while idle | PASS — source/cloud |
| External selection（外部执行环境） | user can select TERMUX while idle | PASS — source/cloud |
| Shared selection store（共享选择存储） | Normal Mode uses existing ProjectRuntimeSelectionStore | PASS — source review |
| Active runtime lock（运行中锁定） | PREPARING / STARTING / RUNNING | PASS — unit coverage |
| Active operation lock（操作中锁定） | non-terminal RuntimeOperationRecord blocks switching | PASS — unit coverage |
| Terminal unlock（终态解锁） | terminal operation does not block switching | PASS — unit coverage |
| Project isolation（项目隔离） | selection remains keyed by stable project documentId | PASS — existing contract / source review |
| Developer Workspace protection（开发者工作区保护） | V04Activity.kt unchanged | PASS — source diff |
| Localization（多语言） | all supported resource sets updated | PASS — repository validators |
| W0 Cloud Build（W0 云端构建） | #417 / source 49ce5f0a... | PASS |
| Internal Alpine Probe（内部 Alpine 探针） | #65 | PASS |
| APK（安装包） | v183 / 0.8.0-alpha43-r48a3 | PASS |
| APK SHA-256 | d4003fa76c0cc7678dab9d4929c3bed241ee68c02d98e593255a5180924f8617 | RECORDED |
| Real-device Runtime selection（真机运行环境选择） | Internal / External persistence + active lock | PENDING USER TEST |

R48-0C is cloud-verified but not yet an accepted/frozen baseline.

### R48 Normal Home Project Management Hierarchy（普通首页项目管理分级）

| Area | Verification | Result |
| --- | --- | --- |
| First-level home（首页一级） | Normal Mode home | only one Project Management（项目管理） entry is shown |
| Secondary actions（二级操作） | tap Project Management | modal dialog exposes existing four management actions |
| Existing callbacks（既有回调） | choose each secondary action | existing connect / choose root / new project / refresh callbacks are reused |
| Project list visibility（项目列表可见性） | dialog closed | project list remains directly visible as primary content |
| Developer Workspace protection（开发者工作区保护） | source diff | V04Activity.kt unchanged |
| Runtime neutrality（运行中立） | source review | no Runtime / Environment / Project Control Hub behavior changed |
| W0 Cloud Build（W0 云端构建） | #423 / source 8ff9424c... | PASS |
| Internal Alpine Probe（内部 Alpine 探针） | #66 | PASS |
| APK（安装包） | v184 / 0.8.0-alpha43-r48a4 | PASS |
| APK SHA-256 | 9a41728e283db380c20fbead54230d3572a24b86e38eb1ba1dd2520a50f06fff | RECORDED |
| Real-device visual acceptance（真机视觉验收） | hierarchy/dialog appearance | PENDING USER TEST |

### R48-B — Normal Mode Unified Refresh + Open（普通模式统一刷新 + 打开）

| Area | Verification | Result |
| --- | --- | --- |
| Unified Refresh（统一刷新） | Normal Mode single Refresh action | PASS — source/cloud |
| Internal refresh（内部刷新） | read existing Embedded R session snapshot | PASS — source/cloud |
| External active refresh（外部活动态刷新） | existing RuntimeAutoObservationPolicy -> STATUS | PASS — source/cloud |
| External terminal refresh（外部终态刷新） | STATUS terminal -> final LOGS in same user refresh | PASS — source/cloud |
| Shared operation owner（共享操作所有者） | STATUS / LOGS use ProjectOperationCoordinator generation/execution/deadline | PASS — source/cloud |
| Web capability source（网页能力来源） | WebProjectInspector project facts | PASS — source review |
| Browser separation（浏览器解耦） | browser preference is read only when opening a verified URL | PASS — source review |
| Web truth gate（网页事实门禁） | RuntimeWebStateStore candidate + Android endpoint probe | PASS — source/cloud |
| Result Web（结果网页） | existing extractor/analyzer/renderer/store reused | PASS — source/cloud |
| Rich Result（富结果） | existing parser/lifecycle reused | PASS — source/cloud |
| Presentation priority（呈现优先级） | PresentationTargetResolver WEB -> RESULT_WEB -> RICH_RESULT | PASS — source/cloud |
| Developer Runtime protection（开发者运行时保护） | no Internal R / Environment core rewrite | PASS — source diff |
| Localization（多语言） | zh / en / ja / ko / zh-TW resources | PASS — repository validators |
| W0 Cloud Build（W0 云端构建） | #494 | PASS |
| Internal Alpine Probe（内部 Alpine 探针） | #77 | PASS |
| APK（安装包） | v188 / 0.8.0-alpha43-r48b1 | PASS — cloud artifact |
| Real-device acceptance（真机验收） | Refresh/Open + browser separation + cross-surface parity | PENDING |

### R48-B2 — External Provider Probe Soft Timeout（外部执行环境探测软超时）

| Area | Verification | Result |
| --- | --- | --- |
| Probe timeout（探测超时） | no callback for 3 seconds -> `BRIDGE_UNRESPONSIVE` | PASS — unit/cloud |
| Timeout semantics（超时语义） | no-response is not configuration failure | PASS — source/unit |
| Recovery UI（恢复界面） | Open Termux + Re-check | PASS — source/cloud |
| Resume retry（返回重试） | `onResume` retries `BRIDGE_UNRESPONSIVE` | PASS — source/cloud |
| Late callback fencing（迟到回调隔离） | timed-out execution result ignored | PASS — unit/cloud |
| Shared facts（共享事实） | Normal / Developer read same coordinator/store | PASS — source/unit |
| Developer protection（开发者模式保护） | only minimal readiness branch added | PASS — source diff |
| Internal Alpine Probe（内部 Alpine 探针） | #78 | PASS |
| W0 Cloud Build（云端构建） | #504 attempt 2 | PASS |
| APK（安装包） | 0.8.0-alpha43-r48b2 / 189 | PASS — cloud artifact |
| Real-device acceptance（真机验收） | 3-second fallback + open/recheck + return retry | PENDING |

### R48-C — Prepare Productization + Unified Import（准备产品化 + 统一导入）

| Area | Verification | Result |
| --- | --- | --- |
| Prepare facts（准备事实） | existing Detection / Plan / Compatibility / Prepare / Verification reused | PASS — source/cloud |
| Presentation-only policy（仅展示策略） | `PrepareWorkflowPresentationPolicy` does not mutate Runtime facts | PASS — unit/cloud |
| Detection summary（检测摘要） | runtime + direct dependency count surfaced | PASS — source/cloud |
| Compatibility（兼容性） | resolved / Internal Alpine fallback surfaced | PASS — source/cloud |
| Live prepare phase（实时准备阶段） | existing read-only PrepareProgressProbe mapped to human phases | PASS — source/cloud |
| Verification（验证） | READY / FAILED / STOPPED / timeout presentation | PASS — source/cloud |
| Configuration guidance（配置引导） | prepared environment + missing REQUIRED config count | PASS — source/cloud |
| Unified Import（统一导入） | one Normal Home entry | PASS — source/cloud |
| ZIP import（ZIP 导入） | existing V04ProjectGateway importer reused | PASS — source/cloud |
| Python file import（Python 文件导入） | existing V04ProjectGateway importer reused | PASS — source/cloud |
| Import policy（导入策略） | .zip / .py / unsupported classification | PASS — unit/cloud |
| Project Location（项目位置） | current root + AcodeProjects + other root | PASS — source/cloud |
| More（更多） | normal management actions moved to compact menu | PASS — source/cloud |
| Developer Tools（开发者工具） | advanced tools only when Developer Mode enabled | PASS — source/cloud |
| Runtime protection（运行时保护） | no second Runtime / Environment orchestration | PASS — source review |
| Internal Alpine Probe（内部 Alpine 探针） | #80 | PASS |
| W0 Cloud Build（云端构建） | #533 | PASS |
| APK（安装包） | r48c1 / versionCode 190 | PASS — cloud artifact |
| Real-device acceptance（真机验收） | prepare phases + import + location + More | PENDING |

### R48-D — Developer Restoration + User Management（开发者恢复 + 用户管理）

| Area | Verification | Result |
| --- | --- | --- |
| Developer Runtime Bridge（开发者运行时桥） | pre-R48-C environment probe / Termux test / permission / setup / output entries restored | PASS — source/cloud |
| Developer Settings（开发者设置） | remains top-right, not moved into User More | PASS — source review |
| Developer bottom navigation（开发者底部导航） | Home / Runtime / Environment preserved | PASS — source review |
| User bottom navigation（用户底部导航） | redundant Home entry removed | PASS — source/cloud |
| User More（用户更多） | Runtime storage + Settings available | PASS — source/cloud |
| Simplified Runtime Storage（简化运行空间） | same internal/external storage controllers, simplified presentation | PASS — source/cloud |
| Project cleanup isolation（项目清理隔离） | existing project-scoped internal/external clean APIs reused | PASS — source review |
| GitHub shared parser（GitHub 共享解析） | HTTPS / SSH / branch / name rules | PASS — unit/cloud |
| Developer GitHub behavior（开发者 GitHub 行为） | original UI retained; shared parser/metadata only | PASS — source review |
| User GitHub import（用户 GitHub 导入） | does not open Developer Workspace; shared clone + metadata | PASS — source/cloud |
| Branding（品牌） | user-visible SiftAlpha Studio / Studio resources migrated to SiftAlpha X | PASS — source/cloud |
| Application identity（应用身份） | com.siftalpha.studio unchanged | PASS — APK evidence |
| W0 Cloud Build（云端构建） | #587 | PASS |
| Internal Alpine Probe（内部 Alpine 探针） | #81 | PASS |
| APK（安装包） | r48d1 / versionCode 191 | PASS — cloud artifact |
| Real-device acceptance（真机验收） | developer baseline + user More/storage/GitHub/branding | PENDING |

### R48-D2 — Shared Reliability UX（共享可靠性体验）

| Area | Verification | Result |
| --- | --- | --- |
| Battery guidance（电量提醒） | Normal + Developer share one controller/state | PASS — source/cloud |
| System settings routing（系统设置跳转） | generic Android battery settings with app-details fallback, no OEM table | PASS — source review |
| Reminder behavior（提醒行为） | first Prepare/Run; later defers current process; settings acknowledgement persists | PASS — source review |
| Normal activity indicator（普通模式活动条） | indeterminate bar under project status | PASS — source/cloud |
| Developer activity indicator（开发者模式活动条） | indeterminate bar under existing project state line | PASS — source/cloud |
| No fake percentage（无虚假百分比） | indicator is indeterminate only | PASS — source review |
| Timeout integrity（超时完整性） | indicator reads shared state/operation and does not extend Runtime deadlines | PASS — unit/source |
| Stale STARTING/PREPARING（陈旧活动状态） | no animation without active operation unless confirmed RUNNING | PASS — unit |
| Immediate Prepare/Run feedback（准备/运行即时反馈） | Normal Mode sets indicator immediately before async control dispatch | PASS — source/cloud |
| Developer baseline（开发者基线） | only explicitly authorized reminder + indicator added; existing workflow retained | PASS — source review |
| W0 Cloud Build（云端构建） | #599 | PASS |
| Internal Alpine Probe（内部 Alpine 探针） | #82 | PASS |
| APK（安装包） | r48d2 / versionCode 192 | PASS — cloud artifact |
| Real-device acceptance（真机验收） | reminder routing + indicator lifecycle | PENDING |



### R48-D2 UI Baseline Parity Gate（R48-D2 界面基线等价门禁）

This gate is mandatory for every post-R48-D2 Normal Mode UI APK.

| Area | Required parity with R48-D2 | Result |
| --- | --- | --- |
| Baseline immutability（基线不可变） | `baseline/r48d2` remains at `729659c345e19d249de44fdb6492c47d811701cd` | REQUIRED |
| Primary action mapping（主操作映射） | Prepare / Configure / Run / Stop use existing Shared Core semantics | REQUIRED |
| Open（打开） | preserved as project-page secondary action with R48-D2 enablement semantics | REQUIRED |
| Refresh（刷新） | preserved as project-page secondary action with R48-D2 enablement semantics | REQUIRED |
| Runtime location（运行位置） | Internal / External selection preserved | REQUIRED |
| External recovery（外部恢复） | permission / Open Termux / Re-check preserved | REQUIRED |
| Import（导入） | local Python / ZIP / GitHub capabilities preserved | REQUIRED |
| New Project（新建项目） | preserved | REQUIRED |
| Project Location（项目位置） | preserved | REQUIRED |
| Project List（项目列表） | preserved | REQUIRED |
| Search / Filter（搜索 / 筛选） | R48-D2 capability preserved | REQUIRED |
| More（更多） | Refresh Projects / New Project / Runtime Storage / Settings preserved | REQUIRED |
| Background reliability（后台可靠性） | R48-D2 guidance behavior preserved | REQUIRED |
| Activity indicator（活动指示条） | R48-D2 state/timeout behavior preserved; no fake percent | REQUIRED |
| Runtime / Environment semantics（运行时 / 环境语义） | no UI-driven behavior changes | REQUIRED |
| Developer Mode（开发者模式） | unchanged by Normal Mode redesign | REQUIRED |
| Visual redesign（视觉重设计） | may change presentation only after all parity rows pass | REQUIRED |

Failure of any REQUIRED row = UI regression（界面回归）. Do not present the build as an accepted design delivery.


### R48-D3 v196 — Baseline parity + secondary-path design

| Area | Verification | Result |
| --- | --- | --- |
| Baseline branch | `baseline/r48d2` unchanged | REQUIRED |
| Home search/filter | R48-D2 capability visible in D3 | SOURCE READY |
| Project Open | visible / enabled by R48-D2 rules | SOURCE READY |
| Project Refresh | visible / enabled by R48-D2 rules | SOURCE READY |
| Project Details/Delete | restored in D3 project cards | SOURCE READY |
| Runtime Location | visible across project states | SOURCE READY |
| External recovery | permission / Open Termux / Re-check parity | SOURCE READY |
| Configuration flow | Save and Run are separate steps | SOURCE READY |
| Import secondary route | D3-designed | SOURCE READY |
| Project Location secondary route | D3-designed | SOURCE READY |
| More secondary route | D3-designed | SOURCE READY |
| New Project secondary route | D3-designed | SOURCE READY |
| GitHub Import secondary route | D3-designed | SOURCE READY |
| Project Details/Delete dialogs | D3-designed | SOURCE READY |
| Runtime Storage | D3-designed presentation, same controllers | SOURCE READY |
| Normal Settings entry | D3 theme; Developer entry unchanged | SOURCE READY |
| Developer Mode | unchanged | REQUIRED |
| Runtime / Environment semantics | unchanged | REQUIRED |
| W0 Cloud Build | validators + tests + APK | PENDING |


### R48-D3 v197 — Real-device running + brand transition repair

| Area | Verification | Expected |
| --- | --- | --- |
| R48-D2 baseline | `baseline/r48d2` remains unchanged | PASS REQUIRED |
| Result linkage | `state.openEnabled=true` while RUNNING makes result phase Available | PASS REQUIRED |
| Open behavior | Open enablement semantics unchanged from Shared Core | PASS REQUIRED |
| Running text | center Running label uses Normal Mode primary text color | PASS REQUIRED |
| STARTING motion | rotating progress remains only while STARTING | PASS REQUIRED |
| RUNNING motion | no continuous rotation; restrained breathing glow/scale | PASS REQUIRED |
| Reduced motion | no breathing/rotating animation when system animations disabled | PASS REQUIRED |
| Stop behavior | same R48-D2 action/enablement; presentation uses blue tone | PASS REQUIRED |
| Brand dwell | launch brand transition is ~3-5 seconds | PASS REQUIRED |
| Brand logo | original R48-D2 logo asset; geometry not redrawn | PASS REQUIRED |
| Cloud validators/tests/APK | W0 must pass before APK delivery | PENDING |


### R48-D3 v198 — Result-linked execution completion + S-path brand assembly

| Area | Verification | Expected |
| --- | --- | --- |
| R48-D2 baseline | `baseline/r48d2` remains at `729659c...` | PASS REQUIRED |
| Execute phase | `openEnabled=true` -> Execute Project Task = Completed | PASS REQUIRED |
| Result phase | `openEnabled=true` -> Wait for Result = Available | PASS REQUIRED |
| Runtime lifecycle | page may still show Running while result is available | UNCHANGED |
| Full running ring | result available -> no visual gap in outer ring | PASS REQUIRED |
| Inner flow | exactly three restrained thin data-flow curves | PASS REQUIRED |
| Inner motion | subtle highlights move only when animations enabled | PASS REQUIRED |
| Reduced motion | no continuous decorative motion | PASS REQUIRED |
| Brand data streams | upper right->center, lower left->center curved convergence | PASS REQUIRED |
| Original logo | same R48-D2 logo asset; no geometry redraw | PASS REQUIRED |
| Brand dwell | remains within ~3-5 s | PASS REQUIRED |
| Cloud validators/tests/APK | W0 must pass before APK delivery | PENDING |


### R48-D3 v199 — Approved launch concept implemented in Compose

| Area | Verification | Expected |
| --- | --- | --- |
| Real logo source | launch uses `R.drawable.siftalpha_launcher_art` | PASS REQUIRED |
| No static concept image | visual is rendered by Compose code | PASS REQUIRED |
| Upper data stream | dense code/data flows from upper-right to center | PASS REQUIRED |
| Lower data stream | dense code/data flows from lower-left to center | PASS REQUIRED |
| Convergence | streams visually meet around real logo | PASS REQUIRED |
| Wordmark | SiftAlpha X shown after logo resolution | PASS REQUIRED |
| Chinese tagline | existing approved brand tagline shown | PASS REQUIRED |
| Bottom accent | restrained line + INTELLIGENCE IN MOTION | PASS REQUIRED |
| Launch dwell | approximately 3-5 seconds | PASS REQUIRED |
| Reduced motion | static equivalent without continuous decorative motion | PASS REQUIRED |
| R48-D2 baseline | baseline branch/source unchanged | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v200 — Launch code/data flow motion only

| Area | Verification | Expected |
| --- | --- | --- |
| Scope | only launch code/data motion changed | PASS REQUIRED |
| Upper/lower flow | independent non-mirrored continuous motion | PASS REQUIRED |
| Token staggering | code elements do not move as one synchronized block | PASS REQUIRED |
| Loop reset | entry/exit fade hides wrap/reset | PASS REQUIRED |
| Curve movement | subtle drift follows approved stream paths | PASS REQUIRED |
| Logo/layout/text | identical to v199 approved composition | PASS REQUIRED |
| Launch timing | unchanged from v199 | PASS REQUIRED |
| R48-D2 baseline | unchanged | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v201 — Galaxy-style launch flow refinement

| Area | Verification | Expected |
| --- | --- | --- |
| Scope | launch code/data motion only | PASS REQUIRED |
| Wide entry | both sides start broad / cloud-like | PASS REQUIRED |
| Galaxy feel | subtle orbital drift, no rigid synchronized rails | PASS REQUIRED |
| Taper | stream width continuously narrows toward center | PASS REQUIRED |
| Convergence | both sides collapse to one center point | PASS REQUIRED |
| Hard rail | no full-length visible upper-right direct line | PASS REQUIRED |
| Final filament | only short soft line immediately before logo | PASS REQUIRED |
| Logo/layout/text/timing | unchanged from v200 | PASS REQUIRED |
| R48-D2 baseline | unchanged | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v202 — Dense rotating galaxy strands to S endpoints

| Area | Verification | Expected |
| --- | --- | --- |
| Scope | launch code/data motion only | PASS REQUIRED |
| Line density | 24 curved strands per side / 48 total | PASS REQUIRED |
| Code density | 64 visible code tokens | PASS REQUIRED |
| Rotation | strands curve/rotate inward, not straight rails | PASS REQUIRED |
| Funnel | broad edge -> narrow bundle near logo | PASS REQUIRED |
| Upper target | upper-right stream reaches upper visible S endpoint | PASS REQUIRED |
| Lower target | lower-left stream reaches lower visible S endpoint | PASS REQUIRED |
| Center rail | no long center/direct filament | PASS REQUIRED |
| Logo/layout/text/timing | unchanged | PASS REQUIRED |
| R48-D2 baseline | unchanged | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v203 — Four-scene approved launch storyboard

| Area | Verification | Expected |
| --- | --- | --- |
| Scope | launch presentation only | PASS REQUIRED |
| 01 Start | broad two-sided code/data flow, open center | PASS REQUIRED |
| 02 Converge | dense curved S-like convergence | PASS REQUIRED |
| 03 Form | fragment field visibly assembles into S | PASS REQUIRED |
| 04 Complete | actual app logo revealed, not substituted | PASS REQUIRED |
| Header | phase number + localized title + English microcopy | PASS REQUIRED |
| Bottom copy | localized caption + English microcopy | PASS REQUIRED |
| Brand header | SiftAlpha X + FROM CODE TO POSSIBILITY | PASS REQUIRED |
| Final wordmark | SiftAlpha X + INTELLIGENCE IN MOTION | PASS REQUIRED |
| Launch timing | approximately 5 seconds incl. exit | PASS REQUIRED |
| Reduced motion | no continuous decorative animation | PASS REQUIRED |
| Localization keys | identical across 5 locales | PASS REQUIRED |
| R48-D2 baseline | unchanged | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v204 — v202 launch + convergence density only

| Area | Verification | Expected |
| --- | --- | --- |
| Launch composition | same as v202 | PASS REQUIRED |
| Extra v203 stage chrome | absent from app presentation | PASS REQUIRED |
| Curved strands | 40 per side | PASS REQUIRED |
| Code tokens | 120 total | PASS REQUIRED |
| Luminous particles | 144 per side | PASS REQUIRED |
| S endpoint targets | unchanged from v202 | PASS REQUIRED |
| Logo/text/background/timing | unchanged from v202 | PASS REQUIRED |
| R48-D2 baseline | unchanged | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v205 — Cross-endpoint spiral + code-S formation

| Area | Verification | Expected |
| --- | --- | --- |
| Scope | launch convergence/formation only | PASS REQUIRED |
| v204 layout/text/background | unchanged | PASS REQUIRED |
| Upper-right stream | wraps right/bottom -> lower-left S end | PASS REQUIRED |
| Lower-left stream | wraps left/top -> upper-right S end | PASS REQUIRED |
| Center crossing | no direct X-shaped crossing through logo center | PASS REQUIRED |
| Endpoint absorption | orbit radius collapses into S endpoints | PASS REQUIRED |
| Code continuity | code enters endpoints and redistributes along S body | PASS REQUIRED |
| Code S | dense fragment-built S before real logo | PASS REQUIRED |
| Real logo | unchanged original app logo resolves last | PASS REQUIRED |
| R48-D2 baseline | unchanged | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v206 — Final four-stage opening motion

| Area | Verification | Expected |
| --- | --- | --- |
| Stage 1 | dense chaotic lower-left / upper-right inflow with trails | PASS REQUIRED |
| Stage 2 | clear S-shaped curved convergence | PASS REQUIRED |
| Stage 3 | volumetric code/nebula S with rotation/flicker | PASS REQUIRED |
| Stage 4 | actual app logo + SiftAlpha X + INTELLIGENCE IN MOTION | PASS REQUIRED |
| Stage titles / numbers | absent | PASS REQUIRED |
| Explanatory captions | absent | PASS REQUIRED |
| Required code glyphs | AI, 101, </>, {}, 0x, Py, JS present | PASS REQUIRED |
| Logo source | unchanged real launcher asset | PASS REQUIRED |
| Reduced motion | completed-frame fallback | PASS REQUIRED |
| R48-D2 baseline | unchanged | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v207 — Code directly constructs final logo geometry

| Area | Verification | Expected |
| --- | --- | --- |
| Scope | launch presentation only | PASS REQUIRED |
| Inflow | lower-left + upper-right remain | PASS REQUIRED |
| Generic large-S rail | removed as formation target | PASS REQUIRED |
| Logo shell | code/fragments form rounded-square shell | PASS REQUIRED |
| S ribbon | code/fragments form central S | PASS REQUIRED |
| Terminal mark | code/fragments include >_ region | PASS REQUIRED |
| Direct convergence | flow strands terminate across logo geometry | PASS REQUIRED |
| Code-logo -> real-logo | continuous overlap/morph, no hard cut | PASS REQUIRED |
| Real logo source | unchanged launcher asset | PASS REQUIRED |
| Stage labels/captions | absent | PASS REQUIRED |
| R48-D2 baseline | unchanged | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v208 — Readable code-built logo before real logo

| Area | Verification | Expected |
| --- | --- | --- |
| Inflow | converges directly into logo targets | PASS REQUIRED |
| Intermediate form | visually reads as SiftAlpha X logo, not a blob | PASS REQUIRED |
| Code glyph population | 216 | PASS REQUIRED |
| Rounded-square shell | 52 evenly spaced code glyphs | PASS REQUIRED |
| S ribbon | seven-row thick glyph lattice | PASS REQUIRED |
| Terminal | explicit white >_ glyph structure | PASS REQUIRED |
| Generic fragment cloud | removed | PASS REQUIRED |
| Code-logo hold | visible before real-logo reveal | PASS REQUIRED |
| Real logo asset | unchanged | PASS REQUIRED |
| R48-D2 baseline | unchanged | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v209 — Pure code-S -> real logo

| Area | Verification | Expected |
| --- | --- | --- |
| Intermediate object | clean S made from code | PASS REQUIRED |
| Rounded-square code shell | absent | PASS REQUIRED |
| Code terminal >_ | absent in intermediate | PASS REQUIRED |
| Particle/cloud blob | absent | PASS REQUIRED |
| Code glyph count | 252 | PASS REQUIRED |
| S lattice | 9-band thick ribbon | PASS REQUIRED |
| Inflow continuity | glyphs travel directly into S slots | PASS REQUIRED |
| Code-S hold | clearly visible before real-logo reveal | PASS REQUIRED |
| Real logo asset | unchanged | PASS REQUIRED |
| R48-D2 baseline | unchanged | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v210 — True 2D code-S mask fill

| Area | Verification | Expected |
| --- | --- | --- |
| Intermediate silhouette | broad readable S, not text knot/blob | PASS REQUIRED |
| Formation algorithm | 2D filled S mask, not offset path bands | PASS REQUIRED |
| Mask grid | 30 × 36 normalized cells | PASS REQUIRED |
| Waist | visibly tighter than upper/lower lobes | PASS REQUIRED |
| Negative space | clear S interior separation | PASS REQUIRED |
| Settled glyphs | distributed across full S mask | PASS REQUIRED |
| Shell / terminal / cloud | absent | PASS REQUIRED |
| Code-S hold | visible before real-logo reveal | PASS REQUIRED |
| Real logo asset | unchanged | PASS REQUIRED |
| R48-D2 baseline | unchanged | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v211 — Dense full-mask code S

| Area | Verification | Expected |
| --- | --- | --- |
| Intermediate shape | one continuous S | PASS REQUIRED |
| Disconnected islands | absent | PASS REQUIRED |
| Mask coverage | every accepted S cell rendered | PASS REQUIRED |
| Glyph material | actual code text, not dots/cloud | PASS REQUIRED |
| Rendering | Canvas text for dense fill | PASS REQUIRED |
| Inflow handoff | moving glyphs fade into settled S | PASS REQUIRED |
| Real logo | unchanged final asset | PASS REQUIRED |
| R48-D2 baseline | unchanged | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v212 — Decorative-motion lifecycle gate

| Area | Verification | Expected |
| --- | --- | --- |
| Custom code opening | removed | PASS REQUIRED |
| Android system splash | unchanged/native | PASS REQUIRED |
| Motion policy | foreground + system animations => enabled | PASS REQUIRED |
| Background lifecycle | decorative infinite animations disabled below STARTED | PASS REQUIRED |
| System animations disabled | decorative infinite animations disabled | PASS REQUIRED |
| AuroraBackdrop | lifecycle gated | PASS REQUIRED |
| RunOrb | lifecycle gated | PASS REQUIRED |
| DotPulse | lifecycle gated | PASS REQUIRED |
| Runtime execution | unaffected by UI motion gate | PASS REQUIRED |
| Prepare/download/Web/log activity | unaffected by UI motion gate | PASS REQUIRED |
| R48-D2 baseline | unchanged | PASS REQUIRED |
| Unit tests / assembleDebug | cloud build must pass | PENDING |


### R48-D3 v213 — Installable lifecycle-motion verification build

| Area | Verification | Expected |
| --- | --- | --- |
| Custom code opening | absent | PASS REQUIRED |
| AuroraBackdrop | stops below Activity STARTED | PASS REQUIRED |
| RunOrb | stops below Activity STARTED | PASS REQUIRED |
| DotPulse | stops below Activity STARTED | PASS REQUIRED |
| System animations disabled | decorative motion disabled | PASS REQUIRED |
| Runtime/project background work | unaffected | PASS REQUIRED |
| Existing Normal Mode Canvas Size usage | compiles | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v214 — Eight Normal Mode repairs

| Area | Verification | Expected |
| --- | --- | --- |
| Launch | no code-convergence motion; real logo + SiftAlpha X + approved text retained | PASS REQUIRED |
| Home greeting | hello/idea-result copy absent | PASS REQUIRED |
| Project Location text | primary light text on dark theme | PASS REQUIRED |
| Project card status | shared runtime lifecycle state shown after Home resume | PASS REQUIRED |
| Description edit | Details allows edit/save and persistence to project metadata | PASS REQUIRED |
| Description search | saved description is searchable by existing Home search | PASS REQUIRED |
| Home version | absent from Home; remains in Settings > About | PASS REQUIRED |
| Prepare phases | one compact current-phase card, not six stacked rows | PASS REQUIRED |
| No-match text | primary light text on dark theme | PASS REQUIRED |
| Decorative motion lifecycle | Aurora/RunOrb/DotPulse gated while backgrounded | PASS REQUIRED |
| Runtime semantics | execution/prepare/Open/Refresh/Stop unchanged | PASS REQUIRED |
| R48-D2 baseline | unchanged | PASS REQUIRED |
| Localization parity | all five locale home.xml key sets remain aligned | PASS REQUIRED |
| W0 validators/tests/APK | cloud build must pass | PENDING |


### R48-D3 v215 — Launch / project card / prepare progress / Developer Mode boundary

| Area | Verification | Expected |
| --- | --- | --- |
| Android 12+ launch | system starting window uses SiftAlpha dark background + real launcher mark | PASS REQUIRED |
| Static brand hold | ~3 seconds on cold launch only | PASS REQUIRED |
| Mode switch | Developer Mode -> Normal Mode does not replay brand hold | PASS REQUIRED |
| Home copy | big hello/result slogan absent; small secondary tagline present | PASS REQUIRED |
| Project description | description remains visible without replacing configuration state | PASS REQUIRED |
| Project badges | configuration badge immediately followed by runtime-state badge | PASS REQUIRED |
| Internal PREPARE | phase 1 detection -> phase 2 compatibility -> actual runtime/install/verify events -> ready | PASS REQUIRED |
| External PREPARE | existing log-probe mapping unchanged | PASS REQUIRED |
| Developer Mode Home | shared scaffold remains byte-identical to baseline/r48d2 segment | PASS REQUIRED |
| Developer shared import copy | restored to baseline/r48d2 wording | PASS REQUIRED |
| W0 / tests / APK | cloud build must pass | PENDING |


### R48-D4 v216 — Direct brand launch + Home header spacing

| Area | Verification | Expected |
| --- | --- | --- |
| Android 12+ cold launch | no visible separate system-logo page before app brand page | PASS REQUIRED |
| System starting window | #040817 background + transparent icon | PASS REQUIRED |
| App brand page | existing static real logo + text remains ~3 seconds | PASS REQUIRED |
| Home brand mark | top-left logo = 44dp | PASS REQUIRED |
| Home wordmark | top-left SiftAlpha = 22sp | PASS REQUIRED |
| Home tagline spacing | content top padding = 0dp; visually close to enlarged brand row | PASS REQUIRED |
| Developer Mode | UI and runtime behavior unchanged | PASS REQUIRED |
| W0 / tests / APK | cloud build must pass | PENDING |


### R48-D5 v217 — Shared Run Workflow repair

| Area | Verification | Expected |
| --- | --- | --- |
| Shared launch resolution | Normal Mode uses existing Python native-Web / CLI / file launch resolvers before ProjectControlHub RUN | PASS REQUIRED |
| Runtime configuration discovery | External START stdout+stderr and failed Internal R snapshots feed existing RuntimeConfigurationDiagnostic | PASS REQUIRED |
| Shared configuration facts | discovered env / CLI hints persist in existing siftalpha_runtime_configuration_discovery_v1 contract | PASS REQUIRED |
| Configuration recovery | runtime-discovered named env requirement changes Normal Mode to Configure without a second config model | PASS REQUIRED |
| CLI recovery | runtime-discovered CLI arguments produce entry/argument input and structured PythonLaunchInvocation | PASS REQUIRED |
| Pending RUN retry | save resumes RUN only when a pending recovery intent exists | PASS REQUIRED |
| Project isolation | pending recovery keyed by project id only | PASS REQUIRED |
| ProjectControlHub boundary | still owns dispatch only; no second runtime state machine | PASS REQUIRED |
| Developer Mode | V04Activity source unchanged | PASS REQUIRED |
| Existing diagnostic tests | RuntimeConfigurationDiagnostic / PythonCliLaunchResolver regressions remain green | PASS REQUIRED |
| W0 / unit tests / assembleDebug | cloud build | PENDING |
| Internal Alpine Probe | cloud probe | PENDING |


### R48-D6 v218 — Shared PREPARE readiness invariant

| Area | Verification | Expected |
| --- | --- | --- |
| Shared READY policy | READY -> START enabled + PREPARE disabled | PASS REQUIRED |
| Disable reason | READY PREPARE -> ENVIRONMENT_ALREADY_READY | PASS REQUIRED |
| External provider surface | Developer Prepare button consumes shared disabled decision | PASS REQUIRED |
| Internal R surface | READY invariant identical without external host | PASS REQUIRED |
| NOT_READY | PREPARE remains enabled and START disabled | PASS REQUIRED |
| UNKNOWN | existing STATUS / PREPARE recovery policy unchanged | PASS REQUIRED |
| Normal Mode projection | shared READY/START -> Normal RUN, never PREPARE_PROJECT | PASS REQUIRED |
| ENVIRONMENT_ERROR recovery | Normal re-prepare path remains available | PASS REQUIRED |
| Developer Mode source | V04Activity unchanged | PASS REQUIRED |
| Normal Mode source | NormalProjectWorkspaceActivity / SiftAlphaNormalUi unchanged | PASS REQUIRED |
| W0 / unit tests / assembleDebug | cloud build | PENDING |
| Internal Alpine Probe | cloud probe | PENDING |


### R48-D7 v219 — External Provider lifecycle hardening

| Area | Verification | Expected |
| --- | --- | --- |
| Bridge probe | RUN_COMMAND marker returns within 3s | PASS REQUIRED |
| Runtime capability probe | proot-distro Ubuntu returns SIFTALPHA_EXTERNAL_RUNTIME_OK within 8s | PASS REQUIRED |
| Legacy bridge-only PASS | does not become READY without runtime capability proof | PASS REQUIRED |
| Cold/background Termux | no PREPARING state before provider readiness is proven | PASS REQUIRED |
| Open Termux recovery | returning to SiftAlpha re-probes and resumes pending PREPARE/RUN/REFRESH | PASS REQUIRED |
| Internal R | page resume does not auto-probe Termux | PASS REQUIRED |
| External STATUS | timeout deadline = 10s | PASS REQUIRED |
| External LOGS | timeout deadline = 10s | PASS REQUIRED |
| External STOP | timeout deadline = 15s | PASS REQUIRED |
| External START | timeout deadline = 30s | PASS REQUIRED |
| External PREPARE | hard cap remains 30min | PASS REQUIRED |
| Internal deadlines | unchanged | PASS REQUIRED |
| Developer Mode source | V04Activity unchanged | PASS REQUIRED |
| W0 / unit tests / assembleDebug | cloud build | PENDING |
| Internal Alpine Probe | cloud probe | PENDING |


### R48-D8 v220 — Environment identity / PREPARE transaction repair

| Area | Verification | Expected |
| --- | --- | --- |
| Plan identity | Add README / __pycache__ / log / output JSON / runtime DB path | Plan ID unchanged when environment facts are unchanged |
| Dependency identity | Change requirements.txt contents | Plan ID changes |
| Plan schema | Shared Environment Plan schema | 2 |
| Runtime compatibility | Python full version changes | Existing External venv NOT_READY |
| Python requirement | requires-python changes | Existing External venv NOT_READY |
| Python extras | install extras change / cannot be proven on legacy marker | Existing External venv NOT_READY |
| Plan metadata only | Plan ID changes with same manifest/runtime/requirement/extras | READY marker rebound in place; no reinstall |
| Interrupted PREPARE | stale .backup-PID exists with invalid/missing final environment | Previous project-scoped rollback recovered before next PREPARE |
| Completed PREPARE residue | valid final venv + READY plus stale backup | stale backup removed |
| STOP during PREPARE | active prepare killed | rollback pair restored project-locally |
| CLEAN | project environment cleanup | final venv + project rollback backups removed |
| Internal R | generated runtime-output paths appear | Shared plan remains stable; backend bindings unchanged |
| Developer Mode source | V04Activity unchanged | PASS REQUIRED |
| W0 / unit tests / assembleDebug | cloud build | PENDING |
| Internal Alpine Probe | cloud probe | PENDING |


### R48-D9 v221 — PREPARE commit-boundary closure

| Area | Verification | Expected |
| --- | --- | --- |
| Commit order | validate venv -> atomic READY marker -> disable rollback -> READY signal | PASS |
| Post-commit cleanup | old venv backup deletion | occurs only after commit |
| Cleanup deadline | opportunistic rollback-backup removal | hard-bounded to 4s |
| Cleanup over budget | backup still contains files after bound | PREPARE remains success; emits cleanup deferred |
| Failure before commit | install / validation fails | existing rollback remains armed and restores old environment |
| Stale backup on next PREPARE | valid final env plus old backup | bounded cleanup; must not block PREPARE for minutes |
| Shared lifecycle | committed External PREPARE | main operation leaves PREPARING after real callback |
| PREPARE success terminal path | PREPARING -> External READY callback | environmentReady=true; coordinator current/persisted operation cleared |
| PREPARE failure terminal path | PREPARING -> failed External callback | ENVIRONMENT_ERROR; coordinator current/persisted operation cleared |
| PREPARE STOP terminal path | PREPARING superseded by project STOP | STOPPED_BY_USER; old PREPARE callback fenced; no active operation residue |
| Developer source boundary | V04Activity.kt | unchanged |
| W0 / unit tests / assembleDebug | cloud build | PASS — Run #642 on 086ce75316263bca21a132d09a68e86294d98f87 |
| Internal Alpine Probe | cloud probe | PASS — Run #111 on production implementation 242273366504106e039e9417ba003823a0b282a8 |

### R48-D10 v222 — Shared External Action Gate

| Area | Verification | Expected |
| --- | --- | --- |
| Save-before-check | Normal/Developer external action starts while readiness is stale | gate request exists before probe dispatch |
| Immediate READY race | cached/fast probe completes during request call | action proceeds exactly once; no lost continuation |
| Async READY | two-stage check finishes later | original PREPARE/RUN/REFRESH resumes automatically |
| Duplicate click | same project/action clicked repeatedly while checking | one generation / one continuation only |
| Late callback | stale READY/result arrives after newer generation | cannot start superseded action |
| STOP priority | project has deferred PREPARE/RUN | only that project's deferred request is cancelled/fenced |
| Project isolation | another project has deferred/active work | unaffected by STOP on first project |
| Probe failure/timeout | provider becomes FAIL/UNRESPONSIVE/UNAVAILABLE | pending action cleared; no false STARTING/PREPARING |
| Freshness expiry | readiness older than 60s | re-probe transparently while retaining original user action |
| PREPARE runtime proof | successful External PREPARE emits SIFTALPHA_ENV=READY | shared Runtime Capability proof timestamp refreshed |
| Immediate RUN after PREPARE | RUN pressed inside fresh window | no redundant external probe |
| Normal Mode | PREPARE / RUN / manual REFRESH | all use shared gate; local pendingUserIntent removed |
| Developer Mode | External PREPARE / START | minimal V04 gate wiring resumes original dispatch once |
| Developer boundaries | layout/Web Discovery/auto observation/log/result/runtime-control | unchanged |
| version | installable build | 0.8.0-alpha43-r48d10 / versionCode 222 |
| W0 / unit tests / assembleDebug | cloud build | PENDING |
| Internal Alpine Probe | cloud probe after version bump | PENDING |
