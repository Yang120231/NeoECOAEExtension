# GTO 公开 API 接入状态

## 接入约定

- 普通数量表直接使用 `CellDataStorage` 和 `AEKeyLongMap`；单项超过 `Long.MAX_VALUE` 时一次性升级为 `BigCellDataStorage` 和 `AEKeyBigMap`，保持其他键、精确余额与资源身份。
- 无限域从创建时就使用大数量表。超过 U126 的余额继续保留既有精确溢出记录。
- ECO SavedData 仍负责现有库存、迁移收据与所有权的原子快照；原生存储实例共享同一数量表。
- 样板供应器直接调用 `IPatternProviderLogic.gtolib$pushPattern`，使用 DataSyncLib 的 `ObjHolder<KeyCounter[]>` 和原生 `PushResult`。成功回调确认输入提交；单次调度提交后返回 `BREAK`，由 ECO 调度器继续控制提交预算。
- 样板缓存仓仍通过自己的 `pushPattern` 接收输入；供应器回调接入不替代缓存仓已有的容量预检。

## 编译依赖

- GTOCore：`com.gto:gtocore-forge-1.20.1:26.10.26`
- DataSyncLib：`com.gto:datasynclib-forge-1.20.1:26.11.1`
- GTO AE2：继续使用已锁定的 `15.2610.47`。
- GTO 依赖按 Java 21 选择变体；本工程继续使用 Java 21 编译器并输出 Java 17 字节码。

## 验证状态

- 2026-11-04：执行 `build-local.ps1 compileJava compileGtoCompatibilityTestJava --console=plain`，依赖解析成功，`compileJava` 失败，共 10 条错误，均为原名 GTOLib API 包或符号不可见；四个缺失类型是 `CellDataStorage`、`BigCellDataStorage`、`IPatternProviderLogic`、`PushResult`。
- JUnit 回归尚未编译或运行，不能将本次源码接入视为已验证完成。
- 单独编译真实 `GtoAmounts`，使用锁定的 GTO AE2 字节码：50,000 组普通数量预留计算与 BigInteger 参考值一致，另 50,000 次 U126 路径结果一致。该结果只验证算术边界。
- 2026-11-04：按要求移除全部自动化测试源码、测试资源及自定义 Gradle 测试配置；此前编写的存储与供应器测试不再保留。
- 未构建可交付 JAR，未启动开发客户端或专服，未验证真实交互及普通安装。

编译 classpath 具备上述原名公开 API 后，重新执行 `build-local.ps1 compileJava --console=plain`，再分别进行 JAR 检查与真实环境验收。删除测试不会解决上述业务源码编译阻塞。
