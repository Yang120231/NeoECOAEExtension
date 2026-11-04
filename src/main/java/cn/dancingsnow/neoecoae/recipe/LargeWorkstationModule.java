package cn.dancingsnow.neoecoae.recipe;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/** Native workstation appearance retained in saved UI snapshots. */
public enum LargeWorkstationModule implements StringRepresentable {
    REGULAR("regular", 0x878FA5);

    private final String name;
    private final int color;

    LargeWorkstationModule(String name, int color) {
        this.name = name;
        this.color = color;
    }

    @Override
    public String getSerializedName() { return name; }

    public int color() { return color; }

    public Component title() {
        return Component.translatable("gui.neoecoae.workstation.module." + name);
    }

    public Component shortTitle() {
        return Component.translatable("gui.neoecoae.workstation.module_short." + name);
    }

    public boolean unlocks(ResourceLocation sourceType) {
        return supportsSourceType(sourceType);
    }

    public String modelPath(boolean working) {
        return "block/large_integrated_working_station_" + (working ? "on" : "off");
    }

    public static LargeWorkstationModule fromSourceType(ResourceLocation type) {
        if (type == null || type.equals(LargeWorkstationRecipe.NATIVE_TYPE)) return REGULAR;
        throw new IllegalArgumentException("Unsupported workstation source: " + type);
    }

    public static boolean supportsSourceType(ResourceLocation type) {
        return type == null || type.equals(LargeWorkstationRecipe.NATIVE_TYPE);
    }

    public static LargeWorkstationModule byName(String name) {
        return REGULAR;
    }
}
