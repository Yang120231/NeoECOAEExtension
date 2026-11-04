# GTO 公开 API 接入状态

## 当前接入方式

- 按用户决定，仅使用 GTOLib 原始包名、类型名和成员名；由用户与 GTO 官方协调公开 API 保名。
- 已移除 `GtoRuntimeApi` 混淆名适配器、单个 GTOCore 发布包的校验和锁定及其专用编译校验任务，不按每个发行包维护混淆映射。
- 两种存储直接持有原生 `CellDataStorage`、`BigCellDataStorage`；样板供应器直接使用 `IPatternProviderLogic` 和它的嵌套类型 `IPatternProviderLogic.PushResult`。
- GTOCore、GTOLib 的 Forge 依赖范围恢复为最低版本声明。当前声明不证明已有旧包具备保名 API；待官方提供保名发行包后，需要更新实际编译依赖和最低运行版本并重新验证。
- 不补入同名 API 源码、替身类型或编译桩，也不通过反射、混淆名回退或禁用核心功能绕过上游依赖。

## 需与 GTO 官方协调的保名范围

完整类型、成员签名、注解位置、构造器与嵌套枚举要求，集中记录在 [GTOLib 公开 API 的 @KeepName 清单](gto-keepname-checklist.md)，可直接用于对外协调。

## 接入约定

- 普通数量表直接使用 `CellDataStorage` 和 `AEKeyLongMap`；单项超过 `Long.MAX_VALUE` 时一次性升级为 `BigCellDataStorage` 和 `AEKeyBigMap`，保持其他键、精确余额与资源身份。
- 无限域从创建时就使用大数量表。超过 U126 的余额继续保留既有精确溢出记录。
- ECO SavedData 仍负责现有库存、迁移收据与所有权的原子快照；原生存储实例共享同一数量表。
- 样板供应器直接调用 `IPatternProviderLogic.gtolib$pushPattern`，使用 DataSyncLib 的 `ObjHolder<KeyCounter[]>` 和原生嵌套 `PushResult`。成功回调确认输入提交；单次调度提交后返回 `BREAK`，由 ECO 调度器继续控制提交预算。
- 两种原生存储继续共享 ECO 的数量表和 `AvailableStacksCache`；增量调用原生字节数更新接口，并通过缓存的 `markAsDirty(key)` 记录变化。库存持久化仍由 ECO SavedData 负责，不将这些独立实例加入 GTOLib 的 UUID 存储。
- 样板缓存仓仍通过自己的 `pushPattern` 接收输入；供应器回调接入不替代缓存仓已有的容量预检。

## 编译依赖

- GTOCore：`com.gto:gtocore-forge-1.20.1:26.10.26`
- DataSyncLib：`com.gto:datasynclib-forge-1.20.1:26.11.1`
- GTO AE2：继续使用已锁定的 `15.2610.47`。
- GTO 依赖按 Java 21 选择变体；本工程继续使用 Java 21 编译器并输出 Java 17 字节码。
- 以上版本是现有编译基线，尚未替换为保名发行依赖；已检查的 GTO 0.6.26（GTOCore `26.10.26`）与 0.6.27（GTOCore `26.11.1`）实例都未提供上述完整原名契约。

## 历史验证记录

- 2026-11-04：执行 `build-local.ps1 compileJava compileGtoCompatibilityTestJava --console=plain`，依赖解析成功，`compileJava` 失败，共 10 条错误，均为原名 GTOLib API 包或符号不可见；四个缺失类型是 `CellDataStorage`、`BigCellDataStorage`、`IPatternProviderLogic`、`PushResult`。
- JUnit 回归尚未编译或运行，不能将本次源码接入视为已验证完成。
- 单独编译真实 `GtoAmounts`，使用锁定的 GTO AE2 字节码：50,000 组普通数量预留计算与 BigInteger 参考值一致，另 50,000 次 U126 路径结果一致。该结果只验证算术边界。
- 2026-11-04：按要求移除全部自动化测试源码、测试资源及自定义 Gradle 测试配置；此前编写的存储与供应器测试不再保留。
- 未构建可交付 JAR，未启动开发客户端或专服，未验证真实交互及普通安装。

## 历史二进制绑定验证（已撤销该方案）

- 2026-11-04：`build-local.ps1 build --offline --console=plain` 成功，包含完整主源码编译和 Forge 重混淆打包；新增的 `verifyGtoRuntime` 校验通过。现有测试源码已移除，Gradle 测试任务为 `NO-SOURCE`，本次没有自动化测试通过数量。
- `tools/compat/check_artifacts.py` 通过：成品包含 4025 项，未打包第三方类、测试夹具或陈旧资源。
- 对成品逐类检查 Java 17 字节码，并将其引用的 4 个 GTOLib 类型、14 个字段或方法与目标实例 JAR 的实际公开定义逐项核对；同时核对 GTO 供应器 Mixin 实现了相同接口和回调签名，全部通过。证据保存于 `build/compat-build-evidence/gto-runtime-linkage.json`。
- 本次未启动客户端或专服，未进行存储读写、世界重载、实际样板投递、EMI 或渲染交互验收。以上构建与静态链接检查不代表游戏内功能验收。

以上成功记录只对应此前的混淆名适配方案和产物，不适用于当前原名 API 源码。当前源码的完整编译、重混淆打包及游戏验收须在取得上游保名依赖后重新进行；不覆盖实例中已安装的旧产物。

## 当前原名 API 验证状态

- 2026-11-04：执行 `build-local.ps1 compileJava --offline --console=plain`（另设置英语诊断输出），Gradle 配置和依赖解析成功，`compileJava` 因当前 GTOCore `26.10.26` 发布包缺少原名 API 失败，共 10 条错误；缺失类型为 `CellDataStorage`、`BigCellDataStorage`、`IPatternProviderLogic` 及其嵌套 `PushResult`。
- 本次未生成或部署当前源码对应的新 JAR，未启动游戏；等待 GTO 官方提供保名发行依赖后再验证。
