package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchInputScaling;
import com.google.common.collect.BiMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** GTO buffer admission. Protected class names are resolved by their actual inventory contracts. */
public final class ECOGtoProcessingDispatch {
    private static final String INVENTORY = "com.gregtechceu.gtceu.api.transfer.key.KeyInventory";
    private static final ResourceLocation CIRCUIT = new ResourceLocation("gtceu", "programmed_circuit");
    private static final ECOBatchInputScaling CIRCUIT_SCALING = new ECOBatchInputScaling(
        key -> key instanceof AEItemKey && CIRCUIT.equals(key.getId()));
    private static final ThreadLocal<BufferPush> CURRENT_PUSH = new ThreadLocal<>();
    private static final ClassValue<BufferLayout> BUFFERS = new ClassValue<>() {
        @Override protected BufferLayout computeValue(Class<?> type) {
            boolean gto = false;
            Field patterns = null;
            Field wildcardSlots = null;
            boolean circuit = false;
            boolean wildcard = false;
            for (var current = type; current != null; current = current.getSuperclass()) {
                gto |= current.getName().startsWith("com.gtocore.");
                for (var field : current.getDeclaredFields()) {
                    if (field.getName().equals("circuitInventorySimulated")) circuit = true;
                    if (field.getName().equals("blacklistedItems")) wildcard = true;
                    if (BiMap.class.isAssignableFrom(field.getType())) {
                        if (patterns != null || !field.trySetAccessible()) return new BufferLayout(null, null);
                        patterns = field;
                    }
                    if (!Modifier.isStatic(field.getModifiers()) && field.getType().isArray()
                            && slotType(field.getType().getComponentType()) && field.trySetAccessible()) {
                        if (wildcardSlots != null) return new BufferLayout(null, null);
                        wildcardSlots = field;
                    }
                }
            }
            return new BufferLayout(gto && circuit ? patterns : null, wildcard ? wildcardSlots : null);
        }
    };
    private static final ClassValue<SlotLayout> SLOTS = new ClassValue<>() {
        @Override protected SlotLayout computeValue(Class<?> type) {
            return new SlotLayout(inventoryField(type, AEItemKey.class), inventoryField(type, AEFluidKey.class));
        }
    };
    private static final ClassValue<Method> INSERT = new ClassValue<>() {
        @Override protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("insert", AEKey.class, long.class, boolean.class);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("GTO inventory has no simulated insertion contract", failure);
            }
        }
    };

    private ECOGtoProcessingDispatch() {}

    public static boolean isBuffer(Object provider) {
        return provider != null && BUFFERS.get(provider.getClass()).patterns() != null;
    }

    public static ECOBatchInputScaling inputScaling(Object provider) {
        return isBuffer(provider) ? CIRCUIT_SCALING : ECOBatchInputScaling.LINEAR;
    }

    public static boolean supportsInputs(KeyCounter[] inputs) {
        for (var counter : inputs) for (var entry : counter) {
            if (!(entry.getKey() instanceof AEItemKey) && !(entry.getKey() instanceof AEFluidKey)) return false;
        }
        return true;
    }

    /** Check every resource before any CPU debit; GTO's sink otherwise silently accepts partial insertion. */
    public static long maxOperations(Object provider, IPatternDetails pattern, KeyCounter[] inputs, long requested) {
        if (requested <= 0 || provider == null) return 0;
        var layout = BUFFERS.get(provider.getClass());
        if (layout.patterns() == null) return 0;
        try {
            var patterns = (Map<?, ?>) layout.patterns().get(provider);
            Object slot = patterns.get(pattern);
            if (slot == null && layout.wildcardSlots() != null) {
                var slots = (Object[]) layout.wildcardSlots().get(provider);
                // GTO wildcard buffers publish generated patterns and route all of them into slot zero.
                if (slots.length > 0) slot = slots[0];
            }
            if (slot == null) return 0;
            var inventories = SLOTS.get(slot.getClass());
            Map<AEKey, Long> perCraft = new HashMap<>();
            for (var counter : inputs) for (var entry : counter) {
                if (entry.getLongValue() <= 0) return 0;
                perCraft.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            }
            for (var input : perCraft.entrySet()) {
                Field field = input.getKey() instanceof AEItemKey ? inventories.items()
                    : input.getKey() instanceof AEFluidKey ? inventories.fluids() : null;
                if (field == null) return 0;
                Object inventory = field.get(slot);
                long available = ((Number) INSERT.get(inventory.getClass())
                    .invoke(inventory, input.getKey(), Long.MAX_VALUE, true)).longValue();
                if (available < input.getValue()) return 0;
                if (!CIRCUIT_SCALING.perBatch().test(input.getKey()))
                    requested = Math.min(requested, available / input.getValue());
            }
            return requested;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            // No physical inputs have moved. An unknown layout must not authorize a lossy push.
            return 0;
        }
    }

    /** Preserve the catalog pattern at public admission; scale only its native input emission. */
    public static boolean pushBuffer(ICraftingProvider provider, IPatternDetails pattern,
            IPatternDetails executionPattern, KeyCounter[] totals) {
        BufferPush previous = CURRENT_PUSH.get();
        CURRENT_PUSH.set(new BufferPush(pattern, executionPattern, totals));
        try {
            return provider.pushPattern(pattern, totals);
        } finally {
            if (previous == null) CURRENT_PUSH.remove();
            else CURRENT_PUSH.set(previous);
        }
    }

    /** Called only at AEProcessingPattern's physical emission boundary, inside the native GTO slot. */
    public static boolean emitBatch(IPatternDetails pattern, KeyCounter[] inputs, IPatternDetails.PatternInputSink sink) {
        BufferPush push = CURRENT_PUSH.get();
        if (push == null || push.pattern() != pattern || push.inputs() != inputs || push.execution() == pattern)
            return false;
        CURRENT_PUSH.remove();
        try {
            push.execution().pushInputsToExternalInventory(inputs, sink);
            return true;
        } finally {
            CURRENT_PUSH.set(push);
        }
    }

    private static Field inventoryField(Class<?> type, Class<?> key) {
        Field found = null;
        for (var current = type; current != null; current = current.getSuperclass()) {
            for (var field : current.getDeclaredFields()) {
                if (!field.getType().getName().equals(INVENTORY)
                        || !(field.getGenericType() instanceof ParameterizedType generic)
                        || generic.getActualTypeArguments().length != 1
                        || generic.getActualTypeArguments()[0] != key) continue;
                if (found != null || !field.trySetAccessible()) return null;
                found = field;
            }
        }
        return found;
    }

    private static boolean slotType(Class<?> type) {
        if (!type.getName().startsWith("com.gtocore.")) return false;
        for (var current = type; current != null; current = current.getSuperclass())
            for (var method : current.getDeclaredMethods()) {
                var args = method.getParameterTypes();
                if (method.getReturnType() == boolean.class && args.length == 2
                        && args[0] == IPatternDetails.class && args[1] == KeyCounter[].class) return true;
            }
        return false;
    }

    private record BufferLayout(Field patterns, Field wildcardSlots) {}
    private record SlotLayout(Field items, Field fluids) {}
    private record BufferPush(IPatternDetails pattern, IPatternDetails execution, KeyCounter[] inputs) {}
}
