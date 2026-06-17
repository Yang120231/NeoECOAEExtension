package cn.dancingsnow.neoecoae.compat.gtl;

import appeng.api.networking.crafting.ICraftingProvider;

public final class GTLCraftingProviderCompat {
    private static final String GTL_ME_CRAFT_IO_PART = "org.gtlcore.gtlcore.api.machine.trait.AECraft.IMECraftIOPart";
    private static final String GTL_ME_PATTERN_PART =
            "org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternPartMachine";
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
            if (candidate.getName().equals(interfaceName) || hasNamedInterface(candidate, interfaceName)) {
                return true;
            }
        }
        return false;
    }
}
