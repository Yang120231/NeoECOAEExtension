package cn.dancingsnow.neoecoae.compat.gtmthings;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

public final class GTMWirelessCoverSlotValidator {
    private static final String MOD_ID = "gtmthings";
    private static final String COVER_SUFFIX = "_wireless_energy_receive_cover";
    private static final long[] VOLTAGES = {
        8L,
        32L,
        128L,
        512L,
        2_048L,
        8_192L,
        32_768L,
        131_072L,
        524_288L,
        2_097_152L,
        8_388_608L,
        33_554_432L,
        134_217_728L,
        536_870_912L
    };
    private static final String[] TIER_NAMES = {
        "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UXV", "OpV"
    };
    private static final Map<String, Integer> TIERS = Map.ofEntries(
            Map.entry("lv", 1),
            Map.entry("mv", 2),
            Map.entry("hv", 3),
            Map.entry("ev", 4),
            Map.entry("iv", 5),
            Map.entry("luv", 6),
            Map.entry("zpm", 7),
            Map.entry("uv", 8),
            Map.entry("uhv", 9),
            Map.entry("uev", 10),
            Map.entry("uiv", 11),
            Map.entry("uxv", 12),
            Map.entry("opv", 13));

    private GTMWirelessCoverSlotValidator() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isWirelessEnergyCover(ItemStack stack) {
        return getCoverInfo(stack) != null;
    }

    @Nullable public static CoverInfo getCoverInfo(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isAvailable()) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!MOD_ID.equals(id.getNamespace())) {
            return null;
        }

        String path = id.getPath();
        if (!path.endsWith(COVER_SUFFIX)) {
            return null;
        }
        String tierName = path.substring(0, path.length() - COVER_SUFFIX.length());
        if (tierName.endsWith("_4a")) {
            tierName = tierName.substring(0, tierName.length() - 3);
        }
        Integer tier = TIERS.get(tierName);
        if (tier == null || tier < 0 || tier >= VOLTAGES.length) {
            return null;
        }
        return new CoverInfo(tier, VOLTAGES[tier], TIER_NAMES[tier]);
    }

    public record CoverInfo(int tier, long voltage, String tierName) {}
}
