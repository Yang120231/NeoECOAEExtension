package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import net.minecraftforge.fluids.FluidStack;
import cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(cn.dancingsnow.neoecoae.util.GtoNativeKeyExtension.class)
class LargeWorkstationRecipeTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void overlappingTagsCanReassignEarlierAllocations() {
        assertTrue(LargeWorkstationRecipe.matchesQuantities(new long[]{5, 5}, new long[]{5, 5},
            new boolean[][]{{true, true}, {true, false}}));
        assertFalse(LargeWorkstationRecipe.matchesQuantities(new long[]{5, 5}, new long[]{5, 5},
            new boolean[][]{{true, true}, {false, false}}));
    }

    @Test void hugeCountsStayCompactAndRejectSurplus() {
        assertTrue(LargeWorkstationRecipe.matchesQuantities(new long[]{Integer.MAX_VALUE},
            new long[]{Integer.MAX_VALUE}, new boolean[][]{{true}}));
        assertFalse(LargeWorkstationRecipe.matchesQuantities(new long[]{6}, new long[]{5}, new boolean[][]{{true}}));
    }

    @Test void fullOutputContractSelectsBetweenIdenticalInputs() {
        var recipe = nativeRecipe(new ItemStack(Items.DIAMOND, 2));
        KeyCounter inputs = items(Items.IRON_INGOT, 4);
        assertTrue(recipe.matches(inputs, items(Items.DIAMOND, 2)));
        assertFalse(recipe.matches(inputs, items(Items.DIAMOND, 1)));
        assertFalse(recipe.matches(inputs, items(Items.GOLD_INGOT, 2)));
        KeyCounter surplus = items(Items.DIAMOND, 2);
        surplus.add(AEItemKey.of(Items.STONE), 1);
        assertFalse(recipe.matches(inputs, surplus));
        assertFalse(recipe.matches(items(Items.IRON_INGOT, 5), items(Items.DIAMOND, 2)));
    }

    @Test void scaledItemAndFluidOutputsMustShareTheSameIntegerMultiplier() {
        var recipe = new LargeWorkstationRecipe(new ResourceLocation("test:fluids"),
            new IntegratedWorkingStationRecipe(List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 128)),
                SizedFluidIngredient.of(Fluids.WATER, 1000), new ItemStack(Items.DIAMOND, 8),
                new FluidStack(Fluids.LAVA, 250), 12345), 12345, List.of());
        var inputs = items(Items.IRON_INGOT, 256);
        inputs.add(AEFluidKey.of(Fluids.WATER), 2000);
        var outputs = items(Items.DIAMOND, 16);
        outputs.add(AEFluidKey.of(Fluids.LAVA), 500);
        assertEquals(2, recipe.matchingMultiplier(inputs, outputs));
        outputs.remove(AEFluidKey.of(Fluids.LAVA), 250);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
        outputs.add(AEFluidKey.of(Fluids.LAVA), 250);
        inputs.remove(AEFluidKey.of(Fluids.WATER), 1);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
        inputs.add(AEFluidKey.of(Fluids.WATER), 1);
        outputs.add(AEItemKey.of(Items.STONE), 1);
        assertEquals(0, recipe.matchingMultiplier(inputs, outputs));
    }

    @Test void fractionalOutputsOrDifferentInputScaleAreRejected() {
        var recipe = nativeRecipe(new ItemStack(Items.DIAMOND, 2));
        assertEquals(0, recipe.matchingMultiplier(items(Items.IRON_INGOT, 6), items(Items.DIAMOND, 3)));
        assertEquals(0, recipe.matchingMultiplier(items(Items.IRON_INGOT, 4), items(Items.DIAMOND, 4)));
        assertEquals(0, recipe.matchingMultiplier(items(Items.IRON_INGOT, 9), items(Items.DIAMOND, 4)));
    }

    @Test void scaledOverlappingIngredientsAllowDifferentConcreteItemsPerRecipeOperation() {
        var recipe = new LargeWorkstationRecipe(new ResourceLocation("test:mixed"), new IntegratedWorkingStationRecipe(
            List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 4),
                new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), new ItemStack(Items.DIAMOND),
            FluidStack.EMPTY, 100), 100, List.of());
        var inputs = items(Items.IRON_INGOT, 3);
        inputs.add(AEItemKey.of(Items.GOLD_INGOT), 7);
        assertEquals(2, recipe.matchingMultiplier(inputs, items(Items.DIAMOND, 2)));
        inputs.remove(AEItemKey.of(Items.IRON_INGOT), 2);
        inputs.add(AEItemKey.of(Items.GOLD_INGOT), 2);
        assertEquals(0, recipe.matchingMultiplier(inputs, items(Items.DIAMOND, 2)));
    }

    private static <T extends Recipe<?>> T roundTrip(net.minecraft.world.item.crafting.RecipeSerializer<T> serializer,
            ResourceLocation id, com.google.gson.JsonObject json) {
        var recipe = serializer.fromJson(id, json);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try (var registries = mockStatic(net.minecraftforge.registries.RegistryManager.class)) {
            serializer.toNetwork(buffer, recipe);
            var restored = serializer.fromNetwork(id, buffer);
            assertFalse(buffer.isReadable());
            return restored;
        } finally { buffer.release(); }
    }

    @Test void nativeRecipesAreDiscoveredAndReloadReplacesCache() {
        var recipe = nativeRecipe(new ItemStack(Items.DIAMOND, 2));
        var holder = cn.dancingsnow.neoecoae.util.RecipeTestSupport.withId(recipe.id(), recipe.display());
        var manager = mock(net.minecraft.world.item.crafting.RecipeManager.class);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        when(manager.getRecipes()).thenReturn(List.of(holder));
        var first = LargeWorkstationRecipes.getAll(manager, registries);
        assertEquals(1, first.size());
        assertEquals(recipe, cn.dancingsnow.neoecoae.platform.JdkCompat.first(first));
        assertSame(first, LargeWorkstationRecipes.getAll(manager, registries));
        when(manager.getRecipes()).thenReturn(List.of());
        assertTrue(LargeWorkstationRecipes.getAll(manager, registries).isEmpty());
        assertNull(LargeWorkstationRecipes.getById(manager, registries, recipe.id()));
        assertTrue(LargeWorkstationRecipes.getAvailable(manager, registries, LargeWorkstationModule.REGULAR).isEmpty());
    }

    private static LargeWorkstationRecipe nativeRecipe(ItemStack output) {
        return new LargeWorkstationRecipe(new ResourceLocation("test:recipe"), new IntegratedWorkingStationRecipe(
            List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 4)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), output, FluidStack.EMPTY, 100), 100, List.of());
    }

    private static KeyCounter items(net.minecraft.world.item.Item item, long amount) {
        var result = new KeyCounter();
        result.add(AEItemKey.of(item), amount);
        return result;
    }
}
