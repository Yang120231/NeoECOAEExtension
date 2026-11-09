# Neo ECO AE Extension · Minecraft 1.20.1 GTO 专版

[English](README.md) | 简体中文 | [繁體中文](README_ZH_HK.md) | [文言文](README_LZH.md)

Neo ECO AE Extension 是 Applied Energistics 2 的扩展模组，为 ME 网络提供可扩展的合成、存储和计算系统，以及用于加工高级组件的集成工作站。

本分支 `1.20.1-GTO` 基于 `1.20.1`，仅面向 **GregTech Odyssey（GTO）**。当前源码版本为 **`20.5.5-GTO`**，以 **GTO `0.6.26`** 为参考实例。普通 AE2 整合包请使用 `1.20.1` 分支。

## 主要功能

| 系统 | 用途 |
| --- | --- |
| ECO 合成系统 | 通过样板总线、工作核心与并行核心执行自动合成，支持批量处理、超频与主动冷却。 |
| ECO 存储系统 | 通过存储主机、驱动器和存储矩阵扩展 ME 网络容量，支持物品与流体。 |
| ECO 计算系统 | 为自动合成提供 CPU、任务线程、加速器和任务存储，可随结构扩展。 |
| 集成工作站 | 加工盈能材料、处理器、存储组件和三套系统所需的高级部件。 |
| 大型集成工作站 | 提供批量加工、样板自动化、超频与冷却，使用本工程与 GTO 加工配方。 |

合成、存储和计算主机均提供自动搭建子窗口，可设置长度与镜像。结构终端物品保留，打开后显示“请使用对应主机 UI 内的搭建按钮。”

大型集成工作站使用本工程与 GTO 配方，无需附属升级模块。

## 适配环境

下表记录当前 GTO 参考实例的版本，用于实际 JAR 接口核对，不代表已完成游戏内验收：

| 项目 | 目标版本 |
| --- | --- |
| Minecraft | `1.20.1` |
| GregTech Odyssey | `0.6.26` |
| Forge | 实例使用 `47.4.20`；源码构建使用 `47.4.28` |
| Java | 游戏运行使用 **Java 21** |
| GTOCore | `26.10.26` |
| GTO GTM | `26.11.03` |
| GTO AE2 | `15.2610.47` |

GuideME 与 LDLib 为必要基础依赖，具体声明见 [Mod 元信息](src/main/resources/META-INF/mods.toml)。本专版仅保留 GTO 联动。

GTO 专版适配原生合成 CPU、样板缓存批量投料、容量检查、编程电路、加工状态、CPU 列表同步、精确数量和嵌套网络存储。GTO 样板修改器 Pro 可修改通讯接口内的样板，工作站配方支持重载。

## 安装与使用

1. 从 GitHub Releases 下载对应版本，或按下文从当前源码构建 `neoecoae-20.5.5-GTO.jar`。
2. 将该 JAR 放入目标 GTO 实例的 `mods` 目录，并移除旧版 NeoECOAE JAR。`-sources.jar` 是源码包，不用于安装。
3. 联机时，客户端与服务端均安装相同版本。ECO 网络握手要求两端版本完全一致。
4. 使用 Java 21 启动目标实例，在配方查看器中查询材料与机器配方。

初次游玩可以从盈能水晶和集成工作站开始，再按需求制作存储、计算或合成主机。多方块搭建、冷却与升级说明见游戏内 GuideME，也可阅读仓库中的中文指南：

- [入门与系统概览](guidebook/_zh_cn/neoecoae_intro/index.md)
- [集成工作站](guidebook/_zh_cn/neoecoae_intro/integrated_working_station.md)与[大型集成工作站](guidebook/_zh_cn/neoecoae_intro/large_integrated_working_station.md)
- [合成系统](guidebook/_zh_cn/neoecoae_intro/crafting_system.md)与[合成控制器图文教程](guidebook/_zh_cn/neoecoae_intro/crafting_controller.md)
- [计算系统](guidebook/_zh_cn/neoecoae_intro/computation_system.md)

指南中的交互结构场景和物品、配方组件需在游戏内查看。

## 从源码构建

需要 **JDK 17、JDK 21 和 Python 3.11 或更新版本**。`build-local.ps1` 使用 Java 17 启动 Gradle；GTO AE2 依赖使用 Java 21 字节码，因此编译工具链为 Java 21，ECO 自身仍以 `--release 17` 输出。游戏运行环境仍按上表使用 Java 21。

