package cn.dancingsnow.neoecoae.util;

import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateItemModelProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises native cell data-generation callbacks without launching the game. */
class ItemModelUtilTest {


    @Test
    void infiniteCellHasOnlyHousingAndStatusLight() {
        assertEquals(List.of(
            "neoecoae:item/eco_cells/infinite_resource_cell",
            "neoecoae:item/eco_cells/cell_status_light"
        ), generatedTextures(provider -> ItemModelUtil.infiniteCellModel("infinite_resource_cell")
            .accept(context(), provider)));
    }

    @Test
    void nativeCellsUseTheirFamilySpecificItemModels() {
        var provider = provider();
        var context = context();
        for (String family : List.of("item", "fluid", "bulk_item")) {
            String model = "eco_" + family + "_cell_16m";
            when(context.getName()).thenReturn(model);
            ItemModelUtil.cellModel(family, "16m").accept(context, provider);
            verify(provider).withExistingParent(model,
                new ResourceLocation("neoecoae", "item/cell/" + model));
        }
    }

    @SuppressWarnings("unchecked")
    private static DataGenContext<Item, Item> context() {
        return mock(DataGenContext.class);
    }

    private static RegistrateItemModelProvider provider() {
        var provider = mock(RegistrateItemModelProvider.class);
        when(provider.modLoc(anyString())).thenAnswer(call ->
            new ResourceLocation("neoecoae", call.getArgument(0)));
        return provider;
    }

    private static List<String> generatedTextures(java.util.function.Consumer<RegistrateItemModelProvider> generate) {
        var provider = provider();
        var textures = new AtomicReference<List<String>>();
        when(provider.generated(any(), any(ResourceLocation[].class))).thenAnswer(call -> {
            ResourceLocation[] layers = (ResourceLocation[]) call.getRawArguments()[1];
            textures.set(java.util.Arrays.stream(layers).map(ResourceLocation::toString).toList());
            return null;
        });
        generate.accept(provider);
        assertNotNull(textures.get(), "The callback must generate a model");
        return textures.get();
    }
}
