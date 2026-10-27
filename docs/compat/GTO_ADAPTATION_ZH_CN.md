# 1.20.1-GTO 适配与验收

本分支基于 `1.20.1` 的 `9aa5dbc`，仅用于 GregTech Odyssey。参考主仓为
`GregTech-Odyssey/GTOCore-Main`，本地 `E:\Minecraft Project\GTOCore`，本次更新至
`431da7f`。旧 GTOCore 源码及文档不再作为适配依据。main 业务移植基线仍由
`porting/source-lock.json` 固定。

## 集成工作站退出与 GTO 制造路线（2026-10-27）

本阶段参考仓库更新至 `GTOCore-Main d1a1750`，同时核对 Dev25 实例的
GTOCore `26.10.18`、GTO GTM `26.10.80`；不改变 main 移植基线。

GTO 配方档案覆盖移除全部 56 条普通集成、5 条大集成配方，以及 GT 档案额外添加的
4 条模块配方。普通主机、大集成四种专用部件和五种模块不再有制造配方，
并从创造栏、EMI/JEI 物品列表、工作站分类及多方块展示中隐藏。注册和旧存档数据保留。
ECO 自有配方重载时还会拒绝旧工作站类型，防止 GTO 暖缓存恢复旧路线。

`scripts/gto-workstation-plan.json` 是本阶段 26 条产品路线的唯一配方表，
由 `scripts/generate-gt-recipe-packs.py` 编译到 `gt_gto/native_recipes.json`。
GTO 覆盖包加载在基础 GT 档案之后；其他 GT 档案的原有制造方式保持。
Omni、MEGA 不加入 GTO 制造表，也不增加对应依赖。

| 产品 | 数量 | GTO 路线 |
| --- | ---: | --- |
| 盈能水晶、盈能福鲁伊克斯水晶 | 2 | HV 高压釜，480 EU/t，5 秒，单次 8 个 |
| 水晶锭 | 1 | HV 搅拌机，480 EU/t，20 秒，单次 4 个 |
| 盈能超导锭 | 1 | EV 搅拌机，1,920 EU/t，20 秒，单次 4 个；使用汞钡钙铜氧超导锭 |
| 凛冰溶液 | 1 | HV 搅拌机，480 EU/t，10 秒，单次 1,000 mB；支持纯流体输出 |
| 合成、计算、存储三类主机 | 9 | L4 EV 组装机 30 秒；L6 IV 组装机 45 秒；L9 LuV 装配线 60 秒 |
| ECO 存储组件 | 3 | 16M EV、64M IV、256M LuV 组装机，20/30/45 秒 |
| CE4/CE6/CE9 闪存晶阵 | 3 | EV/IV/LuV 电路组装机，洁净室，20/30/45 秒 |
| 两种普通网络交换机 | 2 | EV 组装机，30 秒 |
| 两种高能网络交换机 | 2 | LuV 装配线，60 秒，保留原 AE 256K 组件投入 |
| 无限基础资源存储矩阵 | 1 | LuV 组装机，60 秒 |
| 无限存储组件 | 1 | 沿用下节完整 GTO 原生装配线配方及科研条件 |

EV/IV/LuV 分别使用 1,920/7,680/30,720 EU/t，电路和 GT 零件同步按该等级选取；
主机配方继续消耗上一级主机。原有无限组件复制、组件互认和双向互换保留。

本阶段编译、`jar`/`reobfJar`、配方生成检查通过；GTO 专项共 56 项通过，
0 失败、0 跳过，其中新增 5 项覆盖完整覆盖包效果、26 种产品唯一制造路线、
等级门槛、机器输入规模、冷却液输出和旧配方拒绝。尚未安装新包或进行游戏内交互验收。

## 无限存储组件配方与互换（2026-10-27）

GTO 档案下，ECO 无限存储组件改为原生装配线配方，直接复制实例注册的
`gtceu:infinite_cell_component` 定义，仅将产物替换为 1 个 ECO 无限存储组件。
配方材料、三种流体、耗电、时长以及原有研究条件均继承原定义，不另外生成研究路线。
对应截图的原版配方为：

