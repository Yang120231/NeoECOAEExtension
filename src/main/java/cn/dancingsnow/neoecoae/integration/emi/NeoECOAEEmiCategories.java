package cn.dancingsnow.neoecoae.integration.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;

/**
 * Kept out of {@link NeoECOAEEmiPlugin}: GTO loads whitelisted plugins with Class.forName before Forge
 * constructs mods, and initializing these categories there would initialize NeoECOAE too early.
 */
public final class NeoECOAEEmiCategories {
    private NeoECOAEEmiCategories() {}

    public static final EmiRecipeCategory MULTIBLOCK = new EmiRecipeCategory(NeoECOAE.id("multiblock"), EmiStack.of(NEBlocks.STORAGE_SYSTEM_L4));
    public static final EmiRecipeCategory INTEGRATED_WORKING_STATION = new EmiRecipeCategory(NeoECOAE.id("integrated_working_station"), EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));
    public static final EmiRecipeCategory COOLING = new EmiRecipeCategory(NeoECOAE.id("cooling"), EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L9));
    public static final EmiRecipeCategory LARGE_WORKING_STATION = new EmiRecipeCategory(
        NeoECOAE.id("large_integrated_working_station"), EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));
}
