package cn.dancingsnow.neoecoae.compat.gto;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Keep legacy registry IDs readable while retiring their acquisition and viewer entries in GTO. */
@Mod.EventBusSubscriber(modid = NeoECOAE.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ECOGtoWorkstations {
    private ECOGtoWorkstations() {}

    public static boolean active() {
        return ModList.get() != null && ModList.get().isLoaded("gtocore");
    }

    public static boolean retiredItem(ResourceLocation id) {
        if (id == null || !id.getNamespace().equals("neoecoae")) return false;
        String path = id.getPath();
        return path.equals("integrated_working_station")
            || path.startsWith("integrated_working_station_module_")
            || path.startsWith("large_integrated_working_station_");
    }

    public static boolean retiredRecipe(JsonObject recipe) {
        String type = recipe.has("type") ? recipe.get("type").getAsString() : "";
        if (type.equals("neoecoae:integrated_working_station")
                || type.equals("neoecoae:large_integrated_working_station")) return true;
        for (String key : List.of("result", "itemOutput")) {
            if (!recipe.has(key) || !recipe.get(key).isJsonObject()) continue;
            var output = recipe.getAsJsonObject(key);
            if (output.has("item") && retiredItem(new ResourceLocation(output.get("item").getAsString()))) return true;
        }
        return false;
    }

    public static List<ItemStack> retiredStacks() {
        return BuiltInRegistries.ITEM.stream().filter(item -> retiredItem(BuiltInRegistries.ITEM.getKey(item)))
            .map(ItemStack::new).toList();
    }

    @SubscribeEvent
    public static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (active()) retiredStacks().forEach(event.getEntries()::remove);
    }
}