- 256M 存储组件 64 个、数据库 16 个、神经矩阵 16 个、创造能量单元 64 个；
- UEV 电路 64 个、钌三钛镅中子素酸盐十六倍导线 64 个、中子素双重板 4 个；
- 突变活性焊料 20,000 mB、对立合金 20,000 mB、多氯联苯冷却液 100,000 mB；
- UHV，1,966,080 EU/t，2,400 tick（120 秒），总计 4,718,592,000 EU。

GTO 档案覆盖移除原来的 ECO 工作站低成本配方。两种组件加入
`neoecoae:infinite_cell_components`，供 ECO 主机直接识别；GTO 原生配方的普通组件输入
改用同一标签。GTO 库存原本接受原生组件的槽位同时接受 ECO 组件，并保留原过滤器的
数量和 NBT 限制。另有双向 1:1 无序合成，方便已有物品以及仍按具体物品检查的其他用途。

新增回归覆盖配方复制及研究继承、输入数量、三流体、原定义隔离、共享标签、双向互换、
库存限制以及实例 GTM JAR 的注入接口。编译和专题测试不等于新包的游戏内验收。
本次 `gtoCompatibilityTest` 共 51 项通过、0 失败、0 跳过；`jar`、`reobfJar`、
配方生成检查及 JAR 内的新增类和配方资源核对均通过。尚未安装新包或进行游戏内验证。

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

## Dev25 合成确认注入兼容（2026-10-27）

核对 `0.6.0.25` 实例实际安装的 `appliedenergistics2-forge-1.20.1-15.2610.40.jar`，
SHA-256 为 `b0d97544e618d73ec0c8d8e54df36351bf7f98f7e1984c6455d81658b6320b5c`。
GTO AE2 参考源码已更新到 `974a922`，GTOCore-Main 更新到 `2cd0d07`；
编译依赖仍固定在 `.31`，main 业务移植基线保持 `porting/source-lock.json` 的提交。

`.31` 在 `startJob()` 中调用 `ICraftingService.submitJob`；`.40` 将该调用移入
`startJob(boolean)`，无参入口仅转调布尔重载。旧的裸方法名选择器只选择首个重载，
导致 `CraftConfirmMenuMixin` 的 `neoecoae$confirmedSubmission` 必需注入组命中 0 次。
提交包装器改为 `startJob*`，继续保留 `min = 1, max = 1`，确保实际提交恰好包装一次，
并完整保留 ECO 计划归属、循环执行上下文及原生提交参数。

新增回归检查实际编译依赖、委托重载布局及可选的实例 AE2 JAR，均使用 Mixin 自身的方法选择器。
外部 JAR 通过环境变量 `NEOECOAE_GTO_AE2_JAR` 指定，并计入 Gradle 测试输入。
修复前，重载布局和实例 `.40` 两项检查均复现命中 0 次；修复后，
`gtoCompatibilityTest` 共 23 项全部通过（无跳过），`jar` / `reobfJar` 成功。

此次未启动 Dev25 客户端、进入世界或执行真实合成确认交互；这项修复不代表完整 Dev25 兼容验收。

## Dev25 合成 CPU 界面桥接（2026-10-27）

实例 GTO Core 26.10.18 的合成 CPU 界面在读取暂停状态、设置暂停状态、同步派发结果和查询
待处理位置时，均将 CPU 逻辑直接转换为 GTO 原生实现。NeoECOAE 的 CPU 包装器不属于该类型，
打开合成状态界面时因此触发 ClassCastException。

针对这四处字段读取返回原生委托，保持原有 GTO 暂停、结果同步和位置查询逻辑，未关闭相关功能。
注入要求恰好命中四处；实例 JAR 的字节码回归检查以及委托和合并后方法名检查均已通过。
隔离工作树使用实例 AE2 15.2610.40 与 GTO Core 26.10.18 进行 26 项 GTO 专项测试，全部通过；
jar 和 reobfJar 完成。隔离包与先前实例 JAR 比对，变化仅涉及本次 CPU 桥接及其 Mixin 配置。

