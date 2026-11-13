# 0.5.6 同步及 GTO 0.6 兼容性记录

原记录日期：2026-11-12。目标分支为 `1.20.1-GTO`，基点为 `c77fa26b`；参考分支为 `1.20.1-GTO-0.5.6`，记录的参考提交为 `4651fafe`。

2026-11-13 整理提交时发现原文件大段中文已变为字面问号。以下按仍可辨认的版本、符号和当前改动重新整理；历史验证结果与本次验证分别记录。

## 同步内容

| 范围 | 当前改动 |
| --- | --- |
| 合成执行 | GTO 订单及临时订单完成判定、ECO CPU 每 Tick 调度、缺料计划提交和供应器阻塞诊断 |
| 规划 | 默认规划固定生产样板，计入副产物；循环开关控制固定路线循环求解 |
| 存储 | 通用存储、混合资源容量统计、盘片优先级、无限模式装饰盘片、创造存储传输过滤及精确数量显示 |
| 界面 | 合成报告提示卡、材料图与图片导出、样板上传按钮、EMI 结构预览及悬停交互 |
| 自动搭建 | 检查空间和材料、报告中断原因、保留兼容的网络交换模块 |

原记录列出的参考提交：`15bf7b36`、`3d3b3181`、`51b53871`、`e4811fba`、`a6783201`、`98bc4a1e`、`ea04378b`、`752119be`、`32cd9156`、`3a411f18`、`1405c54c`、`59690e16`、`6ebd3727`、`0caf2a31`、`81c17978`、`a9d87b2a`、`9d517f87`、`826b9afa`、`fc566704`、`2eb2aae8`、`4651fafe`。另有 `d28a44e3` 和 `936fe9b6`，原说明已损坏，本记录不补推其含义。

## 目标环境与接口

原记录中的编译依赖：GTOCore `26.10.26`、AE2 `15.2610.47`、GTCEu `26.11.11`、LDLib `1.0.52.a`，以及本地配置的 GTOLib。

原记录中的目标实例：`E:\Minecraft\1.20.0\.minecraft\versions\GTO 0.6.27`；GTOCore `26.11.1`、AE2 `15.2611.1`、GTCEu `26.11.08`、EMI `1.1.1034`、LDLib `1.0.52.a`。这些版本来自原记录，本次提交整理未重新检查实例。

GTO 参考仓库使用 `GTOCore-Main`。原记录的 `0.6.0` 提交为 `c6bfb909842a173ec01c869a9949adfaa77edf21`；更新参考仓库不自动改变已批准的移植基线。

| 接口 | 适配方式 |
| --- | --- |
| 合成计划 | 使用 `Object2LongOpenHashMap` 承载 `patternTimes()` |
| CPU 调度 | 原生服务跳过 Tick 时补调 ECO CPU |
| 加工诊断 | 观察 `pushTarget`，选择目标方法时排除 lambda |
| 终端精确数量 | 通过 `sendInventoryUpdate` 采集及同步，配合客户端数量渲染 |
| 创造存储传输 | 识别创造存储源与目标，避免吞入实体库存或重复计入创造资源 |
| 大数量 | 保留精确数量路径并适配 AE2 大数量容器 |
| EMI | 注册结构预览和自定义界面交互，返回界面时保留 UI 绑定 |

## 历史证据与本次检查

原记录列出下列检查及证据位置；本次整理不将这些历史记录视为重新验证通过：

- `build-local.ps1 compileJava processResources jar sourcesJar --offline`
- `build-local.ps1 -GradleArguments @('-I','tools/compat/storage-transfer.init.gradle','checkStorageTransfer','--offline')`
- `python tools/compat/check_artifacts.py --release-only --with-sources`
- `.porting/sync-060-api-evidence.json`
- `.porting/sync-060-build.log`
- `.porting/sync-060-transfer.log`
- `build/compat-build-evidence/artifacts.json`

2026-11-13 本次整理执行 `build-local.ps1 -GradleArguments @('compileJava','processResources','--offline')` 成功，两项任务均为 `UP-TO-DATE`。另外执行上述 `checkStorageTransfer` 命令，7 个传输场景全部通过。未执行游戏内功能验证，不能据此认定目标实例的运行时兼容性已验收。
