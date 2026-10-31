# Neo ECO AE Extension · Minecraft 1.20.1 GTO 专版

[English](README.md) | 简体中文 | [繁體中文](README_ZH_HK.md) | [文言文](README_LZH.md)

Neo ECO AE Extension 是 Applied Energistics 2 的扩展模组，为 ME 网络提供可扩展的合成、存储和计算系统，以及用于加工高级组件的集成工作站。

本分支 `1.20.1-GTO` 基于 `1.20.1` 分支，专门适配 **GregTech Odyssey（GTO）** 的 AE2 与 GregTech 接口。当前版本为 **`20.5.4-gto.1`**，面向 **GTO `0.6.0-dev25`（Dev25）**，以预发布版提供。普通 AE2 整合包请使用本仓库的 `1.20.1` 分支及其对应版本。

## 主要功能

| 系统 | 用途 |
| --- | --- |
| ECO 合成系统 | 通过样板总线、工作核心与并行核心执行自动合成，支持批量处理、超频与主动冷却。 |
| ECO 存储系统 | 通过存储主机、驱动器和存储矩阵扩展 ME 网络容量，支持物品、流体及可选资源类型。 |
| ECO 计算系统 | 为自动合成提供 CPU、任务线程、加速器和任务存储，可随结构扩展。 |
| 集成工作站 | 加工盈能材料、处理器、存储组件和三套系统所需的高级部件。 |
| 大型集成工作站 | 提供批量加工、样板自动化、超频与冷却，并接入受支持附属的加工配方。 |

合成、存储和计算主机均提供自动搭建子窗口，可设置长度与镜像。结构终端物品保留，打开后显示“请使用对应主机 UI 内的搭建按钮。”

大型集成工作站保留**常规、AdvancedAE、ExtendedAE、AE2LT** 四类；AE2LT 的过载加工、闪电装配与闪电模拟共用同一类工作站。对应升级模块按附属 Mod 的实际加载状态注册。ExtendedAE Plus 的其他联动保留，不另设 Plus 大集成类别。

## 适配环境

以下为 `20.5.2-gto.1` 发布说明记录的目标实例组合，不是普通 AE2 的通用兼容范围：

| 项目 | 目标版本 |
| --- | --- |
| Minecraft | `1.20.1` |
| GregTech Odyssey | `0.6.0-dev25`（Dev25） |
| Forge | 实例使用 `47.4.20`；源码构建使用 `47.4.28` |
| Java | 游戏运行使用 **Java 21** |
| GTOCore | `26.10.18` |
| GTO GTM | `26.10.80` |
| GTO AE2 | `15.2610.40` |

GuideME 与 LDLib 为必要依赖，具体声明见 [Mod 元信息](src/main/resources/META-INF/mods.toml)。安装时沿用目标 GTO 整合包的依赖组合；AdvancedAE、ExtendedAE、ExtendedAE Plus 和 AE2LT 为可选联动，使用相应功能时还需安装各自前置。

GTO 专版适配了原生合成 CPU、样板派发、合成确认、精确数量同步和嵌套网络存储接口，并支持 GTO 样板修改器 Pro 修改大型集成工作站通讯接口内的样板。工作站配方支持重载，并提供 EMI 分类。

## 安装与使用

1. 在本仓库 GitHub Releases 中选择 **`20.5.4-gto.1`**，下载 `neoecoae-20.5.4-gto.1.jar`。
2. 将该 JAR 放入目标 GTO 实例的 `mods` 目录，并移除旧版 NeoECOAE JAR。`-sources.jar` 是源码包，不用于安装。
3. 联机时，客户端与服务端均安装相同版本。ECO 网络握手要求两端版本完全一致。
4. 使用 Java 21 启动目标实例，在配方查看器中查询材料与机器配方。

初次游玩可以从盈能水晶和集成工作站开始，再按需求制作存储、计算或合成主机。多方块搭建、冷却与升级说明见游戏内 GuideME，也可阅读仓库中的中文指南：

