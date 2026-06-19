package cn.dancingsnow.neoecoae.compat.gtl;

import appeng.api.networking.crafting.ICraftingProvider;
import com.gtocore.common.machine.multiblock.part.ae.MEPatternPartMachineKt;

public final class GTLCraftingProviderCompat {
    private static final String GTO_PACKAGE_MARKER = ".gtocore.";
    private static final String GTL_PACKAGE_MARKER = ".gtlcore.";
    private static final String GTL_ME_CRAFT_IO_PART = "org.gtlcore.gtlcore.api.machine.trait.AECraft.IMECraftIOPart";
    private static final String GTL_ME_PATTERN_PART =
            "org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternPartMachine";
    private static final String ME_CRAFT_IO_PART_SIMPLE_NAME = "IMECraftIOPart";
    private static final String ME_PATTERN_PART_SIMPLE_NAME = "IMEPatternPartMachine";
    private static final ClassValue<Boolean> AUTO_EXPAND_PROVIDER = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return isAutoExpandProviderType(type);
        }
    };

    private GTLCraftingProviderCompat() {}

    public static boolean isAutoExpandProvider(ICraftingProvider provider) {
        return provider != null && AUTO_EXPAND_PROVIDER.get(provider.getClass());
    }

    public static boolean isGTOCoreNativePatternProvider(ICraftingProvider provider) {
        return provider instanceof MEPatternPartMachineKt<?>;
    }

    private static boolean isAutoExpandProviderType(Class<?> type) {
        while (type != null && type != Object.class) {
            if (hasNamedInterface(type, GTL_ME_CRAFT_IO_PART) || hasNamedInterface(type, GTL_ME_PATTERN_PART)) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean hasNamedInterface(Class<?> type, String interfaceName) {
        for (Class<?> candidate : type.getInterfaces()) {
            if (candidate.getName().equals(interfaceName)
                    || isGtAutoExpandInterface(candidate)
                    || hasNamedInterface(candidate, interfaceName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGtAutoExpandInterface(Class<?> type) {
        String name = type.getName();
        return (name.contains(GTL_PACKAGE_MARKER) || name.contains(GTO_PACKAGE_MARKER))
                && (type.getSimpleName().equals(ME_CRAFT_IO_PART_SIMPLE_NAME)
                        || type.getSimpleName().equals(ME_PATTERN_PART_SIMPLE_NAME));
    }
}
