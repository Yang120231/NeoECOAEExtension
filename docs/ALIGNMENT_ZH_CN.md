# 与 1.21.1 主线的对齐：1.20.1-GTO

本文件记录 GTO 版相对 1.21.1 主线的差异，分为特殊（不对齐）、落后（要补）、领先（主线没有）三类。主线的总表在 `NeoECOAEExtension-1.21.1/docs/ALIGNMENT_ZH_CN.md`；与 plain 1.20.1 共有的差异也可参考 `NeoECOAEExtension-1.20.1/docs/ALIGNMENT_ZH_CN.md`。

- 调查日期：2026-11-11
- 本版基线：`8bdd779c`（分支 `1.20.1-GTO`，20.5.6-GTO）
- 主线基线：`2e494131`
- 调查方法：文件清单、标识符计数、行数和提交标题对照。同名文件没有逐行比对，没有运行构建或游戏。
- 路径均相对于 `src/main/java/cn/dancingsnow/neoecoae/`。

**水位**：主线 11-10 前后的核心修复已同步（产物回流、请求器循环订单、递归合成报告、存储增量传输）；工作站停在主线 21.2.13 之前。

**调查时的未提交改动**：31 个文件（+425/−181），是在移植主线的精简合成模式（`ec35bf8f`、`7c7acbd4`）。已核对 `ECOCraftingPlannerService.requiresFullPlanning` 与主线逻辑一致：开了循环规划且有环、或命中忽略组件的输入时走完整规划。`CasingBlock` 的改动可能对应主线 `b2f9d6bc`（机壳碰撞体积）。

## 特殊（不对齐）

| 项 | 位置 |
| --- | --- |
| 强依赖 GTOCore、GTOLib、datasynclib、GTCEu、GTO 版 AE2（`15.2610.47`）、EMI | `src/main/resources/META-INF/mods.toml` |
| 接入 GTOCore 的 CPU 逻辑、样板推送、机器状态、加工发配、配方、存储列表 | `compat/gto/`（13 个文件）、`Gto*Mixin`、`platform/mixin/*Gto*` |
| 有意裁剪联动：只保留 EMI 和 LDLib（`68cee5e1`） | `integration/` 只有 3 个文件；`compat/` 和 `mixins/compat/` 下的其他模组联动、对应配方与约 100 条语言键都已删除 |
| 工作站附属模块裁剪：`LargeWorkstationModule` 只剩 `REGULAR`，配方改由 GTO 提供 | `recipe/profile/GtoRecipePack`、`compat/gto/ECOGtoRecipes` |
| 存储代码在 `impl/storage/`，含按盘 UUID 的 SavedData 持久化 | `impl/storage/`（40 个文件）、`impl/storage/persistence/` |
| Forge 兼容层与 LDLib1 界面运行时（与 plain 1.20.1 相同） | `platform/`（115 个文件）、`gui/ldlib/` |
| 依赖锁与本地 GTOCore 源码检出 | `porting/dependencies.lock.json`、`porting/gto-dependencies.json`、`tools/compat/`；`.porting/gtolib-dev/` 被 git 忽略，不属于本仓库 |
| `CraftingServiceMixin` 用 `CraftingServiceEarlyBridge` 加 `LocalLongRef` 的写法；`currentlyCrafting` 是 GTO 的 `AEKeySet` | `mixins/ae2/crafting/` |
| 没有 key 复用、开发工具、大宗存储 | 主线的 `compat/ae2/keyreuse/`、`dev/`、`storage/bulk/` 在本版都不存在 |
| 没有单元测试（`f155019f`） | |
| 指南缺联动存储矩阵页 `compat_storage_matrices.md` | 联动已裁剪 |

大宗存储、key 复用、开发工具是否永久不要，没有找到书面决定；如果要补，移到落后。

## 落后（主线有，本版要补）

### 工作站

1. **智能冷却模块**（主线 21.2.13，及 `7952b49f`、`2a15bf47`）。完全没有：缺方块 `LARGE_INTEGRATED_WORKING_STATION_SMART_COOLING_MODULE`、`CRYOGENIC_ALUMINUM_ALLOY_CASING`，物品 `PHASE_CHANGE_AIR_COOLER`，`gui/workstation/SmartCoolingModuleUI`，配置 `smartCoolingRefillThresholdPercent`，约 42 条 `gui.smart_cooling` 语言键。plain 1.20.1 已有（`e4851d92`、`23f31601`）。
2. **工作站供能卡片与冷却、供能调整**（主线 `6198946a`、`e815cfdd`、`e7cb1a88`）。缺 `gui/workstation/LargeWorkstationPowerPriorityCard`。plain 1.20.1 已有（`5add91e6`）。
3. **工作站模型和动态配色**（主线 `7d7c001a`）。缺 `client/model/WorkstationModuleBakedModel`、`client/texture/WorkstationSpriteSource`、`blocks/ECOLargeIntegratedWorkingStationCasing` 及其方块实体。plain 1.20.1 已有（`f50999a3`）。
4. **输入仓网络补货**。`ECOFluidInputHatchBlockEntity` 只有 59 行（主线 190 行）；缺 `FluidHatchNetworkPull`、`NETWORK_PULL_CARD`、`gui/common/FluidInputHatchUI`、`util/LongFluidTank`、约 13 条语言键。plain 1.20.1 已有（`b0062e51`）。
5. **配置项**：`largeWorkstationIgnoreUpgradeCards`、`outputFlushThreshold`、`outputTransferIntervalTicks`。