修复包 SHA-256：DEB3345649CD00E9C273E68D30305517280DF26B3D1F813C017245E9EBAADF9C。
已安装到 0.6.0.25 实例的 mods，原 JAR 重命名为 .jar.disabled 备份。
本次未启动游戏、进入存档或实际操作暂停按钮和合成状态界面，运行时验收仍需进行。

## Dev25 CPU 界面桥接的注入优先级修复（2026-10-27）

14:38:57 的真实实例日志显示，上述桥接以优先级 500 向 GTO 优先级 1000 的已合并方法执行
`FIELD` 重定向，被 Mixin 0.8.5 的优先级检查拒绝，导致客户端在注册阶段崩溃。
原有目标计数测试没有执行该优先级检查，因此此前的 26 项通过不能证明运行时注入成功。

将本桥接优先级调整为 1100，仍要求恰好命中四处字段读取，不跳过 Mixin，也不关闭 GTO 的暂停、
结果同步和待处理位置功能。Mixin 在准备注入前已完成全部方法合并，因此动态选择器仍能找到 GTO 方法。
新增回归测试读取实例 GTO Core JAR 的实际 Mixin 优先级，并执行 Mixin 自身的 FIELD 优先级判断：
旧值 500 复现失败，修复后 GTO 专项共 27 项通过、0 跳过，`jar` / `reobfJar` 成功。

部署包为 `neoecoae-20.5.2-gto.1-cpu-menu-priority-fix.jar`，以先前已部署的 CPU 修复包为基础，
仅替换本次重新编译并重混淆的 `GtoCraftingCpuMenuMixin.class`；全部其他 ZIP 条目内容保持一致。
这是为了保留实例现有物品注册行为，避免将当前源码中另有的 `NEItems` 注册差异一并部署。
SHA-256：`EA863F9CED778B0149DDAC41A2364EB40771A36620A3AC2B2C290967307AA2BC`。
实例旧包已保留为 `neoecoae-20.5.2-gto.1-cpu-menu-fix.jar.disabled`，mods 中仅启用一个 NeoECOAE JAR。

实际使用该实例的 Forge 47.4.20、AE2 15.2610.40、GTO Core 26.10.18 及完整 mods 启动离线测试客户端。
14:56:12 导出的运行时 `CraftingCPUMenu` 确认四处重定向全部存在，并调用原生 CPU 解包委托；
客户端随后通过注册阶段，完成资源重载、声音引擎初始化和纹理图集创建，未再出现本次 Mixin 致命错误。
证据位于 `build/acceptance/gto-cpu-priority/`，包含原日志备份、包内容比对、运行日志及导出类反汇编。

首次诊断启动额外打开了全局 `mixin.debug.countInjections`，使 Ars Nouveau 自身的可选注入目标检查失败；
撤去该全局诊断开关后重新启动，上述四处命中仍由桥接自身的 `require=4` / `allow=4` 严格约束。
Computer Use 本机管道不可用，未做窗口截图、进入存档或暂停按钮的实际交互验收。

后续用户确认修复正常，要求停止追加测试并发布。发布编号与适配目标分开记录：
ECO 版本保持 `20.5.2-gto.1`，标签与安装包为 `20.5.2-gto.1` 和 `neoecoae-20.5.2-gto.1.jar`；
上游整合包版本 `0.6.0-dev25` 仅用于说明支持 GTO Dev25，不作为 ECO 版本号。
发布说明参照 `20.5.0-gto.1` 发布页的中英双语内容和结构，保留既有支持能力并补充本次修复。
本次核对实例的 GTO GTM 版本为 `26.10.80`，AE2 为 `15.2610.40`，GTOCore 为 `26.10.18`。
预发布包由当前完整源码重新打包，
包含此前已提交的按附属加载状态注册工作站模块逻辑；上述单类替换热修复包仅用于本次实例排障部署。