- [入门与系统概览](guidebook/_zh_cn/neoecoae_intro/index.md)
- [集成工作站](guidebook/_zh_cn/neoecoae_intro/integrated_working_station.md)与[大型集成工作站](guidebook/_zh_cn/neoecoae_intro/large_integrated_working_station.md)
- [合成系统](guidebook/_zh_cn/neoecoae_intro/crafting_system.md)与[合成控制器图文教程](guidebook/_zh_cn/neoecoae_intro/crafting_controller.md)
- [存储系统](guidebook/_zh_cn/neoecoae_intro/storage_system.md)与[兼容存储矩阵](guidebook/_zh_cn/neoecoae_intro/compat_storage_matrices.md)
- [计算系统](guidebook/_zh_cn/neoecoae_intro/computation_system.md)

指南中的交互结构场景和物品、配方组件需在游戏内查看。

## 从源码构建

需要 **JDK 17、JDK 21 和 Python 3**。`build-local.ps1` 使用 Java 17 启动 Gradle；GTO AE2 依赖使用 Java 21 字节码，因此编译工具链为 Java 21，ECO 自身仍以 `--release 17` 输出。游戏运行环境仍按上表使用 Java 21。

在项目根目录使用 PowerShell 执行：

```powershell
# 找不到 Java 17 时，设置为本机实际安装的 JDK 17 目录
$env:NEOECOAE_JAVA17_HOME = 'C:\path\to\jdk-17'

# 首次检出：下载并校验固定依赖，生成本地依赖锁
python tools/compat/bootstrap.py --gto

# 打包安装包、源码包，并运行 GTO 专题回归
.\build-local.ps1 jar sourcesJar gtoCompatibilityTest --console=plain

# 检查发布包内容
python tools/compat/check_artifacts.py --release-only
```

JDK 21 需能被 Gradle 工具链发现；未自动识别时，可通过 Gradle 的 `org.gradle.java.installations.paths` 指定安装目录。Gradle 验证应串行运行，避免共享输出冲突。依赖缓存齐全后，可为构建命令添加 `--offline`。

构建产物位于 `build/libs/`：

- `neoecoae-20.5.4-gto.1.jar`：安装包。
- `neoecoae-20.5.4-gto.1-sources.jar`：源码包。

编译依赖当前固定为 GTO AE2 **`15.2610.31`**，见 [GTO 依赖锁](porting/gto-dependencies.json)；Dev25 实例使用的 **`15.2610.40`** 属于运行适配目标，两者分别记录。`bootstrap.py --gto` 会恢复构建所需依赖，不会自动安装完整 GTO 整合包。

## 目录结构

```text
src/main/java/cn/dancingsnow/neoecoae/
  api/                      ECO 业务与扩展契约
  blocks/                   机器、接口与方块实体
  crafting/                 合成规划与执行
  impl/storage/             存储实现与精确数量处理
  compat/                   AE2、GTO 与可选附属兼容
  gui/                      主机页面与搭建窗口
  platform/                 Forge 1.20.1 平台适配
src/main/resources/         Mod 元信息、资源与配方
src/test/java/              业务与平台测试
src/gtoTest/java/           GTO 专题回归测试
guidebook/                  GuideME 指南与结构场景
docs/compat/                GTO 适配记录与依赖锁
docs/releases/              中英双语发布说明
tools/compat/               固定依赖恢复与产物检查工具
```

`libs/`、`build/` 和 `porting/` 为本地生成或工作目录；其中固定依赖锁 `porting/gto-dependencies.json` 纳入 Git。干净检出所需的构建依赖信息由 `tools/compat/build-dependencies.json` 与该依赖锁提供。

## 验证状态与反馈

本次本地 `jar sourcesJar gtoCompatibilityTest` 与 JAR 检查通过：**86 项专题测试，83 项通过、3 项跳过、0 失败**；另有 **34 项循环规划专项回归全部通过**。跳过项需要提供实际安装的 AE2/GTOCore JAR；本次未重新运行完整客户端实例和独立专服。详见 [20.5.4-gto.1 发布说明](docs/releases/20.5.4-gto.1.md)。

完整旧测试套件、全部附属组合和独立专服尚未完成验收。部分旧测试仍依赖 GTO 已删除的原版 AE2 类，因此上面的构建命令使用独立的 `gtoCompatibilityTest`，其结果不代表完整 `test` 或 `build` 已通过。

反馈问题时，请附上 ECO 版本、GTO 整合包版本、实际安装的 GTOCore／GTM／AE2 版本、复现步骤，以及 `logs/latest.log` 或对应崩溃报告。

## 作者与许可

作者：DancingSnow、ZhuRuoLing、Yang120231、y271727uy。

本项目采用 [GNU GPL v3](LICENSE)。