### 规划与执行

6. **精简合成模式**（主线 `ec35bf8f`、`7c7acbd4`）。在未提交改动中，尚未提交。
7. **修复批量发配**（主线 `2e494131`）。`ECOProcessingPatternDispatcher` 363 行，主线 499 行。
8. **规划清理与统一**（主线 `9fa1d04b`、`ac366d29`、`1572c494`）。本版仍有 `result/*Ownership*`、`CycleResourceLedger`、`growth/SinglePatternGrowthCycleSolver`、`cycle/UnsupportedCycleSolver`、`solve/CandidateAttemptStatus`；其中 `OwnershipReplayHarness`、`ReferenceOwnershipLedger` 没有被引用。只比过提交标题。
9. **合成卡循环订单与拒收退回**（主线 `f3b572b8`）。本版 `65cd40a2` 只做了 ME Requester，在 mixin 里直接调 `ECOPlanningService`；主线委托给 `ECOAutoCraftingPlanning.begin`。缺 `ECOAutoCraftingPlanning`、`MultiCraftingTrackerMixin`。
10. **CPU 库存分批退回**（主线 `ea36d923`）。缺 `crafting/execution/{ECOCpuInventoryReturn,ECOCraftingInputBuffer,ECOCraftingPlanUpdater}`。
11. **忽略 NBT 的处理样板**（主线 `c7ab9bc1`）。缺 `ECOComponentInsensitiveProcessingPattern`、`PatternDetailsComponentPolicyMixin`。
12. **规划器其他缺失**：`crafting/planner/ECOPlanningImprovements`、`cycle/CycleStartupSolver`、`semantic/PatternContractReader`、`solve/AcyclicSolveMetrics`；`SolveWorkspace` 22 行（主线 154 行，含 Expansion 缓存）；`ActiveRouteSelector` 405 行（主线 604 行）。行数差异里有多少是行为差异未核实。

### API 与存储

13. **附属扩展 API**。缺 `api/extension/`、`api/integration/`、`api/capability/ECOFeature`、`api/multiblock/ECOMultiblock*`、`ECOMachineKind`、`api/power/`、`api/workstation/`、`api/computation/`、`api/ECOCpuTierGlyph`、`api/me/menu/ECOCpuListPage(Host)`、`api/me/diagnostics/ECOProcessingStatusRegistry`。
14. **存储盘配置与优先级接口**（主线 `2754cc6c`、`6724e698`）。缺 `api/storage/{IECOConfigurableCellHost,IECOConfigurableCellDrive,IECOCellWorkbenchDrive,ECOStorageCellPriorities}`、`StorageCellWorkbench`、`CellWorkbench*Mixin`。
15. **存储性能**（主线 21.2.15 的终端共享快照、发信器缓存、返还预算）。`impl/storage` 缺 `ECOStorageListingCache`、`NetworkInventoryCache`、`StorageListingDelta`、`StorageReturnBudget`、`StorageThresholdCache`、`StorageWatcherScope`、`DeferredCellPersistence` 等。本版用 `GtoStorageMenuSnapshotMixin`、`ECOGtoStorageListing` 实现了同主题的优化（`378cf8d8`、`14ecc599`），是否等价未核实。
16. **存储同步与持久化修复**（主线 `9ea8a6b4`）、**避免重复初始化网络节点**（主线 `371d21d7`）。只看了提交标题。

### 界面与文档

17. **合成状态页**：CPU 分页（`CpuSelectionListPagingMixin`、`ECOCpuListClientState`）、机器定位高亮（`ProviderLocationHighlight`）。
18. **语言键**。`en_us` 1318 条，主线 1755 条。扣除联动和裁剪掉的功能后，仍缺 `gui.workstation`（56）、`gui.smart_cooling`（42）、`gui.large_integrated_working_station`（23）、`gui.crafting_report`（21）等。
19. **文档**。没有 CHANGELOG.md 和 API 文档。
20. **指南**。缺 `multiblock_builder.md`；本版用结构终端流程。

## 领先（本版有，主线没有）

没有 GTO 独有、可以直接回流主线的通用功能；本版的提交基本都在跟进主线。

| 项 | 位置 | 备注 |
| --- | --- | --- |
| 结构终端、合成状态暂停、强制开始、存储盘拆解配方 | `items/StructureTerminalItem`、`ECOCraftingStatusSuspension`、`CraftConfirmForceStartMixin`、`StorageCellDisassemblyRecipe` | 与 plain 1.20.1 相同，回流以 plain 1.20.1 为参考 |
| 通用 126 位无符号数 | `crafting/amount/U126.java` | 主线已转向 U128，不建议回流 |
| 规划器内部工具 | `crafting/planner/PlannerLongMaps`、`PlannerText` | 未读代码 |
| 加工机器状态 | `compat/gto/ECOGtoMachineStatus` | 绑定 GTO 的 AE2，只能借鉴思路 |

`8bdd779c`（产物回流）等价于主线 `225f1a34`，不算领先。

## 未核实

- 共有文件的行数差异没有逐行判断是行为差异还是联动裁剪造成的。
- `build.gradle` 没有和主线逐项比较；资源（模型、贴图、方块状态、战利品表）没有比较；配方只比了数量（265 对 343）。
- `.porting/gtolib-dev` 的版本是否与 `gtocore_version` 一致。
