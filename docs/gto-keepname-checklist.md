# NeoECOAE 所需 GTOLib 公开 API 的 @KeepName 清单

本清单依据 NeoECOAE 当前的原名 API 调用整理，供与 GTO 官方协调发行包保名使用。目标是让附属按同一套公开名称编译和运行，不随每次发行包维护混淆映射。

注解完整名称为 `com.gtolib.api.annotation.KeepName`，大小写为 **`@KeepName`**。按 GTO 官方 `docs/gtolib.md` 的规则，仅在类上标注会保留类名和包名，成员仍可改名，因此下列被直接调用的成员也需要保名。

## 1. 类型声明

以下四个类型声明需要 `@KeepName`，并保持现有公开可见性：

- [ ] `com.gtolib.api.ae2.storage.CellDataStorage`
- [ ] `com.gtolib.api.ae2.storage.BigCellDataStorage`
- [ ] `com.gtolib.api.ae2.IPatternProviderLogic`
- [ ] `com.gtolib.api.ae2.IPatternProviderLogic.PushResult`

最后一项是接口中的嵌套枚举，字节码名称为 `com.gtolib.api.ae2.IPatternProviderLogic$PushResult`，不是 `com.gtolib.api.ae2.PushResult`。

## 2. CellDataStorage 成员

| 成员 | 保名及可见性要求 |
| --- | --- |
| `public CellDataStorage()` | 保留可调用的公开无参构造器；可显式标注 `@KeepName`。构造器本身使用 JVM 固定名称 `<init>`，关键是所属类名称和构造器签名稳定 |
| `public final MEStorage.AvailableStacksCache cache` | 字段标注 `@KeepName`，保持名称、字段类型与公开可见性 |
| `public void setStoredMap(AEKeyLongMap<AEKey> amounts)` | 方法标注 `@KeepName` |
| `public void addBytes(AEKey key, long delta)` | 方法标注 `@KeepName` |
| `public void setBytes(double bytes)` | 方法标注 `@KeepName` |

## 3. BigCellDataStorage 成员

| 成员 | 保名及可见性要求 |
| --- | --- |
| `public BigCellDataStorage()` | 保留可调用的公开无参构造器；可显式标注 `@KeepName`。构造器本身使用 JVM 固定名称 `<init>`，关键是所属类名称和构造器签名稳定 |
| `public final MEStorage.AvailableStacksCache cache` | 字段标注 `@KeepName`，保持名称、字段类型与公开可见性 |
| `public void setStoredMap(AEKeyBigMap<AEKey> amounts)` | 方法标注 `@KeepName` |
| `public void addBytes(AEKey key, long delta)` | 方法标注 `@KeepName` |
| `public void setBytes(double bytes)` | 方法标注 `@KeepName` |

若 setter 由 Lombok 生成，需要让**生成的方法本身**携带注解，或使用等效的精确方法保名规则。只在 `storedMap`、`bytes` 字段上标注，不能代替对 `setStoredMap`、`setBytes` 方法的保名。

## 4. IPatternProviderLogic 方法

以下接口方法需要 `@KeepName`，保持公开可调用，并维持现有参数和返回类型：

```java
@KeepName
PushResult gtolib$pushPattern(
    IPatternDetails patternDetails,
    ObjHolder<KeyCounter[]> inputHolder,
    Supplier<PushResult> pushPatternSuccess
);
```

这里的 `PushResult` 是 `IPatternProviderLogic.PushResult`。GTO 注入到 AE2 `PatternProviderLogic` 中的实现也必须保留相同方法名称和签名，保证接口调用能找到实际实现。

## 5. PushResult 枚举常量

以下三个常量需要继续保名，可标注 `@KeepName`；如果已有枚举常量保名规则覆盖，则保留等效规则即可：

- [ ] `IPatternProviderLogic.PushResult.SUCCESS`
- [ ] `IPatternProviderLogic.PushResult.REJECTED`
- [ ] `IPatternProviderLogic.PushResult.BREAK`

常量所属的嵌套枚举类型本身也必须保名。ECO 的回调返回这里的原生枚举对象；输入提交后返回 `BREAK`，不是自定义替代枚举。

## 6. 签名中已有的公开类型

以下外部类型在已检查的实例中保留原名，只需继续维持现有公开契约，无需因此扩大 GTOLib 的保名范围：

| 类型 | 来源与用途 |
| --- | --- |
| `appeng.api.stacks.AEKey` | AE2，资源键 |
| `appeng.api.stacks.AEKeyLongMap` | AE2，普通数量表 |
| `appeng.api.stacks.AEKeyBigMap` | AE2，大数量表 |
| `appeng.api.stacks.KeyCounter` | AE2，输入及库存数量视图 |
| `appeng.api.crafting.IPatternDetails` | AE2，样板参数 |
| `appeng.api.storage.MEStorage.AvailableStacksCache` | AE2，原生库存缓存；ECO 使用其 `markAsDirty(AEKey)`、`invalidateCache()`、`updateCache()`、`getAvailableStacksCache()` |
| `com.gto.datasynclib.util.holder.ObjHolder` | DataSyncLib，输入持有器；ECO 使用公开无参构造器及公开字段 `value` |
| `java.util.function.Supplier` | JDK，成功回调 |

## 7. 发行包核对

请以**实际对外发布的 JAR**核对以上包名、类型名、字段名、方法名及签名；开发包中存在原名不能代替发行包保名。保名发行包可用后，NeoECOAE 再更新编译依赖和最低运行版本，重新完成编译、打包及游戏验证。
