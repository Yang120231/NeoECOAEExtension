package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.recipes.entropy.EntropyMode;
import appeng.recipes.entropy.EntropyRecipe;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(cn.dancingsnow.neoecoae.util.GtoNativeKeyExtension.class)
class NativeWorkstationRecipeScopeTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void ae2AloneDoesNotUnlockEntropyRecipes() {
        var manager = mock(RecipeManager.class);
        var source = new EntropyRecipe(new ResourceLocation("test:entropy_absent"), EntropyMode.HEAT, Blocks.STONE, List.of(),
            null, List.of(), Blocks.COBBLESTONE, List.of(), false, null, List.of(), false, List.of());
        when(manager.getRecipes()).thenReturn(List.of(source));
        assertTrue(LargeWorkstationRecipes.getAll(manager, RegistryAccess.EMPTY).isEmpty());
    }
}
