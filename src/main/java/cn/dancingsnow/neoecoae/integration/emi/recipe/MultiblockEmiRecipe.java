package cn.dancingsnow.neoecoae.integration.emi.recipe;

import cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiBlockInfoWrapper;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.platform.ui.runtime.integration.xei.emi.ModularUIEMIRecipe;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MultiblockEmiRecipe extends ModularUIEMIRecipe {
    private final EmiRecipeCategory category;
    private final MultiBlockDefinition definition;
    private final RecipeState recipeState;

    public MultiblockEmiRecipe(EmiRecipeCategory category, MultiBlockDefinition definition) {
        this(category, definition, new RecipeState(definition.getExpandMin()));
    }

    private MultiblockEmiRecipe(EmiRecipeCategory category, MultiBlockDefinition definition, RecipeState recipeState) {
        super(recipe -> new MultiBlockInfoWrapper(
            definition,
            recipe.getDisplayWidth(),
            recipe.getDisplayHeight(),
            recipeState::setExpand
        ).createModularUI());
        this.category = category;
        this.definition = definition;
        this.recipeState = recipeState;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        var context = MultiBlockContext.dummyDelegated(recipeState.expand, new TrackedDummyWorld());
        definition.createLevel(context);
        return context.getRequiredItems().stream()
            .filter(requiredItem -> !requiredItem.isEmpty())
            .map(requiredItem -> (EmiIngredient) EmiStack.of(requiredItem.stackWithCount()))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return category;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return definition.getOwner().unwrapKey().map(key -> key.location().withPrefix("/")).orElse(null);
    }

    @Override
    public int getDisplayWidth() {
        return MultiBlockInfoWrapper.DEFAULT_WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return MultiBlockInfoWrapper.DEFAULT_HEIGHT;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.of(EmiStack.of(definition.getOwner().value()));
    }

    @Override
    public boolean supportsRecipeTree() { return false; }

    private static final class RecipeState {
        private int expand;

        private RecipeState(int expand) { this.expand = expand; }

        private void setExpand(int expand) {
            this.expand = expand;
        }
    }
}
