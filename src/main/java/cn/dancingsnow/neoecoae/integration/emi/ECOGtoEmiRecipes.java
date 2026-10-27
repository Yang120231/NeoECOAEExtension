package cn.dancingsnow.neoecoae.integration.emi;

import cn.dancingsnow.neoecoae.compat.gto.ECOGtoNativeRecipes;
import cn.dancingsnow.neoecoae.compat.gto.ECOGtoWorkstations;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/** GTO snapshots its EMI catalogue before world recipes load. Publish ECO's later definitions too. */
public final class ECOGtoEmiRecipes {
    private ECOGtoEmiRecipes() {}

    @SuppressWarnings("unchecked")
    public static void register(EmiRegistry registry) {
        if (!ECOGtoWorkstations.active()) return;
        // Also runs on remote clients, whose RecipeManager never applies server datapacks locally.
        ECOGtoNativeRecipes.register();
        try {
            var definitionClass = Class.forName("com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition");
            var displayClass = Class.forName("com.gtocore.integration.emi.GTEMIRecipe");
            var categoryClass = Class.forName("com.gregtechceu.gtceu.integration.emi.recipe.GTRecipeEMICategory");
            var categories = (Function<Object, EmiRecipeCategory>) categoryClass.getField("CATEGORIES").get(null);
            var createDisplay = displayClass.getConstructor(definitionClass, EmiRecipeCategory.class);
            var displays = new LinkedHashMap<ResourceLocation, EmiRecipe>();
            for (var entry : ECOGtoNativeRecipes.registeredDefinitions().entrySet()) {
                Object definition = entry.getValue();
                Object category = definitionClass.getField("recipeCategory").get(definition);
                displays.put(entry.getKey(), (EmiRecipe) createDisplay.newInstance(definition, categories.apply(category)));
            }
            replaceDisplays(registry, displays);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot publish ECO native GTO recipes to EMI", failure);
        }
    }

    static void replaceDisplays(EmiRegistry registry, Map<ResourceLocation, EmiRecipe> additions) {
        // Some GTO versions may have cached ECO already. Replace those IDs, preserving every other recipe.
        // EMI applies removal predicates after plugins finish, including to newly added recipes.
        registry.removeRecipes(recipe -> additions.containsKey(recipe.getId())
            && additions.get(recipe.getId()) != recipe);
        additions.values().forEach(registry::addRecipe);
    }
}
