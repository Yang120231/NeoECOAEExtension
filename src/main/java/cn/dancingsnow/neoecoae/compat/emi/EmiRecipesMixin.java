package cn.dancingsnow.neoecoae.compat.emi;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.compat.emi.IntegratedWorkingStationEmiRecipe;
import cn.dancingsnow.neoecoae.compat.emi.NeoECOAEEmiPlugin;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
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
 * Injects neoecoae Integrated Working Station recipes directly into EMI's
 * internal recipe storage BEFORE the bake step, so they become part of the
 * final baked EmiRecipeManager.
 * <p>
 * This bypasses GTO Core's plugin whitelist entirely by working at the EMI
 * data-structure level rather than the plugin-discovery level.
 */
@Pseudo
@Mixin(value = EmiRecipes.class, remap = false)
public abstract class EmiRecipesMixin {

    /**
     * Called at the start of {@link EmiRecipes#bake()}.
     * Adds our IWS category, workstation, and recipes to the static lists
     * so they are included in the baked EmiRecipeManager.
     */
    @Inject(method = "bake", at = @At("HEAD"), remap = false)
    private static void neoecoae$injectRecipesBeforeBake(CallbackInfo ci) {
        try {
            // ── Guard: level must be available ──
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null)
                return;

            // ── Guard: don't add twice ──
            if (EmiRecipes.categories.contains(NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION))
                return;

            // ── Access private static fields in EmiRecipes ──
            Field recipesField = EmiRecipes.class.getDeclaredField("recipes");
            recipesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<dev.emi.emi.api.recipe.EmiRecipe> recipes =
                (List<dev.emi.emi.api.recipe.EmiRecipe>) recipesField.get(null);

            Field workstationsField = EmiRecipes.class.getDeclaredField("workstations");
            workstationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<dev.emi.emi.api.recipe.EmiRecipeCategory, List<EmiIngredient>> workstations =
                (Map<dev.emi.emi.api.recipe.EmiRecipeCategory, List<EmiIngredient>>) workstationsField.get(null);

            // ── Add category ──
            EmiRecipes.categories.add(NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION);

            // ── Add workstation ──
            workstations.computeIfAbsent(
                NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION,
                k -> new ArrayList<>()
            ).add(EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));

            // ── Add recipes ──
            var levelRecipes = mc.level.getRecipeManager()
                .getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get());
            for (IntegratedWorkingStationRecipe levelRecipe : levelRecipes) {
                recipes.add(new IntegratedWorkingStationEmiRecipe(levelRecipe));
            }
        } catch (Exception e) {
            // Silently ignore — EMI internals may differ across versions
        }
    }
}
