package cn.dancingsnow.neoecoae.bridge;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.compat.emi.CoolingEmiRecipe;
import cn.dancingsnow.neoecoae.compat.emi.IntegratedWorkingStationEmiRecipe;
import cn.dancingsnow.neoecoae.compat.emi.NeoECOAEEmiPlugin;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiRecipes;
import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Injects neoecoae custom machine recipes directly into EMI's internal
 * recipe storage BEFORE the bake step.
 * <p>
 * Only handles custom recipe types (IWS, Cooling) that need their own EMI
 * categories. Vanilla crafting recipes and AE2 inscriber/transform recipes
 * are handled natively by EMI/AE2 and don't need injection.
 */
@Pseudo
@Mixin(value = EmiRecipes.class, remap = false)
public abstract class EmiRecipesMixin {

    @Inject(method = "bake", at = @At("HEAD"), remap = false)
    private static void neoecoae$injectRecipesBeforeBake(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;

        try {
            Field recipesField = EmiRecipes.class.getDeclaredField("recipes");
            recipesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<EmiRecipe> recipes = (List<EmiRecipe>) recipesField.get(null);

            Field workstationsField = EmiRecipes.class.getDeclaredField("workstations");
            workstationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<dev.emi.emi.api.recipe.EmiRecipeCategory, List<EmiIngredient>> workstations = (Map<dev.emi.emi.api.recipe.EmiRecipeCategory, List<EmiIngredient>>) workstationsField
                    .get(null);

            injectIWS(mc, recipes, workstations);
            injectCooling(mc, recipes, workstations);
        } catch (Exception ignored) {
        }
    }

    // ═══ Integrated Working Station ═══

    private static void injectIWS(Minecraft mc, List<EmiRecipe> recipes,
            Map<dev.emi.emi.api.recipe.EmiRecipeCategory, List<EmiIngredient>> workstations) {
        if (EmiRecipes.categories.contains(NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION))
            return;

        EmiRecipes.categories.add(NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION);
        workstations.computeIfAbsent(
                NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION,
                k -> new ArrayList<>()).add(EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));

        var all = mc.level.getRecipeManager()
                .getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get());
        for (IntegratedWorkingStationRecipe r : all) {
            recipes.add(new IntegratedWorkingStationEmiRecipe(r));
        }
    }

    // ═══ Cooling ═══

    private static void injectCooling(Minecraft mc, List<EmiRecipe> recipes,
            Map<dev.emi.emi.api.recipe.EmiRecipeCategory, List<EmiIngredient>> workstations) {
        if (EmiRecipes.categories.contains(NeoECOAEEmiPlugin.COOLING))
            return;

        EmiRecipes.categories.add(NeoECOAEEmiPlugin.COOLING);
        var ws = workstations.computeIfAbsent(
                NeoECOAEEmiPlugin.COOLING,
                k -> new ArrayList<>());
        ws.add(EmiStack.of(NEBlocks.COMPUTATION_COOLING_CONTROLLER_L4));
        ws.add(EmiStack.of(NEBlocks.COMPUTATION_COOLING_CONTROLLER_L6));
        ws.add(EmiStack.of(NEBlocks.COMPUTATION_COOLING_CONTROLLER_L9));

        var all = mc.level.getRecipeManager()
                .getAllRecipesFor(NERecipeTypes.COOLING.get());
        for (CoolingRecipe r : all) {
            recipes.add(new CoolingEmiRecipe(r));
        }
    }
}
