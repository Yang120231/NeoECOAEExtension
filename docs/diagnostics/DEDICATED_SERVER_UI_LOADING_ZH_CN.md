# 专服 UI 客户端类加载修复

## 问题

`latest (7).log` 中，2026-11-12 01:14:21 右键存储主机后，服务端通过
`StorageHostActionUI`、`StoragePriorityUI`、`NeoUiJsonLoader` 加载 `NETextures`，
最终在 `IGuiTexture` 静态初始化时解析空纹理 lambda 的 `GuiGraphics` 参数，
抛出 `BootstrapMethodError`。01:15:22 的看门狗报告发生在崩溃和保存世界之后。

启动时 LDLib 扫描 `PatternItemSlot` 的 `LocalPlayer` 错误来自其公共父类
`ItemSlot` 中未隔离的客户端提示方法。

## 修复范围

- 同步至 `1.20.1-GTO` 和 `1.20.1-GTO-0.5.6`，复用普通 `1.20.1`
  分支提交 `70143e23` 已有的分端实现，仅移植相关修复。
- 空纹理改为具名实现，绘制方法在专服由 Forge 移除；其他纹理绘制方法同样补齐分端标记。
- 隔离客户端槽位提示、默认玩家 UI 构造器和菜单同步读取方法。
- 服务端不进入客户端 UI 反射桥接，也不读取客户端资源管理器。
- 保留服务端菜单树、槽位和数据同步逻辑，以及客户端绘制与提示功能。

## 验证边界

- 两个分支均通过 `build-local.ps1 build` 编译和打包。
- 使用真实 Forge `RuntimeDistCleaner`，屏蔽 Minecraft 客户端和 Blaze3D 类：
  原版空纹理 lambda 复现失败，修复后的共享纹理初始化、复制和组合通过。
- 检查专服移除客户端菜单 reader 及其编译器桥接，客户端保留两个 reader；
  显式玩家构造器在专服保留，客户端便利构造器和槽位提示仅在客户端保留。
- 发布 JAR 和源码 JAR 通过 `tools/compat/check_artifacts.py --with-sources`。
- 本轮没有启动真实专服或客户端，没有验证真实玩家打开、收包和显示效果。
- 验证工具位于忽略目录，不进入发布 JAR；没有新增自动化测试源集。
