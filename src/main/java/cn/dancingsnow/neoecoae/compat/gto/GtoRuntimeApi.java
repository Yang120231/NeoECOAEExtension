package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyBigMap;
import appeng.api.stacks.AEKeyLongMap;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage.AvailableStacksCache;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.gto.datasynclib.util.holder.ObjHolder;
import com.gtolib.\u3164\u08ea\u08e9\u08f5;
import com.gtolib.\u3164\u08f1\u08f1\u08f3;
import com.gtolib.\u3164\u08f1\u08f1\u08f3.\u3164\u08e3\u08e4\u08ed;
import com.gtolib.\u3164\u08fe\u08f5\u08f8;
import java.util.function.Supplier;

/**
 * Direct binary bindings for the GTO 0.6.26 release, verified by porting/gto-dependencies.json.
 *
 * <p>GTOCore 26.10.26 renames GTOLib's API classes, members and nested PushResult enum.
 * Unicode escapes keep those binary names explicit and confined to this adapter. These are calls
 * to the installed implementation; no replacement GTOLib classes are compiled or bundled.
 */
final class GtoRuntimeApi {
    private GtoRuntimeApi() {}

    static Object createCell(AEKeyLongMap<AEKey> amounts) {
        // CellDataStorage.setStoredMap
        var storage = new \u3164\u08fe\u08f5\u08f8();
        storage.\u3164\u08ee\u08ea\u08f5(amounts);
        return storage;
    }

    static Object createBigCell(AEKeyBigMap<AEKey> amounts) {
        // BigCellDataStorage.setStoredMap
        var storage = new \u3164\u08ea\u08e9\u08f5();
        storage.\u3164\u08ee\u08ea\u08f5(amounts);
        return storage;
    }

    static AvailableStacksCache cellCache(Object storage) {
        return ((\u3164\u08fe\u08f5\u08f8) storage).\u3164\u08fc\u08fd\u08f8;
    }

    static AvailableStacksCache bigCellCache(Object storage) {
        return ((\u3164\u08ea\u08e9\u08f5) storage).\u3164\u08ed\u08ef\u08fa;
    }

    static void addCellBytes(Object storage, AEKey key, long delta) {
        ((\u3164\u08fe\u08f5\u08f8) storage).\u3164\u08ee\u08ea\u08f5(key, delta);
    }

    static void addBigCellBytes(Object storage, AEKey key, long delta) {
        ((\u3164\u08ea\u08e9\u08f5) storage).\u3164\u08ee\u08ea\u08f5(key, delta);
    }

    static void setCellBytes(Object storage, double bytes) {
        ((\u3164\u08fe\u08f5\u08f8) storage).\u3164\u08ee\u08ea\u08f5(bytes);
    }

    static void setBigCellBytes(Object storage, double bytes) {
        ((\u3164\u08ea\u08e9\u08f5) storage).\u3164\u08ee\u08ea\u08f5(bytes);
    }

    static boolean isPatternProvider(Class<?> providerType) {
        return \u3164\u08f1\u08f1\u08f3.class.isAssignableFrom(providerType);
    }

    @SuppressWarnings("unchecked")
    static void push(PatternProviderLogic provider, IPatternDetails pattern,
            ObjHolder<KeyCounter[]> inputs, Supplier<?> accepted) {
        // IPatternProviderLogic.gtolib$pushPattern; Supplier erasure preserves the native enum.
        ((\u3164\u08f1\u08f1\u08f3) provider).\u3164\u08ee\u08ea\u08f5(
                pattern, inputs, (Supplier<\u3164\u08e3\u08e4\u08ed>) accepted);
    }

    static Object success() { return \u3164\u08e3\u08e4\u08ed.SUCCESS; }
    static Object rejected() { return \u3164\u08e3\u08e4\u08ed.REJECTED; }
    static Object breakAfterCommit() { return \u3164\u08e3\u08e4\u08ed.BREAK; }
}
