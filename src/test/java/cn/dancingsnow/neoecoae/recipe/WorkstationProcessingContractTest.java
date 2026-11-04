package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(cn.dancingsnow.neoecoae.util.GtoNativeKeyExtension.class)
class WorkstationProcessingContractTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void nativeDurationDefaultsAndSurvivesJsonAndNetworkSerialization() {
        var id = new ResourceLocation("test:duration");
        var json = JsonParser.parseString("""
            {"inputItems":[{"item":"minecraft:iron_ingot","count":4}],
             "itemOutput":{"item":"minecraft:diamond","count":2},"energy":1000}
            """).getAsJsonObject();
        var serializer = new IntegratedWorkingStationRecipe.Serializer();
        assertEquals(100, serializer.fromJson(id, json).processingTicks());
        json.addProperty("processingTicks", 37);
        var recipe = serializer.fromJson(id, json);
        assertEquals(37, serializer.fromJson(id, IntegratedWorkingStationRecipe.Serializer.toJson(recipe)).processingTicks());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, recipe);
            var synced = serializer.fromNetwork(id, buffer);
            assertEquals(37, synced.processingTicks());
            assertEquals(1000, synced.energy());
            assertEquals(2, synced.itemOutput().getCount());
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
        json.addProperty("processingTicks", 0);
        assertThrows(IllegalArgumentException.class, () -> serializer.fromJson(id, json));
    }

    @Test void nativeRecipesUseTheirDeclaredDuration() {
        var nativeRecipe = recipe("native", 4, 1000, LargeWorkstationRecipe.NATIVE_TYPE.toString());
        assertEquals(100, nativeRecipe.processingTicks());
    }

    @Test void durationCapPreservesShortRecipesAndLeavesTheSourceMachineAndBudgetsUnchanged() {
        var original = new IntegratedWorkingStationRecipe(List.of(), new SizedFluidIngredient(FluidIngredient.empty(), 1),
            ItemStack.EMPTY, FluidStack.EMPTY, 1000, new ResourceLocation("test:long"), 160_000);
        var large = new LargeWorkstationRecipe(original.getId(), original, 1000, List.of());
        assertEquals(200, large.processingTicks());
        assertEquals(160_000, original.processingTicks());
        assertEquals(199, large.processing(199, 2000, true).processingTicks());
        var capped = large.processing(Integer.MAX_VALUE, 2000, true);
        assertEquals(200, capped.processingTicks());
        assertEquals(1000, capped.energy());
        assertEquals(2000, capped.feEnergy());
        assertTrue(capped.feAlternative());
    }

    private static LargeWorkstationRecipe recipe(String id, int count, long energy, String type) {
        var display = new IntegratedWorkingStationRecipe(List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), count)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), new ItemStack(Items.DIAMOND, count), FluidStack.EMPTY, (int) energy);
        return new LargeWorkstationRecipe(new ResourceLocation("test", id), display, energy, List.of(), new ResourceLocation(type));
    }
}
