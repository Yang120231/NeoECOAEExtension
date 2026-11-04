package cn.dancingsnow.neoecoae.integration.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

public final class ECOEmiWorkstationRecipe implements EmiRecipe {
    private static final ResourceLocation REGULAR_BACKGROUND = texture("guis/integration_working_station");
    private static final ResourceLocation LARGE_BACKGROUND = texture("guis/large_integration_working_station");
    private static final ResourceLocation PROGRESS = texture("gui/recipe/progress_bar");
    private final EmiRecipeCategory category;
    private final ResourceLocation id;
    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;
    private final long energy;
    private final int ticks;
    private final boolean large;
    private final int itemInputCount;
    private final boolean itemOutput;
    private final boolean fluidOutput;

    private ECOEmiWorkstationRecipe(EmiRecipeCategory category, ResourceLocation source,
            IntegratedWorkingStationRecipe display, List<SizedFluidIngredient> fluids,
            long energy, int ticks, boolean large) {
        this.category = category;
        this.id = large ? new ResourceLocation(NeoECOAE.MOD_ID,
            "/large_workstation/" + source.getNamespace() + "/" + source.getPath()) : source;
        this.energy = energy;
        this.ticks = ticks;
        this.large = large;
        this.itemInputCount = display.inputItems().size();
        this.itemOutput = display.hasItemOutput();
        this.fluidOutput = display.hasFluidOutput();
        var inputs = new ArrayList<EmiIngredient>();
        for (var item : display.inputItems()) inputs.add(EmiIngredient.of(item.ingredient(), item.count()));
        for (var fluid : fluids) {
            var alternatives = new ArrayList<EmiIngredient>();
            for (var stack : fluid.ingredient().getStacks()) alternatives.add(fluid(stack, fluid.amount()));
            inputs.add(EmiIngredient.of(alternatives, fluid.amount()));
        }
        this.inputs = List.copyOf(inputs);
        var outputs = new ArrayList<EmiStack>();
        if (display.hasItemOutput()) outputs.add(EmiStack.of(display.itemOutput()));
        if (display.hasFluidOutput()) outputs.add(fluid(display.fluidOutput(), display.fluidOutput().getAmount()));
        this.outputs = List.copyOf(outputs);
    }

    static ECOEmiWorkstationRecipe regular(EmiRecipeCategory category, IntegratedWorkingStationRecipe recipe) {
        var fluids = recipe.inputFluid().ingredient().isEmpty() ? List.<SizedFluidIngredient>of() : List.of(recipe.inputFluid());
        return new ECOEmiWorkstationRecipe(category, recipe.getId(), recipe, fluids,
            recipe.energy(), recipe.processingTicks(), false);
    }

    static ECOEmiWorkstationRecipe large(EmiRecipeCategory category, LargeWorkstationRecipe recipe) {
        return new ECOEmiWorkstationRecipe(category, recipe.id(), recipe.display(), recipe.inputFluids(),
            recipe.energy(), recipe.processingTicks(), true);
    }

    private static EmiStack fluid(FluidStack stack, long amount) {
        return EmiStack.of(stack.getFluid(), stack.getTag(), amount);
    }

    private static ResourceLocation texture(String path) {
        return new ResourceLocation(NeoECOAE.MOD_ID, "textures/" + path + ".png");
    }

    @Override public EmiRecipeCategory getCategory() { return category; }
    @Override public ResourceLocation getId() { return id; }
    @Override public List<EmiIngredient> getInputs() { return inputs; }
    @Override public List<EmiStack> getOutputs() { return outputs; }
    @Override public int getDisplayWidth() { return 168; }
    private int backgroundHeight() { return large ? 96 : 75; }
    private int extraFluidCount() { return Math.max(0, inputs.size() - itemInputCount - 1); }
    @Override public int getDisplayHeight() {
        int extraRows = (extraFluidCount() + 8) / 9;
        return extraRows == 0 ? backgroundHeight() : backgroundHeight() + 4 + extraRows * 18;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        int height = backgroundHeight();
        widgets.addTexture(large ? LARGE_BACKGROUND : REGULAR_BACKGROUND, 0, 0, 168, height,
            0, 0, 168, height, 168, height);
        widgets.addAnimatedTexture(PROGRESS, 136, 30, 6, 18, 0, 0, 6, 18, 6, 18,
            ticks * 50, false, true, false);

        int columns = large ? 4 : 3;
        int inputX = large ? 25 : 37;
        for (int i = 0; i < itemInputCount; i++) {
            widgets.addSlot(inputs.get(i), inputX + i % columns * 18, 11 + i / columns * 18).drawBack(false);
        }
        if (inputs.size() > itemInputCount) {
            widgets.addTank(inputs.get(itemInputCount), 4, 8, 18, 60, 16000).drawBack(false);
        }
        for (int i = 0; i < extraFluidCount(); i++) {
            widgets.addSlot(inputs.get(itemInputCount + 1 + i), 3 + i % 9 * 18,
                height + 4 + i / 9 * 18);
        }
        if (itemOutput) widgets.addSlot(outputs.get(0), 113, 30).recipeContext(this).drawBack(false);
        if (fluidOutput) {
            widgets.addTank(outputs.get(itemOutput ? 1 : 0), 146, 8, 18, 60, 16000)
                .recipeContext(this).drawBack(false);
        }

        var energyText = Component.translatable("gui.neoecoae.large_integrated_working_station.recipe_energy_label")
            .append(": " + DisplayNumbers.compact(energy) + " AE");
        var duration = Component.translatable("gui.neoecoae.large_integrated_working_station.recipe_duration",
            DisplayNumbers.grouped(ticks), DisplayNumbers.decimal(ticks / 20.0, 2));
        if (large) {
            widgets.addDrawable(0, 72, 168, 24, (graphics, mouseX, mouseY, delta) -> {
                var font = Minecraft.getInstance().font;
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(7, 3, 0);
                pose.scale(0.75f, 0.75f, 1);
                graphics.drawString(font, energyText, 0, 0, 0x403e53, false);
                pose.translate(0, 8 / 0.75f, 0);
                graphics.drawString(font, duration, 0, 0, 0x403e53, false);
                pose.popPose();
            });
        } else widgets.addText(energyText, 24, 66, 0x403e53, false);
        var details = List.<Component>of(
            Component.translatable("gui.neoecoae.large_integrated_working_station.recipe_energy_label")
                .append(": " + DisplayNumbers.grouped(energy) + " AE"), duration);
        widgets.addTooltipText(details, 136, 30, 6, 18);
        widgets.addTooltipText(details, large ? 4 : 24, large ? 72 : 66, large ? 160 : 120, large ? 20 : 9);
    }
}
