package cn.dancingsnow.neoecoae.integration.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiPluginContainer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

/** Native EMI recipes use the same current recipe catalogue as workstation execution. */
@EmiEntrypoint
public final class ECOEmiPlugin implements EmiPlugin {
    public static List<EmiPluginContainer> includeIn(List<EmiPluginContainer> plugins) {
        for (var container : plugins) {
            if (container.plugin() instanceof ECOEmiPlugin) return plugins;
        }
        var result = new ArrayList<>(plugins);
        result.add(new EmiPluginContainer(new ECOEmiPlugin(), NeoECOAE.MOD_ID));
        return result;
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
        registerRecipes(registry, regular, large);
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
        var largeRecipes = LargeWorkstationRecipes.getAll(manager, RegistryAccess.EMPTY);
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
