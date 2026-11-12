package cn.dancingsnow.neoecoae.integration.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.integration.emi.recipe.MultiblockEmiRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import cn.dancingsnow.neoecoae.client.gui.crafting.ECOCraftConfirmScreen;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.RetainedUIGuiContainer;
import dev.emi.emi.registry.EmiPluginContainer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

/** Native EMI recipes use the same current recipe catalogue as workstation execution. */
@EmiEntrypoint
public final class ECOEmiPlugin implements EmiPlugin {
    /** GTO 0.5.6 replaces EMI discovery with this public registration event. */
    public static void initialize() {
        var plugin = new EmiPluginContainer(new ECOEmiPlugin(), NeoECOAE.MOD_ID);
        com.gtolib.api.GTOApi.EMI_PLUGIN_EVENT.addListener(ECOEmiPlugin.class,
            sink -> sink.accept(plugin));
    }

    @Override
    public void register(EmiRegistry registry) {
        var workstation = EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION);
        var regular = category("integrated_working_station", workstation);
        var large = category("large_integrated_working_station", workstation);
        registry.addCategory(regular);
        registry.addCategory(large);
        registry.addWorkstation(regular, workstation);
        registry.addWorkstation(large, workstation);
        registry.addWorkstation(large, EmiStack.of(NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INTERFACE));
        registerRecipes(registry, regular, large);
        var multiblock = category("multiblock", EmiStack.of(NEBlocks.STORAGE_SYSTEM_L4));
        registry.addCategory(multiblock);
        for (var definition : NEMultiBlocks.DEFINITIONS) {
            registry.addWorkstation(multiblock, EmiStack.of(definition.getOwner().value()));
            registry.addRecipe(new MultiblockEmiRecipe(multiblock, definition));
        }
        registerRetainedScreen(registry);
        LoggerFactory.getLogger("neoecoae").info("GTO ECO EMI multiblocks: {}", NEMultiBlocks.DEFINITIONS.size());
    }

    /**
     * EMI matches stack providers and exclusion areas by exact screen class, so LDLib's
     * {@code ModularUIGuiContainer} handlers never apply to this subclass. Mouse coordinates are
     * also converted into the scaled logical space used by the retained element tree.
     */
    private static void registerRetainedScreen(EmiRegistry registry) {
        registry.addStackProvider(RetainedUIGuiContainer.class, (screen, x, y) -> {
            var main = screen.modularUI.mainGroup;
            // EMI passes logical coordinates for key presses (remembered from the scaled render call)
            // but window coordinates for mouse clicks, so read the pointer directly instead.
            var minecraft = Minecraft.getInstance();
            var window = minecraft.getWindow();
            double mouseX = minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
            double mouseY = minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
            Object ingredient = main.getXEIIngredientOverMouse(screen.toLogical(mouseX), screen.toLogical(mouseY));
            if (ingredient instanceof EmiStackInteraction interaction) return interaction;
            if (ingredient instanceof EmiIngredient emi) return new EmiStackInteraction(emi, null, false);
            return EmiStackInteraction.EMPTY;
        });
        registry.addExclusionArea(RetainedUIGuiContainer.class, (screen, consumer) -> screen.getGuiExtraAreas()
            .forEach(area -> consumer.accept(new Bounds(
                (int) Math.floor(screen.toLogical(area.getX())), (int) Math.floor(screen.toLogical(area.getY())),
                (int) Math.ceil(screen.toLogical(area.getWidth())), (int) Math.ceil(screen.toLogical(area.getHeight()))))));
        // EMI ignores every key press on container screens whose menu has no slots unless a bounds
        // provider claims them. AE2 only claims its own CraftConfirmScreen class, not the ECO report.
        // EMI lays itself out from Screen.width/height, which this screen keeps in logical units.
        registry.addScreenBoundsProvider(RetainedUIGuiContainer.class, screen -> new Bounds(
            screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize(), screen.getYSize()));
        registry.addScreenBoundsProvider(ECOCraftConfirmScreen.class, screen -> new Bounds(
            screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize(), screen.getYSize()));
    }

    static void registerRecipes(EmiRegistry registry, EmiRecipeCategory regular, EmiRecipeCategory large) {
        var manager = registry.getRecipeManager();
        int regularCount = 0;
        for (var recipe : manager.getRecipes()) {
            if (recipe instanceof IntegratedWorkingStationRecipe workstation) {
                registry.addRecipe(ECOEmiWorkstationRecipe.regular(regular, workstation));
                regularCount++;
            }
        }
        // The adapter includes both native twelve-input recipes and recipes shared with the single block.
        var level = Minecraft.getInstance().level;
        var largeRecipes = level == null ? List.<cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe>of()
            : LargeWorkstationRecipes.getAll(manager, level.registryAccess());
        for (var recipe : largeRecipes) registry.addRecipe(ECOEmiWorkstationRecipe.large(large, recipe));
        LoggerFactory.getLogger("neoecoae").info("GTO ECO EMI recipes: integrated={}, large={}",
            regularCount, largeRecipes.size());
    }

    private static EmiRecipeCategory category(String name, EmiStack workstation) {
        return new EmiRecipeCategory(NeoECOAE.id(name), workstation) {
            @Override public Component getName() {
                return Component.translatable("category.neoecoae." + name);
            }
        };
    }
}
