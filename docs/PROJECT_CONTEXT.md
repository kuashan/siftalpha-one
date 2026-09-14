# SiftAlpha Studio 项目上下文

最后更新：2026-09-14  
当前仓库：[kuashan/siftalpha-one](https://github.com/kuashan/siftalpha-one)  
当前分支：`main`  
当前版本：`0.8.0-alpha14` / `versionCode 90`  
最新发布：[W2 test APK · w2-test-0ee299e](https://github.com/kuashan/siftalpha-one/releases/tag/w2-test-0ee299e)

## 1. 项目目标

SiftAlpha Studio 是一个 Android 端项目工作台，用于在手机上：

- 导入和管理项目；
- 准备、运行、观察、停止、恢复项目；
- 使用 Python 作为第一优先运行路径；
- 支持 Node.js 独立或辅助运行路径；
- 读取运行状态、日志和输出；
- 检测本地可视化页面，并在端点真正可达时通过浏览器打开；
- 维护项目环境、配置、缓存和运行数据。

项目采用“手机真机使用 + GitHub 云端构建”的工作方式。开发环境不要求本地安装 Gradle、JDK 或 Android SDK。

## 2. 当前阶段

路线仍按 W0 → W1A → W1B → W1C → W2 → W3 → W4 → W5 推进。

| 阶段 | 当前状态 | 说明 |
|---|---|---|
| W0 | 已完成 | GitHub Actions 云端校验、构建、APK、签名和发布证据已经建立。 |
| W1A | 基本完成 | Compose、Material 3、主题和设置能力已有，但部分旧页面仍是 Views。 |
| W1B | 核心完成，持续收敛 | 已有状态快照、动作策略、生命周期和安全守卫；协调逻辑仍需继续从 Activity 收敛。 |
| W1C | 已完成主要验收 | 首页、导航、项目列表和筛选能力已落地并完成真机验证。 |
| W2 | 进行中 | Runtime 能力和项目工作区基础已经存在；当前重点是配置语义、工作区整合和完整任务闭环。 |
| W3 | 部分完成 | 输出、脱敏、失败原因和日志能力已有，结构化诊断体验仍需完善。 |
| W4 | 底层能力较完整 | 环境、工具、缓存和清理保护已有，统一环境页面和完整验收仍待完成。 |
| W5 | 部分完成 | 编辑器、设置、语言机制已有；首次使用、无障碍、升级连续性等仍需验收。 |

## 3. 不可破坏的产品规则

### 3.1 状态必须分开表达

环境、配置、进程生命周期和 Web 可用性是四种不同事实，不能合并成一个“全部就绪”。

### 3.2 项目身份必须可信

项目操作使用 SAF 的稳定 `documentId`，不能使用项目名称、列表下标或临时排序位置作为身份。

### 3.3 运行安全

- PREPARE 与 START 分开，准备完成后由用户主动运行。
- 运行中的项目始终保留 STOP 能力。
- UI 按钮禁用只是反馈，底层动作入口仍必须再次检查。
- STOP 完成后，项目必须能够再次 START。
- 不确定状态时优先 STATUS 恢复，不能凭旧界面猜测进程是否存在。
- 清理项目环境、共享工具、缓存和删除源码必须分开确认。

### 3.4 Web 安全

Browser 只有在当前项目进程存在、Android 回环端点真实可达并且 URL 已验证时才开放。检测到“可能是网页”不等于允许打开浏览器。

### 3.5 隐私与配置

运行输出、配置值和日志不得泄露密钥。Studio 保存的配置使用 Android Keystore 保护并在运行时注入，不写回项目源码或 GitHub。

## 4. 当前架构基线

主要职责分布：

- `V04Activity)：Runtime Center 的页面组装、用户动作入口和结果协调；正在持续拆分，避免继续膨胀。
- `ProjectUiSnapshot`：单项目不可变 UI 事实快照。
- `ProjectActionPolicy`：根据快照统一决定 PREPARE、START、STOP、STATUS、LOGS、CONFIGURE、Browser 等动作。
- `ProjectConfigurationInspector`：读取项目元数据、`.env`、`.env.example` 和 Python 环境变量读取。
- `ProjectConfigurationUiController`：配置列表、必填向导、安全保存和运行时配置提示。
- `ProjectConfigurationPreflight`：纯配置就绪判断。
- `RuntimeConfigurationDiagnostic`：只从运行输出中识别高可信的缺失配置，不猜测变量名。
- `ProjectSecretStore`：Android Keystore 保护的本地配置存储。
- `ProjectStore` / `V04ProjectGateway`：SAF 项目树、文件和项目身份访问。
- `WebProjectInspector`、`RuntimeWebAvailabilityTracker`：网页能力检测与端点可用性验证。
- `ProjectRuntimeController`：生成 Python/Node 等运行命令。

## 5. 配置语义

配置检测分为“必需项”和“候选提醒”两层：

1. `.project.json.requiredEnv` 是项目显式声明的权威来源。
2. Python 的 `os.environ["NAME"]` 属于高可信必需读取。
3. `os.getenv("NAME")`、`os.environ.get("NAME")` 和 `.env.example` 只产生候选提醒，因为静态代码无法可靠判断它们是否真的影响运行。
4. `NAME` 已在项目 `.env` 中有非空值，或已由 Studio 安全保存，则视为已配置。
5. 运行结果明确报告缺失的环境变量时，该变量会成为当前修复周期的必需项，并影响下一次启动前预检。
6. 普通硬编码 URL 不会自动判定为需要用户配置；只有通过项目声明、环境变量或运行结果明确要求时才进入配置流程。
7. 候选项显示提醒但不阻止运行。只有真实必需项缺失时，才显示必填向导或在后续运行前阻止 START。

## 6. 构建与发布基线

工作流：[.github/workflows/w0-cloud-build.yml](../.github/workflows/w0-cloud-build.yml)

云端流程包括：

1. GitHub Actions 安装 JDK、Android SDK 和 Gradle；
2. 恢复稳定测试签名；
3. 执行仓库校验器；
4. 执行单元测试并组装 APK；
5. 记录 APK SHA-256、签名证书和包信息；
6. 只在提交消息严格为 `Publish W2 test APK` 时创建测试 Release。

稳定测试签名的密码只存在于 GitHub Actions Secret 和云端运行环境，项目文档不保存密码。当前已确认的证书 SHA-256 摘要为：

`3bf440487ce3f9010c5843fc1b46abc79e105886ce339c4c075e7635c5542928`

## 7. 当前基线与下一步

当前最新配置修正版：

- 实现提交：[788a235](https://github.com/kuashan/siftalpha-one/commit/788a235abc653df7eb6b479e03868ade9c1deaeb)
- 发布触发提交：[0ee299e](https://github.com/kuashan/siftalpha-one/commit/0ee299e0635e5ad8fcb23098bb466b9677eba00e)
- 云端构建：[Run #27](https://github.com/kuashan/siftalpha-one/actions/runs/34880577089)
- APK：[直接下载](https://github.com/kuashan/siftalpha-one/releases/download/w2-test-0ee299e/app-debug.apk)
- APK SHA-256：`a1550c94aa8a6183f3705e8d3450591eeb5beac4ec9fbdf55965b7603ec3b6a7`

下一步按优先级：

1. 用两个真实脚本验证候选项只提醒、不阻止运行。
2. 验证可选项直接进入列表，不强制打开多步填写向导。
3. 验证运行时明确缺失项会在下一次启动前变成必需项。
4. 回归 STOP → START、Chrome 返回、配置保存和覆盖安装。
5. 继续完善单项目工作区和 W2 的完整任务闭环。

## 8. 记录维护约定

- 开发行为写入 [DEV_LOG.md](DEV_LOG.md)。
- 测试状态写入 [TEST_MATRIX.md](TEST_MATRIX.md)。
- 任何“通过”必须注明是云端通过、真机通过，还是用户确认通过。
- 不把旧仓库、旧分支、旧版本号重新当作当前基线。
