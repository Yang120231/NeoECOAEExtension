package cn.dancingsnow.neoecoae.compat.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.compat.crafting.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * EMI recipe display for the ECO Computation Cooling Controller.
 * Shows fluid input → fluid output with coolant consumption and overclock info.
 */
public class CoolingEmiRecipe implements EmiRecipe {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 52;

    private final ResourceLocation id;
    private final CoolingRecipe recipe;

    public CoolingEmiRecipe(CoolingRecipe recipe) {
        this.id = recipe.getId();
        this.recipe = recipe;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return NeoECOAEEmiPlugin.COOLING;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return id;
    }

    @Override
    public List<dev.emi.emi.api.stack.EmiIngredient> getInputs() {
        SizedFluidIngredient input = recipe.input();
        if (input.ingredient().isEmpty())
            return List.of();
        FluidStack[] fluids = input.getFluids();
        if (fluids == null || fluids.length == 0)
            return List.of();
        return List.of(NeoECOAEEmiPlugin.of(input));
    }

    @Override
    public List<EmiStack> getOutputs() {
        FluidStack output = recipe.output();
        if (output.isEmpty())
            return List.of();
        return List.of(EmiStack.of(output.getFluid(), output.getAmount()));
    }

    @Override
    public List<dev.emi.emi.api.stack.EmiIngredient> getCatalysts() {
        return List.of();
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // Input fluid tank
        SizedFluidIngredient input = recipe.input();
        widgets.addTank(NeoECOAEEmiPlugin.of(input), 8, 8, 20, 36, input.amount())
                .drawBack(false);

        // Progress arrow texture + animation
        widgets.addTexture(
                NeoECOAE.id("textures/gui/jei/cooling_progress_empty.png"),
                36, 6, 30, 30, 0, 0, 30, 30, 30, 30);
        widgets.addAnimatedTexture(
                NeoECOAE.id("textures/gui/jei/cooling_progress.png"),
                36, 6, 30, 30, 0, 0, 30, 30, 30, 30, 2000, false, true, false);

        // Output fluid tank
        FluidStack output = recipe.output();
        if (!output.isEmpty()) {
            widgets.addTank(
                    EmiStack.of(output.getFluid(), output.getAmount()),
                    74, 8, 20, 36, output.getAmount())
                    .drawBack(false)
                    .recipeContext(this);
        }

        // Coolant info
        widgets.addText(
                Component.translatable("category.neoecoae.cooling.coolant", recipe.coolant()),
                6, 44, 0x404040, false);
    }
}