在项目根目录使用 PowerShell 执行：

```powershell
# 找不到 Java 17 时，设置为本机实际安装的 JDK 17 目录
$env:NEOECOAE_JAVA17_HOME = 'C:\path\to\jdk-17'

# 首次检出：下载并校验固定依赖，生成本地依赖锁
python tools/compat/bootstrap.py --gto

# 在被 Git 忽略的 local.properties 中配置匹配的本地 GTOLib 开发 JAR：
# gtolibLocalJar=C:/private/build/gtolib/gtolib-local-compile.jar

# 打包安装包与源码包
.\build-local.ps1 jar sourcesJar --console=plain

# 检查发布包内容
python tools/compat/check_artifacts.py --release-only --with-sources
```

JDK 21 需能被 Gradle 工具链发现；未自动识别时，可通过 Gradle 的 `org.gradle.java.installations.paths` 指定安装目录。Gradle 验证应串行运行，避免共享输出冲突。依赖缓存齐全后，可为构建命令添加 `--offline`。

构建产物位于 `build/libs/`：

- `neoecoae-20.5.5-GTO.jar`：安装包。
- `neoecoae-20.5.5-GTO-sources.jar`：源码包。

编译与实际 JAR 核对使用 GTO AE2 **`15.2610.47`**，见 [GTO 依赖锁](porting/gto-dependencies.json)。`bootstrap.py --gto` 会恢复构建依赖，不会自动安装完整 GTO 整合包。

### 本地构建与 Release 校验

GTOLib 开发依赖仅在本地编译时使用，不能提交、上传或包含在 ECO 安装包、源码包内。公开 CI 不运行 Gradle，也不下载或构建 GTOLib。

1. 在本地完成构建，并运行上述安装包与源码包检查。
2. 推送对应 ECO 源码和标签，标签必须与 `gradle.properties` 的 `mod_version` 一致。
3. 创建该标签的草稿 Release，填写发布说明，并只上传 `build/libs/neoecoae-<版本>.jar` 和 `build/libs/neoecoae-<版本>-sources.jar`。
4. 发布 Release。CI 下载这两个附件，核对版本、源码及私有内容，成功后附加 `SHA256SUMS.txt` 并保存校验报告。

缺少附件或校验失败时流程会失败，不会自动补建。补齐或修正附件后，可重新运行失败的工作流，或在 Actions 中手动运行 `Verify Local Release Artifacts` 并填写已发布的标签。CI 校验不会撤回已发布的 Release，也不代表游戏内运行验收通过。

## 目录结构

```text
src/main/java/cn/dancingsnow/neoecoae/
  api/                      ECO 业务与扩展契约
  blocks/                   机器、接口与方块实体
  crafting/                 合成规划与执行
  impl/storage/             存储实现与精确数量处理
  compat/                   GTO 与原生 AE2 适配
  gui/                      主机页面与搭建窗口
  platform/                 Forge 1.20.1 平台适配
src/main/resources/         Mod 元信息、资源与配方
guidebook/                  GuideME 指南与结构场景
docs/compat/                GTO 适配记录与依赖锁
docs/releases/              中英双语发布说明
tools/compat/               固定依赖恢复与产物检查工具
```

`libs/`、`build/` 和 `porting/` 为本地生成或工作目录；其中固定依赖锁 `porting/gto-dependencies.json` 纳入 Git。干净检出所需的构建依赖信息由 `tools/compat/build-dependencies.json` 与该依赖锁提供。

## 验证状态与反馈

旧版 `20.5.4-gto.1` 的测试记录见其 [发布说明](docs/releases/20.5.4-gto.1.md)。当前工程已移除自动化测试源码与自定义 Gradle 测试任务，构建检查使用 `jar sourcesJar` 和产物检查脚本；客户端、专服启动与真实交互需要单独验收。

反馈问题时，请附上 ECO 版本、GTO 整合包版本、实际安装的 GTOCore／GTM／AE2 版本、复现步骤，以及 `logs/latest.log` 或对应崩溃报告。

## 作者与许可

作者：DancingSnow、ZhuRuoLing、Yang120231、y271727uy。

本项目采用 [GNU GPL v3](LICENSE)。
