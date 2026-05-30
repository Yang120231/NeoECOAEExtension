package cn.dancingsnow.neoecoae.compat.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.compat.crafting.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.compat.crafting.SizedIngredient;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

@EmiEntrypoint
public class NeoECOAEEmiPlugin implements EmiPlugin {

    public static final EmiRecipeCategory INTEGRATED_WORKING_STATION = new EmiRecipeCategory(
        NeoECOAE.id("integrated_working_station"),
        EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));

    public static final EmiRecipeCategory COOLING = new EmiRecipeCategory(
        NeoECOAE.id("cooling"),
        EmiStack.of(NEBlocks.COMPUTATION_COOLING_CONTROLLER_L4));

    @Override
    public void register(EmiRegistry registry) {
        // ── Integrated Working Station ──
        registry.addCategory(INTEGRATED_WORKING_STATION);
        registry.addWorkstation(INTEGRATED_WORKING_STATION, EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));

        // ── Cooling ──
        registry.addCategory(COOLING);
        registry.addWorkstation(COOLING, EmiStack.of(NEBlocks.COMPUTATION_COOLING_CONTROLLER_L4));
        registry.addWorkstation(COOLING, EmiStack.of(NEBlocks.COMPUTATION_COOLING_CONTROLLER_L6));
        registry.addWorkstation(COOLING, EmiStack.of(NEBlocks.COMPUTATION_COOLING_CONTROLLER_L9));

        var mc = Minecraft.getInstance();
        if (mc.level == null) return;

        for (IntegratedWorkingStationRecipe recipe :
             mc.level.getRecipeManager().getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get())) {
            registry.addRecipe(new IntegratedWorkingStationEmiRecipe(recipe));
        }

        for (CoolingRecipe recipe :
             mc.level.getRecipeManager().getAllRecipesFor(NERecipeTypes.COOLING.get())) {
            registry.addRecipe(new CoolingEmiRecipe(recipe));
        }
    }

    // ── Ingredient helpers ──

    public static EmiIngredient of(@NotNull SizedIngredient ingredient) {
        return EmiIngredient.of(ingredient.ingredient(), ingredient.count());
    }

    public static EmiIngredient of(@NotNull SizedFluidIngredient ingredient) {
        List<EmiStack> list = Arrays.stream(ingredient.getFluids())
            .map(stack -> EmiStack.of(stack.getFluid()))
            .toList();
        return EmiIngredient.of(list, ingredient.amount());
    }
}
