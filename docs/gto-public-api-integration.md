# GTO 公开 API 接入状态

## 当前二进制绑定

- 目标实例为 `F:\Minecraft\1.20.0\.minecraft\versions\GTO 0.6.26\mods`。其中的 `gtocore-forge-1.20.1-26.10.26.jar` 与 Maven 编译依赖逐字节一致，SHA-256 为 `90d85ed8a89776cbffc6712aa0169f559e1f6c5f8a5ae61b6d64725cd08919c1`。
- 该发布包已重命名 GTOLib 的存储类、供应器接口、方法、字段和嵌套的 `IPatternProviderLogic.PushResult`；源码中的 `com.gtolib.api.ae2` 原名不能直接用于这个发布包。
- `GtoRuntimeApi` 集中保存经实例字节码核对的绑定，以 Unicode 转义明确表示发布包的真实名称。编译结果直接构造、访问和调用 GTO 原生对象，不补入同名 API 源码，也不打包 GTOLib 替身。
- `porting/gto-dependencies.json` 锁定 GTOCore 版本和校验和；`compileJava` 前执行 `verifyGtoRuntime`。Forge 的 GTOCore、GTOLib 依赖范围同时固定为 `26.10.26`，升级目标包时必须重新核对二进制绑定。

## 接入约定

- 普通数量表直接使用 `CellDataStorage` 和 `AEKeyLongMap`；单项超过 `Long.MAX_VALUE` 时一次性升级为 `BigCellDataStorage` 和 `AEKeyBigMap`，保持其他键、精确余额与资源身份。
- 无限域从创建时就使用大数量表。超过 U126 的余额继续保留既有精确溢出记录。
- ECO SavedData 仍负责现有库存、迁移收据与所有权的原子快照；原生存储实例共享同一数量表。
- 样板供应器经二进制绑定直接调用 `IPatternProviderLogic.gtolib$pushPattern` 对应的发布方法，使用 DataSyncLib 的 `ObjHolder<KeyCounter[]>` 和原生嵌套 `PushResult`。成功回调确认输入提交；单次调度提交后返回 `BREAK`，由 ECO 调度器继续控制提交预算。
- 两种原生存储继续共享 ECO 的数量表和 `AvailableStacksCache`；增量调用原生字节数更新接口，并通过缓存的 `markAsDirty(key)` 记录变化。库存持久化仍由 ECO SavedData 负责，不将这些独立实例加入 GTOLib 的 UUID 存储。
- 样板缓存仓仍通过自己的 `pushPattern` 接收输入；供应器回调接入不替代缓存仓已有的容量预检。

## 编译依赖

- GTOCore：`com.gto:gtocore-forge-1.20.1:26.10.26`
- DataSyncLib：`com.gto:datasynclib-forge-1.20.1:26.11.1`
- GTO AE2：继续使用已锁定的 `15.2610.47`。
- GTO 依赖按 Java 21 选择变体；本工程继续使用 Java 21 编译器并输出 Java 17 字节码。

## 历史验证记录

- 2026-11-04：执行 `build-local.ps1 compileJava compileGtoCompatibilityTestJava --console=plain`，依赖解析成功，`compileJava` 失败，共 10 条错误，均为原名 GTOLib API 包或符号不可见；四个缺失类型是 `CellDataStorage`、`BigCellDataStorage`、`IPatternProviderLogic`、`PushResult`。
- JUnit 回归尚未编译或运行，不能将本次源码接入视为已验证完成。
- 单独编译真实 `GtoAmounts`，使用锁定的 GTO AE2 字节码：50,000 组普通数量预留计算与 BigInteger 参考值一致，另 50,000 次 U126 路径结果一致。该结果只验证算术边界。
- 2026-11-04：按要求移除全部自动化测试源码、测试资源及自定义 Gradle 测试配置；此前编写的存储与供应器测试不再保留。
- 未构建可交付 JAR，未启动开发客户端或专服，未验证真实交互及普通安装。

## 二进制绑定修复验证

- 2026-11-04：`build-local.ps1 build --offline --console=plain` 成功，包含完整主源码编译和 Forge 重混淆打包；新增的 `verifyGtoRuntime` 校验通过。现有测试源码已移除，Gradle 测试任务为 `NO-SOURCE`，本次没有自动化测试通过数量。
- `tools/compat/check_artifacts.py` 通过：成品包含 4025 项，未打包第三方类、测试夹具或陈旧资源。
- 对成品逐类检查 Java 17 字节码，并将其引用的 4 个 GTOLib 类型、14 个字段或方法与目标实例 JAR 的实际公开定义逐项核对；同时核对 GTO 供应器 Mixin 实现了相同接口和回调签名，全部通过。证据保存于 `build/compat-build-evidence/gto-runtime-linkage.json`。
- 本次未启动客户端或专服，未进行存储读写、世界重载、实际样板投递、EMI 或渲染交互验收。以上构建与静态链接检查不代表游戏内功能验收。
