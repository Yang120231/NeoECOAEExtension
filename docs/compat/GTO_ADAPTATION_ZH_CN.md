# 1.20.1-GTO 适配与验收

本分支基于 `1.20.1` 的 `9aa5dbc`，仅用于 GregTech Odyssey。参考主仓为
`GregTech-Odyssey/GTOCore-Main`，本地 `E:\Minecraft Project\GTOCore`，本次更新至
`431da7f`。旧 GTOCore 源码及文档不再作为适配依据。main 业务移植基线仍由
`porting/source-lock.json` 固定。

## 目标实例与构建

- 实例：GregTech Odyssey `0.6.0.23`。
- GTOCore：`26.10.9`；GTO GTM：`26.10.70`。
- GTO AE2：`15.2610.31`，校验值及下载位置见 `gto-dependencies.json`。
- Forge：实例 `47.4.20`，本工程编译映射 `47.4.28`；实例使用 Java 21。
- `build-local.ps1` 仍以 Java 17 启动 Gradle。GTO AE2 的 class 版本为 65，
  因此编译工具链选 Java 21，ECO 输出仍使用 `--release 17`。
- 本实例仅装有 ExtendedAE，未安装 AAE、AE2LT，相关条件配方不应强行加载。

```powershell
.\build-local.ps1 jar gtoCompatibilityTest --console=plain
python scripts/generate-gt-recipe-packs.py --check
```

## 配方与 EMI

GTO 的 RecipeManager 在 HEAD 接管加载和同步，缓存命中时也会提前返回；
原先 GTM 的 `GTRecipes.recipeAddition` 入口不再由 GTO 的 commonSetup 调用。
另外，GTO 替换了 EMI 插件发现，ECO 的 `@EmiEntrypoint` 不会自动进入插件列表。

- 从当前启用的资源管理器直接读取 `neoecoae` 命名空间的最终 JSON，绕过
  GTO 在 RecipeManager 之前的 JSON 扫描缓存。经过 Forge 条件和注册的
  serializer 重新解析，再同步替换 `byName` 与按类型索引。
- 重新加载时先移除缓存中旧的 ECO 配方。复制共享缓存，不原地修改 GTO 缓存。
- 客户端在 GTO 丢弃暖缓存同步包前读取独立的 buffer 索引，保留 ECO 配方，
  随后同步重建索引并使大集成目录失效。
- 20 条 GT 引导和矿物处理配方使用 GTO 原生不可变配方 builder，按 ID 检查避免重复注册。
  单体工作站和大集成继续使用已有完整 ECO 加工契约。
- 在 GTO 返回的 EMI 插件列表中补入 ECO 原生插件，按实例类型去重，不依赖 JEI 转换。

## AE2 运行时接口

- 显式 ECO 规划请求继续进入完整 ECO 规划器；替代 GTO 已删除的原版计算类入口。
- 保留 GTO 优化 CPU 作为原生执行引擎，在其构造注入真正完成后安装 ECO 桥接。
  原生任务账本按唯一类型结构解析一次，并缓存 VarHandle，不绑定发行版随机成员名。
- 原生 provider 使用带 holder 和成功回调的派发接口。ECO 单次派发通过 BREAK 停止原生循环，
  接受后失败按结果不确定处理，防止错误退款和重放。
- 大集成 provider 的虚方法需要单独路由：GTO 回调入口原本会绕过其 `pushPattern` 重写。
  适配后继续使用工作站自身的配方、锁定、输入所有权和输出队列。
- GTO 原生异步计算返回的计划明确标记来源，避免与任务向量相同的 ECO 计划发生诊断缓存归属混淆。
  标记 Future 仍委托原执行器处理取消、超时和完成，不增加规划线程。
- 大集成不再强制关闭旧 `smart_doubling` 设置。原生 provider 回调可连续提交后续批次，
  并保留原生 CPU 的任务 UUID，将大集成产出送回所属 CPU。
- GTO 样板修改器 Pro 原先只认原版供应器方块。补充 ECO 通信接口交互，直接调用
  工具原有的样板变换实现，保留其倍率、除数、数量上限和重复次数。
- 适配 GenericStack、原始 long 计数集合、新库存 handler、网格 tick 和界面签名。
- 借鉴 fork 的实现：规划结果直接写入预分配的原始 long map；网络存储列举使用按递归深度
  复用的线程局部累加器，保留嵌套网络隔离和溢出饱和语义。未进行 FPS 或吞吐量基准测试。

## 验收证据与边界

已运行 `jar`、`gtoCompatibilityTest`，14 项专题回归通过，配方生成检查通过。
专题回归覆盖回调记账、接受后失败、专用 provider 虚派发、递归存储列举、重载旧配方清理及缓存隔离。

真实实例第 08 轮启动已验证：

- 成功进入隔离创造存档；服务端、客户端均有 144 条 ECO 配方。
- EMI 实际加载 ECO 插件，单体集成目录 31 条，大集成目录 34 条，20 条原生 GT 配方已注册。
- 打开两类 EMI 配方界面并保存实际 framebuffer 截图：
  `screenshots/neoecoae-gto-emi-integrated.png`、`screenshots/neoecoae-gto-emi-large.png`。
- 用已加载的 `neoecoae:integrated_working_station/module_extendedae` 真实配方，
  单体工作站和完整成形大集成各消耗一份输入，分别产出 1 个模块。
  大集成通过 GTO 原生 provider 回调派发，输出经实际网格存储入口接收，未手动推进加工进度。
- 原生 CPU 桥接和 provider 回调契约检查通过。

默认启动不运行验收探针。只有显式 `-Dneoecoae.gtoAcceptance=true` 才会创建隔离存档、
放置测试结构、打开 EMI 和生成截图。桌面原启动脚本未改写；其账号参数不纳入仓库。
详细本机日志位于被忽略的 `validation/gto`、`logs/gto-*`。

完整旧单测套件尚未迁移：直接创建已被 GTO 删除的原版 CraftingCalculation、
ExecutingCraftingJob 等类的测试仍有编译错误。14 项专题通过不代表完整套件通过。
本轮加工验证不等于全部大型订单、CPU 持久化、所有可选附属和独立专服均已验收。
