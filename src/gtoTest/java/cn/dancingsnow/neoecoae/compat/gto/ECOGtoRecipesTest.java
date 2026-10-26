package cn.dancingsnow.neoecoae.compat.gto;

import cn.dancingsnow.neoecoae.mixins.minecraft.RecipeManagerAccessor;
import com.google.gson.JsonParser;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOGtoRecipesTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }
    @Test void captureReadsCurrentResourcesAndSkipsMetadataBeforeGtosJsonCache() throws Exception {
        var resources = mock(net.minecraft.server.packs.resources.ResourceManager.class);
        var resource = mock(net.minecraft.server.packs.resources.Resource.class);
        when(resource.openAsReader()).thenReturn(new java.io.BufferedReader(new java.io.StringReader(
            "{\"type\":\"neoecoae:integrated_working_station\"}")));
        when(resources.listResources(eq("recipes"), any())).thenReturn(Map.of(
            new ResourceLocation("neoecoae:recipes/working_station.json"), resource,
            new ResourceLocation("neoecoae:recipes/_metadata.json"), resource));
        var captured = ECOGtoRecipes.capture(resources);
        assertEquals(1, captured.size());
        assertEquals("neoecoae:integrated_working_station", captured.get(new ResourceLocation("neoecoae:working_station"))
            .getAsJsonObject().get("type").getAsString());
        verify(resource, times(1)).openAsReader();
    }

    @Test void reloadRemovesStaleEcoWithoutMutatingGtosSharedCacheAndUpdatesBothIndices() {
        var manager = mock(RecipeManager.class, withSettings().extraInterfaces(RecipeManagerAccessor.class));
        var access = (RecipeManagerAccessor) manager;
        var type = mock(RecipeType.class);
        var oldId = new ResourceLocation("neoecoae:old");
        var newId = new ResourceLocation("neoecoae:new");
        var gtoId = new ResourceLocation("gtocore:keep");
        var old = mock(Recipe.class);
        var replacement = mock(Recipe.class);
        var nativeRecipe = mock(Recipe.class);
        when(replacement.getType()).thenReturn(type);
        when(nativeRecipe.getType()).thenReturn(type);
        Map<ResourceLocation, Recipe<?>> shared = Map.of(oldId, old, gtoId, nativeRecipe);
        when(access.neoecoae$recipesByName()).thenReturn(shared);

        ECOGtoRecipes.replace(manager, Map.of(newId, replacement), "test");

        ArgumentCaptor<Map> byName = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map> byType = ArgumentCaptor.forClass(Map.class);
        verify(access).neoecoae$setRecipesByName(byName.capture());
        verify(access).neoecoae$setRecipesByType(byType.capture());
        assertEquals(Map.of(newId, replacement, gtoId, nativeRecipe), byName.getValue());
        assertEquals(byName.getValue(), byType.getValue().get(type));
        assertEquals(Map.of(oldId, old, gtoId, nativeRecipe), shared);
    }
}
