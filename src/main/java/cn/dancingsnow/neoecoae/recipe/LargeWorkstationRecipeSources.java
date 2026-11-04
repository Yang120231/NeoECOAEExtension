package cn.dancingsnow.neoecoae.recipe;

import java.util.Set;

/** Publishes native workstation recipes without addon requirements. */
final class LargeWorkstationRecipeSources {
    private LargeWorkstationRecipeSources() {}

    static LargeWorkstationRecipeGroup.Variant direct(LargeWorkstationRecipe recipe) {
        LargeWorkstationModule.fromSourceType(recipe.sourceType());
        return new LargeWorkstationRecipeGroup.Variant(recipe, recipe.id(), recipe.sourceType(),
            Set.of(), true, false);
    }

}
