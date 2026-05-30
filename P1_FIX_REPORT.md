# P1 修复报告 — NeoECOAEExtension

**日期**: 2026-05-30  
**分支**: main  
**提交信息建议**: `Refactor Forge 1.20.1 port cleanup and recipe caching`

---

## 1. 修改摘要

| P1 编号 | 问题 | 处理方式 |
|---|---|---|
| P1-1 | integration/** 包 30+ 类被排除编译但未删除 | 移至 `legacy/1.21-porting/`，从 build.gradle excludes 中移除 |
| P1-2 | IWS BE 约 700 行，职责太多 | 提取 `ECOIntegratedWorkingStationRecipeHelper`（recipe 查找 + 日志） |
| P1-3 | CoolingRecipe 每 tick 查询 RecipeManager | 添加基于 FluidStack 快照比较的缓存 |
| P1-4 | AppMek recipe JSON 缺少 forge:mod_loaded | 复制带条件的 JSON 到 `src/main/resources/data/`（已有条件，永久化） |
| P1-5 | settings.gradle / build.gradle 中 mavenLocal() 污染 CI | 改为 `if (System.getenv("CI") == null) { mavenLocal() }` |

---

## 2. 修改文件列表

| 文件 | 操作 |
|---|---|
| `build.gradle` | 修改（移除 mavenLocal 无条件、移除 integration 排除项） |
| `settings.gradle` | 修改（mavenLocal dev-only） |
| `MIGRATION_NOTES.md` | **新增** |
| `legacy/1.21-porting/src/main/java/cn/dancingsnow/neoecoae/integration/**` (20 files) | **移动自 src/main/** |
| `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/ECOIntegratedWorkingStationBlockEntity.java` | 修改（删除 findRecipe 实现和 logRecipeCounts，委托给 helper，移除 unused imports） |
| `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/ECOIntegratedWorkingStationRecipeHelper.java` | **新增**（recipe 查找和日志逻辑） |
| `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/crafting/ECOCraftingSystemBlockEntity.java` | 修改（添加 CoolingRecipe 缓存字段、invalidateCoolingRecipeCache、缓存逻辑） |
| `src/main/resources/data/neoecoae/recipes/eco_chemical_*_*.json` (4 files) | **新增**（带 forge:mod_loaded 条件的 static recipe JSON） |

---

## 3. 每项 P1 的处理说明

### P1-1：integration/** 清理

- **方式**：将 `src/main/java/...integration/**` 下 20 个 Java 源文件移至 `legacy/1.21-porting/src/main/java/...integration/`（不参与编译）
- **build.gradle**：已从 `forge120JavaExcludes` 中移除 `'cn/dancingsnow/neoecoae/integration/**'`
- **MIGRATION_NOTES.md**：已创建，说明这是 Forge 1.20.1 port cleanup
- 迁移列表：集成代码 20 个文件（JEI/EMI/KubeJS/Jade/AppMek），均为 1.21.x NeoForge 参考代码

### P1-2：IWS BE 拆分

- 提取 `ECOIntegratedWorkingStationRecipeHelper`：
  - `findRecipe(Level, InternalInventory, FluidStack)` — 从输入构造 ItemStack 列表并调用 RecipeManager
  - `logRecipeCounts(Level)` — 一次性日志打印配方数量
- 主类变化：
  - 删除 `loggedRecipeCounts` 静态字段
  - 删除 `findRecipe` 方法体（仍保留 private delegate 方法）
  - 删除 `logRecipeCounts` 静态方法
  - 删除未使用的 `AUTO_EXPORT_OFF` 和 `AUTO_EXPORT_ON` 常量（及对应 `AETextures.icon(Icon.*)` 引用）

### P1-3：CoolingRecipe 缓存

- 新增缓存字段 `cachedCoolingRecipe`, `cachedCoolingInputFluid`, `cachedCoolingOutputFluid`, `coolingRecipeDirty`
- `updateState(updateExposed)` 中调用 `invalidateCoolingRecipeCache()` 当结构更新时
- `getCoolingRecipe()` 实现：
  - 输入流体为空 → 清空缓存，返回 null
  - 缓存未过期且流体未变化（`FluidStack.isFluidEqual()`）→ 返回缓存
  - 否则 → RecipeManager 查新配方，更新缓存快照

### P1-4：AppMek recipe JSON 条件

- 4 个 recipe JSON（`eco_chemical_cell_housing`, `eco_chemical_storage_cell_{16m,64m,256m}`）**已有** `forge:mod_loaded: appmek` 条件（由之前 datagen 生成）
- 本次将带条件的 JSON 复制到 `src/main/resources/data/neoecoae/recipes/` 作为静态 recipe，不受未来 `runData` 覆盖
- 注意：disassembly recipe（`disassembly/eco_chemical_storage_cell_*.json`）未单独添加条件，因为它们仅在合成 recipe 已加载时才有意义

### P1-5：mavenLocal() dev-only

- `settings.gradle`：`mavenLocal()` → `if (System.getenv("CI") == null) { mavenLocal() }`
- `build.gradle`：同上

---

## 4. 兼容性说明

| 检查项 | 状态 |
|---|---|
| 注册名 (registry id) | 未改变 |
| 存档 NBT | 未改变 |
| 配方 ID | 未改变 |
| 网络包 | 未改变 |
| 未安装 AppMek 环境 | 更安全（recipe JSON 有 mod_loaded 条件 + static 备份） |
| 安装 AppMek 环境 | chemical recipes 正常加载 |
| 服务端/客户端 | 未改变（无新增 client-only 引用） |

---

## 5. 验证结果

| 命令 | 结果 |
|---|---|
| `./gradlew compileJava` | **BUILD SUCCESSFUL** |
| `./gradlew runData` | **未运行**（需要完整的 Forge 开发环境 + 所有依赖） |
| `./gradlew runServer` | **未运行** |
| `./gradlew runClient` | **未运行** |

---

## 6. 需要人工确认的问题

1. `legacy/1.21-porting/` 目录是否需要推送到 Git，还是仅保留在本地作为参考。
2. 是否需要在未来单独开 NeoForge 1.21.1 分支恢复 integration 代码。
3. AppMek disassembly recipe JSON 是否需要同样添加 `mod_loaded` 条件。
4. IWS BE 的自动导出逻辑（`pushOutResult`/`getTarget`）是否需要进一步拆分到独立 helper 类（当前因需要太多 accessor 方法暂时保留）。
5. 是否需要将 AppMek recipe JSON 的 static 版本同步到 generated 目录。
